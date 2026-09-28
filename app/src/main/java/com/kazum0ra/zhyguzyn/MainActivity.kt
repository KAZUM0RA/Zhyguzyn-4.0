package com.kazum0ra.zhyguzyn

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kazum0ra.zhyguzyn.ui.entry.EntryScreen
import com.kazum0ra.zhyguzyn.ui.estimate.EstimateScreen
import com.kazum0ra.zhyguzyn.ui.history.HistoryScreen
import com.kazum0ra.zhyguzyn.ui.home.HomeScreen
import com.kazum0ra.zhyguzyn.ui.settings.SettingsScreen
import com.kazum0ra.zhyguzyn.ui.stats.StatsScreen
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import com.kazum0ra.zhyguzyn.ui.theme.ZhyguzynTheme
import com.kazum0ra.zhyguzyn.ui.update.UpdateDialogHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Інтерфейс завжди темний — світлі іконки в системних панелях.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as ZhyguzynApp).container
        if (savedInstanceState == null) {
            container.updateManager.checkOnLaunch()
        }
        setContent {
            ZhyguzynTheme {
                AppNavigation()
                UpdateDialogHost(container.updateManager)
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ESTIMATE = "estimate"
    const val ENTRY = "entry?id={id}"
    fun entry(id: Long? = null) = if (id == null) "entry" else "entry?id=$id"
}

/** Вкладки нижнього меню. */
private enum class Tab(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Outlined.History),
    STATS(Routes.STATS, R.string.nav_stats, Icons.Outlined.BarChart),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
}

/** Назад, але ніколи не прибираємо головний екран (захист від подвійного натискання). */
private fun NavController.back() {
    if (currentBackStackEntry?.destination?.route != Routes.HOME) popBackStack()
}

private fun NavController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = Tab.entries.any { it.route == destination?.route }

    Scaffold(
        containerColor = AppColors.Background,
        // Системні відступи (рядок стану тощо) обробляють самі екрани; тут — лише нижнє меню.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = AppColors.CardBorder)
                    NavigationBar(containerColor = AppColors.Card) {
                        Tab.entries.forEach { tab ->
                            val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.openTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppColors.Blue,
                                    selectedTextColor = AppColors.Blue,
                                    indicatorColor = AppColors.Card,
                                    unselectedIconColor = AppColors.TextSecondary,
                                    unselectedTextColor = AppColors.TextSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { outer ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(bottom = outer.calculateBottomPadding())
                .consumeWindowInsets(outer),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddRefuel = { nav.navigate(Routes.entry()) },
                    onEstimate = { nav.navigate(Routes.ESTIMATE) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onEdit = { nav.navigate(Routes.entry(it)) })
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                Routes.ENTRY,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                EntryScreen(entryId = id, onDone = { nav.back() })
            }
            composable(Routes.ESTIMATE) {
                EstimateScreen(onBack = { nav.back() })
            }
        }
    }
}
