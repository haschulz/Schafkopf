package schafkopf.ai;

import org.junit.jupiter.api.Test;
import schafkopf.analysis.GameRecord;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.GameType;
import schafkopf.model.Suit;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionAnalysisTest {
    @Test
    void impossibleRufspielAndInvalidAnalysisRequestsNeverUseRealHiddenHands() {
        List<Card> eichelHand = java.util.Arrays.stream(schafkopf.model.Rank.values())
                .map(rank -> new Card(Suit.EICHEL, rank)).toList();
        var impossibleCall = new GameRecord.ContractSpec(GameType.RUFSPIEL, 0, Suit.EICHEL, false);
        var information = new DecisionInformation(List.of("A", "B", "C", "D"), 0,
                eichelHand, List.of(), impossibleCall, 0, SchafkopfConfig.defaults(), 1);
        BeliefSampler sampler = new BeliefSampler();
        var analysis = new DecisionAnalysis(DecisionAnalysis.opponentPool());
        List<schafkopf.ai.policy.PolicyGenome> noPolicies = List.of();
        List<GameRecord.ContractSpec> noCandidates = List.of();

        assertThrows(IllegalStateException.class, () -> sampler.sample(information, impossibleCall, 1, 1L));
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(information, impossibleCall, 0, 1L));
        assertThrows(IllegalArgumentException.class, () -> new DecisionAnalysis(noPolicies));
        assertThrows(IllegalArgumentException.class,
                () -> analysis.contracts(information, noCandidates, null, 2, 1L));
    }

    @Test
    void noHigherLegalBidMeansPassAndDoesNotReuseAnOldAnalysis() {
        var caller = new schafkopf.model.Player("1", "A");
        caller.setHand(new Deck().deal4x8().get(0));
        var information = new DecisionInformation(List.of("A", "B", "C", "D"), 0,
                caller.hand(), List.of(), null, 0, SchafkopfConfig.defaults(), 1);
        var advisor = new GameBiddingAdvisor(new schafkopf.rules.RuleEngine(),
                schafkopf.ai.policy.PolicyGenome.baseline());
        var standing = new schafkopf.model.SoloContract(new schafkopf.model.Player("2", "B"), Suit.GRAS, true);

        org.junit.jupiter.api.Assertions.assertNull(advisor.recommendWithAnalysis(caller,
                java.util.Set.of(GameType.FARBSOLO, GameType.WENZ, GameType.RUFSPIEL), information, standing, 2));
        org.junit.jupiter.api.Assertions.assertNull(advisor.lastAnalysis());
    }

    @Test
    void weakSoloWithoutObersIsPassedInsteadOfOvervaluingLowTrumps() {
        var caller = new schafkopf.model.Player("1", "A");
        caller.setHand(List.of(new Card(Suit.GRAS, schafkopf.model.Rank.SIEBEN),
                new Card(Suit.GRAS, schafkopf.model.Rank.ACHT), new Card(Suit.GRAS, schafkopf.model.Rank.NEUN),
                new Card(Suit.GRAS, schafkopf.model.Rank.ZEHN), new Card(Suit.HERZ, schafkopf.model.Rank.SIEBEN),
                new Card(Suit.HERZ, schafkopf.model.Rank.ACHT), new Card(Suit.EICHEL, schafkopf.model.Rank.SIEBEN),
                new Card(Suit.SCHELLEN, schafkopf.model.Rank.SIEBEN)));
        var information = new DecisionInformation(List.of("A", "B", "C", "D"), 0,
                caller.hand(), List.of(), null, 0, SchafkopfConfig.defaults(), 1);
        var advisor = new GameBiddingAdvisor(new schafkopf.rules.RuleEngine(),
                schafkopf.ai.policy.PolicyGenome.baseline());

        var decision = advisor.recommendWithAnalysis(caller, java.util.Set.of(GameType.FARBSOLO),
                information, null, 16);

        org.junit.jupiter.api.Assertions.assertNull(decision);
        assertEquals("PASS", advisor.lastAnalysis().selectedAction());
    }

    @Test
    void eachSupportedContractAndRoleGetsItsOwnLegalFinalCardDecision() {
        for (GameType type : List.of(GameType.RUFSPIEL, GameType.FARBSOLO, GameType.WENZ, GameType.FARBWENZ)) {
                        var hands = java.util.stream.IntStream.range(0, 4)
                                        .mapToObj(seat -> new ArrayList<Card>()).toList();
                        for (Suit suit : Suit.values()) {
                                for (schafkopf.model.Rank rank : schafkopf.model.Rank.values()) {
                                        hands.get(rank.ordinal() % 4).add(new Card(suit, rank));
                                }
                        }
            var contract = new GameRecord.ContractSpec(type, 0,
                    type == GameType.WENZ ? null : Suit.GRAS, false);
            for (int seat = 0; seat < 4; seat++) {
                var information = new DecisionInformation(List.of("A", "B", "C", "D"), seat,
                        hands.get(seat), List.of(), contract, seat, SchafkopfConfig.defaults(), 1);
                var analysis = new DecisionAnalysis(DecisionAnalysis.opponentPool());
                var report = analysis.cards(information, 2, 123L + seat);
                assertTrue(information.hand().stream()
                        .anyMatch(card -> card.shortName().equals(report.selectedAction())), type + "/seat=" + seat);
                assertTrue(report.alternatives().values().stream().allMatch(value ->
                        value.worlds() == 2 && Double.isFinite(value.expectedReturnCents())));
            }
        }
    }

        @Test
        void lateGameWorldsRespectEveryPublicPlayAndObservedActionsReweightBeliefs() {
                var players = java.util.stream.IntStream.range(0, 4)
                                .mapToObj(seat -> new schafkopf.model.Player(Integer.toString(seat + 1), "P" + seat)).toList();
                var rules = new schafkopf.rules.RuleEngine();
                var game = new schafkopf.state.SchafkopfGame(players, rules,
                                new SimpleBot(rules, true, schafkopf.ai.policy.PolicyGenome.baseline()));
                game.setLogger(null);
                game.dealRandom(319L);
                game.setupContract(new schafkopf.model.WenzContract(players.get(0)));
                game.startTrickPlay(players.get(0));
                for (int turn = 0; turn < 24; turn++) {
                        game.playBotTurn();
                }
                DecisionInformation information = game.decisionInformation();
                BeliefSampler sampler = new BeliefSampler();
                var worlds = sampler.sample(information, information.contract(), 12, 51L);
                var analysis = new DecisionAnalysis(DecisionAnalysis.opponentPool());
                double[] weights = analysis.weights(information, worlds);

                assertTrue(!worlds.isEmpty());
                for (int index = 0; index < worlds.size(); index++) {
                        var replay = sampler.replay(information, information.contract(), worlds.get(index), new SimpleBot(rules));
                        assertEquals(information.publicPlays().size(), replay.memory().allPlayedCards().size());
                        assertEquals(new java.util.HashSet<>(information.hand()),
                                        new java.util.HashSet<>(replay.currentPlayer().hand()));
                        assertTrue(weights[index] > 0 && weights[index] <= 1);
                }
                var report = analysis.cards(information, 12, 51L);
                assertTrue(game.legalMovesForCurrentPlayer().stream()
                                .anyMatch(card -> card.shortName().equals(report.selectedAction())));
        }

    @Test
    void analyticalBiddingKeepsSuitCandidatesSeparateAndStrictlyOverbidsStandingContract() {
        var player = new schafkopf.model.Player("1", "A");
        player.setHand(new Deck().deal4x8().get(0));
        var information = new DecisionInformation(List.of("A", "B", "C", "D"), 0,
                player.hand(), List.of(), null, 0, SchafkopfConfig.defaults(), 1);
        var traces = new ArrayList<schafkopf.ai.policy.DecisionTrace>();
        var advisor = new GameBiddingAdvisor(new schafkopf.rules.RuleEngine(),
                schafkopf.ai.policy.PolicyGenome.baseline(), traces::add,
                schafkopf.ai.policy.ContractValueModel.empty());
        var standing = new schafkopf.model.WenzContract(new schafkopf.model.Player("2", "B"));

        var choice = advisor.recommendWithAnalysis(player, java.util.Set.of(GameType.FARBSOLO, GameType.WENZ),
                information, standing, 3);

        assertTrue(choice == null || new schafkopf.rules.GameBiddingRules().canAnnounce(choice.contract(), standing));
        var options = advisor.lastAnalysis().alternatives();
        assertTrue(options.containsKey("FARBSOLO:HERZ"));
        assertTrue(options.containsKey("FARBSOLO:GRAS"));
        assertTrue(!options.containsKey("WENZ:NONE"));
        assertEquals(1, traces.size());
        assertEquals(options.keySet(), traces.get(0).actionScores().keySet());
    }

        @Test
        void realGameUsesAnalysisWithoutChangingItsPublicHistoryDuringEvaluation() {
                var players = java.util.stream.IntStream.range(0, 4)
                                .mapToObj(seat -> new schafkopf.model.Player(Integer.toString(seat), "P" + seat)).toList();
                var rules = new schafkopf.rules.RuleEngine();
                var game = new schafkopf.state.SchafkopfGame(players, rules,
                                new SimpleBot(rules, true, schafkopf.ai.policy.PolicyGenome.baseline()));
                game.setLogger(null);
                game.dealRandom(119L);
                game.setupContract(new schafkopf.model.WenzContract(players.get(0)));
                game.startTrickPlay(players.get(0));
                game.enableDecisionAnalysis(3);
                var before = game.decisionInformation();
                var legal = game.legalMovesForCurrentPlayer();

                var choice = game.playBotTurn();

                assertTrue(legal.contains(choice.card()));
                assertEquals(0, before.publicPlays().size());
                assertEquals(1, game.decisionInformation().publicPlays().size());
                assertEquals(choice.card().shortName(), game.lastDecisionAnalysis().selectedAction());
                assertTrue(choice.tacticalReasoning().contains("Informationsgerechte Analyse"));
                assertEquals(8, before.hand().size());
        }

    @Test
    void contractAnalysisComparesSameDealsAndIncludesTariffAndUncertainty() {
        DecisionInformation information = new DecisionInformation(List.of("A", "B", "C", "D"),
                0, new Deck().deal4x8().get(0), List.of(), null, 0, SchafkopfConfig.defaults(), 1);
        var contract = new GameRecord.ContractSpec(GameType.WENZ, 0, null, false);
        DecisionAnalysis analysis = new DecisionAnalysis(DecisionAnalysis.opponentPool());

        var first = analysis.contracts(information, List.of(contract), null, 3, 92L);
        var repeated = analysis.contracts(information, List.of(contract), null, 3, 92L);
        var value = first.alternatives().get("WENZ:NONE");
        DecisionInformation doubled = new DecisionInformation(information.playerNames(), 0,
                information.hand(), List.of(), null, 0, information.config(), 2);
        var twice = analysis.contracts(doubled, List.of(contract), null, 3, 92L);

        assertEquals(first, repeated);
        assertEquals(value.expectedReturnCents() * 2,
                twice.alternatives().get("WENZ:NONE").expectedReturnCents());
        assertEquals(3, value.worlds());
        assertEquals(3, first.opponentPolicies().size());
        assertTrue(value.probabilityLow() <= value.winProbability());
        assertTrue(value.probabilityHigh() >= value.winProbability());
        assertTrue(value.opponentEyes() >= 0 && value.opponentEyes() <= 120);
        assertTrue(first.alternatives().containsKey("PASS"));
    }

        @Test
        void sampledHandsPreserveObserverCardsAndAreReproducibleWithoutAnyRealOpponentHands() {
                DecisionInformation information = new DecisionInformation(List.of("A", "B", "C", "D"),
                                0, new Deck().deal4x8().get(0), List.of(),
                                new GameRecord.ContractSpec(GameType.WENZ, 0, null, false),
                                0, SchafkopfConfig.defaults(), 4);
                BeliefSampler sampler = new BeliefSampler();

                var worlds = sampler.sample(information, information.contract(), 4, 17L);

                assertEquals(worlds, sampler.sample(information, information.contract(), 4, 17L));
                assertEquals(4, worlds.size());
                for (var world : worlds) {
                        assertEquals(information.initialHand(), world.get(0));
                        assertEquals(32, world.stream().flatMap(List::stream).distinct().count());
                        assertTrue(world.stream().allMatch(hand -> hand.size() == 8));
                        assertEquals(4, sampler.replay(information, information.contract(), world,
                                        new SimpleBot(new schafkopf.rules.RuleEngine())).bettingState().multiplier());
                }
        }

    @Test
    void callerDecisionDoesNotDependOnWhichOpponentActuallyHoldsCalledAce() {
        var caller = new schafkopf.model.Player("1", "A");
        var second = new schafkopf.model.Player("2", "B");
        var third = new schafkopf.model.Player("3", "C");
        var fourth = new schafkopf.model.Player("4", "D");
        var rules = new schafkopf.rules.RuleEngine();
        var contract = new schafkopf.model.RufspielContract(caller, Suit.GRAS);
        caller.setHand(new Deck().deal4x8().get(0));
        var memory = new GameMemory(List.of(caller, second, third, fourth), rules, contract);
        var bot = new SimpleBot(rules, true, schafkopf.ai.policy.PolicyGenome.baseline());
        var trick = new schafkopf.model.Trick(1, caller);
        second.setHand(List.of(contract.calledSau()));
        var firstChoice = bot.chooseFromPublicInformation(caller, trick, contract, false,
                1, memory, List.of(second, third, fourth));
        second.setHand(List.of());
        third.setHand(List.of(contract.calledSau()));
        var secondChoice = bot.chooseFromPublicInformation(caller, trick, contract, false,
                1, memory, List.of(second, third, fourth));

        assertEquals(firstChoice.card(), secondChoice.card());
        assertTrue(caller.hasCard(firstChoice.card()));
    }

    @Test
    void decisionInformationContainsOnlyOwnCardsAndPublicHistoryAndIsImmutable() {
        List<Card> hand = new ArrayList<>(new Deck().deal4x8().get(0));
        Card played = hand.remove(0);
        List<GameRecord.Play> history = new ArrayList<>(List.of(new GameRecord.Play(0, played)));
        DecisionInformation information = new DecisionInformation(List.of("A", "B", "C", "D"),
                0, hand, history, new GameRecord.ContractSpec(GameType.WENZ, 0, null, false),
                0, SchafkopfConfig.defaults(), 1);
        hand.clear();
        history.clear();

        assertEquals(7, information.hand().size());
        assertEquals(8, information.initialHand().size());
        assertEquals(1, information.publicPlays().size());
        List<Card> immutableHand = information.hand();
        List<Card> initialHand = information.initialHand();
        List<String> names = List.of("A", "B", "C", "D");
        var contract = new GameRecord.ContractSpec(GameType.FARBSOLO, 0, Suit.HERZ, false);
        var config = SchafkopfConfig.defaults();
        List<GameRecord.Play> noPlays = List.of();
        assertThrows(UnsupportedOperationException.class, immutableHand::clear);
        assertThrows(IllegalArgumentException.class, () -> new DecisionInformation(
                names, 4, initialHand, noPlays, contract, 0, config, 1));
    }
}