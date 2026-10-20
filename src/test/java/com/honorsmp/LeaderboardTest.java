package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Leaderboard;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaderboardTest {
    final HonorConfig c = new HonorConfig();

    @Test
    void bottomOnlyHasCurrentlyDishonored() {
        List<Integer> scores = List.of(-500, -50, 0, 30, 150, -120);
        assertEquals(List.of(-500, -120), Leaderboard.bottom(scores, i -> i, c, 10));
    }

    @Test
    void topOnlyHasHonorable() {
        List<Integer> scores = List.of(99, 100, 850, 20, -300, 450);
        assertEquals(List.of(850, 450, 100), Leaderboard.top(scores, i -> i, c, 10));
    }

    @Test
    void limitApplies() {
        assertEquals(2, Leaderboard.top(List.of(200, 300, 400, 500), i -> i, c, 2).size());
    }
}
