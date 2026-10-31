package com.nparbs.growlog.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nparbs.growlog.GrowLogApp
import com.nparbs.growlog.MainActivity
import com.nparbs.growlog.data.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val repository
        get() = (compose.activity.application as GrowLogApp).container.repository

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForGone(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Bottom nav tabs share labels with screen titles, so match the clickable one. */
    private fun openTab(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun typeInto(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun addPlant(name: String) {
        waitForText("Add a plant")
        compose.onNodeWithText("Add a plant").performClick()
        waitForText("New plant")
        typeInto("Name", name)
        typeInto("Variety / strain", "San Marzano")
        typeInto("Location (tent, room, shelf)", "Tent A")
        compose.onNodeWithText("Vegetative").performClick()
        shot("02_new_plant")
        compose.onNodeWithText("Add plant").performScrollTo().performClick()
        waitForGone("New plant")
        waitForText(name)
    }

    @Test
    fun addPlantThenLogADayEndToEnd() {
        waitForText("No plants yet")
        shot("01_today_empty")
        addPlant("Tomato 1")

        compose.onNodeWithText("Tomato 1").performClick()
        waitForText("Watered")
        typeInto("Amount", "750")
        typeInto("pH", "6.2")
        typeInto("PPM", "850")
        typeInto("Runoff pH", "6,4")
        compose.onNodeWithText("Add fertilizer").performScrollTo().performClick()
        typeInto("Fertilizer", "CalMag")
        typeInto("Temperature", "24")
        typeInto("Humidity", "55")
        typeInto("Observations, training, problems", "New growth on top")
        shot("03_daily_log")
        compose.onNodeWithText("Save").performClick()

        waitForText("750 ml")
        compose.onNodeWithText("pH 6.2").assertIsDisplayed()
        compose.onNodeWithText("850 ppm").assertIsDisplayed()
        shot("04_today_logged")

        val plantId = runBlocking {
            val id = 1L
            val log = repository.getLog(id, LocalDate.now())!!
            assertEquals(true, log.watered)
            assertEquals(750, log.waterMl)
            assertEquals(6.2, log.ph)
            assertEquals(6.4, log.runoffPh)
            assertEquals(850, log.ppm)
            assertEquals(55, log.humidityPct)
            assertEquals(listOf("CalMag"), repository.getDoses(id, LocalDate.now()).map { it.name })
            id
        }
        assertEquals(1L, plantId)

        openTab("Plants")
        waitForText("San Marzano")
        compose.onNodeWithText("San Marzano").performClick()
        waitForText("History")
        waitForText("New growth on top")
        shot("05_plant_history")

        compose.onNodeWithText("Photos").performClick()
        waitForText("Camera")
        shot("06_plant_photos")

        compose.onNodeWithText("Reminders").performClick()
        waitForText("Add reminder")
    }

    @Test
    fun invalidPhBlocksSavingAndShowsError() {
        waitForText("No plants yet")
        addPlant("Basil")
        compose.onNodeWithText("Basil").performClick()
        waitForText("Watered")
        typeInto("pH", "20")
        compose.onNodeWithText("Save").performClick()
        waitForText("pH is 0 to 14")
        assertEquals(null, runBlocking { repository.getLog(1, LocalDate.now()) })
    }

    @Test
    fun quickWaterFromToday() {
        waitForText("No plants yet")
        addPlant("Fern")
        compose.onNodeWithContentDescription("Mark Fern watered").performClick()
        waitForText("Watered")
        compose.onNode(hasContentDescription("Undo watered Fern")).assertIsDisplayed()
    }

    @Test
    fun createReminderThenCompleteItFromToday() {
        waitForText("No plants yet")
        addPlant("Mint")

        openTab("Reminders")
        waitForText("No reminders yet")
        compose.onNodeWithContentDescription("Add reminder").performClick()
        waitForText("New reminder")
        typeInto("Title", "Water the mint")
        compose.onNodeWithText("Weekly").performClick()
        shot("07_new_reminder")
        compose.onNodeWithText("Save").performClick()
        waitForGone("New reminder")
        waitForText("Water the mint")
        shot("08_reminders")

        runBlocking {
            val saved = repository.getReminder(1)!!
            repository.saveReminder(saved.copy(nextDue = LocalDateTime.now().minusHours(1)))
        }
        openTab("Today")
        waitForText("Due now")
        shot("09_today_due")
        compose.onNodeWithText("Done").performClick()
        waitForText("Watered")
        assertEquals(true, runBlocking { repository.getLog(1, LocalDate.now()) }?.watered)
    }

    @Test
    fun scheduleErrorsAreShown() {
        waitForText("No plants yet")
        openTab("Reminders")
        waitForText("No reminders yet")
        compose.onNodeWithContentDescription("Add reminder").performClick()
        waitForText("New reminder")
        compose.onNodeWithText("Weekly").performClick()
        // Today's weekday starts selected; turning it off leaves no days.
        val todayLabel = LocalDate.now().dayOfWeek
            .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        compose.onNodeWithText(todayLabel).performClick()
        compose.onNodeWithText("Save").performClick()
        waitForText("Pick at least one day")
        assertEquals(null, runBlocking { repository.getReminder(1) } as Reminder?)
    }
}
