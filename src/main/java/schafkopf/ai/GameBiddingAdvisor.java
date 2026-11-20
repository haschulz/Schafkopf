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
import java.util.Set;
import java.util.function.Consumer;

public final class GameBiddingAdvisor {
    private static final String SIDE_SUIT_WINNERS = "sideSuitWinners";
    private static final String VOID_SIDE_SUITS = "voidSideSuits";
    private static final String SINGLETON_SIDE_SUITS = "singletonSideSuits";
    private static final String LONG_SIDE_SUITS_WITHOUT_ACE = "longSideSuitsWithoutAce";
    private static final String SIDE_TENS_WITH_ACE = "sideTensWithAce";

    public record ContractRecommendation(GameContract contract, double score) {
    }

    private final RuleEngine rules;
    private final PolicyGenome genome;
    private final Consumer<DecisionTrace> traceSink;
    private final ContractValueModel valueModel;
    private DecisionAnalysis.Report lastAnalysis;

    public DecisionAnalysis.Report lastAnalysis() {
        return lastAnalysis;
    }

    public ContractRecommendation recommendWithAnalysis(
            Player player, Set<GameType> allowedGameTypes, DecisionInformation information,
            GameContract standingBid, int samples
    ) {
        if (!player.hand().equals(information.hand()) || !information.publicPlays().isEmpty()) {
            throw new IllegalArgumentException("Bidding information must describe the acting player's full hand");
        }
        List<GameContract> contracts = analyticalContracts(player, allowedGameTypes, standingBid);
        if (contracts.isEmpty()) {
            lastAnalysis = null;
            return null;
        }
        List<schafkopf.analysis.GameRecord.ContractSpec> specs = contracts.stream()
                .map(contract -> specFor(contract, information.observerSeat())).toList();
        var current = standingBid == null ? null : specFor(standingBid,
                Integer.parseInt(standingBid.caller().id()) - 1);
        lastAnalysis = new DecisionAnalysis(DecisionAnalysis.opponentPool()).contracts(
                information, specs, current, samples, information.seed());
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Map<String, Double>> features = new LinkedHashMap<>();
        lastAnalysis.alternatives().forEach((action, estimate) -> {
            scores.put(action, estimate.expectedReturnCents());
            features.put(action, Map.of("winProbability", estimate.winProbability(),
                    "probabilityLow", estimate.probabilityLow(), "probabilityHigh", estimate.probabilityHigh(),
                    "opponentEyes", estimate.opponentEyes(), "opponentTricks", estimate.opponentTricks(),
                    "samples", (double) estimate.worlds()));
        });
        String selection = lastAnalysis.selectedAction();
        traceSink.accept(new DecisionTrace(genome.id(), DecisionTrace.Stage.BIDDING, "ANALYSIS",
                player.id(), new ArrayList<>(scores.keySet()), selection, scores, features,
                "Vergleich erlaubter Verträge mit PASS auf gleichen Verteilungen. " + lastAnalysis.uncertainty()));
        for (int index = 0; index < contracts.size(); index++) {
            if (DecisionAnalysis.contractKey(specs.get(index)).equals(selection)) {
                return new ContractRecommendation(contracts.get(index), scores.get(selection));
            }
        }
        return null;
    }

    private List<GameContract> analyticalContracts(Player player, Set<GameType> allowed, GameContract standingBid) {
        List<GameContract> candidates = new ArrayList<>();
        for (Suit suit : new BiddingAdvisor(rules, genome).getValidRufspielSuits(player)) {
            candidates.add(new RufspielContract(player, suit));
        }
        for (Suit suit : Suit.values()) {
            candidates.add(new SoloContract(player, suit));
            candidates.add(new SoloContract(player, suit, true));
            candidates.add(new WenzContract(player, suit));
        }
        candidates.add(new WenzContract(player));
        candidates.add(new WenzContract(player, true));
        var biddingRules = new schafkopf.rules.GameBiddingRules();
        return candidates.stream().filter(contract -> allowed.contains(contract.gameType()))
                .filter(contract -> biddingRules.canAnnounce(contract, standingBid)).toList();
    }

