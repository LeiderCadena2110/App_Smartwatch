package co.edu.ucc.hidrasmart.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.alerts.Diagnostico
import co.edu.ucc.hidrasmart.data.Sensores
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.ui.BotonRedondo
import co.edu.ucc.hidrasmart.ui.ChipAccion
import co.edu.ucc.hidrasmart.ui.Nota
import co.edu.ucc.hidrasmart.ui.Pantalla
import co.edu.ucc.hidrasmart.ui.Titulo
import co.edu.ucc.hidrasmart.ui.dec
import co.edu.ucc.hidrasmart.ui.duracion
import co.edu.ucc.hidrasmart.ui.miles
import co.edu.ucc.hidrasmart.ui.theme.Colores

private val TIPOS = listOf("Correr", "Ciclismo", "Gimnasio", "Otro")

/** Elige el tipo de entrenamiento. Antes de empezar pide el permiso de sensores. */
@Composable
fun PantallaElegirTipo(vm: AppViewModel, iniciado: () -> Unit) {
    val contexto = LocalContext.current
    var tipoPendiente by remember { mutableStateOf<String?>(null) }
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Con o sin permisos se puede entrenar: sin sensor la FC es simulada y sin notificaciones
        // la alerta solo se ve con la app abierta
        tipoPendiente?.let { type ->
            vm.iniciarSesion(type)
            iniciado()
        }
    }

    Pantalla {
        item { Titulo("Entrenar", "¿Qué vas a hacer?") }
        TIPOS.forEach { type ->
            item {
                ChipAccion(type, Colores.Azul, {
                    if (Sensores.tienePermisos(contexto)) {
                        vm.iniciarSesion(type)
                        iniciado()
                    } else {
                        tipoPendiente = type
                        pedirPermisos.launch(Sensores.permisosEntrenamiento())
                    }
                })
            }
        }
        if (Diagnostico.todoBien(contexto)) {
            item { Nota("Las alertas suenan y vibran aunque cierres la app o se apague la pantalla.") }
        } else {
            item { Nota("⚠ Faltan permisos de alertas: revisa Perfil → Diagnóstico de alertas.", Colores.Ambar) }
        }
        if (vm.perfil.sweatRateLh == null) {
            item { Nota("Sin prueba de sudoración las alertas solo te recordarán tomar agua según tu sed.") }
        }
    }
}

@Composable
fun PantallaSesion(
    vm: AppViewModel,
    registrar: () -> Unit,
    terminar: () -> Unit,
    salir: () -> Unit,
    abrirAlerta: () -> Unit,
) {
    val s = vm.sesion
    if (s == null) {
        LaunchedEffect(Unit) { salir() }
        return
    }
    val pausada = s.pausedSince != null
    val faltaMs = (s.nextAlertAt - vm.ahora).coerceAtLeast(0)
    val ml = vm.mlPerAlert()
    val condiciones = vm.condicionesActuales()
    val lectura = vm.fc

    Pantalla {
        item { Nota(if (pausada) "${s.type} · EN PAUSA" else s.type, if (pausada) Colores.Ambar else Color.LightGray) }
        item { Text(duracion(vm.duracionMs(s)), style = MaterialTheme.typography.display3) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = "Frecuencia cardíaca", tint = Colores.Rojo)
                Text(
                    if (lectura != null) "${lectura.bpm} lpm${if (lectura.simulada) " (simulada)" else ""}" else "-- lpm",
                    style = MaterialTheme.typography.body1,
                )
            }
        }
        item { Text("Tomado: ${s.drankMl.miles()} ml", style = MaterialTheme.typography.body2) }
        item {
            Nota(
                if (pausada) "Alertas en pausa"
                else if (ml != null) "Próxima toma en ${duracion(faltaMs)} · ~$ml ml"
                else "Recordatorio en ${duracion(faltaMs)}",
                Color.White,
            )
        }
        item {
            Nota(
                "${condiciones.temperatureC?.dec() ?: "--"} °C · ${condiciones.humidityPct?.dec(0) ?: "--"} % · " +
                    "${condiciones.altitudeM?.dec(0) ?: "--"} m",
            )
        }
        // Si la alerta se cerró deslizando, queda pendiente aquí
        if (vm.alerta != null) {
            item { ChipAccion("Responder alerta", Colores.Verde, abrirAlerta) }
        }
        item { ChipAccion("Registrar toma", Colores.Agua, registrar, icono = Icons.Default.Add) }
        item {
            ChipAccion(if (pausada) "Reanudar" else "Pausar", if (pausada) Colores.Azul else Colores.Ambar, vm::pausarOReanudar)
        }
        item { ChipAccion("Terminar", Colores.Rojo, terminar) }
        if (!pausada && vm.alerta == null) {
            item {
                ChipAccion(
                    "Probar alarma (10 s)", Colores.Gris, vm::probarAlarma,
                    detalle = "Sal a la esfera y espera",
                )
            }
        }
    }
}

