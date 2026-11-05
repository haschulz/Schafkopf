package schafkopf.economy;

import schafkopf.model.Player;
import schafkopf.state.SchafkopfGame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreLedger {
    private final Tariff tariff;
    private final Map<String, Integer> balancesCents = new HashMap<>();
    private final Map<String, Integer> gamesPlayed = new HashMap<>();
    private final Map<String, Integer> gamesWon = new HashMap<>();
    private final Map<String, Integer> callerCount = new HashMap<>();
    private final Map<String, Integer> partnerCount = new HashMap<>();
    private final Map<String, Integer> defenderCount = new HashMap<>();
    private int pendingStockMultiplier = 1;

    public ScoreLedger(List<Player> players, Tariff tariff) {
        this.tariff = tariff;
        for (Player p : players) {
            balancesCents.put(p.id(), 0);
            gamesPlayed.put(p.id(), 0);
            gamesWon.put(p.id(), 0);
            callerCount.put(p.id(), 0);
            partnerCount.put(p.id(), 0);
            defenderCount.put(p.id(), 0);
        }
    }

    public void settleGame(SchafkopfGame game) {
        settleGame(game, tariff);
    }

    public void settleGame(SchafkopfGame game, Tariff activeTariff) {
        Tariff.PaymentBreakdown breakdown = activeTariff.calculatePayment(game);
        int amount = breakdown.totalCentsPerPlayer();

        Player caller = game.gameContract().caller();
        Player partner = game.partner();
        List<Player> callerTeam = game.result().callerTeam();

        if (!game.completedTricks().isEmpty()) {
            for (var playedCard : game.completedTricks().get(0).playedCards()) {
                Player p = playedCard.player();
                gamesPlayed.put(p.id(), gamesPlayed.getOrDefault(p.id(), 0) + 1);
            }
        }

        // Tally roles
        if (caller != null) {
            callerCount.put(caller.id(), callerCount.getOrDefault(caller.id(), 0) + 1);
        }
        if (partner != null) {
            partnerCount.put(partner.id(), partnerCount.getOrDefault(partner.id(), 0) + 1);
        }

        List<Player> winners = game.result().callerTeamWon() ? game.result().callerTeam() : game.result().defenderTeam();
        List<Player> losers = game.result().callerTeamWon() ? game.result().defenderTeam() : game.result().callerTeam();

        for (Map.Entry<String, Integer> entry : balancesCents.entrySet()) {
            String id = entry.getKey();
            boolean isCallerTeam = callerTeam.stream().anyMatch(player -> player.id().equals(id));
            if (caller != null && !isCallerTeam && (partner == null || !partner.id().equals(id))) {
                defenderCount.put(id, defenderCount.getOrDefault(id, 0) + 1);
            }

            boolean isWinner = winners.stream().anyMatch(player -> player.id().equals(id));
            if (isWinner) {
                gamesWon.put(id, gamesWon.getOrDefault(id, 0) + 1);
                entry.setValue(entry.getValue() + amount * losers.size());
            } else if (losers.stream().anyMatch(player -> player.id().equals(id))) {
                entry.setValue(entry.getValue() - amount * winners.size());
            }
        }
    }

    public void addStock(int multiplier) {
        if (multiplier < 2) {
            throw new IllegalArgumentException("Stock multiplier must be at least 2");
        }
        pendingStockMultiplier = Math.multiplyExact(pendingStockMultiplier, multiplier);
    }

    public int consumeStockMultiplier() {
        int multiplier = pendingStockMultiplier;
        pendingStockMultiplier = 1;
        return multiplier;
    }

    public int pendingStockMultiplier() {
        return pendingStockMultiplier;
    }

    public void printLeaderboard(List<Player> players) {
        System.out.println("==========================================================================");
        System.out.println("                    SCHAFKOPF-STAMMTISCH: GESAMTABRECHNUNG                ");
        System.out.println("==========================================================================");
        System.out.printf("%-26s | %-6s | %-12s | %-16s | %-12s\n",
                "Spieler", "Spiele", "Siege (Quote)", "Rollen (R / M / G)", "Kontostand");
        System.out.println("---------------------------+--------+---------------+------------------+-------------");

        for (Player p : players) {
            String id = p.id();
            int played = gamesPlayed.getOrDefault(id, 0);
            int won = gamesWon.getOrDefault(id, 0);
            double quote = played > 0 ? (won * 100.0 / played) : 0.0;
            int r = callerCount.getOrDefault(id, 0);
            int m = partnerCount.getOrDefault(id, 0);
            int g = defenderCount.getOrDefault(id, 0);
            int balance = balancesCents.getOrDefault(id, 0);

            String balanceStr = String.format("%+d ct (%+.2f €)", balance, balance / 100.0);
            System.out.printf("%-26s | %6d | %4d (%5.1f%%) | %4d / %2d / %2d  | %12s\n",
                    p.name(), played, won, quote, r, m, g, balanceStr);
        }
        System.out.println("==========================================================================\n");
    }

    public int getBalance(Player player) {
        return balancesCents.getOrDefault(player.id(), 0);
    }
}

