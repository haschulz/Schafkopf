package schafkopf.ai.policy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Consumer;

public final class DecisionTraceWriter implements Consumer<DecisionTrace>, AutoCloseable {
    private final BufferedWriter writer;

    public DecisionTraceWriter(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open decision trace file: " + path, exception);
        }
    }

    @Override
    public synchronized void accept(DecisionTrace trace) {
        try {
            writer.write(toJson(trace));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write decision trace", exception);
        }
    }

    private String toJson(DecisionTrace trace) {
        StringBuilder json = new StringBuilder("{");
        appendString(json, "genomeId", trace.genomeId());
        json.append(',');
        appendString(json, "stage", trace.stage().name());
        json.append(',');
        appendString(json, "gameType", trace.gameType());
        json.append(',');
        appendString(json, "playerId", trace.playerId());
        json.append(",\"legalActions\":[");
        for (int i = 0; i < trace.legalActions().size(); i++) {
            if (i > 0) json.append(',');
            appendQuoted(json, trace.legalActions().get(i));
        }
        json.append("],");
        appendString(json, "selectedAction", trace.selectedAction());
        json.append(",\"actionScores\":");
        appendNumberMap(json, trace.actionScores());
        json.append(",\"featuresByAction\":{");
        boolean firstAction = true;
        for (Map.Entry<String, Map<String, Double>> entry : trace.featuresByAction().entrySet()) {
            if (!firstAction) json.append(',');
            firstAction = false;
            appendQuoted(json, entry.getKey());
            json.append(':');
            appendNumberMap(json, entry.getValue());
        }
        json.append("},");
        appendString(json, "rationale", trace.rationale());
        return json.append('}').toString();
    }

    private void appendNumberMap(StringBuilder json, Map<String, Double> values) {
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            appendQuoted(json, entry.getKey());
            json.append(':').append(entry.getValue());
        }
        json.append('}');
    }

    private void appendString(StringBuilder json, String key, String value) {
        appendQuoted(json, key);
        json.append(':');
        appendQuoted(json, value);
    }

    private void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close decision trace file", exception);
        }
    }
}