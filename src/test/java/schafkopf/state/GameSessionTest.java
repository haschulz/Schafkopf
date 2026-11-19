package schafkopf.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.Suit;
import schafkopf.ai.policy.PolicyGenome;

class GameSessionTest {
    @Test
    void botsBidAndHumanCanCompleteAWholeGameWithoutSeeingTheirHands() {
        Deck deck = new Deck();
        deck.shuffle(new Random(19003L));
        deck.cut(11);
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());

        GameSession.Snapshot bidding = session.snapshot(Suit.EICHEL);
        assertEquals(GameSession.Stage.BIDDING, bidding.stage());
        assertTrue(bidding.seatCalls().stream().filter(java.util.Objects::nonNull)
            .allMatch(call -> List.of("Spiele!", "Auch!!", "Weiter").contains(call)));
        assertEquals(8, bidding.hand().size());
        assertEquals(List.of(8, 8, 8, 8), bidding.seats().stream()
                .map(GameSession.SeatSnapshot::cardsRemaining).toList());
        assertEquals(0, bidding.seats().stream().mapToInt(GameSession.SeatSnapshot::eyes).sum());
        assertFalse(bidding.bidOptions().isEmpty());

        GameSession.BidOption bid = bidding.bidOptions().stream()
                .filter(GameSession.BidOption::enabled)
                .findFirst()
                .orElseThrow();
        GameSession.Snapshot state = session.announce(bid.id(), Suit.EICHEL);
        assertTrue(state.seatCalls().stream().filter(java.util.Objects::nonNull)
            .allMatch(call -> List.of("Spiele!", "Auch!!", "Weiter").contains(call)));

        int humanTurns = 0;
        boolean replayAvailable = false;
        while (state.stage() == GameSession.Stage.TRICK_PLAY) {
            assertTrue(state.yourTurn());
            assertFalse(state.legalCards().isEmpty());
            Card chosen = state.legalCards().get(0);
            state = session.playHumanCard(chosen, Suit.EICHEL);
            humanTurns++;
            if (state.lastCompletedTrick() != null) {
                assertEquals(4, state.lastCompletedTrick().cards().size());
                assertEquals(state.lastCompletedTrick().cards().stream()
                                .mapToInt(tableCard -> tableCard.card().points()).sum(),
                        state.lastCompletedTrick().points());
                replayAvailable = true;
            }
        }

