package schafkopf.analysis;

import schafkopf.config.SchafkopfConfig;
import schafkopf.ai.SimpleBot;
import schafkopf.model.BettelContract;
import schafkopf.model.Card;
import schafkopf.model.GameContract;
import schafkopf.model.GameType;
import schafkopf.model.GeierContract;
import schafkopf.model.PlayedCard;
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.Trick;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;
import schafkopf.state.Phase;
import schafkopf.state.SchafkopfGame;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete, seat-based record of one finished game: initial hands, contract, opening leader and all plays.
 * Seat-based so the same game can be rebuilt with any alternative card distribution.
 */
public record GameRecord(
        List<String> playerNames,
        List<List<Card>> initialHands,
        ContractSpec contract,
        int leaderSeat,
        List<Play> plays
) {
    public record Play(int seat, Card card) {
    }

    public record ContractSpec(GameType type, int callerSeat, Suit suit, boolean tout) {
        public static ContractSpec of(GameContract contract, List<Player> players) {
            int callerSeat = players.indexOf(contract.caller());
            if (contract instanceof RufspielContract rufspiel) {
                return new ContractSpec(GameType.RUFSPIEL, callerSeat, rufspiel.calledSuit(), false);
            }
            if (contract instanceof SoloContract solo) {
                return new ContractSpec(GameType.FARBSOLO, callerSeat, solo.trumpSuit(), solo.isTout());
            }
            if (contract instanceof WenzContract wenz) {
                return new ContractSpec(wenz.gameType(), callerSeat, wenz.trumpSuit(), wenz.isTout());
            }
            if (contract instanceof GeierContract || contract instanceof BettelContract) {
                return new ContractSpec(contract.gameType(), callerSeat, null, false);
            }
            throw new IllegalArgumentException("Nachkarteln unterstützt " + contract.displayName() + " noch nicht");
        }

        public GameContract bind(List<Player> players) {
            Player caller = players.get(callerSeat);
            return switch (type) {
                case RUFSPIEL -> new RufspielContract(caller, suit);
                case FARBSOLO -> new SoloContract(caller, suit, tout);
                case WENZ -> new WenzContract(caller, tout);
                case FARBWENZ -> new WenzContract(caller, suit);
                case GEIER -> new GeierContract(caller);
                case BETTEL -> new BettelContract(caller);
                default -> throw new IllegalArgumentException("Nicht unterstützte Spielart: " + type);
            };
        }
    }

    public GameRecord {
        if (initialHands.size() != 4 || initialHands.stream().anyMatch(hand -> hand.size() != 8)) {
            throw new IllegalArgumentException("A game record needs four hands of eight cards");
        }
        playerNames = List.copyOf(playerNames);
        initialHands = initialHands.stream().map(List::copyOf).toList();
        plays = List.copyOf(plays);
    }

    /** Initial hands are exactly the cards each seat played, so a finished game is fully recoverable. */
    public static GameRecord from(SchafkopfGame game) {
        if (game.phase() != Phase.FINISHED) {
            throw new IllegalStateException("Only finished games can be recorded");
        }
        List<Player> players = game.players();
        List<List<Card>> hands = new ArrayList<>();
        players.forEach(player -> hands.add(new ArrayList<>()));
        List<Play> plays = new ArrayList<>();
        for (Trick trick : game.completedTricks()) {
            for (PlayedCard played : trick.playedCards()) {
                int seat = players.indexOf(played.player());
                hands.get(seat).add(played.card());
                plays.add(new Play(seat, played.card()));
            }
        }
        Player leader = game.completedTricks().get(0).leader();
        return new GameRecord(
                players.stream().map(Player::name).toList(),
                hands,
                ContractSpec.of(game.gameContract(), players),
                players.indexOf(leader),
                plays);
    }

    /** Writes the record in the format read by {@link GameRecordParser}. */
    public String toText() {
        StringBuilder text = new StringBuilder();
        text.append("spieler: ").append(String.join(", ", playerNames)).append('\n');
        text.append("ansage: ").append(playerNames.get(contract.callerSeat())).append(' ')
                .append(switch (contract.type()) {
                    case RUFSPIEL -> "sauspiel";
                    case FARBSOLO -> "solo";
                    case FARBWENZ -> "farbwenz";
                    default -> contract.type().name().toLowerCase(java.util.Locale.ROOT);
                });
        if (contract.suit() != null) {
            text.append(' ').append(contract.suit().displayName().toLowerCase(java.util.Locale.GERMAN));
        }
        if (contract.tout()) {
            text.append(" tout");
        }
        text.append('\n');
        for (int index = 0; index < plays.size(); index += 4) {
            text.append("stich: ");
            for (int offset = 0; offset < 4; offset++) {
                Play play = plays.get(index + offset);
                text.append(offset == 0 ? "" : ", ").append(playerNames.get(play.seat())).append(' ')
                        .append(play.card().suit().displayName()).append('-')
                        .append(play.card().rank().displayName());
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Builds a fresh game with the given hands and replays the first {@code playCount} recorded plays.
     *
     * @throws IllegalArgumentException if a recorded play is illegal with these hands
     */
    public SchafkopfGame replay(List<List<Card>> hands, int playCount, RuleEngine rules, SimpleBot bot) {
        return replay(hands, playCount, rules, bot, SchafkopfConfig.defaults());
    }

    public SchafkopfGame replay(List<List<Card>> hands, int playCount, RuleEngine rules,
                               SimpleBot bot, SchafkopfConfig config) {
        List<Player> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(new Player(String.valueOf(seat + 1), playerNames.get(seat)));
        }
        SchafkopfGame game = new SchafkopfGame(players, rules, player -> bot, config);
        game.setLogger(null);
        game.setCustomHands(hands);
        game.setupContract(contract.bind(players));
        game.startTrickPlay(players.get(leaderSeat));
        for (Play play : plays.subList(0, playCount)) {
            if (players.indexOf(game.currentPlayer()) != play.seat()) {
                throw new IllegalArgumentException("Recorded play order does not match the game flow");
            }
            game.playCard(play.card());
        }
        return game;
    }
}
