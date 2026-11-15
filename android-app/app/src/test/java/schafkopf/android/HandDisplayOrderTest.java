package schafkopf.android;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.Player;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;

public class HandDisplayOrderTest {
    @Test
    public void sortsRufspielTrumpsThenSideSuitsByStrength() {
        List<Card> sorted = HandDisplayOrder.sortForRufspiel(new Deck().cards());
        RuleEngine rules = new RuleEngine();

        assertEquals(32, sorted.size());
        for (int index = 0; index < 14; index++) {
            assertEquals(14 - index, rules.getTrumpStrength(sorted.get(index)));
        }

        int index = 14;
        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            for (int strength = 6; strength >= 1; strength--) {
                Card card = sorted.get(index++);
                assertEquals(suit, card.suit());
                assertEquals(strength, rules.getSuitStrength(card));
            }
        }
    }

    @Test
    public void sortsWenzAndSoloUsingTheirOwnTrumpDefinitions() {
        Player caller = new Player("1", "Caller");
        List<Card> deck = new Deck().cards();
        List<Card> wenz = HandDisplayOrder.sortForContract(deck, new WenzContract(caller));
        List<Card> solo = HandDisplayOrder.sortForContract(deck, new SoloContract(caller, Suit.EICHEL));
        RuleEngine rules = new RuleEngine();

        for (int index = 0; index < 4; index++) {
            assertEquals(10 - index, new WenzContract(caller).getTrumpStrength(wenz.get(index)));
        }
        for (int index = 0; index < 14; index++) {
            assertEquals(14 - index, new SoloContract(caller, Suit.EICHEL).getTrumpStrength(solo.get(index)));
        }
    }
}