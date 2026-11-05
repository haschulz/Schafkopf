package schafkopf;

import schafkopf.analysis.BatchSimulator;
import schafkopf.analysis.ContractValueCalibrator;
import schafkopf.analysis.ContractMatrixOptimizer;
import schafkopf.analysis.ContractMatrixValidator;
import schafkopf.analysis.RoleSpecialistComposer;
import schafkopf.analysis.FarbWenzSoloistValidator;
import schafkopf.analysis.FarbWenzSoloistOptimizer;
import schafkopf.analysis.HoldoutValidator;
import schafkopf.analysis.PolicyTournament;
import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.DecisionTraceWriter;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.debate.DebateEngine;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;
import schafkopf.model.Player;
import schafkopf.rules.RuleEngine;
import schafkopf.state.SchafkopfGame;
import schafkopf.training.InteractiveTrainingGame;

import java.util.List;
import java.nio.file.Path;

public class SchafkopfApp {
    public static void main(String[] args) {
        SchafkopfConfig config = SchafkopfConfig.load();
        if (args.length > 0 && args[0].equalsIgnoreCase("evolve")) {
            runEvolution(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("validate")) {
            runHoldoutValidation(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("calibrate")) {
            runContractCalibration(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("validate-farbwenz")) {
            runFarbWenzValidation(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("evolve-farbwenz")) {
            runFarbWenzEvolution(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("evolve-matrix")) {
            runMatrixEvolution(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("validate-matrix")) {
            runMatrixValidation(args, config);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("compose-matrix")) {
            runMatrixComposition(args, config);
            return;
        }
        // Interaktiver Trainingsmodus ("./run.sh play")
        if (args.length > 0 && args[0].equalsIgnoreCase("play")) {
            ScoreLedger ledger = new ScoreLedger(List.of(
                    new Player("1", "Spieler 1 (Sepp)"),
                    new Player("2", "Spieler 2 (Hans)"),
                    new Player("3", "Spieler 3 (Franz)"),
                    new Player("4", "Spieler 4 (Du)")
            ), new Tariff(config));
            InteractiveTrainingGame training = new InteractiveTrainingGame(System.in, config);
            training.startInteractiveSession(ledger);
            return;
        }

        System.out.println("==========================================================================");
        System.out.println("                 SCHAFKOPF MULTI-AGENTEN & TAKTIK-SIMULATOR               ");
        System.out.println("==========================================================================\n");

        // ---------------------------------------------------------
        // TEIL 1: Eine vollständige Partie mit detailliertem Taktik-Log
        // ---------------------------------------------------------
        System.out.println(">>> TEIL 1: LIVE-PARTIE MIT DEN NEUEN TAKTIK-REGELN <<<\n");
        Player p1 = new Player("1", "Spieler 1 (Sepp, Rufer)");
        Player p2 = new Player("2", "Spieler 2 (Hans, Gegner)");
        Player p3 = new Player("3", "Spieler 3 (Franz, Mitspieler)");
        Player p4 = new Player("4", "Spieler 4 (Michl, Gegner)");
        List<Player> players = List.of(p1, p2, p3, p4);

        RuleEngine rules = new RuleEngine();
        SchafkopfGame singleGame = new SchafkopfGame(players, rules, new SimpleBot(rules), config);
        singleGame.dealRandom();
        singleGame.autoDetermineRufspiel();

        singleGame.playFullGame(p1);

        // ---------------------------------------------------------
        // TEIL 2: Die 4-Agenten-Manöverkritik (Stammtisch-Debatte)
        // ---------------------------------------------------------
        System.out.println("\n>>> TEIL 2: DIE 4 AGENTEN DISKUTIEREN AM STAMMTISCH <<<");
        if (hasArgument(args, "--debate")) {
            DebateEngine debate = new DebateEngine();
            debate.conductPostGameDebate(singleGame);
        }

        // ---------------------------------------------------------
        // TEIL 3: A/B-Benchmark: Baseline vs. Gedächtnis-Bot auf identischen Deals
        // ---------------------------------------------------------
        System.out.println("\n>>> TEIL 3: A/B-BENCHMARK (1.000 PARTIEN AUF IDENTISCHEN DEALS) <<<\n");
        BatchSimulator batchSimulator = new BatchSimulator();
        var abResults = batchSimulator.runABBenchmark(1000);
        batchSimulator.printABBenchmarkReport(abResults);

        // ---------------------------------------------------------
        // TEIL 4: Dynamische Konvergenz-Simulation (bis zu 5.000 Spiele)
        // ---------------------------------------------------------
        System.out.println(">>> TEIL 4: DYNAMISCHE KONVERGENZ-BATTERIE (REGEL-FESTIGUNG) <<<\n");
        var convergence = batchSimulator.runBatchWithDynamicConvergence(5000, 250, 0.30);
        batchSimulator.printConvergenceReport(convergence, 0.30);
        batchSimulator.printAnalysis(convergence.records());
    }

    private static void runEvolution(String[] args, SchafkopfConfig config) {
        int generations = intArgument(args, 1, 2);
        int populationSize = intArgument(args, 2, 4);
        int gamesPerSeat = intArgument(args, 3, 1);
        long seed = longArgument(args, 4, 20261101L);
        if (populationSize < 4) {
            throw new IllegalArgumentException("Evolution population must contain at least four genomes");
        }

        PolicyGenome baseline = PolicyGenome.loadConfigured();
        List<PolicyGenome> seedGenomes = List.of(
                baseline.withId("baseline"),
                baseline.withId("bid-conservative").withWeight("bid.handScore", 1.1),
                baseline.withId("bid-aggressive").withWeight("bid.handScore", 0.9),
            baseline.withId("smear-points").withWeight("card.RUFSPIEL.teammatePoints", 0.25),
            baseline.withId("solo-control")
                .withWeight("bid.FARBSOLO.trumpControl", 1.6)
                .withWeight("bid.FARBWENZ.trumpControl", 1.8)
                .withWeight("bid.FARBSOLO.longSideSuitsWithoutAce", -1.2)
                .withWeight("bid.FARBWENZ.longSideSuitsWithoutAce", -1.2),
            baseline.withId("solo-conservative")
                .withWeight("bid.FARBSOLO.threshold", 11.0)
                .withWeight("bid.FARBWENZ.threshold", 11.0)
                .withWeight("bid.WENZ.threshold", 10.0),
            baseline.withId("solo-opportunity")
                .withWeight("bid.FARBSOLO.sideSuitWinners", 1.2)
                .withWeight("bid.FARBWENZ.sideSuitWinners", 1.2)
                .withWeight("bid.FARBSOLO.voidSideSuits", 0.8)
                .withWeight("bid.FARBWENZ.voidSideSuits", 0.8),
            baseline.withId("bettel-safe")
                .withWeight("bid.BETTEL.highCards", -2.0)
                .withWeight("bid.BETTEL.bettelHighCards", -2.5)
                .withWeight("bid.BETTEL.bettelTopCards", -5.0)
                .withWeight("bid.BETTEL.bettelLowCards", 2.0)
                .withWeight("bid.BETTEL.threshold", 12.0),
            baseline.withId("solo-expected-value")
                .withWeight("bid.FARBSOLO.expectedValueCents", 0.04)
                .withWeight("bid.FARBWENZ.expectedValueCents", 0.04)
                .withWeight("bid.WENZ.expectedValueCents", 0.04)
                .withWeight("bid.BETTEL.expectedValueCents", 0.04),
            baseline.withId("farbwenz-trump-suit-control")
                .withWeight("bid.FARBWENZ.chosenSuitTrumpHighCards", 2.5)
                .withWeight("bid.FARBWENZ.chosenSuitTrumpCount", 1.5)
                .withWeight("bid.FARBWENZ.trumpControl", 1.5)
                .withWeight("bid.FARBWENZ.unterCount", 3.0)
                .withWeight("bid.FARBWENZ.longSideSuitsWithoutAce", -1.2)
                .withWeight("bid.FARBWENZ.threshold", 15.0));
        List<PolicyGenome> initialPopulation = seedGenomes.subList(0, Math.min(populationSize, seedGenomes.size()));

        GeneticAlgorithm.EvolutionResult result;
        Path tracePath = Path.of("config", "traces", "evolution-" + seed + ".jsonl");
        try (DecisionTraceWriter traceWriter = new DecisionTraceWriter(tracePath)) {
            PolicyTournament tournament = new PolicyTournament(config, traceWriter);
            result = tournament.evolve(initialPopulation, generations, populationSize, 1, 0.2, gamesPerSeat, seed);
        }
        Path championPath = Path.of("config", "genomes", "champion-" + seed + ".properties");
        result.champion().save(championPath);

        System.out.printf("Evolution abgeschlossen: %d Bewertungen, Champion %s, risikoadjustierte Fitness %.2f ct/Spiel%n",
                result.evaluationCount(), result.champion().id(), result.championFitness());
        System.out.println("Beste Fitness je Generation: " + result.bestFitnessByGeneration());
        System.out.println("Champion gespeichert: " + championPath);
        System.out.println("Entscheidungen protokolliert: " + tracePath);
        System.out.println("Zum Testen: java -Dschafkopf.genome=" + championPath + " -cp bin schafkopf.SchafkopfApp play");
    }

        private static void runHoldoutValidation(String[] args, SchafkopfConfig config) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                "Usage: ./run.sh validate <genome.properties> [deals] [holdoutSeed] [trainingSeed]");
        }
        PolicyGenome candidate = PolicyGenome.load(Path.of(args[1]));
        int deals = intArgument(args, 2, 250);
        long holdoutSeed = longArgument(args, 3, 20261101L);
        if (args.length > 4 && holdoutSeed == longArgument(args, 4, Long.MIN_VALUE)) {
            throw new IllegalArgumentException("Holdout seed must differ from the training seed");
        }

        HoldoutValidator validator = new HoldoutValidator(config);
        HoldoutValidator.ValidationResult result;
        if (args.length > 5) {
            PolicyGenome comparison = PolicyGenome.load(Path.of(args[5]));
            result = validator.validateAgainst(candidate, comparison, deals, holdoutSeed);
        } else {
            result = validator.validate(candidate, deals, holdoutSeed);
        }
        System.out.printf("Holdout: %d unabhängige Deals, %d Sitzspiele je Policy%n",
            result.independentDeals(), result.pairedGames());
        System.out.printf("Kandidat %s: %.2f ct/Spiel | Vergleich %s: %.2f ct/Spiel%n",
            result.candidateId(), result.candidateAverageNetCents(),
            result.comparisonPolicyId(), result.baselineAverageNetCents());
        System.out.printf("Gepaarter Vorteil: %.2f ct/Spiel, 95%%-Intervall [%.2f, %.2f], Vorteil bei %d/%d Deals%n",
            result.meanPairedDeltaCents(), result.confidenceIntervalLowCents(),
            result.confidenceIntervalHighCents(), result.dealsImproved(), result.independentDeals());
        System.out.printf("Risiko: SD %.2f | Median %.2f | P10 %.2f | schlechteste 10%% im Mittel %.2f ct/Spiel%n",
            result.standardDeviationPairedDeltaCents(), result.medianPairedDeltaCents(),
            result.tenthPercentilePairedDeltaCents(), result.worstDecileAveragePairedDeltaCents());
        System.out.println("Nach Spielart/Rolle:");
        result.byGameTypeAndRole().forEach((group, stats) -> System.out.printf(
            "  %-32s n=%4d mean=%7.2f median=%7.2f P10=%7.2f CVaR10=%7.2f win=%5.1f%%%n",
            group, stats.games(), stats.averageNetCents(), stats.medianNetCents(),
            stats.tenthPercentileNetCents(), stats.worstDecileAverageNetCents(),
            stats.positiveReturnRate() * 100.0));
        String conclusion;
        if (result.evidenceOfImprovement()) {
            conclusion = "Evidenz für einen Vorteil auf diesem Holdout. Champion bleibt manuell zu übernehmen.";
        } else if (result.evidenceOfRegression()) {
            conclusion = "Evidenz für eine Verschlechterung. Champion nicht übernehmen.";
        } else {
            conclusion = "Kein belastbarer Unterschied nachgewiesen. Champion nicht übernehmen.";
        }
        System.out.println("Ergebnis: " + conclusion);
        }

    private static boolean hasArgument(String[] args, String expected) {
        for (String argument : args) {
            if (argument.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

        private static void runContractCalibration(String[] args, SchafkopfConfig config) {
        int deals = intArgument(args, 1, 1000);
        long seed = longArgument(args, 2, 314159L);
        double ridge = args.length > 3 ? Double.parseDouble(args[3]) : 10.0;
        ContractValueCalibrator.CalibrationResult result = new ContractValueCalibrator(config)
            .calibrate(deals, seed, ridge);
        Path modelPath = args.length > 4 ? Path.of(args[4])
            : Path.of("config", "contract-value-model.properties");
        result.model().save(modelPath);
        System.out.printf("Kalibrierung abgeschlossen: %d Rollouts aus %d Deals gegen %d Policies, Seed %d%n",
            result.rollouts(), result.dealsSampled(), result.opponentPolicyCount(), result.seed());
        result.samplesByGameType().forEach((gameType, count) ->
            System.out.printf("  %-10s %d Samples%n", gameType, count));
        System.out.println("Expected-Value-Modell gespeichert: " + modelPath);
        if (modelPath.equals(Path.of("config", "contract-value-model.properties"))) {
            System.out.println("Es wird bei der nächsten Ansage automatisch geladen.");
        } else {
            System.out.println("Separates Modell; zum Laden -Dschafkopf.valueModel=" + modelPath + " setzen.");
        }
        }

        private static void runFarbWenzValidation(String[] args, SchafkopfConfig config) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "Usage: ./run.sh validate-farbwenz <candidate.properties> <comparison.properties> [deals] [seed]");
        }
        var candidate = PolicyGenome.load(Path.of(args[1]));
        var comparison = PolicyGenome.load(Path.of(args[2]));
        int deals = intArgument(args, 3, 1000);
        long seed = longArgument(args, 4, 20270101L);
        var result = new FarbWenzSoloistValidator(config).validate(candidate, comparison, deals, seed);
        System.out.printf("Fixes Farb-Wenz: %d unabhängige Deals, %d gepaarte Spiele%n",
            result.independentDeals(), result.pairedGames());
        System.out.printf("Kandidat %s: %.2f ct/Spiel | Vergleich %s: %.2f ct/Spiel%n",
            result.candidateId(), result.candidateAverageNetCents(),
            result.comparisonId(), result.comparisonAverageNetCents());
        System.out.printf("Gepaarter Vorteil: %.2f ct, 95%%-Intervall [%.2f; %.2f]%n",
            result.meanPairedDeltaCents(), result.confidenceIntervalLowCents(),
            result.confidenceIntervalHighCents());
        result.byTrumpSuit().forEach((suit, metrics) -> System.out.printf(
            "  %-10s n=%d delta=%+.2f ct, 95%% [%.2f; %.2f]%n",
            suit, metrics.pairedGames(), metrics.meanPairedDeltaCents(),
            metrics.confidenceIntervalLowCents(), metrics.confidenceIntervalHighCents()));
        System.out.println(result.evidenceOfImprovement()
            ? "Kartenstrategie: Evidenz für einen Vorteil bei fest angesagtem Farb-Wenz."
            : "Kartenstrategie: Kein belastbarer Vorteil bei fest angesagtem Farb-Wenz.");
        }

    private static int intArgument(String[] args, int index, int defaultValue) {
        return args.length > index ? Integer.parseInt(args[index]) : defaultValue;
    }

        private static void runFarbWenzEvolution(String[] args, SchafkopfConfig config) {
        int generations = intArgument(args, 1, 3);
        int populationSize = intArgument(args, 2, 8);
        int dealsPerEvaluation = intArgument(args, 3, 10);
        long seed = longArgument(args, 4, 20261101L);
        if (populationSize < 4) {
            throw new IllegalArgumentException("Farb-Wenz evolution needs at least four genomes");
        }

        PolicyGenome baseline = PolicyGenome.loadConfigured();
        Path previousChampionPath = Path.of("config", "genomes", "champion-60000.properties");
        PolicyGenome previousChampion = java.nio.file.Files.exists(previousChampionPath)
            ? PolicyGenome.load(previousChampionPath)
            : baseline.withId("previous-default");
        List<PolicyGenome> seedGenomes = List.of(
            baseline.withId("farbwenz-baseline"),
            previousChampion.withId("previous-generalist"),
            baseline.withId("draw-strong-trumps")
                .withWeight("card.FARBWENZ.recommendation", 0.2)
                .withWeight("card.FARBWENZ.farbwenzSoloistLeadTrump", 2.0)
                .withWeight("card.FARBWENZ.farbwenzTrumpStrength", 1.8),
            baseline.withId("save-high-trumps")
                .withWeight("card.FARBWENZ.recommendation", 0.2)
                .withWeight("card.FARBWENZ.points", -0.8)
                .withWeight("card.FARBWENZ.farbwenzTrumpStrength", 0.4),
            baseline.withId("take-certain-tricks")
                .withWeight("card.FARBWENZ.recommendation", 0.2)
                .withWeight("card.FARBWENZ.farbwenzWinningTrick", 4.0)
                .withWeight("card.FARBWENZ.points", -0.2));
        List<PolicyGenome> initial = seedGenomes.subList(0, Math.min(populationSize, seedGenomes.size()));
        var result = new FarbWenzSoloistOptimizer(config).evolve(
            initial, generations, populationSize, 1, 0.25, dealsPerEvaluation, seed);
        Path championPath = Path.of("config", "genomes", "farbwenz-champion-" + seed + ".properties");
        result.champion().save(championPath);
        System.out.printf("Fixvertrag-Evolution: %d Deals/Policy, %d Bewertungen, Champion %s, Fitness %.2f ct/Spiel%n",
            dealsPerEvaluation, result.evaluationCount(), result.champion().id(), result.championFitness());
        System.out.println("Trumpffarbenwerte des Champions: " + result.champion().weights());
        System.out.println("Gespeichert: " + championPath);
        }

    private static long longArgument(String[] args, int index, long defaultValue) {
        return args.length > index ? Long.parseLong(args[index]) : defaultValue;
    }

        private static void runMatrixEvolution(String[] args, SchafkopfConfig config) {
        int generations = intArgument(args, 1, 3);
        int populationSize = intArgument(args, 2, 8);
        int dealsPerEvaluation = intArgument(args, 3, 10);
        long seed = longArgument(args, 4, 20261101L);
        if (populationSize < 4) {
            throw new IllegalArgumentException("Matrix evolution needs at least four genomes");
        }
        PolicyGenome baseline = PolicyGenome.loadConfigured();
        PolicyGenome lowRecommendation = baseline.withId("matrix-low-recommendation")
            .withWeight("card.FARBSOLO.recommendation", 0.1)
            .withWeight("card.FARBWENZ.recommendation", 0.1)
            .withWeight("card.RUFSPIEL.recommendation", 0.1)
            .withWeight("card.WENZ.recommendation", 0.1)
            .withWeight("card.BETTEL.recommendation", 0.1);
        PolicyGenome pointsAndTricks = lowRecommendation.withId("matrix-points-and-tricks")
            .withWeight("card.points", -1.0)
            .withWeight("card.winningTrick", 6.0)
            .withWeight("card.teammatePoints", 0.8)
            .withWeight("card.FARBSOLO.points", -0.8)
            .withWeight("card.FARBWENZ.points", -0.8)
            .withWeight("card.RUFSPIEL.points", -0.8)
            .withWeight("card.WENZ.points", -0.8)
            .withWeight("card.BETTEL.points", -0.8);
        PolicyGenome alternativeFarbs = baseline.withId("matrix-farbs-variation")
            .withWeight("card.FARBSOLO.recommendation", 0.2)
            .withWeight("card.FARBSOLO.points", -0.8)
            .withWeight("card.FARBSOLO.winningTrick", 2.5);
        List<PolicyGenome> initialPopulation = List.of(
            baseline.withId("matrix-baseline"),
            baseline.withId("matrix-avoid-points")
                .withWeight("card.points", -0.5)
                .withWeight("card.FARBWENZ.points", -0.5),
            lowRecommendation,
            pointsAndTricks,
            alternativeFarbs,
            baseline.withId("matrix-take-tricks")
                .withWeight("card.winningTrick", 5.5)
                .withWeight("card.FARBWENZ.farbwenzWinningTrick", 2.5),
            baseline.withId("matrix-trump-control")
                .withWeight("card.FARBWENZ.farbwenzTrumpStrength", 1.6)
                .withWeight("card.FARBWENZ.farbwenzSoloistLeadTrump", 1.4));
        var result = new ContractMatrixOptimizer(config).evolve(
            initialPopulation.subList(0, Math.min(populationSize, initialPopulation.size())),
            generations, populationSize, 1, 0.75, dealsPerEvaluation, seed);
        Path championPath = Path.of("config", "genomes", "matrix-champion-" + seed + ".properties");
        result.champion().save(championPath);
        System.out.printf("Vertragsmatrix-Evolution: %d Deals/Policy/Generation, %d Bewertungen, Champion %s, Fitness %.2f ct/Spiel%n",
            dealsPerEvaluation, result.evaluationCount(), result.champion().id(), result.championFitness());
        System.out.println("Gespeichert: " + championPath);
        }

        private static void runMatrixValidation(String[] args, SchafkopfConfig config) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "Usage: ./run.sh validate-matrix <candidate.properties> <comparison.properties> [deals] [seed]");
        }
        PolicyGenome candidate = PolicyGenome.load(Path.of(args[1]));
        PolicyGenome comparison = PolicyGenome.load(Path.of(args[2]));
        int deals = intArgument(args, 3, 1000);
        long seed = longArgument(args, 4, 20261101L);
        var result = new ContractMatrixValidator(config).validate(candidate, comparison, deals, seed);
        System.out.printf("Vertragsmatrix: %d unabhängige Deals, %d gepaarte Spiele je Policy%n",
            result.independentDeals(), result.pairedGames());
        System.out.printf("Gepaarter Vorteil %s gegen %s: %.2f ct/Spiel, 95%%-Intervall [%.2f; %.2f]%n",
            result.candidateId(), result.comparisonId(), result.meanPairedDeltaCents(),
            result.confidenceIntervalLowCents(), result.confidenceIntervalHighCents());
        result.contracts().forEach(contract -> System.out.printf(
            "  %-20s n=%d delta=%+.2f ct, 95%% [%.2f; %.2f]%n", contract.contract(),
            contract.pairedGames(), contract.meanPairedDeltaCents(),
            contract.confidenceIntervalLowCents(), contract.confidenceIntervalHighCents()));
        System.out.println("Nach gleicher Rolle in beiden Armen:");
        result.byRole().forEach((role, stats) -> System.out.printf(
            "  %-32s n=%d deals=%d delta=%+.2f ct, 95%% [%.2f; %.2f]%n", role,
            stats.pairedGames(), stats.independentDeals(), stats.meanPairedDeltaCents(),
            stats.confidenceIntervalLowCents(), stats.confidenceIntervalHighCents()));
        }

