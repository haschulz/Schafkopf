package schafkopf.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;
import schafkopf.model.GameType;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;
import schafkopf.state.SchafkopfGame;

class GameRecordParserTest {
    private final RuleEngine rules = new RuleEngine();

    private GameRecord botGame(long seed) {
        SimpleBot bot = new SimpleBot(rules, true, PolicyGenome.baseline());
        List<Player> players = List.of(new Player("1", "Sepp"), new Player("2", "Hans"),
                new Player("3", "Franz"), new Player("4", "Du"));
        SchafkopfGame game = new SchafkopfGame(players, rules, bot, SchafkopfConfig.defaults());
        game.setLogger(null);
        game.dealRandom(seed);
        game.autoDetermineRufspiel();
        game.playFullGame(players.get(0));
        return GameRecord.from(game);
    }

    @Test
    void textRoundTripKeepsTheWholeGame() {
        for (long seed = 1; seed <= 5; seed++) {
            GameRecord original = botGame(seed);

            GameRecord parsed = GameRecordParser.parse(original.toText());

            assertEquals(original, parsed);
        }
    }

    @Test
    void acceptsCommonSpellingsAndComments() {
        String text = botGame(4711L).toText()
                .replace("Schellen-", "Schelln-")
                .replace("König", "Koenig")
                .replace("ansage:", "# eigene Partie\nansage:");

        GameRecord parsed = GameRecordParser.parse(text);

        assertEquals(GameType.RUFSPIEL, parsed.contract().type());
        assertEquals(new Card(Suit.SCHELLEN, Rank.KOENIG), GameRecordParser.parseCard("Schelln-Koenig", 1));
        assertEquals(new Card(Suit.GRAS, Rank.SAU), GameRecordParser.parseCard("gras ass", 1));
    }

    @Test
    void rejectsAPlayThatBreaksTheRules() {
        String[] lines = botGame(4711L).toText().split("\n");
        String firstTrick = lines[2];
        String secondTrick = lines[3];
        String firstCardOfSecondTrick = secondTrick.substring("stich: ".length()).split(",")[0].strip();
        String swappedPlayerCard = firstTrick.substring("stich: ".length()).split(",")[0].strip();
        lines[2] = firstTrick.replace(swappedPlayerCard, firstCardOfSecondTrick);
        lines[3] = secondTrick.replace(firstCardOfSecondTrick, swappedPlayerCard);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GameRecordParser.parse(String.join("\n", lines)));

        assertTrue(error.getMessage().contains("passt nicht")
                || error.getMessage().contains("genau einmal"), error.getMessage());
    }

    @Test
    void reportsTheLineOfAnUnknownCard() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GameRecordParser.parse("spieler: A, B, C, D\nansage: A sauspiel eichel\nstich: A Blatt-7, B Herz-7, C Herz-8, D Herz-9"));

        assertTrue(error.getMessage().startsWith("Zeile 3"), error.getMessage());
    }
}
