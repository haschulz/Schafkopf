package schafkopf.model;

public enum Rank {
    SIEBEN("7", 0),
    ACHT("8", 0),
    NEUN("9", 0),
    UNTER("Unter", 2),
    OBER("Ober", 3),
    KOENIG("König", 4),
    ZEHN("10", 10),
    SAU("Sau", 11);

    private final String displayName;
    private final int points;

    Rank(String displayName, int points) {
        this.displayName = displayName;
        this.points = points;
    }

    public String displayName() {
        return displayName;
    }

    public int points() {
        return points;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
