package schafkopf.analysis;

import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Suit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class FarbWenzSoloistOptimizer {
    private static final double LOWER_TAIL_PENALTY = 0.25;

    public record GenomeScore(
            String genomeId,
            int games,
            double averageNetCents,
            double worstDecileAverageNetCents,
            double fitness,
            Map<Suit, Double> averageByTrumpSuit
    ) {
        public GenomeScore {
            averageByTrumpSuit = Map.copyOf(averageByTrumpSuit);
        }
    }

    public record Evaluation(long seed, int independentDeals, List<GenomeScore> genomes) {
        public Evaluation {
            genomes = List.copyOf(genomes);
        }
    }

    private final PolicyTournament tournament;

    public FarbWenzSoloistOptimizer(SchafkopfConfig config) {
        tournament = new PolicyTournament(config);
    }

    public Evaluation evaluate(List<PolicyGenome> population, int independentDeals, long seed) {
        if (population == null || population.size() < 4) {
            throw new IllegalArgumentException("Farb-Wenz optimization needs at least four genomes");
        }
        if (independentDeals < 2) {
            throw new IllegalArgumentException("At least two deals are required for optimization");
        }

        List<GenomeScore> scores = new ArrayList<>(population.size());
        for (PolicyGenome genome : population) {
            scores.add(evaluateGenome(genome, independentDeals, seed));
        }
        return new Evaluation(seed, independentDeals, scores);
    }

    public GeneticAlgorithm.EvolutionResult evolve(
            List<PolicyGenome> initialPopulation,
            int generations,
            int populationSize,
            int eliteCount,
            double mutationStandardDeviation,
            int dealsPerEvaluation,
            long seed
    ) {
            List<PolicyGenome> compatiblePopulation = initialPopulation.stream()
                .map(this::ensureFarbwenzCardWeights)
                    .map(this::farbwenzCardGenome)
                .toList();
            AtomicInteger evaluationBatch = new AtomicInteger();
            GeneticAlgorithm.EvolutionResult specializedResult = new GeneticAlgorithm().evolvePopulation(
                compatiblePopulation, generations, populationSize, eliteCount, mutationStandardDeviation, seed,
                population -> {
                    long batchSeed = PolicyTournament.seedForEvaluationBatch(
                            seed, evaluationBatch.getAndIncrement(), dealsPerEvaluation);
                    return evaluate(population, dealsPerEvaluation, batchSeed).genomes().stream()
                            .map(GenomeScore::fitness)
                            .toList();
                });
            PolicyGenome champion = initialPopulation.get(0).withId(specializedResult.champion().id());
            for (Map.Entry<String, Double> weight : specializedResult.champion().weights().entrySet()) {
                champion = champion.withWeight(weight.getKey(), weight.getValue());
            }
            return new GeneticAlgorithm.EvolutionResult(champion, specializedResult.championFitness(),
                    specializedResult.evaluationCount(), specializedResult.bestFitnessByGeneration());
    }

        private PolicyGenome farbwenzCardGenome(PolicyGenome genome) {
            Map<String, Double> cardWeights = new HashMap<>();
            genome.weights().forEach((key, value) -> {
                if (key.startsWith("card.FARBWENZ.")) {
                    cardWeights.put(key, value);
                }
            });
            return new PolicyGenome(genome.schemaVersion(), genome.id(), cardWeights);
        }

            private PolicyGenome ensureFarbwenzCardWeights(PolicyGenome genome) {
            PolicyGenome defaults = PolicyGenome.baseline();
            PolicyGenome completed = genome;
            for (Map.Entry<String, Double> weight : defaults.weights().entrySet()) {
                if (weight.getKey().startsWith("card.FARBWENZ.")) {
                completed = completed.weights().containsKey(weight.getKey())
                    ? completed
                    : completed.withWeight(weight.getKey(), weight.getValue());
                }
            }
            return completed;
            }

    private GenomeScore evaluateGenome(PolicyGenome genome, int deals, long seed) {
        List<PolicyGenome> opponents = new ArrayList<>(4);
        opponents.add(genome);
        opponents.addAll(ValidationOpponentPool.controls("fzw-training"));
        List<Double> returnsByDeal = new ArrayList<>(deals);
        Map<Suit, Long> netBySuit = new HashMap<>();

        for (int deal = 0; deal < deals; deal++) {
            long dealNet = 0;
            for (Suit trumpSuit : Suit.values()) {
                long suitNet = 0;
                for (int callerSeat = 0; callerSeat < 4; callerSeat++) {
                    int startingSeat = Math.floorMod(deal + callerSeat + trumpSuit.ordinal(), 4);
                    List<Integer> assignments = assignmentsWithCaller(callerSeat);
                    long candidateReturn = tournament.runFixedFarbWenzMatch(
                            opponents, assignments, callerSeat, trumpSuit, startingSeat, seed + deal)
                            .netCentsByGenome().get(0);
                    suitNet += candidateReturn;
                }
                dealNet += suitNet;
                netBySuit.merge(trumpSuit, suitNet, Long::sum);
            }
            returnsByDeal.add(dealNet / 16.0);
        }

        double mean = returnsByDeal.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double tail = worstDecileAverage(returnsByDeal);
        Map<Suit, Double> suitAverages = new HashMap<>();
        for (Suit suit : Suit.values()) {
            suitAverages.put(suit, (double) netBySuit.getOrDefault(suit, 0L) / (deals * 4));
        }
        double fitness = mean - LOWER_TAIL_PENALTY * Math.max(0.0, -tail);
        return new GenomeScore(genome.id(), deals * 16, mean, tail, fitness, suitAverages);
    }

    private List<Integer> assignmentsWithCaller(int callerSeat) {
        List<Integer> assignment = new ArrayList<>(List.of(-1, -1, -1, -1));
        int nextOpponent = 1;
        for (int seat = 0; seat < 4; seat++) {
            assignment.set(seat, seat == callerSeat ? 0 : nextOpponent++);
        }
        return assignment;
    }

    private double worstDecileAverage(List<Double> returnsByDeal) {
        List<Double> sorted = returnsByDeal.stream().sorted().toList();
        int tailCount = Math.max(1, (int) Math.ceil(sorted.size() * 0.10));
        return sorted.subList(0, tailCount).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}