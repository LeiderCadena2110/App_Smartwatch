package co.edu.ucc.hidrasmart.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.Manifest
import android.os.Build
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.BuildConfig
import co.edu.ucc.hidrasmart.alerts.Diagnostico
import co.edu.ucc.hidrasmart.data.Sensores
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.ui.ChipAccion
import co.edu.ucc.hidrasmart.ui.FilaNumero
import co.edu.ucc.hidrasmart.ui.Nota
import co.edu.ucc.hidrasmart.ui.Pantalla
import co.edu.ucc.hidrasmart.ui.Titulo
import co.edu.ucc.hidrasmart.ui.dec
import co.edu.ucc.hidrasmart.ui.miles
import co.edu.ucc.hidrasmart.ui.theme.Colores
import kotlin.math.roundToInt

/** Campos numéricos del perfil que se editan con − y +. */
enum class CampoPerfil(
    val titulo: String,
    val unidad: String,
    val paso: Double,
    val minimo: Double,
    val maximo: Double,
    val decimales: Int,
) {
    PESO("Peso", "kg", 0.5, 30.0, 200.0, 1),
    EDAD("Edad", "años", 1.0, 10.0, 90.0, 0),
    META("Meta diaria", "ml", 100.0, 500.0, 6000.0, 0),
    INTERVALO("Intervalo de alertas", "min", 5.0, 1.0, 60.0, 0), // 1 min solo para pruebas
    ALTITUD("Altitud habitual", "m", 50.0, 0.0, 5000.0, 0),
    TEMPERATURA("Temperatura manual", "°C", 1.0, -10.0, 45.0, 0),
    HUMEDAD("Humedad manual", "%", 5.0, 0.0, 100.0, 0);

    fun leer(p: Profile): Double = when (this) {
        PESO -> p.weightKg
        EDAD -> p.age.toDouble()
        META -> p.dailyGoalMl.toDouble()
        INTERVALO -> p.intervalMin.toDouble()
        ALTITUD -> p.usualAltitudeM
        TEMPERATURA -> p.manualTemperatureC
        HUMEDAD -> p.manualHumidityPct
    }

    fun escribir(p: Profile, v: Double): Profile = when (this) {
        PESO -> p.copy(weightKg = v)
        EDAD -> p.copy(age = v.roundToInt())
        META -> p.copy(dailyGoalMl = v.roundToInt())
        INTERVALO -> p.copy(intervalMin = v.roundToInt())
        ALTITUD -> p.copy(usualAltitudeM = v)
        TEMPERATURA -> p.copy(manualTemperatureC = v)
        HUMEDAD -> p.copy(manualHumidityPct = v)
    }

    fun texto(p: Profile): String = "${leer(p).dec(decimales)} $unidad"
}

private val CIUDADES = listOf("Pasto,CO", "Ipiales,CO", "Tumaco,CO", "Bogota,CO", "Medellin,CO", "Cali,CO")

@Composable
fun PantallaPerfil(vm: AppViewModel, editar: (CampoPerfil) -> Unit, abrirPrueba: () -> Unit) {
    val p = vm.perfil
    val contexto = LocalContext.current
    var permiso by remember { mutableStateOf(Sensores.tienePermisos(contexto)) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permiso = Sensores.tienePermisos(contexto)
    }

    // Diagnóstico de alertas: se vuelve a revisar cada vez que se regresa a esta pantalla
    var notificaciones by remember { mutableStateOf(Diagnostico.notificaciones(contexto)) }
    var exactas by remember { mutableStateOf(Diagnostico.alarmasExactas(contexto)) }
    var completa by remember { mutableStateOf(Diagnostico.pantallaCompleta(contexto)) }
    fun revisar() {
        permiso = Sensores.tienePermisos(contexto)
        notificaciones = Diagnostico.notificaciones(contexto)
        exactas = Diagnostico.alarmasExactas(contexto)
        completa = Diagnostico.pantallaCompleta(contexto)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revisar() }
    var yaPidioNotificaciones by rememberSaveable { mutableStateOf(false) }
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revisar()
        // Si el sistema ya no muestra el diálogo, se abren los ajustes de la app
        if (!notificaciones) Diagnostico.abrirAjustesNotificaciones(contexto)
    }

    Pantalla {
        item { Titulo("Perfil y ajustes") }
        if (!(notificaciones && exactas && completa)) {
            item { Nota("⚠ Las alertas no saldrán con la app cerrada: baja a \"Diagnóstico de alertas\".", Colores.Ambar) }
        }
        item {
            ChipAccion(
                "Prueba de sudoración", Colores.Agua, abrirPrueba,
                detalle = p.sweatRateLh?.let { "${it.dec(2)} L/h" } ?: "Sin hacer",
            )
        }
        listOf(CampoPerfil.PESO, CampoPerfil.EDAD, CampoPerfil.META, CampoPerfil.INTERVALO).forEach { campo ->
            item { ChipAccion(campo.titulo, Colores.GrisOscuro, { editar(campo) }, detalle = campo.texto(p)) }
        }
        item {
            ChipAccion("Ciudad del clima", Colores.GrisOscuro, {
                val siguiente = CIUDADES[(CIUDADES.indexOf(p.city) + 1) % CIUDADES.size]
                vm.guardarPerfil(p.copy(city = siguiente))
                vm.actualizarClima()
            }, detalle = p.city.substringBefore(","))
        }
        val clima = vm.clima
        item {
            Nota(
                if (clima != null) "Clima: ${clima.temperatureC.dec()} °C · ${clima.humidityPct.dec(0)} %"
                else vm.mensajeClima ?: "Clima sin consultar",
            )
        }
        listOf(CampoPerfil.ALTITUD, CampoPerfil.TEMPERATURA, CampoPerfil.HUMEDAD).forEach { campo ->
            item { ChipAccion(campo.titulo, Colores.GrisOscuro, { editar(campo) }, detalle = campo.texto(p)) }
        }
        item {
            ChipAccion(
                "Permisos (sensores y alertas)", if (permiso) Colores.Verde else Colores.Ambar,
                { if (!permiso) pedirPermiso.launch(Sensores.permisosEntrenamiento()) },
                detalle = if (permiso) "Concedido" else "Toca para permitir",
            )
        }
        item { Nota("Altitud, temperatura y humedad manuales se usan cuando no hay barómetro o internet.") }

        item { Titulo("Diagnóstico de alertas", "Para que salgan con la app cerrada") }
        item {
            ChipAccion(
                "Notificaciones", if (notificaciones) Colores.Verde else Colores.Rojo,
                {
                    if (!notificaciones) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !yaPidioNotificaciones) {
                            yaPidioNotificaciones = true
                            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Diagnostico.abrirAjustesNotificaciones(contexto)
                        }
                    }
                },
                detalle = if (notificaciones) "Activadas" else "BLOQUEADAS · toca para activar",
            )
        }
        item {
            ChipAccion(
                "Alarmas exactas", if (exactas) Colores.Verde else Colores.Rojo,
                { if (!exactas) Diagnostico.abrirAjustesAlarmas(contexto) },
                detalle = if (exactas) "Permitidas" else "Toca para permitir",
            )
        }
        item {
            ChipAccion(
                "Pantalla completa", if (completa) Colores.Verde else Colores.Rojo,
                { if (!completa) Diagnostico.abrirAjustesPantallaCompleta(contexto) },
                detalle = if (completa) "Permitida" else "Toca para permitir",
            )
        }
        item { Nota("Versión ${BuildConfig.VERSION_NAME}") }
    }
}

