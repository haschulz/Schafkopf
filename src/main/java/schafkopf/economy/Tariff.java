package schafkopf.economy;

import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.GeierContract;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.model.SoloContract;
import schafkopf.model.WenzContract;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.List;

/**
 * Calculates per-loser stake amounts from the configured base, Rufspiel, and solo tariffs.
 */
public class Tariff {
    private final int rufspielTariffCents;
    private final int soloTariffCents;
    private final int schneiderBonusCents;
    private final int schwarzBonusCents;
    private final int laufendeBonusCents;
    private final boolean halfLaufende;

    public Tariff() {
        this(SchafkopfConfig.defaults());
    }

    public Tariff(SchafkopfConfig config) {
        this(config.rufspielTariffCents(), config.soloTariffCents(),
                config.baseTariffCents(), config.baseTariffCents(), config.baseTariffCents(),
                config.halfLaufende());
    }

    public Tariff(int baseCents, int schneiderCents, int schwarzCents, int laufendeCents) {
        this(baseCents, baseCents, schneiderCents, schwarzCents, laufendeCents, false);
    }

    private Tariff(
            int rufspielCents,
            int soloCents,
            int schneiderCents,
            int schwarzCents,
            int laufendeCents,
            boolean halfLaufende
    ) {
        this.rufspielTariffCents = rufspielCents;
        this.soloTariffCents = soloCents;
        this.schneiderBonusCents = schneiderCents;
        this.schwarzBonusCents = schwarzCents;
        this.laufendeBonusCents = laufendeCents;
        this.halfLaufende = halfLaufende;
    }

    public record PaymentBreakdown(
            int baseCents,
            int schneiderCents,
            int schwarzCents,
            int laufendeCount,
            int laufendeCents,
            int jungfrauCount,
            int jungfrauMultiplier,
            boolean durchmarsch,
            int totalCentsPerPlayer,
            boolean callerWon,
            String description
    ) {}

    private record PaymentAmounts(
            int baseCents,
            int schneiderCents,
            int schwarzCents,
            int laufendeCount,
            int laufendeCents,
            int jungfrauCount,
            int jungfrauMultiplier,
            boolean durchmarsch,
            int totalCentsPerPlayer
    ) {}

    public PaymentBreakdown calculatePayment(SchafkopfGame game) {
        GameResult res = game.result();
        PaymentAmounts amounts = calculateAmounts(game, res);
        return new PaymentBreakdown(
                amounts.baseCents(), amounts.schneiderCents(), amounts.schwarzCents(),
                amounts.laufendeCount(), amounts.laufendeCents(), amounts.jungfrauCount(),
                amounts.jungfrauMultiplier(), amounts.durchmarsch(), amounts.totalCentsPerPlayer(),
                res.callerTeamWon(), describePayment(game, amounts)
        );
    }

    private PaymentAmounts calculateAmounts(SchafkopfGame game, GameResult result) {
        boolean tout = result.contract().isTout();
        boolean durchmarsch = isDurchmarsch(result);
        int baseCents = baseCents(result.contract().gameType(), durchmarsch, tout);
        int schneiderCents = tout || !result.isSchneider() || !game.schneiderPaid() ? 0 : schneiderBonusCents;
        int schwarzCents = tout || !result.isSchwarz() || !game.schwarzClaimed() ? 0 : schwarzBonusCents;
        int laufende = countLaufende(game);
        int laufendeCents = laufendeCents(result, laufende, tout);
        int jungfrauCount = jungfrauCount(game, result, durchmarsch);
        int jungfrauMultiplier = 1 << jungfrauCount;
        int subtotal = baseCents + schneiderCents + schwarzCents + laufendeCents;
        int total = Math.multiplyExact(
                Math.multiplyExact(subtotal, game.bettingState().multiplier()), jungfrauMultiplier);
        return new PaymentAmounts(baseCents, schneiderCents, schwarzCents, laufende, laufendeCents,
                jungfrauCount, jungfrauMultiplier, durchmarsch, total);
    }

    private boolean isDurchmarsch(GameResult result) {
        return result.contract().gameType() == GameType.RAMSCH
                && result.callerTeam().size() == 1 && result.callerTeamAugen() > 90;
    }

    private int baseCents(GameType gameType, boolean durchmarsch, boolean tout) {
        int base = durchmarsch ? soloTariffCents : gameBaseAmount(gameType);
        return Math.multiplyExact(base, tout ? 2 : 1);
    }

    private int laufendeCents(GameResult result, int laufende, boolean tout) {
        if (laufende < minimumLaufende(result.contract().gameType())) {
            return 0;
        }
        int unit = halfLaufende ? laufendeBonusCents / 2 : laufendeBonusCents;
        return Math.multiplyExact(Math.multiplyExact(laufende, unit), tout ? 2 : 1);
    }

