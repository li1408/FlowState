package com.markel.flowstate.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates startup and primary navigation rules for the production app.
 *
 * Run this test through the app producer task on a connected physical device:
 * `./gradlew :app:generateBaselineProfile`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        resetBenchmarkFixturesOnDevice()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun generatePrimaryJourneys() {
        resetBenchmarkFixturesOnDevice()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
            includeInStartupProfile = false,
        ) {
            // BaselineProfileRule kills the target at the beginning of every
            // collection iteration. Relaunch here; fixture setup intentionally
            // remains outside collection so it is not recorded in the profile.
            pressHome()
            startActivityAndWait()
            tapTag(BenchmarkTags.FLOW_TAB)
            tapTag(BenchmarkTags.CALENDAR_TAB)
            waitForTag(BenchmarkTags.CALENDAR_LIST)
            tapTag(BenchmarkTags.HABITS_TAB)
            waitForTag(BenchmarkTags.HABITS_LIST)
            flingTag(BenchmarkTags.HABITS_LIST)
            tapEnabledTag(
                tag = BenchmarkTags.HABIT_BOOLEAN_TOGGLE,
                fixtureRequirement = "at least one Boolean habit scheduled for today",
            )
            tapEnabledTag(
                tag = BenchmarkTags.HABIT_BOOLEAN_TOGGLE,
                fixtureRequirement = "at least one Boolean habit scheduled for today",
            )
            val incrementCenter = tapEnabledTagRapidly(
                tag = BenchmarkTags.HABIT_NUMERIC_INCREMENT,
                count = 10,
                fixtureRequirement = "at least one numeric habit scheduled for today",
            )
            tapEnabledTagRapidly(
                tag = BenchmarkTags.HABIT_NUMERIC_DECREMENT,
                count = 10,
                fixtureRequirement = "the numeric habit must allow decrementing the ten test increments",
                preferredY = incrementCenter.y,
            )
            longPressDragFirstToLast(
                tag = BenchmarkTags.HABIT_REORDER_HANDLE,
                listTag = BenchmarkTags.HABITS_LIST,
            )
            longPressDragLastToFirst(
                tag = BenchmarkTags.HABIT_REORDER_HANDLE,
                listTag = BenchmarkTags.HABITS_LIST,
            )
            tapTag(BenchmarkTags.FLOW_TAB)
        }
    }
}
