package schafkopf.state;

import schafkopf.ai.BiddingAdvisor;
import schafkopf.ai.GameBiddingAdvisor;
import schafkopf.ai.SimpleBot;
import schafkopf.ai.policy.ContractValueModel;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.config.SchafkopfConfig;
import schafkopf.economy.Tariff;
import schafkopf.model.BettelContract;
import schafkopf.model.BettingState;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.GeierContract;
import schafkopf.model.KreuzbockContract;
import schafkopf.model.Personality;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.RamschContract;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;
import schafkopf.model.WenzContract;
import schafkopf.rules.GameBiddingRules;
import schafkopf.rules.RuleEngine;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class GameSession {
    public enum Stage {
        BIDDING,
        TRICK_PLAY,
        FINISHED
    }

    public record BidOption(String id, String label, boolean enabled, boolean implemented) {
    }

    public enum NoGameSelectionResult {
        STOCK_ADDED,
        STOCK_MAXED,
        ZAMMWERFEN,
        NOT_IMPLEMENTED
    }

    public record BidMessage(int seat, String text) {
    }

    public record TableCard(String playerName, Card card) {
    }

    public record SeatSnapshot(String id, String name, int cardsRemaining, int eyes, int tricksWon, boolean toMove) {
    }

    public record TrickSnapshot(int number, List<TableCard> cards, String winnerName, int points) {
        public TrickSnapshot {
            cards = List.copyOf(cards);
        }
    }

    public record Snapshot(
            Stage stage,
            int trickNumber,
            String contractName,
            GameType contractType,
            Suit contractSuit,
            boolean tout,
            String message,
            String resultSummary,
            boolean yourTurn,
            List<Card> hand,
            List<Card> legalCards,
            List<TableCard> tableCards,
            List<SeatSnapshot> seats,
            TrickSnapshot lastCompletedTrick,
            List<BidOption> bidOptions,
            List<TrickSnapshot> completedTricks,
                List<String> seatCalls,
                List<BidMessage> bidMessages,
                boolean humanCallPending,
                boolean awaitingNoGameChoice,
                BettingState.StakeBreakdown stake,
                boolean bettingWindowOpen,
                boolean humanCanCallKontra,
                boolean humanCanCallRetour,
                int contractCallerSeat
    ) {
        public Snapshot {
            hand = List.copyOf(hand);
            legalCards = List.copyOf(legalCards);
            tableCards = List.copyOf(tableCards);
            seats = List.copyOf(seats);
            bidOptions = List.copyOf(bidOptions);
            completedTricks = List.copyOf(completedTricks);
            seatCalls = java.util.Collections.unmodifiableList(new ArrayList<>(seatCalls));
            bidMessages = List.copyOf(bidMessages);
        }
    }

    private static final int HUMAN_SEAT = 3;
    private static final String CALL_PLAY = "Spiele!";
    private static final String CALL_AGREE = "Auch!";
    private static final String CALL_PASS = "Weiter";
    private static final String DEFAULT_FALSE = "false";
    private static final String SAUSPIEL_OPTION_PREFIX = "SAUSPIEL_";
    private static final String FARBWENZ_OPTION_PREFIX = "FARBWENZ_";
    private static final List<Personality> BOT_PERSONALITIES = List.of(
            Personality.MAURER, Personality.SOLIDE, Personality.ZOCKER);
    private static final Set<GameType> SUPPORTED_BID_TYPES = Set.of(
            GameType.RUFSPIEL, GameType.FARBWENZ, GameType.FARBSOLO, GameType.WENZ);

    private final RuleEngine rules = new RuleEngine();
    private final GameBiddingRules biddingRules = new GameBiddingRules();
    private final List<Player> players;
    private final Player human;
    private final SchafkopfGame game;
    private final GameBiddingAdvisor biddingAdvisor;
    private final BiddingAdvisor rufspielAdvisor;
    private GameContract currentBid;
    private Stage stage = Stage.BIDDING;
    private String message;
    private final String[] seatCalls = new String[4];
    private final List<BidMessage> bidMessages = new ArrayList<>();
    private boolean humanCallPending;
    private String previousHumanCall;
    private boolean awaitingNoGameChoice;
    private boolean bettingWindowOpen;
    private int bettingWindowHandledCardCount = -1;
    private final List<List<Card>> initialHands;
    private final int vorhandSeat;

    public GameSession(List<List<Card>> hands, PolicyGenome genome) {
        this(hands, genome, 0);
    }

    /** @param vorhandSeat seat left of the dealer: bids first and leads the first trick */
    public GameSession(List<List<Card>> hands, PolicyGenome genome, int vorhandSeat) {
        this(hands, genome, vorhandSeat, true, false, 0);
    }

    public GameSession(List<List<Card>> hands, PolicyGenome genome, int vorhandSeat, boolean humanKlopfen) {
        this(hands, genome, vorhandSeat, true, humanKlopfen, 0);
    }

    public GameSession(
            List<List<Card>> hands, PolicyGenome genome, int vorhandSeat,
            boolean humanKlopfen, int pendingStockDoubles
    ) {
        this(hands, genome, vorhandSeat, true, humanKlopfen, pendingStockDoubles);
    }

    private GameSession(
            List<List<Card>> hands,
            PolicyGenome genome,
            int vorhandSeat,
            boolean runBidding,
            boolean humanKlopfen,
            int pendingStockDoubles
    ) {
        if (vorhandSeat < 0 || vorhandSeat > 3) {
            throw new IllegalArgumentException("Vorhand must be seat 0-3");
        }
        this.vorhandSeat = vorhandSeat;
        Objects.requireNonNull(hands, "Hands must not be null");
        Objects.requireNonNull(genome, "Genome must not be null");
        if (hands.size() != 4 || hands.stream().anyMatch(hand -> hand.size() != 8)
                || hands.stream().flatMap(List::stream).distinct().count() != 32) {
            throw new IllegalArgumentException("A game session requires four unique eight-card hands");
        }
        if (pendingStockDoubles < 0 || pendingStockDoubles > 5) {
            throw new IllegalArgumentException("pendingStockDoubles must be between 0 and 5");
        }

        players = List.of(
                new Player("1", "Sepp"),
                new Player("2", "Hans"),
                new Player("3", "Franz"),
                new Player("4", "Du"));
        human = players.get(HUMAN_SEAT);
        initialHands = hands.stream().map(List::copyOf).toList();

        SimpleBot bot = new SimpleBot(rules, true, genome);
        game = new SchafkopfGame(players, rules, bot, SchafkopfConfig.defaults());
        game.setLogger(ignored -> { });
        game.setCustomHands(hands);
        if (pendingStockDoubles > 0) {
            game.bettingState().applyStock(1 << pendingStockDoubles);
        }
        biddingAdvisor = new GameBiddingAdvisor(
                rules, genome, ignored -> { }, ContractValueModel.empty());
        rufspielAdvisor = new BiddingAdvisor(rules, genome, ignored -> { }, ContractValueModel.empty());
        if (runBidding) {
            applyBotKlopfen(genome);
            if (humanKlopfen) {
                game.bettingState().applyDouble(human);
            }
            runBotBidding(true);
        }
    }

    private void applyBotKlopfen(PolicyGenome genome) {
        if (!SchafkopfConfig.defaults().klopfenEnabled()) {
            return;
        }
        BiddingAdvisor advisor = new BiddingAdvisor(rules, genome, ignored -> { }, ContractValueModel.empty());
        int cardsToCheck = SchafkopfConfig.defaults().klopfenAfterCards();
        for (Player player : players) {
            if (!player.equals(human)
                    && advisor.shouldKlopfen(player, player.hand().subList(0, cardsToCheck), rules)) {
                game.bettingState().applyDouble(player);
            }
        }
    }

    public int vorhandSeat() {
        return vorhandSeat;
    }

    public int dealerSeat() {
        return (vorhandSeat + 3) % 4;
    }

    /** Rückhand, the dealer's right-hand neighbour, cuts the deck. */
    public static int cutterSeat(int vorhandSeat) {
        return (vorhandSeat + 2) % 4;
    }

    /**
     * Serializes the complete game situation. Every played card of all seats is stored, so a restore
     * reproduces the table exactly instead of asking the bots again.
     */
    public String saveState() {
        Properties state = new Properties();
        state.setProperty("version", "2");
        state.setProperty("vorhand", String.valueOf(vorhandSeat));
        state.setProperty("stage", stage.name());
        state.setProperty("hands", String.join("|", initialHands.stream().map(GameSession::encodeCards).toList()));
        List<String> calls = new ArrayList<>();
        for (String call : seatCalls) {
            calls.add(call == null ? "" : call);
        }
        state.setProperty("calls", String.join("|", calls));
        state.setProperty("bid", encodeContract(currentBid));
        state.setProperty("contract", encodeContract(game.gameContract()));
        List<Card> plays = new ArrayList<>();
        game.completedTricks().forEach(trick -> trick.playedCards().forEach(played -> plays.add(played.card())));
        game.currentTrickCards().forEach(played -> plays.add(played.card()));
        state.setProperty("plays", encodeCards(plays));
        state.setProperty("message", message == null ? "" : message);
        state.setProperty("multiplier", Integer.toString(game.bettingState().multiplier()));
        BettingState.StakeBreakdown stake = game.bettingState().breakdown();
        state.setProperty("stockFactor", Integer.toString(stake.stockFactor()));
        state.setProperty("klopferIds", String.join(",", stake.klopferIds()));
        state.setProperty("additionalKlopfer", Integer.toString(stake.additionalKlopfer()));
        state.setProperty("kontra", Boolean.toString(stake.kontra()));
        state.setProperty("retour", Boolean.toString(stake.retour()));
        state.setProperty("humanCallPending", Boolean.toString(humanCallPending));
        state.setProperty("previousHumanCall", previousHumanCall == null ? "" : previousHumanCall);
        state.setProperty("awaitingNoGameChoice", Boolean.toString(awaitingNoGameChoice));
        state.setProperty("bettingWindowOpen", Boolean.toString(bettingWindowOpen));
        state.setProperty("bettingWindowHandledCardCount", Integer.toString(bettingWindowHandledCardCount));
        state.setProperty("bidMessages", String.join("|", bidMessages.stream()
            .map(entry -> entry.seat() + ";" + entry.text()).toList()));
        StringWriter writer = new StringWriter();
        try {
            state.store(writer, null);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return writer.toString();
    }

    /** Rebuilds a session saved with {@link #saveState()}; fails if the saved game is inconsistent. */
    public static GameSession restore(String saved, PolicyGenome genome) {
        Properties state = readSavedProperties(saved);
        String version = state.getProperty("version");
        if (!"1".equals(version) && !"2".equals(version)) {
            throw new IllegalArgumentException("Unsupported saved game version");
        }
        List<List<Card>> hands = java.util.Arrays.stream(state.getProperty("hands", "").split("\\|", -1))
                .map(GameSession::decodeCards)
                .toList();
        GameSession session = new GameSession(hands, genome,
            Integer.parseInt(state.getProperty("vorhand", "0")), false, false, 0);
        restoreBettingState(session, state, version);
        restoreBiddingState(session, state);
        restoreGameState(session, state);
        return session;
    }

    private static Properties readSavedProperties(String saved) {
        Properties state = new Properties();
        try {
            state.load(new StringReader(saved));
            return state;
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Unreadable saved game", invalid);
        }
    }

    private static void restoreBettingState(GameSession session, Properties state, String version) {
        BettingState bettingState = session.game.bettingState();
        int savedMultiplier = Integer.parseInt(state.getProperty("multiplier", "1"));
        if ("1".equals(version)) {
            bettingState.restoreLegacyMultiplier(savedMultiplier);
            return;
        }
        restoreKlopfers(session, state, bettingState);
        int stockFactor = Integer.parseInt(state.getProperty("stockFactor", "1"));
        if (stockFactor > 1) {
            bettingState.applyStock(stockFactor);
        }
        if (Boolean.parseBoolean(state.getProperty("kontra", DEFAULT_FALSE))) {
            bettingState.callKontra();
        }
        if (Boolean.parseBoolean(state.getProperty("retour", DEFAULT_FALSE))) {
            bettingState.callRetour();
        }
        if (savedMultiplier != bettingState.multiplier()) {
            throw new IllegalArgumentException("Saved stake breakdown does not match total multiplier");
        }
    }

    private static void restoreKlopfers(GameSession session, Properties state, BettingState bettingState) {
        String savedKlopfers = state.getProperty("klopferIds", "");
        if (!savedKlopfers.isEmpty()) {
            for (String id : savedKlopfers.split(",")) {
                Player player = session.players.stream().filter(seated -> seated.id().equals(id))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown klopfer: " + id));
                if (!bettingState.applyDouble(player)) {
                    throw new IllegalArgumentException("Duplicate klopfer: " + id);
                }
            }
        }
        int additionalKlopfer = Integer.parseInt(state.getProperty("additionalKlopfer", "0"));
        for (int index = 0; index < additionalKlopfer; index++) {
            bettingState.applyDouble();
        }
    }

    private static void restoreBiddingState(GameSession session, Properties state) {
        String[] calls = state.getProperty("calls", "").split("\\|", -1);
        for (int seat = 0; seat < Math.min(calls.length, 4); seat++) {
            session.seatCalls[seat] = calls[seat].isEmpty() ? null : calls[seat];
        }
        session.humanCallPending = Boolean.parseBoolean(state.getProperty("humanCallPending", DEFAULT_FALSE));
        session.awaitingNoGameChoice = Boolean.parseBoolean(state.getProperty("awaitingNoGameChoice", DEFAULT_FALSE));
        session.bettingWindowOpen = Boolean.parseBoolean(state.getProperty("bettingWindowOpen", DEFAULT_FALSE));
        session.bettingWindowHandledCardCount = Integer.parseInt(
            state.getProperty("bettingWindowHandledCardCount", "-1"));
        String previousCall = state.getProperty("previousHumanCall", "");
        session.previousHumanCall = previousCall.isEmpty() ? null : previousCall;
        String encodedMessages = state.getProperty("bidMessages", "");
        if (!encodedMessages.isEmpty()) {
            for (String encoded : encodedMessages.split("\\|")) {
                String[] parts = encoded.split(";", 2);
                session.bidMessages.add(new BidMessage(Integer.parseInt(parts[0]), parts[1]));
            }
        }
    }

    private static void restoreGameState(GameSession session, Properties state) {
        session.currentBid = session.decodeContract(state.getProperty("bid", ""));
        String savedMessage = state.getProperty("message", "");
        session.message = savedMessage.isEmpty() ? null : savedMessage;
        Stage savedStage = Stage.valueOf(state.getProperty("stage"));
        if (savedStage != Stage.BIDDING) {
            GameContract contract = session.decodeContract(state.getProperty("contract", ""));
            if (contract == null) {
                throw new IllegalArgumentException("Saved game has no contract");
            }
            session.game.setupContract(contract);
            session.game.startTrickPlay(session.players.get(session.vorhandSeat));
            session.stage = Stage.TRICK_PLAY;
            for (Card card : decodeCards(state.getProperty("plays", ""))) {
                session.game.playCard(card);
            }
            if (session.game.phase() == Phase.FINISHED) {
                session.stage = Stage.FINISHED;
            } else if (!session.bettingWindowOpen) {
                session.advanceBots();
            }
        }
    }

    private static String encodeCards(List<Card> cards) {
        return String.join(",", cards.stream().map(card -> card.suit().name() + ":" + card.rank().name()).toList());
    }

    private static List<Card> decodeCards(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(","))
                .map(code -> code.split(":"))
                .map(parts -> new Card(Suit.valueOf(parts[0]), Rank.valueOf(parts[1])))
                .toList();
    }

    private String encodeContract(GameContract contract) {
        if (contract == null) {
            return "";
        }
        Suit suit = contract instanceof GeierContract ? null : contractSuit(contract);
        return contract.gameType().name() + ";" + players.indexOf(contract.caller()) + ";"
                + (suit == null ? "" : suit.name()) + ";" + contract.isTout();
    }

    private GameContract decodeContract(String text) {
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(";", -1);
        GameType type = GameType.valueOf(parts[0]);
        int callerSeat = Integer.parseInt(parts[1]);
        Player caller = callerSeat < 0 ? null : players.get(callerSeat);
        Suit suit = parts[2].isEmpty() ? null : Suit.valueOf(parts[2]);
        boolean tout = Boolean.parseBoolean(parts[3]);
        return switch (type) {
            case RUFSPIEL -> new RufspielContract(caller, suit);
            case FARBSOLO -> new SoloContract(caller, suit, tout);
            case WENZ -> new WenzContract(caller, tout);
            case FARBWENZ -> new WenzContract(caller, suit);
            case GEIER -> new GeierContract(caller);
            case BETTEL -> new BettelContract(caller);
            case RAMSCH -> new RamschContract();
            case KREUZBOCK -> new KreuzbockContract();
        };
    }

    public Snapshot snapshot(Suit selectedSoloSuit) {
        Player activePlayer = game.currentPlayer();
        List<PlayedCard> played = game.currentTrickCards();
        GameContract activeContract = game.gameContract() != null ? game.gameContract() : currentBid;
        List<TableCard> tableCards = played.stream()
                .map(playedCard -> new TableCard(playedCard.player().name(), playedCard.card()))
                .toList();
        List<SeatSnapshot> seats = players.stream()
                .map(player -> new SeatSnapshot(
                player.id(), player.name(), player.hand().size(), player.totalAugen(),
                player.wonTricks().size(), player.equals(activePlayer)))
                .toList();
        TrickSnapshot lastCompletedTrick = null;
        List<TrickSnapshot> completedTricks = game.completedTricks().stream()
                .map(GameSession::trickSnapshot)
                .toList();
        if (!completedTricks.isEmpty()) {
            lastCompletedTrick = completedTricks.get(completedTricks.size() - 1);
        }

        return new Snapshot(
                stage,
                game.currentTrickNumber(),
            activeContract == null ? null : activeContract.displayName(),
            activeContract == null ? null : activeContract.gameType(),
            contractSuit(activeContract),
                activeContract != null && activeContract.isTout(),
                message,
                game.result() == null ? null : game.result().summary(),
                stage == Stage.TRICK_PLAY && human.equals(activePlayer),
                human.hand(),
                stage == Stage.TRICK_PLAY && human.equals(activePlayer)
                        ? game.legalMovesForCurrentPlayer() : List.of(),
                tableCards,
                seats,
                lastCompletedTrick,
                stage == Stage.BIDDING ? bidOptions(selectedSoloSuit) : List.of(),
                completedTricks,
                java.util.Arrays.asList(seatCalls.clone()),
                bidMessages,
                humanCallPending,
                awaitingNoGameChoice,
                game.bettingState().breakdown(),
                bettingWindowOpen,
                canCallKontra(HUMAN_SEAT),
                canCallRetour(HUMAN_SEAT),
                activeContract == null || activeContract.caller() == null
                    ? -1 : players.indexOf(activeContract.caller()));
    }

    private static TrickSnapshot trickSnapshot(Trick trick) {
        List<TableCard> cards = trick.playedCards().stream()
                .map(playedCard -> new TableCard(playedCard.player().name(), playedCard.card()))
                .toList();
        return new TrickSnapshot(trick.trickNumber(), cards,
                trick.winner() == null ? null : trick.winner().name(), trick.totalPoints());
    }

    public int humanSeat() {
        return HUMAN_SEAT;
    }

    public int stakeMultiplier() {
        return game.bettingState().multiplier();
    }

    public BettingState.StakeBreakdown stakeBreakdown() {
        return game.bettingState().breakdown();
    }

    public int stockDoubles() {
        return game.bettingState().stockDoubles();
    }

    public record SeatOutcome(String name, int eyes, boolean won) {
    }

    public record Outcome(
            boolean humanWon,
            int humanPartyEyes,
            List<Integer> seatPoints,
            List<SeatOutcome> seatOutcomes,
            String contractName,
            String description
    ) {
        public Outcome {
            seatPoints = List.copyOf(seatPoints);
            seatOutcomes = List.copyOf(seatOutcomes);
        }
    }

    public Outcome outcome() {
        requireStage(Stage.FINISHED);
        GameResult result = game.result();
        int perPlayer = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game).totalCentsPerPlayer();
        List<Integer> seatPoints = new ArrayList<>();
        List<SeatOutcome> seatOutcomes = new ArrayList<>();
        for (Player player : players) {
            boolean callerSide = result.callerTeam().contains(player);
            List<Player> ownSide = callerSide ? result.callerTeam() : result.defenderTeam();
            List<Player> otherSide = callerSide ? result.defenderTeam() : result.callerTeam();
            boolean won = callerSide == result.callerTeamWon();
            // In a 2:2 game every loser pays one winner; a single player settles with each opponent.
            int multiplier = ownSide.size() == otherSide.size() ? 1 : otherSide.size();
            seatPoints.add(perPlayer * multiplier * (won ? 1 : -1));
            seatOutcomes.add(new SeatOutcome(player.name(), player.totalAugen(), won));
        }
        boolean humanCallerSide = result.callerTeam().contains(human);
        return new Outcome(humanCallerSide == result.callerTeamWon(),
                humanCallerSide ? result.callerTeamAugen() : result.defenderTeamAugen(),
                seatPoints, seatOutcomes, game.gameContract().displayName(),
                new Tariff(SchafkopfConfig.defaults()).calculatePayment(game).description());
    }

    public SchafkopfGame finishedGame() {
        requireStage(Stage.FINISHED);
        return game;
    }

    private static Suit contractSuit(GameContract contract) {
        if (contract instanceof RufspielContract rufspiel) {
            return rufspiel.calledSuit();
        }
        if (contract instanceof SoloContract solo) {
            return solo.trumpSuit();
        }
        if (contract instanceof WenzContract wenz) {
            return wenz.trumpSuit();
        }
        return null;
    }

    public Snapshot announce(String optionId, Suit selectedSoloSuit) {
        requireStage(Stage.BIDDING);
        GameContract candidate = contractFor(optionId);
        if (candidate == null || !biddingRules.canAnnounce(candidate, currentBid)) {
            throw new IllegalArgumentException("That contract cannot overbid the current announcement");
        }
        String call = currentBid == null ? CALL_PLAY : CALL_AGREE;
        if (!humanCallPending) {
            seatCalls[HUMAN_SEAT] = call;
            bidMessages.add(new BidMessage(HUMAN_SEAT, call));
        }
        humanCallPending = false;
        previousHumanCall = null;
        currentBid = candidate;
        bidMessages.add(new BidMessage(HUMAN_SEAT, contractLabel(candidate)));
        runBotBidding(false);
        message = currentBid == candidate
            ? "Du sagst " + call
            : currentBid.caller().name() + " sagt Auch!!";
        startGame(currentBid);
        return snapshot(selectedSoloSuit);
    }

    public Snapshot prepareHumanPlay(Suit selectedSoloSuit) {
        requireStage(Stage.BIDDING);
        if (!humanCallPending) {
            previousHumanCall = seatCalls[HUMAN_SEAT];
            String call = currentBid == null ? CALL_PLAY : CALL_AGREE;
            seatCalls[HUMAN_SEAT] = call;
            bidMessages.add(new BidMessage(HUMAN_SEAT, call));
            humanCallPending = true;
        }
        return snapshot(selectedSoloSuit);
    }

    public Snapshot cancelHumanPlay(Suit selectedSoloSuit) {
        requireStage(Stage.BIDDING);
        if (humanCallPending) {
            BidMessage pendingMessage = bidMessages.get(bidMessages.size() - 1);
            if (pendingMessage.seat() == HUMAN_SEAT
                    && (pendingMessage.text().equals(CALL_PLAY) || pendingMessage.text().equals(CALL_AGREE))) {
                bidMessages.remove(bidMessages.size() - 1);
            }
            seatCalls[HUMAN_SEAT] = previousHumanCall;
            previousHumanCall = null;
            humanCallPending = false;
        }
        return snapshot(selectedSoloSuit);
    }

    public Snapshot pass(Suit selectedSoloSuit) {
        requireStage(Stage.BIDDING);
        if (humanCallPending) {
            bidMessages.remove(bidMessages.size() - 1);
            humanCallPending = false;
            previousHumanCall = null;
        }
        seatCalls[HUMAN_SEAT] = CALL_PASS;
        bidMessages.add(new BidMessage(HUMAN_SEAT, CALL_PASS));
        runBotBidding(false);
        if (currentBid == null) {
            awaitingNoGameChoice = true;
            message = "Alle sagen weiter. Bitte die Regel für ‚Keiner spielt‘ wählen.";
            return snapshot(selectedSoloSuit);
        }
        message = contractLabel(currentBid) + " von " + currentBid.caller().name() + " wird gespielt.";
        GameContract selected = currentBid;
        startGame(selected);
        return snapshot(selectedSoloSuit);
    }

    public NoGameSelectionResult selectNoGamePolicy(SchafkopfConfig.NoGamePolicy policy) {
        requireStage(Stage.BIDDING);
        if (!awaitingNoGameChoice) {
            throw new IllegalStateException("No no-game policy is currently requested");
        }
        if (policy == SchafkopfConfig.NoGamePolicy.STOCK) {
            if (game.bettingState().stockDoubles() == 5) {
                awaitingNoGameChoice = false;
                message = "Max. Stock erreicht!";
                return NoGameSelectionResult.STOCK_MAXED;
            }
            game.bettingState().applyStock(2);
            awaitingNoGameChoice = false;
            if (game.bettingState().stockDoubles() == 5) {
                message = "Max. Stock erreicht!";
                return NoGameSelectionResult.STOCK_MAXED;
            }
            return NoGameSelectionResult.STOCK_ADDED;
        }
        if (policy == SchafkopfConfig.NoGamePolicy.ZAMMWERFEN) {
            awaitingNoGameChoice = false;
            message = "Zusammengeworfen. Es wird neu gegeben.";
            return NoGameSelectionResult.ZAMMWERFEN;
        }
        return NoGameSelectionResult.NOT_IMPLEMENTED;
    }

    public Snapshot playHumanCard(Card card, Suit selectedSoloSuit) {
        requireStage(Stage.TRICK_PLAY);
        if (!human.equals(game.currentPlayer())) {
            throw new IllegalStateException("It is not the human player's turn");
        }
        if (bettingWindowOpen) {
            bettingWindowHandledCardCount = game.currentTrickCards().size();
            bettingWindowOpen = false;
        }
        game.playCard(card);
        advanceBots();
        return snapshot(selectedSoloSuit);
    }

    public Snapshot continueAfterBettingWindow(Suit selectedSoloSuit) {
        requireStage(Stage.TRICK_PLAY);
        if (bettingWindowOpen) {
            bettingWindowHandledCardCount = game.currentTrickCards().size();
            bettingWindowOpen = false;
        }
        advanceBots();
        return snapshot(selectedSoloSuit);
    }

    public boolean canCallKontra(int seat) {
        if (!isBettingWindowOpen() || game.currentTrickCards().size() >= 2
            || seat < 0 || seat >= players.size()) {
            return false;
        }
        return !isCallerSide(players.get(seat)) && game.bettingState().kontraCount() == 0;
    }

    public boolean callKontra(int seat) {
        if (!canCallKontra(seat) || !game.bettingState().callKontra()) {
            return false;
        }
        closeBettingWindowAndAdvance();
        return true;
    }

    public boolean canCallRetour(int seat) {
        if (!isBettingWindowOpen() || game.currentTrickCards().size() != 2
            || !human.equals(game.currentPlayer()) || seat < 0 || seat >= players.size()) {
            return false;
        }
        return isCallerSide(players.get(seat)) && game.bettingState().kontraCount() == 1
                && game.bettingState().retourCount() == 0;
    }

    public boolean callRetour(int seat) {
        if (!canCallRetour(seat) || !game.bettingState().callRetour()) {
            return false;
        }
        closeBettingWindowAndAdvance();
        return true;
    }

    /**
     * Bots bid clockwise from Vorhand. Players behind must strictly overbid, since the player sitting
     * further ahead keeps the game on equal rank.
     */
    private void runBotBidding(boolean beforeHuman) {
        int humanPosition = (HUMAN_SEAT - vorhandSeat + 4) % 4;
        for (int position = 0; position < 4; position++) {
            if (position == humanPosition || (position < humanPosition) != beforeHuman) {
                continue;
            }
            int seat = (vorhandSeat + position) % 4;
            Player player = players.get(seat);
            GameBiddingAdvisor.ContractRecommendation recommendation = biddingAdvisor.recommend(
                    player, BOT_PERSONALITIES.get(seat), SUPPORTED_BID_TYPES);
            GameContract candidate = recommendation == null ? null : recommendation.contract();
            if (candidate != null && biddingRules.supports(candidate.gameType())
                    && biddingRules.canAnnounce(candidate, currentBid)) {
                String call = currentBid == null ? CALL_PLAY : CALL_AGREE;
                currentBid = candidate;
                message = player.name() + " sagt " + call;
                seatCalls[seat] = call;
                bidMessages.add(new BidMessage(seat, call));
                bidMessages.add(new BidMessage(seat, contractLabel(candidate)));
            } else {
                message = player.name() + " sagt weiter.";
                seatCalls[seat] = CALL_PASS;
                bidMessages.add(new BidMessage(seat, CALL_PASS));
            }
        }
    }

    private List<BidOption> bidOptions(Suit soloSuit) {
        List<BidOption> options = new ArrayList<>();
        addBidOption(options, "SOLO_TOUT_" + soloSuit.name(), "Solo Tout · " + suitLabel(soloSuit),
                new SoloContract(human, soloSuit, true));
        addBidOption(options, "WENZ_TOUT", "Wenz Tout", new WenzContract(human, true));
        addBidOption(options, "SOLO_" + soloSuit.name(), "Solo · " + suitLabel(soloSuit),
                new SoloContract(human, soloSuit));
        addBidOption(options, "WENZ", "Wenz", new WenzContract(human));
        addBidOption(options, FARBWENZ_OPTION_PREFIX + soloSuit.name(), "Farbwenz · " + suitLabel(soloSuit),
            new WenzContract(human, soloSuit));
        List<Suit> validCalledSuits = rufspielAdvisor.getValidRufspielSuits(human);
        if (validCalledSuits.isEmpty()) {
            options.add(new BidOption("SAUSPIEL", "Sauspiel", false, true));
        } else {
            for (Suit suit : validCalledSuits) {
                GameContract contract = new RufspielContract(human, suit);
                options.add(new BidOption(SAUSPIEL_OPTION_PREFIX + suit.name(), "Sauspiel · " + suitLabel(suit),
                        biddingRules.canAnnounce(contract, currentBid), true));
            }
        }
        options.add(new BidOption("GEIER", "Geier", true, false));
        options.add(new BidOption("BETTEL", "Bettel", true, false));
        options.add(new BidOption("RAMSCH", "Ramsch", true, false));
        options.add(new BidOption("KREUZBOCK", "Kreuzbock", true, false));
        return List.copyOf(options);
    }

    private void addBidOption(List<BidOption> options, String id, String label, GameContract contract) {
        options.add(new BidOption(id, label, biddingRules.canAnnounce(contract, currentBid), true));
    }

    private GameContract contractFor(String optionId) {
        if (optionId.startsWith(SAUSPIEL_OPTION_PREFIX)) {
            Suit calledSuit = Suit.valueOf(optionId.substring(SAUSPIEL_OPTION_PREFIX.length()));
            return rufspielAdvisor.getValidRufspielSuits(human).contains(calledSuit)
                    ? new RufspielContract(human, calledSuit) : null;
        }
        if (optionId.startsWith(FARBWENZ_OPTION_PREFIX)) {
            return new WenzContract(human, Suit.valueOf(optionId.substring(FARBWENZ_OPTION_PREFIX.length())));
        }
        return switch (optionId) {
            case "SOLO_" + "EICHEL", "SOLO_" + "GRAS", "SOLO_" + "HERZ", "SOLO_" + "SCHELLEN" ->
                    new SoloContract(human, Suit.valueOf(optionId.substring("SOLO_".length())));
            case "SOLO_TOUT_" + "EICHEL", "SOLO_TOUT_" + "GRAS", "SOLO_TOUT_" + "HERZ", "SOLO_TOUT_" + "SCHELLEN" ->
                    new SoloContract(human, Suit.valueOf(optionId.substring("SOLO_TOUT_".length())), true);
            case "WENZ" -> new WenzContract(human);
            case "WENZ_TOUT" -> new WenzContract(human, true);
            default -> null;
        };
    }

    private void startGame(GameContract contract) {
        game.setupContract(contract);
        game.startTrickPlay(players.get(vorhandSeat));
        stage = Stage.TRICK_PLAY;
        bettingWindowOpen = true;
        bettingWindowHandledCardCount = -1;
    }

    private void advanceBots() {
        while (game.phase() == Phase.TRICK_PLAY) {
            int playedCards = game.currentTrickCards().size();
            if (game.currentTrickNumber() == 1 && playedCards < 2
                    && bettingWindowHandledCardCount != playedCards) {
                bettingWindowOpen = true;
                return;
            }
            if (human.equals(game.currentPlayer())) {
                break;
            }
            game.playBotTurn();
        }
        if (game.phase() == Phase.FINISHED) {
            stage = Stage.FINISHED;
            message = "Spiel beendet.";
        } else if (stage == Stage.TRICK_PLAY && game.currentTrickNumber() == 1
                && game.currentTrickCards().size() == 2
                && bettingWindowHandledCardCount != 2) {
            bettingWindowOpen = true;
            if (!canCallRetour(HUMAN_SEAT)) {
                bettingWindowOpen = false;
            }
        }
    }

    private boolean isBettingWindowOpen() {
        return stage == Stage.TRICK_PLAY && bettingWindowOpen && game.currentTrickNumber() == 1
            && game.currentTrickCards().size() <= 2;
    }

    private boolean isCallerSide(Player player) {
        GameContract contract = game.gameContract();
        return contract != null && (contract.caller() != null && contract.caller().equals(player) || game.partner() != null
                && game.partner().equals(player));
    }

    private void closeBettingWindowAndAdvance() {
        bettingWindowHandledCardCount = game.currentTrickCards().size();
        bettingWindowOpen = false;
        advanceBots();
    }

    private void requireStage(Stage expected) {
        if (stage != expected) {
            throw new IllegalStateException("Expected session stage " + expected + " but was " + stage);
        }
    }

    private static String contractLabel(GameContract contract) {
        if (contract.gameType() == GameType.RUFSPIEL) {
            return "Sauspiel";
        }
        if (contract.gameType() == GameType.WENZ) {
            return contract.isTout() ? "Wenz Tout" : "Wenz";
        }
        if (contract.gameType() == GameType.FARBSOLO) {
            return contract.isTout() ? "Solo Tout" : "Solo";
        }
        return contract.displayName();
    }

    private static String suitLabel(Suit suit) {
        return suit == Suit.SCHELLEN ? "Schelln" : suit.displayName();
    }
}