package schafkopf.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import schafkopf.ai.SimpleBot;
import schafkopf.config.SchafkopfConfig;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;
import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.GameType;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;
import schafkopf.state.SchafkopfGame;

class InteractiveTrainingGameTest {
    @TempDir
    Path tempDir;

    @Test
    void completesAndSettlesOneInteractiveRound() throws Exception {
        Path configPath = tempDir.resolve("training.properties");
        Files.writeString(configPath, """
                noGame.policy=RAMSCH
                klopfen.enabled=false
                kontra.enabled=false
                re.enabled=false
                """);
        String previousConfig = System.getProperty("schafkopf.config");
        System.setProperty("schafkopf.config", configPath.toString());

        List<Player> players = List.of(
                new Player("1", "Sepp"),
                new Player("2", "Hans"),
                new Player("3", "Franz"),
                new Player("4", "Du"));
        ScoreLedger ledger = new ScoreLedger(players, new Tariff(SchafkopfConfig.defaults()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            byte[] coachChoices = "c\n".repeat(40).getBytes(StandardCharsets.UTF_8);
            new InteractiveTrainingGame(
                    new ByteArrayInputStream(coachChoices), SchafkopfConfig.defaults())
                    .startInteractiveSession(ledger);
        } finally {
            System.setOut(originalOut);
            if (previousConfig == null) {
                System.clearProperty("schafkopf.config");
            } else {
                System.setProperty("schafkopf.config", previousConfig);
            }
        }

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("ENDSTAND & TARIF-ABRECHNUNG"));
        assertEquals(0, players.stream().mapToInt(ledger::getBalance).sum());
    }

    @Test
    void defendingBotContraAllowsHumanSoloistToCallRetour() {
        SchafkopfConfig config = new SchafkopfConfig(
                SchafkopfConfig.NoGamePolicy.RAMSCH, false, 4, true, true,
                10, 20, 50, false, 2, GameType.FARBSOLO, Suit.EICHEL);
        List<Player> players = List.of(
                new Player("1", "Sepp"),
                new Player("2", "Hans"),
                new Player("3", "Franz"),
                new Player("4", "Du"));
        RuleEngine rules = new RuleEngine();
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules), config);
        game.setLogger(ignored -> { });
        game.setCustomHands(handsWithHighTrumpDefender());
        game.setupContract(new SoloContract(players.get(3), Suit.EICHEL));

        InteractiveTrainingGame training = new InteractiveTrainingGame(
                new ByteArrayInputStream("j\n".getBytes(StandardCharsets.UTF_8)), config);
        training.resolveKontraAndRe(game, players.get(3));

        assertEquals(1, game.bettingState().kontraCount());
        assertEquals(1, game.bettingState().retourCount());
        assertEquals(4, game.bettingState().multiplier());
    }

    private static List<List<Card>> handsWithHighTrumpDefender() {
        List<List<Card>> hands = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        hands.get(0).addAll(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.OBER),
                new Card(Suit.SCHELLEN, Rank.OBER)));
        hands.get(3).addAll(List.of(
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.EICHEL, Rank.ACHT),
                new Card(Suit.EICHEL, Rank.NEUN)));

        int nextSeat = 0;
        for (Card card : new Deck().cards()) {
            if (hands.stream().anyMatch(hand -> hand.contains(card))) {
                continue;
            }
            while (hands.get(nextSeat).size() == 8) {
                nextSeat = (nextSeat + 1) % hands.size();
            }
            hands.get(nextSeat).add(card);
            nextSeat = (nextSeat + 1) % hands.size();
        }
        return hands.stream().map(List::copyOf).toList();
    }
}