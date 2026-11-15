package schafkopf.analysis;

import schafkopf.model.Card;
import schafkopf.model.GameType;
import schafkopf.model.Rank;
import schafkopf.model.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads a finished game written by hand, for example from another app or a real table:
 *
 * <pre>
 * spieler: Du, Daniel, Max, Stefan
 * ansage: Du sauspiel eichel
 * stich: Daniel Schellen-9, Du Gras-Ober, Max Herz-7, Stefan Eichel-Ober
 * </pre>
 *
 * Players are listed in seating (playing) order. All four hands follow from the eight tricks.
 */
public final class GameRecordParser {
    private GameRecordParser() {
    }

    public static GameRecord parse(String text) {
        List<String> names = null;
        GameRecord.ContractSpec contract = null;
        List<GameRecord.Play> plays = new ArrayList<>();
        int lineNumber = 0;
        for (String rawLine : text.split("\\R")) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw error(lineNumber, "Erwartet 'spieler:', 'ansage:' oder 'stich:'");
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.GERMAN);
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "spieler" -> {
                    names = Arrays.stream(value.split(",")).map(String::strip).toList();
                    if (names.size() != 4 || names.stream().distinct().count() != 4) {
                        throw error(lineNumber, "Genau vier verschiedene Spielernamen angeben");
                    }
                }
                case "ansage" -> contract = parseContract(value, requireNames(names, lineNumber), lineNumber);
                case "stich" -> plays.addAll(parseTrick(value, requireNames(names, lineNumber), lineNumber));
                default -> throw error(lineNumber, "Unbekannter Eintrag '" + key + "'");
            }
        }
        if (names == null || contract == null) {
            throw new IllegalArgumentException("Es fehlen 'spieler:' oder 'ansage:'");
        }
        if (plays.size() != 32) {
            throw new IllegalArgumentException("Es werden genau 8 Stiche benötigt, gefunden: " + plays.size() / 4.0);
        }
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            int finalSeat = seat;
            hands.add(plays.stream().filter(play -> play.seat() == finalSeat).map(GameRecord.Play::card).toList());
        }
        if (hands.stream().flatMap(List::stream).distinct().count() != 32
                || hands.stream().anyMatch(hand -> hand.size() != 8)) {
            throw new IllegalArgumentException("Jede der 32 Karten muss genau einmal vorkommen, je Spieler 8");
        }
        GameRecord record = new GameRecord(names, hands, contract, plays.get(0).seat(), plays);
        try {
            record.replay(hands, plays.size(), new schafkopf.rules.RuleEngine(), null);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IllegalArgumentException(
                    "Der Spielverlauf passt nicht zu den Regeln oder zur Sitzreihenfolge: " + invalid.getMessage(),
                    invalid);
        }
        return record;
    }

    private static List<String> requireNames(List<String> names, int lineNumber) {
        if (names == null) {
            throw error(lineNumber, "'spieler:' muss vor Ansage und Stichen stehen");
        }
        return names;
    }

    private static GameRecord.ContractSpec parseContract(String value, List<String> names, int lineNumber) {
        String[] words = value.split("\\s+");
        if (words.length < 2) {
            throw error(lineNumber, "Format: ansage: <Spieler> <Spielart> [Farbe] [tout]");
        }
        int caller = seatOf(words[0], names, lineNumber);
        String type = words[1].toLowerCase(Locale.GERMAN);
        Suit suit = words.length > 2 && !words[2].equalsIgnoreCase("tout") ? parseSuit(words[2], lineNumber) : null;
        boolean tout = words[words.length - 1].equalsIgnoreCase("tout");
        return switch (type) {
            case "sauspiel", "rufspiel" -> new GameRecord.ContractSpec(GameType.RUFSPIEL, caller,
                    requireSuit(suit, lineNumber), false);
            case "solo", "farbsolo" -> new GameRecord.ContractSpec(GameType.FARBSOLO, caller,
                    requireSuit(suit, lineNumber), tout);
            case "wenz" -> new GameRecord.ContractSpec(GameType.WENZ, caller, null, tout);
            case "farbwenz" -> new GameRecord.ContractSpec(GameType.FARBWENZ, caller,
                    requireSuit(suit, lineNumber), false);
            case "geier" -> new GameRecord.ContractSpec(GameType.GEIER, caller, null, false);
            case "bettel" -> new GameRecord.ContractSpec(GameType.BETTEL, caller, null, false);
            default -> throw error(lineNumber, "Unbekannte Spielart '" + words[1] + "'");
        };
    }

    private static Suit requireSuit(Suit suit, int lineNumber) {
        if (suit == null) {
            throw error(lineNumber, "Diese Spielart braucht eine Farbe");
        }
        return suit;
    }

    private static List<GameRecord.Play> parseTrick(String value, List<String> names, int lineNumber) {
        String[] entries = value.split(",");
        if (entries.length != 4) {
            throw error(lineNumber, "Ein Stich hat genau vier Karten");
        }
        List<GameRecord.Play> plays = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.strip().split("\\s+", 2);
            if (parts.length != 2) {
                throw error(lineNumber, "Format je Karte: <Spieler> <Farbe>-<Rang>");
            }
            plays.add(new GameRecord.Play(seatOf(parts[0], names, lineNumber), parseCard(parts[1], lineNumber)));
        }
        return plays;
    }

    private static int seatOf(String name, List<String> names, int lineNumber) {
        for (int seat = 0; seat < names.size(); seat++) {
            if (names.get(seat).equalsIgnoreCase(name)) {
                return seat;
            }
        }
        throw error(lineNumber, "Unbekannter Spieler '" + name + "'");
    }

    static Card parseCard(String text, int lineNumber) {
        String[] parts = text.strip().split("[-\\s]+");
        if (parts.length != 2) {
            throw error(lineNumber, "Karte '" + text + "' nicht erkannt, z. B. Gras-Ober oder Herz-10");
        }
        return new Card(parseSuit(parts[0], lineNumber), parseRank(parts[1], lineNumber));
    }

    private static Suit parseSuit(String text, int lineNumber) {
        return switch (text.toLowerCase(Locale.GERMAN)) {
            case "eichel", "e" -> Suit.EICHEL;
            case "gras", "grün", "gruen", "laub", "g" -> Suit.GRAS;
            case "herz", "h" -> Suit.HERZ;
            case "schellen", "schelln", "s" -> Suit.SCHELLEN;
            default -> throw error(lineNumber, "Unbekannte Farbe '" + text + "'");
        };
    }

    private static Rank parseRank(String text, int lineNumber) {
        return switch (text.toLowerCase(Locale.GERMAN)) {
            case "7" -> Rank.SIEBEN;
            case "8" -> Rank.ACHT;
            case "9" -> Rank.NEUN;
            case "10", "zehner", "zehn" -> Rank.ZEHN;
            case "unter", "u" -> Rank.UNTER;
            case "ober", "o" -> Rank.OBER;
            case "könig", "koenig", "k" -> Rank.KOENIG;
            case "sau", "ass", "a" -> Rank.SAU;
            default -> throw error(lineNumber, "Unbekannter Rang '" + text + "'");
        };
    }

    private static IllegalArgumentException error(int lineNumber, String message) {
        return new IllegalArgumentException("Zeile " + lineNumber + ": " + message);
    }
}
