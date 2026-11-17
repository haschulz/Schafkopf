package schafkopf.android;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
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

        for (int index = 0; index < 4; index++) {
            assertEquals(10 - index, new WenzContract(caller).getTrumpStrength(wenz.get(index)));
        }
        for (int index = 0; index < 14; index++) {
            assertEquals(14 - index, new SoloContract(caller, Suit.EICHEL).getTrumpStrength(solo.get(index)));
        }
    }

    @Test
    public void sortsExampleSauspielHandByTrumpAndCardStrength() {
        Player caller = new Player("1", "Caller");
        List<Card> hand = List.of(
                new Card(Suit.SCHELLEN, Rank.NEUN),
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.HERZ, Rank.OBER),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.GRAS, Rank.OBER));

        List<Card> sorted = HandDisplayOrder.sortForContract(
                hand, new RufspielContract(caller, Suit.EICHEL));

        assertEquals(List.of(
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.OBER),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.NEUN)), sorted);
    }

    @Test
    public void sortsPlainWenzSideSuitsFromEichelDownToSchellen() {
        Player caller = new Player("1", "Caller");
        List<Card> hand = List.of(
                new Card(Suit.SCHELLEN, Rank.SAU), new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.GRAS, Rank.SAU), new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.SCHELLEN, Rank.SIEBEN), new Card(Suit.HERZ, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.EICHEL, Rank.SIEBEN));

        List<Card> sorted = HandDisplayOrder.sortForContract(hand, new WenzContract(caller));

        assertEquals(List.of(
                new Card(Suit.EICHEL, Rank.SAU), new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.SIEBEN),
                new Card(Suit.HERZ, Rank.SAU), new Card(Suit.HERZ, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.SAU), new Card(Suit.SCHELLEN, Rank.SIEBEN)), sorted);
    }

    @Test
    public void sortsSchellenFarbwenzBeforeDescendingSideSuits() {
        Player caller = new Player("1", "Caller");
        List<Card> sorted = HandDisplayOrder.sortForContract(
                new Deck().cards(), new WenzContract(caller, Suit.SCHELLEN));
        List<Card> expected = new ArrayList<>(List.of(
                new Card(Suit.EICHEL, Rank.UNTER), new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER), new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.SCHELLEN, Rank.SAU), new Card(Suit.SCHELLEN, Rank.ZEHN),
                new Card(Suit.SCHELLEN, Rank.KOENIG), new Card(Suit.SCHELLEN, Rank.OBER),
                new Card(Suit.SCHELLEN, Rank.NEUN), new Card(Suit.SCHELLEN, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.SIEBEN)));
        List<Rank> sideRanks = List.of(
                Rank.SAU, Rank.ZEHN, Rank.KOENIG, Rank.OBER, Rank.NEUN, Rank.ACHT, Rank.SIEBEN);
        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.HERZ)) {
            sideRanks.forEach(rank -> expected.add(new Card(suit, rank)));
        }

        assertEquals(expected, sorted);
    }

    @Test
    public void usesDefaultOrderUntilTheBidIsClosedThenUsesTheAnnouncedGame() {
        Player caller = new Player("1", "Caller");
        WenzContract currentBid = new WenzContract(caller);
        List<Card> hand = List.of(
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.OBER));

        List<Card> preContract = HandDisplayOrder.sortForStage(hand, currentBid, false);
        List<Card> closedContract = HandDisplayOrder.sortForStage(hand, currentBid, true);

        assertEquals(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.EICHEL, Rank.SAU)), preContract);
        assertEquals(List.of(
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.HERZ, Rank.SAU)), closedContract);
    }
}