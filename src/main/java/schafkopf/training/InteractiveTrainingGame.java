package schafkopf.training;

import schafkopf.ai.BiddingAdvisor;
import schafkopf.ai.GameMemory;
import schafkopf.ai.SimpleBot;
import schafkopf.config.SchafkopfConfig;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;
import schafkopf.model.Card;
import schafkopf.model.Deck;
import schafkopf.model.BettelContract;
import schafkopf.model.GameContract;
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
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Interactive Training Mode ("Mit offenen Karten lernen"):
 * - User plays as Player 4 against 3 heuristic AI bots.
 * - Open cards: Hand of all 4 players visible on demand.
 * - AI coach provides step-by-step recommendations and explanations for the user.
 * - Full bidding phase with scenario evaluation (Pessimistic opponent modeling).
 * - Bavarian tariff settling with long-term score ledger.
 */
public class InteractiveTrainingGame {
    private record TrickContext(
            SchafkopfGame game,
            List<Player> players,
            Player user,
            Player partner,
            GameMemory memory
    ) {
    }

    private final RuleEngine rules = new RuleEngine();
    private final SimpleBot bot = new SimpleBot(rules, true);
    private final BiddingAdvisor biddingAdvisor = new BiddingAdvisor(rules);
    private Tariff tariff;
    private SchafkopfConfig config;
    private final Scanner scanner;

    public InteractiveTrainingGame(InputStream in) {
        this(in, SchafkopfConfig.load());
    }

    public InteractiveTrainingGame(InputStream in, SchafkopfConfig config) {
        this.scanner = new Scanner(in);
        this.config = config;
        this.tariff = new Tariff(config);
    }

    public void startInteractiveSession(ScoreLedger ledger) {
        while (true) {
            config = SchafkopfConfig.load();
            tariff = new Tariff(config);
            if (!playInteractiveRound(ledger)) {
                return;
            }
        }
    }

    private boolean playInteractiveRound(ScoreLedger ledger) {
        System.out.println("==========================================================================");
        System.out.println("       WILLKOMMEN AM SCHAFKOPF-TRAININGSTISCH (MIT OFFENEN KARTEN)        ");
        System.out.println("==========================================================================");
        System.out.println("Du spielst als Spieler 4 ('Du') zusammen mit:");
        System.out.println(" - Spieler 1: Sepp (Bot - " + Personality.MAURER.description() + ")");
        System.out.println(" - Spieler 2: Hans (Bot - " + Personality.SOLIDE.description() + ")");
        System.out.println(" - Spieler 3: Franz (Bot - " + Personality.ZOCKER.description() + ")");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("💡 Features im Training:");
        System.out.println(" * Offene Karten: Du siehst alle Hände und die Gedankengänge der Bots.");
        System.out.println(" * Coach-Tipp: Bei jedem Zug empfiehlt dir die KI den optimalen Zug.");
        System.out.println(" * Drücke einfach [Enter] oder 'c' um dem Coach-Tipp zu folgen!\n");

        List<Player> players = createPlayers();
        Player user = players.get(3);
        List<Player> klopfer = dealAndAskKlopfen(players, user);

        // Show open cards
        printOpenCards(players);

        // -------------------------------------------------------------
        // PHASE 1: DAS BIETEN (Reihum fragen mit Szenario-Analyse)
        // -------------------------------------------------------------
        GameContract selectedContract = conductBidding(players, user);
        if (selectedContract == null) {
            selectedContract = resolveNoGamePolicy(players, ledger);
            if (selectedContract == null) {
                return true;
            }
        }

        SchafkopfGame game = prepareGame(players, selectedContract, klopfer, ledger, user);
        playTricks(game, players, user);
        settleTrainingGame(game, players, ledger, user);

        if (!scanner.hasNextLine()) {
            return false;
        }
        System.out.print("Noch eine Runde? [j/n]: ");
        return isYes(readInput());
    }

    private List<Player> createPlayers() {
        return List.of(
                new Player("1", "Spieler 1 (Sepp)"),
                new Player("2", "Spieler 2 (Hans)"),
                new Player("3", "Spieler 3 (Franz)"),
                new Player("4", "Spieler 4 (Du)"));
    }

