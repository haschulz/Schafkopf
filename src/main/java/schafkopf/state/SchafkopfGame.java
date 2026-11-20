package schafkopf.state;

import schafkopf.ai.GameMemory;
import schafkopf.ai.DecisionAnalysis;
import schafkopf.ai.DecisionInformation;
import schafkopf.analysis.GameRecord;
import schafkopf.ai.SimpleBot;
import schafkopf.config.SchafkopfConfig;
import schafkopf.model.Card;
import schafkopf.model.BettingState;
import schafkopf.model.Deck;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.KreuzbockContract;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.RamschContract;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

public class SchafkopfGame {
    private record RamschOutcome(List<Player> winners, List<Player> losers, boolean durchmarsch) {
    }

    private final List<Player> players;
    private final RuleEngine rules;
    private final Function<Player, SimpleBot> botProvider;
    private final Deck deck;
    private final BettingState bettingState = new BettingState();
    private final SchafkopfConfig config;

    private Phase currentPhase = Phase.NOT_STARTED;
    private GameContract contract;
    private Player partner;
    private GameMemory memory;
    private final List<Trick> completedTricks = new ArrayList<>();
    private Trick currentTrick;
    private Player trickLeader;
    private int currentTrickNumber;
    private int turnIndex;
    private boolean calledSauPlayed = false;
    private boolean schneiderPaid = true;
    private boolean schwarzClaimed = true;
    private GameResult result;
    private DecisionAnalysis decisionAnalysis;
    private DecisionAnalysis.Report lastDecisionAnalysis;
    private int analysisSamples;
    private int openingLeaderSeat;

            private Consumer<String> logger = java.util.logging.Logger
                .getLogger(SchafkopfGame.class.getName())::info;

    public SchafkopfGame(List<Player> players, RuleEngine rules) {
        this(players, rules, new SimpleBot(rules), SchafkopfConfig.load());
    }

    public SchafkopfGame(List<Player> players, RuleEngine rules, SimpleBot bot) {
        this(players, rules, bot, SchafkopfConfig.load());
    }

    public SchafkopfGame(List<Player> players, RuleEngine rules, SimpleBot bot, SchafkopfConfig config) {
        this(players, rules, player -> bot, config);
    }

