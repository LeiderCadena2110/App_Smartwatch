package co.edu.ucc.hidrasmart.ui.pantallas

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
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.datos.Sensores
import co.edu.ucc.hidrasmart.dominio.CalculadoraHidratacion
import co.edu.ucc.hidrasmart.dominio.Perfil
import co.edu.ucc.hidrasmart.dominio.PruebaSudoracion
import co.edu.ucc.hidrasmart.ui.ChipAccion
import co.edu.ucc.hidrasmart.ui.FilaNumero
import co.edu.ucc.hidrasmart.ui.Nota
import co.edu.ucc.hidrasmart.ui.Pantalla
import co.edu.ucc.hidrasmart.ui.Titulo
import co.edu.ucc.hidrasmart.ui.dec
import co.edu.ucc.hidrasmart.ui.miles
import co.edu.ucc.hidrasmart.ui.tema.Colores
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

    fun leer(p: Perfil): Double = when (this) {
        PESO -> p.pesoKg
        EDAD -> p.edad.toDouble()
        META -> p.metaDiariaMl.toDouble()
        INTERVALO -> p.intervaloMin.toDouble()
        ALTITUD -> p.altitudHabitualM
        TEMPERATURA -> p.temperaturaManualC
        HUMEDAD -> p.humedadManualPct
    }

    fun escribir(p: Perfil, v: Double): Perfil = when (this) {
        PESO -> p.copy(pesoKg = v)
        EDAD -> p.copy(edad = v.roundToInt())
        META -> p.copy(metaDiariaMl = v.roundToInt())
        INTERVALO -> p.copy(intervaloMin = v.roundToInt())
        ALTITUD -> p.copy(altitudHabitualM = v)
        TEMPERATURA -> p.copy(temperaturaManualC = v)
        HUMEDAD -> p.copy(humedadManualPct = v)
    }

    fun texto(p: Perfil): String = "${leer(p).dec(decimales)} $unidad"
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

    Pantalla {
        item { Titulo("Perfil y ajustes") }
        item {
            ChipAccion(
                "Prueba de sudoración", Colores.Agua, abrirPrueba,
                detalle = p.tasaSudoracionLh?.let { "${it.dec(2)} L/h" } ?: "Sin hacer",
            )
        }
        listOf(CampoPerfil.PESO, CampoPerfil.EDAD, CampoPerfil.META, CampoPerfil.INTERVALO).forEach { campo ->
            item { ChipAccion(campo.titulo, Colores.GrisOscuro, { editar(campo) }, detalle = campo.texto(p)) }
        }
        item {
            ChipAccion("Ciudad del clima", Colores.GrisOscuro, {
                val siguiente = CIUDADES[(CIUDADES.indexOf(p.ciudad) + 1) % CIUDADES.size]
                vm.guardarPerfil(p.copy(ciudad = siguiente))
                vm.actualizarClima()
            }, detalle = p.ciudad.substringBefore(","))
        }
        val clima = vm.clima
        item {
            Nota(
                if (clima != null) "Clima: ${clima.temperaturaC.dec()} °C · ${clima.humedadPct.dec(0)} %"
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
    }
}

@Composable
fun PantallaEditarCampo(vm: AppViewModel, campo: CampoPerfil, listo: () -> Unit) {
    var valor by rememberSaveable { mutableDoubleStateOf(campo.leer(vm.perfil)) }
    fun ajustar(delta: Double) {
        val nuevo = ((valor + delta) * 10).roundToInt() / 10.0
        valor = nuevo.coerceIn(campo.minimo, campo.maximo)
    }
    Pantalla {
        item { Titulo(campo.titulo) }
        item { FilaNumero(campo.unidad, valor.dec(campo.decimales), { ajustar(-campo.paso) }, { ajustar(campo.paso) }) }
        item {
            ChipAccion("Guardar", Colores.Verde, {
                vm.guardarPerfil(campo.escribir(vm.perfil, valor))
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
    var pesoInicial by rememberSaveable { mutableDoubleStateOf(vm.perfil.pesoKg) }
    var pesoFinal by rememberSaveable { mutableDoubleStateOf(vm.perfil.pesoKg - 0.5) }
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
                    val prueba = PruebaSudoracion(pesoInicial, pesoFinal, liquido, orina, minutos)
                    vm.guardarPrueba(prueba)
                        .onSuccess { tasa ->
                            val perdida = CalculadoraHidratacion.porcentajePerdidaPeso(prueba)
                            guardada = true
                            mensaje = "Tasa: ${tasa.dec(2)} L/h. Perdiste ${perdida.dec(1)} % del peso" +
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