    private List<Player> dealAndAskKlopfen(List<Player> players, Player user) {
        Deck deck = new Deck();
        deck.shuffle();
        List<List<Card>> deal = deck.deal4x8();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).reset();
            players.get(i).setHand(deal.get(i).subList(0, config.klopfenAfterCards()));
        }

        List<Player> klopfer = new ArrayList<>();
        if (config.klopfenEnabled()) {
            System.out.println(">>> KLOPFEN nach " + config.klopfenAfterCards() + " Karten <<<");
            for (Player player : players) {
                boolean shouldDouble = player.equals(user)
                        ? promptKlopfen(player)
                        : biddingAdvisor.shouldKlopfen(player, player.hand(), rules);
                if (shouldDouble) {
                    klopfer.add(player);
                    System.out.println("   " + player.name() + " klopft.");
                }
            }
        }

        for (int i = 0; i < players.size(); i++) {
            players.get(i).setHand(deal.get(i));
        }
        return klopfer;
    }

    private GameContract conductBidding(List<Player> players, Player user) {
        System.out.println("\n>>> PHASE 1: DAS BIETEN (Reihum fragen mit Szenario-Analyse) <<<\n");
        List<Personality> personalities = List.of(
                Personality.MAURER, Personality.SOLIDE, Personality.ZOCKER, Personality.SOLIDE);
        for (int i = 0; i < players.size(); i++) {
            Player current = players.get(i);
            GameContract contract = current.equals(user)
                    ? askUserBid(current, personalities.get(i))
                    : askBotBid(current, personalities.get(i));
            if (contract != null) {
                return contract;
            }
        }
        return null;
    }

    private GameContract askUserBid(Player player, Personality personality) {
        BiddingAdvisor.BiddingDecision decision = biddingAdvisor.evaluatePlayer(player, personality);
        System.out.println("👉 DU bist an der Reihe beim Bieten!");
        System.out.printf("   [Coach-Analyse]: Hand-Score: %d | Erwartete eigene Stiche: %d | Gegner-Stiche: %d (Worst-Case: ~%d Augen)%n",
                decision.handScore(), decision.estimatedOwnTricks(), decision.estimatedOpponentTricks(),
                decision.expectedOpponentPoints());
        if (!decision.isBlocked()) {
            List<Suit> validSuits = biddingAdvisor.getValidRufspielSuits(player);
            System.out.println("   Mögliche Ruf-Säue: "
                    + validSuits.stream().map(Suit::displayName).collect(Collectors.joining(", ")));
            System.out.println("   Empfehlung des Coaches: " + (decision.willPlay()
                    ? "SPIELEN auf die " + decision.recommendedSuit().displayName() + "-Sau"
                    : "WEITER sagen (Gegner droht zu stark zu sein)"));
        }
        GameContract contract = promptUserContract(player, decision);
        if (contract != null) {
            System.out.println("   -> Du sagst " + contract.displayName() + " an!\n");
        } else {
            System.out.println("   -> Du sagst 'Weiter'!\n");
        }
        return contract;
    }

    private GameContract askBotBid(Player player, Personality personality) {
        BiddingAdvisor.BiddingDecision decision = biddingAdvisor.evaluatePlayer(player, personality);
        System.out.printf("🗣️ %s (%s):%n", player.name(), personality.description());
        System.out.printf("   %s%n", decision.reasoning());
        if (decision.willPlay()) {
            System.out.printf("   -> %s übernimmt das Spiel und ruft auf die %s-Sau!%n%n",
                    player.name(), decision.recommendedSuit().displayName());
            return new RufspielContract(player, decision.recommendedSuit());
        }
        System.out.printf("   -> %s sagt 'Weiter'!%n%n", player.name());
        return null;
    }

    private SchafkopfGame prepareGame(
            List<Player> players,
            GameContract contract,
            List<Player> klopfer,
            ScoreLedger ledger,
            Player user
    ) {
        SchafkopfGame game = new SchafkopfGame(players, rules, bot, config);
        game.setupContract(contract);
        int stockMultiplier = ledger.consumeStockMultiplier();
        if (stockMultiplier > 1) {
            game.bettingState().applyStock(stockMultiplier);
        }
        klopfer.forEach(game.bettingState()::applyDouble);
        resolveKontraAndRe(game, user);
        return game;
    }

    private void playTricks(SchafkopfGame game, List<Player> players, Player user) {
        GameMemory memory = game.memory();
        TrickContext context = new TrickContext(game, players, user, game.partner(), memory);
        Player leader = players.get(0);
        System.out.println("\n>>> PHASE 2: TRICK PLAY (8 STICHE) <<<\n");
        for (int trickNumber = 1; trickNumber <= 8; trickNumber++) {
            leader = playTrick(context, leader, trickNumber);
        }
    }

    private Player playTrick(TrickContext context, Player leader, int trickNumber) {
        SchafkopfGame game = context.game();
        List<Player> players = context.players();
        System.out.printf("%n======================= STICH %d (Ausspiel: %s) =======================%n",
                trickNumber, leader.name());
        Trick trick = new Trick(trickNumber, leader);
        int leaderIndex = players.indexOf(leader);
        for (int turn = 0; turn < players.size(); turn++) {
            Player current = players.get((leaderIndex + turn) % players.size());
            List<Player> remainingPlayers = playersYetToPlay(players, leaderIndex, turn);
            playTurn(context, trick, current, trickNumber, remainingPlayers);
        }

        PlayedCard winningCard = rules.evaluateTrickWinner(trick, game.gameContract());
        Player winner = winningCard.player();
        winner.addWonTrick(trick);
        if (context.memory() != null) {
            context.memory().recordTrickCompleted(trick, winner);
        }
        game.completedTricks().add(trick);
        System.out.printf("%n=> Stich %d geht an %s mit %s (%d Augen im Stich, Gesamt: %d Augen)%n",
                trickNumber, winner.name(), winningCard.card().shortName(), trick.totalPoints(), winner.totalAugen());
        return winner;
    }

    private List<Player> playersYetToPlay(List<Player> players, int leaderIndex, int turn) {
        List<Player> remaining = new ArrayList<>();
        for (int offset = turn + 1; offset < players.size(); offset++) {
            remaining.add(players.get((leaderIndex + offset) % players.size()));
        }
        return remaining;
    }

    private void playTurn(
            TrickContext context,
            Trick trick,
            Player current,
            int trickNumber,
            List<Player> remainingPlayers
    ) {
        SchafkopfGame game = context.game();
        GameMemory memory = context.memory();
        boolean calledSauPlayed = memory != null && memory.isCalledSauPlayed();
        List<Card> legalMoves = rules.getLegalMovesForContract(
                current, trick, game.gameContract(), calledSauPlayed, trickNumber);
        MoveExplanation suggestion = chooseMove(current, trick, game, context.partner(), memory, trickNumber,
            remainingPlayers);
        Card chosenCard = current.equals(context.user())
                ? promptHumanMove(current, trick, legalMoves, suggestion)
                : suggestion.card();
        current.removeCard(chosenCard);
        if (memory != null) {
            memory.recordCardPlayed(current, chosenCard, trick, game.contract());
        }
        trick.addCard(current, chosenCard);

        if (current.equals(context.user())) {
            System.out.printf("   -> Du spielst: %s (%d Augen) [%s]%n", chosenCard.shortName(), chosenCard.points(),
                    chosenCard.equals(suggestion.card()) ? "Coach-Tipp gefolgt" : "Eigene Wahl");
        } else {
            System.out.printf("🤖 %s: spielt %s (%d Augen)%n   Begründung: %s%n",
                    current.name(), chosenCard.shortName(), chosenCard.points(), suggestion.tacticalReasoning());
        }
        logNewMemoryObservation(memory, current);
    }

    private Card promptHumanMove(Player player, Trick trick, List<Card> legalMoves, MoveExplanation suggestion) {
        System.out.println("\n👉 DEIN ZUG (" + player.name() + ")!");
        String cardsInTrick = trick.playedCards().isEmpty()
                ? "Keine (du eröffnest!)"
                : trick.playedCards().stream()
                        .map(card -> card.player().name() + ": " + card.card().shortName())
                        .collect(Collectors.joining(", "));
        System.out.println("   Karten im aktuellen Stich: " + cardsInTrick);
        System.out.println("   💡 [Coach-Empfehlung]: " + suggestion.card().shortName()
                + " (" + suggestion.card().points() + " Augen)");
        System.out.println("      Taktischer Grund: " + suggestion.tacticalReasoning());
        for (int index = 0; index < legalMoves.size(); index++) {
            Card card = legalMoves.get(index);
            String marker = card.equals(suggestion.card()) ? "  <-- EMPFEHLUNG VOM COACH" : "";
            System.out.printf("   [%d] %-14s (%2d Augen)%s%n", index + 1, card.shortName(), card.points(), marker);
        }
        return promptUserCardChoice(legalMoves, suggestion.card());
    }

    private void logNewMemoryObservation(GameMemory memory, Player current) {
        if (memory == null || memory.observationLogs().isEmpty()) {
            return;
        }
        String latest = memory.observationLogs().get(memory.observationLogs().size() - 1);
        if (latest.contains(current.name())) {
            System.out.println("   💡 [Gedächtnis-Notiz]: " + latest);
        }
    }

    private void settleTrainingGame(SchafkopfGame game, List<Player> players, ScoreLedger ledger, Player user) {
        System.out.println("\n==========================================================================");
        System.out.println("                     ENDSTAND & TARIF-ABRECHNUNG                          ");
        System.out.println("==========================================================================");
        GameResult result = game.finishGame();
        System.out.println(result.summary());
        List<Player> winners = result.callerTeamWon() ? result.callerTeam() : result.defenderTeam();
        List<Player> losers = result.callerTeamWon() ? result.defenderTeam() : result.callerTeam();
        if (result.isSchneider() && losers.contains(user)) {
            System.out.print("Schneider als Ehrensache bezahlen? [j/n]: ");
            game.setSchneiderPaid(isYes(readInput()));
        }
        if (result.isSchwarz() && winners.contains(user)) {
            System.out.print("Schwarz einfordern? [j/n]: ");
            game.setSchwarzClaimed(isYes(readInput()));
        }
        ledger.settleGame(game, tariff);
        ledger.printLeaderboard(players);
    }

    private MoveExplanation chooseMove(
            Player player,
            Trick trick,
            SchafkopfGame game,
            Player partner,
            GameMemory memory,
            int trickNumber,
            List<Player> remainingPlayers
    ) {
        if (game.gameContract() instanceof RufspielContract rufspielContract) {
            return bot.chooseMove(player, trick, rufspielContract, partner,
                    memory != null && memory.isCalledSauPlayed(), trickNumber, memory, remainingPlayers);
        }
        return bot.chooseMove(player, trick, game.gameContract(), game.teamMembers(player), trickNumber);
    }

    private boolean promptKlopfen(Player player) {
        System.out.printf("%s hat %s. Klopfen? [j/n]: ", player.name(),
                player.hand().stream().map(Card::shortName).collect(Collectors.joining(", ")));
        return isYes(readInput());
    }

    private GameContract promptUserContract(Player player, BiddingAdvisor.BiddingDecision decision) {
        System.out.print("Ansage [r=Rufspiel, s=Farbsolo, w=Wenz, f=Farbwenz, b=Bettel, n=Weiter, c=Coach]: ");
        String selection = readInput().toLowerCase();
        return switch (selection) {
            case "r", "j" -> decision.recommendedSuit() == null
                    ? null
                    : new RufspielContract(player, decision.recommendedSuit());
            case "c" -> decision.willPlay() && decision.recommendedSuit() != null
                    ? new RufspielContract(player, decision.recommendedSuit())
                    : null;
            case "s" -> new SoloContract(player, promptSuit());
            case "w" -> new WenzContract(player);
            case "f" -> new WenzContract(player, promptSuit());
            case "b" -> new BettelContract(player);
            default -> null;
        };
    }

    private Suit promptSuit() {
        System.out.print("Farbe [e=Eichel, g=Gras, h=Herz, s=Schellen]: ");
        return switch (readInput().toLowerCase()) {
            case "g" -> Suit.GRAS;
            case "h" -> Suit.HERZ;
            case "s" -> Suit.SCHELLEN;
            default -> Suit.EICHEL;
        };
    }

    private GameContract resolveNoGamePolicy(List<Player> players, ScoreLedger ledger) {
        System.out.println("Alle Spieler haben Weiter gesagt. Regel für 'keiner spielt': " + config.noGamePolicy());
        return switch (config.noGamePolicy()) {
            case STOCK -> {
                ledger.addStock(config.stockMultiplier());
                System.out.println("Stock: nächste gespielte Runde wird mit Faktor "
                        + ledger.pendingStockMultiplier() + " abgerechnet.");
                yield null;
            }
            case ZAMMWERFEN -> {
                System.out.println("Zammwerfen: Karten werden neu gegeben.");
                yield null;
            }
            case RAMSCH -> new RamschContract();
            case KREUZBOCK -> new KreuzbockContract();
            case MUSS_SPIEL -> createMussContract(players);
        };
    }

    private GameContract createMussContract(List<Player> players) {
        Card requiredCard = new Card(Suit.EICHEL, Rank.OBER);
        Player caller = players.stream()
                .filter(player -> player.hasCard(requiredCard))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Eichel-Ober was not dealt"));
        return switch (config.mussGameType()) {
            case FARBSOLO -> new SoloContract(caller, config.mussTrumpfSuit());
            case WENZ -> new WenzContract(caller);
            case FARBWENZ -> new WenzContract(caller, config.mussTrumpfSuit());
            case BETTEL -> new BettelContract(caller);
            default -> throw new IllegalStateException("Unsupported Muss-Spiel type: " + config.mussGameType());
        };
    }

    private void resolveKontraAndRe(SchafkopfGame game, Player user) {
        if (!config.kontraEnabled() && !config.reEnabled()) {
            return;
        }

        List<Player> callerTeam = declarationSide(game);
        if (callerTeam.isEmpty()) {
            return;
        }
        boolean userOnCallerSide = callerTeam.contains(user);
        boolean kontraCalled = false;
        if (config.kontraEnabled()) {
            boolean botContra = game.players().stream()
                    .filter(player -> !player.equals(user) && !callerTeam.contains(player))
                    .anyMatch(player -> shouldDouble(player, game.gameContract()));
            if (botContra) {
                kontraCalled = game.bettingState().callKontra();
                System.out.println("Gegner sagt Kontra!");
            } else if (!userOnCallerSide) {
                System.out.print("Möchtest du Kontra geben? [j/n]: ");
                kontraCalled = isYes(readInput()) && game.bettingState().callKontra();
            }
        }

        if (!kontraCalled || !config.reEnabled()) {
            return;
        }
        boolean botRe = game.players().stream()
                .filter(player -> !player.equals(user) && callerTeam.contains(player))
                .anyMatch(player -> shouldDouble(player, game.gameContract()));
        if (botRe) {
            game.bettingState().callRetour();
            System.out.println("Spielerpartei sagt Re!");
        } else if (userOnCallerSide) {
            System.out.print("Möchtest du Re sagen? [j/n]: ");
            if (isYes(readInput())) {
                game.bettingState().callRetour();
            }
        }
    }

    private List<Player> declarationSide(SchafkopfGame game) {
        if (game.gameContract() instanceof KreuzbockContract) {
            return game.teamMembers(game.players().get(0));
        }
        if (game.gameContract() instanceof RufspielContract) {
            return List.of(game.gameContract().caller(), game.partner());
        }
        if (game.gameContract().caller() != null) {
            return List.of(game.gameContract().caller());
        }
        return List.of();
    }

    private boolean shouldDouble(Player player, GameContract contract) {
        long trumpCount = player.hand().stream().filter(card -> contract.isTrump(card, rules)).count();
        int highestTrump = player.hand().stream()
                .filter(card -> contract.isTrump(card, rules))
                .mapToInt(contract::getTrumpStrength)
                .max()
                .orElse(0);
        return trumpCount >= 4 || (trumpCount >= 2 && highestTrump >= 12);
    }

    private boolean isYes(String input) {
        return input.equalsIgnoreCase("j") || input.equalsIgnoreCase("y") || input.equalsIgnoreCase("ja");
    }

    private void printOpenCards(List<Player> players) {
        System.out.println("========================== OFFENE KARTEN AM TISCH =========================");
        for (Player p : players) {
            long trumps = p.hand().stream().filter(rules::isTrump).count();
            String cardsStr = p.hand().stream().map(Card::shortName).collect(Collectors.joining(", "));
            System.out.printf("%-18s (%d Trümpfe): [%s]\n", p.name(), trumps, cardsStr);
        }
        System.out.println("==========================================================================\n");
    }

    private Card promptUserCardChoice(List<Card> legalMoves, Card coachRec) {
        System.out.print("   Wähle deine Karte [1-" + legalMoves.size() + "] oder [Enter/c] für Coach-Tipp: ");
        String input = readInput();

        if (input.isEmpty() || input.equalsIgnoreCase("c")) {
            return coachRec;
        }

        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx >= 0 && idx < legalMoves.size()) {
                return legalMoves.get(idx);
            }
        } catch (NumberFormatException ignored) {}

        System.out.println("   (Ungültige Eingabe -> Coach-Tipp übernommen)");
        return coachRec;
    }

    private String readInput() {
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return "c"; // Fallback in non-interactive environment: follow coach!
    }
}

