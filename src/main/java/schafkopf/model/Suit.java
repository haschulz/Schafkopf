package schafkopf.model;

public enum Suit {
    EICHEL("Eichel", "♣"),
    GRAS("Gras", "♠"),
    HERZ("Herz", "♥"),
    SCHELLEN("Schellen", "♦");

    private final String displayName;
    private final String symbol;

    Suit(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return displayName + " " + symbol;
    }
}
