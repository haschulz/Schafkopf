package schafkopf.ai;

import org.junit.jupiter.api.Test;
import schafkopf.ai.policy.PolicyGenome;
import schafkopf.ai.policy.ContractValueModel;
import schafkopf.ai.policy.ContractValueModel;
import schafkopf.model.GameType;
import schafkopf.model.Personality;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.Card;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;
import schafkopf.rules.RuleEngine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameBiddingAdvisorTest {
    @Test
    void genomeWeightsSelectWenzWhenRufspielIsUnavailable() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new schafkopf.model.Card(Suit.EICHEL, Rank.UNTER),
                new schafkopf.model.Card(Suit.GRAS, Rank.UNTER),
                new schafkopf.model.Card(Suit.HERZ, Rank.UNTER),
                new schafkopf.model.Card(Suit.SCHELLEN, Rank.UNTER),
                new schafkopf.model.Card(Suit.EICHEL, Rank.SAU),
                new schafkopf.model.Card(Suit.GRAS, Rank.SAU),
                new schafkopf.model.Card(Suit.SCHELLEN, Rank.SAU),
                new schafkopf.model.Card(Suit.EICHEL, Rank.SIEBEN)));
        PolicyGenome genome = PolicyGenome.baseline()
                .withWeight("bid.FARBSOLO.threshold", 100.0)
                .withWeight("bid.FARBWENZ.threshold", 100.0)
                .withWeight("bid.WENZ.threshold", 0.0)
                .withWeight("bid.WENZ.topTrumps", 3.0)
                .withWeight("bid.BETTEL.threshold", 100.0);

        GameBiddingAdvisor.ContractRecommendation recommendation = new GameBiddingAdvisor(
                new RuleEngine(), genome).recommend(player, Personality.SOLIDE);

        assertEquals(GameType.WENZ, recommendation.contract().gameType());
    }

        @Test
        void legacyGenomeWithoutGeierThresholdDoesNotBidGeierByDefault() {
                Player player = new Player("1", "Sepp");
                player.setHand(List.of(
                                new Card(Suit.EICHEL, Rank.OBER), new Card(Suit.GRAS, Rank.OBER),
                                new Card(Suit.HERZ, Rank.UNTER), new Card(Suit.SCHELLEN, Rank.UNTER),
                                new Card(Suit.GRAS, Rank.ZEHN), new Card(Suit.GRAS, Rank.NEUN),
                                new Card(Suit.EICHEL, Rank.ZEHN), new Card(Suit.EICHEL, Rank.KOENIG)));
                java.util.Map<String, Double> legacyWeights = new java.util.HashMap<>(PolicyGenome.baseline().weights());
                legacyWeights.keySet().removeIf(key -> key.startsWith("bid.GEIER."));
                legacyWeights.put("bid.handScore", 0.0);
                legacyWeights.put("bid.expectedValueCents", 0.0);
                legacyWeights.put("bid.FARBSOLO.threshold", 100.0);
                legacyWeights.put("bid.WENZ.threshold", 100.0);
                legacyWeights.put("bid.FARBWENZ.threshold", 100.0);
                legacyWeights.put("bid.BETTEL.threshold", 100.0);
                PolicyGenome legacy = new PolicyGenome(PolicyGenome.CURRENT_SCHEMA_VERSION, "legacy", legacyWeights);

                assertNull(new GameBiddingAdvisor(new RuleEngine(), legacy).recommend(player, Personality.MAURER));
        }

    @Test
    void genomeCanPassOnAllContracts() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new schafkopf.model.Card(Suit.EICHEL, Rank.SIEBEN),
                new schafkopf.model.Card(Suit.GRAS, Rank.SIEBEN),
                new schafkopf.model.Card(Suit.HERZ, Rank.SIEBEN),
                new schafkopf.model.Card(Suit.SCHELLEN, Rank.SIEBEN)));
        PolicyGenome conservative = PolicyGenome.baseline()
                .withWeight("bid.FARBSOLO.threshold", 1000.0)
                .withWeight("bid.FARBWENZ.threshold", 1000.0)
                .withWeight("bid.WENZ.threshold", 1000.0)
                .withWeight("bid.BETTEL.threshold", 1000.0);

        assertNull(new GameBiddingAdvisor(new RuleEngine(), conservative)
                .recommend(player, Personality.MAURER));
    }

    @Test
    void topTrumpControlSelectsColorSoloOverOtherSoloTypes() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.OBER),
                new Card(Suit.SCHELLEN, Rank.OBER),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SIEBEN)));
        PolicyGenome genome = disableOtherContracts(PolicyGenome.baseline())
                .withWeight("bid.FARBSOLO.threshold", 0.0)
                .withWeight("bid.FARBSOLO.trumpControl", 4.0);

        GameBiddingAdvisor.ContractRecommendation recommendation = new GameBiddingAdvisor(
                new RuleEngine(), genome).recommend(player, Personality.SOLIDE);

        assertEquals(GameType.FARBSOLO, recommendation.contract().gameType());
        assertEquals(Suit.EICHEL, ((SoloContract) recommendation.contract()).trumpSuit());
    }

    @Test
    void lowCardBettelHandOutranksHighCardBettelHand() {
        GameBiddingAdvisor advisor = new GameBiddingAdvisor(new RuleEngine(), bettelOnlyGenome());
        Player lowHand = new Player("1", "Low");
        lowHand.setHand(List.of(
                new Card(Suit.EICHEL, Rank.SIEBEN), new Card(Suit.EICHEL, Rank.ACHT),
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.HERZ, Rank.SIEBEN), new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.SIEBEN), new Card(Suit.SCHELLEN, Rank.ACHT)));
        Player highHand = new Player("2", "High");
        highHand.setHand(List.of(
                new Card(Suit.EICHEL, Rank.SAU), new Card(Suit.EICHEL, Rank.ZEHN),
                new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.ZEHN),
                new Card(Suit.HERZ, Rank.SAU), new Card(Suit.HERZ, Rank.ZEHN),
                new Card(Suit.SCHELLEN, Rank.SAU), new Card(Suit.SCHELLEN, Rank.ZEHN)));

        GameBiddingAdvisor.ContractRecommendation lowRecommendation =
                advisor.recommend(lowHand, Personality.SOLIDE);
        GameBiddingAdvisor.ContractRecommendation highRecommendation =
                advisor.recommend(highHand, Personality.SOLIDE);

        assertEquals(GameType.BETTEL, lowRecommendation.contract().gameType());
        assertNull(highRecommendation);
    }

    @Test
    void baselineBettelPolicyRequiresManyLowCardsAndAvoidsHighCards() {
        Player lowHand = new Player("1", "Low");
        lowHand.setHand(List.of(
                new Card(Suit.EICHEL, Rank.SIEBEN), new Card(Suit.EICHEL, Rank.ACHT),
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.HERZ, Rank.SIEBEN), new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.SIEBEN), new Card(Suit.SCHELLEN, Rank.ACHT)));
        Player highHand = new Player("2", "High");
        highHand.setHand(List.of(
                new Card(Suit.EICHEL, Rank.SAU), new Card(Suit.EICHEL, Rank.ZEHN),
                new Card(Suit.GRAS, Rank.SAU), new Card(Suit.GRAS, Rank.ZEHN),
                new Card(Suit.HERZ, Rank.SAU), new Card(Suit.HERZ, Rank.ZEHN),
                new Card(Suit.SCHELLEN, Rank.SAU), new Card(Suit.SCHELLEN, Rank.ZEHN)));
        GameBiddingAdvisor advisor = new GameBiddingAdvisor(new RuleEngine(), PolicyGenome.baseline(),
                ignored -> { }, ContractValueModel.empty());

        assertEquals(GameType.BETTEL, advisor.recommend(lowHand, Personality.SOLIDE).contract().gameType());
        assertNull(advisor.recommend(highHand, Personality.SOLIDE));
    }

    @Test
    void calibratedExpectedValueCanChangeContractSelection() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.SIEBEN),
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.SIEBEN),
                new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.ACHT)));
        PolicyGenome genome = disableOtherContracts(PolicyGenome.baseline())
                .withWeight("bid.FARBSOLO.threshold", 100.0)
                .withWeight("bid.FARBSOLO.expectedValueCents", 1.0);
        GameBiddingAdvisor advisorWithoutModel = new GameBiddingAdvisor(new RuleEngine(), genome);
        ContractValueModel favorableSoloValue = ContractValueModel.fit("favorable-solo", List.of(
                new ContractValueModel.TrainingSample(GameType.FARBSOLO, java.util.Map.of(), 1000.0)), 1.0);
        GameBiddingAdvisor advisorWithModel = new GameBiddingAdvisor(
                new RuleEngine(), genome, ignored -> { }, favorableSoloValue);

        assertNull(advisorWithoutModel.recommend(player, Personality.SOLIDE));
        assertEquals(GameType.FARBSOLO,
                advisorWithModel.recommend(player, Personality.SOLIDE).contract().gameType());
    }

    @Test
    void farbwenzCountsOnlyUntersAsTopTrumpsAndScoresTrumpSuitQuality() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.OBER),
                new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER),
                new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.EICHEL, Rank.ZEHN)));
        GameBiddingAdvisor advisor = new GameBiddingAdvisor(
                new RuleEngine(), PolicyGenome.baseline(), ignored -> { }, ContractValueModel.empty());

        var features = advisor.featuresFor(player, new WenzContract(player, Suit.EICHEL));

        assertEquals(5.0, features.get("topTrumps"));
        assertEquals(4.0, features.get("unterCount"));
        assertEquals(1.0, features.get("nonTrumpOberCount"));
        assertEquals(3.0, features.get("chosenSuitTrumpCount"));
        assertEquals(2.0, features.get("chosenSuitTrumpHighCards"));
        assertEquals(24.0 / 11.0, features.get("chosenSuitTrumpPoints"));
    }

    @Test
    void farbwenzSelectsTheTrumpSuitWithTheStrongestSideTrumps() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.UNTER),
                new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER),
                new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SAU),
                new Card(Suit.EICHEL, Rank.ZEHN),
                new Card(Suit.GRAS, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.ACHT)));
        PolicyGenome genome = PolicyGenome.baseline()
                .withWeight("bid.handScore", 0.0)
                .withWeight("bid.FARBSOLO.threshold", 1000.0)
                .withWeight("bid.WENZ.threshold", 1000.0)
                .withWeight("bid.BETTEL.threshold", 1000.0)
                .withWeight("bid.FARBWENZ.threshold", 0.0)
                .withWeight("bid.FARBWENZ.chosenSuitTrumpHighCards", 10.0)
                .withWeight("bid.FARBWENZ.expectedValueCents", 0.0);

        GameBiddingAdvisor.ContractRecommendation recommendation = new GameBiddingAdvisor(
                new RuleEngine(), genome, ignored -> { }, ContractValueModel.empty())
                .recommend(player, Personality.SOLIDE);

        assertEquals(GameType.FARBWENZ, recommendation.contract().gameType());
        assertEquals(Suit.EICHEL, ((WenzContract) recommendation.contract()).trumpSuit());
    }

    @Test
    void calibratedNegativeFarbwenzExpectedValueBlocksTheBid() {
        Player player = new Player("1", "Sepp");
        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.UNTER), new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER), new Card(Suit.SCHELLEN, Rank.UNTER),
                new Card(Suit.EICHEL, Rank.SAU), new Card(Suit.EICHEL, Rank.ZEHN),
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.GRAS, Rank.ACHT)));
        PolicyGenome genome = disableOtherContracts(PolicyGenome.baseline())
                .withWeight("bid.FARBSOLO.threshold", 1000.0)
                .withWeight("bid.FARBWENZ.threshold", 0.0)
                .withWeight("bid.FARBWENZ.minimumExpectedValueCents", 0.0)
                .withWeight("bid.FARBWENZ.expectedValueCents", 0.0);
        ContractValueModel negativeValue = ContractValueModel.fit("negative-farbwenz", List.of(
                new ContractValueModel.TrainingSample(GameType.FARBWENZ, java.util.Map.of(), -50.0),
                new ContractValueModel.TrainingSample(GameType.FARBWENZ, java.util.Map.of(), -30.0)), 1.0);

        GameBiddingAdvisor.ContractRecommendation recommendation = new GameBiddingAdvisor(
                new RuleEngine(), genome, ignored -> { }, negativeValue)
                .recommend(player, Personality.SOLIDE);

        assertNull(recommendation);
    }

    private PolicyGenome disableOtherContracts(PolicyGenome genome) {
        return genome.withWeight("bid.handScore", 0.0)
                .withWeight("bid.FARBWENZ.threshold", 100.0)
                .withWeight("bid.WENZ.threshold", 100.0)
                .withWeight("bid.BETTEL.threshold", 100.0);
    }

    private PolicyGenome bettelOnlyGenome() {
        return disableOtherContracts(PolicyGenome.baseline())
                .withWeight("bid.FARBSOLO.threshold", 100.0)
                .withWeight("bid.BETTEL.threshold", 0.0)
                .withWeight("bid.BETTEL.highCards", 0.0)
                .withWeight("bid.BETTEL.lowCards", 0.0)
                .withWeight("bid.BETTEL.bettelHighCards", -2.0)
                .withWeight("bid.BETTEL.bettelTopCards", -3.0)
                .withWeight("bid.BETTEL.bettelLowCards", 0.5);
    }
}