package schafkopf.android;

import schafkopf.model.Card;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;

public final class EngineStatus {
    private EngineStatus() {
    }

    public static String describe() {
        int topTrump = new RuleEngine().getTrumpStrength(new Card(Suit.EICHEL, Rank.OBER));
        return "Lokale Spielregeln bereit · höchster Rufspiel-Trumpf: " + topTrump;
    }
}