    private int jungfrauCount(SchafkopfGame game, GameResult result, boolean durchmarsch) {
        if (result.contract().gameType() != GameType.RAMSCH || durchmarsch) {
            return 0;
        }
        return (int) game.players().stream().filter(player -> player.wonTricks().isEmpty()).count();
    }

    private String describePayment(SchafkopfGame game, PaymentAmounts amounts) {
        if (amounts.durchmarsch()) {
            return String.format("Durchmarsch: Solo-Tarif %d ct x %d = %d ct pro Spieler",
                    amounts.baseCents(), game.bettingState().multiplier(), amounts.totalCentsPerPlayer());
        }
        if (amounts.jungfrauCount() > 0) {
            return String.format("%s: %d Jungfrau(en), Tarif x %d = %d ct pro Spieler",
                    game.gameContract().displayName(), amounts.jungfrauCount(),
                    game.bettingState().multiplier() * amounts.jungfrauMultiplier(),
                    amounts.totalCentsPerPlayer());
        }
        return String.format("%s: %d ct%s%s%s x %d = %d ct pro Spieler",
                game.gameContract().displayName(), amounts.baseCents(),
                amounts.schneiderCents() > 0 ? " + Schneider (" + amounts.schneiderCents() + " ct)" : "",
                amounts.schwarzCents() > 0 ? " + Schwarz (" + amounts.schwarzCents() + " ct)" : "",
                amounts.laufendeCents() > 0
                        ? " + " + amounts.laufendeCount() + " Laufende (" + amounts.laufendeCents() + " ct)" : "",
                game.bettingState().multiplier(), amounts.totalCentsPerPlayer());
    }

    /**
     * Counts uninterrupted consecutive top trumps (Eichel-Ober, Gras-Ober, Herz-Ober, Schellen-Ober...)
     * either with ("mit X") or against ("ohne X") the winning team.
     */
    private int countLaufende(SchafkopfGame game) {
        if (game.gameContract().gameType() == GameType.BETTEL || game.gameContract().gameType() == GameType.RAMSCH) {
            return 0;
        }
        List<Card> topTrumpsOrdered = topTrumps(game.gameContract());
        List<Player> winningPlayers = game.result().callerTeamWon()
                ? game.result().callerTeam()
                : game.result().defenderTeam();
        Player firstHolder = findCardHolderInWonTricks(game, topTrumpsOrdered.get(0));
        if (firstHolder == null) {
            return 0;
        }
        boolean winnerHoldsFirst = winningPlayers.contains(firstHolder);
        int count = 0;
        for (Card trump : topTrumpsOrdered) {
            Player holder = findCardHolderInWonTricks(game, trump);
            if (holder == null) {
                return count;
            }

            boolean winnerHolds = winningPlayers.contains(holder);
            if (winnerHolds != winnerHoldsFirst) {
                return count;
            }
            count++;
        }

        return count;
    }

    private int gameBaseAmount(GameType gameType) {
        return gameType == GameType.RUFSPIEL || gameType == GameType.KREUZBOCK || gameType == GameType.RAMSCH
                ? rufspielTariffCents
                : soloTariffCents;
    }

    private int minimumLaufende(GameType gameType) {
        return gameType == GameType.WENZ || gameType == GameType.GEIER
            ? 2 : 3;
    }

    private List<Card> topTrumps(GameContract contract) {
        List<Card> cards = new java.util.ArrayList<>();
        if (contract instanceof WenzContract wenz) {
            for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.HERZ, Suit.SCHELLEN)) {
                cards.add(new Card(suit, Rank.UNTER));
            }
            if (wenz.trumpSuit() != null) {
                addSuitTrumps(cards, wenz.trumpSuit());
            }
            return cards;
        }
        if (contract instanceof GeierContract) {
            for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.HERZ, Suit.SCHELLEN)) {
                cards.add(new Card(suit, Rank.OBER));
            }
            return cards;
        }
        for (Rank rank : List.of(Rank.OBER, Rank.UNTER)) {
            for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.HERZ, Suit.SCHELLEN)) {
                cards.add(new Card(suit, rank));
            }
        }
        if (contract instanceof SoloContract solo) {
            addSuitTrumps(cards, solo.trumpSuit());
        } else {
            for (Rank rank : List.of(Rank.SAU, Rank.ZEHN, Rank.KOENIG, Rank.NEUN, Rank.ACHT, Rank.SIEBEN)) {
                cards.add(new Card(Suit.HERZ, rank));
            }
        }
        return cards;
    }

    private void addSuitTrumps(List<Card> cards, Suit suit) {
        for (Rank rank : List.of(Rank.SAU, Rank.ZEHN, Rank.KOENIG, Rank.NEUN, Rank.ACHT, Rank.SIEBEN)) {
            cards.add(new Card(suit, rank));
        }
    }

    private Player findCardHolderInWonTricks(SchafkopfGame game, Card card) {
        for (var trick : game.completedTricks()) {
            for (var pc : trick.playedCards()) {
                if (pc.card().equals(card)) {
                    return pc.player();
                }
            }
        }
        return null;
    }
}

