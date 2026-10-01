package com.copiloto.motorista.engine

/** Pure daily-goal math (Funcionalidade 2), shared by the overlay and the history UI. */
object DailyGoal {

    /** True when adding [added] to [before] crosses [goal] for the first time. */
    fun crossed(before: Double, added: Double, goal: Double): Boolean =
        goal > 0 && before < goal && (before + added) >= goal

    /** Progress of accepted earnings toward the goal, clamped to 0..1. */
    fun progressFraction(accepted: Double, goal: Double): Float =
        if (goal > 0) (accepted / goal).coerceIn(0.0, 1.0).toFloat() else 0f
}
