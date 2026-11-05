package schafkopf.ai;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.ai.policy.ContractValueModel;
import schafkopf.model.BettelContract;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.GeierContract;
import schafkopf.model.Personality;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public final class GameBiddingAdvisor {
    public record ContractRecommendation(GameContract contract, double score) {
    }

    private final RuleEngine rules;
    private final PolicyGenome genome;
    private final Consumer<DecisionTrace> traceSink;
    private final ContractValueModel valueModel;

    public GameBiddingAdvisor(RuleEngine rules, PolicyGenome genome) {
        this(rules, genome, ignored -> { }, ContractValueModel.loadConfigured());
    }

    public GameBiddingAdvisor(RuleEngine rules, PolicyGenome genome, Consumer<DecisionTrace> traceSink) {
        this(rules, genome, traceSink, ContractValueModel.loadConfigured());
    }

    public GameBiddingAdvisor(
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

    public ContractRecommendation recommend(Player player, Personality personality) {
        List<ContractRecommendation> candidates = new ArrayList<>();
        addRufspielCandidate(player, personality, candidates);
        addSoloCandidates(player, candidates);
        addWenzCandidates(player, candidates);
        addBettelCandidate(player, candidates);
        ContractRecommendation recommendation = candidates.stream()
                .max(Comparator.comparingDouble(ContractRecommendation::score))
                .orElse(null);
        Map<String, Double> scores = new LinkedHashMap<>();
        candidates.forEach(candidate -> scores.put(candidate.contract().gameType().name(), candidate.score()));
        scores.putIfAbsent("PASS", 0.0);
        String selected = recommendation == null ? "PASS" : recommendation.contract().gameType().name();
        List<String> legalActions = new ArrayList<>(scores.keySet());
        Map<String, Map<String, Double>> featureTrace = new LinkedHashMap<>();
        for (ContractRecommendation candidate : candidates) {
            String action = candidate.contract().gameType().name();
            featureTrace.put(action, decisionFeatures(player, candidate.contract()));
        }
        featureTrace.putIfAbsent("PASS", Map.of());
        traceSink.accept(new DecisionTrace(genome.id(), DecisionTrace.Stage.BIDDING,
            recommendation == null ? "NONE" : recommendation.contract().gameType().name(),
            player.id(), legalActions, selected, scores, featureTrace,
            recommendation == null ? "No contract reached its genome threshold" : recommendation.contract().displayName()));
        return recommendation;
    }

    private void addRufspielCandidate(
            Player player,
            Personality personality,
            List<ContractRecommendation> candidates
    ) {
        BiddingAdvisor.BiddingDecision decision = new BiddingAdvisor(rules, genome, ignored -> { }, valueModel)
                .evaluatePlayer(player, personality);
        if (decision.willPlay() && decision.recommendedSuit() != null) {
            candidates.add(new ContractRecommendation(
                    new RufspielContract(player, decision.recommendedSuit()), decision.handScore()));
        }
    }

    private void addSoloCandidates(Player player, List<ContractRecommendation> candidates) {
        for (Suit suit : Suit.values()) {
            addCandidate(player, new SoloContract(player, suit), candidates);
            addCandidate(player, new WenzContract(player, suit), candidates);
        }
    }

    private void addWenzCandidates(Player player, List<ContractRecommendation> candidates) {
        addCandidate(player, new GeierContract(player), candidates);
        addCandidate(player, new WenzContract(player), candidates);
        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.HERZ, Suit.SCHELLEN)) {
            addCandidate(player, new WenzContract(player, suit), candidates);
        }
    }

    private void addBettelCandidate(Player player, List<ContractRecommendation> candidates) {
        addCandidate(player, new BettelContract(player), candidates);
    }

    private void addCandidate(Player player, GameContract contract, List<ContractRecommendation> candidates) {
        String prefix = "bid." + contract.gameType().name() + ".";
        if (!genome.weights().containsKey(prefix + "threshold")) {
            return;
        }
        Map<String, Double> decisionFeatures = decisionFeatures(player, contract);
        double expectedValueCents = decisionFeatures.get("expectedValueCents");
        double minimumExpectedValue = genome.weight(prefix + "minimumExpectedValueCents");
        if (valueModel.isCalibrated(contract.gameType()) && expectedValueCents < minimumExpectedValue) {
            return;
        }
        double score = genome.score(decisionFeatures, prefix);
        if (score >= genome.weight(prefix + "threshold")) {
            candidates.add(new ContractRecommendation(contract, score));
        }
    }

    public Map<String, Double> featuresFor(Player player, GameContract contract) {
        long trumpCount = player.hand().stream().filter(card -> contract.isTrump(card, rules)).count();
        long topTrumps = player.hand().stream()
                .filter(card -> contract.isTrump(card, rules))
                .filter(card -> card.rank() == Rank.OBER || card.rank() == Rank.UNTER)
                .count();
        long unterCount = player.hand().stream()
            .filter(card -> contract.isTrump(card, rules) && card.rank() == Rank.UNTER)
            .count();
        long nonTrumpOberCount = player.hand().stream()
            .filter(card -> !contract.isTrump(card, rules) && card.rank() == Rank.OBER)
            .count();
        int trumpPoints = player.hand().stream()
                .filter(card -> contract.isTrump(card, rules))
                .mapToInt(Card::points)
                .sum();
        long aces = player.hand().stream()
                .filter(card -> card.rank() == Rank.SAU && !contract.isTrump(card, rules))
                .count();
        long highCards = player.hand().stream()
                .filter(card -> card.rank() == Rank.SAU || card.rank() == Rank.ZEHN)
                .count();
        long lowCards = player.hand().stream().filter(card -> card.points() == 0).count();
        double trumpControl = player.hand().stream()
            .filter(card -> contract.isTrump(card, rules))
            .mapToInt(contract::getTrumpStrength)
            .sum() / 14.0;
        int sideSuitWinners = 0;
        int voidSideSuits = 0;
        int singletonSideSuits = 0;
        int longSideSuitsWithoutAce = 0;
        int sideTensWithAce = 0;
        int bettelHighCards = 0;
        int bettelTopCards = 0;
        int bettelLowCards = 0;
        long chosenSuitTrumpCount = 0;
        long chosenSuitTrumpHighCards = 0;
        int chosenSuitTrumpPoints = 0;
        if (contract instanceof WenzContract wenz && wenz.trumpSuit() != null) {
            Suit trumpSuit = wenz.trumpSuit();
            List<Card> suitTrumps = player.hand().stream()
                .filter(card -> card.suit() == trumpSuit && card.rank() != Rank.UNTER)
                .filter(card -> contract.isTrump(card, rules))
                .toList();
            chosenSuitTrumpCount = suitTrumps.size();
            chosenSuitTrumpHighCards = suitTrumps.stream()
                .filter(card -> card.rank() == Rank.SAU || card.rank() == Rank.ZEHN)
                .count();
            chosenSuitTrumpPoints = suitTrumps.stream().mapToInt(Card::points).sum();
        }
        for (Suit suit : Suit.values()) {
            List<Card> sideCards = player.hand().stream()
                .filter(card -> !contract.isTrump(card, rules) && card.suit() == suit)
                .toList();
            if (sideCards.isEmpty()) {
            voidSideSuits++;
            } else {
            int highestSuitStrength = sideCards.stream().mapToInt(contract::getSuitStrength).max().orElse(0);
            if (highestSuitStrength >= 5) {
                sideSuitWinners++;
            }
            if (sideCards.size() == 1) {
                singletonSideSuits++;
            }
            boolean hasAce = sideCards.stream().anyMatch(card -> card.rank() == Rank.SAU);
            if (sideCards.size() >= 3 && !hasAce) {
                longSideSuitsWithoutAce++;
            }
            if (hasAce && sideCards.stream().anyMatch(card -> card.rank() == Rank.ZEHN)) {
                sideTensWithAce++;
            }
            }
        }
        if (contract.gameType() == GameType.BETTEL) {
            bettelHighCards = (int) player.hand().stream()
                .filter(card -> contract.getSuitStrength(card) >= 5)
                .count();
            bettelTopCards = (int) player.hand().stream()
                .filter(card -> card.rank() == Rank.SAU)
                .count();
            bettelLowCards = (int) player.hand().stream()
                .filter(card -> card.rank() == Rank.SIEBEN || card.rank() == Rank.ACHT
                    || card.rank() == Rank.NEUN)
                .count();
        }

        return Map.ofEntries(
            Map.entry("trumpCount", (double) trumpCount),
            Map.entry("topTrumps", (double) topTrumps),
                Map.entry("unterCount", (double) unterCount),
                Map.entry("nonTrumpOberCount", (double) nonTrumpOberCount),
            Map.entry("trumpPoints", trumpPoints / 11.0),
            Map.entry("trumpControl", trumpControl),
            Map.entry("aces", (double) aces),
            Map.entry("highCards", (double) highCards),
            Map.entry("lowCards", (double) lowCards),
            Map.entry("sideSuitWinners", (double) sideSuitWinners),
            Map.entry("voidSideSuits", (double) voidSideSuits),
            Map.entry("singletonSideSuits", (double) singletonSideSuits),
            Map.entry("longSideSuitsWithoutAce", (double) longSideSuitsWithoutAce),
            Map.entry("sideTensWithAce", (double) sideTensWithAce),
            Map.entry("bettelHighCards", (double) bettelHighCards),
            Map.entry("bettelTopCards", (double) bettelTopCards),
                Map.entry("bettelLowCards", (double) bettelLowCards),
                Map.entry("chosenSuitTrumpCount", (double) chosenSuitTrumpCount),
                Map.entry("chosenSuitTrumpHighCards", (double) chosenSuitTrumpHighCards),
                Map.entry("chosenSuitTrumpPoints", chosenSuitTrumpPoints / 11.0));
    }

    private Map<String, Double> decisionFeatures(Player player, GameContract contract) {
        Map<String, Double> features = new LinkedHashMap<>(featuresFor(player, contract));
        features.put("expectedValueCents", valueModel.estimate(contract.gameType(), features));
        return features;
    }
}