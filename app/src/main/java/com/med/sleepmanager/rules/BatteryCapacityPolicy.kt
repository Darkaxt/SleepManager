package com.med.sleepmanager.rules

import kotlin.math.roundToLong

enum class BatteryCapacitySource {
    LEARNED_FULL,
    DESIGN_FULL,
    COUNTER_DERIVED,
    UNAVAILABLE
}

enum class BatteryCurrentSource {
    RAW_COUNTER,
    PERCENT_DERIVED,
    UNAVAILABLE
}

data class BatteryCapacitySelection(
    val selectedFullUah: Long?,
    val displayedCurrentUah: Long?,
    val capacitySource: BatteryCapacitySource,
    val currentSource: BatteryCurrentSource,
    val learnedFullSuspect: Boolean
)

object BatteryCapacityPolicy {
    const val MAX_LEARNED_OVER_DESIGN_RATIO = 1.25
    private const val MAX_COUNTER_OVER_SELECTED_RATIO = 1.05

    fun select(
        percent: Int?,
        chargeCounterUah: Long?,
        learnedFullUah: Long?,
        designFullUah: Long?
    ): BatteryCapacitySelection {
        val normalizedPercent = percent?.takeIf { it in 0..100 }
        val counter = chargeCounterUah?.takeIf { it > 0L }
        val learned = learnedFullUah?.takeIf { it > 0L }
        val design = designFullUah?.takeIf { it > 0L }

        val learnedSuspect =
            learned != null &&
                design != null &&
                learned.toDouble() >
                    design.toDouble() * MAX_LEARNED_OVER_DESIGN_RATIO

        val selectedFull =
            when {
                learnedSuspect && design != null ->
                    design
                learned != null ->
                    learned
                design != null ->
                    design
                counter != null &&
                    normalizedPercent != null &&
                    normalizedPercent > 0 ->
                    (
                        counter.toDouble() * 100.0 /
                            normalizedPercent.toDouble()
                    ).roundToLong().takeIf { it > 0L }
                else ->
                    null
            }

        val capacitySource =
            when {
                selectedFull == null ->
                    BatteryCapacitySource.UNAVAILABLE
                learnedSuspect && design != null ->
                    BatteryCapacitySource.DESIGN_FULL
                learned != null && selectedFull == learned ->
                    BatteryCapacitySource.LEARNED_FULL
                design != null && selectedFull == design ->
                    BatteryCapacitySource.DESIGN_FULL
                else ->
                    BatteryCapacitySource.COUNTER_DERIVED
            }

        val rawCounterPlausible =
            counter != null &&
                selectedFull != null &&
                counter.toDouble() <=
                    selectedFull.toDouble() * MAX_COUNTER_OVER_SELECTED_RATIO

        val displayedCurrent =
            when {
                rawCounterPlausible ->
                    counter
                selectedFull != null && normalizedPercent != null ->
                    (
                        selectedFull.toDouble() *
                            normalizedPercent.toDouble() /
                            100.0
                    ).roundToLong()
                else ->
                    counter
            }

        val currentSource =
            when {
                displayedCurrent == null ->
                    BatteryCurrentSource.UNAVAILABLE
                rawCounterPlausible ->
                    BatteryCurrentSource.RAW_COUNTER
                selectedFull != null && normalizedPercent != null ->
                    BatteryCurrentSource.PERCENT_DERIVED
                counter != null ->
                    BatteryCurrentSource.RAW_COUNTER
                else ->
                    BatteryCurrentSource.UNAVAILABLE
            }

        return BatteryCapacitySelection(
            selectedFullUah = selectedFull,
            displayedCurrentUah = displayedCurrent,
            capacitySource = capacitySource,
            currentSource = currentSource,
            learnedFullSuspect = learnedSuspect
        )
    }
}
