package schafkopf.test;

import org.junit.jupiter.api.Test;

import schafkopf.ai.GameMemory;
import schafkopf.ai.SimpleBot;
import schafkopf.model.Card;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.SuitCategory;
import schafkopf.model.Trick;
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;

import java.util.List;

public class TacticalEngineTest {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("               UNIT TESTS FÜR TAKTIK- UND GEDÄCHTNIS-ENGINE               ");
        System.out.println("==========================================================================");

        testDefiniteVoidTracking();
        testGewinnschwelleSchmieren();
        testAbstichGefahrSchmierenAbbruch();
        testLegacyOverloadIsConservativeWithoutRemainingPlayers();
        testWinningMoveMinimizesPointsBeforeTrumpStrength();
        testUnshieldedTenIsPreferredForSmearing();
        testFreispielenTaktik();
        testSauHeraustreiben();

        System.out.println("\n>>> ALLE TAKTIK-TESTS ERFOLGREICH BESTANDEN! <<<\n");
    }

    @Test
    void tacticalScenarios() {
        main(new String[0]);
    }

    private static void assertEquals(Object expected, Object actual, String testName) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    String.format("FAIL [%s]: Expected '%s', but got '%s'", testName, expected, actual));
        }
        System.out.println("  ✓ PASSED: " + testName);
    }

    private static void assertTrue(boolean condition, String testName) {
        if (!condition) {
            throw new AssertionError("FAIL [" + testName + "]: Condition was false");
        }
        System.out.println("  ✓ PASSED: " + testName);
    }

    /**
     * TEST 1: Hans hat vorhin bei Schellen kein Schellen zugegeben -> Hans ist
     * definitiv Schellen-frei!
     */
    private static void testDefiniteVoidTracking() {
        System.out.println("\n[Test 1: Definite Void Tracking (Farbfreiheit)]");
        RuleEngine rules = new RuleEngine();
        Player p1 = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player p3 = new Player("3", "Franz");
        Player p4 = new Player("4", "Michl");
        List<Player> players = List.of(p1, hans, p3, p4);

        RufspielContract contract = new RufspielContract(p1, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);

        // Trick 1: Sepp leads Schellen-7 (Fehlfarbe Schellen)
        Trick trick = new Trick(1, p1);
        Card schellen7 = new Card(Suit.SCHELLEN, Rank.SIEBEN);
        memory.recordCardPlayed(p1, schellen7, trick, contract);
        trick.addCard(p1, schellen7);

        // Hans plays Herz-8 (Trumpf!) instead of Schellen
        Card herz8 = new Card(Suit.HERZ, Rank.ACHT);
        memory.recordCardPlayed(hans, herz8, trick, contract);
        trick.addCard(hans, herz8);

        // Check: Is Hans definitely void in Schellen?
        assertTrue(memory.isDefiniteVoid(hans, SuitCategory.SCHELLEN), "Hans ist nachweislich Schellen-frei");
        assertTrue(!memory.isDefiniteVoid(hans, SuitCategory.TRUMP), "Hans ist NICHT trumpf-frei");
    }

    /**
     * TEST 2: "Erst gewinnen, dann nachdenken"
     * Teampartner führt den sicheren Stich. Team steht bei 50 Augen, im Stich
     * liegen 4 Augen.
     * Mitspieler hat blanke Eichel-Sau (11 Augen): 50 + 4 + 11 = 65 >= 61 ->
     * Kompromisslos schmieren!
     */
    private static void testGewinnschwelleSchmieren() {
        System.out.println("\n[Test 2: Gewinnschwellen-Regel (>= 61 Augen -> kompromisslos Schmieren)]");
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);

        RufspielContract contract = new RufspielContract(sepp, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        // Precondition: Sepp + Franz (Team) already have 50 points accumulated
        Trick prevTrick = new Trick(1, sepp);
        prevTrick.addCard(sepp, new Card(Suit.HERZ, Rank.ZEHN)); // 10
        prevTrick.addCard(hans, new Card(Suit.HERZ, Rank.SAU)); // 11
        prevTrick.addCard(franz, new Card(Suit.HERZ, Rank.KOENIG)); // 4
        prevTrick.addCard(michl, new Card(Suit.HERZ, Rank.SIEBEN)); // 0
        rules.evaluateTrickWinner(prevTrick);
        sepp.addWonTrick(prevTrick);
        memory.recordTrickCompleted(prevTrick, sepp); // 25 points
        // Add another 25 points
        memory.recordTrickCompleted(prevTrick, sepp); // total 50 points

        // Current Trick: Sepp (caller) leads Eichel-Ober (highest trump -> 100% safe!)
        Trick trick = new Trick(2, sepp);
        Card eichelOber = new Card(Suit.EICHEL, Rank.OBER);
        memory.recordCardPlayed(sepp, eichelOber, trick, contract);
        trick.addCard(sepp, eichelOber);

        // Hans plays Herz-9
        Card herz9 = new Card(Suit.HERZ, Rank.NEUN);
        memory.recordCardPlayed(hans, herz9, trick, contract);
        trick.addCard(hans, herz9);

        // Franz is partner. Holds blank Eichel-Sau (11 Augen) and Eichel-7. He is
        // farbfrei in trump.
        franz.setHand(List.of(new Card(Suit.EICHEL, Rank.SAU), new Card(Suit.SCHELLEN, Rank.SIEBEN)));

        // Franz's turn: Remaining players = [Michl]
        List<Player> remaining = List.of(michl);

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 2, memory, remaining);

        assertEquals(Rank.SAU, move.card().rank(), "Franz schmiert die Eichel-Sau, um 61 Augen zu erreichen");
        assertTrue(move.tacticalReasoning().contains("GEWINNSCHWELLE ERREICHT"), "Begründung enthält Gewinnschwelle");
    }

    /**
     * TEST 3: Akute Abstichgefahr
     * Partner führt mit Gras-Sau. Aber Michl (nachfolgender Gegner) ist
     * nachweislich Gras-frei und hat Trümpfe!
     * Franz darf NICHT seine 10 schmieren, sondern muss Schmieren abbrechen und
     * kleine Karte spielen!
     */
    private static void testAbstichGefahrSchmierenAbbruch() {
        System.out.println("\n[Test 3: Abstichgefahr -> Schmieren abbrechen]");
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);

        RufspielContract contract = new RufspielContract(sepp, Suit.SCHELLEN);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        // Precondition: Michl is DEFINITELY VOID in Gras!
        Trick prevTrick = new Trick(1, sepp);
        memory.recordCardPlayed(sepp, new Card(Suit.GRAS, Rank.SIEBEN), prevTrick, contract);
        prevTrick.addCard(sepp, new Card(Suit.GRAS, Rank.SIEBEN));
        // Michl played Schellen-7 on Gras lead in prev trick -> void in Gras!
        memory.recordCardPlayed(michl, new Card(Suit.SCHELLEN, Rank.SIEBEN), prevTrick, contract);

        assertTrue(memory.isDefiniteVoid(michl, SuitCategory.GRAS), "Michl ist Gras-frei");

        // Current trick: Sepp leads Gras-Sau. Hans plays Gras-8.
        Trick trick = new Trick(2, sepp);
        memory.recordCardPlayed(sepp, new Card(Suit.GRAS, Rank.SAU), trick, contract);
        trick.addCard(sepp, new Card(Suit.GRAS, Rank.SAU));

        memory.recordCardPlayed(hans, new Card(Suit.GRAS, Rank.ACHT), trick, contract);
        trick.addCard(hans, new Card(Suit.GRAS, Rank.ACHT));

        // Franz has Gras-10 and Gras-7
        franz.setHand(List.of(new Card(Suit.GRAS, Rank.ZEHN), new Card(Suit.GRAS, Rank.SIEBEN)));

        // Michl still to play behind Franz!
        List<Player> remaining = List.of(michl);

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 2, memory, remaining);

        assertEquals(Rank.SIEBEN, move.card().rank(), "Franz bricht Schmieren ab und wirft Gras-7 statt Gras-10 ab");
        assertTrue(move.tacticalReasoning().contains("Abstich-Gefahr"),
                "Begründung nennt Abstichgefahr durch farbfreien Gegner");
    }

    private static void testLegacyOverloadIsConservativeWithoutRemainingPlayers() {
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player franz = new Player("3", "Franz");
        RufspielContract contract = new RufspielContract(sepp, Suit.SCHELLEN);
        SimpleBot bot = new SimpleBot(rules);

        Trick trick = new Trick(2, sepp);
        trick.addCard(sepp, new Card(Suit.EICHEL, Rank.SAU));
        franz.setHand(List.of(new Card(Suit.EICHEL, Rank.ZEHN), new Card(Suit.EICHEL, Rank.SIEBEN)));

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 2);

        assertEquals(Rank.SIEBEN, move.card().rank(),
                "Ohne Nachhand-Information schont der Bot die Augen statt blind zu schmieren");
    }

    private static void testWinningMoveMinimizesPointsBeforeTrumpStrength() {
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);
        RufspielContract contract = new RufspielContract(sepp, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        Card herz10 = new Card(Suit.HERZ, Rank.ZEHN);
        Card eichelUnter = new Card(Suit.EICHEL, Rank.UNTER);
        Trick trick = new Trick(2, hans);
        trick.addCard(hans, new Card(Suit.HERZ, Rank.NEUN));
        franz.setHand(List.of(herz10, eichelUnter));

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 2, memory, List.of(michl));

        assertEquals(eichelUnter, move.card(),
                "Der gewinnende Unter mit 2 Augen ist günstiger als die Herz-10 mit 10 Augen");
    }

    private static void testUnshieldedTenIsPreferredForSmearing() {
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);
        RufspielContract contract = new RufspielContract(sepp, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        Card protectedTen = new Card(Suit.EICHEL, Rank.ZEHN);
        Card unshieldedTen = new Card(Suit.SCHELLEN, Rank.ZEHN);
        Trick trick = new Trick(2, sepp);
        trick.addCard(sepp, new Card(Suit.EICHEL, Rank.OBER));
        franz.setHand(List.of(new Card(Suit.EICHEL, Rank.SAU), protectedTen, unshieldedTen));

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 2, memory, List.of(michl));

        assertEquals(unshieldedTen, move.card(),
                "Die ungeschützte Zehn wird vor der durch die Sau geschützten Zehn geschmiert");
    }

    /**
     * TEST 4: Taktisches Freispielen
     * Gegner führt den Stich uneinholbar. Spieler kann nicht gewinnen und hat eine
     * einzelne Schellen-7.
     * Er wirft Schellen-7 ab, um sich für die nächsten Stiche frei zu spielen!
     */
    private static void testFreispielenTaktik() {
        System.out.println("\n[Test 4: Taktisches Freispielen]");
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);

        RufspielContract contract = new RufspielContract(sepp, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        // Hans (Gegner) leads Eichel-Ober (unbeatable trump)
        Trick trick = new Trick(1, hans);
        memory.recordCardPlayed(hans, new Card(Suit.EICHEL, Rank.OBER), trick, contract);
        trick.addCard(hans, new Card(Suit.EICHEL, Rank.OBER));

        // Franz has no trumps. He has 3 Gras cards (Gras-8, Gras-9, Gras-König) and
        // exactly ONE Schellen-7.
        franz.setHand(List.of(
                new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.GRAS, Rank.NEUN),
                new Card(Suit.GRAS, Rank.KOENIG),
                new Card(Suit.SCHELLEN, Rank.SIEBEN)));

        MoveExplanation move = bot.chooseMove(franz, trick, contract, franz, true, 1, memory, List.of(michl, sepp));

        assertEquals(Suit.SCHELLEN, move.card().suit(), "Franz wirft einzelne Schellen-7 ab, um sich freizuspielen");
        assertTrue(move.tacticalReasoning().contains("freizuspielen"), "Begründung erwähnt Freispielen");
    }

    /**
     * TEST 5: Ausschlussverfahren & Sau-Falle ("Gegnerische Sau heraustreiben")
     * Alle Schellen sind gefallen bis auf Schellen-7 und Schellen-Sau.
     * Sepp (Rufer) hat Schellen-7. Partner Franz ist Schellen-frei und hat Trümpfe.
     * Gegner Hans hat nach Ausschlussverfahren die Schellen-Sau.
     * Sepp spielt Schellen-7 an -> treibt Hans' Schellen-Sau heraus -> Franz sticht
     * sie ab!
     */
    private static void testSauHeraustreiben() {
        System.out.println("\n[Test 5: Ausschlussverfahren & Sau-Heraustreiben ('Sau-Falle')]");
        RuleEngine rules = new RuleEngine();
        Player sepp = new Player("1", "Sepp");
        Player hans = new Player("2", "Hans");
        Player franz = new Player("3", "Franz");
        Player michl = new Player("4", "Michl");
        List<Player> players = List.of(sepp, hans, franz, michl);

        RufspielContract contract = new RufspielContract(sepp, Suit.GRAS);
        GameMemory memory = new GameMemory(players, rules);
        SimpleBot bot = new SimpleBot(rules);

        // Precondition: Schellen-10, Schellen-König, Schellen-9, Schellen-8 are already
        // played!
        Trick dummyTrick = new Trick(1, sepp);
        memory.recordCardPlayed(sepp, new Card(Suit.SCHELLEN, Rank.ZEHN), dummyTrick, contract);
        memory.recordCardPlayed(hans, new Card(Suit.SCHELLEN, Rank.KOENIG), dummyTrick, contract);
        memory.recordCardPlayed(franz, new Card(Suit.SCHELLEN, Rank.NEUN), dummyTrick, contract);
        memory.recordCardPlayed(michl, new Card(Suit.SCHELLEN, Rank.ACHT), dummyTrick, contract);

        // Precondition: Partner Franz and opponent Michl have shown to be Schellen-frei
        // in a later trick
        Trick t2 = new Trick(2, sepp);
        memory.recordCardPlayed(sepp, new Card(Suit.HERZ, Rank.ZEHN), t2, contract);
        // Franz & Michl bedienten in einem früheren Schellen-Anspiel nicht ->
        // Schellen-frei!
        Trick schellenTrick = new Trick(3, sepp);
        Card schellenLead = new Card(Suit.SCHELLEN, Rank.KOENIG);
        schellenTrick.addCard(sepp, schellenLead);
        // Franz played Herz-7 on Schellen lead
        memory.recordCardPlayed(franz, new Card(Suit.HERZ, Rank.SIEBEN), schellenTrick, contract);
        schellenTrick.addCard(franz, new Card(Suit.HERZ, Rank.SIEBEN));
        // Michl played Herz-8 on Schellen lead
        memory.recordCardPlayed(michl, new Card(Suit.HERZ, Rank.ACHT), schellenTrick, contract);
        schellenTrick.addCard(michl, new Card(Suit.HERZ, Rank.ACHT));

        assertTrue(memory.isDefiniteVoid(franz, SuitCategory.SCHELLEN), "Partner Franz ist Schellen-frei");
        assertTrue(memory.isDefiniteVoid(michl, SuitCategory.SCHELLEN), "Gegner Michl ist Schellen-frei");

        // Check possible holders of Schellen-Sau: Only Hans remains!
        Card schellenSau = new Card(Suit.SCHELLEN, Rank.SAU);
        List<Player> holders = memory.getPossibleHolders(schellenSau, sepp);
        assertEquals(1, holders.size(), "Nur Hans kann nach Ausschlussverfahren die Schellen-Sau haben");
        assertEquals(hans, holders.get(0), "Hans ist der garantierte Inhaber der Schellen-Sau");

        // Now Sepp leads in Trick 4: He has Schellen-7 and an Eichel-8.
        sepp.setHand(List.of(new Card(Suit.SCHELLEN, Rank.SIEBEN), new Card(Suit.EICHEL, Rank.ACHT)));
        Trick currentTrick = new Trick(4, sepp);

        MoveExplanation move = bot.chooseMove(sepp, currentTrick, contract, franz, true, 4, memory,
                List.of(hans, franz, michl));

        assertEquals(Suit.SCHELLEN, move.card().suit(), "Sepp spielt Schellen-7 an");
        assertEquals(Rank.SIEBEN, move.card().rank(), "Sepp spielt kleine Schellen-7");
        assertTrue(move.tacticalReasoning().contains("heraus"), "Begründung nennt Sau-Heraustreiben");
    }
}
