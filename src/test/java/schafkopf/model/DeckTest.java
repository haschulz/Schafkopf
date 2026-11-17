package schafkopf.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DeckTest {
    @Test
    void cutMovesTheTopPacketBelowTheRemainingCards() {
        Deck deck = new Deck();
        List<Card> original = new ArrayList<>(deck.cards());

        deck.cut(7);

        assertEquals(original.subList(7, 32), deck.cards().subList(0, 25));
        assertEquals(original.subList(0, 7), deck.cards().subList(25, 32));
    }

    @Test
    void cutRequiresTwoNonEmptyPackets() {
        Deck deck = new Deck();

        assertThrows(IllegalArgumentException.class, () -> deck.cut(0));
        assertThrows(IllegalArgumentException.class, () -> deck.cut(32));
    }

    @Test
    void restoresAValidSavedDeckOrderAndRejectsDuplicateCards() {
        Deck original = new Deck();
        original.shuffle(new java.util.Random(23L));
        Card duplicateCard = original.cards().get(0);
        List<Card> duplicateCards = java.util.Collections.nCopies(32, duplicateCard);

        assertEquals(original.cards(), new Deck(original.cards()).cards());
        assertThrows(IllegalArgumentException.class, () -> new Deck(duplicateCards));
    }
}