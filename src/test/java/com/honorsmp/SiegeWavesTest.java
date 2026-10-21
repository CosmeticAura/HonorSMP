package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.honorsmp.logic.SiegeWaves;
import com.honorsmp.logic.SiegeWaves.SiegeMob;
import com.honorsmp.logic.SiegeWaves.Spawn;
import java.util.List;

class SiegeWavesTest {
    static int count(List<Spawn> l, SiegeMob k) {
        return l.stream().filter(s -> s.kind() == k).mapToInt(Spawn::count).sum();
    }

    @Test
    void baseMobs() {
        assertEquals(3, SiegeWaves.baseMobs(0, 3, 60));
        assertEquals(3, SiegeWaves.baseMobs(1, 3, 60));
        assertEquals(6, SiegeWaves.baseMobs(2, 3, 60));
        assertEquals(15, SiegeWaves.baseMobs(5, 3, 60));
        assertEquals(60, SiegeWaves.baseMobs(100, 3, 60));
    }

    @Test
    void wave1() {
        List<Spawn> w = SiegeWaves.compose(1, 5, 3, 60); // 15 mobs
        assertEquals(9, count(w, SiegeMob.PILLAGER));
        assertEquals(6, count(w, SiegeMob.VINDICATOR));
        assertEquals(15, SiegeWaves.totalCount(w));
        assertEquals(0, count(w, SiegeMob.WITCH));
    }

    @Test
    void wave2() {
        List<Spawn> w = SiegeWaves.compose(2, 5, 3, 60); // 15*1.3 = 20 (19.5 rounds to 20)
        assertEquals(20, count(w, SiegeMob.PILLAGER) + count(w, SiegeMob.VINDICATOR));
        assertEquals(1, count(w, SiegeMob.WITCH));
        assertEquals(1, count(w, SiegeMob.EVOKER));
        assertEquals(0, count(w, SiegeMob.RAVAGER));
        assertEquals(0, count(w, SiegeMob.CAPTAIN));
        List<Spawn> big = SiegeWaves.compose(2, 17, 3, 60);
        assertEquals(2, count(big, SiegeMob.WITCH));
        assertEquals(2, count(big, SiegeMob.EVOKER));
    }

    @Test
    void wave3() {
        List<Spawn> w = SiegeWaves.compose(3, 5, 3, 60); // 24
        assertEquals(24, count(w, SiegeMob.PILLAGER) + count(w, SiegeMob.VINDICATOR));
        assertEquals(1, count(w, SiegeMob.RAVAGER));
        assertEquals(1, count(w, SiegeMob.CAPTAIN));
        assertEquals(3, count(SiegeWaves.compose(3, 12, 3, 60), SiegeMob.RAVAGER));
        assertEquals(1, count(SiegeWaves.compose(3, 40, 3, 60), SiegeMob.CAPTAIN));
    }

    @Test
    void growsWithMembersAndWaves() {
        int prev = 0;
        for (int m = 1; m <= 40; m++) {
            int t1 = SiegeWaves.totalCount(SiegeWaves.compose(1, m, 3, 60));
            int t2 = SiegeWaves.totalCount(SiegeWaves.compose(2, m, 3, 60));
            int t3 = SiegeWaves.totalCount(SiegeWaves.compose(3, m, 3, 60));
            assertTrue(t1 >= prev, "monotonic in members");
            assertTrue(t1 < t2 && t2 < t3, "waves grow, m=" + m);
            prev = t1;
        }
    }

    @Test
    void capsRespected() {
        for (int wave = 1; wave <= 3; wave++) {
            List<Spawn> w = SiegeWaves.compose(wave, 500, 3, 60);
            assertEquals(60, count(w, SiegeMob.PILLAGER) + count(w, SiegeMob.VINDICATOR));
        }
        List<Spawn> w = SiegeWaves.compose(1, 1, 3, 60);
        assertEquals(3, SiegeWaves.totalCount(w));
    }

    @Test
    void waveClamped() {
        assertEquals(SiegeWaves.compose(1, 4, 3, 60), SiegeWaves.compose(0, 4, 3, 60));
        assertEquals(SiegeWaves.compose(3, 4, 3, 60), SiegeWaves.compose(9, 4, 3, 60));
    }
}
