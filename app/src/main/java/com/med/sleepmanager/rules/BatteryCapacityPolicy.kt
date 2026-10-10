package com.med.sleepmanager.rules

import kotlin.math.abs
import kotlin.math.roundToLong

enum class BatteryCapacitySource {
    LEARNED_FULL,
    DESIGN_FULL,
    COUNTER_DERIVED,
    UNAVAILABLE
}

enum class BatteryCurrentSource {
    RAW_COUNTER,
    NORMALIZED_COUNTER,
    PERCENT_DERIVED,
    UNAVAILABLE
}

data class BatteryCapacitySelection(
    val selectedFullUah: Long?,
    val displayedCurrentUah: Long?,
    val capacitySource: BatteryCapacitySource,
    val currentSource: BatteryCurrentSource,
    val learnedFullSuspect: Boolean,
    val effectiveDesignUah: Long?,
    val designScaleAdjusted: Boolean
)

object BatteryCapacityPolicy {
    const val MAX_LEARNED_OVER_DESIGN_RATIO = 1.25
    private const val MAX_COUNTER_OVER_SELECTED_RATIO = 1.05
    private const val MAX_NORMALIZED_COUNTER_PERCENT_DELTA = 5.0

    // A decimal-scale correction is only considered when the raw design value
    // is itself implausibly small for this class of handheld while the ×10
    // interpretation is coherent with learned capacity, counter and Android %.
    // This deliberately avoids a blind "ratio ~= 10 => multiply by 10" rule.
    private const val MIN_PLAUSIBLE_HANDHELD_CAPACITY_UAH = 2_000_000L
    private const val MAX_PLAUSIBLE_HANDHELD_CAPACITY_UAH = 10_000_000L
    private const val MAX_RAW_DESIGN_FOR_X10_UAH = 1_000_000L
    private const val DESIGN_X10_FACTOR = 10L
    private const val MIN_LEARNED_TO_EFFECTIVE_DESIGN_RATIO = 0.70
    private const val MAX_DESIGN_SCALE_PERCENT_DELTA = 5.0

    fun isLearnedFullSuspect(
        learnedFullUah: Long?,
        designFullUah: Long?
    ): Boolean {
        val learned = learnedFullUah?.takeIf { it > 0L } ?: return false
        val design = designFullUah?.takeIf { it > 0L } ?: return false
        return learned.toDouble() >
            design.toDouble() * MAX_LEARNED_OVER_DESIGN_RATIO
    }

    fun rawBatteryHealthPercent(
        learnedFullUah: Long?,
        designFullUah: Long?
    ): Double? {
        val learned = learnedFullUah?.takeIf { it > 0L } ?: return null
        val design = designFullUah?.takeIf { it > 0L } ?: return null
        if (isLearnedFullSuspect(learned, design)) return null

        return (learned.toDouble() / design.toDouble() * 100.0)
            .takeIf { it.isFinite() && it > 0.0 }
    }

    fun batteryHealthPercent(
        learnedFullUah: Long?,
        designFullUah: Long?
    ): Double? =
        rawBatteryHealthPercent(
            learnedFullUah = learnedFullUah,
            designFullUah = designFullUah
        )?.coerceAtMost(100.0)

    fun select(
        percent: Int?,
        chargeCounterUah: Long?,
        learnedFullUah: Long?,
        designFullUah: Long?
    ): BatteryCapacitySelection {
        val normalizedPercent = percent?.takeIf { it in 0..100 }
        val counter = chargeCounterUah?.takeIf { it > 0L }
        val learned = learnedFullUah?.takeIf { it > 0L }
        val rawDesign = designFullUah?.takeIf { it > 0L }

        val designNormalization =
            normalizeDesignCapacity(
                percent = normalizedPercent,
                counter = counter,
                learned = learned,
                rawDesign = rawDesign
            )
        val design = designNormalization.first
        val designScaleAdjusted = designNormalization.second

        val learnedSuspect =
            isLearnedFullSuspect(
                learnedFullUah = learned,
                designFullUah = design
            )

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

        // Some Thor battery gauges can report an implausibly high charge_full
        // while charge_counter still tracks that same inflated scale accurately.
        // In that specific case, rescale the counter onto the trusted design
        // capacity instead of falling back to Android's integer percentage.
        val normalizedCounter =
            if (
                !rawCounterPlausible &&
                learnedSuspect &&
                counter != null &&
                learned != null &&
                design != null &&
                counter.toDouble() <=
                    learned.toDouble() * MAX_COUNTER_OVER_SELECTED_RATIO
            ) {
                val scaled =
                    (
                        counter.toDouble() *
                            design.toDouble() /
                            learned.toDouble()
                    ).roundToLong()
                val scaledPercent =
                    scaled.toDouble() / design.toDouble() * 100.0
                scaled.takeIf {
                    it > 0L &&
                        it.toDouble() <=
                            design.toDouble() * MAX_COUNTER_OVER_SELECTED_RATIO &&
                        (
                            normalizedPercent == null ||
                                abs(
                                    scaledPercent -
                                        normalizedPercent.toDouble()
                                ) <= MAX_NORMALIZED_COUNTER_PERCENT_DELTA
                        )
                }
            } else {
                null
            }

        val displayedCurrent =
            when {
                rawCounterPlausible ->
                    counter
                normalizedCounter != null ->
                    normalizedCounter
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
                normalizedCounter != null ->
                    BatteryCurrentSource.NORMALIZED_COUNTER
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
            learnedFullSuspect = learnedSuspect,
            effectiveDesignUah = design,
            designScaleAdjusted = designScaleAdjusted
        )
    }

    private fun normalizeDesignCapacity(
        percent: Int?,
        counter: Long?,
        learned: Long?,
        rawDesign: Long?
    ): Pair<Long?, Boolean> {
        val design = rawDesign ?: return null to false
        if (design >= MAX_RAW_DESIGN_FOR_X10_UAH) {
            return design to false
        }

        val scaled =
            runCatching { Math.multiplyExact(design, DESIGN_X10_FACTOR) }
                .getOrNull()
                ?: return design to false
        if (
            scaled !in
            MIN_PLAUSIBLE_HANDHELD_CAPACITY_UAH..
                MAX_PLAUSIBLE_HANDHELD_CAPACITY_UAH
        ) {
            return design to false
        }

        val learnedValue = learned ?: return design to false
        if (
            learnedValue !in
            MIN_PLAUSIBLE_HANDHELD_CAPACITY_UAH..
                MAX_PLAUSIBLE_HANDHELD_CAPACITY_UAH
        ) {
            return design to false
        }

        val learnedRatio =
            learnedValue.toDouble() / scaled.toDouble()
        if (
            learnedRatio < MIN_LEARNED_TO_EFFECTIVE_DESIGN_RATIO ||
            learnedRatio > MAX_LEARNED_OVER_DESIGN_RATIO
        ) {
            return design to false
        }

        if (counter != null) {
            if (
                counter.toDouble() >
                scaled.toDouble() * MAX_COUNTER_OVER_SELECTED_RATIO
            ) {
                return design to false
            }
            if (percent != null && percent > 0) {
                val counterPercent =
                    counter.toDouble() / scaled.toDouble() * 100.0
                if (
                    abs(counterPercent - percent.toDouble()) >
                    MAX_DESIGN_SCALE_PERCENT_DELTA
                ) {
                    return design to false
                }
            }
        } else if (percent == null) {
            return design to false
        }

        return scaled to true
    }
}
