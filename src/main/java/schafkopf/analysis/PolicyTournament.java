package schafkopf.analysis;

import schafkopf.ai.BiddingAdvisor;
import schafkopf.ai.GameBiddingAdvisor;
import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.DecisionTrace;
import schafkopf.ai.policy.GeneticAlgorithm;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.economy.Tariff;
import schafkopf.model.BettelContract;
import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.GeierContract;
import schafkopf.model.KreuzbockContract;
import schafkopf.model.Personality;
import schafkopf.model.Player;
import schafkopf.model.RamschContract;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class PolicyTournament {
    private static final int MAX_CONSECUTIVE_REDEALS = 12;
        private record FixedContract(
            GameType gameType,
            Suit trumpSuit,
            Suit calledSuit,
            int callerSeat,
            int focalSeat,
            PlayerRole requiredRole
        ) {
        private GameContract create(List<Player> players) {
            Player caller = players.get(callerSeat);
            return switch (gameType) {
                case RUFSPIEL -> new RufspielContract(caller, calledSuit);
                case FARBSOLO -> new SoloContract(caller, trumpSuit);
                case WENZ -> new WenzContract(caller);
                case GEIER -> new GeierContract(caller);
                case FARBWENZ -> new WenzContract(caller, trumpSuit);
                case BETTEL -> new BettelContract(caller);
                case RAMSCH -> new RamschContract();
                case KREUZBOCK -> new KreuzbockContract();
                default -> throw new IllegalArgumentException("Unsupported fixed tournament game: " + gameType);
            };
        }

        private boolean isValidDeal(List<Player> players) {
            return switch (gameType) {
                case RUFSPIEL -> isValidRufspielDeal(players);
                case FARBSOLO, WENZ, GEIER, FARBWENZ, BETTEL -> isValidSoloRole();
                case KREUZBOCK -> isValidKreuzbockRole();
                default -> true;
            };
        }

        private boolean isValidRufspielDeal(List<Player> players) {
            Player focal = players.get(focalSeat);
            Player caller = players.get(callerSeat);
            Card calledSau = new Card(calledSuit, Rank.SAU);
            boolean validCaller = !caller.hasCard(calledSau) && caller.hand().stream().anyMatch(card ->
                    card.suit() == calledSuit && card.rank() != Rank.SAU
                            && card.rank() != Rank.OBER && card.rank() != Rank.UNTER);
            if (!validCaller) {
                return false;
            }
            if (requiredRole == null) {
                return true;
            }
            return switch (requiredRole) {
                case RUFSPIEL_CALLER -> focalSeat == callerSeat;
                case RUFSPIEL_PARTNER -> focalSeat != callerSeat && focal.hasCard(calledSau);
                case DEFENDER -> focalSeat != callerSeat && !focal.hasCard(calledSau);
                default -> true;
            };
        }

        private boolean isValidSoloRole() {
            return requiredRole == PlayerRole.SOLOIST ? focalSeat == callerSeat
                    : requiredRole != PlayerRole.DEFENDER || focalSeat != callerSeat;
        }

        private boolean isValidKreuzbockRole() {
            return requiredRole == null || (focalSeat % 2 == 0
                    ? requiredRole == PlayerRole.KREUZBOCK_TEAM_A
                    : requiredRole == PlayerRole.KREUZBOCK_TEAM_B);
        }
    }
    private static final double OVERALL_TAIL_PENALTY = 0.10;
    private static final double SOLOIST_TAIL_PENALTY = 0.50;
    private static final double FARBWENZ_SOLOIST_MEAN_PENALTY = 0.50;
    public enum PlayerRole {
        RUFSPIEL_CALLER,
        RUFSPIEL_PARTNER,
        SOLOIST,
        DEFENDER,
        RAMSCH_LOWEST_EYES,
        RAMSCH_OTHER,
        KREUZBOCK_TEAM_A,
        KREUZBOCK_TEAM_B
    }

    public record MatchResult(
            GameType gameType,
            Map<Integer, Long> netCentsByGenome,
            Map<Integer, PlayerRole> rolesByGenome
    ) {
        public MatchResult {
            netCentsByGenome = Map.copyOf(netCentsByGenome);
            rolesByGenome = Map.copyOf(rolesByGenome);
        }
    }

    private record PreparedDeal(
            List<Player> players,
            SchafkopfGame game,
            Map<String, Integer> genomeIndexByPlayerId,
            Map<Integer, GameBiddingAdvisor> biddingAdvisors,
            Map<Integer, BiddingAdvisor> klopfenAdvisors
    ) {
    }

    private record ContractSelection(GameContract contract, int stockMultiplier) {
    }

    private record GameAttempt(MatchResult matchResult, int stockMultiplier) {
    }

    public record CandidateFitness(
            String genomeId,
            int gamesPlayed,
            long netCents,
            double averageNetCents,
            double worstDecileAverageNetCents,
            Map<String, RoleFitness> byGameTypeAndRole,
            List<Integer> gamesBySeat,
            List<Integer> gamesByStartingSeat
    ) {
        public CandidateFitness {
            byGameTypeAndRole = Map.copyOf(byGameTypeAndRole);
            gamesBySeat = List.copyOf(gamesBySeat);
            gamesByStartingSeat = List.copyOf(gamesByStartingSeat);
        }

        public double fitnessValue() {
            double score = averageNetCents
                    - OVERALL_TAIL_PENALTY * Math.max(0.0, -worstDecileAverageNetCents);
            double worstSoloistTail = byGameTypeAndRole.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("/SOLOIST"))
                    .filter(entry -> entry.getValue().games() >= 5)
                    .mapToDouble(entry -> entry.getValue().worstDecileAverageNetCents())
                    .min()
                    .orElse(0.0);
                    double farbwenzSoloistMean = byGameTypeAndRole.containsKey("FARBWENZ/SOLOIST")
                        ? byGameTypeAndRole.get("FARBWENZ/SOLOIST").averageNetCents()
                        : 0.0;
                    return score
                        - SOLOIST_TAIL_PENALTY * Math.max(0.0, -worstSoloistTail)
                        - FARBWENZ_SOLOIST_MEAN_PENALTY * Math.max(0.0, -farbwenzSoloistMean);
        }
    }

    public record RoleFitness(int games, double averageNetCents, double worstDecileAverageNetCents) {
    }

    public record TournamentResult(long seed, int gamesPerSeat, List<CandidateFitness> candidates) {
        public TournamentResult {
            candidates = List.copyOf(candidates);
        }
    }

    private final RuleEngine rules = new RuleEngine();
    private final Tariff tariff;
    private final SchafkopfConfig config;
    private final Consumer<DecisionTrace> traceSink;

    public PolicyTournament() {
        this(SchafkopfConfig.defaults());
    }

    public PolicyTournament(SchafkopfConfig config) {
        this(config, ignored -> { });
    }

    public PolicyTournament(SchafkopfConfig config, Consumer<DecisionTrace> traceSink) {
        this.config = config;
        this.tariff = new Tariff(config);
        this.traceSink = traceSink;
    }

    public TournamentResult run(List<PolicyGenome> population, int gamesPerSeat, long seed) {
        if (population == null || population.size() < 4) {
            throw new IllegalArgumentException("A policy tournament needs at least four genomes");
        }
        if (gamesPerSeat < 1) {
            throw new IllegalArgumentException("gamesPerSeat must be positive");
        }

        int populationSize = population.size();
        long[] netCents = new long[populationSize];
        int[] gamesPlayed = new int[populationSize];
        int[][] gamesBySeat = new int[populationSize][4];
        int[][] gamesByStartingSeat = new int[populationSize][4];
        List<List<Double>> individualReturns = new ArrayList<>(populationSize);
        List<Map<String, List<Double>>> individualRoleReturns = new ArrayList<>(populationSize);
        for (int index = 0; index < populationSize; index++) {
            individualReturns.add(new ArrayList<>());
            individualRoleReturns.add(new HashMap<>());
        }

        for (int focalIndex = 0; focalIndex < populationSize; focalIndex++) {
            for (int focalSeat = 0; focalSeat < 4; focalSeat++) {
                for (int repetition = 0; repetition < gamesPerSeat; repetition++) {
                    int startingSeat = Math.floorMod(focalIndex + repetition, 4);
                    List<Integer> genomeAtSeat = assignSeats(populationSize, focalIndex, focalSeat);
                    long dealSeed = seed + repetition;
                            MatchResult match = playGame(population, genomeAtSeat, startingSeat, dealSeed, null);
                            Map<Integer, Long> netForGame = match.netCentsByGenome();
                    for (int seat = 0; seat < 4; seat++) {
                        int genomeIndex = genomeAtSeat.get(seat);
                            long net = netForGame.get(genomeIndex);
                            netCents[genomeIndex] += net;
                        gamesPlayed[genomeIndex]++;
                        gamesBySeat[genomeIndex][seat]++;
                        gamesByStartingSeat[genomeIndex][startingSeat]++;
                            individualReturns.get(genomeIndex).add((double) net);
                            String roleKey = match.gameType().name() + "/" + match.rolesByGenome().get(genomeIndex).name();
                            individualRoleReturns.get(genomeIndex)
                                .computeIfAbsent(roleKey, ignored -> new ArrayList<>())
                                .add((double) net);
                    }
                }
            }
        }

        List<CandidateFitness> results = new ArrayList<>(populationSize);
        for (int index = 0; index < populationSize; index++) {
            List<Integer> seats = List.of(gamesBySeat[index][0], gamesBySeat[index][1],
                    gamesBySeat[index][2], gamesBySeat[index][3]);
                List<Integer> startingSeats = List.of(gamesByStartingSeat[index][0], gamesByStartingSeat[index][1],
                    gamesByStartingSeat[index][2], gamesByStartingSeat[index][3]);
                    Map<String, RoleFitness> roleFitness = summarizeRoles(individualRoleReturns.get(index));
            results.add(new CandidateFitness(population.get(index).id(), gamesPlayed[index], netCents[index],
                        (double) netCents[index] / gamesPlayed[index],
                        worstDecileAverage(individualReturns.get(index)), roleFitness, seats, startingSeats));
        }
        return new TournamentResult(seed, gamesPerSeat, results);
    }

    public GeneticAlgorithm.PopulationFitnessEvaluator fitnessEvaluator(int gamesPerSeat, long seed) {
        AtomicInteger evaluationBatch = new AtomicInteger();
        return population -> {
            long batchSeed = seedForEvaluationBatch(seed, evaluationBatch.getAndIncrement(), gamesPerSeat);
            return run(population, gamesPerSeat, batchSeed).candidates().stream()
                    .map(CandidateFitness::fitnessValue)
                    .toList();
        };
    }

    static long seedForEvaluationBatch(long seed, int batch, int dealsPerBatch) {
        if (batch < 0 || dealsPerBatch < 1) {
            throw new IllegalArgumentException("Evaluation batches need a non-negative index and positive size");
        }
        return seed + (long) batch * dealsPerBatch;
    }

    private Map<String, RoleFitness> summarizeRoles(Map<String, List<Double>> returnsByRole) {
        Map<String, RoleFitness> summaries = new HashMap<>();
        returnsByRole.forEach((role, values) -> summaries.put(role, new RoleFitness(
                values.size(),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
                worstDecileAverage(values))));
        return summaries;
    }

    private double worstDecileAverage(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int tailSize = Math.max(1, (int) Math.ceil(sorted.size() * 0.10));
        return sorted.subList(0, tailSize).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public Map<Integer, Long> runMatch(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            int startingSeat,
            long dealSeed
    ) {
        return runMatchDetailed(population, genomeAtSeat, startingSeat, dealSeed).netCentsByGenome();
        }

    public MatchResult runFixedFarbWenzMatch(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            int callerSeat,
            Suit trumpSuit,
            int startingSeat,
            long dealSeed
    ) {
        return runFixedContractMatch(population, genomeAtSeat, GameType.FARBWENZ,
            trumpSuit, callerSeat, startingSeat, dealSeed);
    }

    public MatchResult runFixedContractMatch(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            GameType gameType,
            Suit suit,
            int callerSeat,
            int startingSeat,
            long dealSeed
    ) {
            return runFixedContractMatch(population, genomeAtSeat, gameType, suit,
                callerSeat, genomeAtSeat.indexOf(0), null, startingSeat, dealSeed);
            }

    @SuppressWarnings("java:S107")
    public MatchResult runFixedContractMatch(
                List<PolicyGenome> population,
                List<Integer> genomeAtSeat,
                GameType gameType,
                Suit suit,
                int callerSeat,
                int focalSeat,
                PlayerRole requiredRole,
                int startingSeat,
                long dealSeed
            ) {
        validateMatch(population, genomeAtSeat, startingSeat);
            if (callerSeat < 0 || callerSeat >= 4 || focalSeat < 0 || focalSeat >= 4 || gameType == null
                || genomeAtSeat.get(focalSeat) != 0) {
            throw new IllegalArgumentException("Fixed contract requires a game type and seated caller");
        }
        boolean needsSuit = gameType == GameType.RUFSPIEL || gameType == GameType.FARBSOLO
                || gameType == GameType.FARBWENZ;
        if (needsSuit != (suit != null) || suit == Suit.HERZ && gameType == GameType.RUFSPIEL) {
            throw new IllegalArgumentException("Invalid suit for fixed contract " + gameType);
        }
        FixedContract fixedContract = new FixedContract(
                gameType, gameType == GameType.RUFSPIEL ? null : suit,
            gameType == GameType.RUFSPIEL ? suit : null, callerSeat, focalSeat, requiredRole);
        return playGame(population, genomeAtSeat, startingSeat, dealSeed, fixedContract);
    }

        public MatchResult runMatchDetailed(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            int startingSeat,
            long dealSeed
        ) {
            validateMatch(population, genomeAtSeat, startingSeat);
            return playGame(population, genomeAtSeat, startingSeat, dealSeed, null);
        }

        private void validateMatch(List<PolicyGenome> population, List<Integer> genomeAtSeat, int startingSeat) {
        if (population == null || population.size() < 4 || genomeAtSeat.size() != 4) {
            throw new IllegalArgumentException("A match requires four seated genomes and a population of at least four");
        }
        if (startingSeat < 0 || startingSeat >= 4
                || genomeAtSeat.stream().anyMatch(index -> index < 0 || index >= population.size())) {
            throw new IllegalArgumentException("Invalid starting seat or genome assignment");
        }
    }

    public GeneticAlgorithm.EvolutionResult evolve(
            List<PolicyGenome> initialPopulation,
            int generations,
            int populationSize,
            int eliteCount,
            double mutationStandardDeviation,
            int gamesPerSeat,
            long seed
    ) {
        if (populationSize < 4) {
            throw new IllegalArgumentException("Policy tournament population must have at least four genomes");
        }
        return new GeneticAlgorithm().evolvePopulation(initialPopulation, generations, populationSize, eliteCount,
                mutationStandardDeviation, seed, fitnessEvaluator(gamesPerSeat, seed));
    }

    private List<Integer> assignSeats(int populationSize, int focalIndex, int focalSeat) {
        List<Integer> genomeAtSeat = new ArrayList<>(List.of(-1, -1, -1, -1));
        for (int offset = 0; offset < 4; offset++) {
            int seat = (focalSeat + offset) % 4;
            genomeAtSeat.set(seat, (focalIndex + offset) % populationSize);
        }
        return genomeAtSeat;
    }

    private MatchResult playGame(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            int startingSeat,
            long dealSeed,
            FixedContract fixedContract
    ) {
        int stockMultiplier = 1;
        for (int redeal = 0; ; redeal++) {
                GameAttempt attempt = playGameAttempt(population, genomeAtSeat, startingSeat, dealSeed + redeal,
                        redeal, fixedContract, stockMultiplier);
            if (attempt.matchResult() != null) {
                return attempt.matchResult();
            }
            if (fixedContract != null && redeal >= 1000) {
                throw new IllegalStateException("Could not deal a valid fixed-contract role after 1000 redeals");
            }
            stockMultiplier = attempt.stockMultiplier();
        }
    }

    private GameAttempt playGameAttempt(
            List<PolicyGenome> population,
            List<Integer> genomeAtSeat,
            int startingSeat,
            long attemptSeed,
            int redeal,
            FixedContract fixedContract,
            int stockMultiplier
    ) {
        PreparedDeal deal = prepareDeal(population, genomeAtSeat, attemptSeed);
        if (fixedContract != null && !fixedContract.isValidDeal(deal.players())) {
            return new GameAttempt(null, stockMultiplier);
        }
        applyKlopfen(deal, fixedContract == null);
        ContractSelection selection = selectContract(deal, startingSeat, redeal, fixedContract, stockMultiplier);
        if (selection.contract() == null) {
            return new GameAttempt(null, selection.stockMultiplier());
        }

        deal.game().setupContract(selection.contract());
        if (selection.stockMultiplier() > 1) {
            deal.game().bettingState().applyStock(selection.stockMultiplier());
        }
        GameResult result = deal.game().playFullGame(deal.players().get(startingSeat));
        return new GameAttempt(createMatchResult(deal, result), selection.stockMultiplier());
    }

    private PreparedDeal prepareDeal(
            List<PolicyGenome> population, List<Integer> genomeAtSeat, long dealSeed
    ) {
        List<Player> players = createPlayers();
        Deck deck = new Deck();
        deck.shuffle(new Random(dealSeed));
        Map<String, Integer> genomeIndexByPlayerId = new HashMap<>();
        Map<Integer, GameBiddingAdvisor> biddingAdvisors = new HashMap<>();
        Map<Integer, BiddingAdvisor> klopfenAdvisors = new HashMap<>();
        Map<Integer, SimpleBot> bots = new HashMap<>();
        for (int seat = 0; seat < 4; seat++) {
            int genomeIndex = genomeAtSeat.get(seat);
            PolicyGenome genome = population.get(genomeIndex);
            genomeIndexByPlayerId.put(players.get(seat).id(), genomeIndex);
            biddingAdvisors.put(seat, new GameBiddingAdvisor(rules, genome, traceSink));
            klopfenAdvisors.put(seat, new BiddingAdvisor(rules, genome, traceSink));
            bots.put(seat, new SimpleBot(rules, true, genome, traceSink));
        }
        SchafkopfGame game = new SchafkopfGame(players, rules,
                player -> bots.get(players.indexOf(player)), config);
        game.setLogger(ignored -> { });
        game.setCustomHands(deck.deal4x8());
        return new PreparedDeal(players, game, genomeIndexByPlayerId, biddingAdvisors, klopfenAdvisors);
    }

    private void applyKlopfen(PreparedDeal deal, boolean enabled) {
        if (!enabled || !config.klopfenEnabled()) {
            return;
        }
        for (Player player : deal.players()) {
            int seat = deal.players().indexOf(player);
            if (deal.klopfenAdvisors().get(seat).shouldKlopfen(player,
                    player.hand().subList(0, config.klopfenAfterCards()), rules)) {
                deal.game().bettingState().applyDouble(player);
            }
        }
    }

    private ContractSelection selectContract(
            PreparedDeal deal,
            int startingSeat,
            int redeal,
            FixedContract fixedContract,
            int stockMultiplier
    ) {
        GameContract contract = fixedContract == null
                ? chooseContract(deal.players(), deal.biddingAdvisors(), startingSeat)
                : fixedContract.create(deal.players());
        if (contract != null) {
            return new ContractSelection(contract, stockMultiplier);
        }
        if (redeal >= MAX_CONSECUTIVE_REDEALS || config.noGamePolicy() == SchafkopfConfig.NoGamePolicy.RAMSCH) {
            return new ContractSelection(new RamschContract(), stockMultiplier);
        }
        if (config.noGamePolicy() == SchafkopfConfig.NoGamePolicy.STOCK) {
            stockMultiplier = Math.min(32, Math.multiplyExact(stockMultiplier, config.stockMultiplier()));
        } else if (config.noGamePolicy() == SchafkopfConfig.NoGamePolicy.KREUZBOCK) {
            contract = new KreuzbockContract();
        } else if (config.noGamePolicy() == SchafkopfConfig.NoGamePolicy.MUSS_SPIEL) {
            contract = createMussContract(deal.players());
        }
        return new ContractSelection(contract, stockMultiplier);
    }

    private MatchResult createMatchResult(PreparedDeal deal, GameResult result) {
        int amountPerPlayer = tariff.calculatePayment(deal.game()).totalCentsPerPlayer();
        List<Player> winners = result.callerTeamWon() ? result.callerTeam() : result.defenderTeam();
        List<Player> losers = result.callerTeamWon() ? result.defenderTeam() : result.callerTeam();

        Map<Integer, Long> returns = new HashMap<>();
        Map<Integer, PlayerRole> roles = new HashMap<>();
        for (Player player : deal.players()) {
            int genomeIndex = deal.genomeIndexByPlayerId().get(player.id());
            long net = winners.contains(player)
                    ? (long) amountPerPlayer * losers.size()
                    : -(long) amountPerPlayer * winners.size();
            returns.put(genomeIndex, net);
            roles.put(genomeIndex, roleOf(player, deal.game(), result));
        }
        return new MatchResult(result.contract().gameType(), returns, roles);
    }

    private PlayerRole roleOf(Player player, SchafkopfGame game, GameResult result) {
        GameType gameType = result.contract().gameType();
        if (gameType == GameType.RAMSCH) {
            return result.callerTeam().contains(player) ? PlayerRole.RAMSCH_LOWEST_EYES : PlayerRole.RAMSCH_OTHER;
        }
        if (gameType == GameType.KREUZBOCK) {
            return result.callerTeam().contains(player) ? PlayerRole.KREUZBOCK_TEAM_A : PlayerRole.KREUZBOCK_TEAM_B;
        }
        if (gameType == GameType.RUFSPIEL) {
            if (player.equals(result.contract().caller())) {
                return PlayerRole.RUFSPIEL_CALLER;
            }
            return player.equals(game.partner()) ? PlayerRole.RUFSPIEL_PARTNER : PlayerRole.DEFENDER;
        }
        return player.equals(result.contract().caller()) ? PlayerRole.SOLOIST : PlayerRole.DEFENDER;
    }

    private List<Player> createPlayers() {
        return List.of(
                new Player("0", "Agent 0"),
                new Player("1", "Agent 1"),
                new Player("2", "Agent 2"),
                new Player("3", "Agent 3"));
    }

        private GameContract chooseContract(
            List<Player> players,
            Map<Integer, GameBiddingAdvisor> advisors,
            int startingSeat
        ) {
        for (int offset = 0; offset < players.size(); offset++) {
            int seat = (startingSeat + offset) % players.size();
            Player player = players.get(seat);
            GameBiddingAdvisor.ContractRecommendation decision = advisors.get(seat)
                .recommend(player, Personality.SOLIDE);
            if (decision != null) {
            return decision.contract();
            }
        }
        return null;
    }

    private GameContract createMussContract(List<Player> players) {
        Card requiredCard = new Card(Suit.EICHEL, Rank.OBER);
        Player caller = players.stream().filter(player -> player.hasCard(requiredCard)).findFirst().orElseThrow();
        return switch (config.mussGameType()) {
            case FARBSOLO -> new SoloContract(caller, config.mussTrumpfSuit());
            case WENZ -> new WenzContract(caller);
            case FARBWENZ -> new WenzContract(caller, config.mussTrumpfSuit());
            case BETTEL -> new BettelContract(caller);
            default -> new RamschContract();
        };
    }
}