package schafkopf.analysis;

import schafkopf.ai.SimpleBot;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.Phase;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * "Nachkarteln": reviews every decision of one seat using only the information available at that moment.
 *
 * <p>For each decision, card distributions consistent with all observed plays are sampled. Every legal
 * alternative is played out in the same distributions, so differences come from the decision, not from
 * card luck. A move is a mistake only if an alternative was clearly better under the information at
 * that time; being better only with knowledge of the real hands counts as hindsight, not as a mistake.
 */
public final class PostGameAnalyzer {
    /** Minimum win-rate gain that makes an alternative clearly better. */
    public static final double WIN_RATE_MARGIN = 0.10;
    /** Minimum eye gain, at no loss of win rate, that makes an alternative clearly better. */
    public static final double EYES_MARGIN = 10.0;
    private static final int DEAL_ATTEMPTS_PER_SAMPLE = 50;

    public enum Verdict {
        FEHLER("Fehler"),
        RUECKSCHAU("nur im Nachhinein besser"),
        IN_ORDNUNG("in Ordnung"),
        ZWANGSZUG("Zwangszug");

        private final String label;

        Verdict(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record MoveAssessment(Card card, double winRate, double meanEyes, boolean winsWithRealCards,
                                 int eyesWithRealCards) {
    }

    public record DecisionReview(int trickNumber, int positionInTrick, Card played, Verdict verdict,
                                 MoveAssessment better, List<MoveAssessment> alternatives, int samples) {
        public MoveAssessment playedAssessment() {
            return alternatives.stream().filter(move -> move.card().equals(played)).findFirst().orElseThrow();
        }
    }

    public record GameReview(int seat, String playerName, boolean won, int teamEyes,
                             double firstDecisionWinChance, List<DecisionReview> decisions) {
        public long mistakes() {
            return decisions.stream().filter(decision -> decision.verdict() == Verdict.FEHLER).count();
        }
    }

    private record Category(boolean trump, Suit suit) {
    }

    private record Outcome(boolean won, int eyes) {
    }

    private final RuleEngine rules;
    private final SimpleBot rolloutBot;
    private final int samples;

    public PostGameAnalyzer(RuleEngine rules, SimpleBot rolloutBot, int samples) {
        if (samples < 1) {
            throw new IllegalArgumentException("At least one sample is required");
        }
        this.rules = rules;
        this.rolloutBot = rolloutBot;
        this.samples = samples;
    }

    public GameReview review(GameRecord record, int seat, long seed) {
        Random random = new Random(seed);
        List<DecisionReview> decisions = new ArrayList<>();
        for (int index = 0; index < record.plays().size(); index++) {
            if (record.plays().get(index).seat() == seat) {
                decisions.add(reviewDecision(record, index, random));
            }
        }
        Outcome actual = playOut(record, record.initialHands(), record.plays().size(), null, seat);
        double firstChance = decisions.get(0).alternatives().stream()
                .mapToDouble(MoveAssessment::winRate).max().orElse(0);
        return new GameReview(seat, record.playerNames().get(seat), actual.won(), actual.eyes(),
                firstChance, List.copyOf(decisions));
    }

    private DecisionReview reviewDecision(GameRecord record, int playIndex, Random random) {
        GameRecord.Play play = record.plays().get(playIndex);
        SchafkopfGame realGame = record.replay(record.initialHands(), playIndex, rules, rolloutBot);
        int trickNumber = realGame.currentTrickNumber();
        int position = realGame.currentTrickCards().size() + 1;
        List<Card> legalMoves = realGame.legalMovesForCurrentPlayer();
        if (legalMoves.size() == 1) {
            Outcome outcome = playOut(record, record.initialHands(), playIndex, play.card(), play.seat());
            MoveAssessment only = new MoveAssessment(play.card(), outcome.won() ? 1 : 0, outcome.eyes(),
                    outcome.won(), outcome.eyes());
            return new DecisionReview(trickNumber, position, play.card(), Verdict.ZWANGSZUG, null,
                    List.of(only), 0);
        }

        List<List<List<Card>>> worlds = sampleWorlds(record, playIndex, play.seat(), random);
        List<MoveAssessment> assessments = new ArrayList<>();
        for (Card move : legalMoves) {
            int wins = 0;
            long eyes = 0;
            for (List<List<Card>> world : worlds) {
                Outcome outcome = playOut(record, world, playIndex, move, play.seat());
                wins += outcome.won() ? 1 : 0;
                eyes += outcome.eyes();
            }
            Outcome real = playOut(record, record.initialHands(), playIndex, move, play.seat());
            assessments.add(new MoveAssessment(move, (double) wins / worlds.size(),
                    (double) eyes / worlds.size(), real.won(), real.eyes()));
        }
        assessments.sort(Comparator.comparingDouble(MoveAssessment::winRate)
                .thenComparingDouble(MoveAssessment::meanEyes).reversed());

        MoveAssessment chosen = assessments.stream()
                .filter(move -> move.card().equals(play.card())).findFirst().orElseThrow();
        MoveAssessment better = assessments.stream()
                .filter(move -> isClearlyBetter(move, chosen))
                .findFirst()
                .orElse(null);
        Verdict verdict;
        if (better != null) {
            verdict = Verdict.FEHLER;
        } else if (!chosen.winsWithRealCards()
                && assessments.stream().anyMatch(MoveAssessment::winsWithRealCards)) {
            verdict = Verdict.RUECKSCHAU;
            better = assessments.stream().filter(MoveAssessment::winsWithRealCards).findFirst().orElseThrow();
        } else {
            verdict = Verdict.IN_ORDNUNG;
        }
        return new DecisionReview(trickNumber, position, play.card(), verdict, better,
                List.copyOf(assessments), worlds.size());
    }

    static boolean isClearlyBetter(MoveAssessment candidate, MoveAssessment chosen) {
        double tolerance = 1e-9;
        double winGain = candidate.winRate() - chosen.winRate();
        double eyeGain = candidate.meanEyes() - chosen.meanEyes();
        return winGain >= WIN_RATE_MARGIN - tolerance
                || (winGain >= -tolerance && eyeGain >= EYES_MARGIN - tolerance);
    }

    /** Plays one move (or none) and lets the rollout bot finish the game; returns the seat's team view. */
    private Outcome playOut(GameRecord record, List<List<Card>> hands, int playIndex, Card move, int seat) {
        SchafkopfGame game = record.replay(hands, playIndex, rules, rolloutBot);
        if (move != null) {
            game.playCard(move);
        }
        while (game.phase() == Phase.TRICK_PLAY) {
            game.playBotTurn();
        }
        GameResult result = game.result();
        Player player = game.players().get(seat);
        boolean callerSide = result.callerTeam().contains(player);
        return new Outcome(callerSide == result.callerTeamWon(),
                callerSide ? result.callerTeamAugen() : result.defenderTeamAugen());
    }

    /** Hands for all seats that agree with the observer's hand and every play before {@code playIndex}. */
    List<List<List<Card>>> sampleWorlds(GameRecord record, int playIndex, int observer, Random random) {
        GameContract contract = record.contract().bind(placeholderPlayers());
        List<List<Card>> played = new ArrayList<>();
        List<Set<Category>> voids = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            played.add(new ArrayList<>());
            voids.add(new HashSet<>());
        }
        Set<Card> unknown = new HashSet<>();
        record.initialHands().forEach(unknown::addAll);
        record.initialHands().get(observer).forEach(unknown::remove);

        Category led = null;
        for (int index = 0; index < playIndex; index++) {
            GameRecord.Play play = record.plays().get(index);
            Category category = categoryOf(play.card(), contract);
            if (index % 4 == 0) {
                led = category;
            } else if (!category.equals(led)) {
                voids.get(play.seat()).add(led);
            }
            played.get(play.seat()).add(play.card());
            unknown.remove(play.card());
        }

        List<List<List<Card>>> worlds = new ArrayList<>();
        int attempts = samples * DEAL_ATTEMPTS_PER_SAMPLE;
        for (int attempt = 0; attempt < attempts && worlds.size() < samples; attempt++) {
            List<List<Card>> world = dealUnknown(record, observer, played, voids, unknown, contract, random);
            if (world != null && isConsistent(record, world, playIndex, contract)) {
                worlds.add(world);
            }
        }
        if (worlds.isEmpty()) {
            worlds.add(record.initialHands());
        }
        return worlds;
    }

