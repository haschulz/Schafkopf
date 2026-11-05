package schafkopf.model;

import org.junit.jupiter.api.Test;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}