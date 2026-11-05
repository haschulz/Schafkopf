package schafkopf.analysis;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.GameType;
import schafkopf.model.Suit;
import schafkopf.analysis.PolicyTournament.PlayerRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ContractMatrixValidator {
    private static final double Z_95 = 1.96;

    public record ContractResult(
            String contract,
            int pairedGames,
            double candidateAverageNetCents,
            double comparisonAverageNetCents,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents
    ) {
    }

        public record RoleResult(
            int pairedGames,
            int independentDeals,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents
        ) {
        }

    public record ValidationResult(
            String candidateId,
            String comparisonId,
            int independentDeals,
            int pairedGames,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents,
            List<ContractResult> contracts,
            Map<String, RoleResult> byRole,
            long seed
    ) {
        public ValidationResult {
            contracts = List.copyOf(contracts);
            byRole = Collections.unmodifiableMap(new LinkedHashMap<>(byRole));
        }

        public boolean evidenceOfImprovement() {
            return confidenceIntervalLowCents > 0.0;
        }
    }

    private record ContractSpec(GameType gameType, Suit suit) {
        private String label() {
            return suit == null ? gameType.name() : gameType.name() + ":" + suit.name();
        }
    }

    private record RoleScenario(int callerSeat, int focalSeat, PlayerRole role) {
    }

    private final PolicyTournament tournament;

    public ContractMatrixValidator(SchafkopfConfig config) {
        tournament = new PolicyTournament(config);
    }

    public ValidationResult validate(
            PolicyGenome candidate,
            PolicyGenome comparison,
            int independentDeals,
            long seed
    ) {
        if (candidate == null || comparison == null) {
            throw new IllegalArgumentException("Candidate and comparison policies are required");
        }
        if (independentDeals < 2) {
            throw new IllegalArgumentException("At least two independent deals are required for an interval");
        }

        List<ContractSpec> contracts = contracts();
        List<PolicyGenome> controls = ValidationOpponentPool.controls("matrix");
        List<PolicyGenome> treatment = population(candidate, controls);
        List<PolicyGenome> comparisonPopulation = population(comparison, controls);
        List<List<Double>> deltasByContract = new ArrayList<>(contracts.size());
        List<Long> candidateTotals = new ArrayList<>(contracts.size());
        List<Long> comparisonTotals = new ArrayList<>(contracts.size());
        List<Double> overallDealDeltas = new ArrayList<>(independentDeals);
        Map<String, List<List<Double>>> roleDeltasByDeal = new TreeMap<>();

        for (int contractIndex = 0; contractIndex < contracts.size(); contractIndex++) {
            ContractSpec contract = contracts.get(contractIndex);
            List<Double> contractDealDeltas = new ArrayList<>(independentDeals);
            List<RoleScenario> scenarios = scenarios(contract);
            long candidateTotal = 0;
            long comparisonTotal = 0;
            for (int deal = 0; deal < independentDeals; deal++) {
                long dealCandidate = 0;
                long dealComparison = 0;
                Map<PlayerRole, List<Double>> roleDeltasForDeal = new EnumMap<>(PlayerRole.class);
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                RoleScenario scenario = scenarios.get(scenarioIndex);
                int startingSeat = Math.floorMod(deal + scenario.focalSeat() + contractIndex, 4);
                long dealSeed = seed + (long) contractIndex * 1_000_000_000L + (long) deal * 100L
                    + scenarioIndex * 16L;
                List<Integer> assignments = assignmentsWithFocalSeat(scenario.focalSeat());
                PolicyTournament.MatchResult candidateMatch = tournament.runFixedContractMatch(
                    treatment, assignments, contract.gameType(), contract.suit(), scenario.callerSeat(),
                    scenario.focalSeat(), scenario.role(), startingSeat, dealSeed);
                PolicyTournament.MatchResult comparisonMatch = tournament.runFixedContractMatch(
                    comparisonPopulation, assignments, contract.gameType(), contract.suit(),
                    scenario.callerSeat(), scenario.focalSeat(), scenario.role(), startingSeat, dealSeed);
                        long candidateReturn = candidateMatch.netCentsByGenome().get(0);
                        long comparisonReturn = comparisonMatch.netCentsByGenome().get(0);
                    dealCandidate += candidateReturn;
                    dealComparison += comparisonReturn;
                        PlayerRole candidateRole = candidateMatch.rolesByGenome().get(0);
                        PlayerRole comparisonRole = comparisonMatch.rolesByGenome().get(0);
                        if (candidateRole == comparisonRole) {
                        String roleKey = contract.gameType().name() + "/" + candidateRole.name();
                        roleDeltasByDeal.computeIfAbsent(roleKey, ignored -> emptyDealSamples(independentDeals))
                            .get(deal).add((double) (candidateReturn - comparisonReturn));
                        roleDeltasForDeal.computeIfAbsent(candidateRole, ignored -> new ArrayList<>())
                                .add((double) (candidateReturn - comparisonReturn));
                        }
                }
                candidateTotal += dealCandidate;
                comparisonTotal += dealComparison;
                double balancedDelta = contract.gameType() == GameType.RAMSCH || roleDeltasForDeal.isEmpty()
                    ? (dealCandidate - dealComparison) / scenarios.size()
                    : mean(roleDeltasForDeal.values().stream().map(this::mean).toList());
                contractDealDeltas.add(balancedDelta);
            }
            deltasByContract.add(contractDealDeltas);
            candidateTotals.add(candidateTotal);
            comparisonTotals.add(comparisonTotal);
        }

        for (int deal = 0; deal < independentDeals; deal++) {
            double totalDelta = 0.0;
            for (List<Double> contractDeltas : deltasByContract) {
                totalDelta += contractDeltas.get(deal);
            }
            overallDealDeltas.add(totalDelta / contracts.size());
        }

        List<ContractResult> contractResults = new ArrayList<>(contracts.size());
        int totalPairedGames = 0;
        for (int index = 0; index < contracts.size(); index++) {
            List<Double> values = deltasByContract.get(index);
            double mean = mean(values);
            double[] interval = confidenceInterval(values, mean);
            int games = independentDeals * scenarios(contracts.get(index)).size();
            totalPairedGames += games;
            contractResults.add(new ContractResult(contracts.get(index).label(), games,
                    (double) candidateTotals.get(index) / games,
                    (double) comparisonTotals.get(index) / games,
                    mean, interval[0], interval[1]));
        }
        Map<String, RoleResult> roleResults = summarizeRoles(roleDeltasByDeal);
        double mean = mean(overallDealDeltas);
        double[] interval = confidenceInterval(overallDealDeltas, mean);
        return new ValidationResult(candidate.id(), comparison.id(), independentDeals,
            totalPairedGames, mean, interval[0], interval[1], contractResults,
                roleResults, seed);
    }

    private List<List<Double>> emptyDealSamples(int deals) {
        List<List<Double>> samples = new ArrayList<>(deals);
        for (int deal = 0; deal < deals; deal++) {
            samples.add(new ArrayList<>());
        }
        return samples;
    }

    private Map<String, RoleResult> summarizeRoles(Map<String, List<List<Double>>> roleDeltasByDeal) {
        Map<String, RoleResult> results = new LinkedHashMap<>();
        roleDeltasByDeal.forEach((role, valuesByDeal) -> {
            List<Double> dealMeans = valuesByDeal.stream()
                    .filter(values -> !values.isEmpty())
                    .map(this::mean)
                    .toList();
            int pairedGames = valuesByDeal.stream().mapToInt(List::size).sum();
            double mean = mean(dealMeans);
            double[] interval = dealMeans.size() >= 2
                    ? confidenceInterval(dealMeans, mean)
                    : new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
            results.put(role, new RoleResult(pairedGames, dealMeans.size(), mean, interval[0], interval[1]));
        });
        return results;
    }

    private List<ContractSpec> contracts() {
        List<ContractSpec> result = new ArrayList<>();
        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            result.add(new ContractSpec(GameType.RUFSPIEL, suit));
        }
        for (Suit suit : Suit.values()) {
            result.add(new ContractSpec(GameType.FARBSOLO, suit));
        }
        result.add(new ContractSpec(GameType.WENZ, null));
        result.add(new ContractSpec(GameType.GEIER, null));
        for (Suit suit : Suit.values()) {
            result.add(new ContractSpec(GameType.FARBWENZ, suit));
        }
        result.add(new ContractSpec(GameType.BETTEL, null));
        result.add(new ContractSpec(GameType.RAMSCH, null));
        result.add(new ContractSpec(GameType.KREUZBOCK, null));
        return List.copyOf(result);
    }

    private List<RoleScenario> scenarios(ContractSpec contract) {
        List<RoleScenario> result = new ArrayList<>();
        if (contract.gameType() == GameType.RUFSPIEL) {
            for (int callerSeat = 0; callerSeat < 4; callerSeat++) {
                int otherSeat = (callerSeat + 1) % 4;
                result.add(new RoleScenario(callerSeat, callerSeat, PlayerRole.RUFSPIEL_CALLER));
                result.add(new RoleScenario(callerSeat, otherSeat, PlayerRole.RUFSPIEL_PARTNER));
                result.add(new RoleScenario(callerSeat, otherSeat, PlayerRole.DEFENDER));
            }
        } else if (isSoloContract(contract.gameType())) {
            for (int callerSeat = 0; callerSeat < 4; callerSeat++) {
                result.add(new RoleScenario(callerSeat, callerSeat, PlayerRole.SOLOIST));
                for (int offset = 1; offset < 4; offset++) {
                    result.add(new RoleScenario(callerSeat, (callerSeat + offset) % 4, PlayerRole.DEFENDER));
                }
            }
        } else if (contract.gameType() == GameType.KREUZBOCK) {
            for (int focalSeat = 0; focalSeat < 4; focalSeat++) {
                PlayerRole role = focalSeat % 2 == 0 ? PlayerRole.KREUZBOCK_TEAM_A
                        : PlayerRole.KREUZBOCK_TEAM_B;
                result.add(new RoleScenario(0, focalSeat, role));
            }
        } else {
            for (int focalSeat = 0; focalSeat < 4; focalSeat++) {
                result.add(new RoleScenario(0, focalSeat, null));
            }
        }
        return List.copyOf(result);
    }

    private boolean isSoloContract(GameType gameType) {
        return gameType == GameType.FARBSOLO || gameType == GameType.WENZ || gameType == GameType.GEIER
                || gameType == GameType.FARBWENZ || gameType == GameType.BETTEL;
    }

    private List<PolicyGenome> population(PolicyGenome focal, List<PolicyGenome> controls) {
        List<PolicyGenome> result = new ArrayList<>(4);
        result.add(focal);
        result.addAll(controls);
        return List.copyOf(result);
    }

    private List<Integer> assignmentsWithFocalSeat(int focalSeat) {
        List<Integer> assignments = new ArrayList<>(List.of(-1, -1, -1, -1));
        int nextOpponent = 1;
        for (int seat = 0; seat < 4; seat++) {
            assignments.set(seat, seat == focalSeat ? 0 : nextOpponent++);
        }
        return assignments;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double[] confidenceInterval(List<Double> values, double mean) {
        double variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum()
                / (values.size() - 1);
        double margin = Z_95 * Math.sqrt(variance / values.size());
        return new double[] {mean - margin, mean + margin};
    }
}