    private List<List<Card>> dealUnknown(GameRecord record, int observer, List<List<Card>> played,
                                         List<Set<Category>> voids, Set<Card> unknown, GameContract contract,
                                         Random random) {
        List<List<Card>> remaining = new ArrayList<>();
        int[] capacity = new int[4];
        for (int seat = 0; seat < 4; seat++) {
            remaining.add(new ArrayList<>());
            capacity[seat] = seat == observer ? 0 : 8 - played.get(seat).size();
        }
        List<Card> cards = new ArrayList<>(unknown);
        Collections.shuffle(cards, random);
        cards.sort(Comparator.comparingInt(
                card -> eligibleSeats(card, observer, voids, contract, record.contract()).size()));
        for (Card card : cards) {
            List<Integer> options = eligibleSeats(card, observer, voids, contract, record.contract()).stream()
                    .filter(seat -> capacity[seat] > 0)
                    .toList();
            if (options.isEmpty()) {
                return null;
            }
            int seat = options.get(random.nextInt(options.size()));
            remaining.get(seat).add(card);
            capacity[seat]--;
        }
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            if (seat == observer) {
                hands.add(record.initialHands().get(seat));
            } else {
                List<Card> hand = new ArrayList<>(played.get(seat));
                hand.addAll(remaining.get(seat));
                hands.add(hand);
            }
        }
        return hands;
    }

