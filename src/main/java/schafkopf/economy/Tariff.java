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

    public PaymentBreakdown calculatePayment(SchafkopfGame game) {
        GameResult res = game.result();
        boolean callerWon = res.callerTeamWon();
        boolean ramsch = res.contract().gameType() == GameType.RAMSCH;
        boolean durchmarsch = ramsch && res.callerTeam().size() == 1
            && res.callerTeamAugen() > 90;

        int baseGameAmount = durchmarsch ? soloTariffCents : gameBaseAmount(res.contract().gameType());
        int schneiderAmount = res.isSchneider() && game.schneiderPaid() ? schneiderBonusCents : 0;
        int schwarzAmount = res.isSchwarz() && game.schwarzClaimed() ? schwarzBonusCents : 0;

        // Calculate Laufende (consecutive top trumps held by winning or losing team)
        int laufende = countLaufende(game);
        int minimumLaufende = minimumLaufende(res.contract().gameType());
        int laufendeUnit = halfLaufende ? laufendeBonusCents / 2 : laufendeBonusCents;
        int laufendeAmount = laufende >= minimumLaufende ? laufende * laufendeUnit : 0;

        int subtotal = baseGameAmount + schneiderAmount + schwarzAmount + laufendeAmount;
        int jungfrauCount = ramsch && !durchmarsch
            ? (int) game.players().stream().filter(player -> player.wonTricks().isEmpty()).count()
            : 0;
        int jungfrauMultiplier = 1 << jungfrauCount;
        int totalPerPlayer = Math.multiplyExact(
            Math.multiplyExact(subtotal, game.bettingState().multiplier()), jungfrauMultiplier);

        String desc;
        if (durchmarsch) {
            desc = String.format("Durchmarsch: Solo-Tarif %d ct x %d = %d ct pro Spieler",
                baseGameAmount, game.bettingState().multiplier(), totalPerPlayer);
        } else if (jungfrauCount > 0) {
            desc = String.format("%s: %d Jungfrau(en), Tarif x %d = %d ct pro Spieler",
                res.contract().displayName(), jungfrauCount,
                game.bettingState().multiplier() * jungfrauMultiplier, totalPerPlayer);
        } else {
            desc = String.format("%s: %d ct%s%s%s x %d = %d ct pro Spieler",
                res.contract().displayName(), baseGameAmount,
                (schneiderAmount > 0 ? " + Schneider (" + schneiderAmount + " ct)" : ""),
                (schwarzAmount > 0 ? " + Schwarz (" + schwarzAmount + " ct)" : ""),
                (laufendeAmount > 0 ? " + " + laufende + " Laufende (" + laufendeAmount + " ct)" : ""),
                game.bettingState().multiplier(), totalPerPlayer);
        }

        return new PaymentBreakdown(
                baseGameAmount,
                schneiderAmount,
                schwarzAmount,
                laufende,
                laufendeAmount,
                jungfrauCount,
                jungfrauMultiplier,
                durchmarsch,
                totalPerPlayer,
                callerWon,
                desc
        );
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
        return gameType == GameType.WENZ || gameType == GameType.GEIER || gameType == GameType.FARBWENZ
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

