package com.kronos.chiron.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceTierTest {

    @Test
    void ephebe_isLowestLevel() {
        assertThat(PerformanceTier.EPHEBE.getLevel()).isEqualTo(1);
    }

    @Test
    void olympien_isHighestLevel() {
        assertThat(PerformanceTier.OLYMPIEN.getLevel()).isEqualTo(8);
    }

    @Test
    void allTiers_haveUniqueLevels() {
        long distinct = java.util.Arrays.stream(PerformanceTier.values())
                .mapToInt(PerformanceTier::getLevel)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(PerformanceTier.values().length);
    }

    @Test
    void allTiers_haveNonNullNomAndCategorie() {
        for (PerformanceTier t : PerformanceTier.values()) {
            assertThat(t.getNom()).isNotBlank();
            assertThat(t.getCategorie()).isNotBlank();
        }
    }

    @Test
    void levels_increaseMonotonically() {
        PerformanceTier[] tiers = PerformanceTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertThat(tiers[i].getLevel()).isGreaterThan(tiers[i - 1].getLevel());
        }
    }
}
