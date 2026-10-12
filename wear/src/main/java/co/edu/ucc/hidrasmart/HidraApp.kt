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
import co.edu.ucc.hidrasmart.ui.pantallas.CampoPerfil
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaAlerta
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaConfirmacion
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaConfirmarFin
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaDetalleDia
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaDetalleSesion
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaEditarCampo
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaElegirTipo
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaHistorial
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaInicio
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaOtraCantidad
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaPerfil
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaPrueba
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaRegistrar
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaResumen
import co.edu.ucc.hidrasmart.ui.pantallas.PantallaSesion
import co.edu.ucc.hidrasmart.ui.pantallas.Rutas
import co.edu.ucc.hidrasmart.ui.tema.HidraTema

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
            if (vm.alerta != null && nav.currentDestination?.route != Rutas.ALERTA) {
                nav.navigate(Rutas.ALERTA)
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
                    abrirAlerta = { nav.navigate(Rutas.ALERTA) },
                )
            }
            composable(Rutas.ALERTA) {
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
            composable(Rutas.PERFIL) {
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
