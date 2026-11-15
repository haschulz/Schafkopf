package schafkopf.state;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import schafkopf.model.Card;
import schafkopf.model.Player;
import schafkopf.rules.RuleEngine;

class SchafkopfGameDealTest {
    @Test
    void seededDealsAreReproducible() {
        List<Player> firstPlayers = players();
        List<Player> secondPlayers = players();
        List<Player> otherSeedPlayers = players();

        new SchafkopfGame(firstPlayers, new RuleEngine()).dealRandom(123456L);
        new SchafkopfGame(secondPlayers, new RuleEngine()).dealRandom(123456L);
        new SchafkopfGame(otherSeedPlayers, new RuleEngine()).dealRandom(654321L);

        List<List<Card>> firstDeal = firstPlayers.stream().map(Player::hand).toList();
        List<List<Card>> secondDeal = secondPlayers.stream().map(Player::hand).toList();
        List<List<Card>> otherDeal = otherSeedPlayers.stream().map(Player::hand).toList();

        assertEquals(firstDeal, secondDeal);
        assertNotEquals(firstDeal, otherDeal);
    }

    @Test
    void seededDealsRemainReproducibleAfterTheSameCut() {
        List<Player> firstPlayers = players();
        List<Player> secondPlayers = players();

        new SchafkopfGame(firstPlayers, new RuleEngine()).dealRandom(123456L, 7);
        new SchafkopfGame(secondPlayers, new RuleEngine()).dealRandom(123456L, 7);

        assertEquals(
                firstPlayers.stream().map(Player::hand).toList(),
                secondPlayers.stream().map(Player::hand).toList());
    }

    private static List<Player> players() {
        return List.of(
                new Player("1", "Player 1"),
                new Player("2", "Player 2"),
                new Player("3", "Player 3"),
                new Player("4", "Player 4"));
    }
}