    private schafkopf.analysis.GameRecord.ContractSpec specFor(GameContract contract, int seat) {
        Suit suit = null;
        if (contract instanceof RufspielContract rufspiel) {
            suit = rufspiel.calledSuit();
        } else if (contract instanceof SoloContract solo) {
            suit = solo.trumpSuit();
        } else if (contract instanceof WenzContract wenz) {
            suit = wenz.trumpSuit();
        }
        return new schafkopf.analysis.GameRecord.ContractSpec(contract.gameType(), seat, suit, contract.isTout());
    }

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
        return recommend(player, personality, Set.of(GameType.values()));
    }

    public ContractRecommendation recommend(
            Player player,
            Personality personality,
            Set<GameType> allowedGameTypes
    ) {
        List<ContractRecommendation> candidates = new ArrayList<>();
        addRufspielCandidate(player, personality, candidates);
        addSoloCandidates(player, candidates);
        addWenzCandidates(player, candidates);
        addBettelCandidate(player, candidates);
        candidates.removeIf(candidate -> !allowedGameTypes.contains(candidate.contract().gameType()));
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
        long oberCount = player.hand().stream()
            .filter(card -> contract.isTrump(card, rules) && card.rank() == Rank.OBER)
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
        Map<String, Double> sideSuits = sideSuitFeatures(player, contract);
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
                Map.entry("oberCount", (double) oberCount),
                Map.entry("unterCount", (double) unterCount),
                Map.entry("nonTrumpOberCount", (double) nonTrumpOberCount),
            Map.entry("trumpPoints", trumpPoints / 11.0),
            Map.entry("trumpControl", trumpControl),
            Map.entry("aces", (double) aces),
            Map.entry("highCards", (double) highCards),
            Map.entry("lowCards", (double) lowCards),
            Map.entry(SIDE_SUIT_WINNERS, sideSuits.get(SIDE_SUIT_WINNERS)),
            Map.entry(VOID_SIDE_SUITS, sideSuits.get(VOID_SIDE_SUITS)),
            Map.entry(SINGLETON_SIDE_SUITS, sideSuits.get(SINGLETON_SIDE_SUITS)),
            Map.entry(LONG_SIDE_SUITS_WITHOUT_ACE, sideSuits.get(LONG_SIDE_SUITS_WITHOUT_ACE)),
            Map.entry(SIDE_TENS_WITH_ACE, sideSuits.get(SIDE_TENS_WITH_ACE)),
            Map.entry("bettelHighCards", (double) bettelHighCards),
            Map.entry("bettelTopCards", (double) bettelTopCards),
                Map.entry("bettelLowCards", (double) bettelLowCards),
                Map.entry("chosenSuitTrumpCount", (double) chosenSuitTrumpCount),
                Map.entry("chosenSuitTrumpHighCards", (double) chosenSuitTrumpHighCards),
                Map.entry("chosenSuitTrumpPoints", chosenSuitTrumpPoints / 11.0));
    }

        private Map<String, Double> sideSuitFeatures(Player player, GameContract contract) {
        List<List<Card>> suits = java.util.Arrays.stream(Suit.values())
            .filter(suit -> java.util.Arrays.stream(Rank.values())
                .anyMatch(rank -> !contract.isTrump(new Card(suit, rank), rules)))
            .map(suit -> player.hand().stream()
                .filter(card -> card.suit() == suit && !contract.isTrump(card, rules)).toList())
            .toList();
        return Map.of(
                SIDE_SUIT_WINNERS, (double) suits.stream().filter(cards -> cards.stream()
                .anyMatch(card -> contract.getSuitStrength(card) >= 5)).count(),
                VOID_SIDE_SUITS, (double) suits.stream().filter(List::isEmpty).count(),
                SINGLETON_SIDE_SUITS, (double) suits.stream().filter(cards -> cards.size() == 1).count(),
                LONG_SIDE_SUITS_WITHOUT_ACE, (double) suits.stream()
                .filter(cards -> cards.size() >= 3 && cards.stream()
                    .noneMatch(card -> card.rank() == Rank.SAU)).count(),
                SIDE_TENS_WITH_ACE, (double) suits.stream().filter(cards ->
                cards.stream().anyMatch(card -> card.rank() == Rank.SAU)
                && cards.stream().anyMatch(card -> card.rank() == Rank.ZEHN)).count());
        }

        private Map<String, Double> decisionFeatures(Player player, GameContract contract) {
        Map<String, Double> features = new LinkedHashMap<>(featuresFor(player, contract));
        features.put("expectedValueCents", valueModel.estimate(contract.gameType(), features));
        return features;
    }
}