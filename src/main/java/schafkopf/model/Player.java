package schafkopf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private final String id;
    private final String name;
    private final List<Card> hand = new ArrayList<>();
    private final List<Trick> wonTricks = new ArrayList<>();

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Card> hand() {
        return Collections.unmodifiableList(hand);
    }

    public void setHand(List<Card> cards) {
        this.hand.clear();
        this.hand.addAll(cards);
    }

    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    public void removeCard(Card card) {
        if (!hand.remove(card)) {
            throw new IllegalArgumentException("Player " + name + " does not hold card: " + card);
        }
    }

    public void addWonTrick(Trick trick) {
        this.wonTricks.add(trick);
    }

    public List<Trick> wonTricks() {
        return Collections.unmodifiableList(wonTricks);
    }

    public int totalAugen() {
        int sum = 0;
        for (Trick trick : wonTricks) {
            sum += trick.totalPoints();
        }
        return sum;
    }

    public void reset() {
        this.hand.clear();
        this.wonTricks.clear();
    }

    @Override
    public String toString() {
        return name;
    }
}
