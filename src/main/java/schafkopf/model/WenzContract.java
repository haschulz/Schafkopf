package schafkopf.model;

import java.util.Objects;

/**
 * Contract for Wenz and Farbwenz game types.
 *
 * <p><b>Wenz:</b> Only the 4 Unter cards are trump (no Ober, no Herz suit).
 * Trump order (strongest first): Eichel-Unter > Gras-Unter > Herz-Unter > Schellen-Unter.
 *
 * <p><b>Farbwenz:</b> All 4 Unter cards are trump PLUS a chosen suit (trumpSuit).
 * Trump order: Eichel-Unter (4) > Gras-Unter (3) > Herz-Unter (2) > Schellen-Unter (1),
 * then trumpSuit cards in normal rank order (Sau > 10 > König > 9 > 8 > 7).
 */
public class WenzContract implements GameContract {

    private final Player caller;
    private final GameType type; // WENZ or FARBWENZ
    private final Suit trumpSuit; // null for plain Wenz, non-null for Farbwenz

    /** Creates a plain Wenz contract (no suit trump). */
    public WenzContract(Player caller) {
        this(caller, null);
    }

    /**
     * Creates a Farbwenz contract when trumpSuit is non-null, or a plain Wenz when null.
     *
     * @param caller    the announcing player
     * @param trumpSuit additional trump suit (null = plain Wenz)
     */
    public WenzContract(Player caller, Suit trumpSuit) {
        this.caller = Objects.requireNonNull(caller, "Caller must not be null");
        this.trumpSuit = trumpSuit;
        this.type = (trumpSuit != null) ? GameType.FARBWENZ : GameType.WENZ;
    }

    @Override
    public Player caller() {
        return caller;
    }

    @Override
    public GameType gameType() {
        return type;
    }

    /** Returns the optional additional trump suit (only for Farbwenz). */
    public Suit trumpSuit() {
        return trumpSuit;
    }

    /**
     * In Wenz: only Unter cards are trump.
     * In Farbwenz: Unter cards + all non-Unter cards of trumpSuit.
     */
    @Override
    public boolean isTrump(Card card, schafkopf.rules.RuleEngine rules) {
        if (card.rank() == Rank.UNTER) {
            return true; // All 4 Unter are always trump in Wenz/Farbwenz
        }
        if (trumpSuit != null && card.suit() == trumpSuit && card.rank() != Rank.UNTER) {
            return true; // Farbwenz: trump suit cards (Unter already handled above)
        }
        return false;
    }

    /**
     * Trump strength:
     *   Eichel-Unter = 10
     *   Gras-Unter   = 9
     *   Herz-Unter   = 8
     *   Schellen-Unter = 7
     *
    * For Farbwenz, suit trump cards rank below Unter in normal suit order:
    *   trumpSuit-Sau = 6, trumpSuit-Zehn = 5, trumpSuit-König = 4,
    *   trumpSuit-Ober = 3, trumpSuit-Neun = 2, trumpSuit-Acht = 1,
    *   trumpSuit-Sieben = 0.
     *
     * Returns -1 if card is not trump.
     */
    @Override
    public int getTrumpStrength(Card card) {
        if (!isTrump(card, null)) {
            return -1;
        }
        if (card.rank() == Rank.UNTER) {
            return switch (card.suit()) {
                case EICHEL   -> 10;
                case GRAS     -> 9;
                case HERZ     -> 8;
                case SCHELLEN -> 7;
            };
        }
        // Farbwenz: non-Unter trumpSuit cards
        return switch (card.rank()) {
            case SAU    -> 6;
            case ZEHN   -> 5;
            case KOENIG -> 4;
            case OBER   -> 3;
            case NEUN   -> 2;
            case ACHT   -> 1;
            case SIEBEN -> 0;
            case UNTER  -> throw new AssertionError("Unter handled above");
        };
    }

    /**
     * Non-trump suit strength (Sau=6, Zehn=5, König=4, Neun=3, Acht=2, Sieben=1).
     * Ober are NOT trump in Wenz, so they follow normal suit strength.
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
            case OBER   -> 3; // Ober is a normal suit card in Wenz!
            case NEUN   -> 2;
            case ACHT   -> 1;
            case SIEBEN -> 0;
            default     -> 0;
        };
    }

    @Override
    public String displayName() {
        if (type == GameType.FARBWENZ) {
            return "Farbwenz auf " + trumpSuit.displayName() + " (gerufen von " + caller.name() + ")";
        }
        return "Wenz (gerufen von " + caller.name() + ")";
    }

    @Override
    public String toString() {
        return displayName();
    }
}
