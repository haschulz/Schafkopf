package schafkopf.ai.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schafkopf.model.GameType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractValueModelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void ridgeCalibrationLearnsExpectedValueFromSyntheticRollouts() {
        List<ContractValueModel.TrainingSample> samples = new ArrayList<>();
        for (int trumpCount = 0; trumpCount <= 6; trumpCount++) {
            for (int aces = 0; aces <= 2; aces++) {
                double value = 5.0 + 2.0 * trumpCount - 3.0 * aces;
                samples.add(new ContractValueModel.TrainingSample(GameType.FARBSOLO,
                        Map.of("trumpCount", (double) trumpCount, "aces", (double) aces), value));
            }
        }

        ContractValueModel model = ContractValueModel.fit("synthetic", samples, 1e-8);

        assertEquals(12.0, model.estimate(GameType.FARBSOLO,
                Map.of("trumpCount", 5.0, "aces", 1.0)), 1e-5);
        assertEquals(21, model.sampleCount(GameType.FARBSOLO));
        assertEquals(0.0, model.estimate(GameType.WENZ, Map.of("trumpCount", 4.0)));
    }

    @Test
    void calibratedModelPropertiesRoundTrip() {
        List<ContractValueModel.TrainingSample> samples = List.of(
                new ContractValueModel.TrainingSample(GameType.WENZ, Map.of("topTrumps", 2.0), 30.0),
                new ContractValueModel.TrainingSample(GameType.WENZ, Map.of("topTrumps", 4.0), 60.0));
        ContractValueModel expected = ContractValueModel.fit("test-model", samples, 0.1);
        Path path = temporaryDirectory.resolve("models/value.properties");

        expected.save(path);

        ContractValueModel loaded = ContractValueModel.load(path);
        assertEquals(expected.id(), loaded.id());
        assertEquals(expected.sampleCount(GameType.WENZ), loaded.sampleCount(GameType.WENZ));
        assertEquals(expected.estimate(GameType.WENZ, Map.of("topTrumps", 3.0)),
                loaded.estimate(GameType.WENZ, Map.of("topTrumps", 3.0)), 1e-10);
    }
}