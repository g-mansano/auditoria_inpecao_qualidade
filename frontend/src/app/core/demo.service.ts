import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Conta de exemplo da demonstração pública (GET /api/demo). */
export interface ContaDemo {
  username: string;
  senha: string;
  nome: string;
  papel: string;
  descricao: string;
}

const CHAVE = 'checklist.demo';

/**
 * Modo demonstração: o servidor informa as contas de exemplo. Fora da demo, /api/demo responde 404
 * e nada aparece. A última resposta fica no localStorage para o selo "Demo" continuar visível offline.
 */
@Injectable({ providedIn: 'root' })
export class DemoService {
  private http = inject(HttpClient);

  readonly contas = signal<ContaDemo[]>(this.lerCache());
  readonly ativo = computed(() => this.contas().length > 0);

  async carregar(): Promise<void> {
    try {
      const r = await firstValueFrom(this.http.get<{ contas: ContaDemo[] }>('/api/demo'));
      this.contas.set(r.contas ?? []);
    } catch (e: unknown) {
      // 404 = servidor fora do modo demonstração; sem rede, fica o cache.
      if ((e as { status?: number })?.status === 404) this.contas.set([]);
    }
    try {
      localStorage.setItem(CHAVE, JSON.stringify(this.contas()));
    } catch {
      // armazenamento indisponível: só não guarda o cache
    }
  }

  private lerCache(): ContaDemo[] {
    try {
      return JSON.parse(localStorage.getItem(CHAVE) ?? '[]') as ContaDemo[];
    } catch {
      return [];
    }
  }
}
