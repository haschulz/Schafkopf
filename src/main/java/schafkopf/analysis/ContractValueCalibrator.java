package schafkopf.analysis;

import schafkopf.ai.BiddingAdvisor;
import schafkopf.ai.GameBiddingAdvisor;
import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.ContractValueModel;
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
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ContractValueCalibrator {
    public record CalibrationResult(
            ContractValueModel model,
            int dealsSampled,
            int rollouts,
            int opponentPolicyCount,
            long seed
    ) {
        public Map<GameType, Integer> samplesByGameType() {
            return model.samplesByGameType();
        }
    }

    private record ContractSpec(GameType gameType, Suit suit) {
        private GameContract create(Player caller) {
            return switch (gameType) {
                case RUFSPIEL -> new RufspielContract(caller, suit);
                case FARBSOLO -> new SoloContract(caller, suit);
                case WENZ -> new WenzContract(caller);
                case GEIER -> new GeierContract(caller);
                case FARBWENZ -> new WenzContract(caller, suit);
                case BETTEL -> new BettelContract(caller);
                case KREUZBOCK -> new KreuzbockContract();
                default -> throw new IllegalArgumentException("Unsupported calibration contract: " + gameType);
            };
        }
    }

    private final RuleEngine rules = new RuleEngine();
    private final SchafkopfConfig config;
    private final List<PolicyGenome> opponentPool;
    private final Tariff tariff;

    public ContractValueCalibrator(SchafkopfConfig config) {
        this(config, defaultOpponentPool());
    }

    public ContractValueCalibrator(SchafkopfConfig config, PolicyGenome opponents) {
        this(config, List.of(opponents));
    }

    public ContractValueCalibrator(SchafkopfConfig config, List<PolicyGenome> opponentPool) {
        if (opponentPool == null || opponentPool.isEmpty()) {
            throw new IllegalArgumentException("Calibration requires at least one opponent policy");
        }
        this.config = config;
        this.opponentPool = List.copyOf(opponentPool);
        this.tariff = new Tariff(config);
    }

    public CalibrationResult calibrate(int deals, long seed, double ridge) {
        if (deals < 2) {
            throw new IllegalArgumentException("Calibration requires at least two deals");
        }

        List<ContractValueModel.TrainingSample> samples = new ArrayList<>();
        for (int deal = 0; deal < deals; deal++) {
            List<List<Card>> hands = deal(seed + deal);
            int callerSeat = deal % 4;
            int startingSeat = Math.floorMod(deal * 3, 4);
            Player caller = createPlayers(hands).get(callerSeat);
            List<ContractSpec> dealContracts = candidates(caller);
            for (int contractIndex = 0; contractIndex < dealContracts.size(); contractIndex++) {
                samples.add(simulateSample(hands, callerSeat, startingSeat,
                        deal, contractIndex, dealContracts.get(contractIndex)));
            }
        }

        ContractValueModel model = ContractValueModel.fit(
                "rollout-" + seed + "-" + deals + "-pool-" + opponentPool.size(), samples, ridge);
        return new CalibrationResult(model, deals, samples.size(), opponentPool.size(), seed);
    }

    private List<List<Card>> deal(long seed) {
        Deck deck = new Deck();
        deck.shuffle(new Random(seed));
        return deck.deal4x8();
    }

    private List<Player> createPlayers(List<List<Card>> hands) {
        List<Player> players = List.of(
                new Player("0", "Calibration 0"),
                new Player("1", "Calibration 1"),
                new Player("2", "Calibration 2"),
                new Player("3", "Calibration 3"));
        for (int seat = 0; seat < players.size(); seat++) {
            players.get(seat).setHand(hands.get(seat));
        }
        return players;
    }

    private List<ContractSpec> candidates(Player caller) {
        List<ContractSpec> candidates = new ArrayList<>();
        for (Suit suit : new BiddingAdvisor(rules, opponentPool.get(0)).getValidRufspielSuits(caller)) {
            candidates.add(new ContractSpec(GameType.RUFSPIEL, suit));
        }
        for (Suit suit : Suit.values()) {
            candidates.add(new ContractSpec(GameType.FARBSOLO, suit));
            candidates.add(new ContractSpec(GameType.FARBWENZ, suit));
        }
        candidates.add(new ContractSpec(GameType.WENZ, null));
        candidates.add(new ContractSpec(GameType.GEIER, null));
        candidates.add(new ContractSpec(GameType.BETTEL, null));
        return candidates;
    }

    private ContractValueModel.TrainingSample simulateSample(
            List<List<Card>> hands,
            int callerSeat,
            int startingSeat,
            int dealIndex,
            int contractIndex,
            ContractSpec spec
    ) {
        List<Player> players = createPlayers(hands);
        Player caller = players.get(callerSeat);
        GameContract contract = spec.create(caller);
        Map<String, Double> features = new GameBiddingAdvisor(rules, opponentPool.get(0))
            .featuresFor(caller, contract);
        SchafkopfGame game = new SchafkopfGame(players, rules, player ->
            new SimpleBot(rules, true, policyForSeat(dealIndex, contractIndex, players.indexOf(player))), config);
        game.setLogger(ignored -> { });
        game.setupContract(contract);
        applyKlopfen(game, dealIndex, contractIndex);
        GameResult result = game.playFullGame(players.get(startingSeat));
        int amountPerPlayer = tariff.calculatePayment(game).totalCentsPerPlayer();
        List<Player> winners = result.callerTeamWon() ? result.callerTeam() : result.defenderTeam();
        List<Player> losers = result.callerTeamWon() ? result.defenderTeam() : result.callerTeam();
        double netCents = winners.contains(caller)
                ? (double) amountPerPlayer * losers.size()
                : -(double) amountPerPlayer * winners.size();
        return new ContractValueModel.TrainingSample(contract.gameType(), features, netCents);
    }

    private void applyKlopfen(SchafkopfGame game, int dealIndex, int contractIndex) {
        if (!config.klopfenEnabled()) {
            return;
        }
        for (Player player : game.players()) {
            PolicyGenome policy = policyForSeat(dealIndex, contractIndex, game.players().indexOf(player));
            BiddingAdvisor advisor = new BiddingAdvisor(rules, policy);
            List<Card> firstCards = player.hand().subList(0, config.klopfenAfterCards());
            if (advisor.shouldKlopfen(player, firstCards, rules)) {
                game.bettingState().applyDouble(player);
            }
        }
    }

    private PolicyGenome policyForSeat(int dealIndex, int contractIndex, int seat) {
        int index = Math.floorMod(dealIndex + contractIndex + seat, opponentPool.size());
        return opponentPool.get(index);
    }

    private static List<PolicyGenome> defaultOpponentPool() {
        PolicyGenome baseline = PolicyGenome.baseline();
        return List.of(
                baseline.withId("calibration-baseline"),
                baseline.withId("calibration-defensive")
                        .withWeight("bid.handScore", 1.15)
                        .withWeight("bid.FARBSOLO.threshold", 10.0)
                        .withWeight("bid.FARBWENZ.threshold", 10.0)
                        .withWeight("bid.WENZ.threshold", 9.0),
                baseline.withId("calibration-aggressive")
                        .withWeight("bid.handScore", 0.85)
                        .withWeight("bid.BETTEL.threshold", -1.0),
                baseline.withId("calibration-solo-control")
                        .withWeight("bid.FARBSOLO.trumpControl", 1.6)
                        .withWeight("bid.FARBWENZ.trumpControl", 1.8));
    }
}