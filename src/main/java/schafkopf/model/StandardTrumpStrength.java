package schafkopf.model;

final class StandardTrumpStrength {
    private StandardTrumpStrength() {
    }

    static int of(Card card) {
        if (card.rank() == Rank.OBER) {
            return switch (card.suit()) {
                case EICHEL -> 14;
                case GRAS -> 13;
                case HERZ -> 12;
                case SCHELLEN -> 11;
            };
        }
        if (card.rank() == Rank.UNTER) {
            return switch (card.suit()) {
                case EICHEL -> 10;
                case GRAS -> 9;
                case HERZ -> 8;
                case SCHELLEN -> 7;
            };
        }
        return switch (card.rank()) {
            case SAU -> card.suit() == Suit.HERZ ? 6 : -1;
            case ZEHN -> card.suit() == Suit.HERZ ? 5 : -1;
            case KOENIG -> card.suit() == Suit.HERZ ? 4 : -1;
            case NEUN -> card.suit() == Suit.HERZ ? 3 : -1;
            case ACHT -> card.suit() == Suit.HERZ ? 2 : -1;
            case SIEBEN -> card.suit() == Suit.HERZ ? 1 : -1;
            case OBER, UNTER -> throw new AssertionError("Ober and Unter handled above");
        };
    }

    static int suitStrength(Card card) {
        return switch (card.rank()) {
            case SAU -> 6;
            case ZEHN -> 5;
            case KOENIG -> 4;
            case NEUN -> 3;
            case ACHT -> 2;
            case SIEBEN -> 1;
            case OBER, UNTER -> -1;
        };
    }
}