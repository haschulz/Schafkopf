package schafkopf.state;

import schafkopf.model.Player;
import schafkopf.model.GameContract;

import java.util.List;

public record GameResult(
    GameContract contract,
    Player partner,
    List<Player> callerTeam,
    List<Player> defenderTeam,
    int callerTeamAugen,
    int defenderTeamAugen,
    boolean callerTeamWon,
    boolean isSchneider,
    boolean isSchwarz,
    String summary
) {
    @Override
    public String toString() {
        return summary;
    }
}
