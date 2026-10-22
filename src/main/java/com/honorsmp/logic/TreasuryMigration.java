package com.honorsmp.logic;

import com.honorsmp.HonorData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 1.6.2: donations never go to the judges' treasury. Food donated before 1.4.0 (and anything else donatable
 * that ended up there) is taken out of the treasury once and shared out like a new donation.
 * Pure list logic, so it is tested without Minecraft.
 */
public final class TreasuryMigration {
    private TreasuryMigration() {}

    /**
     * Removes every stack that {@code moves} accepts from {@code treasury} (in place) and returns them in order.
     * Runs once: sets {@code d.treasuryDonationsMoved}, and later calls return nothing. With nobody to share with
     * nothing is touched and the flag stays false, so it is retried at the next start.
     */
    public static <S> List<S> runOnce(HonorData d, List<S> treasury, Collection<String> recipients, Predicate<S> moves) {
        if (d.treasuryDonationsMoved) return new ArrayList<>();
        if (recipients == null || recipients.isEmpty()) return new ArrayList<>();
        List<S> moved = new ArrayList<>();
        for (Iterator<S> it = treasury.iterator(); it.hasNext(); ) {
            S s = it.next();
            if (moves.test(s)) {
                moved.add(s);
                it.remove();
            }
        }
        d.treasuryDonationsMoved = true;
        return moved;
    }
}
