package schafkopf.ai;

import schafkopf.model.Card;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.SuitCategory;
import schafkopf.model.Trick;
import schafkopf.rules.RuleEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the complete table history, card accounting, and inferred player states (Belief State).
 * "Führt Buch über jeden Stich":
 * 1. Welche Karten sind bereits gefallen? Welcher ist der aktuell höchste Trumpf?
 * 2. Wer hat welche Farbe nicht bedient und ist somit DEFINITIV farbfrei?
 * 3. Wer sticht voraussichtlich ab?
 * 4. Live-Augen pro Spieler und Team zur Gewinnschwellen-Prüfung.
 */
public class GameMemory {
    private final RuleEngine rules;
    private final List<Player> players;
    private final Set<Card> playedCards = new HashSet<>();
    private final Map<String, Set<SuitCategory>> definiteVoids = new HashMap<>();
    private final Map<String, Integer> playerPoints = new HashMap<>();
    private final List<String> observationLogs = new ArrayList<>();

    private boolean calledSauPlayed = false;
    private Player confirmedPartner = null;

    // Standard trump hierarchy from highest to lowest (14 trumps)
    private static final List<Card> ALL_TRUMPS_ORDERED = List.of(
            new Card(Suit.EICHEL, Rank.OBER),
            new Card(Suit.GRAS, Rank.OBER),
            new Card(Suit.HERZ, Rank.OBER),
            new Card(Suit.SCHELLEN, Rank.OBER),
            new Card(Suit.EICHEL, Rank.UNTER),
            new Card(Suit.GRAS, Rank.UNTER),
            new Card(Suit.HERZ, Rank.UNTER),
            new Card(Suit.SCHELLEN, Rank.UNTER),
            new Card(Suit.HERZ, Rank.SAU),
            new Card(Suit.HERZ, Rank.ZEHN),
            new Card(Suit.HERZ, Rank.KOENIG),
            new Card(Suit.HERZ, Rank.NEUN),
            new Card(Suit.HERZ, Rank.ACHT),
            new Card(Suit.HERZ, Rank.SIEBEN)
    );

    public GameMemory(List<Player> players, RuleEngine rules) {
        this.players = players;
        this.rules = rules;
        for (Player p : players) {
            definiteVoids.put(p.id(), EnumSet.noneOf(SuitCategory.class));
            playerPoints.put(p.id(), 0);
        }
    }

    /**
     * Records a card played into an active trick.
     * Evaluates Bedienpflicht violations to deduce 100% definite voids (Farbfreiheit).
     */
    public void recordCardPlayed(Player player, Card card, Trick trick, RufspielContract contract) {
        playedCards.add(card);

        if (card.equals(contract.calledSau())) {
            calledSauPlayed = true;
            confirmedPartner = player;
            observationLogs.add(String.format("Aufdeckung: %s spielt die gesuchte %s-Sau und ist damit der Mitspieler!",
                    player.name(), contract.calledSuit().displayName()));
        }

        // If this is not the lead card, check if player followed the led suit category
        if (!trick.playedCards().isEmpty()) {
            Card leadCard = trick.leadCard();
            SuitCategory leadCat = SuitCategory.fromCard(leadCard, rules);
            SuitCategory playedCat = SuitCategory.fromCard(card, rules);

            if (leadCat != playedCat) {
                // Player was unable to follow -> DEFINITIV VOID in leadCat!
                Set<SuitCategory> playerVoids = definiteVoids.get(player.id());
                if (!playerVoids.contains(leadCat)) {
                    playerVoids.add(leadCat);
                    String logMsg = String.format("Beobachtung: %s bedient %s nicht -> ist definitiv %s-frei!",
                            player.name(), leadCat.displayName(), leadCat.displayName());
                    observationLogs.add(logMsg);
                }
            }
        }
    }

    /**
     * Records trick completion and updates accumulated points.
     */
    public void recordTrickCompleted(Trick trick, Player winner) {
        int current = playerPoints.getOrDefault(winner.id(), 0);
        playerPoints.put(winner.id(), current + trick.totalPoints());
    }