/** Alerta dentro de la app (cuando la app está abierta). */
@Composable
fun PantallaAlerta(vm: AppViewModel, cerrar: () -> Unit) {
    val alerta = vm.alerta
    if (alerta == null) {
        LaunchedEffect(Unit) { cerrar() }
        return
    }
    ContenidoAlerta(
        alerta = alerta,
        onYaTome = {
            vm.yaTome()
            cerrar()
        },
        onPosponer = {
            vm.posponer()
            cerrar()
        },
    )
}

/** Contenido de la alerta; lo usan la app y la pantalla que sale sobre la esfera. */
@Composable
fun ContenidoAlerta(alerta: IntakeAlert, onYaTome: () -> Unit, onPosponer: () -> Unit) {
    Pantalla {
        item { Titulo("¡Hora de hidratarte!") }
        item {
            Text(
                if (alerta.ml != null) "${alerta.ml} ml" else "Toma según tu sed",
                style = if (alerta.ml != null) MaterialTheme.typography.display2 else MaterialTheme.typography.title2,
                color = Colores.AguaClara,
            )
        }
        if (alerta.factor.reasons.isNotEmpty()) {
            item { Nota("Ajuste ×${alerta.factor.value.dec()}: ${alerta.factor.reasons.joinToString(", ")}") }
        }
        if (alerta.ml == null) {
            item { Nota("Haz la prueba de sudoración en Perfil para recibir una cantidad exacta.") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonRedondo("✓", Colores.Verde, onYaTome, tamano = ButtonDefaults.DefaultButtonSize, descripcion = "Ya tomé")
                BotonRedondo("5'", Colores.Gris, onPosponer, tamano = ButtonDefaults.DefaultButtonSize, descripcion = "En 5 minutos")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nota("Ya tomé")
                Nota("En 5 min")
            }
        }
    }
}

@Composable
fun PantallaConfirmarFin(terminar: () -> Unit, cancelar: () -> Unit) {
    Pantalla {
        item { Titulo("¿Terminar el entrenamiento?") }
        item { ChipAccion("Sí, terminar", Colores.Rojo, terminar) }
        item { ChipAccion("Seguir entrenando", Colores.Gris, cancelar) }
    }
}

@Composable
fun PantallaResumen(vm: AppViewModel, id: Long, listo: () -> Unit) {
    val s = vm.sesiones.firstOrNull { it.id == id }
    Pantalla {
        item { Titulo("Resumen", s?.type) }
        if (s != null) {
            item { Text(duracion(s.durationSec * 1000), style = MaterialTheme.typography.display3) }
            item { Nota("FC media: ${s.avgHeartRate?.let { "$it lpm" } ?: "sin datos"}", Color.White) }
            item {
                Nota(
                    if (s.recommendedMl > 0) "Tomado ${s.drankMl.miles()} ml de ${s.recommendedMl.miles()} ml recomendados"
                    else "Tomado ${s.drankMl.miles()} ml",
                    Color.White,
                )
            }
            item { Nota("Guardado en el historial") }
        }
        item { ChipAccion("Listo", Colores.Verde, listo) }
    }
}