        assertEquals(GameSession.Stage.FINISHED, state.stage());
        assertEquals(8, humanTurns);
        assertEquals(0, state.seats().stream().mapToInt(GameSession.SeatSnapshot::cardsRemaining).sum());
        assertEquals(120, state.seats().stream().mapToInt(GameSession.SeatSnapshot::eyes).sum());
        assertTrue(replayAvailable);
        assertNotNull(state.lastCompletedTrick());
        assertNotNull(state.resultSummary());
    }

    @Test
    void oneWenzToutCardAdvancesBotsOnlyToTheNextHumanTurn() {
        Deck deck = new Deck();
        deck.shuffle(new Random(1L));
        deck.cut(6);
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());

        GameSession.Snapshot state = session.announce("WENZ_TOUT", Suit.EICHEL);
        assertEquals(GameSession.Stage.TRICK_PLAY, state.stage());
        assertTrue(state.yourTurn());
        assertEquals(1, state.trickNumber());

        state = session.playHumanCard(state.legalCards().get(0), Suit.EICHEL);

        assertEquals(GameSession.Stage.TRICK_PLAY, state.stage());
        assertTrue(state.yourTurn());
        assertTrue(state.trickNumber() <= 2);
        assertEquals(7, state.hand().size());
    }

    @Test
    void farbwenzIsOfferedAndTheFirstCallIsSpiele() {
        Deck deck = new Deck();
        deck.shuffle(new Random(5L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), 3);

        GameSession.Snapshot bidding = session.snapshot(Suit.EICHEL);
        GameSession.BidOption farbwenz = bidding.bidOptions().stream()
                .filter(option -> option.id().equals("FARBWENZ_EICHEL"))
                .findFirst()
                .orElseThrow();
        assertTrue(farbwenz.enabled());

        GameSession.Snapshot state = session.announce(farbwenz.id(), Suit.EICHEL);
        assertEquals("Spiele!", state.seatCalls().get(3));
        assertFalse(state.message().contains("Solo"));
    }

    @Test
    void restoredSessionContinuesExactlyWhereItWasInterrupted() {
        for (long seed = 1; seed <= 12; seed++) {
            Deck deck = new Deck();
            deck.shuffle(new Random(seed));
            GameSession original = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), (int) (seed % 4));

            assertSameTable(original.snapshot(Suit.EICHEL), restore(original).snapshot(Suit.EICHEL));

            GameSession.Snapshot state = seed % 2 == 0
                    ? original.pass(Suit.EICHEL)
                    : original.announce(original.snapshot(Suit.EICHEL).bidOptions().stream()
                            .filter(GameSession.BidOption::enabled).findFirst()
                            .map(GameSession.BidOption::id).orElseThrow(), Suit.EICHEL);
            int turn = 0;
            while (state.stage() == GameSession.Stage.TRICK_PLAY) {
                GameSession restored = restore(original);
                assertSameTable(state, restored.snapshot(Suit.EICHEL));
                Card card = state.legalCards().get(turn++ % state.legalCards().size());
                state = original.playHumanCard(card, Suit.EICHEL);
                assertSameTable(state, restored.playHumanCard(card, Suit.EICHEL));
            }

            GameSession finished = restore(original);
            assertEquals(GameSession.Stage.FINISHED, finished.snapshot(Suit.EICHEL).stage());
            assertEquals(original.outcome(), finished.outcome());
        }
    }

    @Test
    void biddingStartsAtVorhandAndVorhandLeadsTheFirstTrick() {
        List<String> names = List.of("Sepp", "Hans", "Franz", "Du");
        for (int vorhand = 0; vorhand < 4; vorhand++) {
            Deck deck = new Deck();
            deck.shuffle(new Random(40L + vorhand));
            GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), vorhand);
            int humanPosition = (3 - vorhand + 4) % 4;

            List<String> callsBeforeHuman = session.snapshot(Suit.EICHEL).seatCalls();
            for (int position = 0; position < 4; position++) {
                int seat = (vorhand + position) % 4;
                assertEquals(position < humanPosition, callsBeforeHuman.get(seat) != null,
                        "Vorhand " + vorhand + ", seat " + seat);
            }

            GameSession.Snapshot state = session.pass(Suit.EICHEL);
            assertTrue(state.seatCalls().stream().allMatch(java.util.Objects::nonNull));
            List<GameSession.TableCard> firstCards = state.completedTricks().isEmpty()
                    ? state.tableCards() : state.completedTricks().get(0).cards();
            if (!firstCards.isEmpty()) {
                assertEquals(names.get(vorhand), firstCards.get(0).playerName());
            } else {
                assertEquals(3, vorhand);
                assertTrue(state.yourTurn());
            }
        }
        assertEquals(1, GameSession.cutterSeat(3));
        assertEquals(3, GameSession.cutterSeat(1));
    }

    @Test
    void corruptSavedGameIsRejected() {
        Deck deck = new Deck();
        deck.shuffle(new Random(3L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());
        session.pass(Suit.EICHEL);
        String saved = session.saveState().replace("version=1", "version=99");

        assertThrows(IllegalArgumentException.class, () -> GameSession.restore(saved, PolicyGenome.baseline()));
    }

    private static GameSession restore(GameSession session) {
        return GameSession.restore(session.saveState(), PolicyGenome.baseline());
    }

    private static void assertSameTable(GameSession.Snapshot expected, GameSession.Snapshot actual) {
        assertEquals(expected.stage(), actual.stage());
        assertEquals(expected.hand(), actual.hand());
        assertEquals(expected.legalCards(), actual.legalCards());
        assertEquals(expected.tableCards(), actual.tableCards());
        assertEquals(expected.completedTricks(), actual.completedTricks());
        assertEquals(expected.seats(), actual.seats());
        assertEquals(expected.seatCalls(), actual.seatCalls());
        assertEquals(expected.contractName(), actual.contractName());
        assertEquals(expected.bidOptions(), actual.bidOptions());
        assertEquals(expected.message(), actual.message());
    }
}