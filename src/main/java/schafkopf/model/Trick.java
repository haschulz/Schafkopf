package schafkopf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Trick {
    private final int trickNumber;
    private final Player leader;
    private final List<PlayedCard> playedCards = new ArrayList<>();
    private PlayedCard winningPlayedCard;

    public Trick(int trickNumber, Player leader) {
        this.trickNumber = trickNumber;
        this.leader = leader;
    }

    public int trickNumber() {
        return trickNumber;
    }

    public Player leader() {
        return leader;
    }

    public void addCard(Player player, Card card) {
        if (playedCards.size() >= 4) {
            throw new IllegalStateException("Trick already has 4 cards.");
        }
        playedCards.add(new PlayedCard(player, card));
    }

    public List<PlayedCard> playedCards() {
        return Collections.unmodifiableList(playedCards);
    }

    public boolean isComplete() {
        return playedCards.size() == 4;
    }

    public Card leadCard() {
        return playedCards.isEmpty() ? null : playedCards.get(0).card();
    }

    public int totalPoints() {
        int sum = 0;
        for (PlayedCard pc : playedCards) {
            sum += pc.card().points();
        }
        return sum;
    }

    public void setWinningPlayedCard(PlayedCard winningPlayedCard) {
        this.winningPlayedCard = winningPlayedCard;
    }

    public PlayedCard winningPlayedCard() {
        return winningPlayedCard;
    }

    public Player winner() {
        return winningPlayedCard == null ? null : winningPlayedCard.player();
    }
}
