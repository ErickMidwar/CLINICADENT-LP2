import { HttpErrorResponse } from '@angular/common/http';

interface ApiErrorBody {
  message?: string;
  camposInvalidos?: { campo: string; mensaje: string }[];
}

export function mensajeDeError(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return 'No hay conexión con el servidor. Verifica que el backend esté activo.';
  if (error.status === 403) return 'No tienes permisos para realizar esta acción.';
  const body = error.error as ApiErrorBody | null;
  const campos = body?.camposInvalidos?.map((campo) => campo.mensaje).filter(Boolean) ?? [];
  if (campos.length > 0) return campos.join(' · ');
  return body?.message || fallback;
}
