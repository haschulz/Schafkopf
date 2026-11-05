package schafkopf.analysis;

import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ContractMatrixOptimizer {
    private final ContractMatrixValidator validator;

    public ContractMatrixOptimizer(SchafkopfConfig config) {
        validator = new ContractMatrixValidator(config);
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
        List<PolicyGenome> cardPopulation = initialPopulation.stream()
                .map(this::completeCardWeights)
                .map(this::cardOnlyGenome)
                .toList();
        AtomicInteger evaluationBatch = new AtomicInteger();
        PolicyGenome comparison = PolicyGenome.baseline().withId("matrix-baseline");
        GeneticAlgorithm.EvolutionResult result = new GeneticAlgorithm().evolvePopulation(
                cardPopulation, generations, populationSize, eliteCount, mutationStandardDeviation, seed,
                population -> {
                    long batchSeed = PolicyTournament.seedForEvaluationBatch(
                            seed, evaluationBatch.getAndIncrement(), dealsPerEvaluation);
                    return population.stream()
                            .map(candidate -> mergeCardWeights(initialPopulation.get(0), candidate))
                            .map(candidate -> validator.validate(candidate, comparison, dealsPerEvaluation, batchSeed)
                                    .meanPairedDeltaCents())
                            .toList();
                });
        PolicyGenome champion = mergeCardWeights(initialPopulation.get(0), result.champion());
        return new GeneticAlgorithm.EvolutionResult(champion, result.championFitness(),
                result.evaluationCount(), result.bestFitnessByGeneration());
    }

    private PolicyGenome completeCardWeights(PolicyGenome genome) {
        PolicyGenome defaults = PolicyGenome.baseline();
        PolicyGenome completed = genome;
        for (Map.Entry<String, Double> weight : defaults.weights().entrySet()) {
            if (weight.getKey().startsWith("card.") && !completed.weights().containsKey(weight.getKey())) {
                completed = completed.withWeight(weight.getKey(), weight.getValue());
            }
        }
        return completed;
    }

    private PolicyGenome cardOnlyGenome(PolicyGenome genome) {
        Map<String, Double> cardWeights = new HashMap<>();
        genome.weights().forEach((key, value) -> {
            if (key.startsWith("card.")) {
                cardWeights.put(key, value);
            }
        });
        return new PolicyGenome(genome.schemaVersion(), genome.id(), cardWeights);
    }

    private PolicyGenome mergeCardWeights(PolicyGenome base, PolicyGenome cardGenome) {
        PolicyGenome merged = base.withId(cardGenome.id());
        List<String> existingCardKeys = merged.weights().keySet().stream()
                .filter(key -> key.startsWith("card."))
                .toList();
        for (String key : existingCardKeys) {
            merged = merged.withWeight(key, cardGenome.weight(key));
        }
        for (Map.Entry<String, Double> weight : cardGenome.weights().entrySet()) {
            merged = merged.withWeight(weight.getKey(), weight.getValue());
        }
        return merged;
    }
}