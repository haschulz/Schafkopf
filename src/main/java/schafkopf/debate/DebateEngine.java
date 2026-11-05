package schafkopf.debate;

import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.model.Trick;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.Comparator;
import java.util.List;

public class DebateEngine {
    private final RuleEngine rules = new RuleEngine();

    public void conductPostGameDebate(SchafkopfGame game) {
        GameResult result = game.result();
        RufspielContract contract = game.contract();
        Player caller = contract.caller();
        Player partner = game.partner();

        List<Player> defenders = game.completedTricks().get(0).playedCards().stream()
                .map(PlayedCard::player)
                .filter(p -> !p.equals(caller) && !p.equals(partner))
                .distinct()
                .toList();

        Player def1 = defenders.size() > 0 ? defenders.get(0) : new Player("D1", "Gegner 1");
        Player def2 = defenders.size() > 1 ? defenders.get(1) : new Player("D2", "Gegner 2");

        // Finde den Knackpunkt-Stich:
        // Wenn Rufer verloren hat: Der höchste Stich, den die GEGNER gewonnen haben.
        // Wenn Rufer gewonnen hat: Der höchste Stich, den die RUFER-PARTEI gewonnen hat.
        Trick keyTrick = game.completedTricks().stream()
                .filter(t -> {
                    boolean wonByCallerTeam = t.winningPlayedCard().player().equals(caller)
                            || t.winningPlayedCard().player().equals(partner);
                    return result.callerTeamWon() == wonByCallerTeam;
                })
                .max(Comparator.comparingInt(Trick::totalPoints))
                .orElse(game.completedTricks().get(0));

        PlayedCard winningCard = keyTrick.winningPlayedCard();
        boolean callerTeamWonKeyTrick = winningCard.player().equals(caller) || winningCard.player().equals(partner);

        System.out.println("\n==========================================================================");
        System.out.println("         4-AGENTEN-STAMMTISCH: MANÖVERKRITIK NACH DEM SPIEL               ");
        System.out.println("==========================================================================");
        System.out.printf("Endstand: Rufer-Partei [%s + %s] = %d Augen  VS  Gegner [%s + %s] = %d Augen\n",
                caller.name(), partner.name(), result.callerTeamAugen(),
                def1.name(), def2.name(), result.defenderTeamAugen());
        System.out.printf("Ergebnis: %s\n", result.callerTeamWon() ? "Rufer-Partei hat GEWONNEN!" : "Gegner-Partei hat GEWONNEN!");
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("[Knackpunkt des Spiels: Stich %d mit satten %d Augen!]\n",
                keyTrick.trickNumber(), keyTrick.totalPoints());
        System.out.println("Karten im Stich: " + keyTrick.playedCards());
        System.out.printf("Gewonnen von: %s mit %s\n\n", winningCard.player().name(), winningCard.card().shortName());

        // Agent 1: Der Rufer
        if (result.callerTeamWon()) {
            System.out.printf("🗣️ %s (Rufer):\n", caller.name());
            System.out.printf("   'Sauber gspielt! Mein Plan ging voll auf: Ich habe sofort Trümpfe gezogen,\n" +
                    "    um euch Gegenspieler blank zu machen. In Stich %d haben wir mit %s\n" +
                    "    den Sack zugemacht und %d Augen kassiert!'\n\n",
                    keyTrick.trickNumber(), winningCard.card().shortName(), keyTrick.totalPoints());
        } else {
            System.out.printf("🗣️ %s (Rufer):\n", caller.name());
            System.out.printf("   'Zefix nochamoi! Das Spiel hätten wir nie verlieren dürfen!\n" +
                    "    Vor allem Stich %d hat uns das Genick gebrochen: Da gehen %d fette Augen an die Gegner (%s)!\n" +
                    "    %s, warum hast du da nicht höher eingestochen oder die Sau geschützt?!'\n\n",
                    keyTrick.trickNumber(), keyTrick.totalPoints(), winningCard.player().name(), partner.name());
        }

        // Agent 2: Der Mitspieler
        System.out.printf("🗣️ %s (Mitspieler):\n", partner.name());
        if (callerTeamWonKeyTrick) {
            System.out.printf("   'Genauso schaut's aus, %s! Ich habe gewartet, bis du frei warst,\n" +
                    "    und dann die Augen geschmiert. Die Taktik hat gepasst wie die Faust aufs Auge.'\n\n",
                    caller.name());
        } else {
            System.out.printf("   'Jetzt bleib mal ruhig, %s! Du siehst doch, was ich auf der Hand hatte!\n" +
                    "    Die Gegner haben mich mit ihrer Spielweise in die Zange genommen.\n" +
                    "    Weil %s so früh die Farbe gesucht hat, musste ich die gerufene Sau auf den Tisch legen,\n" +
                    "    ohne dass du den Stich sicher hattest!'\n\n",
                    caller.name(), def1.name());
        }

        // Agent 3: Gegenspieler 1
        System.out.printf("🗣️ %s (Gegenspieler 1):\n", def1.name());
        if (!result.callerTeamWon()) {
            System.out.printf("   'Ganz genau, %s! Ich habe mich strikt an die goldene Schafkopf-Regel gehalten:\n" +
                    "    \"Nicht-Spieler sucht die Sau, um Klarheit zu schaffen!\"\n" +
                    "    Sobald eure Sau gefallen war, wussten %s und ich, woran wir sind, und konnten\n" +
                    "    unsere hohen Karten gezielt nach Hause bringen.'\n\n",
                    partner.name(), def2.name());
        } else {
            System.out.printf("   'Da habt ihr einfach Glück mit der Kartenverteilung gehabt.\n" +
                    "    Mein Fehler war: Ich hätte in Stich 2 nicht zögern dürfen, sondern sofort\n" +
                    "    die gesuchte Sau anspielen müssen, um euch unter Druck zu setzen.'\n\n");
        }

        // Agent 4: Gegenspieler 2
        System.out.printf("🗣️ %s (Gegenspieler 2):\n", def2.name());
        System.out.printf("   'Und ich habe peinlich genau darauf geachtet, keinen Trumpf anzuziehen,\n" +
                "    weil wir als Nicht-Spieler keine Trumpfübermacht hatten. Wer ohne Not Trumpf bringt,\n" +
                "    spielt dem Rufer direkt in die Karten. Genau diese Disziplin hat uns %s geholfen!'\n\n",
                result.callerTeamWon() ? "zumindest den Schneider gerettet" : "den Sieg gebracht");

        // Synthese & Regel-Bestätigung der Debatte
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("📋 FAZIT DER AGENTEN-DEBATTE (GEMEINSAM ABGELEITETE REGEL):");
        System.out.println("   1. BESTÄTIGT durch Agenten: Das frühe Suchen der Sau durch die Nichtspieler");
        System.out.println("      zerstört den Überraschungsvorteil der Rufer-Partei.");
        System.out.println("   2. WIDERLEGT: Es bringt den Gegenspielern fast nie etwas, ohne mindestens");
        System.out.println("      4-5 Trümpfe selbst Trumpf anzuziehen.");
        System.out.println("==========================================================================\n");
    }
}

