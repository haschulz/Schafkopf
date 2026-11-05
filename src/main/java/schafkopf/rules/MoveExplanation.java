package schafkopf.rules;

import schafkopf.model.Card;
import schafkopf.model.Player;

public record MoveExplanation(
    Player player,
    Card card,
    String ruleRequirement,
    String tacticalReasoning,
    boolean isLegal
) {
    public String toLogLine(int trickNumber, int turnInTrick) {
        return String.format("[Stich %d, Karte %d] %s: %s -> spielt %s (%d Augen) [%s]",
                trickNumber,
                turnInTrick,
                player.name(),
                ruleRequirement,
                card.shortName(),
                card.points(),
                tacticalReasoning);
    }
}
