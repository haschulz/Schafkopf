package schafkopf.rules;

import schafkopf.model.GameContract;
import schafkopf.model.GameType;

public final class GameBiddingRules {
    public int rank(GameContract contract) {
        return switch (contract.gameType()) {
            case RUFSPIEL -> 1;
            case FARBWENZ -> 2;
            case WENZ -> contract.isTout() ? 5 : 3;
            case FARBSOLO -> contract.isTout() ? 6 : 4;
            default -> throw new IllegalArgumentException(
                    "No bidding rank defined for " + contract.gameType());
        };
    }

    public boolean canAnnounce(GameContract candidate, GameContract current) {
        return current == null || rank(candidate) > rank(current);
    }

    public boolean supports(GameType gameType) {
        return gameType == GameType.RUFSPIEL || gameType == GameType.FARBWENZ
                || gameType == GameType.FARBSOLO || gameType == GameType.WENZ;
    }
}