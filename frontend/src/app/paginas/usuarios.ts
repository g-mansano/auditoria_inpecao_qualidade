import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { mensagemErro } from '../core/interceptors';
import { NivelAcesso, Papel, PermissaoFormulario, RegistroPermissao, Usuario } from '../core/modelos';

interface Edicao {
  id: number | null;
  username: string;
  nome: string;
  papel: Papel;
  ativo: boolean;
  senha: string;
  podeRevisar: boolean;
  /** Nível por formulário (nulo = sem acesso). */
  permissoes: PermissaoFormulario[];
}

const NIVEIS: Record<string, string> = { VER: 'Ver', PREENCHER: 'Preencher' };

@Component({
  selector: 'app-usuarios',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  template: `
    <div class="linha topo">
      <button mat-icon-button routerLink="/" class="btn-voltar" aria-label="Voltar"><mat-icon>arrow_back</mat-icon></button>
      <h1>Usuários</h1>
      <span class="espaco"></span>
      <button mat-flat-button (click)="novo()"><mat-icon>person_add</mat-icon> Novo usuário</button>
    </div>

    @if (edicao(); as e) {
      <div class="cartao">
        <h2>{{ e.id ? 'Editar ' + e.username : 'Novo usuário' }}</h2>
        <div class="grade">
          @if (!e.id) {
            <mat-form-field>
              <mat-label>Login (e-mail da empresa)</mat-label>
              <input matInput [(ngModel)]="e.username" autocapitalize="off" maxlength="60" />
            </mat-form-field>
          }
          <mat-form-field>
            <mat-label>Nome completo</mat-label>
            <input matInput [(ngModel)]="e.nome" maxlength="150" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Papel</mat-label>
            <mat-select [(ngModel)]="e.papel">
              <mat-option value="AUDITOR">Auditor / inspetor (preenche)</mat-option>
              <mat-option value="LEITOR">Leitor (somente consulta)</mat-option>
              <mat-option value="ADMIN">Administrador</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ e.id ? 'Nova senha (opcional)' : 'Senha (mín. 8)' }}</mat-label>
            <input matInput type="password" [(ngModel)]="e.senha" autocomplete="new-password" />
          </mat-form-field>
        </div>
        @if (e.id) {
          <div class="chaves">
            <mat-slide-toggle [(ngModel)]="e.ativo">Ativo</mat-slide-toggle>
            @if (e.papel !== 'ADMIN') {
              <mat-slide-toggle [(ngModel)]="e.podeRevisar">Pode revisar formulários</mat-slide-toggle>
            }
          </div>
        }

        <h3>Acesso aos formulários</h3>
        @if (e.papel === 'ADMIN') {
          <p class="mudo">O administrador acessa todos os formulários.</p>
        } @else {
          <p class="mudo">Ver = consultar, imprimir e baixar o PDF · Preencher = criar e editar (e ver).</p>
          <div class="permissoes">
            @for (p of e.permissoes; track p.templateId) {
              <div class="linha permissao">
                <span class="nome-form">
                  <b>{{ p.nome }}</b>
                  <span class="mudo">{{ p.identificacao }}</span>
                </span>
                <mat-button-toggle-group [value]="p.nivel ?? 'NENHUM'" (change)="p.nivel = $event.value === 'NENHUM' ? null : $event.value"
                  hideSingleSelectionIndicator [attr.aria-label]="'Acesso a ' + p.nome">
                  <mat-button-toggle value="NENHUM">Nenhum</mat-button-toggle>
                  <mat-button-toggle value="VER">Ver</mat-button-toggle>
                  <mat-button-toggle value="PREENCHER" [disabled]="e.papel === 'LEITOR'">Preencher</mat-button-toggle>
                </mat-button-toggle-group>
              </div>
            }
          </div>
        }

        @if (historico().length) {
          <details class="historico">
            <summary>Alterações de acesso ({{ historico().length }})</summary>
            @for (h of historico(); track $index) {
              <div class="mudo registro">
                {{ h.alteradoEm | date: 'dd/MM/yyyy HH:mm' }} · {{ h.alteradoPor }}: {{ h.formulario }}
                {{ nivel(h.nivelAnterior) }} → {{ nivel(h.nivelNovo) }}
              </div>
            }
          </details>
        }

        <div class="linha fim">
          <button mat-button (click)="edicao.set(null)">Cancelar</button>
          <button mat-flat-button (click)="salvar(e)">Salvar</button>
        </div>
      </div>
    }

    <div class="linha filtro">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="campo-filtro">
        <mat-label>Quem acessa o formulário</mat-label>
        <mat-select [ngModel]="filtroForm()" (ngModelChange)="filtrar($event)">
          <mat-option [value]="null">Todos os usuários</mat-option>
          @for (f of formularios(); track f.templateId) {
            <mat-option [value]="f.templateId">{{ f.nome }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <div class="cartao lista">
      @for (u of visiveis(); track u.id) {
        <button class="usuario" (click)="editar(u)">
          <mat-icon>{{ u.ativo ? 'person' : 'person_off' }}</mat-icon>
          <span class="corpo">
            <b>{{ u.nome }}</b>
            <span class="mudo">
              {{ u.username }} · {{ papeis[u.papel] }}{{ u.podeRevisar && u.papel !== 'ADMIN' ? ' · revisa formulários' : '' }}{{ u.ativo ? '' : ' · inativo' }}
            </span>
          </span>
          @if (filtroForm() !== null) {
            <span class="nivel">{{ u.papel === 'ADMIN' ? 'Admin' : nivel(acessoDe(u.id)) }}</span>
          }
          <mat-icon>chevron_right</mat-icon>
        </button>
      } @empty {
        <div class="mudo vazio">Ninguém acessa este formulário.</div>
      }
    </div>
  `,
  styles: `
    .topo { margin-bottom: 16px; }
    .topo h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); }
    .espaco { flex: 1; }
    .fim { justify-content: flex-end; margin-top: 12px; }
    .grade { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0 12px; }
    .lista { padding: 0; }
    .usuario {
      width: 100%; display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: none;
      border: 0; border-bottom: 1px solid #f1f5f9; text-align: left; font: inherit; cursor: pointer;
    }
    .corpo { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow-wrap: anywhere; }
    .chaves { display: flex; flex-wrap: wrap; gap: 8px 24px; }
    h3 { font-size: 15px; margin: 20px 0 4px; color: var(--cor-primaria); }
    .permissoes { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
    .permissao { gap: 8px 12px; padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
    .nome-form { flex: 1 1 200px; display: flex; flex-direction: column; min-width: 0; }
    .historico { margin-top: 16px; font-size: 13px; }
    .historico summary { cursor: pointer; color: var(--cor-primaria); font-weight: 600; }
    .registro { padding: 4px 0; }
    .filtro { margin-bottom: 12px; }
    .campo-filtro { width: 100%; max-width: 360px; }
    .nivel { font-size: 12px; font-weight: 700; color: #0369a1; background: #f0f7ff; border-radius: 9999px; padding: 2px 10px; }
    .vazio { padding: 24px; text-align: center; }
  `,
})
export class Usuarios {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  protected usuarios = signal<Usuario[]>([]);
  protected edicao = signal<Edicao | null>(null);
  protected historico = signal<RegistroPermissao[]>([]);
  protected formularios = signal<PermissaoFormulario[]>([]);
  protected filtroForm = signal<number | null>(null);
  /** usuarioId -> nível no formulário filtrado. */
  private acessos = signal<Record<number, NivelAcesso | null>>({});
  protected papeis: Record<Papel, string> = { ADMIN: 'Administrador', AUDITOR: 'Auditor', LEITOR: 'Leitor' };