    public SchafkopfGame(
            List<Player> players,
            RuleEngine rules,
            Function<Player, SimpleBot> botProvider,
            SchafkopfConfig config
    ) {
        if (players.size() != 4) {
            throw new IllegalArgumentException("Schafkopf requires exactly 4 players.");
        }
        this.players = players;
        this.rules = rules;
        this.botProvider = botProvider;
        this.deck = new Deck();
        this.config = config;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public Phase phase() {
        return currentPhase;
    }

    public RufspielContract contract() {
        if (!(contract instanceof RufspielContract rufspielContract)) {
            throw new IllegalStateException("The active game is not a Rufspiel");
        }
        return rufspielContract;
    }

    public GameContract gameContract() {
        return contract;
    }

    public Player partner() {
        return partner;
    }

    public List<Trick> completedTricks() {
        return completedTricks;
    }

    public GameResult result() {
        return result;
    }

    public Player currentPlayer() {
        if (currentPhase != Phase.TRICK_PLAY || currentTrick == null) {
            return null;
        }
        int leaderIndex = players.indexOf(trickLeader);
        return players.get((leaderIndex + turnIndex) % players.size());
    }

    public int currentTrickNumber() {
        return currentTrickNumber;
    }

    public List<PlayedCard> currentTrickCards() {
        return currentTrick == null ? List.of() : List.copyOf(currentTrick.playedCards());
    }

    public List<Card> legalMovesForCurrentPlayer() {
        Player player = currentPlayer();
        if (player == null) {
            return List.of();
        }
        return List.copyOf(rules.getLegalMovesForContract(
                player, currentTrick, contract, calledSauPlayed, currentTrickNumber));
    }

    public List<Player> players() {
        return List.copyOf(players);
    }

    public BettingState bettingState() {
        return bettingState;
    }

    public boolean schneiderPaid() {
        return schneiderPaid;
    }

    public void setSchneiderPaid(boolean paid) {
        this.schneiderPaid = paid;
    }

    public boolean schwarzClaimed() {
        return schwarzClaimed;
    }

    public void setSchwarzClaimed(boolean claimed) {
        this.schwarzClaimed = claimed;
    }

    public SchafkopfConfig config() {
        return config;
    }

    public GameMemory memory() {
        return memory;
    }

    public void enableDecisionAnalysis(int samples) {
        if (samples < 2 || samples > 10000) {
            throw new IllegalArgumentException("Decision analysis needs 2 to 10000 worlds");
        }
        decisionAnalysis = new DecisionAnalysis(DecisionAnalysis.opponentPool());
        analysisSamples = samples;
    }

    public DecisionAnalysis.Report lastDecisionAnalysis() {
        return lastDecisionAnalysis;
    }

    public DecisionInformation decisionInformation() {
        Player observer = currentPlayer();
        if (observer == null) {
            throw new IllegalStateException("No pending decision");
        }
        List<GameRecord.Play> plays = new ArrayList<>();
        completedTricks.forEach(trick -> trick.playedCards().forEach(play ->
                plays.add(new GameRecord.Play(players.indexOf(play.player()), play.card()))));
        currentTrick.playedCards().forEach(play ->
                plays.add(new GameRecord.Play(players.indexOf(play.player()), play.card())));
        return new DecisionInformation(players.stream().map(Player::name).toList(), players.indexOf(observer),
                observer.hand(), plays, GameRecord.ContractSpec.of(contract, players), openingLeaderSeat,
                config, bettingState.multiplier());
    }

    /**
     * Deals cards randomly to all 4 players.
     */
    public void dealRandom() {
        deal(null, null);
    }

    public void dealRandom(long seed) {
        deal(new Random(seed), null);
    }

    public void dealRandom(long seed, int cutCardsFromTop) {
        deal(new Random(seed), cutCardsFromTop);
    }

    private void deal(Random random, Integer cutCardsFromTop) {
        transitionTo(Phase.DEALING);
        deck.reset();
        if (random == null) {
            deck.shuffle();
        } else {
            deck.shuffle(random);
        }
        if (cutCardsFromTop != null) {
            deck.cut(cutCardsFromTop);
        }
        List<List<Card>> hands = deck.deal4x8();
        for (int i = 0; i < 4; i++) {
            players.get(i).reset();
            players.get(i).setHand(hands.get(i));
        }
        log("=== Karten ausgeteilt (4 x 8 Karten) ===");
    }

    /**
     * Sets predetermined hands (useful for testing specific scenarios).
     */
    public void setCustomHands(List<List<Card>> hands) {
        transitionTo(Phase.DEALING);
        for (int i = 0; i < 4; i++) {
            players.get(i).reset();
            players.get(i).setHand(new ArrayList<>(hands.get(i)));
        }
    }

    /**
     * Sets contract explicitly (useful for scenario tests).
     */
    public void setupRufspiel(Player caller, Suit calledSuit) {
        setupContract(new RufspielContract(caller, calledSuit));
        }

        public void setupContract(GameContract contract) {
        transitionTo(Phase.BIDDING_CALLING);
            if (contract.caller() != null && !players.contains(contract.caller())) {
                throw new IllegalArgumentException("Contract caller must be seated at this table");
            }
        this.contract = contract;
        this.partner = null;
        this.memory = new GameMemory(players, rules, contract);

        if (contract instanceof RufspielContract rufspielContract) {
            Card calledSau = rufspielContract.calledSau();
            this.partner = players.stream()
                .filter(player -> player.hasCard(calledSau))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Called Sau " + calledSau + " not found in any player hand!"));
            log(String.format("Ansage: %s ruft auf die %s-Sau!", contract.caller().name(),
                rufspielContract.calledSuit().displayName()));
            log(String.format("-> Geheimer Mitspieler: %s (besitzt %s)", partner.name(), calledSau.shortName()));
            return;
        }

        log("Ansage: " + contract.displayName());
    }

    /**
     * Automatically finds an eligible caller and called suit based on dealt hands.
     */
    public void autoDetermineRufspiel() {
        transitionTo(Phase.BIDDING_CALLING);
        for (Player candidate : players) {
            for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
                Card sau = new Card(suit, Rank.SAU);
                boolean hasSau = candidate.hasCard(sau);
                boolean hasOtherOfSuit = candidate.hand().stream()
                        .anyMatch(c -> rules.isNonTrumpSuit(c, suit) && !c.equals(sau));

                if (!hasSau && hasOtherOfSuit) {
                    setupRufspiel(candidate, suit);
                    return;
                }
            }
        }
        // Fallback: Default to first valid suit
        setupRufspiel(players.get(0), Suit.GRAS);
    }

