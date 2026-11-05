package schafkopf.model;

import java.util.Objects;

public record BettelContract(Player caller) implements GameContract {
    public BettelContract {
        Objects.requireNonNull(caller, "Caller must not be null");
    }

    @Override
    public GameType gameType() {
        return GameType.BETTEL;
    }

    @Override
    public boolean isTrump(Card card, schafkopf.rules.RuleEngine rules) {
        return false;
    }

    @Override
    public int getTrumpStrength(Card card) {
        return -1;
    }

    @Override
    public int getSuitStrength(Card card) {
        return switch (card.rank()) {
            case SAU -> 8;
            case ZEHN -> 7;
            case KOENIG -> 6;
            case OBER -> 5;
            case UNTER -> 4;
            case NEUN -> 3;
            case ACHT -> 2;
            case SIEBEN -> 1;
        };
    }

    @Override
    public String displayName() {
        return "Bettel (gerufen von " + caller.name() + ")";
    }
}