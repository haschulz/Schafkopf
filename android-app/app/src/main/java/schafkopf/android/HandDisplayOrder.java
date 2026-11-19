package schafkopf.android;

import java.util.Comparator;
import java.util.List;

import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;

public final class HandDisplayOrder {
    private static final RuleEngine RULES = new RuleEngine();

    private HandDisplayOrder() {
    }

    public static List<Card> sortForRufspiel(List<Card> cards) {
        return sortForContract(cards, new RufspielContract(new Player("sort", "Sort"), Suit.EICHEL));
    }

    public static List<Card> sortForContract(List<Card> cards, GameContract contract) {
        return cards.stream().sorted((first, second) -> compareStrength(first, second, contract)).toList();
    }

    private static int compareStrength(Card first, Card second, GameContract contract) {
        boolean firstTrump = contract.isTrump(first, RULES);
        boolean secondTrump = contract.isTrump(second, RULES);
        if (firstTrump != secondTrump) {
            return firstTrump ? -1 : 1;
        }
        if (firstTrump) {
            return Integer.compare(contract.getTrumpStrength(second), contract.getTrumpStrength(first));
        }

        int suitOrder = Integer.compare(suitOrder(first.suit()), suitOrder(second.suit()));
        if (suitOrder != 0) {
            return suitOrder;
        }
        return Integer.compare(contract.getSuitStrength(second), contract.getSuitStrength(first));
    }

    private static int suitOrder(Suit suit) {
        return switch (suit) {
            case EICHEL -> 0;
            case GRAS -> 1;
            case SCHELLEN -> 2;
            case HERZ -> 3;
        };
    }
}