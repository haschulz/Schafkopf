package schafkopf.analysis;

import schafkopf.model.Card;
import schafkopf.model.Rank;
import schafkopf.model.Suit;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/** Stable, versioned offline encoding for learning-journal entries. */
public final class LearningJournalCodec {
    private static final String VERSION = "1";

    private LearningJournalCodec() {
    }

    public static String encode(LearningJournalEntry entry) {
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("id", entry.id());
        properties.setProperty("createdAtMillis", Long.toString(entry.createdAtMillis()));
        properties.setProperty("gameRecord", Base64.getEncoder().encodeToString(
                entry.gameRecordText().getBytes(StandardCharsets.UTF_8)));
        properties.setProperty("proposalCount", Integer.toString(entry.proposals().size()));
        for (int index = 0; index < entry.proposals().size(); index++) {
            encodeProposal(properties, index, entry.proposals().get(index));
        }
        StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode in-memory learning journal", impossible);
        }
        return writer.toString();
    }

    public static LearningJournalEntry decode(String encoded) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(encoded));
            if (!VERSION.equals(properties.getProperty("version"))) {
                throw new IllegalArgumentException("Unsupported learning journal version");
            }
            long createdAtMillis = Long.parseLong(properties.getProperty("createdAtMillis"));
            int proposalCount = Integer.parseInt(properties.getProperty("proposalCount"));
            if (proposalCount < 1 || proposalCount > 32) {
                throw new IllegalArgumentException("Invalid learning proposal count");
            }
            String gameRecord = new String(Base64.getDecoder().decode(properties.getProperty("gameRecord")),
                    StandardCharsets.UTF_8);
            List<LearningJournalEntry.Proposal> proposals = new ArrayList<>(proposalCount);
            for (int index = 0; index < proposalCount; index++) {
                proposals.add(decodeProposal(properties, index));
            }
            LearningJournalEntry entry = new LearningJournalEntry(
                    properties.getProperty("id"), createdAtMillis, gameRecord, proposals);
            entry.gameRecord();
            return entry;
        } catch (IOException | RuntimeException invalid) {
            if (invalid instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            throw new IllegalArgumentException("Invalid learning journal entry", invalid);
        }
    }

    private static void encodeProposal(
            Properties properties, int index, LearningJournalEntry.Proposal proposal
    ) {
        String prefix = "proposal." + index + ".";
        properties.setProperty(prefix + "seat", Integer.toString(proposal.seat()));
        properties.setProperty(prefix + "playerName", proposal.playerName());
        properties.setProperty(prefix + "playIndex", Integer.toString(proposal.playIndex()));
        properties.setProperty(prefix + "trickNumber", Integer.toString(proposal.trickNumber()));
        properties.setProperty(prefix + "played", encodeCard(proposal.played()));
        properties.setProperty(prefix + "suggested", encodeCard(proposal.suggested()));
        properties.setProperty(prefix + "playedWinRate", Double.toString(proposal.playedWinRate()));
        properties.setProperty(prefix + "suggestedWinRate", Double.toString(proposal.suggestedWinRate()));
        properties.setProperty(prefix + "playedMeanEyes", Double.toString(proposal.playedMeanEyes()));
        properties.setProperty(prefix + "suggestedMeanEyes", Double.toString(proposal.suggestedMeanEyes()));
        properties.setProperty(prefix + "samples", Integer.toString(proposal.samples()));
    }

    private static LearningJournalEntry.Proposal decodeProposal(Properties properties, int index) {
        String prefix = "proposal." + index + ".";
        return new LearningJournalEntry.Proposal(
                Integer.parseInt(properties.getProperty(prefix + "seat")),
                properties.getProperty(prefix + "playerName"),
                Integer.parseInt(properties.getProperty(prefix + "playIndex")),
                Integer.parseInt(properties.getProperty(prefix + "trickNumber")),
                decodeCard(properties.getProperty(prefix + "played")),
                decodeCard(properties.getProperty(prefix + "suggested")),
                Double.parseDouble(properties.getProperty(prefix + "playedWinRate")),
                Double.parseDouble(properties.getProperty(prefix + "suggestedWinRate")),
                Double.parseDouble(properties.getProperty(prefix + "playedMeanEyes")),
                Double.parseDouble(properties.getProperty(prefix + "suggestedMeanEyes")),
                Integer.parseInt(properties.getProperty(prefix + "samples")));
    }

    private static String encodeCard(Card card) {
        return card.suit().name() + ":" + card.rank().name();
    }

    private static Card decodeCard(String encoded) {
        String[] parts = encoded.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid card in learning journal");
        }
        return new Card(Suit.valueOf(parts[0]), Rank.valueOf(parts[1]));
    }
}