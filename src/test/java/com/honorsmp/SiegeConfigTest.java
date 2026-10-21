package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.honorsmp.HonorData;
import com.honorsmp.logic.HonorConfig;

class SiegeConfigTest {
    @Test
    void oldConfigGetsSiegeDefaults() {
        HonorConfig c = new Gson().fromJson("{\"minScore\":-500,\"donateDailyCap\":25}", HonorConfig.class);
        assertEquals(-500, c.minScore);
        assertTrue(c.siegesEnabled);
        assertEquals(2.0, c.siegeMinDays);
        assertEquals(4.0, c.siegeMaxDays);
        assertEquals(100, c.siegeHomeRadius);
        assertEquals(2, c.siegeMinMembersOnline);
        assertEquals(60, c.siegeWarnSeconds);
        assertEquals(40, c.siegeSpawnMinDistance);
        assertEquals(60, c.siegeSpawnMaxDistance);
        assertEquals(3, c.siegeMobsPerMember);
        assertEquals(60, c.siegeMaxMobsPerWave);
        assertEquals(20, c.siegeWaveDelaySeconds);
        assertEquals(150, c.siegeAbandonRadius);
        assertEquals(120, c.siegeAbandonSeconds);
        assertEquals(15, c.siegeMaxMinutes);
        assertEquals(10, c.siegeRewardHonor);
        assertEquals(60.0, c.siegeCaptainHealth);
        assertNotNull(c.siegeLoot);
        assertEquals(6, c.siegeLoot.rolls);
        assertEquals(7, c.siegeLoot.entries.size());
    }

    @Test
    void siegeConfigRoundTripsAndOverrides() {
        Gson g = new Gson();
        HonorConfig c = g.fromJson(g.toJson(new HonorConfig()), HonorConfig.class);
        assertEquals(7, c.siegeLoot.entries.size());
        HonorConfig o = g.fromJson("{\"siegesEnabled\":false,\"siegeMinDays\":0.5}", HonorConfig.class);
        assertFalse(o.siegesEnabled);
        assertEquals(0.5, o.siegeMinDays);
        assertEquals(4.0, o.siegeMaxDays);
    }

    @Test
    void oldCivLoadsWithZeroSchedule() {
        HonorData.Civ civ = new Gson().fromJson("{\"id\":\"x\",\"name\":\"X\",\"created\":5}", HonorData.Civ.class);
        assertEquals(0, civ.nextSiegeAt);
        assertEquals(0, civ.lastSiegeAt);
    }
}