    /**
     * Plays the entire 8 tricks using the Bot logic with full explanatory logging.
     */
    public GameResult playFullGame(Player startingLeader) {
        startTrickPlay(startingLeader);
        while (currentPhase == Phase.TRICK_PLAY) {
            playBotTurn();
        }
        return result;
    }

    public void startTrickPlay(Player startingLeader) {
        if (contract == null) {
            throw new IllegalStateException("No game contract has been selected");
        }
        if (!players.contains(startingLeader)) {
            throw new IllegalArgumentException("Starting leader must be seated at this table");
        }
        transitionTo(Phase.TRICK_PLAY);
        if (memory == null) {
            memory = new GameMemory(players, rules, contract);
        }
        currentTrickNumber = 1;
        turnIndex = 0;
        trickLeader = startingLeader;
        openingLeaderSeat = players.indexOf(startingLeader);
        currentTrick = new Trick(currentTrickNumber, trickLeader);
    }

    public MoveExplanation playBotTurn() {
        Player player = currentPlayer();
        if (player == null) {
            throw new IllegalStateException("The game is not waiting for a card play");
        }
        List<Player> remainingPlayers = remainingPlayers();
        MoveExplanation explanation = chooseBotMove(player, remainingPlayers);
        explanation = analyseBotMove(explanation);
        int trickNumber = currentTrickNumber;
        int turnNumber = turnIndex + 1;
        playCard(explanation.card());
        log(explanation.toLogLine(trickNumber, turnNumber));
        return explanation;
    }

    private MoveExplanation analyseBotMove(MoveExplanation heuristic) {
        if (decisionAnalysis == null || legalMovesForCurrentPlayer().size() == 1
                || !List.of(GameType.RUFSPIEL, GameType.FARBSOLO, GameType.WENZ, GameType.FARBWENZ)
                        .contains(contract.gameType())) {
            return heuristic;
        }
        DecisionInformation information = decisionInformation();
        try {
            lastDecisionAnalysis = decisionAnalysis.cards(information, analysisSamples, information.seed());
        } catch (IllegalStateException unavailable) {
            return new MoveExplanation(currentPlayer(), heuristic.card(), heuristic.ruleRequirement(),
                    heuristic.tacticalReasoning() + " | Keine konsistente Stichprobe: Heuristik, keine Gewinnschätzung", true);
        }
        String selection = lastDecisionAnalysis.selectedAction();
        Card selected = legalMovesForCurrentPlayer().stream()
                .filter(card -> card.shortName().equals(selection)).findFirst().orElseThrow();
        var estimate = lastDecisionAnalysis.alternatives().get(selection);
        String reasoning = String.format(java.util.Locale.ROOT,
                "Informationsgerechte Analyse: %s, Gewinn %.1f%% [%.1f; %.1f], Erwartung %.2f ct +/- %.2f; "
                        + "Gegner %.1f Augen / %.1f Stiche, %d Welten, %d Policies. %s",
                selection, 100 * estimate.winProbability(), 100 * estimate.probabilityLow(),
                100 * estimate.probabilityHigh(), estimate.expectedReturnCents(),
                estimate.returnStandardErrorCents(), estimate.opponentEyes(), estimate.opponentTricks(),
                estimate.worlds(), lastDecisionAnalysis.opponentPolicies().size(), lastDecisionAnalysis.uncertainty());
        return new MoveExplanation(currentPlayer(), selected, heuristic.ruleRequirement(), reasoning, true);
    }

    public MoveExplanation previewBotTurn() {
        Player player = currentPlayer();
        if (player == null) {
            throw new IllegalStateException("No pending card decision");
        }
        return chooseBotMove(player, remainingPlayers());
    }

    public void playCard(Card card) {
        Player player = currentPlayer();
        if (player == null) {
            throw new IllegalStateException("The game is not waiting for a card play");
        }
        if (!legalMovesForCurrentPlayer().contains(card)) {
            throw new IllegalArgumentException("Illegal card for " + player.name() + ": " + card);
        }

        player.removeCard(card);
        int previousObservations = memory == null ? 0 : memory.observationLogs().size();
        if (memory != null) {
            memory.recordCardPlayed(player, card, currentTrick, contract);
        }
        currentTrick.addCard(player, card);

        if (contract instanceof RufspielContract rufspielContract && card.equals(rufspielContract.calledSau())) {
            calledSauPlayed = true;
        }

        if (memory != null) {
            List<String> observations = memory.observationLogs();
            for (int index = previousObservations; index < observations.size(); index++) {
                log("   [Gedächtnis] " + observations.get(index));
            }
        }

        turnIndex++;
        if (currentTrick.isComplete()) {
            completeCurrentTrick();
        }
    }