@Composable
fun PantallaEditarCampo(vm: AppViewModel, campo: CampoPerfil, listo: () -> Unit) {
    var value by rememberSaveable { mutableDoubleStateOf(campo.leer(vm.perfil)) }
    fun ajustar(delta: Double) {
        val nuevo = ((value + delta) * 10).roundToInt() / 10.0
        value = nuevo.coerceIn(campo.minimo, campo.maximo)
    }
    Pantalla {
        item { Titulo(campo.titulo) }
        item { FilaNumero(campo.unidad, value.dec(campo.decimales), { ajustar(-campo.paso) }, { ajustar(campo.paso) }) }
        item {
            ChipAccion("Guardar", Colores.Verde, {
                vm.guardarPerfil(campo.escribir(vm.perfil, value))
                listo()
            })
        }
    }
}

/**
 * Prueba de sudoración (NATA 2017): pesarse antes y después de entrenar 1 hora,
 * anotar lo que se tomó y la orina. El reloj no pesa: los datos se ingresan aquí.
 */
@Composable
fun PantallaPrueba(vm: AppViewModel, listo: () -> Unit) {
    var pesoInicial by rememberSaveable { mutableDoubleStateOf(vm.perfil.weightKg) }
    var pesoFinal by rememberSaveable { mutableDoubleStateOf(vm.perfil.weightKg - 0.5) }
    var liquido by rememberSaveable { mutableIntStateOf(0) }
    var orina by rememberSaveable { mutableIntStateOf(0) }
    var minutos by rememberSaveable { mutableIntStateOf(60) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var guardada by remember { mutableStateOf(false) }

    fun paso(v: Double, d: Double) = (((v + d) * 10).roundToInt() / 10.0).coerceIn(30.0, 200.0)

    Pantalla {
        item { Titulo("Prueba de sudoración", "Pésate antes y después de entrenar") }
        item { FilaNumero("Peso inicial (kg)", pesoInicial.dec(1), { pesoInicial = paso(pesoInicial, -0.1) }, { pesoInicial = paso(pesoInicial, 0.1) }) }
        item { FilaNumero("Peso final (kg)", pesoFinal.dec(1), { pesoFinal = paso(pesoFinal, -0.1) }, { pesoFinal = paso(pesoFinal, 0.1) }) }
        item { FilaNumero("Líquido tomado (ml)", liquido.miles(), { liquido = (liquido - 50).coerceAtLeast(0) }, { liquido += 50 }) }
        item { FilaNumero("Orina (ml)", orina.miles(), { orina = (orina - 50).coerceAtLeast(0) }, { orina += 50 }) }
        item { FilaNumero("Duración (min)", "$minutos", { minutos = (minutos - 5).coerceAtLeast(5) }, { minutos = (minutos + 5).coerceAtMost(240) }) }

        val mensajeActual = mensaje
        if (mensajeActual != null) {
            item { Nota(mensajeActual, if (guardada) Colores.AguaClara else Colores.Ambar) }
        }
        item {
            if (!guardada) {
                ChipAccion("Calcular y guardar", Colores.Verde, {
                    val sweatTest = SweatTest(pesoInicial, pesoFinal, liquido, orina, minutos)
                    vm.guardarPrueba(sweatTest)
                        .onSuccess { rate ->
                            val perdida = HydrationCalculator.weightLossPercent(sweatTest)
                            guardada = true
                            mensaje = "Tasa: ${rate.dec(2)} L/h. Perdiste ${perdida.dec(1)} % del peso" +
                                if (perdida >= 2.0) " (NATA recomienda menos del 2 %)" else ""
                        }
                        .onFailure { mensaje = it.message ?: "Revisa los datos" }
                })
            } else {
                ChipAccion("Listo", Colores.Verde, listo)
            }
        }
    }
}
