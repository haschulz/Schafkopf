package schafkopf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchafkopfAppTest {
    @TempDir
    Path tempDir;

    @Test
    void postGameCommandsRunWithoutWritingFiles() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            SchafkopfApp.main(new String[] {"nachkarteln-datei"});
                SchafkopfApp.main(new String[] {
                    "nachkarteln-datei", tempDir.resolve("missing-game.txt").toString(), "Sepp"});
            SchafkopfApp.main(new String[] {"nachkarteln", "4711", "0", "1"});
        } finally {
            System.setOut(originalOut);
        }

        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Aufruf: nachkarteln-datei"));
        assertTrue(report.contains("Partie konnte nicht gelesen werden"));
        assertTrue(report.contains("Stich 8:"));
    }
}