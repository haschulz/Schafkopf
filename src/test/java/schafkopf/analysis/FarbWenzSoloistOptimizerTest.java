package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarbWenzSoloistOptimizerTest {
    @Test
    void evolutionAddsFarbwenzFeatureLociToOlderSeedGenomes() {
        PolicyGenome oldGenome = new PolicyGenome(PolicyGenome.CURRENT_SCHEMA_VERSION, "old",
            Map.of("bid.handScore", 2.0, "card.WENZ.points", 7.0));
        GeneticAlgorithm.EvolutionResult result = new FarbWenzSoloistOptimizer(SchafkopfConfig.defaults())
                .evolve(List.of(oldGenome, oldGenome.withId("old-2"), oldGenome.withId("old-3"),
                                oldGenome.withId("old-4")),
                        0, 4, 1, 0.0, 2, 778899L);

        assertEquals(0.8, result.champion().weight("card.FARBWENZ.farbwenzTrumpStrength"));
        assertEquals(1.5, result.champion().weight("card.FARBWENZ.farbwenzWinningTrick"));
        assertEquals(0.2, result.champion().weight("card.FARBWENZ.farbwenzSideSuitWinner"));
        assertEquals(2.0, result.champion().weight("bid.handScore"));
        assertEquals(7.0, result.champion().weight("card.WENZ.points"));
    }

    @Test
    void identicalGenomesReceiveIdenticalFixedContractFitnessAndSuitCoverage() {
        PolicyGenome baseline = PolicyGenome.baseline();
        FarbWenzSoloistOptimizer.Evaluation result = new FarbWenzSoloistOptimizer(SchafkopfConfig.defaults())
                .evaluate(List.of(baseline.withId("a"), baseline.withId("b"),
                        baseline.withId("c"), baseline.withId("d")), 2, 112233L);

        assertEquals(4, result.genomes().size());
        assertEquals(32, result.genomes().get(0).games());
        assertEquals(result.genomes().get(0).averageNetCents(), result.genomes().get(1).averageNetCents());
        assertEquals(4, result.genomes().get(0).averageByTrumpSuit().size());
        assertEquals(result.genomes().get(0).fitness(), result.genomes().get(3).fitness());
    }
}