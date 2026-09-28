package com.kazum0ra.zhyguzyn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kazum0ra.zhyguzyn.ui.entry.EntryScreen
import com.kazum0ra.zhyguzyn.ui.estimate.EstimateScreen
import com.kazum0ra.zhyguzyn.ui.history.HistoryScreen
import com.kazum0ra.zhyguzyn.ui.home.HomeScreen
import com.kazum0ra.zhyguzyn.ui.settings.SettingsScreen
import com.kazum0ra.zhyguzyn.ui.theme.ZhyguzynTheme
import com.kazum0ra.zhyguzyn.ui.update.UpdateDialogHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    const val SETTINGS = "settings"
    const val ESTIMATE = "estimate"
    const val ENTRY = "entry?id={id}"
    fun entry(id: Long? = null) = if (id == null) "entry" else "entry?id=$id"
}

/** Назад, але ніколи не прибираємо головний екран (захист від подвійного натискання). */
private fun NavController.back() {
    if (currentBackStackEntry?.destination?.route != Routes.HOME) popBackStack()
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddRefuel = { nav.navigate(Routes.entry()) },
                onEstimate = { nav.navigate(Routes.ESTIMATE) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.ENTRY,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            EntryScreen(entryId = id, onDone = { nav.back() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.back() }, onEdit = { nav.navigate(Routes.entry(it)) })
        }
        composable(Routes.ESTIMATE) {
            EstimateScreen(onBack = { nav.back() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.back() })
        }
    }
}
