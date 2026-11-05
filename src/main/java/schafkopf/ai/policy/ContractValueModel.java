package schafkopf.ai.policy;

import schafkopf.model.GameType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

public record ContractValueModel(
        int schemaVersion,
        String id,
        Map<GameType, Map<String, Double>> weightsByGameType,
        Map<GameType, Integer> samplesByGameType
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String BIAS = "__bias";

    public record TrainingSample(GameType gameType, Map<String, Double> features, double netCents) {
        public TrainingSample {
            if (gameType == null || features == null || !Double.isFinite(netCents)) {
                throw new IllegalArgumentException("Training samples need a game type, features, and finite return");
            }
            features.forEach((name, value) -> {
                if (name == null || name.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Training features need names and finite values");
                }
            });
            features = Map.copyOf(features);
        }
    }

    public ContractValueModel {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported contract-value model version: " + schemaVersion);
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Model id must not be blank");
        }
        TreeMap<GameType, Map<String, Double>> sortedWeights = new TreeMap<>();
        weightsByGameType.forEach((gameType, weights) -> {
            TreeMap<String, Double> sorted = new TreeMap<>(weights);
            if (sorted.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("Model coefficients must be finite");
            }
            sortedWeights.put(gameType, Collections.unmodifiableMap(sorted));
        });
        weightsByGameType = Collections.unmodifiableMap(sortedWeights);
        samplesByGameType = Collections.unmodifiableMap(new TreeMap<>(samplesByGameType));
    }

    public static ContractValueModel empty() {
        return new ContractValueModel(CURRENT_SCHEMA_VERSION, "uncalibrated", Map.of(), Map.of());
    }

    public static ContractValueModel loadOrEmpty(Path path) {
        return Files.exists(path) ? load(path) : empty();
    }

    public static ContractValueModel loadConfigured() {
        String path = System.getProperty("schafkopf.valueModel", "config/contract-value-model.properties");
        return loadOrEmpty(Paths.get(path));
    }

    public boolean isCalibrated(GameType gameType) {
        return samplesByGameType.getOrDefault(gameType, 0) > 0;
    }

    public int sampleCount(GameType gameType) {
        return samplesByGameType.getOrDefault(gameType, 0);
    }

    public double estimate(GameType gameType, Map<String, Double> features) {
        Map<String, Double> coefficients = weightsByGameType.get(gameType);
        if (coefficients == null) {
            return 0.0;
        }
        double estimate = coefficients.getOrDefault(BIAS, 0.0);
        for (Map.Entry<String, Double> feature : features.entrySet()) {
            estimate += coefficients.getOrDefault(feature.getKey(), 0.0) * feature.getValue();
        }
        return estimate;
    }

    public static ContractValueModel fit(String id, List<TrainingSample> samples, double ridge) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Calibration requires at least one sample");
        }
        if (!Double.isFinite(ridge) || ridge < 0.0) {
            throw new IllegalArgumentException("Ridge strength must be finite and non-negative");
        }

        TreeMap<GameType, List<TrainingSample>> grouped = new TreeMap<>();
        samples.forEach(sample -> grouped.computeIfAbsent(sample.gameType(), ignored -> new ArrayList<>()).add(sample));
        TreeMap<GameType, Map<String, Double>> coefficients = new TreeMap<>();
        TreeMap<GameType, Integer> counts = new TreeMap<>();
        grouped.forEach((gameType, gameSamples) -> {
            Map<String, Double> fitted = fitType(gameSamples, ridge);
            coefficients.put(gameType, fitted);
            counts.put(gameType, gameSamples.size());
        });
        return new ContractValueModel(CURRENT_SCHEMA_VERSION, id, coefficients, counts);
    }

    private static Map<String, Double> fitType(List<TrainingSample> samples, double ridge) {
        TreeSet<String> featureNames = new TreeSet<>();
        samples.forEach(sample -> featureNames.addAll(sample.features().keySet()));
        List<String> columns = new ArrayList<>();
        columns.add(BIAS);
        columns.addAll(featureNames);
        int dimension = columns.size();
        double[][] normal = new double[dimension][dimension];
        double[] target = new double[dimension];

        for (TrainingSample sample : samples) {
            double[] row = new double[dimension];
            row[0] = 1.0;
            for (int column = 1; column < dimension; column++) {
                row[column] = sample.features().getOrDefault(columns.get(column), 0.0);
            }
            for (int rowIndex = 0; rowIndex < dimension; rowIndex++) {
                target[rowIndex] += row[rowIndex] * sample.netCents();
                for (int column = 0; column < dimension; column++) {
                    normal[rowIndex][column] += row[rowIndex] * row[column];
                }
            }
        }

        for (int index = 1; index < dimension; index++) {
            normal[index][index] += ridge;
        }
        double[] solved = solve(normal, target);
        TreeMap<String, Double> result = new TreeMap<>();
        for (int index = 0; index < dimension; index++) {
            result.put(columns.get(index), solved[index]);
        }
        return result;
    }

    private static double[] solve(double[][] matrix, double[] vector) {
        int dimension = vector.length;
        double[][] augmented = new double[dimension][dimension + 1];
        for (int row = 0; row < dimension; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, dimension);
            augmented[row][dimension] = vector[row];
        }

        for (int pivot = 0; pivot < dimension; pivot++) {
            int bestRow = pivot;
            for (int row = pivot + 1; row < dimension; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[bestRow][pivot])) {
                    bestRow = row;
                }
            }
            if (Math.abs(augmented[bestRow][pivot]) < 1e-12) {
                throw new IllegalArgumentException("Singular calibration data; use positive ridge strength");
            }
            double[] swap = augmented[pivot];
            augmented[pivot] = augmented[bestRow];
            augmented[bestRow] = swap;

            double pivotValue = augmented[pivot][pivot];
            for (int column = pivot; column <= dimension; column++) {
                augmented[pivot][column] /= pivotValue;
            }
            for (int row = 0; row < dimension; row++) {
                if (row == pivot) continue;
                double factor = augmented[row][pivot];
                for (int column = pivot; column <= dimension; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }

        double[] solution = new double[dimension];
        for (int index = 0; index < dimension; index++) {
            solution[index] = augmented[index][dimension];
        }
        return solution;
    }

    public void save(Path path) {
        Properties properties = new Properties();
        properties.setProperty("model.schemaVersion", Integer.toString(schemaVersion));
        properties.setProperty("model.id", id);
        weightsByGameType.forEach((gameType, weights) -> {
            properties.setProperty("samples." + gameType.name(),
                    Integer.toString(samplesByGameType.getOrDefault(gameType, 0)));
            weights.forEach((name, value) -> properties.setProperty(
                    "weight." + gameType.name() + "." + name, Double.toString(value)));
        });
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Calibrated expected contract value in cents");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save contract value model: " + path, exception);
        }
    }

    public static ContractValueModel load(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load contract value model: " + path, exception);
        }

        int version;
        try {
            version = Integer.parseInt(properties.getProperty("model.schemaVersion", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid model.schemaVersion", exception);
        }
        String id = properties.getProperty("model.id");
        TreeMap<GameType, Map<String, Double>> weights = new TreeMap<>();
        TreeMap<GameType, Integer> sampleCounts = new TreeMap<>();
        for (GameType type : GameType.values()) {
            String sampleValue = properties.getProperty("samples." + type.name());
            if (sampleValue != null) {
                sampleCounts.put(type, Integer.parseInt(sampleValue));
            }
        }
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith("weight.")) continue;
            String[] parts = propertyName.split("\\.", 3);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid model weight key: " + propertyName);
            GameType type = GameType.valueOf(parts[1]);
            double value = Double.parseDouble(properties.getProperty(propertyName));
            weights.computeIfAbsent(type, ignored -> new TreeMap<>()).put(parts[2], value);
        }
        return new ContractValueModel(version, id, weights, sampleCounts);
    }
}