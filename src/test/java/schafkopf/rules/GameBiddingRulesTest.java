package schafkopf.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import schafkopf.model.GameContract;
import schafkopf.model.Player;
import schafkopf.model.RufspielContract;
import schafkopf.model.SoloContract;
import schafkopf.model.Suit;
import schafkopf.model.WenzContract;

class GameBiddingRulesTest {
    @Test
    void onlyStrictlyHigherContractsCanBeAnnounced() {
        Player caller = new Player("1", "Caller");
        GameContract sauspiel = new RufspielContract(caller, Suit.GRAS);
        GameBiddingRules rules = new GameBiddingRules();

        assertFalse(rules.canAnnounce(new RufspielContract(caller, Suit.EICHEL), sauspiel));
        assertTrue(rules.canAnnounce(new WenzContract(caller), sauspiel));
        assertTrue(rules.canAnnounce(new WenzContract(caller), null));
    }

    @Test
    void followsTheSauspielTeachingPageAnnouncementOrder() {
        Player caller = new Player("1", "Caller");
        GameContract solo = new SoloContract(caller, Suit.EICHEL);
        GameContract soloTout = new SoloContract(caller, Suit.EICHEL, true);
        GameContract wenz = new WenzContract(caller);
        GameContract farbwenz = new WenzContract(caller, Suit.GRAS);
        GameContract wenzTout = new WenzContract(caller, true);
        GameContract rufspiel = new RufspielContract(caller, Suit.EICHEL);
        GameBiddingRules rules = new GameBiddingRules();

        assertTrue(rules.canAnnounce(farbwenz, rufspiel));
        assertTrue(rules.canAnnounce(wenz, farbwenz));
        assertTrue(rules.canAnnounce(solo, wenz));
        assertTrue(rules.canAnnounce(wenzTout, solo));
        assertTrue(rules.canAnnounce(soloTout, wenzTout));
        assertFalse(rules.canAnnounce(rufspiel, farbwenz));
        assertFalse(rules.canAnnounce(farbwenz, wenz));
        assertFalse(rules.canAnnounce(wenz, solo));
        assertFalse(rules.canAnnounce(wenzTout, soloTout));
        assertFalse(rules.canAnnounce(solo, soloTout));
        assertTrue(rules.supports(schafkopf.model.GameType.FARBWENZ));
    }
}