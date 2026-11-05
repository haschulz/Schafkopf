package schafkopf.ai;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.ai.policy.ContractValueModel;
import schafkopf.model.Card;
import schafkopf.model.Personality;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class BiddingAdvisor {
    private static final String ACTION_PASS = "PASS";
    private static final String ACTION_RUFSPIEL = "RUFSPIEL";
    private static final String ACTION_KLOPFEN = "KLOPFEN";

    private final RuleEngine rules;
    private final PolicyGenome genome;
    private final Consumer<DecisionTrace> traceSink;
    private final ContractValueModel valueModel;

    public record BiddingDecision(
            Player player,
            boolean willPlay,
            boolean isBlocked,
            Suit recommendedSuit,
            int handScore,
            int estimatedOwnTricks,
            int estimatedOpponentTricks,
            int expectedOpponentPoints,
            Personality personality,
            String reasoning
    ) {}

    public BiddingAdvisor(RuleEngine rules) {
        this(rules, PolicyGenome.loadConfigured(), ignored -> { });
    }

    public BiddingAdvisor(RuleEngine rules, PolicyGenome genome) {
        this(rules, genome, ignored -> { });
    }

    public BiddingAdvisor(RuleEngine rules, PolicyGenome genome, Consumer<DecisionTrace> traceSink) {
        this(rules, genome, traceSink, ContractValueModel.loadConfigured());
    }

    public BiddingAdvisor(
            RuleEngine rules,
            PolicyGenome genome,
            Consumer<DecisionTrace> traceSink,
            ContractValueModel valueModel
    ) {
        this.rules = rules;
        this.genome = genome;
        this.traceSink = traceSink;
        this.valueModel = valueModel;
    }

    /**
     * Checks if a player is completely blocked from calling a Rufspiel:
     * - Holds all 3 non-trump Aces (Eichel, Gras, Schellen)
     * - OR has no valid suit where player has a small card but lacks the Ace.
     */
    public boolean isBlockedForRufspiel(Player player) {
        return getValidRufspielSuits(player).isEmpty();
    }

    /**
     * Returns all suits that the player is legally allowed to call.
     */
    public List<Suit> getValidRufspielSuits(Player player) {
        List<Suit> valid = new ArrayList<>();
        List<Card> hand = player.hand();

        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            Card sau = new Card(suit, Rank.SAU);
            boolean hasSau = hand.contains(sau);
            boolean hasSmallCardOfSuit = hand.stream().anyMatch(c -> rules.isNonTrumpSuit(c, suit) && !c.equals(sau));

            // Must NOT have the Ace, and MUST have at least one card of the suit!
            if (!hasSau && hasSmallCardOfSuit) {
                valid.add(suit);
            }
        }
        return valid;
    }

    /**
     * Evaluates whether the player should announce a game, calculating:
     * - Hand strength score
     * - Scenario analysis (estimated own tricks vs. opponent tricks)
     * - Pessimistic opponent points calculation (Worst-Case Szenario)
     */
    public BiddingDecision evaluatePlayer(Player player, Personality personality) {
        BiddingDecision decision = Objects.requireNonNull(evaluatePlayerDecision(player, personality));
        String selectedAction = decision.willPlay() && decision.recommendedSuit() != null
            ? ACTION_RUFSPIEL + ":" + decision.recommendedSuit().name()
            : ACTION_PASS;
        Map<String, Double> scores = Map.of(
            ACTION_PASS, 0.0,
            ACTION_RUFSPIEL, (double) decision.handScore() - personality.minPlayScore());
        Map<String, Double> features = Map.of(
            "handScore", (double) decision.handScore(),
            "estimatedOpponentPoints", (double) decision.expectedOpponentPoints());
        traceSink.accept(new DecisionTrace(
            genome.id(), DecisionTrace.Stage.BIDDING, ACTION_RUFSPIEL, player.id(),
                decision.isBlocked() ? List.of(ACTION_PASS) : List.of(ACTION_PASS, ACTION_RUFSPIEL),
            selectedAction, scores, Map.of(selectedAction, features), decision.reasoning()));
        return decision;
        }

        private BiddingDecision evaluatePlayerDecision(Player player, Personality personality) {
        List<Suit> validSuits = getValidRufspielSuits(player);

        if (validSuits.isEmpty()) {
            return new BiddingDecision(
                    player,
                    false,
                    true,
                    null,
                    0,
                    0,
                    8,
                    120,
                    personality,
                    "GESPERRT: Besitzt entweder alle drei Farbsäue oder hat keine Beikarten für ein Rufspiel!"
            );
        }

        // Evaluate each possible suit to call and pick the best one
        BiddingDecision bestDecision = null;
        for (Suit suit : validSuits) {
            BiddingDecision decision = evaluateSuit(player, suit, personality);
            if (bestDecision == null || decision.handScore() > bestDecision.handScore()) {
                bestDecision = decision;
            }
        }

        return bestDecision;
    }

    // =========================================================================
    // KLOPFEN (Doubling before the deal) – Task 6
    // =========================================================================

    /**
     * Decides whether a player should "klopfen" (double the stakes before the deal)
     * based on their first 4 cards.
     *
     * <p>Klopfen criteria (any one is sufficient):
     * <ol>
     *   <li>Holds both Eichel-Ober AND Gras-Ober (the top two trumps).</li>
     *   <li>Holds 3 or more Ober or Unter cards combined (dominant trump top).</li>
     *   <li>Holds Eichel-Ober AND a non-trump Sau of any suit (powerful combo).</li>
     * </ol>
     *
     * @param player     the player considering klopfen
     * @param first4Cards the first four cards dealt to the player
     * @param rules      the rule engine (for trump classification)
     * @return true if klopfen is recommended
     */
    public boolean shouldKlopfen(Player player, List<Card> first4Cards, RuleEngine rules) {
        Card eichelOber = new Card(Suit.EICHEL, Rank.OBER);
        Card grasOber   = new Card(Suit.GRAS,   Rank.OBER);

        boolean hasEichelOber = first4Cards.contains(eichelOber);
        boolean hasGrasOber   = first4Cards.contains(grasOber);

        long topTrumps = first4Cards.stream()
                .filter(c -> c.rank() == Rank.OBER || c.rank() == Rank.UNTER)
                .count();
        boolean hasNonTrumpSau = first4Cards.stream()
            .anyMatch(c -> c.rank() == Rank.SAU && !rules.isTrump(c));
        Map<String, Double> features = Map.of(
            "topPair", hasEichelOber && hasGrasOber ? 1.0 : 0.0,
            "topTrumps", (double) topTrumps,
            "eichelOberAndSau", hasEichelOber && hasNonTrumpSau ? 1.0 : 0.0);
        double score = genome.score(features, "klopfen.");
        boolean shouldDouble = score >= genome.weight("klopfen.threshold");
        String selectedAction = shouldDouble ? ACTION_KLOPFEN : ACTION_PASS;
        Map<String, Double> scores = Map.of(ACTION_PASS, 0.0,
            ACTION_KLOPFEN, score - genome.weight("klopfen.threshold"));
        traceSink.accept(new DecisionTrace(genome.id(), DecisionTrace.Stage.KLOPFEN, "UNDECLARED",
            player.id(), List.of(ACTION_PASS, ACTION_KLOPFEN), selectedAction, scores,
            Map.of(ACTION_KLOPFEN, features), "Genome threshold for the first four cards"));
        return shouldDouble;
    }

    // =========================================================================
    // EINDOPPELN – Task 7
    // =========================================================================

    /**
     * Decides whether the next game should be doubled (Eindoppeln) because all
     * four players have passed consecutively.
     *
     * <p>The rule: if {@code consecutivePassCount >= 4} (all four players said
     * "Weiter"), the next game is played at double the base stake.
     *
     * @param consecutivePassCount how many players in a row have said "Weiter"
     * @return true if Eindoppeln should be applied to the next game
     */
    public boolean shouldEindoppeln(int consecutivePassCount) {
        return consecutivePassCount >= 4;
    }

    private BiddingDecision evaluateSuit(Player player, Suit calledSuit, Personality personality) {
        List<Card> hand = player.hand();
        int score = 0;

        // 1. Trumpf-Qualität (Spitzen)
        long trumpCount = hand.stream().filter(rules::isTrump).count();
        for (Card c : hand) {
            if (rules.isTrump(c)) {
                if (c.rank() == Rank.OBER) {
                    score += switch (c.suit()) {
                        case EICHEL -> 10;
                        case GRAS -> 8;
                        case HERZ -> 6;
                        case SCHELLEN -> 5;
                    };
                } else if (c.rank() == Rank.UNTER) {
                    score += switch (c.suit()) {
                        case EICHEL -> 4;
                        case GRAS -> 3;
                        case HERZ -> 3;
                        case SCHELLEN -> 2;
                    };
                } else if (c.rank() == Rank.SAU) {
                    score += 3;
                } else if (c.rank() == Rank.ZEHN) {
                    score += 2;
                } else {
                    score += 1;
                }
            }
        }

        // Trump bonus for length
        if (trumpCount >= 5) score += 6;
        else if (trumpCount == 4) score += 3;
        else if (trumpCount <= 2) score -= 6;

        // 2. Eigene Farbsäue (außer gerufener Farbe)
        int ownFarbsaeue = 0;
        for (Suit s : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            if (s != calledSuit) {
                Card sau = new Card(s, Rank.SAU);
                if (hand.contains(sau)) {
                    ownFarbsaeue++;
                    score += 6; // Sichere 11 Augen + Stichchance
                }
            }
        }

        // 3. Fehlfarben-Struktur (Stechchancen vs. Gefahr)
        for (Suit s : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            if (s != calledSuit) {
                long suitCardsCount = hand.stream().filter(c -> rules.isNonTrumpSuit(c, s)).count();
                if (suitCardsCount == 0) {
                    score += 4; // Sofort farbfrei -> kann früh stechen!
                } else if (suitCardsCount == 1 && !hand.contains(new Card(s, Rank.SAU))) {
                    score += 2; // Einzelne kleine Karte -> schnell frei gespielt!
                } else if (suitCardsCount >= 3 && !hand.contains(new Card(s, Rank.SAU))) {
                    score -= 4; // Lange Fehlfarbe ohne Sau -> Abstichgefahr durch Gegner!
                }
            }
        }

        // 4. Struktur der Suchfarbe
        long calledSuitCount = hand.stream().filter(c -> rules.isNonTrumpSuit(c, calledSuit)).count();
        if (calledSuitCount == 1) {
            score += 4; // Ideal: 1 Beikarte, Partner macht die Sau, danach farbfrei!
        } else if (calledSuitCount >= 3) {
            score -= 3; // Mehrere Beikarten: Rufer muss oft Farbe zugeben
        }

        // =========================================================================
        // 5. SZENARIO- & WORST-CASE-ANALYSE (Gegner-Modellierung)
        // Wie viele Stiche machen wir, wie viele die Gegner?
        // =========================================================================
        int estimatedCallerTricks = 0;
        if (hand.contains(new Card(Suit.EICHEL, Rank.OBER))) estimatedCallerTricks++;
        if (hand.contains(new Card(Suit.GRAS, Rank.OBER))) estimatedCallerTricks++;
        if (trumpCount >= 5) estimatedCallerTricks += 2;
        else if (trumpCount >= 4) estimatedCallerTricks += 1;
        estimatedCallerTricks += ownFarbsaeue;

        // Partner expected tricks (the called Sau trick is expected, plus maybe 0.5 trick)
        int estimatedTeamTricks = Math.min(7, estimatedCallerTricks + 2);
        int estimatedOpponentTricks = Math.max(1, 8 - estimatedTeamTricks);

        // Pessimistische Gegner-Augen-Schätzung:
        // Ein optimaler Gegner schmiert bei jedem Stich 10er oder Säue -> Ø 16-20 Augen pro gegnerischem Stich!
        int expectedOpponentPoints = estimatedOpponentTricks * 18;

        boolean opponentThreatHigh = expectedOpponentPoints >= 60;
        RufspielContract contract = new RufspielContract(player, calledSuit);
        Map<String, Double> contractFeatures = new GameBiddingAdvisor(rules, genome,
            ignored -> { }, valueModel).featuresFor(player, contract);
        double expectedValueCents = valueModel.estimate(contract.gameType(), contractFeatures);
        Map<String, Double> bidFeatures = Map.of(
            "handScore", (double) score,
            "riskPenalty", opponentThreatHigh ? 1.0 : 0.0,
            "zockerRiskRelief", opponentThreatHigh && personality == Personality.ZOCKER ? 1.0 : 0.0,
            "expectedValueCents", expectedValueCents);
        double policyScore = genome.score(bidFeatures, "bid.");
        boolean willPlay = policyScore >= personality.minPlayScore();

        String scenarioReason = String.format(
                "Szenario: Erwartete Team-Stiche: %d | Gegner-Stiche: %d (Pessimistisch ~%d Gegner-Augen). Hand-Score: %d (Schwelle: %d). %s",
                estimatedTeamTricks,
                estimatedOpponentTricks,
                expectedOpponentPoints,
                score,
                personality.minPlayScore(),
                willPlay
                        ? ("Sagt Spiel an auf die " + calledSuit.displayName() + "-Sau!")
                        : ("Sagt 'Weiter' (Pessimistisches Risiko: Gegner droht >= 60 Augen zu machen).")
        );

        return new BiddingDecision(
                player,
                willPlay,
                false,
                calledSuit,
                score,
                estimatedTeamTricks,
                estimatedOpponentTricks,
                expectedOpponentPoints,
                personality,
                scenarioReason
        );
    }
}

