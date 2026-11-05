package schafkopf.model;

/**
 * Common interface for all game contracts (Rufspiel, solos, Ramsch, and Kreuzbock).
 * A contract defines which cards are trump, how trump strength is calculated,
 * and which player announced the game.
 */
public interface GameContract {
    /** The announcing player, or {@code null} for games without a soloist. */
    Player caller();

    /** The type of game being played. */
    GameType gameType();

    /**
     * Returns true if the given card is a trump card under this contract's rules.
     *
     * @param card  the card to test
     * @param rules the RuleEngine to delegate basic queries to
     * @return true if the card is trump
     */
    boolean isTrump(Card card, schafkopf.rules.RuleEngine rules);

    /**
     * Returns the trump strength of a card (higher = stronger).
     * Returns -1 if the card is not trump.
     */
    int getTrumpStrength(Card card);

    /**
     * Returns the non-trump suit strength of a card (higher = stronger).
     * Returns -1 if the card is trump.
     */
    int getSuitStrength(Card card);

    /** Human-readable name of the contract. */
    String displayName();
}
