package schafkopf.model;

import java.util.Objects;

public record PlayedCard(Player player, Card card) {
    public PlayedCard {
        Objects.requireNonNull(player, "Player must not be null");
        Objects.requireNonNull(card, "Card must not be null");
    }

    @Override
    public String toString() {
        return player.name() + ": " + card.shortName() + " (" + card.points() + " Pkt)";
    }
}
