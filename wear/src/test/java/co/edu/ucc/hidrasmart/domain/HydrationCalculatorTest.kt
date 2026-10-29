package co.edu.ucc.hidrasmart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraHidratacionTest {

    private val prueba = PruebaSudoracion(pesoInicialKg = 70.0, pesoFinalKg = 69.2, liquidoMl = 500, orinaMl = 0, duracionMin = 60)

    @Test
    fun tasaSudoracion_ejemploNata() {
        // (70 − 69,2) + 0,5 − 0 = 1,3 L en 1 h
        assertEquals(1.3, CalculadoraHidratacion.tasaSudoracion(prueba), 0.0001)
    }

    @Test
    fun tasaSudoracion_restaLaOrina() {
        val conOrina = prueba.copy(orinaMl = 300, duracionMin = 30)
        // (0,8 + 0,5 − 0,3) / 0,5 h = 2,0 L/h
        assertEquals(2.0, CalculadoraHidratacion.tasaSudoracion(conOrina), 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tasaSudoracion_rechazaDatosImposibles() {
        CalculadoraHidratacion.tasaSudoracion(prueba.copy(pesoFinalKg = 75.0, liquidoMl = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tasaSudoracion_rechazaDuracionCero() {
        CalculadoraHidratacion.tasaSudoracion(prueba.copy(duracionMin = 0))
    }

    @Test
    fun perdidaDePeso_enPorcentaje() {
        assertEquals(1.142857, CalculadoraHidratacion.porcentajePerdidaPeso(prueba), 0.0001)
    }

    @Test
    fun mlPorAlerta_sinAjuste() {
        // 1,3 L/h × 1 × 1000 × 15/60 = 325 → 330 (redondeo a 10 ml)
        assertEquals(330, CalculadoraHidratacion.mlPorAlerta(1.3, 1.0, 15))
    }

    @Test
    fun mlPorAlerta_conFactor() {
        // 0,8 L/h × 1,2 × 1000 × 20/60 = 320
        assertEquals(320, CalculadoraHidratacion.mlPorAlerta(0.8, 1.2, 20))
    }

    @Test
    fun factor_esUnoEnLasMismasCondiciones() {
        val c = Condiciones(altitudM = 2527.0, temperaturaC = 14.0, humedadPct = 75.0, fcBpm = 120)
        assertEquals(1.0, CalculadoraHidratacion.factor(c, c, edad = 22).valor, 0.0001)
    }

    @Test
    fun factor_noSeActivaSoloPorEstarEnPasto() {
        // La prueba se hizo en Pasto y se entrena en Pasto: la altitud alta no suma
        val pasto = Condiciones(altitudM = 2527.0, temperaturaC = 14.0, humedadPct = 60.0)
        assertEquals(1.0, CalculadoraHidratacion.factor(pasto, pasto, edad = 22).valor, 0.0001)
    }

    @Test
    fun factor_sumaPorCadaCondicionYTieneTope() {
        val pruebaCond = Condiciones(altitudM = 500.0, temperaturaC = 15.0, humedadPct = 50.0)
        val dura = Condiciones(altitudM = 2600.0, temperaturaC = 25.0, humedadPct = 85.0, fcBpm = 190)
        val f = CalculadoraHidratacion.factor(dura, pruebaCond, edad = 22)
        assertEquals(1.3, f.valor, 0.0001) // 4 razones pero tope 1,3
        assertEquals(4, f.razones.size)
    }

    @Test
    fun factor_humedad() {
        val pruebaSeca = Condiciones(humedadPct = 60.0)
        val f = CalculadoraHidratacion.factor(Condiciones(humedadPct = 80.0), pruebaSeca, edad = 22)
        assertEquals(1.1, f.valor, 0.0001)
        assertTrue(f.razones.first().contains("Humedad"))
    }

    @Test
    fun fcMaxima_clasica() {
        assertEquals(198, CalculadoraHidratacion.fcMaxima(22))
    }
}
