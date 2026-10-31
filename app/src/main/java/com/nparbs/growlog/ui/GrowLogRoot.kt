package com.nparbs.growlog.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate

private object Routes {
    const val TODAY = "today"
    const val PLANTS = "plants"
    const val REMINDERS = "reminders"
    const val PLANT_EDIT = "plant/edit/{plantId}"
    const val PLANT_DETAIL = "plant/{plantId}"
    const val DAILY_LOG = "log/{plantId}/{date}"
    const val REMINDER_EDIT = "reminder/edit/{reminderId}?plantId={plantId}"

    fun plantEdit(plantId: Long = 0) = "plant/edit/$plantId"
    fun plantDetail(plantId: Long) = "plant/$plantId"
    fun dailyLog(plantId: Long, date: LocalDate) = "log/$plantId/$date"
    fun reminderEdit(reminderId: Long = 0, plantId: Long = 0) =
        "reminder/edit/$reminderId?plantId=$plantId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Today),
    Tab(Routes.PLANTS, "Plants", Icons.Filled.Yard),
    Tab(Routes.REMINDERS, "Reminders", Icons.Filled.Notifications),
)

@Composable
fun GrowLogRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Screens with their own top bar handle the status bar inset themselves.
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding())),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenLog = { plantId, date -> nav.navigate(Routes.dailyLog(plantId, date)) },
                    onAddPlant = { nav.navigate(Routes.plantEdit()) },
                )
            }
            composable(Routes.PLANTS) {
                PlantsScreen(
                    onOpenPlant = { nav.navigate(Routes.plantDetail(it)) },
                    onAddPlant = { nav.navigate(Routes.plantEdit()) },
                )
            }
            composable(Routes.REMINDERS) {
                RemindersScreen(
                    onAdd = { nav.navigate(Routes.reminderEdit()) },
                    onEdit = { nav.navigate(Routes.reminderEdit(reminderId = it)) },
                )
            }
            composable(
                Routes.PLANT_EDIT,
                arguments = listOf(navArgument("plantId") { type = NavType.LongType }),
            ) { entry ->
                PlantEditScreen(
                    plantId = entry.arguments!!.getLong("plantId"),
                    onBack = nav::popBackStack,
                )
            }
            composable(
                Routes.PLANT_DETAIL,
                arguments = listOf(navArgument("plantId") { type = NavType.LongType }),
            ) { entry ->
                val plantId = entry.arguments!!.getLong("plantId")
                PlantDetailScreen(
                    plantId = plantId,
                    onBack = nav::popBackStack,
                    onEdit = { nav.navigate(Routes.plantEdit(plantId)) },
                    onOpenLog = { nav.navigate(Routes.dailyLog(plantId, it)) },
                    onAddReminder = { nav.navigate(Routes.reminderEdit(plantId = plantId)) },
                    onEditReminder = { nav.navigate(Routes.reminderEdit(reminderId = it)) },
                )
            }
            composable(
                Routes.DAILY_LOG,
                arguments = listOf(
                    navArgument("plantId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType },
                ),
            ) { entry ->
                DailyLogScreen(
                    plantId = entry.arguments!!.getLong("plantId"),
                    date = LocalDate.parse(entry.arguments!!.getString("date")),
                    onBack = nav::popBackStack,
                )
            }
            composable(
                Routes.REMINDER_EDIT,
                arguments = listOf(
                    navArgument("reminderId") { type = NavType.LongType },
                    navArgument("plantId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                ReminderEditScreen(
                    reminderId = entry.arguments!!.getLong("reminderId"),
                    initialPlantId = entry.arguments!!.getLong("plantId").takeIf { it != 0L },
                    onBack = nav::popBackStack,
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