    private MoveExplanation chooseBotMove(Player player, List<Player> remainingPlayers) {
        if (contract instanceof RufspielContract rufspielContract) {
            return botProvider.apply(player).chooseFromPublicInformation(
                player, currentTrick, rufspielContract, calledSauPlayed,
                    currentTrickNumber, memory, remainingPlayers);
        }
        String roleOverride = null;
        if (contract instanceof KreuzbockContract) {
            roleOverride = players.indexOf(player) % 2 == 0 ? "KREUZBOCK_TEAM_A" : "KREUZBOCK_TEAM_B";
        }
        return botProvider.apply(player).chooseMove(
            player, currentTrick, contract, teamFor(player), currentTrickNumber, roleOverride,
            memory, remainingPlayers);
    }

    private List<Player> remainingPlayers() {
        List<Player> remaining = new ArrayList<>();
        int leaderIndex = players.indexOf(trickLeader);
        for (int offset = turnIndex + 1; offset < players.size(); offset++) {
            remaining.add(players.get((leaderIndex + offset) % players.size()));
        }
        return remaining;
    }

    private void completeCurrentTrick() {
        PlayedCard winningCard = rules.evaluateTrickWinner(currentTrick, contract);
        trickLeader = winningCard.player();
        trickLeader.addWonTrick(currentTrick);
        if (memory != null) {
            memory.recordTrickCompleted(currentTrick, trickLeader);
        }
        completedTricks.add(currentTrick);

        log(String.format("=> Stich %d geht an %s mit %s (%d Augen im Stich, Gesamt: %d Augen)",
                currentTrickNumber, trickLeader.name(), winningCard.card().shortName(),
                currentTrick.totalPoints(), trickLeader.totalAugen()));

        if (currentTrickNumber == 8) {
            currentTrick = null;
            finishGame();
        } else {
            currentTrickNumber++;
            turnIndex = 0;
            currentTrick = new Trick(currentTrickNumber, trickLeader);
        }
    }

    public GameResult finishGame() {
        transitionTo(Phase.SCORING);
        List<Player> initialSideA = determineSideA();
        List<Player> initialSideB = players.stream().filter(player -> !initialSideA.contains(player)).toList();
        List<Player> callerTeam = initialSideA;
        List<Player> defenderTeam = initialSideB;

        int callerPoints = callerTeam.stream().mapToInt(Player::totalAugen).sum();
        int defenderPoints = defenderTeam.stream().mapToInt(Player::totalAugen).sum();
        long callerTricks = completedTricks.stream()
            .filter(trick -> trick.winner() != null && initialSideA.contains(trick.winner()))
            .count();
        long defenderTricks = completedTricks.stream()
            .filter(trick -> trick.winner() != null && initialSideB.contains(trick.winner()))
            .count();

        boolean callerWon;
        RamschOutcome ramschOutcome = null;
        if (contract.gameType() == GameType.RAMSCH) {
            ramschOutcome = determineRamschOutcome();
            callerTeam = ramschOutcome.winners();
            defenderTeam = ramschOutcome.losers();
            callerPoints = callerTeam.stream().mapToInt(Player::totalAugen).sum();
            defenderPoints = defenderTeam.stream().mapToInt(Player::totalAugen).sum();
            callerWon = true;
        } else if (contract.gameType() == GameType.BETTEL) {
            callerWon = contract.caller().wonTricks().isEmpty();
        } else if (contract.isTout()) {
            callerWon = callerTricks == 8;
        } else {
            callerWon = callerPoints >= 61;
        }
        boolean specialScoring = contract.gameType() == GameType.BETTEL
            || contract.gameType() == GameType.RAMSCH;
        boolean isSchneider = !specialScoring && !contract.isTout()
                && ((callerWon && defenderPoints <= 30) || (!callerWon && callerPoints <= 30));
        boolean isSchwarz = !specialScoring && schwarzResult(callerWon, callerTricks, defenderTricks);

        StringBuilder sb = new StringBuilder();
        sb.append("\n=========================================\n");
        sb.append("           SPIELABRECHNUNG               \n");
        sb.append("=========================================\n");
        sb.append(String.format("Partei 1 (%s): %d Augen%n",
            callerTeam.stream().map(Player::name).toList(), callerPoints));
        sb.append(String.format("Partei 2 (%s): %d Augen%n",
            defenderTeam.stream().map(Player::name).toList(), defenderPoints));

        appendOutcome(sb, ramschOutcome, callerWon, isSchwarz, isSchneider);
        sb.append("\n=========================================");

        log(sb.toString());
        transitionTo(Phase.FINISHED);

        this.result = new GameResult(
                contract,
                partner,
                callerTeam,
                defenderTeam,
                callerPoints,
                defenderPoints,
                callerWon,
                isSchneider,
                isSchwarz,
                sb.toString()
        );
        return result;
    }