    /**
     * Checks whether a player is 100% definitely void in a suit category (Trumpf, Eichel, Gras, Schellen).
     */
    public boolean isDefiniteVoid(Player player, SuitCategory category) {
        Set<SuitCategory> voids = definiteVoids.get(player.id());
        return voids != null && voids.contains(category);
    }

    public boolean isCardPlayed(Card card) {
        return playedCards.contains(card);
    }

    public Set<Card> allPlayedCards() {
        return Collections.unmodifiableSet(playedCards);
    }

    /**
     * Determines which players could possibly hold a specific unplayed card,
     * using definite void elimination ("Ausschlussverfahren").
     * If only 1 player remains in the list, that player 100% DEFINITIVELY holds the card!
     */
    public List<Player> getPossibleHolders(Card card, Player observer) {
        if (playedCards.contains(card)) {
            return Collections.emptyList();
        }
        SuitCategory cat = SuitCategory.fromCard(card, rules);
        List<Player> candidates = new ArrayList<>();
        for (Player p : players) {
            if (p.equals(observer)) continue;
            if (!isDefiniteVoid(p, cat)) {
                candidates.add(p);
            }
        }
        return candidates;
    }

    /**
     * Returns the highest trump card in the entire game that has not yet been played.
     */
    public Card getHighestRemainingTrump() {
        for (Card trump : ALL_TRUMPS_ORDERED) {
            if (!playedCards.contains(trump)) {
                return trump;
            }
        }
        return null;
    }

    /**
     * Checks if a given card is currently the undisputed highest trump in the game.
     */
    public boolean isHighestRemainingTrump(Card card) {
        Card highest = getHighestRemainingTrump();
        return highest != null && highest.equals(card);
    }

