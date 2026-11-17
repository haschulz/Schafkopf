package schafkopf.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import schafkopf.model.Card;
import schafkopf.model.BettingState;
import schafkopf.model.Deck;
import schafkopf.model.GameType;
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
            .allMatch(call -> List.of("Spiele!", "Auch!", "Weiter").contains(call)));
        assertEquals(8, bidding.hand().size());
        assertEquals(List.of(8, 8, 8, 8), bidding.seats().stream()
                .map(GameSession.SeatSnapshot::cardsRemaining).toList());
        assertEquals(0, bidding.seats().stream().mapToInt(GameSession.SeatSnapshot::eyes).sum());
        assertFalse(bidding.bidOptions().isEmpty());

        GameSession.BidOption bid = bidding.bidOptions().stream()
                .filter(GameSession.BidOption::enabled)
            .filter(GameSession.BidOption::implemented)
                .findFirst()
                .orElseThrow();
        GameSession.Snapshot state = continueBettingWindows(session,
            session.announce(bid.id(), Suit.EICHEL));
        assertTrue(state.seatCalls().stream().filter(java.util.Objects::nonNull)
            .allMatch(call -> List.of("Spiele!", "Auch!", "Weiter").contains(call)));

        int humanTurns = 0;
        boolean replayAvailable = false;
        while (state.stage() == GameSession.Stage.TRICK_PLAY) {
            if (state.bettingWindowOpen()) {
                state = session.continueAfterBettingWindow(Suit.EICHEL);
                continue;
            }
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
        int callerSeat = session.finishedGame().gameContract().caller().id().equals("1") ? 0
            : session.finishedGame().gameContract().caller().id().equals("2") ? 1
            : session.finishedGame().gameContract().caller().id().equals("3") ? 2 : 3;
        assertEquals(callerSeat, state.contractCallerSeat());
        GameSession.Outcome outcome = session.outcome();
        assertEquals(120, outcome.seatOutcomes().stream().mapToInt(GameSession.SeatOutcome::eyes).sum());
        for (int seat = 0; seat < 4; seat++) {
            var player = session.finishedGame().players().get(seat);
            assertEquals(player.totalAugen(), outcome.seatOutcomes().get(seat).eyes());
            boolean callerSide = session.finishedGame().result().callerTeam().contains(player);
            assertEquals(callerSide == session.finishedGame().result().callerTeamWon(),
                outcome.seatOutcomes().get(seat).won());
        }
    }

    @Test
    void oneWenzToutCardAdvancesBotsOnlyToTheNextHumanTurn() {
        Deck deck = new Deck();
        deck.shuffle(new Random(1L));
        deck.cut(6);
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());

        GameSession.Snapshot state = continueBettingWindows(session,
            session.announce("WENZ_TOUT", Suit.EICHEL));
        assertEquals(GameSession.Stage.TRICK_PLAY, state.stage());
        assertTrue(state.yourTurn());
        assertEquals(1, state.trickNumber());

        state = session.playHumanCard(state.legalCards().get(0), Suit.EICHEL);
        state = continueBettingWindows(session, state);

        assertEquals(GameSession.Stage.TRICK_PLAY, state.stage());
        assertTrue(state.yourTurn());
        assertTrue(state.trickNumber() <= 2);
        assertEquals(7, state.hand().size());
    }

    @Test
    void offersOnlyApprovedGameTypesAndToutVariants() {
        Deck deck = new Deck();
        deck.shuffle(new Random(19004L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());

        List<GameSession.BidOption> options = session.snapshot(Suit.EICHEL).bidOptions();
        List<String> optionIds = options.stream().filter(GameSession.BidOption::implemented)
                .map(GameSession.BidOption::id)
                .toList();

        assertTrue(optionIds.containsAll(List.of(
                "SOLO_TOUT_EICHEL", "WENZ_TOUT", "SOLO_EICHEL", "WENZ", "FARBWENZ_EICHEL")));
        assertTrue(optionIds.stream().allMatch(optionId -> optionId.matches(
                "(SOLO_TOUT_EICHEL|WENZ_TOUT|SOLO_EICHEL|WENZ|FARBWENZ_EICHEL|"
                        + "SAUSPIEL(_(EICHEL|GRAS|HERZ|SCHELLEN))?)")));
        assertEquals(List.of("GEIER", "BETTEL", "RAMSCH", "KREUZBOCK"), options.stream()
            .filter(option -> !option.implemented()).map(GameSession.BidOption::id).toList());
        assertTrue(options.stream().filter(option -> !option.implemented()).allMatch(GameSession.BidOption::enabled));
        assertThrows(IllegalArgumentException.class, () -> session.announce("GEIER", Suit.EICHEL));
        assertEquals(GameSession.Stage.BIDDING, session.snapshot(Suit.EICHEL).stage());
    }

        @Test
        void selectingHerzSoloCreatesFarbsoloWithHerzTrump() {
        Deck deck = new Deck();
        deck.shuffle(new Random(19010L));
        PolicyGenome quietGenome = new PolicyGenome(1, "quiet-bidding",
            java.util.Map.of("klopfen.threshold", Double.MAX_VALUE));
        GameSession session = new GameSession(deck.deal4x8(), quietGenome, 3);

        GameSession.BidOption herzSolo = session.snapshot(Suit.HERZ).bidOptions().stream()
            .filter(option -> option.id().equals("SOLO_HERZ"))
            .findFirst()
            .orElseThrow();
        assertTrue(herzSolo.enabled());

        GameSession.Snapshot state = continueBettingWindows(
            session, session.announce(herzSolo.id(), Suit.HERZ));

        assertEquals(GameType.FARBSOLO, state.contractType());
        assertEquals(Suit.HERZ, state.contractSuit());
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

        GameSession.Snapshot call = session.prepareHumanPlay(Suit.EICHEL);
        assertEquals("Spiele!", call.bidMessages().get(call.bidMessages().size() - 1).text());
        GameSession.Snapshot state = session.announce(farbwenz.id(), Suit.EICHEL);
        assertEquals("Spiele!", state.seatCalls().get(3));
        assertFalse(state.message().contains("Solo"));
        assertEquals(3, state.bidMessages().get(0).seat());
        assertEquals("Spiele!", state.bidMessages().get(0).text());
        assertTrue(state.bidMessages().get(1).text().contains("Farbwenz"));
        assertEquals(state.bidMessages(), GameSession.restore(session.saveState(), PolicyGenome.baseline())
            .snapshot(Suit.EICHEL).bidMessages());
    }

    @Test
    void cancelingGameSelectionRemovesOnlyTheUnconfirmedHumanCall() {
        Deck deck = new Deck();
        deck.shuffle(new Random(19005L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), 3);
        GameSession.Snapshot before = session.snapshot(Suit.EICHEL);

        session.prepareHumanPlay(Suit.EICHEL);
        GameSession restored = restore(session);
        assertTrue(restored.snapshot(Suit.EICHEL).humanCallPending());
        GameSession.Snapshot canceled = restored.cancelHumanPlay(Suit.EICHEL);

        assertEquals(GameSession.Stage.BIDDING, canceled.stage());
        assertEquals(before.seatCalls(), canceled.seatCalls());
        assertEquals(before.bidMessages(), canceled.bidMessages());
        assertEquals(before.bidOptions(), canceled.bidOptions());
        assertEquals("Weiter", restored.pass(Suit.EICHEL).seatCalls().get(3));
    }

    @Test
    void allPassWaitsForAnExplicitNoGamePolicy() {
        Deck deck = new Deck();
        deck.shuffle(new Random(19006L));
        PolicyGenome quietGenome = new PolicyGenome(1, "quiet-bidding",
                java.util.Map.of("klopfen.threshold", Double.MAX_VALUE));
        GameSession session = new GameSession(deck.deal4x8(), quietGenome, 3);

        GameSession.Snapshot state = session.pass(Suit.EICHEL);

        assertEquals(GameSession.Stage.BIDDING, state.stage());
        assertTrue(state.awaitingNoGameChoice());
        assertNull(state.contractType());
        assertTrue(GameSession.restore(session.saveState(), quietGenome)
            .snapshot(Suit.EICHEL).awaitingNoGameChoice());
        assertEquals(GameSession.NoGameSelectionResult.NOT_IMPLEMENTED,
            session.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.RAMSCH));
        assertTrue(session.snapshot(Suit.EICHEL).awaitingNoGameChoice());
        assertEquals(GameSession.NoGameSelectionResult.STOCK_ADDED,
            session.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.STOCK));
        assertEquals(1, session.stockDoubles());

        Deck throwInDeck = new Deck();
        throwInDeck.shuffle(new Random(19007L));
        GameSession throwIn = new GameSession(throwInDeck.deal4x8(), quietGenome, 3);
        throwIn.pass(Suit.EICHEL);
        assertEquals(GameSession.NoGameSelectionResult.ZAMMWERFEN,
            throwIn.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.ZAMMWERFEN));
        assertEquals(0, throwIn.stockDoubles());

        Deck maxStockDeck = new Deck();
        maxStockDeck.shuffle(new Random(19008L));
        GameSession maxStock = new GameSession(maxStockDeck.deal4x8(), quietGenome, 3, false, 5);
        maxStock.pass(Suit.EICHEL);
        assertEquals(GameSession.NoGameSelectionResult.STOCK_MAXED,
            maxStock.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.STOCK));
        assertEquals(5, maxStock.stockDoubles());

        Deck reachingMaxDeck = new Deck();
        reachingMaxDeck.shuffle(new Random(19009L));
        GameSession reachingMax = new GameSession(reachingMaxDeck.deal4x8(), quietGenome, 3, false, 4);
        reachingMax.pass(Suit.EICHEL);
        assertEquals(GameSession.NoGameSelectionResult.STOCK_MAXED,
            reachingMax.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.STOCK));
        assertEquals(5, reachingMax.stockDoubles());
    }

    @Test
    void restoredSessionContinuesExactlyWhereItWasInterrupted() {
        for (long seed = 1; seed <= 12; seed++) {
            Deck deck = new Deck();
            deck.shuffle(new Random(seed));
            GameSession original = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), (int) (seed % 4));

            assertSameTable(original.snapshot(Suit.EICHEL), restore(original).snapshot(Suit.EICHEL));

            GameSession.Snapshot state = continueBettingWindows(original,
                    original.announce(original.snapshot(Suit.EICHEL).bidOptions().stream()
                            .filter(GameSession.BidOption::enabled)
                            .filter(GameSession.BidOption::implemented)
                            .findFirst().map(GameSession.BidOption::id).orElseThrow(), Suit.EICHEL));
            int turn = 0;
            while (state.stage() == GameSession.Stage.TRICK_PLAY) {
                if (state.bettingWindowOpen()) {
                    state = original.continueAfterBettingWindow(Suit.EICHEL);
                    assertSameTable(state, restore(original).snapshot(Suit.EICHEL));
                    continue;
                }
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
            if (state.awaitingNoGameChoice()) {
                assertEquals(GameSession.Stage.BIDDING, state.stage());
                assertEquals(GameSession.NoGameSelectionResult.STOCK_ADDED,
                        session.selectNoGamePolicy(schafkopf.config.SchafkopfConfig.NoGamePolicy.STOCK));
                continue;
            }
            state = continueBettingWindows(session, state);
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
    void humanKlopfenDoublesTheStakeAndSurvivesRestore() {
        Deck deck = new Deck();
        deck.shuffle(new Random(731L));
        PolicyGenome quietGenome = PolicyGenome.baseline().withWeight("klopfen.threshold", 100.0);
        GameSession session = new GameSession(deck.deal4x8(), quietGenome, 3, true);

        assertEquals(2, session.stakeMultiplier());
        assertEquals(2, GameSession.restore(session.saveState(), quietGenome).stakeMultiplier());
    }

    @Test
    void restoredSessionKeepsStockAndKlopferSeparate() {
        Deck deck = new Deck();
        deck.shuffle(new Random(732L));
        PolicyGenome quietGenome = PolicyGenome.baseline().withWeight("klopfen.threshold", 100.0);
        GameSession session = new GameSession(deck.deal4x8(), quietGenome, 3, true, 4);

        GameSession.Snapshot restored = GameSession.restore(session.saveState(), quietGenome)
                .snapshot(Suit.EICHEL);

        assertEquals(16, restored.stake().stockFactor());
        assertEquals(4, restored.stake().stockDoubles());
        assertEquals(List.of("4"), restored.stake().klopferIds());
        assertEquals(32, restored.stake().totalMultiplier());
    }

    @Test
    void legacySessionMultiplierIsRestoredAsUnattributedKlopfen() {
        Deck deck = new Deck();
        deck.shuffle(new Random(735L));
        PolicyGenome quietGenome = PolicyGenome.baseline().withWeight("klopfen.threshold", 100.0);
        GameSession session = new GameSession(deck.deal4x8(), quietGenome, 3, true);
        String legacyState = session.saveState().replace("version=2", "version=1");

        BettingState.StakeBreakdown restored = GameSession.restore(legacyState, quietGenome)
                .stakeBreakdown();

        assertEquals(1, restored.additionalKlopfer());
        assertEquals(1, restored.klopferCount());
        assertEquals(1, restored.stockFactor());
        assertEquals(2, restored.totalMultiplier());
    }

    @Test
    void kontraAndRetourAreRoleAndCardWindowBound() {
        Deck deck = new Deck();
        deck.shuffle(new Random(733L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), 1);
        GameSession.Snapshot state = session.announce("WENZ_TOUT", Suit.EICHEL);

        assertTrue(state.bettingWindowOpen());
        assertTrue(session.canCallKontra(1));
        assertFalse(session.canCallKontra(session.humanSeat()));
        assertFalse(session.canCallRetour(session.humanSeat()));
        assertTrue(session.callKontra(1));
        assertFalse(session.canCallRetour(session.humanSeat()));
        state = session.snapshot(Suit.EICHEL);
        while (state.bettingWindowOpen() && !session.canCallRetour(session.humanSeat())) {
            state = session.continueAfterBettingWindow(Suit.EICHEL);
        }
        assertTrue(session.canCallRetour(session.humanSeat()));
        assertFalse(session.canCallRetour(2));
        assertTrue(session.callRetour(session.humanSeat()));
        assertFalse(session.canCallKontra(1));
        assertEquals(4, session.stakeMultiplier());
        assertTrue(session.stakeBreakdown().kontra());
        assertTrue(session.stakeBreakdown().retour());
    }

    @Test
    void bettingCallsCloseBeforeTheSecondCardIsPlayed() {
        Deck deck = new Deck();
        deck.shuffle(new Random(734L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), 0);
        session.announce("WENZ_TOUT", Suit.EICHEL);

        GameSession.Snapshot state = session.continueAfterBettingWindow(Suit.EICHEL);
        assertTrue(state.bettingWindowOpen());
        state = session.continueAfterBettingWindow(Suit.EICHEL);

        assertFalse(state.bettingWindowOpen());
        assertFalse(session.canCallKontra(1));
        assertFalse(session.canCallRetour(session.humanSeat()));
    }

    @Test
    void secondKontraWindowOpensWhenHumanFollowsTheFirstCard() {
        Deck deck = new Deck();
        deck.shuffle(new Random(736L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline(), 2);
        session.announce("WENZ_TOUT", Suit.EICHEL);

        GameSession.Snapshot state = session.continueAfterBettingWindow(Suit.EICHEL);

        assertEquals(1, state.tableCards().size());
        assertTrue(state.yourTurn());
        assertTrue(state.bettingWindowOpen());
    }

    @Test
    void corruptSavedGameIsRejected() {
        Deck deck = new Deck();
        deck.shuffle(new Random(3L));
        GameSession session = new GameSession(deck.deal4x8(), PolicyGenome.baseline());
        session.pass(Suit.EICHEL);
        String saved = session.saveState().replace("version=2", "version=99");
        PolicyGenome genome = PolicyGenome.baseline();

        assertThrows(IllegalArgumentException.class, () -> GameSession.restore(saved, genome));
    }

    private static GameSession restore(GameSession session) {
        return GameSession.restore(session.saveState(), PolicyGenome.baseline());
    }

    private static GameSession.Snapshot continueBettingWindows(
            GameSession session, GameSession.Snapshot state
    ) {
        while (state.bettingWindowOpen()) {
            state = session.continueAfterBettingWindow(Suit.EICHEL);
        }
        return state;
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
        assertEquals(expected.contractCallerSeat(), actual.contractCallerSeat());
        assertEquals(expected.humanCallPending(), actual.humanCallPending());
        assertEquals(expected.awaitingNoGameChoice(), actual.awaitingNoGameChoice());
        assertEquals(expected.stake(), actual.stake());
        assertEquals(expected.bettingWindowOpen(), actual.bettingWindowOpen());
    }
}