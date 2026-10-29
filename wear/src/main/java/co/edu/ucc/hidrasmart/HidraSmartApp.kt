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
import co.edu.ucc.hidrasmart.ui.screens.CampoPerfil
import co.edu.ucc.hidrasmart.ui.screens.PantallaAlerta
import co.edu.ucc.hidrasmart.ui.screens.PantallaConfirmacion
import co.edu.ucc.hidrasmart.ui.screens.PantallaConfirmarFin
import co.edu.ucc.hidrasmart.ui.screens.PantallaDetalleDia
import co.edu.ucc.hidrasmart.ui.screens.PantallaDetalleSesion
import co.edu.ucc.hidrasmart.ui.screens.PantallaEditarCampo
import co.edu.ucc.hidrasmart.ui.screens.PantallaElegirTipo
import co.edu.ucc.hidrasmart.ui.screens.PantallaHistorial
import co.edu.ucc.hidrasmart.ui.screens.PantallaInicio
import co.edu.ucc.hidrasmart.ui.screens.PantallaOtraCantidad
import co.edu.ucc.hidrasmart.ui.screens.PantallaPerfil
import co.edu.ucc.hidrasmart.ui.screens.PantallaPrueba
import co.edu.ucc.hidrasmart.ui.screens.PantallaRegistrar
import co.edu.ucc.hidrasmart.ui.screens.PantallaResumen
import co.edu.ucc.hidrasmart.ui.screens.PantallaSesion
import co.edu.ucc.hidrasmart.ui.screens.Rutas
import co.edu.ucc.hidrasmart.ui.theme.HidraTema

/**
 * Navegación de la app: una pila con SwipeDismissableNavHost.
 * Deslizar a la derecha vuelve atrás; salir de la sesión con el gesto NO la termina.
 */
@Composable
fun HidraApp(vm: AppViewModel = viewModel()) {
    HidraTema {
        val nav = rememberSwipeDismissableNavController()
        val contexto = LocalContext.current
        val enSesion = vm.sesion != null

        // Mantener la pantalla encendida mientras se entrena
        DisposableEffect(enSesion) {
            val ventana = (contexto as? Activity)?.window
            if (enSesion) ventana?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else ventana?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }

        // Cuando llega la hora de tomar agua, abrir la alerta desde cualquier pantalla
        LaunchedEffect(vm.alerta) {
            if (vm.alerta != null && nav.currentDestination?.route != Rutas.ALERT) {
                nav.navigate(Rutas.ALERT)
            }
        }

        // Al registrar una toma se muestra la confirmación y se vuelve a quien abrió "Registrar"
        val alRegistrar: (Int) -> Unit = { ml -> nav.navigate(Rutas.confirmacion(ml)) }

        SwipeDismissableNavHost(navController = nav, startDestination = Rutas.INICIO) {
            composable(Rutas.INICIO) {
                PantallaInicio(vm) { ruta -> nav.navigate(ruta) }
            }
            composable(Rutas.REGISTRAR) {
                PantallaRegistrar(vm, registrado = alRegistrar, otraCantidad = { nav.navigate(Rutas.OTRA_CANTIDAD) })
            }
            composable(Rutas.OTRA_CANTIDAD) {
                PantallaOtraCantidad(vm, registrado = alRegistrar)
            }
            composable(
                Rutas.CONFIRMACION,
                arguments = listOf(navArgument("ml") { type = NavType.IntType }),
            ) { entrada ->
                PantallaConfirmacion(ml = entrada.arguments?.getInt("ml") ?: 0) {
                    nav.popBackStack(Rutas.REGISTRAR, inclusive = true)
                }
            }
            composable(Rutas.TIPO) {
                PantallaElegirTipo(vm) {
                    nav.navigate(Rutas.SESION) { popUpTo(Rutas.TIPO) { inclusive = true } }
                }
            }
            composable(Rutas.SESION) {
                PantallaSesion(
                    vm,
                    registrar = { nav.navigate(Rutas.REGISTRAR) },
                    terminar = { nav.navigate(Rutas.CONFIRMAR_FIN) },
                    salir = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
                    abrirAlerta = { nav.navigate(Rutas.ALERT) },
                )
            }
            composable(Rutas.ALERT) {
                PantallaAlerta(vm) { nav.popBackStack() }
            }
            composable(Rutas.CONFIRMAR_FIN) {
                PantallaConfirmarFin(
                    terminar = {
                        val id = vm.terminarSesion()
                        if (id != null) {
                            nav.navigate(Rutas.resumen(id)) { popUpTo(Rutas.INICIO) }
                        } else {
                            nav.popBackStack(Rutas.INICIO, inclusive = false)
                        }
                    },
                    cancelar = { nav.popBackStack() },
                )
            }
            composable(
                Rutas.RESUMEN,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entrada ->
                PantallaResumen(vm, id = entrada.arguments?.getLong("id") ?: 0L) {
                    nav.popBackStack(Rutas.INICIO, inclusive = false)
                }
            }
            composable(Rutas.HISTORIAL) {
                PantallaHistorial(vm) { fecha -> nav.navigate(Rutas.dia(fecha)) }
            }
            composable(Rutas.DIA) { entrada ->
                PantallaDetalleDia(vm, fecha = entrada.arguments?.getString("fecha") ?: "") { id ->
                    nav.navigate(Rutas.detalleSesion(id))
                }
            }
            composable(
                Rutas.DETALLE_SESION,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entrada ->
                PantallaDetalleSesion(vm, id = entrada.arguments?.getLong("id") ?: 0L)
            }
            composable(Rutas.PROFILE) {
                PantallaPerfil(
                    vm,
                    editar = { campo -> nav.navigate(Rutas.editar(campo)) },
                    abrirPrueba = { nav.navigate(Rutas.PRUEBA) },
                )
            }
            composable(Rutas.EDITAR) { entrada ->
                val campo = runCatching { CampoPerfil.valueOf(entrada.arguments?.getString("campo") ?: "") }
                    .getOrDefault(CampoPerfil.PESO)
                PantallaEditarCampo(vm, campo) { nav.popBackStack() }
            }
            composable(Rutas.PRUEBA) {
                PantallaPrueba(vm) { nav.popBackStack() }
            }
        }
    }
}
