package schafkopf.config;

import schafkopf.model.GameType;
import schafkopf.model.Suit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public record SchafkopfConfig(
        NoGamePolicy noGamePolicy,
        boolean klopfenEnabled,
        int klopfenAfterCards,
        boolean kontraEnabled,
        boolean reEnabled,
        int baseTariffCents,
        int rufspielTariffCents,
        int soloTariffCents,
        boolean halfLaufende,
        int stockMultiplier,
        GameType mussGameType,
        Suit mussTrumpfSuit
) {
    public enum NoGamePolicy {
        STOCK,
        RAMSCH,
        ZAMMWERFEN,
        KREUZBOCK,
        MUSS_SPIEL
    }

    public SchafkopfConfig {
        if (klopfenAfterCards < 1 || klopfenAfterCards > 7) {
            throw new IllegalArgumentException("klopfenAfterCards must be between 1 and 7");
        }
        if (baseTariffCents <= 0 || rufspielTariffCents <= 0 || soloTariffCents <= 0) {
            throw new IllegalArgumentException("Tariff amounts must be positive");
        }
        if (stockMultiplier < 2) {
            throw new IllegalArgumentException("stockMultiplier must be at least 2");
        }
        if (mussGameType == GameType.RUFSPIEL
            || mussGameType == GameType.RAMSCH || mussGameType == GameType.KREUZBOCK
            || mussGameType == GameType.GEIER) {
            throw new IllegalArgumentException("Muss-Spiel must use a solo contract type");
        }
    }

    public static SchafkopfConfig defaults() {
        return new SchafkopfConfig(
                NoGamePolicy.STOCK,
                true,
                4,
                true,
                true,
                10,
                20,
                50,
                false,
                2,
                GameType.FARBSOLO,
                Suit.EICHEL);
    }

    public static SchafkopfConfig load() {
        String configuredPath = System.getProperty("schafkopf.config", "config/schafkopf.properties");
        return load(Path.of(configuredPath));
    }

    public static SchafkopfConfig load(Path path) {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read Schafkopf config: " + path, exception);
            }
        }
        return from(properties);
    }

    private static SchafkopfConfig from(Properties properties) {
        SchafkopfConfig defaults = defaults();
        return new SchafkopfConfig(
                enumValue(properties, "noGame.policy", NoGamePolicy.class, defaults.noGamePolicy()),
                booleanValue(properties, "klopfen.enabled", defaults.klopfenEnabled()),
                intValue(properties, "klopfen.afterCards", defaults.klopfenAfterCards()),
                booleanValue(properties, "kontra.enabled", defaults.kontraEnabled()),
                booleanValue(properties, "re.enabled", defaults.reEnabled()),
                intValue(properties, "tariff.baseCents", defaults.baseTariffCents()),
                intValue(properties, "tariff.rufspielCents", defaults.rufspielTariffCents()),
                intValue(properties, "tariff.soloCents", defaults.soloTariffCents()),
                booleanValue(properties, "tariff.halfLaufende", defaults.halfLaufende()),
                intValue(properties, "stock.multiplier", defaults.stockMultiplier()),
                enumValue(properties, "muss.gameType", GameType.class, defaults.mussGameType()),
                enumValue(properties, "muss.trumpSuit", Suit.class, defaults.mussTrumpfSuit()));
    }

    private static boolean booleanValue(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Property " + key + " must be true or false");
    }

    private static int intValue(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property " + key + " must be an integer", exception);
        }
    }

    private static <T extends Enum<T>> T enumValue(Properties properties, String key, Class<T> type, T defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown value for " + key + ": " + value, exception);
        }
    }
}