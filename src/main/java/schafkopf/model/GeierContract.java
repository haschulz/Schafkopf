package schafkopf.model;

/** Standard Geier contract: only the four Ober cards are trump. */
public record GeierContract(Player caller) implements GameContract {
    public GeierContract {
        java.util.Objects.requireNonNull(caller, "Caller must not be null");
    }

    @Override
    public GameType gameType() {
        return GameType.GEIER;
    }

    @Override
    public boolean isTrump(Card card, schafkopf.rules.RuleEngine rules) {
        return card.rank() == Rank.OBER;
    }

    @Override
    public int getTrumpStrength(Card card) {
        if (card.rank() != Rank.OBER) {
            return -1;
        }
        return switch (card.suit()) {
            case EICHEL -> 4;
            case GRAS -> 3;
            case HERZ -> 2;
            case SCHELLEN -> 1;
        };
    }

    @Override
    public int getSuitStrength(Card card) {
        if (isTrump(card, null)) {
            return -1;
        }
        return switch (card.rank()) {
            case SAU -> 6;
            case ZEHN -> 5;
            case KOENIG -> 4;
            case UNTER -> 3;
            case NEUN -> 2;
            case ACHT -> 1;
            case SIEBEN -> 0;
            case OBER -> throw new AssertionError("Ober handled as trump");
        };
    }

    @Override
    public String displayName() {
        return "Geier (gespielt von " + caller.name() + ")";
    }
}