package co.edu.ucc.hidrasmart.alertas

import android.content.Context
import co.edu.ucc.hidrasmart.datos.Repositorio
import co.edu.ucc.hidrasmart.dominio.AlertaToma
import co.edu.ucc.hidrasmart.dominio.CalculadoraHidratacion
import co.edu.ucc.hidrasmart.dominio.Condiciones
import co.edu.ucc.hidrasmart.dominio.SesionEnCurso
import co.edu.ucc.hidrasmart.dominio.SesionGuardada
import co.edu.ucc.hidrasmart.dominio.Toma

/**
 * Reglas del entrenamiento y de las alertas. Todo se guarda en el reloj, así funciona
 * igual desde la app abierta, desde la alarma o desde los botones de la notificación.
 */
object MotorSesion {

    private const val POSPONER_MS = 5 * 60_000L

    private fun ahora() = System.currentTimeMillis()
    private fun intervaloMs(repo: Repositorio) = repo.perfil().intervaloMin * 60_000L

    fun iniciar(contexto: Context, tipo: String) {
        val repo = Repositorio(contexto)
        if (repo.sesionActiva() != null) return
        val t = ahora()
        val sesion = SesionEnCurso(tipo = tipo, inicio = t, proximaAlerta = t + intervaloMs(repo))
        repo.guardarAlerta(null)
        repo.guardarSesionActiva(sesion)
        Alarmas.programar(contexto, sesion.proximaAlerta)
    }

    /** Llega la hora: calcula los ml, guarda la alerta, avisa y programa la siguiente. */
    fun dispararAlerta(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausadaDesde != null) return
        val t = ahora()
        val siguiente = t + intervaloMs(repo)

        val pendiente = repo.alertaPendiente()
        if (pendiente != null) {
            // No respondió la anterior: se recuerda la misma, sin sumar otra recomendación
            repo.guardarSesionActiva(sesion.copy(proximaAlerta = siguiente))
            Alarmas.programar(contexto, siguiente)
            Alarmas.notificar(contexto, pendiente)
            return
        }

        val perfil = repo.perfil()
        val clima = repo.ultimoClima()
        val condiciones = Condiciones(
            altitudM = repo.ultimaAltitud() ?: perfil.altitudHabitualM,
            temperaturaC = clima?.temperaturaC ?: perfil.temperaturaManualC,
            humedadPct = clima?.humedadPct ?: perfil.humedadManualPct,
            fcBpm = repo.ultimaFc(),
        )
        val factor = CalculadoraHidratacion.factor(condiciones, perfil.condicionesPrueba, perfil.edad)
        val ml = perfil.tasaSudoracionLh?.let { CalculadoraHidratacion.mlPorAlerta(it, factor.valor, perfil.intervaloMin) }
        val alerta = AlertaToma(ml, factor, t)

        repo.guardarAlerta(alerta)
        repo.guardarSesionActiva(sesion.copy(recomendadoMl = sesion.recomendadoMl + (ml ?: 0), proximaAlerta = siguiente))
        Alarmas.programar(contexto, siguiente)
        Alarmas.notificar(contexto, alerta)
    }

    /** Prueba real: la alarma del sistema suena en 10 s, para comprobarla con la app cerrada. */
    fun programarPrueba(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausadaDesde != null) return
        val cuando = ahora() + 10_000L
        repo.guardarSesionActiva(sesion.copy(proximaAlerta = cuando))
        Alarmas.programar(contexto, cuando)
    }

    /** "Ya tomé": registra los ml y la próxima alerta queda a un intervalo de ahora. */
    fun yaTome(contexto: Context) {
        val repo = Repositorio(contexto)
        val alerta = repo.alertaPendiente() ?: return
        alerta.ml?.let { registrarToma(contexto, it) }
        repo.guardarAlerta(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.sesionActiva() ?: return
        val siguiente = ahora() + intervaloMs(repo)
        repo.guardarSesionActiva(sesion.copy(proximaAlerta = siguiente))
        Alarmas.programar(contexto, siguiente)
    }

    /** "En 5 min": esa recomendación no cuenta todavía. */
    fun posponer(contexto: Context) {
        val repo = Repositorio(contexto)
        val alerta = repo.alertaPendiente() ?: return
        repo.guardarAlerta(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.sesionActiva() ?: return
        val siguiente = ahora() + POSPONER_MS
        repo.guardarSesionActiva(
            sesion.copy(proximaAlerta = siguiente, recomendadoMl = (sesion.recomendadoMl - (alerta.ml ?: 0)).coerceAtLeast(0)),
        )
        Alarmas.programar(contexto, siguiente)
    }

    fun registrarToma(contexto: Context, ml: Int) {
        val repo = Repositorio(contexto)
        repo.agregarToma(Toma(ahora(), ml))
        repo.sesionActiva()?.let { repo.guardarSesionActiva(it.copy(tomadoMl = it.tomadoMl + ml)) }
    }

    fun pausarOReanudar(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        val t = ahora()
        val pausadaDesde = sesion.pausadaDesde
        if (pausadaDesde == null) {
            repo.guardarSesionActiva(sesion.copy(pausadaDesde = t))
            Alarmas.cancelar(contexto)
        } else {
            val pausa = t - pausadaDesde
            val reanudada = sesion.copy(pausadaDesde = null, pausadoMs = sesion.pausadoMs + pausa, proximaAlerta = sesion.proximaAlerta + pausa)
            repo.guardarSesionActiva(reanudada)
            Alarmas.programar(contexto, reanudada.proximaAlerta)
        }
    }

    /** Suma un bloque de lecturas de frecuencia cardíaca al promedio de la sesión. */
    fun sumarFc(contexto: Context, suma: Long, lecturas: Int) {
        if (lecturas <= 0) return
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        repo.guardarSesionActiva(sesion.copy(fcSuma = sesion.fcSuma + suma, fcLecturas = sesion.fcLecturas + lecturas))
    }

    /** Si cambia el intervalo durante la sesión, la próxima alerta se ajusta de una vez. */
    fun ajustarIntervalo(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausadaDesde != null) return
        val conNuevo = ahora() + intervaloMs(repo)
        if (conNuevo < sesion.proximaAlerta) {
            repo.guardarSesionActiva(sesion.copy(proximaAlerta = conNuevo))
            Alarmas.programar(contexto, conNuevo)
        }
    }

    /** Termina, guarda en el historial y devuelve el id. */
    fun terminar(contexto: Context): Long? {
        val repo = Repositorio(contexto)
        val s = repo.sesionActiva() ?: return null
        val t = ahora()
        Alarmas.cancelar(contexto)
        Alarmas.quitarNotificacion(contexto)
        repo.guardarAlerta(null)
        val guardada = SesionGuardada(
            id = t,
            tipo = s.tipo,
            inicio = s.inicio,
            fin = t,
            duracionSeg = (((s.pausadaDesde ?: t) - s.inicio - s.pausadoMs) / 1000).coerceAtLeast(0),
            fcMedia = if (s.fcLecturas > 0) (s.fcSuma / s.fcLecturas).toInt() else null,
            tomadoMl = s.tomadoMl,
            recomendadoMl = s.recomendadoMl.coerceAtLeast(0),
        )
        repo.guardarSesion(guardada)
        repo.guardarSesionActiva(null)
        return guardada.id
    }
}
