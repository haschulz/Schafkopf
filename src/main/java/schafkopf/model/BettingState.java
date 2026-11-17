package schafkopf.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the betting/multiplier state for a Schafkopf game session.
 *
 * <p>Supports the following multiplier events:
 * <ul>
 *   <li><b>Klopfen (Doubling before deal)</b>: any player may double the stakes before
 *       the first card of the deal is revealed.  After one doubling, further pre-deal
 *       doublings are not allowed ({@code doublingAllowed} becomes false).</li>
 *   <li><b>Kontra</b>: opponent team challenges after the game is announced
 *       (doubles the base multiplier).</li>
 *   <li><b>Retour</b>: caller team re-challenges after Kontra (doubles again).</li>
 *   <li><b>Eindoppeln</b>: when 4+ consecutive passes occur the next game is
 *       played with a doubled stake (handled externally via {@link #applyDouble()}).</li>
 * </ul>
 */
public class BettingState {

    public record StakeBreakdown(
            List<String> klopferIds,
            int additionalKlopfer,
            int stockDoubles,
            int stockFactor,
            boolean kontra,
            boolean retour,
            int totalMultiplier
    ) {
        public StakeBreakdown {
            klopferIds = List.copyOf(klopferIds);
        }

        public int klopferCount() {
            return klopferIds.size() + additionalKlopfer;
        }
    }

    private int multiplier = 1;
    private int stockFactor = 1;
    private boolean doublingAllowed = true;
    private int additionalKlopfer;

    // tracks kontra/retour steps so we can cap or display them
    private int kontraCount = 0;
    private int retourCount = 0;
    private final Set<String> playersWhoDoubled = new LinkedHashSet<>();

    /**
     * Applies a pre-deal doubling (Klopfen / Eindoppeln).
     * After the first doubling, further pre-deal doublings are disabled.
     */
    public void applyDouble() {
        multiplier = Math.multiplyExact(multiplier, 2);
        additionalKlopfer++;
        doublingAllowed = false;
    }

    public boolean applyDouble(Player player) {
        if (!playersWhoDoubled.add(player.id())) {
            return false;
        }
        multiplier = Math.multiplyExact(multiplier, 2);
        return true;
    }

    public boolean canDouble(Player player) {
        return !playersWhoDoubled.contains(player.id());
    }

    public void applyStock(int stockMultiplier) {
        if (stockMultiplier < 2 || stockMultiplier > 32 || Integer.bitCount(stockMultiplier) != 1) {
            throw new IllegalArgumentException("stockMultiplier must be a power of two from 2 through 32");
        }
        if (stockFactor > 32 / stockMultiplier) {
            throw new IllegalArgumentException("Stock factor cannot exceed 32");
        }
        multiplier = Math.multiplyExact(multiplier, stockMultiplier);
        stockFactor = Math.multiplyExact(stockFactor, stockMultiplier);
    }

    /**
     * Returns whether a pre-deal doubling (Klopfen) is still allowed.
     */
    public boolean canDouble() {
        return doublingAllowed;
    }

    /**
     * Current stake multiplier (1 = normal, 2 = doubled, 4 = quadrupled, …).
     */
    public int multiplier() {
        return multiplier;
    }

    public int stockFactor() {
        return stockFactor;
    }

    public int stockDoubles() {
        return Integer.numberOfTrailingZeros(stockFactor);
    }

    public void restoreLegacyMultiplier(int legacyMultiplier) {
        if (legacyMultiplier < 1) {
            throw new IllegalArgumentException("legacyMultiplier must be positive");
        }
        multiplier = legacyMultiplier;
        if (Integer.bitCount(legacyMultiplier) == 1) {
            additionalKlopfer = Integer.numberOfTrailingZeros(legacyMultiplier);
            doublingAllowed = legacyMultiplier == 1;
        }
    }

    public StakeBreakdown breakdown() {
        return new StakeBreakdown(new ArrayList<>(playersWhoDoubled), additionalKlopfer,
                stockDoubles(), stockFactor, kontraCount > 0, retourCount > 0, multiplier);
    }

    /**
     * Kontra: the defending team challenges the caller's team after the game is announced.
     * Doubles the multiplier.
     */
    public void kontra() {
        if (!callKontra()) {
            throw new IllegalStateException("Kontra has already been called");
        }
    }

    public boolean callKontra() {
        if (kontraCount > 0) {
            return false;
        }
        multiplier = Math.multiplyExact(multiplier, 2);
        kontraCount++;
        return true;
    }

    /**
     * Retour: the caller's team re-challenges after the opponents' Kontra.
     * Doubles the multiplier again.
     */
    public void retour() {
        if (!callRetour()) {
            throw new IllegalStateException("Re requires Kontra and may only be called once");
        }
    }

    public boolean callRetour() {
        if (kontraCount == 0 || retourCount > 0) {
            return false;
        }
        multiplier = Math.multiplyExact(multiplier, 2);
        retourCount++;
        return true;
    }

    public int kontraCount() {
        return kontraCount;
    }

    public int retourCount() {
        return retourCount;
    }

    /** Resets all state for a new game. */
    public void reset() {
        multiplier = 1;
        stockFactor = 1;
        doublingAllowed = true;
        additionalKlopfer = 0;
        kontraCount = 0;
        retourCount = 0;
        playersWhoDoubled.clear();
    }

    @Override
    public String toString() {
        return String.format("BettingState{multiplier=%d, doublingAllowed=%b, kontra=%d, retour=%d}",
                multiplier, doublingAllowed, kontraCount, retourCount);
    }
}
