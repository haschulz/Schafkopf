package schafkopf.analysis;

import schafkopf.ai.policy.PolicyGenome;

import java.util.List;

final class ValidationOpponentPool {
    private ValidationOpponentPool() {
    }

    static List<PolicyGenome> controls(String idPrefix) {
        PolicyGenome baseline = PolicyGenome.baseline();
        PolicyGenome cautious = baseline.withId(idPrefix + "-cautious")
                .withWeight("bid.FARBSOLO.threshold", 11.0)
                .withWeight("bid.FARBWENZ.threshold", 15.0)
                .withWeight("bid.WENZ.threshold", 10.0)
                .withWeight("bid.BETTEL.threshold", 10.0);
        PolicyGenome aggressive = baseline.withId(idPrefix + "-aggressive")
                .withWeight("bid.FARBSOLO.threshold", 7.0)
                .withWeight("bid.FARBWENZ.threshold", 11.0)
                .withWeight("bid.WENZ.threshold", 6.0)
                .withWeight("bid.BETTEL.threshold", 6.0);
        PolicyGenome trumpFocused = baseline.withId(idPrefix + "-trump-focused")
                .withWeight("bid.FARBSOLO.trumpControl", 1.5)
                .withWeight("bid.FARBWENZ.trumpControl", 1.6)
                .withWeight("bid.FARBWENZ.unterCount", 3.0)
                .withWeight("card.FARBWENZ.farbwenzTrumpStrength", 1.6)
                .withWeight("card.FARBWENZ.farbwenzSoloistLeadTrump", 1.4);
        return List.of(cautious, aggressive, trumpFocused);
    }
}