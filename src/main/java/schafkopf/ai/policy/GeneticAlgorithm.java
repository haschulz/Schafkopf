package schafkopf.ai.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class GeneticAlgorithm {
    @FunctionalInterface
    public interface FitnessEvaluator {
        double evaluate(PolicyGenome genome);
    }

    @FunctionalInterface
    public interface PopulationFitnessEvaluator {
        List<Double> evaluate(List<PolicyGenome> population);
    }

    public record Evaluation(PolicyGenome genome, double fitness) {
        public Evaluation {
            Objects.requireNonNull(genome, "Genome must not be null");
            if (!Double.isFinite(fitness)) {
                throw new IllegalArgumentException("Fitness must be finite");
            }
        }
    }

    public record EvolutionResult(
            PolicyGenome champion,
            double championFitness,
            int evaluationCount,
            List<Double> bestFitnessByGeneration
    ) {
        public EvolutionResult {
            bestFitnessByGeneration = List.copyOf(bestFitnessByGeneration);
        }
    }

    private static final int TOURNAMENT_SIZE = 3;
    private static final Comparator<Evaluation> FITNESS_ORDER = Comparator
            .comparingDouble(Evaluation::fitness)
            .reversed()
            .thenComparing(evaluation -> evaluation.genome().id());

    public EvolutionResult evolve(
            List<PolicyGenome> initialPopulation,
            int generations,
            int populationSize,
            int eliteCount,
            double mutationStandardDeviation,
            long randomSeed,
            FitnessEvaluator evaluator
    ) {
            Objects.requireNonNull(evaluator, "Fitness evaluator must not be null");
            return evolvePopulation(initialPopulation, generations, populationSize, eliteCount,
                mutationStandardDeviation, randomSeed,
                population -> population.stream().map(evaluator::evaluate).toList());
            }

            public EvolutionResult evolvePopulation(
                List<PolicyGenome> initialPopulation,
                int generations,
                int populationSize,
                int eliteCount,
                double mutationStandardDeviation,
                long randomSeed,
                PopulationFitnessEvaluator evaluator
            ) {
        validateParameters(initialPopulation, generations, populationSize, eliteCount,
                mutationStandardDeviation, evaluator);
        Random random = new Random(randomSeed);
        List<PolicyGenome> population = new ArrayList<>(initialPopulation);
        while (population.size() < populationSize) {
            PolicyGenome parent = population.get(random.nextInt(population.size()));
            population.add(parent.mutate(random, mutationStandardDeviation, "initial-" + population.size()));
        }

        List<Double> bestByGeneration = new ArrayList<>();
        int evaluationCount = 0;
        List<Evaluation> evaluated = evaluate(population, evaluator);
        evaluationCount += evaluated.size();
        bestByGeneration.add(evaluated.get(0).fitness());

        for (int generation = 1; generation <= generations; generation++) {
            List<PolicyGenome> nextGeneration = new ArrayList<>(populationSize);
            for (int i = 0; i < eliteCount; i++) {
                nextGeneration.add(evaluated.get(i).genome());
            }
            while (nextGeneration.size() < populationSize) {
                PolicyGenome firstParent = selectParent(evaluated, random);
                PolicyGenome secondParent = selectParent(evaluated, random);
                String childId = "generation-" + generation + "-" + nextGeneration.size();
                PolicyGenome child = firstParent.crossover(secondParent, random, childId)
                        .mutate(random, mutationStandardDeviation, childId);
                nextGeneration.add(child);
            }
            population = nextGeneration;
            evaluated = evaluate(population, evaluator);
            evaluationCount += evaluated.size();
            bestByGeneration.add(evaluated.get(0).fitness());
        }

        Evaluation champion = evaluated.get(0);
        return new EvolutionResult(champion.genome(), champion.fitness(), evaluationCount, bestByGeneration);
    }

    private List<Evaluation> evaluate(List<PolicyGenome> population, PopulationFitnessEvaluator evaluator) {
        List<Double> fitnessValues = evaluator.evaluate(List.copyOf(population));
        if (fitnessValues.size() != population.size()) {
            throw new IllegalStateException("Population fitness evaluator must return one value per genome");
        }
        List<Evaluation> evaluated = new ArrayList<>(population.size());
        for (int i = 0; i < population.size(); i++) {
            evaluated.add(new Evaluation(population.get(i), fitnessValues.get(i)));
        }
        return evaluated.stream().sorted(FITNESS_ORDER).toList();
    }

    private PolicyGenome selectParent(List<Evaluation> population, Random random) {
        Evaluation best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Evaluation candidate = population.get(random.nextInt(population.size()));
            if (best == null || FITNESS_ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best).genome();
    }

    private void validateParameters(
            List<PolicyGenome> initialPopulation,
            int generations,
            int populationSize,
            int eliteCount,
            double mutationStandardDeviation,
            PopulationFitnessEvaluator evaluator
    ) {
        if (initialPopulation == null || initialPopulation.isEmpty()) {
            throw new IllegalArgumentException("Initial population must not be empty");
        }
        if (initialPopulation.size() > populationSize) {
            throw new IllegalArgumentException("Initial population cannot exceed populationSize");
        }
        if (generations < 0 || populationSize < 2 || eliteCount < 1 || eliteCount >= populationSize) {
            throw new IllegalArgumentException("Invalid generation, population, or elite count");
        }
        if (!Double.isFinite(mutationStandardDeviation) || mutationStandardDeviation < 0) {
            throw new IllegalArgumentException("Mutation standard deviation must be finite and non-negative");
        }
        Objects.requireNonNull(evaluator, "Fitness evaluator must not be null");
    }
}