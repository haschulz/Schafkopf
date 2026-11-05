package schafkopf.model;

import java.util.Objects;

/**
 * Contract for Farbsolo game type.
 *
 * <p>In Farbsolo, the following 14 cards are trump:
 * <ol>
 *   <li>Eichel-Ober (strength 14)</li>
 *   <li>Gras-Ober   (strength 13)</li>
 *   <li>Herz-Ober   (strength 12)</li>
 *   <li>Schellen-Ober (strength 11)</li>
 *   <li>Eichel-Unter (strength 10)</li>
 *   <li>Gras-Unter   (strength 9)</li>
 *   <li>Herz-Unter   (strength 8)</li>
 *   <li>Schellen-Unter (strength 7)</li>
 *   <li>trumpSuit-Sau   (strength 6)</li>
 *   <li>trumpSuit-Zehn  (strength 5)</li>
 *   <li>trumpSuit-König (strength 4)</li>
 *   <li>trumpSuit-Neun  (strength 3)</li>
 *   <li>trumpSuit-Acht  (strength 2)</li>
 *   <li>trumpSuit-Sieben (strength 1)</li>
 * </ol>
 *
 * <p>The trump suit can be Eichel, Gras, Herz, or Schellen. When it is Herz, this is
 * effectively a Herzsolo (similar to the standard Rufspiel trump structure, but all
 * Herz cards are still covered).
 */
public class SoloContract implements GameContract {

    private final Player caller;
    private final Suit trumpSuit;

    /**
     * Creates a Farbsolo contract.
     *
     * @param caller    the announcing player
     * @param trumpSuit the chosen trump suit (any of the four suits)
     */
    public SoloContract(Player caller, Suit trumpSuit) {
        this.caller    = Objects.requireNonNull(caller,    "Caller must not be null");
        this.trumpSuit = Objects.requireNonNull(trumpSuit, "Trump suit must not be null");
    }

    @Override
    public Player caller() {
        return caller;
    }

    @Override
    public GameType gameType() {
        return GameType.FARBSOLO;
    }

    /** Returns the chosen trump suit. */
    public Suit trumpSuit() {
        return trumpSuit;
    }

    /**
     * In Farbsolo:
     * - All Ober are trump.
     * - All Unter are trump.
     * - All non-Ober, non-Unter cards of the chosen trumpSuit are trump.
     */
    @Override
    public boolean isTrump(Card card, schafkopf.rules.RuleEngine rules) {
        if (card.rank() == Rank.OBER || card.rank() == Rank.UNTER) {
            return true;
        }
        return card.suit() == trumpSuit;
    }

    /**
     * Trump strength:
     *   Eichel-Ober   = 14
     *   Gras-Ober     = 13
     *   Herz-Ober     = 12
     *   Schellen-Ober = 11
     *   Eichel-Unter  = 10
     *   Gras-Unter    = 9
     *   Herz-Unter    = 8
     *   Schellen-Unter = 7
     *   trumpSuit-Sau  = 6
     *   trumpSuit-Zehn = 5
     *   trumpSuit-König = 4
     *   trumpSuit-Neun = 3
     *   trumpSuit-Acht = 2
     *   trumpSuit-Sieben = 1
     *
     * Returns -1 if the card is not trump.
     */
    @Override
    public int getTrumpStrength(Card card) {
        if (!isTrump(card, null)) {
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
        // trumpSuit rank cards (excluding Ober/Unter)
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

    /**
     * Non-trump suit strength (Sau=6, Zehn=5, König=4, Neun=3, Acht=2, Sieben=1).
     * Returns -1 if the card is trump.
     */
    @Override
    public int getSuitStrength(Card card) {
        if (isTrump(card, null)) {
            return -1;
        }
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

    @Override
    public String displayName() {
        return "Farbsolo auf " + trumpSuit.displayName() + " (gerufen von " + caller.name() + ")";
    }

    @Override
    public String toString() {
        return displayName();
    }
}
