package schafkopf.ai.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionTraceWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOneEscapedJsonObjectPerDecision() throws IOException {
        Path traceFile = temporaryDirectory.resolve("nested/decisions.jsonl");
        DecisionTrace trace = new DecisionTrace(
                "agent\"one",
                DecisionTrace.Stage.CARD_PLAY,
                "FARBSOLO",
                "p1",
                List.of("Eichel-Ober", "Eichel-Sau"),
                "Eichel-Sau",
                Map.of("Eichel-Sau", 1.2),
                Map.of("Eichel-Sau", Map.of("points", 1.0)),
                "Rationale with\nnewline");

        try (DecisionTraceWriter writer = new DecisionTraceWriter(traceFile)) {
            writer.accept(trace);
        }

        String output = Files.readString(traceFile);
        assertTrue(output.startsWith("{\"genomeId\":\"agent\\\"one\""));
        assertTrue(output.contains("\"selectedAction\":\"Eichel-Sau\""));
        assertTrue(output.contains("Rationale with\\nnewline"));
        assertTrue(output.lines().count() == 1);
    }
}