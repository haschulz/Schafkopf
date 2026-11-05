package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldoutValidatorTest {
    @Test
    void identicalCandidateAndBaselineHaveZeroPairedDifferenceOnIndependentDeals() {
        PolicyGenome candidate = PolicyGenome.baseline().withId("same-policy");

        HoldoutValidator.ValidationResult result = new HoldoutValidator(SchafkopfConfig.defaults())
                .validate(candidate, 3, 7654321L);

        assertEquals("holdout-baseline-reference", result.comparisonPolicyId());
        assertEquals(3, result.independentDeals());
        assertEquals(12, result.pairedGames());
        assertEquals(0.0, result.meanPairedDeltaCents());
        assertEquals(0.0, result.confidenceIntervalLowCents());
        assertEquals(0.0, result.confidenceIntervalHighCents());
        assertEquals(0.0, result.standardDeviationPairedDeltaCents());
        assertEquals(0.0, result.medianPairedDeltaCents());
        assertEquals(0.0, result.tenthPercentilePairedDeltaCents());
        assertEquals(0.0, result.worstDecileAveragePairedDeltaCents());
        assertFalse(result.byGameTypeAndRole().isEmpty());
        assertTrue(result.byGameTypeAndRole().values().stream()
            .allMatch(group -> group.games() > 0 && Double.isFinite(group.worstDecileAverageNetCents())));
        assertEquals(result.pairedGames(), result.byGameTypeAndRole().values().stream()
            .mapToInt(HoldoutValidator.GroupPerformance::games).sum());
        assertFalse(result.evidenceOfImprovement());
    }

    @Test
    void rejectsSingleDealBecauseItCannotEstimateUncertainty() {
        HoldoutValidator validator = new HoldoutValidator(SchafkopfConfig.defaults());

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(PolicyGenome.baseline(), 1, 7654321L));
    }

    @Test
    void onlyClaimsImprovementOrRegressionWhenTheIntervalExcludesZero() {
        HoldoutValidator.ValidationResult improved = result(1.0, 0.1);
        HoldoutValidator.ValidationResult regressed = result(-1.0, -0.1);
        HoldoutValidator.ValidationResult inconclusive = result(-0.2, 0.4);

        assertTrue(improved.evidenceOfImprovement());
        assertFalse(improved.evidenceOfRegression());
        assertTrue(regressed.evidenceOfRegression());
        assertFalse(regressed.evidenceOfImprovement());
        assertFalse(inconclusive.evidenceOfImprovement());
        assertFalse(inconclusive.evidenceOfRegression());
    }

    @Test
    void validationOpponentPoolContainsDistinctPolicies() {
        var controls = ValidationOpponentPool.controls("test");

        assertEquals(3, controls.size());
        assertEquals(3, controls.stream().map(policy -> policy.weights()).distinct().count());
    }

    private HoldoutValidator.ValidationResult result(double low, double high) {
        return new HoldoutValidator.ValidationResult(
                "test", "baseline", 10, 40, 1.0, 0.0, 0.5, low, high,
                1.0, 0.5, -1.0, -2.0, 6, java.util.Map.of(), 456L);
    }
}