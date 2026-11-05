package schafkopf.model;

public final class RamschContract implements GameContract {
    @Override
    public Player caller() {
        return null;
    }

    @Override
    public GameType gameType() {
        return GameType.RAMSCH;
    }

    @Override
    public boolean isTrump(Card card, schafkopf.rules.RuleEngine rules) {
        return card.rank() == Rank.OBER || card.rank() == Rank.UNTER || card.suit() == Suit.HERZ;
    }

    @Override
    public int getTrumpStrength(Card card) {
        return StandardTrumpStrength.of(card);
    }

    @Override
    public int getSuitStrength(Card card) {
        if (isTrump(card, null)) {
            return -1;
        }
        return StandardTrumpStrength.suitStrength(card);
    }

    @Override
    public String displayName() {
        return "Ramsch";
    }
}