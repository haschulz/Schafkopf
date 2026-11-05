package schafkopf.rules;

import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.Predicate;

public class RuleEngine {

    /**
     * In Rufspiel (Sauspiel):
     * - All 4 Obers are trumps (Eichel, Gras, Herz, Schellen)
     * - All 4 Unters are trumps (Eichel, Gras, Herz, Schellen)
     * - All Herz cards are trumps (Sau, 10, König, 9, 8, 7)
     * Total = 14 trumps.
     */
    public boolean isTrump(Card card) {
        if (card.rank() == Rank.OBER || card.rank() == Rank.UNTER) {
            return true;
        }
        return card.suit() == Suit.HERZ;
    }

    /**
     * Strength of a trump card (1 to 14, higher is stronger).
     */
    public int getTrumpStrength(Card card) {
        if (!isTrump(card)) {
            return -1;
        }
        if (card.rank() == Rank.OBER) {
            return switch (card.suit()) {
                case EICHEL -> 14;
                case GRAS -> 13;
                case HERZ -> 12;
                case SCHELLEN -> 11;
            };
        }
        if (card.rank() == Rank.UNTER) {
            return switch (card.suit()) {
                case EICHEL -> 10;
                case GRAS -> 9;
                case HERZ -> 8;
                case SCHELLEN -> 7;
            };
        }
        // Herz suit trumps (excluding Ober and Unter)
        return switch (card.rank()) {
            case SAU -> 6;
            case ZEHN -> 5;
            case KOENIG -> 4;
            case NEUN -> 3;
            case ACHT -> 2;
            case SIEBEN -> 1;
            default -> 0;
        };
    }

    /**
     * Strength within a non-trump suit (1 to 6, higher is stronger).
     */
    public int getSuitStrength(Card card) {
        if (isTrump(card)) {
            return -1;
        }
        return switch (card.rank()) {
            case SAU -> 6;
            case ZEHN -> 5;
            case KOENIG -> 4;
            case NEUN -> 3;
            case ACHT -> 2;
            case SIEBEN -> 1;
            default -> 0;
        };
    }

    /**
     * Determines whether card belongs to the given non-trump suit.
     * Note: Ober and Unter belong to TRUMP, NOT the suit!
     */
    public boolean isNonTrumpSuit(Card card, Suit suit) {
        return !isTrump(card) && card.suit() == suit;
    }

    /**
     * Returns all legal cards the player is allowed to play in the current state of the trick.
     */
    public List<Card> getLegalMoves(
            Player player,
            Trick trick,
            RufspielContract contract,
            boolean calledSauPlayedBefore,
            int currentTrickNumber
    ) {
        List<Card> hand = player.hand();
        if (hand.isEmpty()) {
            return Collections.emptyList();
        }

        boolean isPartnerWithCalledSau = hand.contains(contract.calledSau());

        // 1. Ausspiel (First card of the trick)
        if (trick.playedCards().isEmpty()) {
            List<Card> legalCards = new ArrayList<>();
            for (Card c : hand) {
                // Rule check for caller: Caller may not lead the called suit before the Sau was played,
                // UNLESS caller has >= 3 cards of the called suit or only cards of that suit left.
                if (player.equals(contract.caller()) && !calledSauPlayedBefore && isNonTrumpSuit(c, contract.calledSuit())) {
                    long suitCount = hand.stream().filter(card -> isNonTrumpSuit(card, contract.calledSuit())).count();
                    boolean onlySuitLeft = hand.stream().allMatch(card -> isNonTrumpSuit(card, contract.calledSuit()));
                    if (suitCount < 3 && !onlySuitLeft) {
                        continue; // Cannot lead called suit yet!
                    }
                }
                legalCards.add(c);
            }
            return legalCards.isEmpty() ? new ArrayList<>(hand) : legalCards;
        }

        // 2. Zugeben (Following player in the trick)
        Card leadCard = trick.leadCard();
        boolean leadIsTrump = isTrump(leadCard);

        if (leadIsTrump) {
            // Trump led: Must play trump if player has one
            List<Card> trumpsInHand = hand.stream().filter(this::isTrump).toList();
            if (!trumpsInHand.isEmpty()) {
                return trumpsInHand;
            }
            // Trumpffrei: Can play any card EXCEPT the called Sau cannot be discarded
            return filterDiscardRestrictions(hand, isPartnerWithCalledSau, contract.calledSau(), currentTrickNumber);
        } else {
            // Non-trump suit led
            Suit ledSuit = leadCard.suit();
            List<Card> suitCardsInHand = hand.stream().filter(c -> isNonTrumpSuit(c, ledSuit)).toList();

            if (!suitCardsInHand.isEmpty()) {
                // Must follow suit!
                // Special rule: If led suit is the CALLED suit and player holds the called Sau -> MUST play the called Sau!
                if (ledSuit == contract.calledSuit() && isPartnerWithCalledSau) {
                    return List.of(contract.calledSau());
                }
                return suitCardsInHand;
            } else {
                // Farbfrei: Can play any card (trump or discard) EXCEPT cannot discard called Sau
                return filterDiscardRestrictions(hand, isPartnerWithCalledSau, contract.calledSau(), currentTrickNumber);
            }
        }
    }

