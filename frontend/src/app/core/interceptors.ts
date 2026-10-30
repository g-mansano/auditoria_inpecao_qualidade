import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { RedeService } from './rede.service';
import { dispositivoId } from './util';

export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) return next(req);
  const auth = inject(AuthService);
  const rede = inject(RedeService);

  const headers: Record<string, string> = { 'X-Dispositivo': dispositivoId() };
  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`;

  return next(req.clone({ setHeaders: headers })).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status === 0) rede.falhou();
      if (e.status === 401 && !req.url.endsWith('/auth/login')) auth.encerrarLocal();
      return throwError(() => e);
    }),
  );
};

/** Mensagem legível de um erro da API. */
export function mensagemErro(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'Sem conexão com o servidor.';
    const corpo = e.error as { mensagem?: string } | null;
    if (corpo?.mensagem) return corpo.mensagem;
    return `Erro ${e.status}.`;
  }
  return e instanceof Error ? e.message : 'Erro inesperado.';
}
