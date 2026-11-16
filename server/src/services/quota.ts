import { createHash } from 'node:crypto';
import type { Firestore } from 'firebase-admin/firestore';

/**
 * Cuota diaria de preguntas al coach.
 * - Por instalación: evita que un usuario (o un bucle) gaste el presupuesto de todos.
 * - Global: tope diario de todo el servicio.
 * Se consume antes de llamar a Gemini; si Gemini falla, se devuelve (release).
 */
export interface QuotaStore {
  /** Suma 1 si no se pasa del límite. */
  consume(key: string, day: string, limit: number): Promise<{ allowed: boolean; used: number }>;
  /** Devuelve 1 (la pregunta no se pudo responder). */
  release(key: string, day: string): Promise<void>;
}

export class MemoryQuotaStore implements QuotaStore {
  private readonly counts = new Map<string, number>();
  private currentDay = '';

  async consume(key: string, day: string, limit: number) {
    this.rollover(day);
    const used = this.counts.get(key) ?? 0;
    if (used >= limit) return { allowed: false, used };
    this.counts.set(key, used + 1);
    return { allowed: true, used: used + 1 };
  }

  async release(key: string, day: string) {
    this.rollover(day);
    const used = this.counts.get(key) ?? 0;
    if (used > 0) this.counts.set(key, used - 1);
  }

  private rollover(day: string) {
    if (day !== this.currentDay) {
      this.counts.clear();
      this.currentDay = day;
    }
  }
}

/**
 * Contadores en Firestore, compartidos por todas las instancias de Cloud Run.
 * Documento: coachQuotas/{día}_{hash de la clave}. El id de instalación se guarda como hash (no en claro).
 * Activa en Firestore una política TTL sobre el campo expireAt para borrar los documentos viejos.
 */
export class FirestoreQuotaStore implements QuotaStore {
  constructor(private readonly db: Firestore) {}

  private ref(key: string, day: string) {
    const hashed = createHash('sha256').update(key).digest('hex').slice(0, 40);
    return this.db.collection('coachQuotas').doc(`${day}_${hashed}`);
  }

  async consume(key: string, day: string, limit: number) {
    const ref = this.ref(key, day);
    return this.db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const used = (snap.exists ? Number(snap.get('count')) : 0) || 0;
      if (used >= limit) return { allowed: false, used };
      const expireAt = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
      tx.set(ref, { count: used + 1, day, expireAt }, { merge: true });
      return { allowed: true, used: used + 1 };
    });
  }

  async release(key: string, day: string) {
    const ref = this.ref(key, day);
    await this.db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const used = (snap.exists ? Number(snap.get('count')) : 0) || 0;
      if (used > 0) tx.set(ref, { count: used - 1 }, { merge: true });
    });
  }
}

/** Fecha "AAAA-MM-DD" en la zona horaria de las cuotas (se reinician a medianoche de Colombia). */
export function quotaDay(timeZone: string, date: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

/** Segundos hasta la siguiente medianoche en esa zona horaria (para el encabezado Retry-After). */
export function secondsUntilReset(timeZone: string, date: Date = new Date()): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  const elapsed = get('hour') * 3600 + get('minute') * 60 + get('second');
  return Math.max(1, 24 * 3600 - elapsed);
}
