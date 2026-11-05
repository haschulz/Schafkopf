package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Suit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FarbWenzSoloistValidatorTest {
    @Test
    void identicalPoliciesProduceZeroIsolatedPostDeclarationDifference() {
        PolicyGenome policy = PolicyGenome.baseline().withId("same");

        FarbWenzSoloistValidator.ValidationResult result = new FarbWenzSoloistValidator(
                SchafkopfConfig.defaults()).validate(policy, policy, 3, 919191L);

        assertEquals(48, result.pairedGames());
        assertEquals(0.0, result.meanPairedDeltaCents());
        assertEquals(0.0, result.confidenceIntervalLowCents());
        assertEquals(0.0, result.confidenceIntervalHighCents());
        assertFalse(result.evidenceOfImprovement());
        for (Suit suit : Suit.values()) {
            assertEquals(12, result.byTrumpSuit().get(suit).pairedGames());
            assertEquals(0.0, result.byTrumpSuit().get(suit).meanPairedDeltaCents());
        }
    }
}