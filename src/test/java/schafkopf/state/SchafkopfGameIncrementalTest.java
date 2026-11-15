package schafkopf.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import schafkopf.ai.SimpleBot;
import schafkopf.model.Card;
import schafkopf.model.Player;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;

class SchafkopfGameIncrementalTest {
    @Test
    void validatesEachHumanCardAndCompletesThroughSingleCardSteps() {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        RuleEngine rules = new RuleEngine();
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(message -> { });
        game.dealRandom(7812L, 9);
        game.setupContract(new SoloContract(players.get(0), Suit.EICHEL));
        game.startTrickPlay(players.get(0));

        assertEquals(players.get(0), game.currentPlayer());
        assertEquals(8, game.legalMovesForCurrentPlayer().size());
        Card cardFromAnotherHand = players.get(1).hand().get(0);
        assertThrows(IllegalArgumentException.class, () -> game.playCard(cardFromAnotherHand));
        assertEquals(8, players.get(0).hand().size());

        int playedCards = 0;
        while (game.phase() == Phase.TRICK_PLAY) {
            List<Card> legalMoves = game.legalMovesForCurrentPlayer();
            assertFalse(legalMoves.isEmpty());
            game.playCard(legalMoves.get(0));
            playedCards++;
        }

        assertEquals(32, playedCards);
        assertEquals(8, game.completedTricks().size());
        assertNotNull(game.result());
    }
}