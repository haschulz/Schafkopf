package schafkopf.ai;

import schafkopf.analysis.GameRecord;
import schafkopf.model.Card;
import schafkopf.model.GameType;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.model.SuitCategory;
import schafkopf.rules.RuleEngine;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

public final class BeliefSampler {
    private final RuleEngine rules = new RuleEngine();

    public List<List<List<Card>>> sample(DecisionInformation information,
                                        GameRecord.ContractSpec contract, int samples, long seed) {
        if (samples < 1 || samples > 10000 || contract == null) {
            throw new IllegalArgumentException("A contract and 1 to 10000 samples are required");
        }
        List<List<Card>> fixed = fixedCards(information);
        List<Card> unknown = allCards();
        fixed.forEach(unknown::removeAll);
        List<Set<SuitCategory>> voids = observedVoids(information, contract);
        List<List<List<Card>>> worlds = new ArrayList<>();
        Random random = new Random(seed);
        if (unknown.size() <= 8) {
            enumerate(information, contract, fixed, unknown, voids, 0, worlds);
            Collections.shuffle(worlds, random);
            if (worlds.isEmpty()) {
                throw new IllegalStateException("No legal world matches the public history");
            }
            return List.copyOf(worlds.subList(0, Math.min(samples, worlds.size())));
        }
        for (int attempt = 0; attempt < samples * 2000 && worlds.size() < samples; attempt++) {
            List<List<Card>> hands = deal(fixed, unknown, information.observerSeat(), random);
            if (matchesVoids(information, contract, hands, voids)
                    && legalHistory(information, contract, hands)) {
                worlds.add(hands.stream().map(List::copyOf).toList());
            }
        }
        if (worlds.isEmpty()) {
            throw new IllegalStateException("No consistent sampled world; hidden hands are not a fallback");
        }
        return List.copyOf(worlds);
    }

    private void enumerate(DecisionInformation information, GameRecord.ContractSpec spec,
                            List<List<Card>> hands, List<Card> unknown, List<Set<SuitCategory>> voids,
                            int next, List<List<List<Card>>> worlds) {
        if (next == unknown.size()) {
            if (matchesVoids(information, spec, hands, voids) && legalHistory(information, spec, hands)) {
                worlds.add(hands.stream().map(List::copyOf).toList());
            }
            return;
        }
        Card card = unknown.get(next);
        SuitCategory category = SuitCategory.fromCard(card, spec.bind(players(information)), rules);
        for (int seat = 0; seat < 4; seat++) {
            List<Card> hand = hands.get(seat);
            if (seat != information.observerSeat() && hand.size() < 8 && !voids.get(seat).contains(category)) {
                hand.add(card);
                enumerate(information, spec, hands, unknown, voids, next + 1, worlds);
                hand.remove(card);
            }
        }
    }

    public SchafkopfGame replay(DecisionInformation information, GameRecord.ContractSpec contract,
                                List<List<Card>> hands, SimpleBot bot) {
        GameRecord gameRecord = new GameRecord(information.playerNames(), hands, contract,
                information.leaderSeat(), information.publicPlays());
        SchafkopfGame game = gameRecord.replay(hands, information.publicPlays().size(), rules, bot,
                information.config());
        game.bettingState().restoreLegacyMultiplier(information.multiplier());
        return game;
    }

    private List<List<Card>> fixedCards(DecisionInformation information) {
        List<List<Card>> fixed = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            fixed.add(new ArrayList<>());
        }
        information.publicPlays().forEach(play -> fixed.get(play.seat()).add(play.card()));
        fixed.set(information.observerSeat(), new ArrayList<>(information.initialHand()));
        return fixed;
    }

    private List<Card> allCards() {
        List<Card> cards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        return cards;
    }

    private List<List<Card>> deal(List<List<Card>> fixed, List<Card> unknown, int observer, Random random) {
        List<Card> shuffled = new ArrayList<>(unknown);
        Collections.shuffle(shuffled, random);
        List<List<Card>> hands = new ArrayList<>();
        int next = 0;
        for (int seat = 0; seat < 4; seat++) {
            List<Card> hand = new ArrayList<>(fixed.get(seat));
            while (seat != observer && hand.size() < 8) {
                hand.add(shuffled.get(next++));
            }
            hands.add(hand);
        }
        return hands;
    }

    private List<Set<SuitCategory>> observedVoids(DecisionInformation information,
                                                 GameRecord.ContractSpec spec) {
        var contract = spec.bind(players(information));
        List<Set<SuitCategory>> voids = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            voids.add(new HashSet<>());
        }
        List<GameRecord.Play> plays = information.publicPlays();
        for (int index = 0; index < plays.size(); index++) {
            SuitCategory led = SuitCategory.fromCard(plays.get(index - index % 4).card(), contract, rules);
            GameRecord.Play play = plays.get(index);
            if (SuitCategory.fromCard(play.card(), contract, rules) != led) {
                voids.get(play.seat()).add(led);
            }
        }
        return voids;
    }

    private boolean matchesVoids(DecisionInformation information, GameRecord.ContractSpec spec,
                                  List<List<Card>> hands, List<Set<SuitCategory>> voids) {
        var contract = spec.bind(players(information));
        Set<Card> publicCards = new HashSet<>(information.publicPlays().stream()
                .map(GameRecord.Play::card).toList());
        for (int seat = 0; seat < 4; seat++) {
            for (Card card : hands.get(seat)) {
                if (!publicCards.contains(card)
                        && voids.get(seat).contains(SuitCategory.fromCard(card, contract, rules))) {
                    return false;
                }
            }
        }
        if (spec.type() != GameType.RUFSPIEL) {
            return true;
        }
        List<Card> caller = hands.get(spec.callerSeat());
        return !caller.contains(new Card(spec.suit(), Rank.SAU)) && caller.stream()
                .anyMatch(card -> card.suit() == spec.suit() && !contract.isTrump(card, rules));
    }

    private boolean legalHistory(DecisionInformation information, GameRecord.ContractSpec contract,
                                  List<List<Card>> hands) {
        try {
            replay(information, contract, hands,
                    new SimpleBot(rules, true, schafkopf.ai.policy.PolicyGenome.baseline()));
            return true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return false;
        }
    }

    private List<Player> players(DecisionInformation information) {
        return IntStream.range(0, 4).mapToObj(seat ->
                new Player(Integer.toString(seat + 1), information.playerNames().get(seat))).toList();
    }
}