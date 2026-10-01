package com.copiloto.motorista.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalTest {

    @Test
    fun `crossing the goal for the first time returns true`() {
        // 280 + 30 = 310 >= 300, and 280 was below 300.
        assertTrue(DailyGoal.crossed(before = 280.0, added = 30.0, goal = 300.0))
    }

    @Test
    fun `exactly reaching the goal counts as crossing`() {
        assertTrue(DailyGoal.crossed(before = 290.0, added = 10.0, goal = 300.0))
    }

    @Test
    fun `already past the goal does not cross again`() {
        assertFalse(DailyGoal.crossed(before = 300.0, added = 20.0, goal = 300.0))
    }

    @Test
    fun `staying below the goal does not cross`() {
        assertFalse(DailyGoal.crossed(before = 100.0, added = 50.0, goal = 300.0))
    }

    @Test
    fun `zero goal never crosses`() {
        assertFalse(DailyGoal.crossed(before = 0.0, added = 50.0, goal = 0.0))
    }

    @Test
    fun `progress fraction is clamped between 0 and 1`() {
        assertEquals(0.5f, DailyGoal.progressFraction(150.0, 300.0), 1e-6f)
        assertEquals(1.0f, DailyGoal.progressFraction(400.0, 300.0), 1e-6f)
        assertEquals(0.0f, DailyGoal.progressFraction(50.0, 0.0), 1e-6f)
    }
}
