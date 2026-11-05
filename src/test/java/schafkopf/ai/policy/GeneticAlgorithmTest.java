package schafkopf.ai.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneticAlgorithmTest {
    @Test
    void seededEvolutionIsReproducibleAndPreservesTheBestGenome() {
        List<PolicyGenome> initialPopulation = List.of(
                genome("low", -1.0),
                genome("best", 5.0),
                genome("middle", 1.0));
        GeneticAlgorithm algorithm = new GeneticAlgorithm();
        GeneticAlgorithm.FitnessEvaluator fitness = candidate -> candidate.weight("target");

        GeneticAlgorithm.EvolutionResult first = algorithm.evolve(
                initialPopulation, 5, 8, 2, 0.5, 1234L, fitness);
        GeneticAlgorithm.EvolutionResult second = algorithm.evolve(
                initialPopulation, 5, 8, 2, 0.5, 1234L, fitness);

        assertEquals(first, second);
        assertTrue(first.championFitness() >= 5.0);
        assertEquals(48, first.evaluationCount());
        double previousBest = Double.NEGATIVE_INFINITY;
        for (double generationBest : first.bestFitnessByGeneration()) {
            assertTrue(generationBest >= previousBest);
            previousBest = generationBest;
        }
    }

    @Test
    void rejectsInvalidPopulationSettings() {
        assertThrows(IllegalArgumentException.class, () -> new GeneticAlgorithm().evolve(
                List.of(genome("only", 1.0)), 1, 2, 2, 0.1, 1L, candidate -> 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeneticAlgorithm().evolve(
            List.of(genome("a", 1.0), genome("b", 2.0), genome("c", 3.0)),
            1, 2, 1, 0.1, 1L, candidate -> 0.0));
    }

    private PolicyGenome genome(String id, double targetWeight) {
        return new PolicyGenome(PolicyGenome.CURRENT_SCHEMA_VERSION, id, Map.of("target", targetWeight));
    }
}