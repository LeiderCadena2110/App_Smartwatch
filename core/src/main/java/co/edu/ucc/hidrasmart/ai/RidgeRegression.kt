package co.edu.ucc.hidrasmart.ai

import kotlin.math.abs

/**
 * Regresión lineal con regularización L2 (ridge), entrenada en el propio dispositivo.
 *
 * Resuelve (XᵀX + λI)·w = Xᵀy con eliminación de Gauss. Es pequeña a propósito:
 * pocas variables y pocos datos, así que corre en milisegundos en el reloj y no
 * necesita librerías de ML ni conexión a internet.
 */
object RidgeRegression {

    /**
     * @param x filas de variables (todas del mismo tamaño)
     * @param y valor observado para cada fila
     * @param lambda fuerza de la regularización (evita pesos extremos con pocos datos)
     * @return los pesos w, uno por variable
     */
    fun fit(x: List<DoubleArray>, y: List<Double>, lambda: Double): DoubleArray {
        require(x.isNotEmpty()) { "Se necesita al menos una fila" }
        require(x.size == y.size) { "x e y deben tener el mismo número de filas" }
        val k = x.first().size
        require(x.all { it.size == k }) { "Todas las filas deben tener $k variables" }

        // A = XᵀX + λI   b = Xᵀy
        val a = Array(k) { DoubleArray(k) }
        val b = DoubleArray(k)
        for (row in x.indices) {
            val xi = x[row]
            for (i in 0 until k) {
                b[i] += xi[i] * y[row]
                for (j in 0 until k) a[i][j] += xi[i] * xi[j]
            }
        }
        for (i in 0 until k) a[i][i] += lambda
        return solve(a, b)
    }

    fun predict(weights: DoubleArray, features: DoubleArray): Double {
        require(weights.size == features.size) { "Pesos y variables deben tener el mismo tamaño" }
        var sum = 0.0
        for (i in weights.indices) sum += weights[i] * features[i]
        return sum
    }

    /** Coeficiente de determinación R² (1 = el modelo explica todo; 0 = igual que el promedio). */
    fun rSquared(weights: DoubleArray, x: List<DoubleArray>, y: List<Double>): Double {
        val mean = y.average()
        var ssRes = 0.0
        var ssTot = 0.0
        for (i in x.indices) {
            val err = y[i] - predict(weights, x[i])
            ssRes += err * err
            ssTot += (y[i] - mean) * (y[i] - mean)
        }
        return if (ssTot == 0.0) (if (ssRes < 1e-9) 1.0 else 0.0) else 1.0 - ssRes / ssTot
    }

    /** Eliminación de Gauss con pivoteo parcial. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf() }
        val v = b.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            require(abs(m[pivot][col]) > 1e-12) { "Sistema sin solución única (datos insuficientes)" }
            if (pivot != col) {
                val tmpRow = m[col]; m[col] = m[pivot]; m[pivot] = tmpRow
                val tmpVal = v[col]; v[col] = v[pivot]; v[pivot] = tmpVal
            }
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                for (c in col until n) m[r][c] -= f * m[col][c]
                v[r] -= f * v[col]
            }
        }
        val w = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = v[r]
            for (c in r + 1 until n) s -= m[r][c] * w[c]
            w[r] = s / m[r][r]
        }
        return w
    }
}
