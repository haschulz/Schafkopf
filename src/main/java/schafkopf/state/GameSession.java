package schafkopf.state;

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

    public record BidOption(String id, String label, boolean enabled) {
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
                List<BidMessage> bidMessages
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
    private final List<List<Card>> initialHands;
    private final int vorhandSeat;

    public GameSession(List<List<Card>> hands, PolicyGenome genome) {
        this(hands, genome, 0);
    }

    /** @param vorhandSeat seat left of the dealer: bids first and leads the first trick */
    public GameSession(List<List<Card>> hands, PolicyGenome genome, int vorhandSeat) {
        this(hands, genome, vorhandSeat, true, false);
    }

    public GameSession(List<List<Card>> hands, PolicyGenome genome, int vorhandSeat, boolean humanKlopfen) {
        this(hands, genome, vorhandSeat, true, humanKlopfen);
    }

    private GameSession(
            List<List<Card>> hands,
            PolicyGenome genome,
            int vorhandSeat,
            boolean runBidding,
            boolean humanKlopfen
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
        state.setProperty("version", "1");
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
        state.setProperty("humanCallPending", Boolean.toString(humanCallPending));
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
        Properties state = new Properties();
        try {
            state.load(new StringReader(saved));
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Unreadable saved game", invalid);
        }
        if (!"1".equals(state.getProperty("version"))) {
            throw new IllegalArgumentException("Unsupported saved game version");
        }
        List<List<Card>> hands = java.util.Arrays.stream(state.getProperty("hands", "").split("\\|", -1))
                .map(GameSession::decodeCards)
                .toList();
        GameSession session = new GameSession(hands, genome,
                Integer.parseInt(state.getProperty("vorhand", "0")), false, false);
        int savedMultiplier = Integer.parseInt(state.getProperty("multiplier", "1"));
        if (savedMultiplier > 1) {
            session.game.bettingState().applyStock(savedMultiplier);
        }
        String[] calls = state.getProperty("calls", "").split("\\|", -1);
        for (int seat = 0; seat < Math.min(calls.length, 4); seat++) {
            session.seatCalls[seat] = calls[seat].isEmpty() ? null : calls[seat];
        }
        session.humanCallPending = Boolean.parseBoolean(state.getProperty("humanCallPending", "false"));
        String encodedMessages = state.getProperty("bidMessages", "");
        if (!encodedMessages.isEmpty()) {
            for (String encoded : encodedMessages.split("\\|")) {
                String[] parts = encoded.split(";", 2);
                session.bidMessages.add(new BidMessage(Integer.parseInt(parts[0]), parts[1]));
            }
        }
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
            } else {
                session.advanceBots();
            }
        }
        return session;
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
                bidMessages);
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

    public record Outcome(boolean humanWon, int humanPartyEyes, List<Integer> seatPoints, String description) {
        public Outcome {
            seatPoints = List.copyOf(seatPoints);
        }
    }

    public Outcome outcome() {
        requireStage(Stage.FINISHED);
        GameResult result = game.result();
        int perPlayer = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game).totalCentsPerPlayer();
        List<Integer> seatPoints = new ArrayList<>();
        for (Player player : players) {
            boolean callerSide = result.callerTeam().contains(player);
            List<Player> ownSide = callerSide ? result.callerTeam() : result.defenderTeam();
            List<Player> otherSide = callerSide ? result.defenderTeam() : result.callerTeam();
            boolean won = callerSide == result.callerTeamWon();
            // In a 2:2 game every loser pays one winner; a single player settles with each opponent.
            int multiplier = ownSide.size() == otherSide.size() ? 1 : otherSide.size();
            seatPoints.add(perPlayer * multiplier * (won ? 1 : -1));
        }
        boolean humanCallerSide = result.callerTeam().contains(human);
        return new Outcome(humanCallerSide == result.callerTeamWon(),
                humanCallerSide ? result.callerTeamAugen() : result.defenderTeamAugen(),
                seatPoints, new Tariff(SchafkopfConfig.defaults()).calculatePayment(game).description());
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
        GameContract candidate = contractFor(optionId, selectedSoloSuit);
        if (candidate == null || !biddingRules.canAnnounce(candidate, currentBid)) {
            throw new IllegalArgumentException("That contract cannot overbid the current announcement");
        }
        String call = currentBid == null ? "Spiele!" : "Auch!";
        if (!humanCallPending) {
            seatCalls[HUMAN_SEAT] = call;
            bidMessages.add(new BidMessage(HUMAN_SEAT, call));
        }
        humanCallPending = false;
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
            String call = currentBid == null ? "Spiele!" : "Auch!";
            seatCalls[HUMAN_SEAT] = call;
            bidMessages.add(new BidMessage(HUMAN_SEAT, call));
            humanCallPending = true;
        }
        return snapshot(selectedSoloSuit);
    }

    public Snapshot pass(Suit selectedSoloSuit) {
        requireStage(Stage.BIDDING);
        if (humanCallPending) {
            bidMessages.remove(bidMessages.size() - 1);
            humanCallPending = false;
        }
        seatCalls[HUMAN_SEAT] = "Weiter";
        bidMessages.add(new BidMessage(HUMAN_SEAT, "Weiter"));
        runBotBidding(false);
        GameContract selected = currentBid == null ? new RamschContract() : currentBid;
        message = currentBid == null
                ? "Alle sagen weiter. Es wird Ramsch gespielt."
                : contractLabel(currentBid) + " von " + currentBid.caller().name() + " wird gespielt.";
        startGame(selected);
        return snapshot(selectedSoloSuit);
    }

    public Snapshot playHumanCard(Card card, Suit selectedSoloSuit) {
        requireStage(Stage.TRICK_PLAY);
        if (!human.equals(game.currentPlayer())) {
            throw new IllegalStateException("It is not the human player's turn");
        }
        game.playCard(card);
        advanceBots();
        return snapshot(selectedSoloSuit);
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
                String call = currentBid == null ? "Spiele!" : "Auch!";
                currentBid = candidate;
                message = player.name() + " sagt " + call;
                seatCalls[seat] = call;
                bidMessages.add(new BidMessage(seat, call));
                bidMessages.add(new BidMessage(seat, contractLabel(candidate)));
            } else {
                message = player.name() + " sagt weiter.";
                seatCalls[seat] = "Weiter";
                bidMessages.add(new BidMessage(seat, "Weiter"));
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
        addBidOption(options, "FARBWENZ_" + soloSuit.name(), "Farbwenz · " + suitLabel(soloSuit),
            new WenzContract(human, soloSuit));
        List<Suit> validCalledSuits = rufspielAdvisor.getValidRufspielSuits(human);
        if (validCalledSuits.isEmpty()) {
            options.add(new BidOption("SAUSPIEL", "Sauspiel", false));
        } else {
            for (Suit suit : validCalledSuits) {
                GameContract contract = new RufspielContract(human, suit);
                options.add(new BidOption("SAUSPIEL_" + suit.name(), "Sauspiel · " + suitLabel(suit),
                        biddingRules.canAnnounce(contract, currentBid)));
            }
        }
        return List.copyOf(options);
    }

    private void addBidOption(List<BidOption> options, String id, String label, GameContract contract) {
        options.add(new BidOption(id, label, biddingRules.canAnnounce(contract, currentBid)));
    }

    private GameContract contractFor(String optionId, Suit soloSuit) {
        if (optionId.startsWith("SAUSPIEL_")) {
            Suit calledSuit = Suit.valueOf(optionId.substring("SAUSPIEL_".length()));
            return rufspielAdvisor.getValidRufspielSuits(human).contains(calledSuit)
                    ? new RufspielContract(human, calledSuit) : null;
        }
        if (optionId.startsWith("FARBWENZ_")) {
            return new WenzContract(human, Suit.valueOf(optionId.substring("FARBWENZ_".length())));
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
        advanceBots();
    }

    private void advanceBots() {
        while (game.phase() == Phase.TRICK_PLAY && !human.equals(game.currentPlayer())) {
            game.playBotTurn();
        }
        if (game.phase() == Phase.FINISHED) {
            stage = Stage.FINISHED;
            message = "Spiel beendet.";
        }
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