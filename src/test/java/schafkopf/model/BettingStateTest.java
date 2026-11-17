package schafkopf.model;

import org.junit.jupiter.api.Test;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BettingStateTest {
    @Test
    void allowsEachPlayerToDoubleOnceAndTracksKontraReSequence() {
        BettingState state = new BettingState();
        Player first = new Player("1", "P1");
        Player second = new Player("2", "P2");

        assertTrue(state.applyDouble(first));
        assertFalse(state.applyDouble(first));
        assertTrue(state.applyDouble(second));
        assertEquals(4, state.multiplier());
        assertTrue(state.callKontra());
        assertFalse(state.callKontra());
        assertTrue(state.callRetour());
        assertFalse(state.callRetour());
        assertEquals(16, state.multiplier());
        BettingState.StakeBreakdown breakdown = state.breakdown();
        assertEquals(List.of("1", "2"), breakdown.klopferIds());
        assertEquals(2, breakdown.klopferCount());
        assertEquals(1, breakdown.stockFactor());
        assertTrue(breakdown.kontra());
        assertTrue(breakdown.retour());
        assertEquals(16, breakdown.totalMultiplier());
    }

    @Test
    void stockCarriesUntilConsumedAndCanBeCombined() {
        ScoreLedger ledger = new ScoreLedger(List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4")), new Tariff());

        ledger.addStock(2);
        ledger.addStock(2);

        assertEquals(4, ledger.consumeStockMultiplier());
        assertEquals(1, ledger.pendingStockMultiplier());
    }

    @Test
    void stockLedgerStopsAtFiveDoublesAndReportsTheLimit() {
        ScoreLedger ledger = new ScoreLedger(List.of(
                new Player("1", "P1"), new Player("2", "P2"),
                new Player("3", "P3"), new Player("4", "P4")), new Tariff());

        for (int index = 0; index < 5; index++) {
            assertTrue(ledger.addStock(2));
        }

        assertEquals(32, ledger.pendingStockMultiplier());
        assertFalse(ledger.addStock(2));
        assertEquals(32, ledger.pendingStockMultiplier());
    }

    @Test
    void stockCannotExceedFiveDoubles() {
        BettingState state = new BettingState();
        state.applyStock(32);

        assertThrows(IllegalArgumentException.class, () -> state.applyStock(2));
    }

    @Test
    void nonStockMultipliersMayExceedStockLimit() {
        BettingState state = new BettingState();
        state.applyStock(32);

        assertTrue(state.applyDouble(new Player("1", "P1")));

        assertEquals(32, state.stockFactor());
        assertEquals(64, state.multiplier());
    }
}