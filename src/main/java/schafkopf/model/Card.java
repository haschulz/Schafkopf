package schafkopf.model;

import java.util.Objects;

public record Card(Suit suit, Rank rank) implements Comparable<Card> {
    public Card {
        Objects.requireNonNull(suit, "Suit must not be null");
        Objects.requireNonNull(rank, "Rank must not be null");
    }

    public int points() {
        return rank.points();
    }

    public String shortName() {
        return suit.displayName() + "-" + rank.displayName();
    }

    @Override
    public String toString() {
        return suit.displayName() + " " + rank.displayName() + " (" + suit.symbol() + ")";
    }

    @Override
    public int compareTo(Card other) {
        int suitComparison = this.suit.compareTo(other.suit);
        if (suitComparison != 0) {
            return suitComparison;
        }
        return this.rank.compareTo(other.rank);
    }
}
