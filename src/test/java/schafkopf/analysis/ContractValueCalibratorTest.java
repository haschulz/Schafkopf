package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.GameType;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractValueCalibratorTest {
    @Test
    void seededRolloutsProduceCalibratedValuesForSoloGameTypes() {
        ContractValueCalibrator.CalibrationResult result = new ContractValueCalibrator(SchafkopfConfig.defaults())
                .calibrate(8, 34567L, 10.0);

        assertTrue(result.rollouts() >= 8 * 10);
        assertTrue(result.opponentPolicyCount() >= 4);
        assertTrue(result.model().id().contains("pool-" + result.opponentPolicyCount()));
        assertTrue(result.samplesByGameType().getOrDefault(GameType.FARBSOLO, 0) > 0);
        assertTrue(result.samplesByGameType().getOrDefault(GameType.WENZ, 0) > 0);
        assertTrue(result.samplesByGameType().getOrDefault(GameType.FARBWENZ, 0) > 0);
        assertTrue(result.samplesByGameType().getOrDefault(GameType.GEIER, 0) > 0);
        assertTrue(result.samplesByGameType().getOrDefault(GameType.BETTEL, 0) > 0);
        assertTrue(result.model().weightsByGameType().get(GameType.FARBSOLO).entrySet().stream()
            .anyMatch(entry -> !entry.getKey().equals("__bias") && Math.abs(entry.getValue()) > 1e-6));
        assertTrue(Double.isFinite(result.model().estimate(GameType.FARBSOLO,
                java.util.Map.of("trumpCount", 4.0, "topTrumps", 2.0))));
    }
}