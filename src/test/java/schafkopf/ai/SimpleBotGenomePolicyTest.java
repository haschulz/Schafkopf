package schafkopf.ai;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.model.Card;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;
import schafkopf.model.WenzContract;
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;

import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleBotGenomePolicyTest {
    private final RuleEngine rules = new RuleEngine();

        @Test
        void defenderOpeningUsesLongSuitAgainstNextCallerAndShortSuitAgainstLastCaller() {
                Player defender = new Player("1", "A");
                Player caller = new Player("2", "B");
                Player third = new Player("3", "C");
                Player fourth = new Player("4", "D");
                Card longLead = new Card(Suit.EICHEL, Rank.SIEBEN);
                Card shortLead = new Card(Suit.GRAS, Rank.SIEBEN);
                defender.setHand(List.of(longLead, new Card(Suit.EICHEL, Rank.ACHT), shortLead));
                WenzContract contract = new WenzContract(caller);
                SimpleBot bot = new SimpleBot(rules, true, PolicyGenome.baseline());
                var next = bot.chooseMove(defender, new Trick(1, defender), contract, List.of(defender, third, fourth),
                                1, null, new GameMemory(List.of(defender, caller, third, fourth), rules, contract),
                                List.of(caller, third, fourth));
                var last = bot.chooseMove(defender, new Trick(1, defender), contract, List.of(defender, third, fourth),
                                1, null, new GameMemory(List.of(defender, third, fourth, caller), rules, contract),
                                List.of(third, fourth, caller));

                assertEquals(longLead, next.card());
                assertEquals(shortLead, last.card());
        }

        @Test
        void soloistCanConcealOwnTopOberByLeadingTheSafeGrasOber() {
                Player caller = new Player("1", "Sepp");
                Player defender = new Player("2", "Hans");
                Card grasOber = new Card(Suit.GRAS, Rank.OBER);
                caller.setHand(List.of(new Card(Suit.EICHEL, Rank.OBER), grasOber,
                                new Card(Suit.EICHEL, Rank.SIEBEN)));
                SoloContract contract = new SoloContract(caller, Suit.HERZ);
                GameMemory memory = new GameMemory(List.of(caller, defender), rules, contract);

                MoveExplanation move = new SimpleBot(rules, true, PolicyGenome.baseline())
                                .chooseMove(caller, new Trick(1, caller), contract, List.of(caller), 1,
                                                null, memory, List.of(defender));

                assertEquals(grasOber, move.card());
                assertTrue(move.tacticalReasoning().contains("Untertreibt"));
                assertTrue(move.tacticalReasoning().contains("unsicher"));
        }

        @Test
        void wenzDefenderSmearsShortTenWhenPartnerTrickIsUnbeatable() {
                Player caller = new Player("1", "Hans");
                Player partner = new Player("2", "Franz");
                Player defender = new Player("3", "Sepp");
                Player last = new Player("4", "Du");
                Card ten = new Card(Suit.EICHEL, Rank.ZEHN);
                defender.setHand(List.of(ten, new Card(Suit.GRAS, Rank.SIEBEN)));
                WenzContract contract = new WenzContract(caller);
                Trick trick = new Trick(1, partner);
                GameMemory memory = new GameMemory(List.of(caller, partner, defender, last), rules, contract);
                Card topTrump = new Card(Suit.EICHEL, Rank.UNTER);
                memory.recordCardPlayed(partner, topTrump, trick, contract);
                trick.addCard(partner, topTrump);

                MoveExplanation move = new SimpleBot(rules, true, PolicyGenome.baseline())
                                .chooseMove(defender, trick, contract, List.of(partner, defender, last), 1,
                                                null, memory, List.of(last, caller));

                assertEquals(ten, move.card());
                assertTrue(move.tacticalReasoning().contains("sicheren Partnerstich"));
        }

        @Test
        void activeGenomeDoesNotLeadLowestTrumpFromObservedWeakGrassSoloHand() {
                Player caller = new Player("1", "Sepp");
                Card highestTrump = new Card(Suit.SCHELLEN, Rank.UNTER);
                caller.setHand(List.of(
                                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.HERZ, Rank.KOENIG),
                                new Card(Suit.GRAS, Rank.ZEHN), new Card(Suit.HERZ, Rank.NEUN),
                                new Card(Suit.EICHEL, Rank.ZEHN), new Card(Suit.GRAS, Rank.SAU),
                                highestTrump, new Card(Suit.EICHEL, Rank.SAU)));
                SoloContract contract = new SoloContract(caller, Suit.GRAS);
                PolicyGenome activeGenome = PolicyGenome.load(Path.of("config/policy-genome.properties"));

                MoveExplanation move = new SimpleBot(rules, true, activeGenome)
                                .chooseMove(caller, new Trick(1, caller), contract, List.of(caller), 1);

                assertEquals(highestTrump, move.card());
        }

        @Test
        void soloDefenderLeadsNonTrumpWhenOneIsAvailable() {
                Player caller = new Player("1", "Hans");
                Player defender = new Player("2", "Franz");
                Card sideSuitCard = new Card(Suit.EICHEL, Rank.SIEBEN);
                defender.setHand(List.of(new Card(Suit.GRAS, Rank.SIEBEN), sideSuitCard));
                SoloContract contract = new SoloContract(caller, Suit.GRAS);
                PolicyGenome activeGenome = PolicyGenome.load(Path.of("config/policy-genome.properties"));

                MoveExplanation move = new SimpleBot(rules, true, activeGenome)
                                .chooseMove(defender, new Trick(1, defender), contract, List.of(caller), 1);

                assertEquals(sideSuitCard, move.card());
        }

    @Test
    void genomeCanChangeFarbsoloLeadFromHighestTrumpToHighPointSau() {
        Player caller = new Player("1", "Sepp");
        Card highestTrump = new Card(Suit.EICHEL, Rank.OBER);
        Card ace = new Card(Suit.EICHEL, Rank.SAU);
        caller.setHand(List.of(highestTrump, ace, new Card(Suit.GRAS, Rank.SIEBEN)));
        SoloContract contract = new SoloContract(caller, Suit.EICHEL);
        Trick trick = new Trick(1, caller);
        List<Player> team = List.of(caller);

        MoveExplanation defaultMove = new SimpleBot(rules, true, PolicyGenome.baseline())
                .chooseMove(caller, trick, contract, team, 1);
        PolicyGenome pointSeekingGenome = PolicyGenome.baseline()
                .withWeight("card.FARBSOLO.recommendation", 0.0)
                .withWeight("card.FARBSOLO.points", 10.0);
        MoveExplanation evolvedMove = new SimpleBot(rules, true, pointSeekingGenome)
                .chooseMove(caller, trick, contract, team, 1);

        assertEquals(highestTrump, defaultMove.card());
        assertEquals(ace, evolvedMove.card());
        assertTrue(rules.getLegalMovesForContract(caller, trick, contract, false, 1).contains(evolvedMove.card()));
    }

    @Test
    void genomeCanChangeRufspielLeadWithoutBypassingLegalMoves() {
        Player caller = new Player("1", "Sepp");
        Player defender = new Player("2", "Hans");
        Player partner = new Player("3", "Franz");
        Player otherDefender = new Player("4", "Michl");
        List<Player> players = List.of(caller, defender, partner, otherDefender);
        Card highestTrump = new Card(Suit.EICHEL, Rank.OBER);
        Card ace = new Card(Suit.EICHEL, Rank.SAU);
        caller.setHand(List.of(highestTrump, ace, new Card(Suit.SCHELLEN, Rank.SIEBEN)));
        partner.setHand(List.of(new Card(Suit.GRAS, Rank.SAU)));
        RufspielContract contract = new RufspielContract(caller, Suit.GRAS);
        Trick trick = new Trick(1, caller);
        GameMemory memory = new GameMemory(players, rules);

        MoveExplanation move = new SimpleBot(rules, true, PolicyGenome.baseline()
                .withWeight("card.RUFSPIEL.recommendation", 0.0)
                .withWeight("card.RUFSPIEL.points", 10.0))
                .chooseMove(caller, trick, contract, partner, false, 1, memory, List.of(defender, partner, otherDefender));

        List<Card> legalMoves = rules.getLegalMoves(caller, trick, contract, false, 1);
        assertEquals(ace, move.card());
        assertTrue(legalMoves.contains(move.card()));
    }

    @Test
    void farbwenzCardGenomeCanChangeThePostDeclarationPlay() {
        Player caller = new Player("1", "Sepp");
        Card highestTrump = new Card(Suit.EICHEL, Rank.UNTER);
        Card highPointTrump = new Card(Suit.EICHEL, Rank.SAU);
        caller.setHand(List.of(highestTrump, highPointTrump,
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.SCHELLEN, Rank.ACHT)));
        WenzContract contract = new WenzContract(caller, Suit.EICHEL);
        Trick trick = new Trick(1, caller);
        List<Player> team = List.of(caller);

        MoveExplanation defaultMove = new SimpleBot(rules, true, PolicyGenome.baseline())
                .chooseMove(caller, trick, contract, team, 1);
        PolicyGenome pointSeekingGenome = PolicyGenome.baseline()
                .withWeight("card.FARBWENZ.recommendation", 0.0)
                .withWeight("card.FARBWENZ.points", 20.0)
                .withWeight("card.FARBWENZ.farbwenzTrumpStrength", 0.0)
                .withWeight("card.FARBWENZ.farbwenzSoloistLeadTrump", 0.0);
        MoveExplanation changedMove = new SimpleBot(rules, true, pointSeekingGenome)
                .chooseMove(caller, trick, contract, team, 1);

        assertEquals(highestTrump, defaultMove.card());
        assertEquals(highPointTrump, changedMove.card());
        assertTrue(rules.getLegalMovesForContract(caller, trick, contract, false, 1)
                .contains(changedMove.card()));
    }

    @Test
    void genericCardWeightsApplyWhenWenzSpecificWeightIsMissing() {
        Player caller = new Player("1", "Sepp");
        Player leader = new Player("2", "Hans");
        Player follower = new Player("3", "Franz");
        follower.setHand(List.of(new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.SIEBEN)));
        Trick trick = new Trick(1, leader);
        trick.addCard(leader, new Card(Suit.GRAS, Rank.KOENIG));
        WenzContract contract = new WenzContract(caller);
        PolicyGenome genericWinningWeight = PolicyGenome.baseline()
                .withWeight("card.WENZ.recommendation", 0.0)
                .withWeight("card.WENZ.points", 0.0)
                .withWeight("card.points", 0.0)
                .withWeight("card.winningTrick", 100.0);

        MoveExplanation move = new SimpleBot(rules, true, genericWinningWeight)
                .chooseMove(follower, trick, contract, List.of(caller), 1);

        assertEquals(new Card(Suit.GRAS, Rank.SAU), move.card());
    }

    @Test
    void contractSpecificCardWeightOverridesGenericFallback() {
        Player caller = new Player("1", "Sepp");
        Player leader = new Player("2", "Hans");
        Player follower = new Player("3", "Franz");
        follower.setHand(List.of(new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.SIEBEN)));
        Trick trick = new Trick(1, leader);
        trick.addCard(leader, new Card(Suit.GRAS, Rank.KOENIG));
        WenzContract contract = new WenzContract(caller);
        PolicyGenome specificOverride = PolicyGenome.baseline()
                .withWeight("card.WENZ.recommendation", 0.0)
                .withWeight("card.WENZ.points", 0.0)
                .withWeight("card.points", 0.0)
                .withWeight("card.winningTrick", 100.0)
                .withWeight("card.WENZ.winningTrick", -100.0);

        MoveExplanation move = new SimpleBot(rules, true, specificOverride)
                .chooseMove(follower, trick, contract, List.of(caller), 1);

        assertEquals(new Card(Suit.GRAS, Rank.SIEBEN), move.card());
    }

        @Test
        void roleSpecificCardWeightOverridesContractAndGlobalWeights() {
                Player caller = new Player("1", "Sepp");
                Player leader = new Player("2", "Hans");
                Player defender = new Player("3", "Franz");
                defender.setHand(List.of(new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.SIEBEN)));
                Trick trick = new Trick(1, leader);
                trick.addCard(leader, new Card(Suit.GRAS, Rank.KOENIG));
                WenzContract contract = new WenzContract(caller);
                PolicyGenome rolePolicy = PolicyGenome.baseline()
                                .withWeight("card.WENZ.recommendation", 0.0)
                                .withWeight("card.WENZ.points", 0.0)
                                .withWeight("card.points", 0.0)
                                .withWeight("card.winningTrick", 100.0)
                                .withWeight("card.WENZ.DEFENDER.winningTrick", -100.0);

                MoveExplanation move = new SimpleBot(rules, true, rolePolicy)
                                .chooseMove(defender, trick, contract, List.of(caller), 1);

                assertEquals(new Card(Suit.GRAS, Rank.SIEBEN), move.card());
        }

        @Test
        void emitsDecisionTraceWithLegalActionsScoresAndSelectedCard() {
                Player caller = new Player("1", "Sepp");
                Player partner = new Player("2", "Franz");
                Player defender = new Player("3", "Hans");
                Player otherDefender = new Player("4", "Michl");
                List<Player> players = List.of(caller, partner, defender, otherDefender);
                Card highestTrump = new Card(Suit.EICHEL, Rank.OBER);
                Card ace = new Card(Suit.EICHEL, Rank.SAU);
                caller.setHand(List.of(highestTrump, ace, new Card(Suit.SCHELLEN, Rank.SIEBEN)));
                partner.setHand(List.of(new Card(Suit.GRAS, Rank.SAU)));
                RufspielContract contract = new RufspielContract(caller, Suit.GRAS);
                Trick trick = new Trick(1, caller);
                List<DecisionTrace> events = new ArrayList<>();
                GameMemory memory = new GameMemory(players, rules);

                MoveExplanation move = new SimpleBot(rules, true, PolicyGenome.baseline(), events::add)
                                .chooseMove(caller, trick, contract, partner, false, 1, memory, List.of(partner, defender, otherDefender));

                assertEquals(1, events.size());
                DecisionTrace event = events.get(0);
                assertEquals(DecisionTrace.Stage.CARD_PLAY, event.stage());
                assertEquals(move.card().shortName(), event.selectedAction());
                assertTrue(event.legalActions().contains(move.card().shortName()));
                assertTrue(event.actionScores().containsKey(move.card().shortName()));
                assertTrue(event.featuresByAction().containsKey(move.card().shortName()));
                assertTrue(move.tacticalReasoning().contains("Genom bestätigt"));
                assertTrue(move.tacticalReasoning().contains("Heuristikvorschlag"));
        }
}