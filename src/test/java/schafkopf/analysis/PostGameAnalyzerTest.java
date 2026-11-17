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
        GameRecord gameRecord = GameRecord.from(original);

        SchafkopfGame replayed = gameRecord.replay(
            gameRecord.initialHands(), gameRecord.plays().size(), rules, bot);

        assertEquals(Phase.FINISHED, replayed.phase());
        assertEquals(original.result().callerTeamAugen(), replayed.result().callerTeamAugen());
        assertEquals(original.result().callerTeamWon(), replayed.result().callerTeamWon());
    }

    @Test
    void sampledWorldsRespectObservedFactsAndKeepHistoryLegal() {
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 20);
        boolean anyVoidObserved = false;
        for (long seed = 1; seed <= 6; seed++) {
                GameRecord gameRecord = GameRecord.from(playedGame(seed));
            int playIndex = 20;
                int observer = gameRecord.plays().get(playIndex).seat();
                GameContract contract = gameRecord.contract().bind(
                    List.of(new Player("a", "a"), new Player("b", "b"), new Player("c", "c"), new Player("d", "d")));

                List<List<List<Card>>> worlds = analyzer.sampleWorlds(gameRecord, playIndex, observer, new Random(seed));

            assertFalse(worlds.isEmpty());
            for (List<List<Card>> world : worlds) {
                assertEquals(gameRecord.initialHands().get(observer), world.get(observer));
                Set<Card> allCards = new HashSet<>();
                world.forEach(allCards::addAll);
                assertEquals(32, allCards.size());
                gameRecord.replay(world, playIndex, rules, bot);
                for (int index = 0; index < playIndex; index++) {
                    GameRecord.Play play = gameRecord.plays().get(index);
                    Card lead = gameRecord.plays().get(index - index % 4).card();
                    if (index % 4 == 0 || sameCategory(lead, play.card(), contract)) {
                        continue;
                    }
                    anyVoidObserved = true;
                    Set<Card> playedBySeat = new HashSet<>();
                    gameRecord.plays().subList(0, playIndex).stream()
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
        GameRecord gameRecord = GameRecord.from(playedGame(4711L));
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 6);

        PostGameAnalyzer.GameReview review = analyzer.review(gameRecord, 1, 42L);

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
        void reviewAllAnalyzesEverySeatAndCollectsOnlyActionableErrors() {
        GameRecord gameRecord = GameRecord.from(playedGame(4711L));
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 2);

        List<PostGameAnalyzer.GameReview> reviews = analyzer.reviewAll(gameRecord, 420L);
        List<PostGameAnalyzer.LearningProposal> proposals = PostGameAnalyzer.learningProposals(reviews);

        assertEquals(4, reviews.size());
        for (int seat = 0; seat < 4; seat++) {
            PostGameAnalyzer.GameReview review = reviews.get(seat);
            assertEquals(seat, review.seat());
            assertEquals(gameRecord.playerNames().get(seat), review.playerName());
            assertEquals(8, review.decisions().size());
        }
        assertTrue(proposals.stream().allMatch(proposal -> reviews.get(proposal.seat()).decisions().stream()
            .anyMatch(decision -> decision.playIndex() == proposal.playIndex()
                && decision.verdict() == PostGameAnalyzer.Verdict.FEHLER)));
        }

        @Test
        void counterfactualReplayKeepsOriginalPrefixAndCompletesAllTricks() {
        GameRecord gameRecord = GameRecord.from(playedGame(4711L));
        PostGameAnalyzer analyzer = new PostGameAnalyzer(rules, bot, 1);
        PostGameAnalyzer.GameReview review = analyzer.review(gameRecord, 0, 421L);
        PostGameAnalyzer.DecisionReview decision = review.decisions().stream()
            .filter(item -> item.alternatives().size() > 1)
            .findFirst()
            .orElseThrow();
        Card alternative = decision.alternatives().stream()
            .map(PostGameAnalyzer.MoveAssessment::card)
            .filter(card -> !card.equals(decision.played()))
            .findFirst()
            .orElseThrow();

        PostGameAnalyzer.CounterfactualLine line = analyzer.simulateAlternative(gameRecord, decision, alternative);

        List<PostGameAnalyzer.CounterfactualPlay> replayedPlays = line.tricks().stream()
            .flatMap(trick -> trick.plays().stream())
            .toList();
        assertEquals(8, line.tricks().size());
        assertTrue(line.tricks().stream().allMatch(trick -> trick.plays().size() == 4));
        for (int index = 0; index < decision.playIndex(); index++) {
            assertEquals(gameRecord.plays().get(index).card(), replayedPlays.get(index).card());
        }
        assertEquals(alternative, replayedPlays.get(decision.playIndex()).card());
        assertEquals(32, gameRecord.plays().size());
        }

        @Test
        void learningProposalsExcludeHindsightOnlyAndCorrectDecisions() {
        Card played = new Card(schafkopf.model.Suit.EICHEL, schafkopf.model.Rank.SIEBEN);
        Card better = new Card(schafkopf.model.Suit.GRAS, schafkopf.model.Rank.SIEBEN);
        PostGameAnalyzer.MoveAssessment playedAssessment =
            new PostGameAnalyzer.MoveAssessment(played, 0.40, 35, false, 35);
        PostGameAnalyzer.MoveAssessment betterAssessment =
            new PostGameAnalyzer.MoveAssessment(better, 0.55, 42, true, 42);
        PostGameAnalyzer.DecisionReview error = new PostGameAnalyzer.DecisionReview(
            7, 2, 1, played, PostGameAnalyzer.Verdict.FEHLER, betterAssessment,
            List.of(playedAssessment, betterAssessment), 20);
        PostGameAnalyzer.DecisionReview hindsight = new PostGameAnalyzer.DecisionReview(
            12, 4, 1, played, PostGameAnalyzer.Verdict.RUECKSCHAU, betterAssessment,
            List.of(playedAssessment, betterAssessment), 20);
        PostGameAnalyzer.DecisionReview correct = new PostGameAnalyzer.DecisionReview(
            19, 5, 2, played, PostGameAnalyzer.Verdict.IN_ORDNUNG, null,
            List.of(playedAssessment), 20);
        List<PostGameAnalyzer.LearningProposal> proposals = PostGameAnalyzer.learningProposals(List.of(
            new PostGameAnalyzer.GameReview(0, "Sepp", false, 35, 0.5,
                List.of(error, hindsight, correct))));

        assertEquals(1, proposals.size());
        assertEquals(7, proposals.get(0).playIndex());
        assertEquals(played, proposals.get(0).played());
        assertEquals(better, proposals.get(0).suggested());
        }

    @Test
    void learningJournalRoundTripsGameAndProposalOffline() {
        GameRecord gameRecord = GameRecord.from(playedGame(4711L));
        PostGameAnalyzer.LearningProposal proposal = new PostGameAnalyzer.LearningProposal(
                2, "Franz", 11, 3,
                new Card(schafkopf.model.Suit.EICHEL, schafkopf.model.Rank.SIEBEN),
                new Card(schafkopf.model.Suit.GRAS, schafkopf.model.Rank.SIEBEN),
                0.4, 0.6, 32, 54, 40);
        LearningJournalEntry entry = new LearningJournalEntry(
                "game-4711", 4711L, gameRecord.toText(), List.of(proposal));

        LearningJournalEntry restored = LearningJournalCodec.decode(LearningJournalCodec.encode(entry));

        assertEquals(entry.id(), restored.id());
        assertEquals(entry.createdAtMillis(), restored.createdAtMillis());
        assertEquals(gameRecord.plays(), restored.gameRecord().plays());
        assertEquals(List.of(proposal), restored.proposals());
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