    private List<Card> filterDiscardRestrictions(
            List<Card> hand,
            boolean isPartnerWithCalledSau,
            Card calledSau,
            int currentTrickNumber
    ) {
        // "Die gerufene Sau darf nicht abgeworfen werden!"
        // In trick 8 (hand size 1), the player must play it if it's the last card.
        if (isPartnerWithCalledSau && currentTrickNumber < 8 && hand.size() > 1) {
            List<Card> allowed = new ArrayList<>(hand);
            allowed.remove(calledSau);
            return allowed;
        }
        return new ArrayList<>(hand);
    }

    /**
     * Determines the winning card and player of a trick.
     */
    public PlayedCard evaluateTrickWinner(Trick trick) {
        return evaluateTrickWinner(trick, this::isTrump, this::getTrumpStrength, this::getSuitStrength);
    }

    public PlayedCard evaluateTrickWinner(Trick trick, GameContract contract) {
        return evaluateTrickWinner(
                trick,
                card -> contract.isTrump(card, this),
                contract::getTrumpStrength,
                contract::getSuitStrength);
    }

    private PlayedCard evaluateTrickWinner(
            Trick trick,
            Predicate<Card> isTrump,
            ToIntFunction<Card> trumpStrength,
            ToIntFunction<Card> suitStrength
    ) {
        List<PlayedCard> playedCards = trick.playedCards();
        if (playedCards.isEmpty()) {
            return null;
        }

        Card leadCard = playedCards.get(0).card();
        PlayedCard bestCard = playedCards.get(0);
        boolean anyTrump = isTrump.test(bestCard.card());

        for (int i = 1; i < playedCards.size(); i++) {
            PlayedCard current = playedCards.get(i);
            Card card = current.card();

            if (isTrump.test(card)) {
                if (!anyTrump) {
                    // First trump beats non-trump
                    bestCard = current;
                    anyTrump = true;
                } else {
                    // Higher trump beats lower trump
                    if (trumpStrength.applyAsInt(card) > trumpStrength.applyAsInt(bestCard.card())) {
                        bestCard = current;
                    }
                }
            } else if (!anyTrump && card.suit() == leadCard.suit()) {
                // Non-trump only competes if no trump played and same suit as lead
                if (suitStrength.applyAsInt(card) > suitStrength.applyAsInt(bestCard.card())) {
                    bestCard = current;
                }
            }
        }

        trick.setWinningPlayedCard(bestCard);
        return bestCard;
    }

    // =========================================================================
    // WENZ SUPPORT
    // =========================================================================

    /**
     * Returns true if the given card is a Wenz trump (i.e. rank == UNTER).
     * In a plain Wenz only Unter cards are trump.
     */
    public boolean isWenzTrump(Card card) {
        return card.rank() == Rank.UNTER;
    }

    /**
     * Trump strength for Wenz:
     *   Eichel-Unter = 4, Gras-Unter = 3, Herz-Unter = 2, Schellen-Unter = 1.
     * Returns -1 if the card is not a Wenz trump.
     */
    public int getWenzTrumpStrength(Card card) {
        if (!isWenzTrump(card)) {
            return -1;
        }
        return switch (card.suit()) {
            case EICHEL   -> 4;
            case GRAS     -> 3;
            case HERZ     -> 2;
            case SCHELLEN -> 1;
        };
    }

    // =========================================================================
    // FARBSOLO SUPPORT
    // =========================================================================

    /**
     * Returns true if the given card is trump in a Farbsolo with the specified trumpSuit.
     * Trump = all Ober, all Unter, or any card of trumpSuit.
     */
    public boolean isSoloTrump(Card card, Suit trumpSuit) {
        if (card.rank() == Rank.OBER || card.rank() == Rank.UNTER) {
            return true;
        }
        return card.suit() == trumpSuit;
    }