    private List<Integer> eligibleSeats(Card card, int observer, List<Set<Category>> voids,
                                        GameContract contract, GameRecord.ContractSpec spec) {
        Category category = categoryOf(card, contract);
        boolean calledSau = spec.type() == GameType.RUFSPIEL && card.equals(new Card(spec.suit(), Rank.SAU));
        return IntStream.range(0, 4)
                .filter(seat -> seat != observer && !voids.get(seat).contains(category))
                .filter(seat -> !(calledSau && seat == spec.callerSeat()))
                .boxed()
                .toList();
    }

    private boolean isConsistent(GameRecord record, List<List<Card>> world, int playIndex, GameContract contract) {
        if (record.contract().type() == GameType.RUFSPIEL) {
            Suit suit = record.contract().suit();
            boolean callerHoldsCalledSuit = world.get(record.contract().callerSeat()).stream()
                    .anyMatch(card -> card.suit() == suit && card.rank() != Rank.SAU
                            && !contract.isTrump(card, rules));
            if (!callerHoldsCalledSuit) {
                return false;
            }
        }
        try {
            record.replay(world, playIndex, rules, rolloutBot);
            return true;
        } catch (IllegalArgumentException | IllegalStateException inconsistent) {
            return false;
        }
    }

    private Category categoryOf(Card card, GameContract contract) {
        return contract.isTrump(card, rules) ? new Category(true, null) : new Category(false, card.suit());
    }

    private static List<Player> placeholderPlayers() {
        return IntStream.range(0, 4).mapToObj(seat -> new Player(String.valueOf(seat), "Platz " + seat)).toList();
    }

    public static String report(GameReview review) {
        StringBuilder text = new StringBuilder();
        text.append("NACHKARTELN für ").append(review.playerName()).append('\n');
        text.append(review.won() ? "Ergebnis: gewonnen" : "Ergebnis: verloren")
                .append(" (").append(review.teamEyes()).append(" Augen für die eigene Partei)\n");
        text.append(String.format(Locale.GERMAN, "Gewinnchance beim ersten eigenen Zug (bestes Spiel): %.0f %%%n",
                review.firstDecisionWinChance() * 100));
        for (DecisionReview decision : review.decisions()) {
            text.append(String.format(Locale.GERMAN, "%nStich %d, Position %d: %s – %s",
                    decision.trickNumber(), decision.positionInTrick(), decision.played().shortName(),
                    decision.verdict().label()));
            if (decision.verdict() == Verdict.ZWANGSZUG) {
                continue;
            }
            text.append(String.format(Locale.GERMAN, " (%d mögliche Verteilungen)%n", decision.samples()));
            for (MoveAssessment move : decision.alternatives()) {
                text.append(String.format(Locale.GERMAN, "   %s %-16s Gewinn %3.0f %%, Ø %5.1f Augen | echte Karten: %s, %d Augen%n",
                        move.card().equals(decision.played()) ? "▶" : " ",
                        move.card().shortName(), move.winRate() * 100, move.meanEyes(),
                        move.winsWithRealCards() ? "gewonnen" : "verloren", move.eyesWithRealCards()));
            }
            if (decision.verdict() == Verdict.FEHLER) {
                text.append("   → Mit dem damaligen Wissen war ").append(decision.better().card().shortName())
                        .append(" klar besser.\n");
            } else if (decision.verdict() == Verdict.RUECKSCHAU) {
                text.append("   → Nur mit Kenntnis aller Karten wäre ").append(decision.better().card().shortName())
                        .append(" besser gewesen – kein Fehler.\n");
            }
        }
        text.append('\n');
        if (review.won()) {
            text.append(review.mistakes() == 0 ? "Fazit: gewonnen ohne erkennbaren Fehler."
                    : "Fazit: gewonnen, aber mit " + review.mistakes() + " vermeidbaren Fehler(n).");
        } else {
            text.append(review.mistakes() == 0
                    ? "Fazit: verloren ohne vermeidbaren Fehler – Pech mit der Kartenverteilung."
                    : "Fazit: verloren mit " + review.mistakes() + " vermeidbaren Fehler(n).");
        }
        text.append("\nHinweis: Die Fortsetzungen spielt der vorhandene Bot; die Bewertung ist nur so gut wie er.\n");
        return text.toString();
    }
}
