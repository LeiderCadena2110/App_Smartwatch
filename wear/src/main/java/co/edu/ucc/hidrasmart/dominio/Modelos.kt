package co.edu.ucc.hidrasmart.dominio

/** Condiciones en las que se entrena (o en las que se hizo la prueba de sudoración). */
data class Condiciones(
    val altitudM: Double? = null,
    val temperaturaC: Double? = null,
    val humedadPct: Double? = null,
    val fcBpm: Int? = null,
)

/** Datos que el atleta ingresa después de la prueba de sudoración. */
data class PruebaSudoracion(
    val pesoInicialKg: Double,
    val pesoFinalKg: Double,
    val liquidoMl: Int,
    val orinaMl: Int,
    val duracionMin: Int,
)

/**
 * Perfil del atleta. Los valores por defecto son solo un punto de partida:
 * el atleta los cambia en Perfil y ajustes.
 */
data class Perfil(
    val pesoKg: Double = 70.0,
    val edad: Int = 22,
    val metaDiariaMl: Int = 2000,
    val intervaloMin: Int = 15,
    val ciudad: String = "Pasto,CO",
    val altitudHabitualM: Double = 2527.0,
    val temperaturaManualC: Double = 14.0,
    val humedadManualPct: Double = 75.0,
    val tasaSudoracionLh: Double? = null,
    val condicionesPrueba: Condiciones? = null,
)

data class Toma(val instante: Long, val ml: Int)

data class SesionGuardada(
    val id: Long,
    val tipo: String,
    val inicio: Long,
    val fin: Long,
    val duracionSeg: Long,
    val fcMedia: Int?,
    val tomadoMl: Int,
    val recomendadoMl: Int,
)

data class Clima(val temperaturaC: Double, val humedadPct: Double, val ciudad: String, val instante: Long)

/** Factor de ajuste y las razones que lo explican (para mostrarlas al atleta). */
data class Factor(val valor: Double, val razones: List<String>)

/** Entrenamiento en curso. Se guarda en el reloj para que las alarmas funcionen con la app cerrada. */
data class SesionEnCurso(
    val tipo: String,
    val inicio: Long,
    val proximaAlerta: Long,
    val pausadaDesde: Long? = null,
    val pausadoMs: Long = 0,
    val tomadoMl: Int = 0,
    val recomendadoMl: Int = 0,
    val fcSuma: Long = 0,
    val fcLecturas: Int = 0,
)

/** Alerta de hidratación pendiente de respuesta. [ml] es null si aún no hay prueba de sudoración. */
data class AlertaToma(val ml: Int?, val factor: Factor, val instante: Long)
