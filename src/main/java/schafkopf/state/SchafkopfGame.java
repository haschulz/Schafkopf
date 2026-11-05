package schafkopf.state;

import schafkopf.ai.GameMemory;
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
    private boolean calledSauPlayed = false;
    private boolean schneiderPaid = true;
    private boolean schwarzClaimed = true;
    private GameResult result;

    private Consumer<String> logger = System.out::println;

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

    /**
     * Deals cards randomly to all 4 players.
     */
    public void dealRandom() {
        transitionTo(Phase.DEALING);
        deck.reset();
        deck.shuffle();
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

        if (contract instanceof RufspielContract rufspielContract) {
            Card calledSau = rufspielContract.calledSau();
            this.partner = players.stream()
                .filter(player -> player.hasCard(calledSau))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Called Sau " + calledSau + " not found in any player hand!"));
            this.memory = new GameMemory(players, rules);
            log(String.format("Ansage: %s ruft auf die %s-Sau!", contract.caller().name(),
                rufspielContract.calledSuit().displayName()));
            log(String.format("-> Geheimer Mitspieler: %s (besitzt %s)", partner.name(), calledSau.shortName()));
            return;
        }

        this.memory = null;
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
        transitionTo(Phase.TRICK_PLAY);
        if (contract == null) {
            throw new IllegalStateException("No game contract has been selected");
        }
        if (contract instanceof RufspielContract && memory == null) {
            memory = new GameMemory(players, rules);
        }
        Player leader = startingLeader;

        for (int trickNum = 1; trickNum <= 8; trickNum++) {
            log(String.format("\n--- STICH %d (Ausspiel: %s) ---", trickNum, leader.name()));
            currentTrick = new Trick(trickNum, leader);

            int leaderIdx = players.indexOf(leader);
            for (int turn = 0; turn < 4; turn++) {
                Player current = players.get((leaderIdx + turn) % 4);

                // Compute remaining players yet to play after current in this trick
                List<Player> remainingPlayers = new ArrayList<>();
                for (int k = turn + 1; k < 4; k++) {
                    remainingPlayers.add(players.get((leaderIdx + k) % 4));
                }

                MoveExplanation explanation = contract instanceof RufspielContract rufspielContract
                    ? botProvider.apply(current).chooseMove(current, currentTrick, rufspielContract, partner, calledSauPlayed,
                        trickNum, memory, remainingPlayers)
                    : botProvider.apply(current).chooseMove(current, currentTrick, contract, teamFor(current), trickNum,
                        contract instanceof KreuzbockContract
                            ? (players.indexOf(current) % 2 == 0 ? "KREUZBOCK_TEAM_A" : "KREUZBOCK_TEAM_B")
                            : null);

                Card chosen = explanation.card();
                current.removeCard(chosen);

                int prevObsCount = memory == null ? 0 : memory.observationLogs().size();
                if (memory != null) {
                    memory.recordCardPlayed(current, chosen, currentTrick, (RufspielContract) contract);
                }
                currentTrick.addCard(current, chosen);

                if (contract instanceof RufspielContract rufspielContract && chosen.equals(rufspielContract.calledSau())) {
                    calledSauPlayed = true;
                }

                log(explanation.toLogLine(trickNum, turn + 1));

                // Log any new memory deductions (Farbfreiheit / Aufdeckung)
                if (memory != null) {
                    for (int o = prevObsCount; o < memory.observationLogs().size(); o++) {
                        log("   💡 [Gedächtnis] " + memory.observationLogs().get(o));
                    }
                }
            }

            PlayedCard winningCard = rules.evaluateTrickWinner(currentTrick, contract);
            Player winner = winningCard.player();
            winner.addWonTrick(currentTrick);
            if (memory != null) {
                memory.recordTrickCompleted(currentTrick, winner);
            }
            completedTricks.add(currentTrick);

            log(String.format("=> Stich %d geht an %s mit %s (%d Augen im Stich, Gesamt: %d Augen)",
                    trickNum,
                    winner.name(),
                    winningCard.card().shortName(),
                    currentTrick.totalPoints(),
                    winner.totalAugen()));

            leader = winner; // Winner leads next trick
        }

        return finishGame();
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
        } else {
            callerWon = callerPoints >= 61;
        }
        boolean specialScoring = contract.gameType() == GameType.BETTEL || contract.gameType() == GameType.RAMSCH;
        boolean isSchneider = !specialScoring
                && ((callerWon && defenderPoints <= 30) || (!callerWon && callerPoints <= 30));
        boolean isSchwarz = !specialScoring
            && ((callerWon && defenderTricks == 0) || (!callerWon && callerTricks == 0));

        StringBuilder sb = new StringBuilder();
        sb.append("\n=========================================\n");
        sb.append("           SPIELABRECHNUNG               \n");
        sb.append("=========================================\n");
        sb.append(String.format("Partei 1 (%s): %d Augen\n",
            callerTeam.stream().map(Player::name).toList(), callerPoints));
        sb.append(String.format("Partei 2 (%s): %d Augen\n",
            defenderTeam.stream().map(Player::name).toList(), defenderPoints));

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
