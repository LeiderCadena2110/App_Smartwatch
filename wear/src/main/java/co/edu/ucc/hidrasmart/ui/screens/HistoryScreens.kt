package co.edu.ucc.hidrasmart.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.ui.ChipAccion
import co.edu.ucc.hidrasmart.ui.Nota
import co.edu.ucc.hidrasmart.ui.Pantalla
import co.edu.ucc.hidrasmart.ui.Titulo
import co.edu.ucc.hidrasmart.ui.aFecha
import co.edu.ucc.hidrasmart.ui.duracion
import co.edu.ucc.hidrasmart.ui.hora
import co.edu.ucc.hidrasmart.ui.miles
import co.edu.ucc.hidrasmart.ui.nombre
import co.edu.ucc.hidrasmart.ui.theme.Colores
import java.time.LocalDate

@Composable
fun PantallaHistorial(vm: AppViewModel, abrirDia: (String) -> Unit) {
    val dias = (vm.tomas.map { it.instante.aFecha() } + vm.sesiones.map { it.inicio.aFecha() })
        .distinct()
        .sortedDescending()
        .take(30)

    Pantalla {
        item { Titulo("Historial") }
        if (dias.isEmpty()) {
            item { Nota("Aún no hay registros. Empieza registrando una toma.") }
        }
        dias.forEach { dia ->
            val total = vm.tomas.filter { it.instante.aFecha() == dia }.sumOf { it.ml }
            val entrenos = vm.sesiones.count { it.inicio.aFecha() == dia }
            item {
                ChipAccion(
                    dia.nombre(), Colores.GrisOscuro, { abrirDia(dia.toString()) },
                    detalle = "${total.miles()} ml · $entrenos entreno${if (entrenos == 1) "" else "s"}",
                )
            }
        }
    }
}

@Composable
fun PantallaDetalleDia(vm: AppViewModel, fecha: String, abrirSesion: (Long) -> Unit) {
    val dia = runCatching { LocalDate.parse(fecha) }.getOrNull() ?: LocalDate.now()
    val tomas = vm.tomas.filter { it.instante.aFecha() == dia }.sortedByDescending { it.instante }
    val sesiones = vm.sesiones.filter { it.inicio.aFecha() == dia }.sortedByDescending { it.inicio }
    val total = tomas.sumOf { it.ml }

    Pantalla {
        item { Titulo(dia.nombre(), "${total.miles()} de ${vm.perfil.metaDiariaMl.miles()} ml") }
        sesiones.forEach { s ->
            item {
                ChipAccion(
                    s.tipo, Colores.Azul, { abrirSesion(s.id) },
                    detalle = "${s.inicio.hora()} · ${duracion(s.duracionSeg * 1000)}",
                    icono = Icons.Default.PlayArrow,
                )
            }
        }
        if (tomas.isNotEmpty()) {
            item { Nota("Tomas") }
        }
        tomas.forEach { t ->
            item { Text("${t.instante.hora()} · ${t.ml} ml", style = MaterialTheme.typography.body2) }
        }
    }
}

@Composable
fun PantallaDetalleSesion(vm: AppViewModel, id: Long) {
    val s = vm.sesiones.firstOrNull { it.id == id }
    Pantalla {
        if (s == null) {
            item { Nota("No se encontró la sesión") }
            return@Pantalla
        }
        item { Titulo(s.tipo, "${s.inicio.aFecha().nombre()} · ${s.inicio.hora()}") }
        item { Text(duracion(s.duracionSeg * 1000), style = MaterialTheme.typography.display3) }
        item { Nota("FC media: ${s.fcMedia?.let { "$it lpm" } ?: "sin datos"}", Color.White) }
        item { Nota("Tomado: ${s.tomadoMl.miles()} ml", Color.White) }
        item { Nota("Recomendado: ${s.recomendadoMl.miles()} ml", Color.White) }
    }
}
