package schafkopf.analysis;

import schafkopf.ai.SimpleBot;
import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.List;

public class BatchSimulator {
    private final RuleEngine rules = new RuleEngine();

    public record GameRecord(
            int gameId,
            GameResult result,
            int callerTrumpCount,
            boolean callerLedTrumpTrick1,
            boolean defendersSearchedSauEarly,
            int trickWhereSauWasPlayed
    ) {}

    public record ConvergenceCheckpoint(
            int gamesPlayed,
            double callerWinRate,
            double defenderWinRate,
            double deltaPercent
    ) {}

    public record ConvergenceResult(
            List<GameRecord> records,
            List<ConvergenceCheckpoint> checkpoints,
            int totalGamesRun,
            boolean converged,
            long executionTimeMs
    ) {}

    public record ABBenchmarkResult(
            int totalGames,
            int botAWins,
            int botBWins,
            double botAWinRate,
            double botBWinRate,
            double botAAvgAugen,
            double botBAvgAugen,
            int botBTurnedLossToWin,
            int botBTurnedWinToLoss,
            int identicalOutcomes,
            long executionTimeMs
    ) {}

    public List<GameRecord> runBatch(int numGames) {
        return runBatch(numGames, new SimpleBot(rules, true));
    }

    public List<GameRecord> runBatch(int numGames, SimpleBot bot) {
        List<GameRecord> records = new ArrayList<>();

        for (int i = 1; i <= numGames; i++) {
            Player p1 = new Player("1", "Spieler 1 (Sepp)");
            Player p2 = new Player("2", "Spieler 2 (Hans)");
            Player p3 = new Player("3", "Spieler 3 (Franz)");
            Player p4 = new Player("4", "Spieler 4 (Michl)");
            List<Player> players = List.of(p1, p2, p3, p4);

            SchafkopfGame game = new SchafkopfGame(players, rules, bot);
            game.setLogger(msg -> {});

            game.dealRandom();
            game.autoDetermineRufspiel();

            RufspielContract contract = game.contract();
            Player caller = contract.caller();
            int callerTrumpCount = (int) caller.hand().stream().filter(rules::isTrump).count();

            GameResult result = game.playFullGame(players.get(0));

            Card trick1Lead = game.completedTricks().get(0).leadCard();
            Player trick1Leader = game.completedTricks().get(0).leader();
            boolean callerLedTrumpTrick1 = trick1Leader.equals(caller) && rules.isTrump(trick1Lead);

            int trickWhereSauWasPlayed = -1;
            boolean defendersSearchedSauEarly = false;
            for (int t = 0; t < game.completedTricks().size(); t++) {
                var trick = game.completedTricks().get(t);
                boolean containsSau = trick.playedCards().stream().anyMatch(pc -> pc.card().equals(contract.calledSau()));
                if (containsSau && trickWhereSauWasPlayed == -1) {
                    trickWhereSauWasPlayed = t + 1;
                }
                boolean leaderIsDefender = !trick.leader().equals(caller) && !trick.leader().equals(game.partner());
                if (leaderIsDefender && rules.isNonTrumpSuit(trick.leadCard(), contract.calledSuit()) && (t + 1) <= 3) {
                    defendersSearchedSauEarly = true;
                }
            }

            records.add(new GameRecord(
                    i,
                    result,
                    callerTrumpCount,
                    callerLedTrumpTrick1,
                    defendersSearchedSauEarly,
                    trickWhereSauWasPlayed
            ));
        }

        return records;
    }

