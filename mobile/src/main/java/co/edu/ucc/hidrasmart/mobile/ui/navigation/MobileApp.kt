package co.edu.ucc.hidrasmart.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.edu.ucc.hidrasmart.mobile.MobileViewModel
import co.edu.ucc.hidrasmart.mobile.ui.screens.HomeScreen
import co.edu.ucc.hidrasmart.mobile.ui.theme.HidraSmartMobileTheme

/** Pestañas de la barra inferior. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Inicio", Icons.Default.Home),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(vm: MobileViewModel = viewModel()) {
    HidraSmartMobileTheme {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val current = Tab.entries.firstOrNull { it.route == entry?.destination?.route } ?: Tab.HOME

        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text(if (current == Tab.HOME) "HidraSmart" else current.label) }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = { nav.goTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
                composable(Tab.HOME.route) { HomeScreen(vm, onOpenCoach = { nav.goTo(Tab.HOME) }) }
            }
        }
    }
}

/** Cambia de pestaña sin apilar pantallas y conservando el estado de cada una. */
private fun NavHostController.goTo(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
