package schafkopf.model;

import schafkopf.rules.RuleEngine;

/**
 * The 4 card categories that must be followed under Schafkopf rules:
 * - TRUMP (Ober, Unter, and all Herz cards)
 * - EICHEL (Non-trump Eichel cards: Sau, 10, König, 9, 8, 7)
 * - GRAS (Non-trump Gras cards: Sau, 10, König, 9, 8, 7)
 * - SCHELLEN (Non-trump Schellen cards: Sau, 10, König, 9, 8, 7)
 */
public enum SuitCategory {
    TRUMP("Trumpf"),
    EICHEL("Eichel"),
    GRAS("Gras"),
    SCHELLEN("Schellen");

    private final String displayName;

    SuitCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static SuitCategory fromCard(Card card, RuleEngine rules) {
        if (rules.isTrump(card)) {
            return TRUMP;
        }
        return switch (card.suit()) {
            case EICHEL -> EICHEL;
            case GRAS -> GRAS;
            case SCHELLEN -> SCHELLEN;
            case HERZ -> TRUMP;
        };
    }

    public static SuitCategory fromSuit(Suit suit) {
        return switch (suit) {
            case EICHEL -> EICHEL;
            case GRAS -> GRAS;
            case SCHELLEN -> SCHELLEN;
            case HERZ -> TRUMP;
        };
    }
}

