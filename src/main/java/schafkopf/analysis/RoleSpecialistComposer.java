package schafkopf.analysis;

import schafkopf.ai.policy.PolicyGenome;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RoleSpecialistComposer {
    private static final int MINIMUM_INDEPENDENT_DEALS = 30;

    private RoleSpecialistComposer() {
    }

    public static PolicyGenome compose(
            PolicyGenome reference,
            PolicyGenome specialist,
            Map<String, ContractMatrixValidator.RoleResult> measuredRoles
    ) {
        if (reference == null || specialist == null || measuredRoles == null) {
            throw new IllegalArgumentException("Reference, specialist, and role measurements are required");
        }
        PolicyGenome composed = reference.withId("role-composite-" + specialist.id());
        PolicyGenome defaults = PolicyGenome.baseline();
        for (Map.Entry<String, Double> weight : defaults.weights().entrySet()) {
            if (weight.getKey().startsWith("bid.GEIER.")
                    && !composed.weights().containsKey(weight.getKey())) {
                composed = composed.withWeight(weight.getKey(), weight.getValue());
            }
        }
        Set<String> promotedRoles = new HashSet<>();
        for (Map.Entry<String, ContractMatrixValidator.RoleResult> entry : measuredRoles.entrySet()) {
            if (entry.getValue().independentDeals() < MINIMUM_INDEPENDENT_DEALS
                    || entry.getValue().confidenceIntervalLowCents() <= 0.0) {
                continue;
            }
            String[] parts = entry.getKey().split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid game/role key: " + entry.getKey());
            }
            if (parts[0].equals("RAMSCH")) {
                continue;
            }
            String contractPrefix = "card." + parts[0] + ".";
            String rolePrefix = contractPrefix + parts[1] + ".";
            Set<String> features = effectiveFeatures(reference, specialist, contractPrefix);
            for (String feature : features) {
                composed = composed.withWeight(rolePrefix + feature,
                        effectiveWeight(specialist, reference, contractPrefix, feature));
            }
            promotedRoles.add(entry.getKey());
        }
        if (promotedRoles.isEmpty()) {
            throw new IllegalArgumentException("No role has a statistically positive holdout interval");
        }
        return composed;
    }

    private static Set<String> effectiveFeatures(
            PolicyGenome reference,
            PolicyGenome specialist,
            String contractPrefix
    ) {
        Set<String> features = new TreeSet<>();
        addFeatures(features, reference, contractPrefix);
        addFeatures(features, specialist, contractPrefix);
        return features;
    }

    private static void addFeatures(Set<String> features, PolicyGenome genome, String contractPrefix) {
        for (String key : genome.weights().keySet()) {
            if (key.startsWith(contractPrefix)) {
                String feature = key.substring(contractPrefix.length());
                if (!feature.contains(".")) {
                    features.add(feature);
                }
            } else if (key.startsWith("card.")) {
                String feature = key.substring("card.".length());
                if (!feature.contains(".") && !isContractName(feature)) {
                    features.add(feature);
                }
            }
        }
    }

    private static double effectiveWeight(
            PolicyGenome specialist,
            PolicyGenome reference,
            String contractPrefix,
            String feature
    ) {
        String contractKey = contractPrefix + feature;
        if (specialist.weights().containsKey(contractKey)) {
            return specialist.weight(contractKey);
        }
        String sharedKey = "card." + feature;
        if (specialist.weights().containsKey(sharedKey)) {
            return specialist.weight(sharedKey);
        }
        if (reference.weights().containsKey(contractKey)) {
            return reference.weight(contractKey);
        }
        return reference.weight(sharedKey);
    }

    private static boolean isContractName(String value) {
        return switch (value) {
            case "RUFSPIEL", "FARBSOLO", "WENZ", "GEIER", "FARBWENZ", "BETTEL", "RAMSCH", "KREUZBOCK" -> true;
            default -> false;
        };
    }
}