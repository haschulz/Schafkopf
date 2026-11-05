package schafkopf.ai;

import schafkopf.model.Card;
import schafkopf.model.Player;
import schafkopf.model.Rank;
import schafkopf.model.RufspielContract;
import schafkopf.model.Suit;
import schafkopf.model.SuitCategory;
import schafkopf.rules.MoveExplanation;
import schafkopf.rules.RuleEngine;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RufspielLeadStrategy {
        private record LeadContext(
                        Player player,
                        List<Card> legalMoves,
                        RufspielContract contract,
                        Player partner,
                        boolean calledSauPlayedBefore,
                        String ruleRequirement,
                        GameMemory memory
        ) {
                private boolean isCaller() {
                        return player.equals(contract.caller());
                }

                private boolean isPartner() {
                        return player.equals(partner);
                }

                private boolean isDefender() {
                        return !isCaller() && !isPartner();
                }
        }

    private final RuleEngine rules;
    private final boolean useMemoryEngine;

    public RufspielLeadStrategy(RuleEngine rules, boolean useMemoryEngine) {
        this.rules = rules;
        this.useMemoryEngine = useMemoryEngine;
    }

    public MoveExplanation chooseLeadMove(
            Player player,
            List<Card> legalMoves,
            RufspielContract contract,
            Player partner,
            boolean calledSauPlayedBefore,
            String ruleRequirement,
            GameMemory memory) {
        LeadContext context = new LeadContext(player, legalMoves, contract, partner, calledSauPlayedBefore,
                ruleRequirement, memory);

        return choosePartnerSauLead(context)
                .or(() -> chooseCalledSuitSearchLead(context))
                .or(() -> chooseTrumpCommunicationLead(context))
                .or(() -> choosePartnerVoidLead(context))
                .or(() -> chooseDefenderTrumpPowerLead(context))
                .or(() -> chooseOwnSauLead(context))
                .or(() -> chooseSafeDefenderLead(context))
                .orElseGet(() -> chooseFallbackLead(context));
    }

    private Optional<MoveExplanation> choosePartnerSauLead(LeadContext context) {
        if (!context.isPartner() || !context.legalMoves().contains(context.contract().calledSau())) {
            return Optional.empty();
        }

        int remainingTrumps = context.memory().remainingTrumpsCount();
        long ownTrumps = context.player().hand().stream().filter(rules::isTrump).count();
        boolean lateGame = remainingTrumps - ownTrumps <= 0;
        boolean verySafeOnTrumps = remainingTrumps <= 4;
        if (!lateGame && !verySafeOnTrumps) {
            return Optional.empty();
        }

        return Optional.of(new MoveExplanation(
                context.player(),
                context.contract().calledSau(),
                context.ruleRequirement(),
                "Mitspieler spielt die gerufene Sau von vorn an (spätes Spiel / Gegner kaum Trumpf): Stich sicher einholen",
                true));
    }

    private Optional<MoveExplanation> chooseCalledSuitSearchLead(LeadContext context) {
        if (!context.isDefender() || context.calledSauPlayedBefore()) {
            return Optional.empty();
        }

        return context.legalMoves().stream()
                .filter(card -> rules.isNonTrumpSuit(card, context.contract().calledSuit()))
                .min(Comparator.comparingInt(Card::points))
                .map(card -> new MoveExplanation(
                        context.player(),
                        card,
                        context.ruleRequirement(),
                        "Nicht-Spieler sucht die Ruf-Sau mit " + card.shortName()
                                + ", um Klarheit zu schaffen und die Partnerschaft aufzudecken",
                        true));
    }

    private Optional<MoveExplanation> chooseTrumpCommunicationLead(LeadContext context) {
        if (!context.isCaller() && !context.isPartner()) {
            return Optional.empty();
        }

        List<Card> trumps = context.legalMoves().stream().filter(rules::isTrump).toList();
        if (trumps.isEmpty()) {
            return Optional.empty();
        }

        Card highestTrump = trumps.stream()
                .max(Comparator.comparingInt(rules::getTrumpStrength))
                .orElse(null);
        if (useMemoryEngine && highestTrump != null && context.memory().isHighestRemainingTrump(highestTrump)) {
            return Optional.of(new MoveExplanation(
                    context.player(),
                    highestTrump,
                    context.ruleRequirement(),
                    "Zieht höchsten verbliebenen Trumpf (" + highestTrump.shortName()
                            + ") von vorn: Volle Kontrolle & zieht Trümpfe",
                    true));
        }

        int remainingTrumps = context.memory().remainingTrumpsCount();
        if (useMemoryEngine && context.isCaller() && trumps.size() >= 2 && remainingTrumps > trumps.size()) {
            Card smallTrump = trumps.stream()
                    .min(Comparator.comparingInt(rules::getTrumpStrength))
                    .orElse(trumps.get(0));
            return Optional.of(new MoveExplanation(
                    context.player(),
                    smallTrump,
                    context.ruleRequirement(),
                    "Rufer spielt kleinen Trumpf (" + smallTrump.shortName()
                            + ") von vorn: Signal an Partner ('Zeig deine Muskeln'), mit hohem Trumpf zu stechen",
                    true));
        }

        if (context.isCaller() && highestTrump != null) {
            return Optional.of(new MoveExplanation(
                    context.player(),
                    highestTrump,
                    context.ruleRequirement(),
                    "Rufer zieht Trumpf an (" + highestTrump.shortName()
                            + "), um den Gegnern die Trümpfe zu ziehen",
                    true));
        }
        return Optional.empty();
    }

    private Optional<MoveExplanation> choosePartnerVoidLead(LeadContext context) {
        if (!useMemoryEngine) {
            return Optional.empty();
        }

                Player knownTeammate = null;
                if (context.isCaller()) {
                        knownTeammate = context.partner();
                } else if (context.isPartner()) {
                        knownTeammate = context.contract().caller();
                }
        if (knownTeammate == null) {
            return Optional.empty();
        }

        for (Suit suit : List.of(Suit.EICHEL, Suit.GRAS, Suit.SCHELLEN)) {
            Optional<MoveExplanation> decision = choosePartnerVoidLeadInSuit(context, knownTeammate, suit);
            if (decision.isPresent()) {
                return decision;
            }
        }
        return Optional.empty();
    }

    private Optional<MoveExplanation> choosePartnerVoidLeadInSuit(
            LeadContext context,
            Player knownTeammate,
            Suit suit
    ) {
        SuitCategory category = SuitCategory.fromSuit(suit);
        if (!context.memory().isDefiniteVoid(knownTeammate, category)
                || context.memory().isDefiniteVoid(knownTeammate, SuitCategory.TRUMP)) {
            return Optional.empty();
        }

        Card suitSau = new Card(suit, Rank.SAU);
        if (!context.memory().isCardPlayed(suitSau) && !context.player().hasCard(suitSau)) {
            List<Player> possibleHolders = context.memory().getPossibleHolders(suitSau, context.player());
            boolean heldByOpponent = !possibleHolders.isEmpty()
                    && possibleHolders.stream().allMatch(player ->
                            !isTeammate(player, context.player(), context.contract(), context.partner()));
            Card smallLead = lowestCardInSuit(context.legalMoves(), suit);
            if (smallLead != null && heldByOpponent) {
                String holderDescription = possibleHolders.size() == 1
                        ? possibleHolders.get(0).name()
                        : "Gegner";
                return Optional.of(new MoveExplanation(
                        context.player(),
                        smallLead,
                        context.ruleRequirement(),
                        String.format(
                                "Treibt mit %s die gegnerische %s-Sau heraus (liegt sicher bei %s): Partner %s ist farbfrei und sticht sie mit Trumpf ab!",
                                smallLead.shortName(), suit.displayName(), holderDescription, knownTeammate.name()),
                        true));
            }
        }

        Card leadSuitCard = lowestCardInSuit(context.legalMoves(), suit);
        if (leadSuitCard == null) {
            return Optional.empty();
        }
        return Optional.of(new MoveExplanation(
                context.player(),
                leadSuitCard,
                context.ruleRequirement(),
                "Spielt " + leadSuitCard.shortName() + " an: Partner " + knownTeammate.name()
                        + " ist nachweislich " + suit.displayName()
                        + "-frei und kann mit Trumpf billig einstechen!",
                true));
    }

    private Card lowestCardInSuit(List<Card> cards, Suit suit) {
        return cards.stream()
                .filter(card -> rules.isNonTrumpSuit(card, suit))
                .min(Comparator.comparingInt(Card::points))
                .orElse(null);
    }

    private Optional<MoveExplanation> chooseDefenderTrumpPowerLead(LeadContext context) {
        long trumpCount = context.player().hand().stream().filter(rules::isTrump).count();
        if (!context.isDefender() || trumpCount < 4) {
            return Optional.empty();
        }

        return context.legalMoves().stream()
                .filter(rules::isTrump)
                .max(Comparator.comparingInt(rules::getTrumpStrength))
                .filter(card -> rules.getTrumpStrength(card) >= 10)
                .map(card -> new MoveExplanation(
                        context.player(),
                        card,
                        context.ruleRequirement(),
                        "Gegenspieler hat Trumpfübermacht (" + trumpCount
                                + " Trümpfe) und übernimmt das Spiel: Zieht Trumpf an!",
                        true));
    }

    private Optional<MoveExplanation> chooseOwnSauLead(LeadContext context) {
        return context.legalMoves().stream()
                .filter(card -> !rules.isTrump(card) && card.rank() == Rank.SAU)
                .findFirst()
                .map(card -> new MoveExplanation(
                        context.player(),
                        card,
                        context.ruleRequirement(),
                        "Spielt eigene Farbsau von vorn an (" + card.shortName()
                                + "), um sichere 11 Augen einzufahren",
                        true));
    }

    private Optional<MoveExplanation> chooseSafeDefenderLead(LeadContext context) {
        if (!context.isDefender()) {
            return Optional.empty();
        }

        return context.legalMoves().stream()
                .filter(card -> !rules.isTrump(card))
                .min(Comparator.comparingInt(Card::points))
                .map(card -> new MoveExplanation(
                        context.player(),
                        card,
                        context.ruleRequirement(),
                        "Nicht-Spieler meidet Trumpf und eröffnet mit Fehlfarbe " + card.shortName(),
                        true));
    }

    private MoveExplanation chooseFallbackLead(LeadContext context) {
        Card lowest = context.legalMoves().stream()
                .min(Comparator.comparingInt(Card::points))
                .orElse(context.legalMoves().get(0));
        return new MoveExplanation(
                context.player(),
                lowest,
                context.ruleRequirement(),
                "Eröffnet mit kleiner Karte " + lowest.shortName(),
                true);
    }

    private boolean isTeammate(
            Player p1,
            Player p2,
            RufspielContract contract,
            Player partner) {
        if (p1.equals(p2))
            return true;

        boolean p1IsCallerTeam = p1.equals(contract.caller()) || p1.equals(partner);
        boolean p2IsCallerTeam = p2.equals(contract.caller()) || p2.equals(partner);

        return p1IsCallerTeam == p2IsCallerTeam;
    }
}