package schafkopf.ai.policy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;

public record PolicyGenome(int schemaVersion, String id, Map<String, Double> weights) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String WEIGHT_PREFIX = "weight.";

    public PolicyGenome {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported genome schema version: " + schemaVersion);
        }
        Objects.requireNonNull(id, "Genome id must not be null");
        Objects.requireNonNull(weights, "Genome weights must not be null");
        TreeMap<String, Double> orderedWeights = new TreeMap<>();
        weights.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Genome weights require non-empty keys and finite values");
            }
            orderedWeights.put(key, value);
        });
        weights = Collections.unmodifiableMap(orderedWeights);
    }

    public static PolicyGenome baseline() {
        return new PolicyGenome(CURRENT_SCHEMA_VERSION, "baseline-v1", Map.ofEntries(
            Map.entry("klopfen.threshold", 1.0),
            Map.entry("klopfen.topPair", 1.0),
            Map.entry("klopfen.topTrumps", 0.34),
            Map.entry("klopfen.eichelOberAndSau", 1.0),
            Map.entry("bid.handScore", 1.0),
            Map.entry("bid.riskPenalty", -100.0),
            Map.entry("bid.zockerRiskRelief", 100.0),
                Map.entry("bid.expectedValueCents", 0.02),
                Map.entry("bid.FARBSOLO.trumpCount", 1.5),
                Map.entry("bid.FARBSOLO.topTrumps", 2.0),
                Map.entry("bid.FARBSOLO.trumpPoints", 0.2),
                Map.entry("bid.FARBSOLO.trumpControl", 0.8),
                Map.entry("bid.FARBSOLO.sideSuitWinners", 0.4),
                Map.entry("bid.FARBSOLO.voidSideSuits", 0.3),
                Map.entry("bid.FARBSOLO.longSideSuitsWithoutAce", -0.8),
                Map.entry("bid.FARBSOLO.expectedValueCents", 0.01),
                Map.entry("bid.FARBSOLO.threshold", 9.0),
                Map.entry("bid.WENZ.trumpCount", 1.0),
                Map.entry("bid.WENZ.topTrumps", 2.5),
                Map.entry("bid.WENZ.trumpPoints", 0.1),
                Map.entry("bid.WENZ.trumpControl", 0.8),
                Map.entry("bid.WENZ.sideSuitWinners", 0.4),
                Map.entry("bid.WENZ.longSideSuitsWithoutAce", -0.5),
                Map.entry("bid.WENZ.expectedValueCents", 0.01),
                Map.entry("bid.WENZ.threshold", 8.0),
                Map.entry("bid.GEIER.trumpCount", 1.0),
                Map.entry("bid.GEIER.topTrumps", 2.0),
                Map.entry("bid.GEIER.trumpControl", 1.0),
                Map.entry("bid.GEIER.sideSuitWinners", 0.3),
                Map.entry("bid.GEIER.expectedValueCents", 0.01),
                Map.entry("bid.GEIER.threshold", 8.0),
                Map.entry("bid.FARBWENZ.trumpCount", 1.5),
                Map.entry("bid.FARBWENZ.topTrumps", 2.5),
                Map.entry("bid.FARBWENZ.unterCount", 2.0),
                Map.entry("bid.FARBWENZ.nonTrumpOberCount", 0.1),
                Map.entry("bid.FARBWENZ.trumpPoints", 0.2),
                Map.entry("bid.FARBWENZ.trumpControl", 1.0),
                Map.entry("bid.FARBWENZ.chosenSuitTrumpCount", 1.0),
                Map.entry("bid.FARBWENZ.chosenSuitTrumpHighCards", 2.0),
                Map.entry("bid.FARBWENZ.chosenSuitTrumpPoints", 0.5),
                Map.entry("bid.FARBWENZ.sideSuitWinners", 0.4),
                Map.entry("bid.FARBWENZ.voidSideSuits", 0.3),
                Map.entry("bid.FARBWENZ.longSideSuitsWithoutAce", -0.8),
                Map.entry("bid.FARBWENZ.expectedValueCents", 0.01),
                Map.entry("bid.FARBWENZ.minimumExpectedValueCents", 0.0),
                Map.entry("bid.FARBWENZ.threshold", 13.0),
                Map.entry("bid.BETTEL.highCards", -1.5),
                Map.entry("bid.BETTEL.lowCards", 0.1),
                Map.entry("bid.BETTEL.bettelHighCards", -1.5),
                Map.entry("bid.BETTEL.bettelTopCards", -4.0),
                Map.entry("bid.BETTEL.bettelLowCards", 1.5),
                Map.entry("bid.BETTEL.longSideSuitsWithoutAce", 0.4),
                Map.entry("bid.BETTEL.expectedValueCents", 0.01),
                Map.entry("bid.BETTEL.threshold", 8.0),
            Map.entry("card.points", -0.1),
            Map.entry("card.winningTrick", 4.0),
            Map.entry("card.teammatePoints", 0.1),
            Map.entry("card.bettelWinning", -10.0),
                Map.entry("card.ramschWinning", -2.0),
                Map.entry("card.FARBSOLO.recommendation", 2.0),
                Map.entry("card.WENZ.recommendation", 2.0),
                Map.entry("card.GEIER.recommendation", 2.0),
                Map.entry("card.GEIER.points", -0.1),
                Map.entry("card.FARBWENZ.recommendation", 0.4),
                Map.entry("card.BETTEL.recommendation", 2.0),
                Map.entry("card.RAMSCH.recommendation", 2.0),
                Map.entry("card.KREUZBOCK.recommendation", 2.0),
                Map.entry("card.FARBSOLO.points", -0.1),
                Map.entry("card.WENZ.points", -0.1),
                Map.entry("card.FARBWENZ.points", -0.15),
                Map.entry("card.FARBWENZ.farbwenzSoloistLeadTrump", 0.8),
                Map.entry("card.FARBWENZ.farbwenzTrumpStrength", 0.8),
                Map.entry("card.FARBWENZ.farbwenzWinningTrick", 1.5),
                Map.entry("card.FARBWENZ.farbwenzSideSuitWinner", 0.2),
                Map.entry("card.BETTEL.points", -0.1),
                Map.entry("card.RAMSCH.points", -0.1),
                Map.entry("card.KREUZBOCK.points", -0.1),
                Map.entry("card.RUFSPIEL.recommendation", 2.0),
                Map.entry("card.RUFSPIEL.points", -0.1),
                Map.entry("card.RUFSPIEL.winningTrick", 0.0),
                Map.entry("card.RUFSPIEL.teammatePoints", 0.1)));
    }

    public double weight(String key) {
        return weights.getOrDefault(key, 0.0);
    }

    public PolicyGenome withWeight(String key, double value) {
        TreeMap<String, Double> updatedWeights = new TreeMap<>(weights);
        updatedWeights.put(key, value);
        return new PolicyGenome(schemaVersion, id, updatedWeights);
    }

    public PolicyGenome withId(String newId) {
        return new PolicyGenome(schemaVersion, newId, weights);
    }

    public double score(Map<String, Double> features, String weightPrefix) {
        double score = 0.0;
        for (Map.Entry<String, Double> feature : features.entrySet()) {
            score += weight(weightPrefix + feature.getKey()) * feature.getValue();
        }
        return score;
    }

    public Map<String, Double> contributions(Map<String, Double> features, String weightPrefix) {
        TreeMap<String, Double> contributions = new TreeMap<>();
        features.forEach((name, value) ->
                contributions.put(name, weight(weightPrefix + name) * value));
        return Collections.unmodifiableMap(contributions);
    }

    public PolicyGenome crossover(PolicyGenome other, Random random, String childId) {
        Objects.requireNonNull(other, "Other genome must not be null");
        Objects.requireNonNull(random, "Random must not be null");
        if (schemaVersion != other.schemaVersion) {
            throw new IllegalArgumentException("Cannot cross genomes with different schema versions");
        }
        TreeMap<String, Double> childWeights = new TreeMap<>();
        TreeMap<String, Boolean> keys = new TreeMap<>();
        weights.keySet().forEach(key -> keys.put(key, true));
        other.weights.keySet().forEach(key -> keys.put(key, true));
        for (String key : keys.keySet()) {
            Double first = weights.get(key);
            Double second = other.weights.get(key);
            double value;
            if (first == null) {
                value = second;
            } else if (second == null || random.nextBoolean()) {
                value = first;
            } else {
                value = second;
            }
            childWeights.put(key, value);
        }
        return new PolicyGenome(schemaVersion, childId, childWeights);
    }

    public PolicyGenome mutate(Random random, double standardDeviation, String childId) {
        Objects.requireNonNull(random, "Random must not be null");
        if (!Double.isFinite(standardDeviation) || standardDeviation < 0.0) {
            throw new IllegalArgumentException("Mutation standard deviation must be finite and non-negative");
        }
        TreeMap<String, Double> mutated = new TreeMap<>();
        weights.forEach((key, value) -> mutated.put(key, value + random.nextGaussian() * standardDeviation));
        return new PolicyGenome(schemaVersion, childId, mutated);
    }

    public void save(Path path) {
        Properties properties = new Properties();
        properties.setProperty("genome.schemaVersion", Integer.toString(schemaVersion));
        properties.setProperty("genome.id", id);
        weights.forEach((key, value) -> properties.setProperty(WEIGHT_PREFIX + key, Double.toString(value)));
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Schafkopf policy genome");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save policy genome: " + path, exception);
        }
    }

    public static PolicyGenome load(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load policy genome: " + path, exception);
        }

        int schemaVersion;
        try {
            schemaVersion = Integer.parseInt(properties.getProperty("genome.schemaVersion", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid genome.schemaVersion", exception);
        }
        String id = properties.getProperty("genome.id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing genome.id");
        }

        TreeMap<String, Double> weights = new TreeMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(WEIGHT_PREFIX)) {
                String key = propertyName.substring(WEIGHT_PREFIX.length());
                try {
                    weights.put(key, Double.parseDouble(properties.getProperty(propertyName)));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid genome weight: " + propertyName, exception);
                }
            }
        }
        return new PolicyGenome(schemaVersion, id, weights);
    }

    public static PolicyGenome loadOrBaseline(Path path) {
        return Files.exists(path) ? load(path) : baseline();
    }

    public static PolicyGenome loadConfigured() {
        String path = System.getProperty("schafkopf.genome", "config/policy-genome.properties");
        return loadOrBaseline(Paths.get(path));
    }
}