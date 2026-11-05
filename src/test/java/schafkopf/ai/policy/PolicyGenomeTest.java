package schafkopf.ai.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyGenomeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void genomePropertiesRoundTripPreservesAllWeights() {
        PolicyGenome expected = PolicyGenome.baseline();
        Path path = temporaryDirectory.resolve("nested/baseline.properties");

        expected.save(path);

        assertEquals(expected, PolicyGenome.load(path));
    }

    @Test
    void mutationIsDeterministicForTheSameSeedAndChangesWeights() {
        PolicyGenome genome = PolicyGenome.baseline();

        PolicyGenome first = genome.mutate(new Random(42), 0.25, "child");
        PolicyGenome second = genome.mutate(new Random(42), 0.25, "child");

        assertEquals(first, second);
        assertNotEquals(genome.weights(), first.weights());
        assertEquals(genome.weights(), PolicyGenome.baseline().weights());
    }

    @Test
    void crossoverUsesOnlyParentGenesAndIsSeeded() {
        PolicyGenome first = new PolicyGenome(1, "a", Map.of("x", 1.0, "shared", 2.0));
        PolicyGenome second = new PolicyGenome(1, "b", Map.of("y", 3.0, "shared", 4.0));

        PolicyGenome childA = first.crossover(second, new Random(7), "child");
        PolicyGenome childB = first.crossover(second, new Random(7), "child");

        assertEquals(childA, childB);
        assertEquals(1.0, childA.weight("x"));
        assertEquals(3.0, childA.weight("y"));
        assertTrueChildSharedWeight(childA.weight("shared"));
    }

    @Test
    void rejectsUnsupportedVersionsAndNonFiniteWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyGenome(2, "future", Map.of("weight", 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyGenome(1, "invalid", Map.of("weight", Double.NaN)));
    }

    private void assertTrueChildSharedWeight(double weight) {
        if (weight != 2.0 && weight != 4.0) {
            throw new AssertionError("Crossover must select a parent's shared gene, got " + weight);
        }
    }
}