    private static void runMatrixComposition(String[] args, SchafkopfConfig config) {
        if (args.length < 6) {
            throw new IllegalArgumentException(
                    "Usage: ./run.sh compose-matrix <specialist.properties> <reference.properties> "
                            + "<selectionDeals> <selectionSeed> <output.properties>");
        }
        PolicyGenome specialist = PolicyGenome.load(Path.of(args[1]));
        PolicyGenome reference = PolicyGenome.load(Path.of(args[2]));
        int deals = intArgument(args, 3, 1000);
        long seed = longArgument(args, 4, 20261101L);
        ContractMatrixValidator.ValidationResult selection = new ContractMatrixValidator(config)
                .validate(specialist, reference, deals, seed);
        PolicyGenome composite = RoleSpecialistComposer.compose(reference, specialist, selection.byRole());
        Path output = Path.of(args[5]);
        composite.save(output);
        System.out.printf("Rollen-Policy gespeichert: %s (%s)%n", output, composite.id());
        selection.byRole().forEach((role, result) -> {
            if (result.independentDeals() >= 30 && result.confidenceIntervalLowCents() > 0.0
                    && !role.startsWith("RAMSCH/")) {
                System.out.printf("  übernommen %-32s deals=%d delta=%+.2f ct%n", role,
                        result.independentDeals(), result.meanPairedDeltaCents());
            }
        });
        System.out.println("Vor Freigabe mit validate-matrix auf neuen Seeds prüfen.");
    }
}
