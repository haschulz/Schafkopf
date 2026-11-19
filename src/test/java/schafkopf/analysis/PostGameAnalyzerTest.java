package schafkopf.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.Player;
import schafkopf.rules.RuleEngine;
import schafkopf.state.Phase;
import schafkopf.state.SchafkopfGame;

class PostGameAnalyzerTest {
    private final RuleEngine rules = new RuleEngine();
    private final SimpleBot bot = new SimpleBot(rules, true, PolicyGenome.baseline());

    private SchafkopfGame playedGame(long seed) {
        List<Player> players = List.of(new Player("1", "Sepp"), new Player("2", "Hans"),
                new Player("3", "Franz"), new Player("4", "Michl"));
        SchafkopfGame game = new SchafkopfGame(players, rules, bot, SchafkopfConfig.defaults());
        game.setLogger(null);
        game.dealRandom(seed);
        game.autoDetermineRufspiel();
        game.playFullGame(players.get(0));
        return game;
    }

    @Test
    void recordReplaysTheFinishedGameExactly() {
        SchafkopfGame original = playedGame(4711L);
        GameRecord record = GameRecord.from(original);

        SchafkopfGame replayed = record.replay(record.initialHands(), record.plays().size(), rules, bot);

        assertEquals(Phase.FINISHED, replayed.phase());
        assertEquals(original.result().callerTeamAugen(), replayed.result().callerTeamAugen());
        assertEquals(original.result().callerTeamWon(), replayed.result().callerTeamWon());
    }

    @Test
    void sampledWorldsRespectObservedFactsAndKeepHistoryLegal() {
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 20);
        boolean anyVoidObserved = false;
        for (long seed = 1; seed <= 6; seed++) {
            GameRecord record = GameRecord.from(playedGame(seed));
            int playIndex = 20;
            int observer = record.plays().get(playIndex).seat();
            GameContract contract = record.contract().bind(
                    List.of(new Player("a", "a"), new Player("b", "b"), new Player("c", "c"), new Player("d", "d")));

            List<List<List<Card>>> worlds = analyzer.sampleWorlds(record, playIndex, observer, new Random(seed));

            assertFalse(worlds.isEmpty());
            for (List<List<Card>> world : worlds) {
                assertEquals(record.initialHands().get(observer), world.get(observer));
                Set<Card> allCards = new HashSet<>();
                world.forEach(allCards::addAll);
                assertEquals(32, allCards.size());
                record.replay(world, playIndex, rules, bot);
                for (int index = 0; index < playIndex; index++) {
                    GameRecord.Play play = record.plays().get(index);
                    Card lead = record.plays().get(index - index % 4).card();
                    if (index % 4 == 0 || sameCategory(lead, play.card(), contract)) {
                        continue;
                    }
                    anyVoidObserved = true;
                    Set<Card> playedBySeat = new HashSet<>();
                    record.plays().subList(0, playIndex).stream()
                            .filter(p -> p.seat() == play.seat()).forEach(p -> playedBySeat.add(p.card()));
                    assertTrue(world.get(play.seat()).stream()
                                    .filter(card -> !playedBySeat.contains(card))
                                    .noneMatch(card -> sameCategory(lead, card, contract)),
                            "Seat " + play.seat() + " was seen void but got a card of that category");
                }
            }
        }
        assertTrue(anyVoidObserved, "Test games should contain at least one observed void");
    }

    @Test
    void reviewCoversEveryOwnPlayAndMarksForcedMoves() {
        GameRecord record = GameRecord.from(playedGame(4711L));
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 6);

        PostGameAnalyzer.GameReview review = analyzer.review(record, 1, 42L);

        assertEquals(8, review.decisions().size());
        for (PostGameAnalyzer.DecisionReview decision : review.decisions()) {
            assertTrue(decision.alternatives().stream().anyMatch(move -> move.card().equals(decision.played())));
            if (decision.verdict() == PostGameAnalyzer.Verdict.ZWANGSZUG) {
                assertEquals(1, decision.alternatives().size());
            }
        }
        assertEquals(PostGameAnalyzer.Verdict.ZWANGSZUG, review.decisions().get(7).verdict());
        assertTrue(PostGameAnalyzer.report(review).contains("NACHKARTELN"));
    }

    @Test
    void onlyClearImprovementsCountAsMistakes() {
        Card card = record4711FirstCard();
        PostGameAnalyzer.MoveAssessment chosen = new PostGameAnalyzer.MoveAssessment(card, 0.50, 60, false, 50);

        assertTrue(PostGameAnalyzer.isClearlyBetter(assessment(card, 0.60, 55), chosen));
        assertTrue(PostGameAnalyzer.isClearlyBetter(assessment(card, 0.50, 70), chosen));
        assertFalse(PostGameAnalyzer.isClearlyBetter(assessment(card, 0.55, 69), chosen));
        assertFalse(PostGameAnalyzer.isClearlyBetter(assessment(card, 0.45, 90), chosen));
    }

    private Card record4711FirstCard() {
        return GameRecord.from(playedGame(4711L)).plays().get(0).card();
    }

    private static PostGameAnalyzer.MoveAssessment assessment(Card card, double winRate, double eyes) {
        return new PostGameAnalyzer.MoveAssessment(card, winRate, eyes, false, 0);
    }

    private boolean sameCategory(Card lead, Card card, GameContract contract) {
        boolean leadTrump = contract.isTrump(lead, rules);
        boolean cardTrump = contract.isTrump(card, rules);
        return leadTrump ? cardTrump : !cardTrump && card.suit() == lead.suit();
    }
}