    private boolean schwarzResult(boolean callerWon, long callerTricks, long defenderTricks) {
        if (contract.isTout()) {
            return callerWon && defenderTricks == 0;
        }
        return callerWon ? defenderTricks == 0 : callerTricks == 0;
    }

    private void appendOutcome(StringBuilder sb, RamschOutcome ramschOutcome,
                               boolean callerWon, boolean isSchwarz, boolean isSchneider) {
        if (ramschOutcome != null) {
            if (ramschOutcome.durchmarsch()) {
                sb.append("ERGEBNIS: ").append(ramschOutcome.winners().get(0).name())
                    .append(" GEWINNT DEN DURCHMARSCH (> 90 AUGEN)!");
            } else {
                sb.append("ERGEBNIS: Ramsch-Verlierer: ")
                    .append(ramschOutcome.losers().stream().map(Player::name).toList());
            }
        } else if (callerWon) {
            sb.append("ERGEBNIS: Partei 1 GEWINNT!");
            if (isSchwarz) sb.append(" (SCHWARZ - Partei 2 ohne Stich!)");
            else if (isSchneider) sb.append(" (SCHNEIDER - Partei 2 <= 30 Augen!)");
        } else {
            sb.append("ERGEBNIS: Partei 2 GEWINNT!");
            if (isSchwarz) sb.append(" (SCHWARZ - Partei 1 ohne Stich!)");
            else if (isSchneider) sb.append(" (SCHNEIDER - Partei 1 <= 30 Augen!)");
        }
    }

        private RamschOutcome determineRamschOutcome() {
        Player durchmarschierer = players.stream()
            .filter(player -> player.totalAugen() > 90)
            .findFirst()
            .orElse(null);
        if (durchmarschierer != null) {
            List<Player> losers = players.stream()
                .filter(player -> !player.equals(durchmarschierer))
                .toList();
            return new RamschOutcome(List.of(durchmarschierer), losers, true);
        }

        int mostEyes = players.stream().mapToInt(Player::totalAugen).max().orElse(0);
        List<Player> tiedOnEyes = players.stream()
            .filter(player -> player.totalAugen() == mostEyes)
            .toList();
        int mostTricks = tiedOnEyes.stream().mapToInt(player -> player.wonTricks().size()).max().orElse(0);
        List<Player> losers = tiedOnEyes.stream()
            .filter(player -> player.wonTricks().size() == mostTricks)
            .toList();
        List<Player> winners = players.stream().filter(player -> !losers.contains(player)).toList();
        return new RamschOutcome(winners, losers, false);
        }

    private List<Player> determineSideA() {
        if (contract instanceof RufspielContract) {
            return List.of(contract.caller(), partner);
        }
        if (contract instanceof RamschContract) {
            return List.of();
        }
        if (contract instanceof KreuzbockContract) {
            return List.of(players.get(0), players.get(2));
        }
        return List.of(contract.caller());
    }

    private List<Player> teamFor(Player player) {
        if (contract instanceof RamschContract) {
            return List.of(player);
        }
        if (contract instanceof KreuzbockContract) {
            int seat = players.indexOf(player);
            return seat % 2 == 0 ? List.of(players.get(0), players.get(2)) : List.of(players.get(1), players.get(3));
        }
        if (contract instanceof RufspielContract) {
            return List.of(contract.caller(), partner);
        }
        return player.equals(contract.caller()) ? List.of(player)
                : players.stream().filter(other -> !other.equals(contract.caller())).toList();
    }

    public List<Player> teamMembers(Player player) {
        return teamFor(player);
    }

    private void transitionTo(Phase next) {
        this.currentPhase = next;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
