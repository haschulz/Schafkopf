package schafkopf.analysis;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class FarbWenzSoloistValidator {
    private static final double Z_95 = 1.96;

    public record SuitResult(
            int pairedGames,
            double candidateAverageNetCents,
            double comparisonAverageNetCents,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents,
            int dealsImproved
    ) {
    }

    public record ValidationResult(
            String candidateId,
            String comparisonId,
            int independentDeals,
            int pairedGames,
            double candidateAverageNetCents,
            double comparisonAverageNetCents,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents,
            int dealsImproved,
            Map<Suit, SuitResult> byTrumpSuit,
            long holdoutSeed
    ) {
        public ValidationResult {
            byTrumpSuit = Map.copyOf(byTrumpSuit);
        }

        public boolean evidenceOfImprovement() {
            return confidenceIntervalLowCents > 0.0;
        }
    }

    private final PolicyTournament tournament;

    public FarbWenzSoloistValidator(SchafkopfConfig config) {
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

        List<PolicyGenome> treatment = population(candidate, "treatment");
        List<PolicyGenome> control = population(comparison, "control");
        List<Double> deltaByDeal = new ArrayList<>(independentDeals);
        Map<Suit, List<Double>> deltaBySuitAndDeal = new TreeMap<>();
        Map<Suit, Long> candidateCentsBySuit = new TreeMap<>();
        Map<Suit, Long> comparisonCentsBySuit = new TreeMap<>();
        long candidateCents = 0;
        long comparisonCents = 0;
        int improvedDeals = 0;

        for (int deal = 0; deal < independentDeals; deal++) {
            long dealSeed = seed + deal;
            long dealCandidateCents = 0;
            long dealComparisonCents = 0;
            for (Suit suit : Suit.values()) {
                long suitCandidateCents = 0;
                long suitComparisonCents = 0;
                for (int callerSeat = 0; callerSeat < 4; callerSeat++) {
                    int startingSeat = Math.floorMod(deal + suit.ordinal() + callerSeat, 4);
                    List<Integer> assignments = assignmentsWithCaller(callerSeat);
                        Map<Integer, Long> treatmentReturns = tournament.runFixedFarbWenzMatch(
                            treatment, assignments, callerSeat, suit, startingSeat, dealSeed).netCentsByGenome();
                        Map<Integer, Long> controlReturns = tournament.runFixedFarbWenzMatch(
                            control, assignments, callerSeat, suit, startingSeat, dealSeed).netCentsByGenome();
                    suitCandidateCents += treatmentReturns.get(0);
                    suitComparisonCents += controlReturns.get(0);
                }
                dealCandidateCents += suitCandidateCents;
                dealComparisonCents += suitComparisonCents;
                candidateCentsBySuit.merge(suit, suitCandidateCents, Long::sum);
                comparisonCentsBySuit.merge(suit, suitComparisonCents, Long::sum);
                deltaBySuitAndDeal.computeIfAbsent(suit, ignored -> new ArrayList<>())
                        .add((suitCandidateCents - suitComparisonCents) / 4.0);
            }
            double dealDelta = (dealCandidateCents - dealComparisonCents) / 16.0;
            deltaByDeal.add(dealDelta);
            candidateCents += dealCandidateCents;
            comparisonCents += dealComparisonCents;
            if (dealDelta > 0.0) improvedDeals++;
        }

        int pairedGames = independentDeals * 16;
        Map<Suit, SuitResult> suitResults = new TreeMap<>();
        for (Suit suit : Suit.values()) {
            List<Double> values = deltaBySuitAndDeal.get(suit);
            double mean = mean(values);
            double[] interval = confidenceInterval(values, mean);
            int suitGames = independentDeals * 4;
            long suitCandidateCents = candidateCentsBySuit.getOrDefault(suit, 0L);
            long suitComparisonCents = comparisonCentsBySuit.getOrDefault(suit, 0L);
            suitResults.put(suit, new SuitResult(
                    suitGames,
                    (double) suitCandidateCents / suitGames,
                    (double) suitComparisonCents / suitGames,
                    mean,
                    interval[0],
                    interval[1],
                    (int) values.stream().filter(value -> value > 0.0).count()));
        }

        double mean = mean(deltaByDeal);
        double[] interval = confidenceInterval(deltaByDeal, mean);
        return new ValidationResult(
                candidate.id(), comparison.id(), independentDeals, pairedGames,
                (double) candidateCents / pairedGames,
                (double) comparisonCents / pairedGames,
                mean, interval[0], interval[1], improvedDeals, suitResults, seed);
    }

    private List<PolicyGenome> population(PolicyGenome focal, String label) {
        List<PolicyGenome> population = new ArrayList<>(4);
        population.add(focal);
        population.addAll(ValidationOpponentPool.controls("fixed-farbwenz-" + label));
        return List.copyOf(population);
    }

    private List<Integer> assignmentsWithCaller(int callerSeat) {
        List<Integer> assignment = new ArrayList<>(List.of(-1, -1, -1, -1));
        int controlIndex = 1;
        for (int seat = 0; seat < 4; seat++) {
            assignment.set(seat, seat == callerSeat ? 0 : controlIndex++);
        }
        return assignment;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double[] confidenceInterval(List<Double> independentValues, double mean) {
        double variance = independentValues.stream()
                .mapToDouble(value -> (value - mean) * (value - mean))
                .sum() / (independentValues.size() - 1);
        double margin = Z_95 * Math.sqrt(variance / independentValues.size());
        return new double[] {mean - margin, mean + margin};
    }
}