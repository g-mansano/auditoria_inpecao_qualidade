import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Usuario } from './modelos';

interface Sessao {
  token: string;
  expiraEm: string;
  usuario: Usuario;
}

const CHAVE = 'checklist.sessao';

/**
 * Sessão guardada no aparelho para o inspetor continuar trabalhando sem rede.
 * O token vale 30 dias (configurável no servidor).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private sessao = signal<Sessao | null>(this.carregar());

  readonly usuario = computed(() => this.sessao()?.usuario ?? null);
  readonly logado = computed(() => this.sessao() !== null);
  readonly podeEditar = computed(() => this.usuario()?.papel !== 'LEITOR');
  readonly admin = computed(() => this.usuario()?.papel === 'ADMIN');
  readonly podeRevisar = computed(() => this.admin() || !!this.usuario()?.podeRevisar);

  /** Atualiza nome/papel/permissões da sessão (ex.: o admin liberou a revisão de formulários). */
  async atualizarUsuario(): Promise<void> {
    const s = this.sessao();
    if (!s) return;
    try {
      const usuario = await firstValueFrom(this.http.get<Usuario>('/api/auth/eu'));
      this.salvar({ ...s, usuario });
    } catch {
      /* offline: segue com o que está salvo */
    }
  }

  get token(): string | null {
    return this.sessao()?.token ?? null;
  }

  async entrar(username: string, senha: string): Promise<void> {
    const s = await firstValueFrom(this.http.post<Sessao>('/api/auth/login', { username, senha }));
    this.salvar(s);
  }

  async sair(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', {}));
    } catch {
      // offline ou token já inválido: limpa local de qualquer forma
    }
    this.encerrarLocal();
  }

  /** Token expirado ou revogado. */
  encerrarLocal(): void {
    try {
      localStorage.removeItem(CHAVE);
    } catch {
      /* armazenamento indisponível */
    }
    this.sessao.set(null);
    this.router.navigateByUrl('/login');
  }

  private salvar(s: Sessao): void {
    try {
      localStorage.setItem(CHAVE, JSON.stringify(s));
    } catch {
      /* segue só em memória */
    }
    this.sessao.set(s);
  }

  private carregar(): Sessao | null {
    try {
      const raw = localStorage.getItem(CHAVE);
      if (!raw) return null;
      const s = JSON.parse(raw) as Sessao;
      return new Date(s.expiraEm) > new Date() ? s : null;
    } catch {
      return null;
    }
  }
}
