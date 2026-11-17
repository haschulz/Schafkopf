package schafkopf.model;

import org.junit.jupiter.api.Test;
import schafkopf.ai.SimpleBot;
import schafkopf.config.SchafkopfConfig;
import schafkopf.economy.ScoreLedger;
import schafkopf.economy.Tariff;
import schafkopf.rules.RuleEngine;
import schafkopf.rules.GameBiddingRules;
import schafkopf.state.GameResult;
import schafkopf.state.SchafkopfGame;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContractRulesTest {

    private final RuleEngine rules = new RuleEngine();

    @Test
    void rufspielContractImplementsSharedInterfaceAndKeepsCalledSauSpecific() {
        Player caller = new Player("1", "Sepp");
        RufspielContract contract = new RufspielContract(caller, Suit.EICHEL);

        assertInstanceOf(GameContract.class, contract);
        assertEquals(GameType.RUFSPIEL, contract.gameType());
        assertEquals(new Card(Suit.EICHEL, Rank.SAU), contract.calledSau());
        assertEquals("Rufspiel auf die Eichel-Sau (gerufen von Sepp)", contract.displayName());
    }

    @Test
    void rufspielTrumpfUndKartenstaerkenSindKorrekt() {
        RufspielContract contract =
                new RufspielContract(new Player("1", "Sepp"), Suit.EICHEL);

        assertTrue(contract.isTrump(new Card(Suit.EICHEL, Rank.OBER), rules));
        assertTrue(contract.isTrump(new Card(Suit.HERZ, Rank.SIEBEN), rules));
        assertFalse(contract.isTrump(new Card(Suit.EICHEL, Rank.SAU), rules));

        assertEquals(14, contract.getTrumpStrength(new Card(Suit.EICHEL, Rank.OBER)));
        assertEquals(1, contract.getTrumpStrength(new Card(Suit.HERZ, Rank.SIEBEN)));
        assertEquals(-1, contract.getTrumpStrength(new Card(Suit.EICHEL, Rank.SAU)));

        assertEquals(6, contract.getSuitStrength(new Card(Suit.EICHEL, Rank.SAU)));
        assertEquals(-1, contract.getSuitStrength(new Card(Suit.HERZ, Rank.SIEBEN)));
    }

    @Test
    void herzsauKannNichtImRufspielGerufenWerden() {
        Player caller = new Player("1", "Sepp");

        assertThrows(
                IllegalArgumentException.class,
                () -> new RufspielContract(caller, Suit.HERZ)
        );
    }

    @Test
    void rufspielPartnerMustNotLeadUnderCalledAceWithoutFourCardsOfTheSuit() {
        Player caller = new Player("1", "Sepp");
        Player partner = new Player("2", "Hans");
        RufspielContract contract = new RufspielContract(caller, Suit.EICHEL);
        Card calledAce = contract.calledSau();
        Card eichelTen = new Card(Suit.EICHEL, Rank.ZEHN);
        partner.setHand(List.of(
                calledAce, eichelTen,
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.HERZ, Rank.SIEBEN), new Card(Suit.HERZ, Rank.ACHT),
                new Card(Suit.SCHELLEN, Rank.SIEBEN), new Card(Suit.SCHELLEN, Rank.ACHT)));
        Trick trick = new Trick(1, caller);

        List<Card> legalMoves = rules.getLegalMoves(partner, trick, contract, false, 1);

        assertTrue(legalMoves.contains(calledAce));
        assertFalse(legalMoves.contains(eichelTen));
    }

    @Test
    void rufspielPartnerMayRunUnderCalledAceWithFourCardsOfTheSuit() {
        Player caller = new Player("1", "Sepp");
        Player partner = new Player("2", "Hans");
        RufspielContract contract = new RufspielContract(caller, Suit.EICHEL);
        Card eichelTen = new Card(Suit.EICHEL, Rank.ZEHN);
        partner.setHand(List.of(
                contract.calledSau(), eichelTen,
                new Card(Suit.EICHEL, Rank.KOENIG), new Card(Suit.EICHEL, Rank.NEUN),
                new Card(Suit.GRAS, Rank.SIEBEN), new Card(Suit.GRAS, Rank.ACHT),
                new Card(Suit.HERZ, Rank.SIEBEN), new Card(Suit.SCHELLEN, Rank.ACHT)));
        Trick trick = new Trick(1, caller);

        assertTrue(rules.getLegalMoves(partner, trick, contract, false, 1).contains(eichelTen));
    }

    @Test
    void wenzBedientDieAngespielteFehlfarbe() {
        Player caller = new Player("1", "Sepp");
        Player player = new Player("2", "Hans");
        WenzContract contract = new WenzContract(caller);

        player.setHand(List.of(
                new Card(Suit.EICHEL, Rank.SIEBEN),
                new Card(Suit.GRAS, Rank.SIEBEN),
                new Card(Suit.SCHELLEN, Rank.UNTER)
        ));

        Trick trick = new Trick(1, caller);
        trick.addCard(caller, new Card(Suit.EICHEL, Rank.SAU));

        List<Card> legalMoves =
                rules.getLegalMovesForContract(player, trick, contract, false, 1);

        assertEquals(List.of(new Card(Suit.EICHEL, Rank.SIEBEN)), legalMoves);
    }

    @Test
    void geierUsesOnlyOberAsTrumpAndRanksUnterAsSuitCard() {
        GeierContract contract = new GeierContract(new Player("1", "Sepp"));

        assertTrue(contract.isTrump(new Card(Suit.EICHEL, Rank.OBER), rules));
        assertFalse(contract.isTrump(new Card(Suit.EICHEL, Rank.UNTER), rules));
        assertEquals(4, contract.getTrumpStrength(new Card(Suit.EICHEL, Rank.OBER)));
        assertEquals(1, contract.getTrumpStrength(new Card(Suit.SCHELLEN, Rank.OBER)));
        assertEquals(3, contract.getSuitStrength(new Card(Suit.GRAS, Rank.UNTER)));
    }

    @Test
    void trickWinnerUsesContractSpecificTrumpRules() {
        Player leader = new Player("1", "Sepp");
        Player oberPlayer = new Player("2", "Hans");
        Trick trick = new Trick(1, leader);
        Card eichelSau = new Card(Suit.EICHEL, Rank.SAU);
        Card eichelOber = new Card(Suit.EICHEL, Rank.OBER);
        trick.addCard(leader, eichelSau);
        trick.addCard(oberPlayer, eichelOber);

        assertEquals(leader, rules.evaluateTrickWinner(trick, new WenzContract(leader)).player());
        assertEquals(oberPlayer,
                rules.evaluateTrickWinner(trick, new RufspielContract(leader, Suit.GRAS)).player());
    }

    @Test
    void bettelHasNoTrumpsAndUsesBettelRankOrder() {
        BettelContract contract = new BettelContract(new Player("1", "Sepp"));
        Card eichelOber = new Card(Suit.EICHEL, Rank.OBER);
        Card eichelSau = new Card(Suit.EICHEL, Rank.SAU);

        assertFalse(contract.isTrump(eichelOber, rules));
        assertEquals(5, contract.getSuitStrength(eichelOber));
        assertEquals(8, contract.getSuitStrength(eichelSau));
    }

    @Test
    void biddingOnlyAllowsContractsAboveThePreviousAnnouncement() {
        Player caller = new Player("1", "Sepp");
        GameBiddingRules bidding = new GameBiddingRules();
        GameContract rufspiel = new RufspielContract(caller, Suit.EICHEL);

        assertFalse(bidding.canAnnounce(new RufspielContract(caller, Suit.GRAS), rufspiel));
        assertTrue(bidding.canAnnounce(new WenzContract(caller), rufspiel));
        assertTrue(bidding.canAnnounce(new SoloContract(caller, Suit.EICHEL), rufspiel));
        assertTrue(bidding.canAnnounce(new SoloContract(caller, Suit.EICHEL, true), rufspiel));
        assertTrue(bidding.canAnnounce(new WenzContract(caller, true), rufspiel));
        assertFalse(bidding.canAnnounce(new WenzContract(caller), new WenzContract(caller)));
        assertTrue(bidding.canAnnounce(new WenzContract(caller, true), new WenzContract(caller)));
    }

    @Test
    void soloDisplayNamesTheCallerAsThePlayerNotAsSomeoneWhoWasCalled() {
        Player soloist = new Player("3", "Franz");

        assertEquals("Farbsolo auf Gras (gespielt von Franz)",
                new SoloContract(soloist, Suit.GRAS).displayName());
        assertEquals("Wenz (gespielt von Franz)", new WenzContract(soloist).displayName());
        assertEquals("Farbwenz auf Gras (gespielt von Franz)",
            new WenzContract(soloist, Suit.GRAS).displayName());
        assertEquals("Geier (gespielt von Franz)", new GeierContract(soloist).displayName());
        assertEquals("Bettel (gespielt von Franz)", new BettelContract(soloist).displayName());
    }

    @Test
    void toutRequiresAllEightTricksAndPaysExistingSchwarzBonus() {
        Player caller = new Player("1", "Sepp");
        assertToutResult(new SoloContract(caller, Suit.EICHEL, true), true);
        assertToutResult(new WenzContract(caller, true), false);
    }

    @Test
    void laufendeMinimumDependsOnContractTrumpRank() {
        Player caller = new Player("1", "P1");
        List<Card> unters = List.of(
                new Card(Suit.EICHEL, Rank.UNTER), new Card(Suit.GRAS, Rank.UNTER),
                new Card(Suit.HERZ, Rank.UNTER), new Card(Suit.SCHELLEN, Rank.UNTER));
        List<Card> obers = List.of(
                new Card(Suit.EICHEL, Rank.OBER), new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.OBER), new Card(Suit.SCHELLEN, Rank.OBER));

        assertEquals(0, paymentForLaufende(new WenzContract(caller, Suit.EICHEL), unters, 2).laufendeCents());
        assertEquals(30, paymentForLaufende(new WenzContract(caller, Suit.EICHEL), unters, 3).laufendeCents());
        assertEquals(20, paymentForLaufende(new WenzContract(caller), unters, 2).laufendeCents());
        assertEquals(0, paymentForLaufende(new SoloContract(caller, Suit.EICHEL), obers, 2).laufendeCents());
        assertEquals(30, paymentForLaufende(new SoloContract(caller, Suit.EICHEL), obers, 3).laufendeCents());
    }

    @Test
    void toutDoublesLaufendeButDoesNotChargeSchneiderOrSchwarz() {
        Player caller = new Player("1", "P1");
        List<Player> players = List.of(caller, new Player("2", "P2"),
                new Player("3", "P3"), new Player("4", "P4"));
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(ignored -> { });
        game.setupContract(new SoloContract(caller, Suit.EICHEL, true));
        List<Card> topTrumps = List.of(
                new Card(Suit.EICHEL, Rank.OBER), new Card(Suit.GRAS, Rank.OBER),
                new Card(Suit.HERZ, Rank.OBER), new Card(Suit.SCHELLEN, Rank.OBER));
        for (int index = 0; index < 8; index++) {
            Player winner = index < 4 ? caller : players.get(1);
            Card card = index < 4 ? topTrumps.get(index) : new Card(Suit.EICHEL, Rank.SIEBEN);
            Trick trick = new Trick(index + 1, winner);
            trick.addCard(winner, card);
            trick.setWinningPlayedCard(new schafkopf.model.PlayedCard(winner, card));
            winner.addWonTrick(trick);
            game.completedTricks().add(trick);
        }
        game.finishGame();

        Tariff.PaymentBreakdown payment = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);

        assertEquals(100, payment.baseCents());
        assertEquals(80, payment.laufendeCents());
        assertEquals(0, payment.schneiderCents());
        assertEquals(0, payment.schwarzCents());
        assertEquals(180, payment.totalCentsPerPlayer());
    }

    private Tariff.PaymentBreakdown paymentForLaufende(
            GameContract contract, List<Card> topTrumps, int callerHolds
    ) {
        Player caller = contract.caller();
        Player defender = new Player("2", "P2");
        List<Player> players = List.of(caller, defender, new Player("3", "P3"), new Player("4", "P4"));
        if (contract instanceof RufspielContract rufspiel) {
            defender.setHand(List.of(rufspiel.calledSau()));
        }
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(ignored -> { });
        game.setupContract(contract);
        for (int index = 0; index < topTrumps.size(); index++) {
            Player holder = index < callerHolds ? caller : defender;
            Trick trick = new Trick(index + 1, holder);
            trick.addCard(holder, topTrumps.get(index));
            holder.addWonTrick(trick);
            game.completedTricks().add(trick);
        }
        game.finishGame();
        return new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);
    }

    private void assertToutResult(GameContract contract, boolean callerWins) {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(message -> { });
        game.setupContract(contract instanceof SoloContract
                ? new SoloContract(players.get(0), ((SoloContract) contract).trumpSuit(), true)
                : new WenzContract(players.get(0), true));
        for (int trickNumber = 1; trickNumber <= 8; trickNumber++) {
            Trick trick = new Trick(trickNumber, players.get(0));
            trick.setWinningPlayedCard(new schafkopf.model.PlayedCard(
                    players.get(callerWins || trickNumber < 8 ? 0 : 1),
                new Card(Suit.EICHEL, Rank.OBER)));
            Player winner = trick.winningPlayedCard().player();
            winner.addWonTrick(trick);
            game.completedTricks().add(trick);
        }

        GameResult result = game.finishGame();
        Tariff.PaymentBreakdown payment = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);

        assertEquals(callerWins, result.callerTeamWon());
        assertEquals(callerWins, result.isSchwarz());
        assertFalse(result.isSchneider());
        assertEquals(0, payment.schneiderCents());
        assertEquals(0, payment.schwarzCents());
        assertEquals(100, payment.totalCentsPerPlayer());
    }

    @Test
    void gameEnginePlaysAndScoresSoloBettelAndRamsch() {
        assertCompleteGame(GameType.FARBSOLO);
        assertCompleteGame(GameType.GEIER);
        assertCompleteGame(GameType.BETTEL);
        assertCompleteGame(GameType.RAMSCH);
    }

    @Test
    void bettelIsLostAfterAZeroPointTrick() {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        BettelContract contract = new BettelContract(players.get(0));
        game.setupContract(contract);

        Trick zeroPointTrick = new Trick(1, players.get(0));
        zeroPointTrick.addCard(players.get(0), new Card(Suit.EICHEL, Rank.SIEBEN));
        zeroPointTrick.addCard(players.get(1), new Card(Suit.GRAS, Rank.SIEBEN));
        zeroPointTrick.addCard(players.get(2), new Card(Suit.HERZ, Rank.SIEBEN));
        zeroPointTrick.addCard(players.get(3), new Card(Suit.SCHELLEN, Rank.SIEBEN));
        players.get(0).addWonTrick(zeroPointTrick);
        game.completedTricks().add(zeroPointTrick);

        GameResult result = game.finishGame();

        assertFalse(result.callerTeamWon());
        assertEquals(0, result.callerTeamAugen());
    }

    @Test
    void schwarzRequiresZeroTricksRatherThanZeroPoints() {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setupContract(new SoloContract(players.get(0), Suit.EICHEL));

        Trick zeroPointTrick = new Trick(1, players.get(0));
        zeroPointTrick.addCard(players.get(0), new Card(Suit.GRAS, Rank.SIEBEN));
        zeroPointTrick.addCard(players.get(1), new Card(Suit.SCHELLEN, Rank.SIEBEN));
        rules.evaluateTrickWinner(zeroPointTrick, game.gameContract());
        players.get(0).addWonTrick(zeroPointTrick);
        game.completedTricks().add(zeroPointTrick);

        Trick pointsTrick = new Trick(2, players.get(1));
        pointsTrick.addCard(players.get(1), new Card(Suit.EICHEL, Rank.SAU));
        pointsTrick.addCard(players.get(2), new Card(Suit.GRAS, Rank.SAU));
        pointsTrick.addCard(players.get(3), new Card(Suit.SCHELLEN, Rank.SAU));
        rules.evaluateTrickWinner(pointsTrick, game.gameContract());
        players.get(1).addWonTrick(pointsTrick);
        game.completedTricks().add(pointsTrick);

        GameResult result = game.finishGame();

        assertTrue(result.isSchneider());
        assertFalse(result.isSchwarz());
        game.setSchneiderPaid(false);
        Tariff.PaymentBreakdown breakdown = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);
        assertEquals(0, breakdown.schneiderCents());
        assertEquals(50, breakdown.totalCentsPerPlayer());
    }

    @Test
    void schwarzBonusIsOnlyPaidWhenClaimed() {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(message -> { });
        game.setupContract(new SoloContract(players.get(0), Suit.EICHEL));

        Trick trick = new Trick(1, players.get(0));
        trick.addCard(players.get(0), new Card(Suit.EICHEL, Rank.OBER));
        trick.addCard(players.get(1), new Card(Suit.GRAS, Rank.SIEBEN));
        trick.addCard(players.get(2), new Card(Suit.SCHELLEN, Rank.SIEBEN));
        trick.addCard(players.get(3), new Card(Suit.EICHEL, Rank.SIEBEN));
        rules.evaluateTrickWinner(trick, game.gameContract());
        for (int i = 0; i < 22; i++) {
            players.get(0).addWonTrick(trick);
            game.completedTricks().add(trick);
        }
        GameResult result = game.finishGame();
        assertTrue(result.isSchwarz());

        game.setSchwarzClaimed(false);
        Tariff.PaymentBreakdown unclaimed = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);
        game.setSchwarzClaimed(true);
        Tariff.PaymentBreakdown claimed = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);

        assertEquals(0, unclaimed.schwarzCents());
        assertEquals(10, claimed.schwarzCents());
        assertEquals(10, claimed.totalCentsPerPlayer() - unclaimed.totalCentsPerPlayer());
    }

    @Test
    void soloSettlementUsesSoloTariffAndPaysOneAgainstThree() {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        List<List<Card>> hands = new Deck().deal4x8();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setHand(hands.get(i));
        }
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(message -> { });
        game.setupContract(new SoloContract(players.get(0), Suit.EICHEL));
        game.bettingState().applyStock(2);
        assertTrue(game.bettingState().applyDouble(players.get(1)));
        game.playFullGame(players.get(0));

        Tariff.PaymentBreakdown breakdown = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);
        ScoreLedger ledger = new ScoreLedger(players, new Tariff(SchafkopfConfig.defaults()));
        ledger.settleGame(game);

        assertEquals(50, breakdown.baseCents());
        assertEquals(4, game.bettingState().multiplier());
        assertEquals((breakdown.baseCents() + breakdown.schneiderCents() + breakdown.schwarzCents()
                + breakdown.laufendeCents()) * game.bettingState().multiplier(), breakdown.totalCentsPerPlayer());
        assertEquals(0, players.stream().mapToInt(ledger::getBalance).sum());
        if (game.result().callerTeamWon()) {
            assertEquals(breakdown.totalCentsPerPlayer() * 3, ledger.getBalance(players.get(0)));
        } else {
            assertEquals(-breakdown.totalCentsPerPlayer() * 3, ledger.getBalance(players.get(0)));
        }
    }

    @Test
    void oneRamschJungfrauDoublesLoserPaymentAndKeepsLedgerZeroSum() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {80, 20, 20, 0}, new int[] {8, 2, 2, 0});
        Tariff tariff = new Tariff(SchafkopfConfig.defaults());

        Tariff.PaymentBreakdown breakdown = tariff.calculatePayment(game);
        ScoreLedger ledger = new ScoreLedger(players, tariff);
        ledger.settleGame(game);
        int doubledTariff = SchafkopfConfig.defaults().rufspielTariffCents() * 2;

        assertEquals(1, breakdown.jungfrauCount());
        assertEquals(2, breakdown.jungfrauMultiplier());
        assertFalse(breakdown.durchmarsch());
        assertEquals(doubledTariff, breakdown.totalCentsPerPlayer());
        assertEquals(-doubledTariff * 3, ledger.getBalance(players.get(0)));
        assertEquals(doubledTariff, ledger.getBalance(players.get(1)));
        assertEquals(doubledTariff, ledger.getBalance(players.get(2)));
        assertEquals(doubledTariff, ledger.getBalance(players.get(3)));
        assertEquals(0, players.stream().mapToInt(ledger::getBalance).sum());
    }

    @Test
    void twoRamschJungfrauenMultiplyTariffByFour() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {80, 40, 0, 0}, new int[] {8, 4, 0, 0});

        Tariff.PaymentBreakdown breakdown = new Tariff(SchafkopfConfig.defaults()).calculatePayment(game);

        assertEquals(2, breakdown.jungfrauCount());
        assertEquals(4, breakdown.jungfrauMultiplier());
        assertEquals(SchafkopfConfig.defaults().rufspielTariffCents() * 4,
            breakdown.totalCentsPerPlayer());
    }

    @Test
    void ramschDurchmarschUsesSoloTariffAndIgnoresJungfrauen() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {100, 10, 10, 0}, new int[] {10, 1, 1, 0});
        Tariff tariff = new Tariff(SchafkopfConfig.defaults());
        Tariff.PaymentBreakdown breakdown = tariff.calculatePayment(game);
        ScoreLedger ledger = new ScoreLedger(players, tariff);
        ledger.settleGame(game);

        assertTrue(breakdown.durchmarsch());
        assertEquals(0, breakdown.jungfrauCount());
        assertEquals(1, breakdown.jungfrauMultiplier());
        int soloTariff = SchafkopfConfig.defaults().soloTariffCents();
        assertEquals(soloTariff, breakdown.baseCents());
        assertEquals(soloTariff, breakdown.totalCentsPerPlayer());
        assertEquals(soloTariff * 3, ledger.getBalance(players.get(0)));
        assertEquals(-soloTariff, ledger.getBalance(players.get(1)));
        assertEquals(-soloTariff, ledger.getBalance(players.get(2)));
        assertEquals(-soloTariff, ledger.getBalance(players.get(3)));
    }

    @Test
    void exactNinetyEyesIsNotDurchmarschAndMostTricksBreaksEyesTie() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {90, 10, 10, 10}, new int[] {9, 1, 1, 1});

        assertFalse(game.result().callerTeam().contains(players.get(0)));
        assertTrue(game.result().callerTeam().contains(players.get(3)));
        assertFalse(new Tariff(SchafkopfConfig.defaults()).calculatePayment(game).durchmarsch());
    }

    @Test
    void playerWithMoreTricksLosesWhenHighestEyesAreTied() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {50, 50, 10, 10}, new int[] {6, 5, 1, 1});

        assertEquals(List.of(players.get(0)), game.result().defenderTeam());
        assertTrue(game.result().callerTeam().contains(players.get(1)));
    }

    @Test
    void tiedHighestEyesAndTricksMakeBothPlayersPayTheRemainingWinners() {
        List<Player> players = ramschPlayers();
        SchafkopfGame game = finishSyntheticRamsch(players,
                new int[] {50, 50, 10, 10}, new int[] {5, 5, 1, 1});
        Tariff tariff = new Tariff(SchafkopfConfig.defaults());
        ScoreLedger ledger = new ScoreLedger(players, tariff);
        ledger.settleGame(game);
        int baseTariff = SchafkopfConfig.defaults().rufspielTariffCents();

        assertEquals(2, game.result().defenderTeam().size());
        assertTrue(game.result().defenderTeam().contains(players.get(0)));
        assertTrue(game.result().defenderTeam().contains(players.get(1)));
        assertEquals(-baseTariff * 2, ledger.getBalance(players.get(0)));
        assertEquals(-baseTariff * 2, ledger.getBalance(players.get(1)));
        assertEquals(baseTariff * 2, ledger.getBalance(players.get(2)));
        assertEquals(baseTariff * 2, ledger.getBalance(players.get(3)));
        assertEquals(0, players.stream().mapToInt(ledger::getBalance).sum());
    }

    private List<Player> ramschPlayers() {
        return List.of(new Player("1", "P1"), new Player("2", "P2"),
                new Player("3", "P3"), new Player("4", "P4"));
    }

    private SchafkopfGame finishSyntheticRamsch(List<Player> players, int[] points, int[] tricks) {
        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(ignored -> { });
        game.setupContract(new RamschContract());
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            int remainingPoints = points[playerIndex];
            for (int trickIndex = 0; trickIndex < tricks[playerIndex]; trickIndex++) {
                Trick trick = new Trick(trickIndex + 1, players.get(playerIndex));
                while (remainingPoints >= 10 && trick.playedCards().size() < 4) {
                    trick.addCard(players.get(playerIndex), new Card(Suit.EICHEL, Rank.ZEHN));
                    remainingPoints -= 10;
                }
                players.get(playerIndex).addWonTrick(trick);
            }
            assertEquals(0, remainingPoints, "Synthetic Ramsch test points must be multiples of ten");
        }
        game.finishGame();
        return game;
    }

    private void assertCompleteGame(GameType gameType) {
        List<Player> players = List.of(
                new Player("1", "P1"),
                new Player("2", "P2"),
                new Player("3", "P3"),
                new Player("4", "P4"));
        GameContract contract = switch (gameType) {
            case FARBSOLO -> new SoloContract(players.get(0), Suit.EICHEL);
            case GEIER -> new GeierContract(players.get(0));
            case BETTEL -> new BettelContract(players.get(0));
            case RAMSCH -> new RamschContract();
            default -> throw new IllegalArgumentException("Unsupported test game type: " + gameType);
        };
        List<List<Card>> hands = new Deck().deal4x8();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setHand(hands.get(i));
        }

        SchafkopfGame game = new SchafkopfGame(players, rules, new SimpleBot(rules));
        game.setLogger(message -> { });
        game.setupContract(contract);

        GameResult result = game.playFullGame(players.get(0));

        assertEquals(gameType, result.contract().gameType());
        assertEquals(8, game.completedTricks().size());
        assertEquals(120, result.callerTeamAugen() + result.defenderTeamAugen());
    }
}