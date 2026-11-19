package schafkopf.analysis;

import java.util.List;

/** A replayable game plus human-reviewable suggestions; it never changes a policy automatically. */
public record LearningJournalEntry(
        String id,
        long createdAtMillis,
        String gameRecordText,
    List<Proposal> proposals
) {
    public record Proposal(
        int seat,
        String playerName,
        int playIndex,
        int trickNumber,
        schafkopf.model.Card played,
        schafkopf.model.Card suggested,
        double playedWinRate,
        double suggestedWinRate,
        double playedMeanEyes,
        double suggestedMeanEyes,
        int samples
    ) {
    }

    public LearningJournalEntry {
        if (id == null || id.isBlank() || createdAtMillis < 0 || gameRecordText == null || gameRecordText.isBlank()) {
            throw new IllegalArgumentException("Learning journal entries need an id, timestamp, and game record");
        }
        proposals = List.copyOf(proposals);
        if (proposals.isEmpty() || proposals.size() > 32) {
            throw new IllegalArgumentException("Learning journal entries require 1 to 32 proposals");
        }
    }

    public GameRecord gameRecord() {
        return GameRecordParser.parse(gameRecordText);
    }
}