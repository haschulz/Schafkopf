package schafkopf.ai.policy;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DecisionTrace(
        String genomeId,
        Stage stage,
        String gameType,
        String playerId,
        List<String> legalActions,
        String selectedAction,
        Map<String, Double> actionScores,
        Map<String, Map<String, Double>> featuresByAction,
        String rationale
) {
    public enum Stage {
        KLOPFEN,
        BIDDING,
        CARD_PLAY
    }

    public DecisionTrace {
        Objects.requireNonNull(genomeId, "Genome id must not be null");
        Objects.requireNonNull(stage, "Decision stage must not be null");
        Objects.requireNonNull(gameType, "Game type must not be null");
        Objects.requireNonNull(playerId, "Player id must not be null");
        legalActions = List.copyOf(legalActions);
        Objects.requireNonNull(selectedAction, "Selected action must not be null");
        actionScores = Collections.unmodifiableMap(new LinkedHashMap<>(actionScores));
        LinkedHashMap<String, Map<String, Double>> featureCopy = new LinkedHashMap<>();
        featuresByAction.forEach((action, features) -> featureCopy.put(action,
            Collections.unmodifiableMap(new LinkedHashMap<>(features))));
        featuresByAction = Collections.unmodifiableMap(featureCopy);
        rationale = rationale == null ? "" : rationale;
    }
}