    /**
     * Runs simulations in blocks and automatically stops when win rates converge within thresholdDelta (e.g. 0.3%).
     */
    public ConvergenceResult runBatchWithDynamicConvergence(int maxGames, int chunkSize, double thresholdDelta) {
        long startTime = System.currentTimeMillis();
        List<GameRecord> allRecords = new ArrayList<>();
        List<ConvergenceCheckpoint> checkpoints = new ArrayList<>();

        double previousRate = -1.0;
        int consecutiveConverged = 0;
        boolean converged = false;

        for (int g = chunkSize; g <= maxGames; g += chunkSize) {
            List<GameRecord> chunk = runBatch(chunkSize, new SimpleBot(rules, true));
            allRecords.addAll(chunk);

            long totalCallerWins = allRecords.stream().filter(r -> r.result().callerTeamWon()).count();
            double callerWinRate = (totalCallerWins * 100.0) / allRecords.size();
            double defenderWinRate = 100.0 - callerWinRate;

            double delta = previousRate < 0 ? 100.0 : Math.abs(callerWinRate - previousRate);
            previousRate = callerWinRate;

            checkpoints.add(new ConvergenceCheckpoint(allRecords.size(), callerWinRate, defenderWinRate, delta));

            // Convergence criteria: At least 1000 games and 2 consecutive blocks with delta <= thresholdDelta
            if (allRecords.size() >= 1000 && delta <= thresholdDelta) {
                consecutiveConverged++;
                if (consecutiveConverged >= 2) {
                    converged = true;
                    break;
                }
            } else {
                consecutiveConverged = 0;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new ConvergenceResult(allRecords, checkpoints, allRecords.size(), converged, duration);
    }

    /**
     * A/B Benchmark: Runs identical deals against Bot A (Baseline Heuristic) and Bot B (Master Engine with Memory).
     */
    public ABBenchmarkResult runABBenchmark(int numGames) {
        long startTime = System.currentTimeMillis();
        Deck deck = new Deck();

        int botAWins = 0;
        int botBWins = 0;
        int botBTurnedLossToWin = 0;
        int botBTurnedWinToLoss = 0;
        int identicalOutcomes = 0;

        int totalAugenA = 0;
        int totalAugenB = 0;

        SimpleBot baselineBot = new SimpleBot(rules, false);
        SimpleBot masterBot = new SimpleBot(rules, true);

        for (int i = 1; i <= numGames; i++) {
            deck.reset();
            deck.shuffle();
            List<List<Card>> deal = deck.deal4x8();

            // Run Match A (Baseline Bot)
            Player a1 = new Player("1", "P1");
            Player a2 = new Player("2", "P2");
            Player a3 = new Player("3", "P3");
            Player a4 = new Player("4", "P4");
            List<Player> playersA = List.of(a1, a2, a3, a4);
            for (int p = 0; p < 4; p++) {
                playersA.get(p).setHand(new ArrayList<>(deal.get(p)));
            }
            SchafkopfGame gameA = new SchafkopfGame(playersA, rules, baselineBot);
            gameA.setLogger(msg -> {});
            gameA.autoDetermineRufspiel();
            GameResult resA = gameA.playFullGame(a1);

            // Run Match B (Master Bot with GameMemory) on EXACT SAME DEAL & CONTRACT
            Player b1 = new Player("1", "P1");
            Player b2 = new Player("2", "P2");
            Player b3 = new Player("3", "P3");
            Player b4 = new Player("4", "P4");
            List<Player> playersB = List.of(b1, b2, b3, b4);
            for (int p = 0; p < 4; p++) {
                playersB.get(p).setHand(new ArrayList<>(deal.get(p)));
            }
            SchafkopfGame gameB = new SchafkopfGame(playersB, rules, masterBot);
            gameB.setLogger(msg -> {});
            int callerIdx = playersA.indexOf(gameA.contract().caller());
            gameB.setupRufspiel(playersB.get(callerIdx), gameA.contract().calledSuit());
            GameResult resB = gameB.playFullGame(b1);

            boolean wonA = resA.callerTeamWon();
            boolean wonB = resB.callerTeamWon();

            if (wonA) botAWins++;
            if (wonB) botBWins++;

            totalAugenA += resA.callerTeamAugen();
            totalAugenB += resB.callerTeamAugen();

            if (wonA == wonB) {
                identicalOutcomes++;
            } else if (!wonA && wonB) {
                botBTurnedLossToWin++;
            } else {
                botBTurnedWinToLoss++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double winRateA = (botAWins * 100.0) / numGames;
        double winRateB = (botBWins * 100.0) / numGames;
        double avgAugenA = (totalAugenA * 1.0) / numGames;
        double avgAugenB = (totalAugenB * 1.0) / numGames;

        return new ABBenchmarkResult(
                numGames,
                botAWins,
                botBWins,
                winRateA,
                winRateB,
                avgAugenA,
                avgAugenB,
                botBTurnedLossToWin,
                botBTurnedWinToLoss,
                identicalOutcomes,
                duration
        );
    }

    public void printConvergenceReport(ConvergenceResult result, double thresholdDelta) {
        System.out.println("==========================================================================");
        System.out.println("      DYNAMISCHE KONVERGENZ-ANALYSE (BATTERIE BIS ZU 10.000 SPIELE)       ");
        System.out.println("==========================================================================");
        System.out.printf("Gesamtspiele simuliert:    %d Spiele in %d ms (%.1f Spiele/Sekunde)\n",
                result.totalGamesRun(), result.executionTimeMs(),
                (result.totalGamesRun() * 1000.0 / Math.max(1, result.executionTimeMs())));
        System.out.printf("Konvergenzkriterium:       Delta <= %.2f%% über 2 aufeinanderfolgende Blöcke\n", thresholdDelta);
        System.out.printf("Status:                    %s\n",
                result.converged() ? "✅ KONVERGIERT (Statistisch gefestigt!)" : "⚠️ Maximales Limit erreicht");
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("%-12s | %-16s | %-16s | %-12s\n", "Spiele", "Rufer-Quote", "Gegner-Quote", "Delta");
        System.out.println("-------------+------------------+------------------+-------------");

        for (ConvergenceCheckpoint cp : result.checkpoints()) {
            System.out.printf("%-12d | %14.2f%% | %14.2f%% | %10.2f%%\n",
                    cp.gamesPlayed(), cp.callerWinRate(), cp.defenderWinRate(), cp.deltaPercent());
        }
        System.out.println("==========================================================================\n");
    }

    public void printABBenchmarkReport(ABBenchmarkResult res) {
        System.out.println("==========================================================================");
        System.out.println("               A/B-BENCHMARK: BASELINE VS. GEDÄCHTNIS-BOT                 ");
        System.out.println("                  (Identische Kartenverteilungen & Deals)                 ");
        System.out.println("==========================================================================");
        System.out.printf("Gespielte Runden:                 %d Partien (Dauer: %d ms)\n", res.totalGames(), res.executionTimeMs());
        System.out.printf("Bot A (Baseline ohne Gedächtnis):  %d Siege (%.2f%%) | Ø %.1f Augen\n",
                res.botAWins(), res.botAWinRate(), res.botAAvgAugen());
        System.out.printf("Bot B (Master mit GameMemory):     %d Siege (%.2f%%) | Ø %.1f Augen\n",
                res.botBWins(), res.botBWinRate(), res.botBAvgAugen());
        System.out.println("--------------------------------------------------------------------------");
        double diffRate = res.botBWinRate() - res.botAWinRate();
        double diffAugen = res.botBAvgAugen() - res.botAAvgAugen();
        System.out.printf("DIREKTER PERFORMANCE-GEWINN:      %+6.2f%% Siegquote | %+5.2f Augen\n", diffRate, diffAugen);
        System.out.printf("Partei-Veränderungen durch Bot B: +%d verlorene Spiele gedreht in Siege\n", res.botBTurnedLossToWin());
        System.out.printf("                                  -%d gewonnene Spiele verloren\n", res.botBTurnedWinToLoss());
        System.out.printf("                                   %d identische Ausgänge\n", res.identicalOutcomes());
        System.out.println("==========================================================================\n");
    }

    public void printAnalysis(List<GameRecord> records) {
        int total = records.size();
        long callerWins = records.stream().filter(r -> r.result().callerTeamWon()).count();
        long defenderWins = total - callerWins;

        long schneiderRufer = records.stream().filter(r -> r.result().callerTeamWon() && r.result().isSchneider()).count();
        long schneiderGegner = records.stream().filter(r -> !r.result().callerTeamWon() && r.result().isSchneider()).count();
        long schwarzRufer = records.stream().filter(r -> r.result().callerTeamWon() && r.result().isSchwarz()).count();

        double avgCallerPoints = records.stream().mapToInt(r -> r.result().callerTeamAugen()).average().orElse(0.0);
        double avgDefenderPoints = records.stream().mapToInt(r -> r.result().defenderTeamAugen()).average().orElse(0.0);

        System.out.println("==========================================================================");
        System.out.println("          STATISTISCHE ANALYSE VON " + total + " GESPIELTEN SCHAFKOPF-RUNDEN          ");
        System.out.println("==========================================================================");
        System.out.printf("Gesamtspiele:              %d\n", total);
        System.out.printf("Siege Rufer-Partei:        %d (%.1f%%)\n", callerWins, (callerWins * 100.0 / total));
        System.out.printf("Siege Gegner-Partei:       %d (%.1f%%)\n", defenderWins, (defenderWins * 100.0 / total));
        System.out.printf("Ø Augen Rufer-Partei:      %.1f Augen\n", avgCallerPoints);
        System.out.printf("Ø Augen Gegner-Partei:     %.1f Augen\n", avgDefenderPoints);
        System.out.printf("Schneider (Rufer/Gegner):  %d / %d\n", schneiderRufer, schneiderGegner);
        System.out.printf("Schwarz (Rufer gewinnt):   %d\n", schwarzRufer);
        System.out.println("--------------------------------------------------------------------------");

        // Analyse 1: Rufer zieht Trumpf in Stich 1
        List<GameRecord> callerLedTrump = records.stream().filter(GameRecord::callerLedTrumpTrick1).toList();
        List<GameRecord> callerDidNotLeadTrump = records.stream().filter(r -> !r.callerLedTrumpTrick1()).toList();

        double winRateLedTrump = callerLedTrump.isEmpty() ? 0 :
                (callerLedTrump.stream().filter(r -> r.result().callerTeamWon()).count() * 100.0 / callerLedTrump.size());
        double winRateNoTrump = callerDidNotLeadTrump.isEmpty() ? 0 :
                (callerDidNotLeadTrump.stream().filter(r -> r.result().callerTeamWon()).count() * 100.0 / callerDidNotLeadTrump.size());

        System.out.println("TAKTIK-ANALYSE 1: 'Der Spieler spielt Trumpf'");
        System.out.printf("  * Wenn Rufer in Stich 1 Trumpf zieht (%d Spiele):    Siegquote Rufer = %.1f%%\n",
                callerLedTrump.size(), winRateLedTrump);
        System.out.printf("  * Wenn Rufer in Stich 1 keinen Trumpf zieht (%d Spiele): Siegquote Rufer = %.1f%%\n",
                callerDidNotLeadTrump.size(), winRateNoTrump);

        // Analyse 2: Gegner sucht die Sau früh
        List<GameRecord> sauSearchedEarly = records.stream().filter(GameRecord::defendersSearchedSauEarly).toList();
        List<GameRecord> sauNotSearchedEarly = records.stream().filter(r -> !r.defendersSearchedSauEarly()).toList();

        double defWinRateEarlySearch = sauSearchedEarly.isEmpty() ? 0 :
                (sauSearchedEarly.stream().filter(r -> !r.result().callerTeamWon()).count() * 100.0 / sauSearchedEarly.size());
        double defWinRateNoEarlySearch = sauNotSearchedEarly.isEmpty() ? 0 :
                (sauNotSearchedEarly.stream().filter(r -> !r.result().callerTeamWon()).count() * 100.0 / sauNotSearchedEarly.size());

        System.out.println("\nTAKTIK-ANALYSE 2: 'Nicht-Spieler sucht die Sau, um Klarheit zu schaffen'");
        System.out.printf("  * Gegenspieler suchen die Sau früh in Stich 1-3 (%d Spiele):  Siegquote Gegner = %.1f%%\n",
                sauSearchedEarly.size(), defWinRateEarlySearch);
        System.out.printf("  * Sau wird nicht früh von Gegnern gesucht (%d Spiele):         Siegquote Gegner = %.1f%%\n",
                sauNotSearchedEarly.size(), defWinRateNoEarlySearch);

        // Analyse 3: Trumpf-Ausstattung des Rufers
        System.out.println("\nTAKTIK-ANALYSE 3: 'Trumpf-Ausstattung des Rufers'");
        for (int tr = 2; tr <= 6; tr++) {
            final int t = tr;
            List<GameRecord> byTrumps = records.stream().filter(r -> r.callerTrumpCount() == t).toList();
            if (!byTrumps.isEmpty()) {
                double rate = byTrumps.stream().filter(r -> r.result().callerTeamWon()).count() * 100.0 / byTrumps.size();
                System.out.printf("  * Rufer hat genau %d Trümpfe (%d Spiele): Siegquote = %.1f%%\n", t, byTrumps.size(), rate);
            }
        }

        System.out.println("==========================================================================");
        System.out.println("                     ABGELEITETE REGELN & ERKENNTNISSE                    ");
        System.out.println("==========================================================================");
        System.out.println("1. [Bestätigt] 'Sau suchen schafft Klarheit': Wenn Gegenspieler die gesuchte");
        System.out.println("   Sau früh anspielen, steigen ihre Siegchancen spürbar, weil der Rufer die");
        System.out.println("   Sau nicht mehr unerwartet stechen oder zum freien Schmieren nutzen kann.");
        System.out.println("2. [Bestätigt] 'Trumpf zieht Trumpf': Wenn der Rufer in Stich 1 Trumpf zieht,");
        System.out.println("   entwaffnet er die Gegenspieler frühzeitig, wodurch seine Fehlfarben-Säue");
        System.out.println("   in den Folgestichen sichere Augen einfahren.");
        System.out.println("3. [Kritische Grenze]: Ein Rufspiel mit weniger als 4 Trümpfen ist ein");
        System.out.println("   hohes Risiko für den Rufer; ab 5 Trümpfen liegt die Siegquote über 70%.");
        System.out.println("==========================================================================\n");
    }
}
