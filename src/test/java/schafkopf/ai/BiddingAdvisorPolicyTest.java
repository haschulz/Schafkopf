package schafkopf.ai;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.model.Card;
import schafkopf.model.Personality;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Suit;
import schafkopf.rules.RuleEngine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiddingAdvisorPolicyTest {
    private final RuleEngine rules = new RuleEngine();

    @Test
    void klopfenDecisionChangesWhenGenomeThresholdChanges() {
        List<Card> firstFour = List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.GRAS, Rank.SIEBEN));
        Player player = new Player("1", "Sepp");
        PolicyGenome baseline = PolicyGenome.baseline();
        PolicyGenome conservative = baseline.withWeight("klopfen.threshold", 100.0);

        assertTrue(new BiddingAdvisor(rules, baseline).shouldKlopfen(player, firstFour, rules));
        assertFalse(new BiddingAdvisor(rules, conservative).shouldKlopfen(player, firstFour, rules));
    }

    @Test
    void rufspielBidRespondsToGenomeHandStrengthWeight() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.SAU),
                new Card(Suit.HERZ, Rank.ZEHN),
                new Card(Suit.SCHELLEN, Rank.SAU),
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.SIEBEN)));
        PolicyGenome baseline = PolicyGenome.baseline();
        PolicyGenome suppressBids = baseline.withWeight("bid.handScore", 0.0);

        assertTrue(new BiddingAdvisor(rules, baseline)
                .evaluatePlayer(player, Personality.MAURER).willPlay());
        assertFalse(new BiddingAdvisor(rules, suppressBids)
                .evaluatePlayer(player, Personality.MAURER).willPlay());
    }

    @Test
    void highOpponentThreatAppliesConfiguredRiskPenaltyToSolidPlayer() {
        Player player = highThreatRufspielHand();
        PolicyGenome withoutRiskPenalty = PolicyGenome.baseline()
                .withWeight("bid.riskPenalty", 0.0)
                .withWeight("bid.expectedValueCents", 0.0);
        PolicyGenome baseline = PolicyGenome.baseline()
                .withWeight("bid.expectedValueCents", 0.0);

        assertTrue(new BiddingAdvisor(rules, withoutRiskPenalty)
                .evaluatePlayer(player, Personality.SOLIDE).willPlay());
        assertFalse(new BiddingAdvisor(rules, baseline)
                .evaluatePlayer(player, Personality.SOLIDE).willPlay());
    }

    @Test
    void zockerRiskReliefOffsetsConfiguredRiskPenaltyForAggressivePlayer() {
        Player player = highThreatRufspielHand();
        PolicyGenome baseline = PolicyGenome.baseline()
                .withWeight("bid.expectedValueCents", 0.0);
        PolicyGenome withoutRelief = baseline.withWeight("bid.zockerRiskRelief", 0.0);

        assertTrue(new BiddingAdvisor(rules, baseline)
                .evaluatePlayer(player, Personality.ZOCKER).willPlay());
        assertFalse(new BiddingAdvisor(rules, withoutRelief)
                .evaluatePlayer(player, Personality.ZOCKER).willPlay());
    }

    private Player highThreatRufspielHand() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.HERZ, Rank.OBER),
                new Card(Suit.SCHELLEN, Rank.OBER),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.ACHT)));
        return player;
    }
}