    /**
     * Trump strength for Farbsolo:
     *   Eichel-Ober = 14, Gras-Ober = 13, Herz-Ober = 12, Schellen-Ober = 11,
     *   Eichel-Unter = 10, Gras-Unter = 9, Herz-Unter = 8, Schellen-Unter = 7,
     *   trumpSuit-Sau = 6, trumpSuit-Zehn = 5, trumpSuit-König = 4,
     *   trumpSuit-Neun = 3, trumpSuit-Acht = 2, trumpSuit-Sieben = 1.
     * Returns -1 if not trump.
     */
    public int getSoloTrumpStrength(Card card, Suit trumpSuit) {
        if (!isSoloTrump(card, trumpSuit)) {
            return -1;
        }
        if (card.rank() == Rank.OBER) {
            return switch (card.suit()) {
                case EICHEL   -> 14;
                case GRAS     -> 13;
                case HERZ     -> 12;
                case SCHELLEN -> 11;
            };
        }
        if (card.rank() == Rank.UNTER) {
            return switch (card.suit()) {
                case EICHEL   -> 10;
                case GRAS     -> 9;
                case HERZ     -> 8;
                case SCHELLEN -> 7;
            };
        }
        // trumpSuit non-Ober/non-Unter
        return switch (card.rank()) {
            case SAU    -> 6;
            case ZEHN   -> 5;
            case KOENIG -> 4;
            case NEUN   -> 3;
            case ACHT   -> 2;
            case SIEBEN -> 1;
            default     -> 0;
        };
    }

    // =========================================================================
    // GENERALIZED LEGAL MOVES
    // =========================================================================

    /**
     * Generalized version of getLegalMoves that works with any GameContract implementation.
     * For Rufspiel contracts, delegates to the specific Rufspiel logic.
     * For Wenz/Solo contracts, applies generic trump/suit follow rules (no Sau-search rules).
     *
     * @param player               the player whose legal cards to compute
     * @param trick                the current (possibly empty) trick
     * @param contract             the active game contract
     * @param calledSauPlayed      whether the called Sau has been played (Rufspiel only)
     * @param trickNumber          the current trick number (1-8)
     * @return list of cards the player may legally play
     */
    public List<Card> getLegalMovesForContract(
            Player player,
            Trick trick,
            GameContract contract,
            boolean calledSauPlayed,
            int trickNumber
    ) {
        // Delegate to type-specific method where possible
        if (contract instanceof schafkopf.model.RufspielContract rc) {
            return getLegalMoves(player, trick, rc, calledSauPlayed, trickNumber);
        }

        // Generic handling for Wenz, Farbwenz, Farbsolo, Geier
        List<Card> hand = player.hand();
        if (hand.isEmpty()) {
            return Collections.emptyList();
        }

        // Leading (Ausspiel) – free choice
        if (trick.playedCards().isEmpty()) {
            return new ArrayList<>(hand);
        }

        Card leadCard = trick.leadCard();
        boolean leadIsTrump = contract.isTrump(leadCard, this);

        if (leadIsTrump) {
            List<Card> trumpsInHand = hand.stream()
                    .filter(c -> contract.isTrump(c, this))
                    .toList();
            if (!trumpsInHand.isEmpty()) {
                return new ArrayList<>(trumpsInHand);
            }
            return new ArrayList<>(hand); // Trumpffrei: free choice
        } else {
            Suit ledSuit = leadCard.suit();
            List<Card> suitCardsInHand = hand.stream()
                    .filter(c -> !contract.isTrump(c, this) && c.suit() == ledSuit)
                    .toList();
            if (!suitCardsInHand.isEmpty()) {
                return new ArrayList<>(suitCardsInHand);
            }
            return new ArrayList<>(hand); // Farbfrei: free choice
        }
    }

    /**
     * Generates a descriptive explanation of the rule requirement for the player's turn.
     */
    public String explainRuleRequirement(
            Player player,
            Trick trick,
            RufspielContract contract,
            boolean calledSauPlayedBefore,
            int currentTrickNumber
    ) {
        return explainRuleRequirement(player, trick, (GameContract) contract);
    }

    public String explainRuleRequirement(Player player, Trick trick, GameContract contract) {
        List<Card> hand = player.hand();
        if (trick.playedCards().isEmpty()) {
            return "hat das Ausspiel (freie Wahl der Eröffnungskarte)";
        }

        Card leadCard = trick.leadCard();
        boolean leadIsTrump = contract.isTrump(leadCard, this);

        if (leadIsTrump) {
            long trumps = hand.stream().filter(card -> contract.isTrump(card, this)).count();
            if (trumps > 0) {
                return "muss Trumpf zugeben (Regel: Bedienpflicht Trumpf, hat " + trumps + " Trümpfe)";
            } else {
                return "hat keinen Trumpf (trumpffrei) -> freie Wahl";
            }
        } else {
            Suit ledSuit = leadCard.suit();
            long count = hand.stream()
                    .filter(card -> !contract.isTrump(card, this) && card.suit() == ledSuit)
                    .count();

            if (count > 0) {
                return "muss zugeben (Regel: Bedienpflicht Farbe " + ledSuit.displayName() + ")";
            } else {
                return "hat kein " + ledSuit.displayName() + " (farbfrei) -> freie Wahl (Regel: stechen oder abwerfen)";
            }
        }
    }
}
