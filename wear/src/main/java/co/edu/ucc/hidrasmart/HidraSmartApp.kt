package co.edu.ucc.hidrasmart

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import co.edu.ucc.hidrasmart.ui.screens.ProfileField
import co.edu.ucc.hidrasmart.ui.screens.AlertScreen
import co.edu.ucc.hidrasmart.ui.screens.ConfirmationScreen
import co.edu.ucc.hidrasmart.ui.screens.ConfirmEndScreen
import co.edu.ucc.hidrasmart.ui.screens.DayDetailScreen
import co.edu.ucc.hidrasmart.ui.screens.SessionDetailScreen
import co.edu.ucc.hidrasmart.ui.screens.EditFieldScreen
import co.edu.ucc.hidrasmart.ui.screens.WorkoutTypeScreen
import co.edu.ucc.hidrasmart.ui.screens.HistoryScreen
import co.edu.ucc.hidrasmart.ui.screens.HomeScreen
import co.edu.ucc.hidrasmart.ui.screens.CustomAmountScreen
import co.edu.ucc.hidrasmart.ui.screens.ProfileScreen
import co.edu.ucc.hidrasmart.ui.screens.SweatTestScreen
import co.edu.ucc.hidrasmart.ui.screens.LogIntakeScreen
import co.edu.ucc.hidrasmart.ui.screens.SummaryScreen
import co.edu.ucc.hidrasmart.ui.screens.SessionScreen
import co.edu.ucc.hidrasmart.ui.screens.Routes
import co.edu.ucc.hidrasmart.ui.theme.HidraSmartTheme

/**
 * Navegación de la app: una pila con SwipeDismissableNavHost.
 * Deslizar a la derecha vuelve atrás; salir de la sesión con el gesto NO la termina.
 */
@Composable
fun HidraSmartApp(vm: AppViewModel = viewModel()) {
    HidraSmartTheme {
        val nav = rememberSwipeDismissableNavController()
        val context = LocalContext.current
        val inSession = vm.session != null

        // Mantener la pantalla encendida mientras se entrena
        DisposableEffect(inSession) {
            val window = (context as? Activity)?.window
            if (inSession) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }

        // Cuando llega la hora de tomar agua, abrir la alerta desde cualquier pantalla
        LaunchedEffect(vm.alert) {
            if (vm.alert != null && nav.currentDestination?.route != Routes.ALERT) {
                nav.navigate(Routes.ALERT)
            }
        }

        // Al registrar una toma se muestra la confirmación y se vuelve a quien abrió "Registrar"
        val onLogged: (Int) -> Unit = { ml -> nav.navigate(Routes.confirmation(ml)) }

        SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(vm) { route -> nav.navigate(route) }
            }
            composable(Routes.LOG_INTAKE) {
                LogIntakeScreen(vm, onLogged = onLogged, onCustomAmount = { nav.navigate(Routes.CUSTOM_AMOUNT) })
            }
            composable(Routes.CUSTOM_AMOUNT) {
                CustomAmountScreen(vm, onLogged = onLogged)
            }
            composable(
                Routes.CONFIRMATION,
                arguments = listOf(navArgument("ml") { type = NavType.IntType }),
            ) { entry ->
                ConfirmationScreen(ml = entry.arguments?.getInt("ml") ?: 0) {
                    nav.popBackStack(Routes.LOG_INTAKE, inclusive = true)
                }
            }
            composable(Routes.WORKOUT_TYPE) {
                WorkoutTypeScreen(vm) {
                    nav.navigate(Routes.SESSION) { popUpTo(Routes.WORKOUT_TYPE) { inclusive = true } }
                }
            }
            composable(Routes.SESSION) {
                SessionScreen(
                    vm,
                    onLogIntake = { nav.navigate(Routes.LOG_INTAKE) },
                    end = { nav.navigate(Routes.CONFIRM_END) },
                    onExit = { nav.popBackStack(Routes.HOME, inclusive = false) },
                    onOpenAlert = { nav.navigate(Routes.ALERT) },
                )
            }
            composable(Routes.ALERT) {
                AlertScreen(vm) { nav.popBackStack() }
            }
            composable(Routes.CONFIRM_END) {
                ConfirmEndScreen(
                    end = {
                        val id = vm.endSession()
                        if (id != null) {
                            nav.navigate(Routes.summary(id)) { popUpTo(Routes.HOME) }
                        } else {
                            nav.popBackStack(Routes.HOME, inclusive = false)
                        }
                    },
                    cancel = { nav.popBackStack() },
                )
            }
            composable(
                Routes.SUMMARY,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                SummaryScreen(vm, id = entry.arguments?.getLong("id") ?: 0L) {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                }
            }
            composable(Routes.HISTORY) {
                HistoryScreen(vm) { date -> nav.navigate(Routes.day(date)) }
            }
            composable(Routes.DAY) { entry ->
                DayDetailScreen(vm, date = entry.arguments?.getString("fecha") ?: "") { id ->
                    nav.navigate(Routes.sessionDetail(id))
                }
            }
            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                SessionDetailScreen(vm, id = entry.arguments?.getLong("id") ?: 0L)
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    vm,
                    editField = { field -> nav.navigate(Routes.editField(field)) },
                    onOpenSweatTest = { nav.navigate(Routes.SWEAT_TEST) },
                )
            }
            composable(Routes.EDIT_FIELD) { entry ->
                val field = runCatching { ProfileField.valueOf(entry.arguments?.getString("campo") ?: "") }
                    .getOrDefault(ProfileField.WEIGHT)
                EditFieldScreen(vm, field) { nav.popBackStack() }
            }
            composable(Routes.SWEAT_TEST) {
                SweatTestScreen(vm) { nav.popBackStack() }
            }
        }
    }
}
