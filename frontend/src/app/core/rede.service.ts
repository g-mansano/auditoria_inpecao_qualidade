import { Injectable, signal } from '@angular/core';

/**
 * Estado da conexão com o servidor. navigator.onLine sozinho engana (Wi-Fi sem rota
 * para o servidor), então também testamos /actuator/health periodicamente.
 */
@Injectable({ providedIn: 'root' })
export class RedeService {
  readonly online = signal(navigator.onLine);
  private ouvintes: (() => void)[] = [];

  constructor() {
    window.addEventListener('online', () => this.verificar());
    window.addEventListener('offline', () => this.definir(false));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.verificar();
    });
    setInterval(() => this.verificar(), 30_000);
    this.verificar();
  }

  /** Chamado quando a conexão volta. */
  aoReconectar(fn: () => void): void {
    this.ouvintes.push(fn);
  }

  /** Marca offline após uma falha de rede numa chamada da API. */
  falhou(): void {
    this.definir(false);
  }

  async verificar(): Promise<boolean> {
    if (!navigator.onLine) {
      this.definir(false);
      return false;
    }
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 5000);
      const r = await fetch('/actuator/health', { cache: 'no-store', signal: ctrl.signal });
      clearTimeout(t);
      this.definir(r.ok);
    } catch {
      this.definir(false);
    }
    return this.online();
  }

  private definir(v: boolean): void {
    const antes = this.online();
    this.online.set(v);
    if (v && !antes) this.ouvintes.forEach((f) => f());
  }
}
