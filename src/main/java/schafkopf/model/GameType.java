package schafkopf.model;

public enum GameType {
    RUFSPIEL("Rufspiel (Sauspiel)"),
    WENZ("Wenz"),
    FARBWENZ("Farbwenz"),
    FARBSOLO("Farbsolo"),
    GEIER("Geier"),
    BETTEL("Bettel"),
    RAMSCH("Ramsch"),
    KREUZBOCK("Kreuzbock");

    private final String displayName;

    GameType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
