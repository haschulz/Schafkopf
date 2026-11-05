package schafkopf.ai;

import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SimpleBot {
    private record DecisionContext(GameMemory memory, List<Player> remainingPlayers, boolean complete) {
    }

    private final RuleEngine rules;
    private final boolean useMemoryEngine;
    private final RufspielLeadStrategy leadStrategy;
    private final PolicyGenome genome;
    private final Consumer<DecisionTrace> traceSink;

    public SimpleBot(RuleEngine rules) {
        this(rules, true, PolicyGenome.loadConfigured());
    }

    public SimpleBot(RuleEngine rules, boolean useMemoryEngine) {
        this(rules, useMemoryEngine, PolicyGenome.loadConfigured());
    }

    public SimpleBot(RuleEngine rules, boolean useMemoryEngine, PolicyGenome genome) {
        this(rules, useMemoryEngine, genome, ignored -> { });
    }

    public SimpleBot(
            RuleEngine rules,
            boolean useMemoryEngine,
            PolicyGenome genome,
            Consumer<DecisionTrace> traceSink
    ) {
        this.rules = rules;
        this.useMemoryEngine = useMemoryEngine;
        this.leadStrategy = new RufspielLeadStrategy(rules, useMemoryEngine);
        this.genome = genome;
        this.traceSink = traceSink;
    }
    /**
     * Chooses the optimal card with full tactical reasoning, memory, card accounting,
     * signaling semantics, and threshold evaluation ("Erst gewinnen, dann nachdenken").
     */
    public MoveExplanation chooseMove(
            Player player,
            Trick trick,
            RufspielContract contract,
            Player partner,
            boolean calledSauPlayedBefore,
            int currentTrickNumber,
            GameMemory memory,
            List<Player> remainingPlayers
    ) {
            return chooseRufspielMoveAndRank(player, trick, contract, partner, calledSauPlayedBefore,
                currentTrickNumber, new DecisionContext(memory, remainingPlayers, true));
            }

            private MoveExplanation decideRufspielMove(
                Player player,
                Trick trick,
                RufspielContract contract,
                Player partner,
                boolean calledSauPlayedBefore,
                int currentTrickNumber,
                DecisionContext context
            ) {
        List<Card> legalMoves = rules.getLegalMoves(player, trick, contract, calledSauPlayedBefore, currentTrickNumber);
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("Player " + player.name() + " has no legal moves!");
        }

        String ruleRequirement = rules.explainRuleRequirement(player, trick, contract, calledSauPlayedBefore, currentTrickNumber);

        // Forced move (only 1 card legal)
        if (legalMoves.size() == 1) {
            Card forcedCard = legalMoves.get(0);
            String reason = forcedCard.equals(contract.calledSau())
                    ? "Muss die gerufene Sau zwingend zugeben"
                    : "Einzige regelkonforme Karte";
            return new MoveExplanation(player, forcedCard, ruleRequirement, reason, true);
        }

        // Leading the trick (Ausspiel)
        if (trick.playedCards().isEmpty()) {
            RufspielLeadStrategy strategy = context.complete() ? leadStrategy : new RufspielLeadStrategy(rules, false);
            return strategy.chooseLeadMove(player, legalMoves, contract, partner, calledSauPlayedBefore, ruleRequirement,
                    context.memory());
        }

        // Following the trick (Zugeben)
        return chooseFollowMove(player, trick, legalMoves, contract, partner, ruleRequirement, context);
    }

    /**
     * Backward-compatible overload without memory / remainingPlayers (creates temporary memory).
     */
    public MoveExplanation chooseMove(
            Player player,
            Trick trick,
            RufspielContract contract,
            Player partner,
            boolean calledSauPlayedBefore,
            int currentTrickNumber
    ) {
        GameMemory dummyMemory = new GameMemory(List.of(player, contract.caller(), partner), rules);
        return chooseRufspielMoveAndRank(player, trick, contract, partner, calledSauPlayedBefore,
            currentTrickNumber, new DecisionContext(dummyMemory, List.of(), false));
    }

        private MoveExplanation chooseRufspielMoveAndRank(
            Player player,
            Trick trick,
            RufspielContract contract,
            Player partner,
            boolean calledSauPlayedBefore,
            int currentTrickNumber,
            DecisionContext context
        ) {
        MoveExplanation recommendation = decideRufspielMove(player, trick, contract, partner,
            calledSauPlayedBefore, currentTrickNumber, context);
        List<Card> legalMoves = rules.getLegalMoves(player, trick, contract, calledSauPlayedBefore, currentTrickNumber);
        return rankLegalMoves(player, trick, contract, List.of(contract.caller(), partner), legalMoves,
            recommendation, null);
        }

    private MoveExplanation chooseFollowMove(
            Player player,
            Trick trick,
            List<Card> legalMoves,
            RufspielContract contract,
            Player partner,
            String ruleRequirement,
            DecisionContext context
    ) {
        if (!context.complete()) {
            PlayedCard currentBest = rules.evaluateTrickWinner(trick);
            if (isTeammate(player, currentBest.player(), contract, partner)) {
                return chooseConservativeFollowMove(player, legalMoves, contract, ruleRequirement);
            }
        }
        return chooseFollowMoveWithContext(player, trick, legalMoves, contract, partner, ruleRequirement, context);
    }

    public MoveExplanation chooseMove(
            Player player,
            Trick trick,
            GameContract contract,
            List<Player> ownTeam,
            int currentTrickNumber
    ) {
        return chooseMove(player, trick, contract, ownTeam, currentTrickNumber, null);
        }

        public MoveExplanation chooseMove(
            Player player,
            Trick trick,
            GameContract contract,
            List<Player> ownTeam,
            int currentTrickNumber,
            String roleOverride
        ) {
            List<Card> legalMoves = rules.getLegalMovesForContract(player, trick, contract, false, currentTrickNumber);
            MoveExplanation recommendation = chooseGenericMove(
                player, trick, contract, ownTeam, legalMoves);
            return rankLegalMoves(player, trick, contract, ownTeam, legalMoves, recommendation, roleOverride);
            }

            private MoveExplanation chooseGenericMove(
                Player player,
                Trick trick,
                GameContract contract,
                List<Player> ownTeam,
                List<Card> legalMoves
            ) {
        String requirement = rules.explainRuleRequirement(player, trick, contract);
        if (legalMoves.size() == 1) {
            return new MoveExplanation(player, legalMoves.get(0), requirement, "Einzige regelkonforme Karte", true);
        }

        if (trick.playedCards().isEmpty()) {
            return chooseGenericLead(player, legalMoves, contract, requirement);
        }

        PlayedCard currentBest = rules.evaluateTrickWinner(trick, contract);
        List<Card> winningMoves = legalMoves.stream()
                .filter(card -> canBeat(card, currentBest.card(), trick.leadCard(), contract))
                .toList();
        boolean teammateWinning = ownTeam.contains(currentBest.player());

        if (contract.gameType() == GameType.BETTEL) {
            return chooseBettelFollow(player, trick, legalMoves, winningMoves, currentBest, contract, requirement);
        }
        if (contract.gameType() == GameType.RAMSCH) {
            return chooseRamschFollow(player, trick, legalMoves, winningMoves, currentBest, contract, requirement);
        }
        if (teammateWinning || winningMoves.isEmpty()) {
            Card discard = legalMoves.stream()
                    .min(Comparator.comparingInt(Card::points))
                    .orElse(legalMoves.get(0));
            String reason = teammateWinning
                    ? "Teammitglied führt -> legt die kleinste Karte"
                    : "Kann den Stich nicht übernehmen -> legt die kleinste Karte";
            return new MoveExplanation(player, discard, requirement, reason, true);
        }

        Card cheapestWinner = winningMoves.stream()
                .min(Comparator.comparingInt(Card::points)
                        .thenComparingInt(card -> contract.isTrump(card, rules)
                                ? contract.getTrumpStrength(card)
                                : contract.getSuitStrength(card)))
                .orElseThrow();
        return new MoveExplanation(player, cheapestWinner, requirement,
                "Übernimmt den Stich mit der günstigsten Gewinnkarte " + cheapestWinner.shortName(), true);
    }

        private MoveExplanation rankLegalMoves(
            Player player,
            Trick trick,
            GameContract contract,
            List<Player> ownTeam,
            List<Card> legalMoves,
            MoveExplanation recommendation,
            String roleOverride
        ) {
        PlayedCard currentBest = trick.playedCards().isEmpty()
            ? null
            : rules.evaluateTrickWinner(trick, contract);
        boolean teammateWinning = currentBest != null && ownTeam.contains(currentBest.player());
        String prefix = "card." + contract.gameType().name() + ".";
        String role = roleOverride == null ? cardPolicyRole(player, contract, ownTeam) : roleOverride;
        String rolePrefix = prefix + role + ".";
            Map<String, Double> scores = new LinkedHashMap<>();
            Map<String, Map<String, Double>> featuresByAction = new LinkedHashMap<>();
            for (Card card : legalMoves) {
                Map<String, Double> features = cardFeatures(
                    card, player, trick, contract, currentBest, teammateWinning, recommendation.card());
                featuresByAction.put(card.shortName(), features);
                scores.put(card.shortName(), scoreCardFeatures(features, rolePrefix, prefix));
            }
        Card selected = legalMoves.stream()
                .max(Comparator.comparingDouble(card -> scores.get(card.shortName())))
            .orElse(recommendation.card());
            traceSink.accept(new DecisionTrace(
                genome.id(),
                DecisionTrace.Stage.CARD_PLAY,
                contract.gameType().name(),
                player.id(),
                legalMoves.stream().map(Card::shortName).toList(),
                selected.shortName(),
                scores,
                featuresByAction,
                recommendation.tacticalReasoning()));
        Map<String, Double> contributions = cardContributions(
            featuresByAction.get(selected.shortName()), rolePrefix, prefix);
        String decision = selected.equals(recommendation.card())
                ? "Genom bestätigt den Heuristikvorschlag"
                : "Genom weicht vom Heuristikvorschlag ab";
        String reasoning = recommendation.tacticalReasoning() + " | " + decision + ": "
                + summarizeContributions(contributions);
        return new MoveExplanation(player, selected, recommendation.ruleRequirement(), reasoning, true);
        }

    private double scoreCardFeatures(Map<String, Double> features, String rolePrefix, String gamePrefix) {
        return features.entrySet().stream()
                .mapToDouble(feature -> genome.weight(cardWeightKey(rolePrefix, gamePrefix, feature.getKey()))
                        * feature.getValue())
                .sum();
    }

    private Map<String, Double> cardContributions(
            Map<String, Double> features,
            String rolePrefix,
            String gamePrefix
    ) {
        Map<String, Double> contributions = new LinkedHashMap<>();
        features.forEach((name, value) -> contributions.put(name,
                genome.weight(cardWeightKey(rolePrefix, gamePrefix, name)) * value));
        return contributions;
    }

    private String cardWeightKey(String rolePrefix, String gamePrefix, String feature) {
        String roleKey = rolePrefix + feature;
        if (genome.weights().containsKey(roleKey)) {
            return roleKey;
        }
        String specificKey = gamePrefix + feature;
        return genome.weights().containsKey(specificKey) ? specificKey : "card." + feature;
    }

    private String cardPolicyRole(Player player, GameContract contract, List<Player> ownTeam) {
        if (contract.gameType() == GameType.RUFSPIEL) {
            if (player.equals(contract.caller())) {
                return "RUFSPIEL_CALLER";
            }
            return ownTeam.contains(player) ? "RUFSPIEL_PARTNER" : "DEFENDER";
        }
        if (contract.gameType() == GameType.RAMSCH) {
            return "RAMSCH_PLAYER";
        }
        if (contract.gameType() == GameType.KREUZBOCK) {
            return "KREUZBOCK_TEAM";
        }
        return player.equals(contract.caller()) ? "SOLOIST" : "DEFENDER";
    }

    private String summarizeContributions(Map<String, Double> contributions) {
        return contributions.entrySet().stream()
                .filter(entry -> entry.getValue() != 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.comparingDouble(Math::abs)).reversed())
                .limit(3)
                .map(entry -> featureLabel(entry.getKey()) + " " + String.format("%+.2f", entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String featureLabel(String feature) {
        return switch (feature) {
            case "recommendation" -> "Heuristikvorschlag";
            case "points" -> "Augen";
            case "isTrump" -> "Trumpf";
            case "trumpStrength" -> "Trumpfstärke";
            case "winningTrick" -> "Stichgewinn";
            case "teammatePoints" -> "Partner-Augen";
            case "bettelWinning" -> "Bettel-Stichrisiko";
            case "ramschWinning" -> "Ramsch-Stichrisiko";
            default -> feature;
        };
    }

        private Map<String, Double> cardFeatures(
            Card card,
            Player player,
            Trick trick,
            GameContract contract,
            PlayedCard currentBest,
            boolean teammateWinning,
            Card recommendedCard
        ) {
        boolean isTrump = contract.isTrump(card, rules);
        boolean canWin = currentBest != null && canBeat(card, currentBest.card(), trick.leadCard(), contract);
        boolean bettelDeclarer = contract.gameType() == GameType.BETTEL && player.equals(contract.caller());
        boolean wouldGiveBettelTrick = bettelDeclarer && (canWin
            || (currentBest != null && currentBest.player().equals(contract.caller())));
        boolean farbWenz = contract.gameType() == GameType.FARBWENZ;
        boolean farbWenzSoloist = farbWenz && player.equals(contract.caller());
        boolean leading = trick.playedCards().isEmpty();
        return Map.ofEntries(
            Map.entry("recommendation", card.equals(recommendedCard) ? 1.0 : 0.0),
            Map.entry("points", card.points() / 11.0),
            Map.entry("isTrump", isTrump ? 1.0 : 0.0),
            Map.entry("trumpStrength", isTrump ? contract.getTrumpStrength(card) / 14.0 : 0.0),
            Map.entry("winningTrick", canWin ? 1.0 : 0.0),
            Map.entry("teammatePoints", teammateWinning ? card.points() / 11.0 : 0.0),
            Map.entry("bettelWinning", wouldGiveBettelTrick ? 1.0 : 0.0),
            Map.entry("ramschWinning", contract.gameType() == GameType.RAMSCH && canWin ? 1.0 : 0.0),
            Map.entry("farbwenzSoloistLeadTrump", farbWenzSoloist && leading && isTrump ? 1.0 : 0.0),
            Map.entry("farbwenzTrumpStrength", farbWenz && isTrump
                ? contract.getTrumpStrength(card) / 10.0 : 0.0),
            Map.entry("farbwenzWinningTrick", farbWenz && canWin ? 1.0 : 0.0),
            Map.entry("farbwenzSideSuitWinner", farbWenz && !isTrump
                && contract.getSuitStrength(card) >= 5 ? 1.0 : 0.0));
        }

    private MoveExplanation chooseGenericLead(
            Player player,
            List<Card> legalMoves,
            GameContract contract,
            String requirement
    ) {
        if (contract.gameType() != GameType.BETTEL && player.equals(contract.caller())) {
            Card highestTrump = legalMoves.stream()
                    .filter(card -> contract.isTrump(card, rules))
                    .max(Comparator.comparingInt(contract::getTrumpStrength))
                    .orElse(null);
            if (highestTrump != null) {
                return new MoveExplanation(player, highestTrump, requirement,
                        "Soloist zieht den höchsten Trumpf " + highestTrump.shortName(), true);
            }
        }

        Card lowest = legalMoves.stream()
                .min(Comparator.comparingInt(Card::points)
                        .thenComparingInt(contract::getSuitStrength))
                .orElse(legalMoves.get(0));
        return new MoveExplanation(player, lowest, requirement,
                "Eröffnet mit kleiner Karte " + lowest.shortName(), true);
    }

    private MoveExplanation chooseBettelFollow(
            Player player,
            Trick trick,
            List<Card> legalMoves,
            List<Card> winningMoves,
            PlayedCard currentBest,
            GameContract contract,
            String requirement
    ) {
        boolean isDeclarer = player.equals(contract.caller());
        boolean callerAlreadyWinning = currentBest.player().equals(contract.caller());
        if (isDeclarer || callerAlreadyWinning) {
            List<Card> nonWinningMoves = legalMoves.stream()
                    .filter(card -> !canBeat(card, currentBest.card(), trick.leadCard(), contract))
                    .toList();
            if (!nonWinningMoves.isEmpty()) {
                Card safest = nonWinningMoves.stream()
                        .min(Comparator.comparingInt(Card::points)
                                .thenComparingInt(contract::getSuitStrength))
                        .orElseThrow();
                return new MoveExplanation(player, safest, requirement,
                        "Bettel: vermeidet einen Stich für den Spieler", true);
            }
        }

        List<Card> candidates = winningMoves.isEmpty() ? legalMoves : winningMoves;
        Card choice = isDeclarer
                ? candidates.stream().min(Comparator.comparingInt(contract::getSuitStrength)).orElseThrow()
                : candidates.stream().min(Comparator.comparingInt(Card::points)).orElseThrow();
        return new MoveExplanation(player, choice, requirement,
                "Bettel: versucht, die Spielerpartei zum Stichgewinn zu zwingen", true);
    }

    private MoveExplanation chooseRamschFollow(
            Player player,
            Trick trick,
            List<Card> legalMoves,
            List<Card> winningMoves,
            PlayedCard currentBest,
            GameContract contract,
            String requirement
    ) {
        List<Card> nonWinningMoves = legalMoves.stream()
                .filter(card -> !canBeat(card, currentBest.card(), trick.leadCard(), contract))
                .toList();
        List<Card> candidates = winningMoves.isEmpty() ? legalMoves : nonWinningMoves;
        Comparator<Card> priority = winningMoves.isEmpty()
                ? Comparator.comparingInt(Card::points)
                : Comparator.comparingInt(Card::points).reversed();
        Card choice = candidates.stream().min(priority).orElse(legalMoves.get(0));
        return new MoveExplanation(player, choice, requirement,
                "Ramsch: versucht, möglichst wenige Augen zu behalten", true);
    }

    private MoveExplanation chooseFollowMoveWithContext(
            Player player,
            Trick trick,
            List<Card> legalMoves,
            RufspielContract contract,
            Player partner,
            String ruleRequirement,
            DecisionContext context
    ) {
        PlayedCard currentBest = rules.evaluateTrickWinner(trick);
        boolean teammateWinning = isTeammate(player, currentBest.player(), contract, partner);

        if (teammateWinning) {
            // BASELINE MODE (A/B Test): Naives Schmieren ohne Gedächtnis
            if (!useMemoryEngine) {
                Card highestPoints = legalMoves.stream()
                        .filter(c -> !c.equals(contract.calledSau()))
                        .max(Comparator.comparingInt(Card::points))
                        .orElse(legalMoves.get(0));

                String act = highestPoints.points() >= 10
                        ? "Schmiert " + highestPoints.points() + " Augen (" + highestPoints.shortName() + "), da Teampartner " + currentBest.player().name() + " führt"
                        : "Legt zu (" + highestPoints.shortName() + "), Teampartner " + currentBest.player().name() + " hat den Stich";
                return new MoveExplanation(player, highestPoints, ruleRequirement, act, true);
            }

            // Check trick safety & ruff danger from opponents behind us
            GameMemory memory = context.memory();
            List<Player> remainingPlayers = context.remainingPlayers();
            boolean isTrickSafe = memory.isTrickSafeAgainstRemainingPlayers(trick, currentBest, remainingPlayers, contract, partner);
            boolean acuteRuffDanger = memory.acuteRuffDangerFromRemainingOpponents(trick, remainingPlayers, contract, partner);

            // =========================================================================
            // SÄULE 1: GEWINNSCHWELLEN-REGEL („Erst gewinnen, dann nachdenken“)
            // Wenn der Stich sicher beim Team ist und die 61-Augen-Grenze erreichbar ist:
            // KOMPROMISSLOS SCHMIEREN, auch eine blanke Farbsau!
            // =========================================================================
            if (isTrickSafe) {
                int teamAugen = memory.getTeamPoints(player, contract, partner);
                int trickAugen = trick.totalPoints();

                // Look for legal cards that push team points >= 61
                Card thresholdWinnerCard = legalMoves.stream()
                        .filter(c -> !c.equals(contract.calledSau())) // Cannot discard called Sau
                        .filter(c -> (teamAugen + trickAugen + c.points()) >= 61)
                        .max(Comparator.comparingInt(Card::points))
                        .orElse(null);

                if (thresholdWinnerCard != null) {
                    int finalPoints = teamAugen + trickAugen + thresholdWinnerCard.points();
                    return new MoveExplanation(
                            player,
                            thresholdWinnerCard,
                            ruleRequirement,
                            String.format("GEWINNSCHWELLE ERREICHT! Team hat bisher %d Augen + %d im Stich + %d Augen (%s) = %d Augen (>= 61) -> Sieg sofort gesichert! ('Erst gewinnen, dann nachdenken')",
                                    teamAugen, trickAugen, thresholdWinnerCard.points(), thresholdWinnerCard.shortName(), finalPoints),
                            true
                    );
                }
            }

            // =========================================================================
            // SÄULE 2: RISIKO-ABWÄGUNG BEI AKUTER ABSTICH-GEFAHR DURCH GEGNER HINTEN
            // Wenn ein nachfolgender Gegner in der angespielten Farbe nachweislich frei ist:
            // SCHMIEREN ABBRECHEN! Keine Punkte an den Gegner verschenken!
            // =========================================================================
            if (acuteRuffDanger || !isTrickSafe) {
                Card cheapestSafeCard = legalMoves.stream()
                        .filter(c -> !c.equals(contract.calledSau()))
                        .min(Comparator.comparingInt(Card::points))
                        .orElse(legalMoves.get(0));

                String dangerReason = acuteRuffDanger
                        ? "Gegner in Nachhand ist nachweislich farbfrei (akute Abstich-Gefahr!) -> Bricht Schmieren ab und wirft kleine Karte " + cheapestSafeCard.shortName() + " ab"
                        : "Stich nicht garantiert sicher (Gegner sitzen noch dahinter) -> Schont hohe Punkte und legt " + cheapestSafeCard.shortName() + " zu";

                return new MoveExplanation(player, cheapestSafeCard, ruleRequirement, dangerReason, true);
            }

            // =========================================================================
            // SÄULE 3: SCHMIER-HIERARCHIE BEI SICHEREM STICH (Punkte < 61)
            // 1. Blanke oder gefährdete 10 (muss weg, bevor sie abgestochen wird)
            // 2. Reguläre 10
            // 3. Blanke Sau (nur wenn keine Trümpfe mehr zum späteren Selbsteinholen)
            // 4. König (4 Augen)
            // 5. Kleine Karte
            // =========================================================================
            Card bestSmear = chooseBestSmearCard(player, legalMoves, contract, rules);
            if (bestSmear.points() >= 10) {
                return new MoveExplanation(
                        player,
                        bestSmear,
                        ruleRequirement,
                        "Schmiert " + bestSmear.points() + " Augen (" + bestSmear.shortName() + ") auf sicheren Stich von Teampartner " + currentBest.player().name(),
                        true
                );
            } else {
                return new MoveExplanation(
                        player,
                        bestSmear,
                        ruleRequirement,
                        "Legt zu (" + bestSmear.shortName() + "), Teampartner " + currentBest.player().name() + " hat den Stich sicher",
                        true
                );
            }

        } else {
            // Opponent holds the trick -> try to take it ("stechen" or beat with higher suit)
            List<Card> winningMoves = legalMoves.stream()
                    .filter(candidate -> canBeat(candidate, currentBest.card(), trick.leadCard()))
                    .toList();

            if (!winningMoves.isEmpty()) {
                // Pick the cheapest winning card (lowest points / lowest trump rank)
                Card cheapestWinner = winningMoves.stream()
                    .min(Comparator.comparingInt(Card::points)
                        .thenComparingInt(c -> rules.isTrump(c)
                            ? rules.getTrumpStrength(c)
                            : rules.getSuitStrength(c)))
                        .orElse(winningMoves.get(0));

                String act = rules.isTrump(cheapestWinner) && !rules.isTrump(trick.leadCard())
                        ? "Sticht mit Trumpf " + cheapestWinner.shortName() + " ein, um Stich von Gegner " + currentBest.player().name() + " abzuluchsen"
                        : "Übersticht " + currentBest.player().name() + " mit höherer Karte " + cheapestWinner.shortName();

                return new MoveExplanation(player, cheapestWinner, ruleRequirement, act, true);
            } else {
                // Cannot win the trick -> check if we can discard a card to get "freigespielt" (void in a suit)
                if (useMemoryEngine) {
                    Card tacticalDiscard = findTacticalFreispielenDiscard(player, legalMoves, contract, rules);
                    if (tacticalDiscard != null) {
                        return new MoveExplanation(
                                player,
                                tacticalDiscard,
                                ruleRequirement,
                                "Kann Stich nicht übernehmen -> wirft einzelne " + tacticalDiscard.shortName() + " ab, um sich in " + tacticalDiscard.suit().displayName() + " freizuspielen (künftig stechen!)",
                                true
                        );
                    }
                }

                // Fallback: Discard cheapest possible card (0 points: 7, 8, 9)
                Card cheapestDiscard = legalMoves.stream()
                        .min(Comparator.comparingInt(Card::points))
                        .orElse(legalMoves.get(0));

                return new MoveExplanation(
                        player,
                        cheapestDiscard,
                        ruleRequirement,
                        "Kann Stich nicht übernehmen -> gibt kleine Karte " + cheapestDiscard.shortName() + " ab (keine Augen an Gegner)",
                        true
                );
            }
        }
    }

    private MoveExplanation chooseConservativeFollowMove(
            Player player,
            List<Card> legalMoves,
            RufspielContract contract,
            String ruleRequirement
    ) {
        Card cheapestCard = legalMoves.stream()
                .filter(c -> !c.equals(contract.calledSau()))
                .min(Comparator.comparingInt(Card::points))
                .orElse(legalMoves.get(0));
        return new MoveExplanation(player, cheapestCard, ruleRequirement,
                "Nachhand-Spieler unbekannt -> schont Augen und vermeidet riskantes Schmieren mit "
                        + cheapestCard.shortName(), true);
    }

    /**
     * Evaluates the best card to smear when a teammate has a safe trick.
     */
    private Card chooseBestSmearCard(Player player, List<Card> legalMoves, RufspielContract contract, RuleEngine rules) {
        List<Card> candidates = legalMoves.stream()
                .filter(c -> !c.equals(contract.calledSau()))
                .toList();

        // 1. Look for a 10 (especially unshielded 10 without the Ace)
        Card unshielded10 = candidates.stream()
                .filter(c -> !rules.isTrump(c) && c.rank() == Rank.ZEHN)
                .filter(c -> player.hand().stream().noneMatch(held ->
                        rules.isNonTrumpSuit(held, c.suit()) && held.rank() == Rank.SAU))
                .findFirst()
                .orElse(null);
        if (unshielded10 != null) {
            return unshielded10;
        }

        // 2. Any 10 (including Trump-10)
        Card any10 = candidates.stream()
                .filter(c -> c.rank() == Rank.ZEHN)
                .findFirst()
                .orElse(null);
        if (any10 != null) {
            return any10;
        }

        // 3. Farbsau (Ace) -> smear if player has no trumps left (cannot regain lead)
        boolean hasNoTrumps = player.hand().stream().noneMatch(rules::isTrump);
        if (hasNoTrumps) {
            Card farbSau = candidates.stream()
                    .filter(c -> !rules.isTrump(c) && c.rank() == Rank.SAU)
                    .findFirst()
                    .orElse(null);
            if (farbSau != null) {
                return farbSau;
            }
        }

        // 4. Highest points among remaining legal moves (König = 4 points, or Sau)
        return candidates.stream()
                .max(Comparator.comparingInt(Card::points))
                .orElse(candidates.get(0));
    }

    /**
     * Checks whether discarding a specific card makes the player completely void in that Fehlfarbe,
     * allowing them to ruff (stechen) in subsequent tricks.
     */
    private Card findTacticalFreispielenDiscard(Player player, List<Card> legalMoves, RufspielContract contract, RuleEngine rules) {
        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            if (suit == contract.calledSuit()) continue; // Do not discard search suit frivolously

            List<Card> suitCardsInHand = player.hand().stream()
                    .filter(c -> rules.isNonTrumpSuit(c, suit))
                    .toList();

            // If player holds exactly 1 card of this suit, and it's 0 points (7, 8, 9) and legal to play:
            if (suitCardsInHand.size() == 1) {
                Card singleCard = suitCardsInHand.get(0);
                if (singleCard.points() == 0 && legalMoves.contains(singleCard)) {
                    return singleCard;
                }
            }
        }
        return null;
    }

    private boolean canBeat(Card candidate, Card bestSoFar, Card leadCard) {
        if (rules.isTrump(candidate)) {
            if (!rules.isTrump(bestSoFar)) {
                return true;
            }
            return rules.getTrumpStrength(candidate) > rules.getTrumpStrength(bestSoFar);
        } else {
            if (rules.isTrump(bestSoFar)) {
                return false;
            }
            if (candidate.suit() != leadCard.suit()) {
                return false;
            }
            return rules.getSuitStrength(candidate) > rules.getSuitStrength(bestSoFar);
        }
    }

    private boolean canBeat(Card candidate, Card bestSoFar, Card leadCard, GameContract contract) {
        boolean candidateIsTrump = contract.isTrump(candidate, rules);
        boolean bestIsTrump = contract.isTrump(bestSoFar, rules);
        if (candidateIsTrump) {
            return !bestIsTrump || contract.getTrumpStrength(candidate) > contract.getTrumpStrength(bestSoFar);
        }
        if (bestIsTrump || candidate.suit() != leadCard.suit()) {
            return false;
        }
        return contract.getSuitStrength(candidate) > contract.getSuitStrength(bestSoFar);
    }

    private boolean isTeammate(Player p1, Player p2, RufspielContract contract, Player partner) {
        if (p1.equals(p2)) return true;
        boolean p1IsCallerTeam = p1.equals(contract.caller()) || p1.equals(partner);
        boolean p2IsCallerTeam = p2.equals(contract.caller()) || p2.equals(partner);
        return p1IsCallerTeam == p2IsCallerTeam;
    }
}