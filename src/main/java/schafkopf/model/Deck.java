package schafkopf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        reset();
    }

    public void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public List<List<Card>> deal4x8() {
        if (cards.size() != 32) {
            throw new IllegalStateException("A Schafkopf deck must contain exactly 32 cards to deal.");
        }
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            hands.add(new ArrayList<>(cards.subList(i * 8, (i + 1) * 8)));
        }
        return hands;
    }
}
