package schafkopf.ai;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.analysis.GameRecord;
import schafkopf.economy.Tariff;
import schafkopf.model.Card;
import schafkopf.model.GameType;
import schafkopf.rules.RuleEngine;
import schafkopf.state.Phase;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DecisionAnalysis {
    public record Estimate(String action, double winProbability, double expectedReturnCents,
                           double returnStandardErrorCents, double probabilityLow, double probabilityHigh,
                           double opponentEyes, double opponentTricks, int worlds, double effectiveWorlds) {
    }

    public record Report(Map<String, Estimate> alternatives, String selectedAction,
                         List<String> opponentPolicies, long seed, String uncertainty) {
        public Report {
            alternatives = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(alternatives));
            opponentPolicies = List.copyOf(opponentPolicies);
        }
    }

    private record Outcome(double won, double cents, double opponentEyes, double opponentTricks) {
    }

    private final BeliefSampler sampler = new BeliefSampler();
    private final RuleEngine rules = new RuleEngine();
    private final List<PolicyGenome> opponents;

    public DecisionAnalysis(List<PolicyGenome> opponents) {
        if (opponents == null || opponents.isEmpty()) {
            throw new IllegalArgumentException("At least one explicit opponent policy is required");
        }
        this.opponents = List.copyOf(opponents);
    }

    public static List<PolicyGenome> opponentPool() {
        PolicyGenome conservative = PolicyGenome.baseline().withId("analysis-conservative");
        PolicyGenome aggressive = conservative.withId("analysis-aggressive");
        PolicyGenome control = conservative.withId("analysis-trump-control");
        for (GameType type : List.of(GameType.RUFSPIEL, GameType.FARBSOLO, GameType.WENZ, GameType.FARBWENZ)) {
            String prefix = "card." + type.name() + ".";
            conservative = conservative.withWeight(prefix + "points", -0.5);
            aggressive = aggressive.withWeight(prefix + "points", 0.5).withWeight(prefix + "winningTrick", 4);
            control = control.withWeight(prefix + "trumpStrength", 1.5);
        }
        return List.of(conservative, aggressive, control);
    }

    public Report cards(DecisionInformation information, int samples, long seed) {
        if (information.contract() == null) {
            throw new IllegalArgumentException("A card decision requires a public contract");
        }
        var worlds = sampler.sample(information, information.contract(), samples, seed);
        SimpleBot baseline = new SimpleBot(rules, true, opponents.get(0));
        SchafkopfGame first = sampler.replay(information, information.contract(), worlds.get(0), baseline);
        if (first.players().indexOf(first.currentPlayer()) != information.observerSeat()) {
            throw new IllegalArgumentException("The observer is not the acting player");
        }
        List<Card> legal = first.legalMovesForCurrentPlayer();
        double[] weights = weights(information, worlds);
        Map<String, Estimate> estimates = new LinkedHashMap<>();
        for (Card action : legal) {
            estimates.put(action.shortName(), evaluate(information, information.contract(),
                    worlds, weights, action, action.shortName()));
        }
        return report(estimates, seed);
    }

    public Report contracts(DecisionInformation information, List<GameRecord.ContractSpec> candidates,
                            GameRecord.ContractSpec standingBid, int samples, long seed) {
        if (!information.publicPlays().isEmpty() || candidates.isEmpty()) {
            throw new IllegalArgumentException("Contract analysis needs candidates before card play");
        }
        GameRecord.ContractSpec constraint = standingBid == null
                ? new GameRecord.ContractSpec(GameType.WENZ, information.observerSeat(), null, false) : standingBid;
        var worlds = sampler.sample(information, constraint, samples, seed);
        double[] weights = new double[worlds.size()];
        java.util.Arrays.fill(weights, 1.0);
        Map<String, Estimate> estimates = new LinkedHashMap<>();
        estimates.put("PASS", standingBid == null
                ? new Estimate("PASS", 0, 0, 0, 0, 1, 0, 0, worlds.size(), worlds.size())
                : evaluate(information, standingBid, worlds, weights, null, "PASS"));
        for (GameRecord.ContractSpec candidate : candidates) {
            String key = contractKey(candidate);
            estimates.put(key, evaluate(information, candidate, worlds, weights, null, key));
        }
        return report(estimates, seed);
    }

    public static String contractKey(GameRecord.ContractSpec contract) {
        return contract.type().name() + ":" + (contract.suit() == null ? "NONE" : contract.suit().name())
                + (contract.tout() ? ":TOUT" : "");
    }

    private Report report(Map<String, Estimate> estimates, long seed) {
        String selected = estimates.values().stream()
                .max(Comparator.comparingDouble(Estimate::expectedReturnCents))
                .orElseThrow().action();
        return new Report(estimates, selected, opponents.stream().map(PolicyGenome::id).toList(), seed,
                "Policy-abhängige Stichprobe, kein Beweis optimalen Spiels; "
                + "Intervalle beschreiben nur Monte-Carlo-Unsicherheit. PASS ohne Gebot: kein Spiel, 0 ct.");
    }

    private Estimate evaluate(DecisionInformation information, GameRecord.ContractSpec contract,
                              List<List<List<Card>>> worlds, double[] weights, Card action, String label) {
        List<Outcome> outcomes = new ArrayList<>();
        for (var world : worlds) {
            List<Outcome> policyOutcomes = new ArrayList<>();
            for (PolicyGenome policy : opponents) {
                SchafkopfGame game = sampler.replay(information, contract, world, new SimpleBot(rules, true, policy));
                if (action != null) {
                    game.playCard(action);
                }
                while (game.phase() == Phase.TRICK_PLAY) {
                    game.playBotTurn();
                }
                policyOutcomes.add(outcome(game, information.observerSeat()));
            }
            outcomes.add(mean(policyOutcomes));
        }
        double total = java.util.Arrays.stream(weights).sum();
        double squares = java.util.Arrays.stream(weights).map(weight -> weight * weight).sum();
        double effective = total * total / squares;
        double probability = weighted(outcomes, weights, Outcome::won) / total;
        double cents = weighted(outcomes, weights, Outcome::cents) / total;
        double variance = 0;
        for (int index = 0; index < outcomes.size(); index++) {
            variance += weights[index] * Math.pow(outcomes.get(index).cents() - cents, 2);
        }
        double error = effective > 1 ? Math.sqrt(variance / total / (effective - 1)) : Double.POSITIVE_INFINITY;
        double margin = 1.96 * Math.sqrt((probability * (1 - probability) + 1 / effective) / effective);
        return new Estimate(label, probability, cents, error,
                Math.max(0, probability - margin), Math.min(1, probability + margin),
                weighted(outcomes, weights, Outcome::opponentEyes) / total,
                weighted(outcomes, weights, Outcome::opponentTricks) / total, outcomes.size(), effective);
    }

    private double weighted(List<Outcome> outcomes, double[] weights,
                              java.util.function.ToDoubleFunction<Outcome> value) {
        double sum = 0;
        for (int index = 0; index < outcomes.size(); index++) {
            sum += weights[index] * value.applyAsDouble(outcomes.get(index));
        }
        return sum;
    }

    private Outcome mean(List<Outcome> outcomes) {
        return new Outcome(outcomes.stream().mapToDouble(Outcome::won).average().orElseThrow(),
                outcomes.stream().mapToDouble(Outcome::cents).average().orElseThrow(),
                outcomes.stream().mapToDouble(Outcome::opponentEyes).average().orElseThrow(),
                outcomes.stream().mapToDouble(Outcome::opponentTricks).average().orElseThrow());
    }

    private Outcome outcome(SchafkopfGame game, int observer) {
        var result = game.result();
        var player = game.players().get(observer);
        boolean callerSide = result.callerTeam().contains(player);
        boolean won = callerSide == result.callerTeamWon();
        var otherTeam = callerSide ? result.defenderTeam() : result.callerTeam();
        int payment = new Tariff(game.config()).calculatePayment(game).totalCentsPerPlayer();
        double cents = won ? payment * otherTeam.size() : -payment * otherTeam.size();
        return new Outcome(won ? 1 : 0, cents,
                otherTeam.stream().mapToInt(schafkopf.model.Player::totalAugen).sum(),
                otherTeam.stream().mapToInt(member -> member.wonTricks().size()).sum());
    }

    double[] weights(DecisionInformation information, List<List<List<Card>>> worlds) {
        double[] weights = new double[worlds.size()];
        for (int index = 0; index < worlds.size(); index++) {
            double likelihood = 0;
            for (PolicyGenome policy : opponents) {
                likelihood += likelihood(information, worlds.get(index), policy);
            }
            weights[index] = likelihood / opponents.size();
        }
        return weights;
    }

    private double likelihood(DecisionInformation information, List<List<Card>> world, PolicyGenome policy) {
        DecisionInformation start = new DecisionInformation(information.playerNames(), information.observerSeat(),
                information.initialHand(), List.of(), information.contract(), information.leaderSeat(),
                information.config(), information.multiplier());
        SchafkopfGame game = sampler.replay(start, information.contract(), world, new SimpleBot(rules, true, policy));
        double logLikelihood = 0;
        for (GameRecord.Play observed : information.publicPlays()) {
            int legal = game.legalMovesForCurrentPlayer().size();
            if (observed.seat() != information.observerSeat() && legal > 1) {
                Card expected = game.previewBotTurn().card();
                logLikelihood += Math.log(expected.equals(observed.card()) ? 0.8 : 0.2 / (legal - 1));
            }
            game.playCard(observed.card());
        }
        return Math.exp(logLikelihood);
    }
}