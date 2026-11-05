package schafkopf.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schafkopf.model.GameType;
import schafkopf.model.Suit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchafkopfConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsDefaultsWhenConfigFileDoesNotExist() {
        SchafkopfConfig config = SchafkopfConfig.load(temporaryDirectory.resolve("missing.properties"));

        assertEquals(SchafkopfConfig.NoGamePolicy.STOCK, config.noGamePolicy());
        assertEquals(4, config.klopfenAfterCards());
        assertEquals(10, config.baseTariffCents());
        assertEquals(20, config.rufspielTariffCents());
        assertEquals(50, config.soloTariffCents());
    }

    @Test
    void reloadsConfiguredRulesAndTariffValues() throws IOException {
        Path configFile = temporaryDirectory.resolve("table.properties");
        Files.writeString(configFile, """
                noGame.policy=RAMSCH
                klopfen.afterCards=3
                tariff.baseCents=5
                tariff.rufspielCents=5
                tariff.soloCents=20
                tariff.halfLaufende=true
                muss.gameType=WENZ
                muss.trumpSuit=GRAS
                """);

        SchafkopfConfig config = SchafkopfConfig.load(configFile);

        assertEquals(SchafkopfConfig.NoGamePolicy.RAMSCH, config.noGamePolicy());
        assertEquals(3, config.klopfenAfterCards());
        assertEquals(5, config.baseTariffCents());
        assertEquals(5, config.rufspielTariffCents());
        assertEquals(20, config.soloTariffCents());
        assertTrue(config.halfLaufende());
        assertEquals(GameType.WENZ, config.mussGameType());
        assertEquals(Suit.GRAS, config.mussTrumpfSuit());
    }

    @Test
    void rejectsInvalidConfigValues() throws IOException {
        Path configFile = temporaryDirectory.resolve("invalid.properties");
        Files.writeString(configFile, "klopfen.afterCards=8\n");

        assertThrows(IllegalArgumentException.class, () -> SchafkopfConfig.load(configFile));
        assertTrue(Files.exists(configFile));
    }
}