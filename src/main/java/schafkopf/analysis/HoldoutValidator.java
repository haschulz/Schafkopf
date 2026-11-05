package schafkopf.analysis;

import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class HoldoutValidator {
    private static final double Z_95 = 1.96;

    public record GroupPerformance(
            int games,
            double averageNetCents,
            double medianNetCents,
            double tenthPercentileNetCents,
            double worstDecileAverageNetCents,
            double positiveReturnRate
    ) {
    }

    public record ValidationResult(
            String candidateId,
            String comparisonPolicyId,
            int independentDeals,
            int pairedGames,
            double candidateAverageNetCents,
            double baselineAverageNetCents,
            double meanPairedDeltaCents,
            double confidenceIntervalLowCents,
            double confidenceIntervalHighCents,
            double standardDeviationPairedDeltaCents,
            double medianPairedDeltaCents,
            double tenthPercentilePairedDeltaCents,
            double worstDecileAveragePairedDeltaCents,
            int dealsImproved,
            Map<String, GroupPerformance> byGameTypeAndRole,
            long holdoutSeed
    ) {
        public ValidationResult {
            byGameTypeAndRole = Collections.unmodifiableMap(new LinkedHashMap<>(byGameTypeAndRole));
        }

        public boolean evidenceOfImprovement() {
            return confidenceIntervalLowCents > 0.0;
        }

        public boolean evidenceOfRegression() {
            return confidenceIntervalHighCents < 0.0;
        }
    }

    private final PolicyTournament tournament;

    public HoldoutValidator(SchafkopfConfig config) {
        this.tournament = new PolicyTournament(config);
    }

    public ValidationResult validate(PolicyGenome candidate, int independentDeals, long holdoutSeed) {
        return validateAgainst(candidate, PolicyGenome.baseline().withId("holdout-baseline-reference"),
                independentDeals, holdoutSeed);
    }

    public ValidationResult validateAgainst(
            PolicyGenome candidate,
            PolicyGenome comparisonPolicy,
            int independentDeals,
            long holdoutSeed
    ) {
        if (independentDeals < 2) {
            throw new IllegalArgumentException("At least two independent deals are required for an interval");
        }
        if (candidate == null || comparisonPolicy == null) {
            throw new IllegalArgumentException("Candidate and comparison policies are required");
        }

        List<PolicyGenome> controls = ValidationOpponentPool.controls("holdout");
        List<PolicyGenome> treatmentPopulation = new ArrayList<>(4);
        treatmentPopulation.add(candidate);
        treatmentPopulation.addAll(controls);
        List<PolicyGenome> controlPopulation = new ArrayList<>(4);
        controlPopulation.add(comparisonPolicy);
        controlPopulation.addAll(controls);

        List<Double> pairedDeltaByDeal = new ArrayList<>(independentDeals);
        Map<String, List<Double>> candidateNetByStratum = new TreeMap<>();
        long candidateCents = 0;
        long baselineCents = 0;
        int dealsImproved = 0;

        for (int deal = 0; deal < independentDeals; deal++) {
            long dealSeed = holdoutSeed + deal;
            long dealCandidateCents = 0;
            long dealBaselineCents = 0;
                int startingSeat = deal % 4;
            for (int focalSeat = 0; focalSeat < 4; focalSeat++) {
                List<Integer> treatmentSeats = treatmentSeats(focalSeat);
                PolicyTournament.MatchResult treatment = tournament.runMatchDetailed(
                    treatmentPopulation, treatmentSeats, startingSeat, dealSeed);
                Map<Integer, Long> controlReturns = tournament.runMatch(
                    controlPopulation, treatmentSeats, startingSeat, dealSeed);
                long candidateReturn = treatment.netCentsByGenome().get(0);
                dealCandidateCents += candidateReturn;
                dealBaselineCents += controlReturns.get(0);
                PolicyTournament.PlayerRole role = treatment.rolesByGenome().get(0);
                String stratum = treatment.gameType().name() + "/" + role.name();
                candidateNetByStratum.computeIfAbsent(stratum, ignored -> new ArrayList<>())
                    .add((double) candidateReturn);
            }
            candidateCents += dealCandidateCents;
            baselineCents += dealBaselineCents;
            double pairedDelta = (dealCandidateCents - dealBaselineCents) / 4.0;
            pairedDeltaByDeal.add(pairedDelta);
            if (pairedDelta > 0.0) {
                dealsImproved++;
            }
        }

        double meanDelta = pairedDeltaByDeal.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSquaredDifferences = pairedDeltaByDeal.stream()
                .mapToDouble(delta -> (delta - meanDelta) * (delta - meanDelta))
                .sum();
        double sampleVariance = sumSquaredDifferences / (independentDeals - 1);
        double standardDeviation = Math.sqrt(sampleVariance);
        double standardError = Math.sqrt(sampleVariance / independentDeals);
        double intervalMargin = Z_95 * standardError;
        int pairedGames = independentDeals * 4;
        Map<String, GroupPerformance> groupPerformance = new LinkedHashMap<>();
        candidateNetByStratum.forEach((key, returns) -> groupPerformance.put(key, summarize(returns)));
        List<Double> sortedDealDeltas = sorted(pairedDeltaByDeal);
        return new ValidationResult(
                candidate.id(),
            comparisonPolicy.id(),
                independentDeals,
                pairedGames,
                (double) candidateCents / pairedGames,
                (double) baselineCents / pairedGames,
                meanDelta,
                meanDelta - intervalMargin,
                meanDelta + intervalMargin,
                standardDeviation,
                median(sortedDealDeltas),
                percentile(sortedDealDeltas, 0.10),
                worstDecileAverage(sortedDealDeltas),
                dealsImproved,
                groupPerformance,
                holdoutSeed);
    }

    private GroupPerformance summarize(List<Double> values) {
        List<Double> ordered = sorted(values);
        long positive = values.stream().filter(value -> value > 0.0).count();
        return new GroupPerformance(
                values.size(),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
                median(ordered),
                percentile(ordered, 0.10),
                worstDecileAverage(ordered),
                (double) positive / values.size());
    }

    private List<Double> sorted(List<Double> values) {
        return values.stream().sorted().toList();
    }

    private double median(List<Double> sortedValues) {
        int size = sortedValues.size();
        if (size % 2 == 1) {
            return sortedValues.get(size / 2);
        }
        return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
    }

    private double percentile(List<Double> sortedValues, double quantile) {
        int index = Math.max(0, (int) Math.ceil(quantile * sortedValues.size()) - 1);
        return sortedValues.get(index);
    }

    private double worstDecileAverage(List<Double> sortedValues) {
        int tailSize = Math.max(1, (int) Math.ceil(sortedValues.size() * 0.10));
        return sortedValues.subList(0, tailSize).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private List<Integer> treatmentSeats(int focalSeat) {
        List<Integer> assignments = new ArrayList<>(List.of(-1, -1, -1, -1));
        int nextBaseline = 1;
        for (int seat = 0; seat < 4; seat++) {
            assignments.set(seat, seat == focalSeat ? 0 : nextBaseline++);
        }
        return assignments;
    }

}