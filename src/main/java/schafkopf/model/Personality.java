package schafkopf.model;

/**
 * Defines player risk attitudes at the Schafkopf table:
 * - MAURER: Very defensive, risk-averse, plays only with overwhelming strength (Score >= 26).
 * - SOLIDE: Balanced, realistic tournament style (Score >= 20).
 * - ZOCKER: Aggressive, risk-seeking, attacks even on thin margins (Score >= 14).
 */
public enum Personality {
    MAURER("Der Maurer (Defensiv & Vorsichtig)", 26, 0.25),
    SOLIDE("Solider Spieler (Ausgewogen)", 20, 0.45),
    ZOCKER("Der Zocker (Offensiv & Risikofreudig)", 14, 0.70);

    private final String description;
    private final int minPlayScore;
    private final double riskTolerance;

    Personality(String description, int minPlayScore, double riskTolerance) {
        this.description = description;
        this.minPlayScore = minPlayScore;
        this.riskTolerance = riskTolerance;
    }

    public String description() {
        return description;
    }

    public int minPlayScore() {
        return minPlayScore;
    }

    public double riskTolerance() {
        return riskTolerance;
    }
}

