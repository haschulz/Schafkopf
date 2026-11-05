package schafkopf.model;

import java.util.Objects;
import schafkopf.rules.RuleEngine;

public record RufspielContract(Player caller, Suit calledSuit)
        implements GameContract {

    public RufspielContract {
        Objects.requireNonNull(caller, "Caller must not be null");
        Objects.requireNonNull(calledSuit, "Called suit must not be null");

        if (calledSuit == Suit.HERZ) {
            throw new IllegalArgumentException(
                    "Herz cannot be called in Rufspiel because Herz is trump!");
        }
    }

    /** Die gerufene Sau – Rufspiel-spezifisch, nicht Teil von GameContract. */
    public Card calledSau() {
        return new Card(calledSuit, Rank.SAU);
    }

    @Override
    public GameType gameType() {
        return GameType.RUFSPIEL;
    }

    /** Im Rufspiel sind alle Ober, Unter und Herz-Karten Trumpf. */
    @Override
    public boolean isTrump(Card card, RuleEngine rules) {
        return card.rank() == Rank.OBER
                || card.rank() == Rank.UNTER
                || card.suit() == Suit.HERZ;
    }

    /**
     * Trumpfreihenfolge: Ober, Unter, danach die Herz-Karten
     * in normaler Rangfolge.
     * Nicht-Trümpfe erhalten -1.
     */
    @Override
    public int getTrumpStrength(Card card) {
        return StandardTrumpStrength.of(card);
    }

    /** Stärke einer Nicht-Trumpfkarte innerhalb ihrer Farbe. */
    @Override
    public int getSuitStrength(Card card) {
        if (isTrump(card, null)) {
            return -1;
        }

        return StandardTrumpStrength.suitStrength(card);
    }

    @Override
    public String displayName() {
        return "Rufspiel auf die " + calledSuit.displayName()
                + "-Sau (gerufen von " + caller.name() + ")";
    }

    @Override
    public String toString() {
        return displayName();
    }
}