    /**
     * Returns the highest unplayed card of a Fehlfarbe (Sau > 10 > König > 9 > 8 > 7).
     */
    public Card getHighestRemainingCardOfSuit(Suit suit) {
        List<Rank> ranks = List.of(Rank.SAU, Rank.ZEHN, Rank.KOENIG, Rank.NEUN, Rank.ACHT, Rank.SIEBEN);
        for (Rank r : ranks) {
            Card c = new Card(suit, r);
            if (!playedCards.contains(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Count of trumps still unplayed.
     */
    public int remainingTrumpsCount() {
        int count = 0;
        for (Card t : ALL_TRUMPS_ORDERED) {
            if (!playedCards.contains(t)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns total points accumulated by a player.
     */
    public int getPlayerPoints(Player player) {
        return playerPoints.getOrDefault(player.id(), 0);
    }

    /**
     * Returns total points accumulated by player's team so far.
     */
    public int getTeamPoints(Player player, RufspielContract contract, Player partner) {
        boolean playerIsCallerTeam = player.equals(contract.caller()) || player.equals(partner);
        int sum = 0;
        for (Player p : players) {
            boolean pIsCallerTeam = p.equals(contract.caller()) || p.equals(partner);
            if (pIsCallerTeam == playerIsCallerTeam) {
                sum += playerPoints.getOrDefault(p.id(), 0);
            }
        }
        return sum;
    }

    /**
     * Checks if any unplayed trump (still in any player's hand) is stronger than the given trump.
     */
    public boolean canBeBeatenByUnplayedTrump(Card trumpCard) {
        int strength = rules.getTrumpStrength(trumpCard);
        for (Card trump : ALL_TRUMPS_ORDERED) {
            if (!playedCards.contains(trump) && rules.getTrumpStrength(trump) > strength) {
                return true; // A higher trump is still out!
            }
        }
        return false; // Undisputed highest trump among all remaining cards in players' hands!
    }

    /**
     * Checks if any unplayed non-trump card of the same suit is stronger than the given card.
     */
    public boolean canBeBeatenByUnplayedSuitCard(Card suitCard) {
        int strength = rules.getSuitStrength(suitCard);
        for (Rank r : List.of(Rank.SAU, Rank.ZEHN, Rank.KOENIG, Rank.NEUN, Rank.ACHT, Rank.SIEBEN)) {
            Card c = new Card(suitCard.suit(), r);
            if (!playedCards.contains(c) && rules.getSuitStrength(c) > strength) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether the teammate's current best card in the trick is GUARANTEED safe
     * against all remaining players who have yet to play in this trick.
     */
    public boolean isTrickSafeAgainstRemainingPlayers(
            Trick trick,
            PlayedCard currentBest,
            List<Player> remainingPlayers,
            RufspielContract contract,
            Player partner
    ) {
        // Filter out remaining players who are on the same team
        List<Player> remainingOpponents = remainingPlayers.stream()
                .filter(p -> !isTeammate(p, currentBest.player(), contract, partner))
                .toList();

        if (remainingOpponents.isEmpty()) {
            return true; // No opponents left to play -> Stich is 100% safe!
        }

        Card bestCard = currentBest.card();
        boolean bestIsTrump = rules.isTrump(bestCard);

        if (bestIsTrump) {
            // Teammate holds a trump card
            if (!canBeBeatenByUnplayedTrump(bestCard)) {
                // No remaining trump in any player's hand can beat teammate's trump!
                return true;
            }
            // If all remaining opponents are definitely void in Trumpf -> they cannot trump!
            boolean allOpponentsTrumpVoid = remainingOpponents.stream()
                    .allMatch(opp -> isDefiniteVoid(opp, SuitCategory.TRUMP));
            return allOpponentsTrumpVoid;
        } else {
            // Teammate holds a Fehlfarbe card
            SuitCategory suitCat = SuitCategory.fromCard(bestCard, rules);

            // 1. Can any remaining opponent stechen (ruff)?
            for (Player opp : remainingOpponents) {
                // If opponent is known to be void in this suit, and NOT known to be void in trump -> danger!
                if (isDefiniteVoid(opp, suitCat) && !isDefiniteVoid(opp, SuitCategory.TRUMP)) {
                    return false; // Opponent can and likely will stechen with trump!
                }
            }

            // 2. If no opponent can stechen, all opponents must follow suit.
            // Can any opponent beat bestCard with a higher card in the same suit?
            return !canBeBeatenByUnplayedSuitCard(bestCard);
        }
    }

    /**
     * Checks whether an opponent sitting behind us in this trick is void in the led suit
     * and therefore poses an acute danger of ruffing (stechen).
     */
    public boolean acuteRuffDangerFromRemainingOpponents(
            Trick trick,
            List<Player> remainingPlayers,
            RufspielContract contract,
            Player partner
    ) {
        if (trick.playedCards().isEmpty()) {
            return false;
        }
        Card leadCard = trick.leadCard();
        SuitCategory leadCat = SuitCategory.fromCard(leadCard, rules);

        if (leadCat == SuitCategory.TRUMP) {
            return false; // Already trump
        }

        for (Player p : remainingPlayers) {
            boolean isOpp = !isTeammate(p, contract.caller(), contract, partner);
            if (isOpp && isDefiniteVoid(p, leadCat) && !isDefiniteVoid(p, SuitCategory.TRUMP)) {
                return true; // Opponent is void in suit and has trumps!
            }
        }
        return false;
    }

    private boolean isTeammate(Player p1, Player p2, RufspielContract contract, Player partner) {
        if (p1.equals(p2)) return true;
        boolean p1CallerTeam = p1.equals(contract.caller()) || p1.equals(partner);
        boolean p2CallerTeam = p2.equals(contract.caller()) || p2.equals(partner);
        return p1CallerTeam == p2CallerTeam;
    }

    public boolean isCalledSauPlayed() {
        return calledSauPlayed;
    }

    public Player confirmedPartner() {
        return confirmedPartner;
    }

    public List<String> observationLogs() {
        return Collections.unmodifiableList(observationLogs);
    }
}