  protected visiveis = computed(() => {
    const f = this.filtroForm();
    if (f === null) return this.usuarios();
    return this.usuarios().filter((u) => u.papel === 'ADMIN' || this.acessos()[u.id]);
  });

  ngOnInit(): void {
    this.carregar();
  }

  private async carregar(): Promise<void> {
    try {
      const lista = await firstValueFrom(this.http.get<Usuario[]>('/api/usuarios'));
      this.usuarios.set(lista);
      // Lista de formulários (com nível nulo) para o filtro e para usuários novos.
      const admin = lista.find((u) => u.papel === 'ADMIN');
      if (admin && !this.formularios().length) {
        const fs = await firstValueFrom(this.http.get<PermissaoFormulario[]>(`/api/usuarios/${admin.id}/permissoes`));
        this.formularios.set(fs.map((f) => ({ ...f, nivel: null })));
      }
      if (this.filtroForm() !== null) await this.filtrar(this.filtroForm());
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 5000 });
    }
  }

  protected async filtrar(templateId: number | null): Promise<void> {
    this.filtroForm.set(templateId);
    if (templateId === null) return;
    const mapa: Record<number, NivelAcesso | null> = {};
    await Promise.all(
      this.usuarios()
        .filter((u) => u.papel !== 'ADMIN')
        .map(async (u) => {
          const ps = await firstValueFrom(this.http.get<PermissaoFormulario[]>(`/api/usuarios/${u.id}/permissoes`));
          mapa[u.id] = ps.find((p) => p.templateId === templateId)?.nivel ?? null;
        }),
    );
    this.acessos.set(mapa);
  }

  protected acessoDe(id: number): NivelAcesso | null {
    return this.acessos()[id] ?? null;
  }

  protected nivel(n: string | null): string {
    return n ? NIVEIS[n] : 'Nenhum';
  }

  protected novo(): void {
    this.historico.set([]);
    this.edicao.set({
      id: null,
      username: '',
      nome: '',
      papel: 'AUDITOR',
      ativo: true,
      senha: '',
      podeRevisar: false,
      permissoes: this.formularios().map((f) => ({ ...f, nivel: null })),
    });
  }

  protected async editar(u: Usuario): Promise<void> {
    try {
      const [permissoes, historico] = await Promise.all([
        firstValueFrom(this.http.get<PermissaoFormulario[]>(`/api/usuarios/${u.id}/permissoes`)),
        firstValueFrom(this.http.get<RegistroPermissao[]>(`/api/usuarios/${u.id}/permissoes/historico`)),
      ]);
      this.historico.set(historico);
      this.edicao.set({
        id: u.id,
        username: u.username,
        nome: u.nome,
        papel: u.papel,
        ativo: u.ativo,
        senha: '',
        podeRevisar: !!u.podeRevisar,
        permissoes,
      });
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 5000 });
    }
  }

  protected async salvar(e: Edicao): Promise<void> {
    try {
      let id = e.id;
      if (id) {
        await firstValueFrom(
          this.http.put(`/api/usuarios/${id}`, {
            nome: e.nome,
            papel: e.papel,
            ativo: e.ativo,
            novaSenha: e.senha || null,
            podeRevisar: e.podeRevisar,
          }),
        );
      } else {
        const criado = await firstValueFrom(
          this.http.post<Usuario>('/api/usuarios', { username: e.username, nome: e.nome, senha: e.senha, papel: e.papel }),
        );
        id = criado.id;
      }
      if (e.papel !== 'ADMIN') {
        const niveis = e.permissoes.map((p) => ({
          templateId: p.templateId,
          nivel: e.papel === 'LEITOR' && p.nivel === 'PREENCHER' ? 'VER' : p.nivel,
        }));
        await firstValueFrom(this.http.put(`/api/usuarios/${id}/permissoes`, niveis));
      }
      this.edicao.set(null);
      this.snack.open('Usuário salvo.', undefined, { duration: 3000 });
      await this.carregar();
    } catch (err) {
      this.snack.open(mensagemErro(err), 'OK', { duration: 6000 });
    }
  }
}
