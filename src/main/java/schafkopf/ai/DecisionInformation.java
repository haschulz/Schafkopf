package schafkopf.ai;

import schafkopf.analysis.GameRecord;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;

import java.util.ArrayList;
import java.util.List;

public record DecisionInformation(
        List<String> playerNames,
        int observerSeat,
        List<Card> hand,
        List<GameRecord.Play> publicPlays,
        GameRecord.ContractSpec contract,
        int leaderSeat,
        SchafkopfConfig config,
        int multiplier
) {
    public DecisionInformation {
        playerNames = List.copyOf(playerNames);
        hand = List.copyOf(hand);
        publicPlays = List.copyOf(publicPlays);
        if (playerNames.size() != 4 || observerSeat < 0 || observerSeat >= 4
                || leaderSeat < 0 || leaderSeat >= 4 || multiplier < 1 || config == null) {
            throw new IllegalArgumentException("Invalid decision information");
        }
        if (initialHand(observerSeat, hand, publicPlays).size() != 8
                || initialHand(observerSeat, hand, publicPlays).stream().distinct().count() != 8) {
            throw new IllegalArgumentException("The observer must account for eight distinct cards");
        }
        if (publicPlays.stream().anyMatch(play -> play.seat() < 0 || play.seat() >= 4)
                || publicPlays.stream().map(GameRecord.Play::card).distinct().count() != publicPlays.size()
                || !java.util.Collections.disjoint(hand,
                    publicPlays.stream().map(GameRecord.Play::card).toList())) {
            throw new IllegalArgumentException("Invalid public card history");
        }
    }

    public List<Card> initialHand() {
        return initialHand(observerSeat, hand, publicPlays);
    }

    public long seed() {
        long seed = 1729L + observerSeat * 31L + leaderSeat;
        for (Card card : initialHand().stream().sorted(java.util.Comparator
                .comparingInt((Card card) -> card.suit().ordinal())
                .thenComparingInt(card -> card.rank().ordinal())).toList()) {
            seed = seed * 31 + card.suit().ordinal() * 8L + card.rank().ordinal();
        }
        for (GameRecord.Play play : publicPlays) {
            seed = seed * 31 + play.seat() * 32L + play.card().suit().ordinal() * 8L
                    + play.card().rank().ordinal();
        }
        return seed;
    }

    private static List<Card> initialHand(int observer, List<Card> cards, List<GameRecord.Play> plays) {
        List<Card> initial = new ArrayList<>(cards);
        plays.stream().filter(play -> play.seat() == observer)
                .map(GameRecord.Play::card).forEach(initial::add);
        return List.copyOf(initial);
    }
}