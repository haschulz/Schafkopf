package schafkopf.analysis;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyTournamentTest {
    @Test
    void seededTournamentRotatesSeatsAndReturnsBalancedReproducibleFitness() {
        List<PolicyGenome> population = List.of(
                genome("agent-a"), genome("agent-b"), genome("agent-c"), genome("agent-d"));
        List<DecisionTrace> traces = new ArrayList<>();
        PolicyTournament tournament = new PolicyTournament(schafkopf.config.SchafkopfConfig.defaults(), traces::add);

        PolicyTournament.TournamentResult first = tournament.run(population, 1, 5678L);
        PolicyTournament.TournamentResult second = tournament.run(population, 1, 5678L);

        assertEquals(first, second);
        assertEquals(16, first.candidates().get(0).gamesPlayed());
        assertEquals(List.of(4, 4, 4, 4), first.candidates().get(0).gamesBySeat());
        assertEquals(List.of(4, 4, 4, 4), first.candidates().get(0).gamesByStartingSeat());
        assertEquals(0, first.candidates().stream().mapToLong(PolicyTournament.CandidateFitness::netCents).sum());
        assertTrue(traces.stream().anyMatch(trace -> trace.stage() == DecisionTrace.Stage.KLOPFEN));
        assertTrue(traces.stream().anyMatch(trace -> trace.stage() == DecisionTrace.Stage.BIDDING));
        assertTrue(traces.stream().anyMatch(trace -> trace.stage() == DecisionTrace.Stage.CARD_PLAY));
    }

    @Test
    void geneticAlgorithmCanUseFullGameTournamentFitness() {
        List<PolicyGenome> population = List.of(
                genome("agent-a"), genome("agent-b"), genome("agent-c"), genome("agent-d"));

        GeneticAlgorithm.EvolutionResult result = new PolicyTournament().evolve(
                population, 1, 4, 1, 0.01, 1, 9876L);

        assertEquals(8, result.evaluationCount());
        assertEquals(2, result.bestFitnessByGeneration().size());
    }

        @Test
        void consecutiveFitnessEvaluationsUseDisjointDealSeedBlocks() {
                assertEquals(500L, PolicyTournament.seedForEvaluationBatch(500L, 0, 100));
                assertEquals(600L, PolicyTournament.seedForEvaluationBatch(500L, 1, 100));
                assertEquals(700L, PolicyTournament.seedForEvaluationBatch(500L, 2, 100));
        }

    @Test
    void riskAdjustedFitnessCanPreferStablePolicyOverHighMeanSoloist() {
        PolicyTournament.CandidateFitness stable = candidateFitness(
                "stable", 5.0, -10.0, -10.0);
        PolicyTournament.CandidateFitness riskySoloist = candidateFitness(
                "risky-solo", 10.0, -100.0, -200.0);

        assertTrue(stable.fitnessValue() > riskySoloist.fitnessValue());
        assertEquals(5.0 - 1.0 - 5.0, stable.fitnessValue());
        assertEquals(10.0 - 10.0 - 100.0, riskySoloist.fitnessValue());
    }

        @Test
        void farbwenzSoloistLossesReduceEvolutionFitness() {
                PolicyTournament.CandidateFitness safe = candidateFitness(
                                "safe", 5.0, -10.0, 20.0);
                PolicyTournament.CandidateFitness losingFarbwenz = new PolicyTournament.CandidateFitness(
                                "losing-farbwenz", 100, 500, 5.0, -10.0,
                                Map.of("FARBWENZ/SOLOIST", new PolicyTournament.RoleFitness(20, -40.0, -60.0)),
                                List.of(25, 25, 25, 25), List.of(25, 25, 25, 25));

                assertTrue(safe.fitnessValue() > losingFarbwenz.fitnessValue());
        }

    private PolicyGenome genome(String id) {
        return new PolicyGenome(PolicyGenome.CURRENT_SCHEMA_VERSION, id, PolicyGenome.baseline().weights());
    }

    private PolicyTournament.CandidateFitness candidateFitness(
            String id,
            double average,
            double overallTail,
            double soloistTail
    ) {
        return new PolicyTournament.CandidateFitness(
                id,
                100,
                Math.round(average * 100),
                average,
                overallTail,
                Map.of("FARBSOLO/SOLOIST", new PolicyTournament.RoleFitness(10, -20.0, soloistTail)),
                List.of(25, 25, 25, 25),
                List.of(25, 25, 25, 25));
    }
}