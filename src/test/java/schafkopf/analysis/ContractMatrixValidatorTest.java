package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContractMatrixValidatorTest {
    @Test
    void matrixEvolutionPreservesNonCardWeights() {
        PolicyGenome initial = PolicyGenome.baseline().withId("initial")
                .withWeight("bid.handScore", 3.0);

        GeneticAlgorithm.EvolutionResult result = new ContractMatrixOptimizer(SchafkopfConfig.defaults())
                .evolve(java.util.List.of(initial, initial.withId("b"), initial.withId("c"), initial.withId("d")),
                        0, 4, 1, 0.0, 2, 778812L);

        assertEquals(3.0, result.champion().weight("bid.handScore"));
        assertEquals(0.8, result.champion().weight("card.FARBWENZ.farbwenzTrumpStrength"));
    }

    @Test
    void identicalPoliciesMatchAcrossEverySupportedFixedContract() {
        PolicyGenome policy = PolicyGenome.baseline().withId("same-policy");

        ContractMatrixValidator.ValidationResult result = new ContractMatrixValidator(SchafkopfConfig.defaults())
                .validate(policy, policy.withId("same-comparison"), 2, 991122L);

        assertEquals(16, result.contracts().size());
        assertEquals(440, result.pairedGames());
        assertEquals(0.0, result.meanPairedDeltaCents());
        assertEquals(0.0, result.confidenceIntervalLowCents());
        assertEquals(0.0, result.confidenceIntervalHighCents());
        assertFalse(result.evidenceOfImprovement());
        assertEquals(24, result.contracts().get(0).pairedGames());
        assertEquals("RAMSCH", result.contracts().get(14).contract());
        assertFalse(result.byRole().isEmpty());
        assertEquals(0.0, result.byRole().values().iterator().next().meanPairedDeltaCents());
        assertEquals(0.0, result.byRole().get("RUFSPIEL/RUFSPIEL_PARTNER").meanPairedDeltaCents());
        assertEquals(0.0, result.byRole().get("FARBSOLO/DEFENDER").meanPairedDeltaCents());
        assertEquals("GEIER", result.contracts().get(8).contract());
        assertEquals("KREUZBOCK", result.contracts().get(15).contract());
    }

        @Test
        void roleComposerRequiresEnoughDealsAndPromotesPredictableRoleWeights() {
        PolicyGenome reference = PolicyGenome.baseline().withId("reference");
        PolicyGenome specialist = reference.withId("specialist")
            .withWeight("card.FARBSOLO.points", -5.0);
        ContractMatrixValidator.RoleResult enoughData = new ContractMatrixValidator.RoleResult(
            300, 30, 5.0, 1.0, 9.0);
        ContractMatrixValidator.RoleResult tooLittleData = new ContractMatrixValidator.RoleResult(
            100, 10, 5.0, 1.0, 9.0);

        PolicyGenome composed = RoleSpecialistComposer.compose(reference, specialist,
            java.util.Map.of("FARBSOLO/SOLOIST", enoughData));

        assertEquals(-5.0, composed.weight("card.FARBSOLO.SOLOIST.points"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> RoleSpecialistComposer.compose(reference, specialist,
                java.util.Map.of("FARBSOLO/SOLOIST", tooLittleData)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> RoleSpecialistComposer.compose(reference, specialist,
                java.util.Map.of("RAMSCH/RAMSCH_LOWEST_EYES", enoughData)));
        }

        @Test
        void roleComposerAddsGeierBidDefaultsToLegacyReferenceGenomes() {
        java.util.Map<String, Double> legacyWeights = new java.util.HashMap<>(PolicyGenome.baseline().weights());
        legacyWeights.keySet().removeIf(key -> key.startsWith("bid.GEIER."));
        PolicyGenome legacy = new PolicyGenome(PolicyGenome.CURRENT_SCHEMA_VERSION, "legacy", legacyWeights);
        ContractMatrixValidator.RoleResult measured = new ContractMatrixValidator.RoleResult(
            300, 30, 5.0, 1.0, 9.0);

        PolicyGenome composed = RoleSpecialistComposer.compose(legacy, PolicyGenome.baseline().withId("specialist"),
            java.util.Map.of("GEIER/SOLOIST", measured));

        assertEquals(8.0, composed.weight("bid.GEIER.threshold"));
        }

}