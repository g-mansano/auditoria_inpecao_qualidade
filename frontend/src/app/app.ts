import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterOutlet } from '@angular/router';
import { SwUpdate } from '@angular/service-worker';
import { AuthService } from './core/auth.service';
import { DemoService } from './core/demo.service';
import { RedeService } from './core/rede.service';
import { SyncService } from './core/sync.service';
import { TrocarSenhaDialog } from './paginas/trocar-senha';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule],
  template: `
    @if (auth.logado()) {
      <mat-toolbar class="barra">
        <a routerLink="/" class="marca">
          <img src="/logo-acme.svg" alt="Acme Indústria (empresa fictícia)" class="logo" />
          <span class="divisor-vertical"></span>
          <span class="subtitulo-app">Auditoria Interna</span>
        </a>
        @if (demo.ativo()) {
          <span class="selo-demo" matTooltip="Demonstração: empresa e dados fictícios, reiniciados todo dia">Demo</span>
        }
        <span class="espaco"></span>

        <button
          mat-button
          class="pilula-status"
          [class.offline]="!rede.online()"
          [class.enviando]="rede.online() && sync.pendentes() > 0"
          [class.alerta]="sync.rejeitadas() > 0"
          (click)="sync.sincronizar()"
          [matTooltip]="dicaStatus()"
        >
          <span class="ponto-status"></span>
          <mat-icon class="icone-status">{{ iconeStatus() }}</mat-icon>
          <span class="rotulo-status">{{ rotuloStatus() }}</span>
        </button>

        <button mat-icon-button [matMenuTriggerFor]="menu" class="btn-usuario" aria-label="Menu do usuário">
          <mat-icon>account_circle</mat-icon>
        </button>
        <mat-menu #menu="matMenu" class="menu-usuario">
          <div class="quem">
            <span class="nome-usuario">{{ auth.usuario()?.nome }}</span>
            <span class="papel-usuario">{{ auth.usuario()?.papel }}</span>
          </div>
          @if (auth.admin()) {
            <button mat-menu-item routerLink="/usuarios"><mat-icon>group</mat-icon>Usuários</button>
          }
          <button mat-menu-item (click)="trocarSenha()" [disabled]="!rede.online()">
            <mat-icon>key</mat-icon>Trocar senha
          </button>
          <button mat-menu-item (click)="sair()"><mat-icon>logout</mat-icon>Sair</button>
        </mat-menu>
      </mat-toolbar>
    }
    @if (atualizacaoDisponivel()) {
      <div class="faixa-atualizacao">
        <mat-icon>system_update</mat-icon>
        <span>Nova versão disponível.</span>
        <button mat-flat-button (click)="recarregar()">Atualizar agora</button>
      </div>
    }
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .barra {
      position: sticky;
      top: 0;
      z-index: 10;
      background: #ffffff;
      color: #1e293b;
      border-bottom: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      padding: 0 16px;
      padding-top: env(safe-area-inset-top);
      height: 64px;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .marca {
      display: flex;
      align-items: center;
      gap: 12px;
      text-decoration: none;
      color: inherit;
    }
    .logo {
      height: 28px;
      width: auto;
      object-fit: contain;
      display: block;
    }
    .divisor-vertical {
      width: 1px;
      height: 22px;
      background: #cbd5e1;
    }
    .subtitulo-app {
      font-size: 15px;
      font-weight: 600;
      color: var(--cor-primaria);
      letter-spacing: -0.01em;
    }
    .espaco { flex: 1; }
    .selo-demo {
      margin-left: 10px;
      padding: 2px 10px;
      border-radius: 9999px;
      background: var(--cor-secundaria-clara);
      color: var(--cor-secundaria);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .pilula-status {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 4px 14px;
      height: 36px;
      border-radius: 9999px;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #15803d !important;
      font-size: 13px;
      font-weight: 600;
      transition: all 0.2s ease;
    }
    .ponto-status {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #22c55e;
      display: inline-block;
      box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
      animation: pulsar 2s ease-in-out infinite;
    }
    @keyframes pulsar {
      50% { box-shadow: 0 0 0 5px rgba(34, 197, 94, 0); }
    }
    @media (prefers-reduced-motion: reduce) {
      .ponto-status { animation: none; }
    }
    .icone-status {
      font-size: 18px;
      width: 18px;
      height: 18px;
      display: none;
    }

    .pilula-status.enviando {
      background: #f0f7ff;
      border-color: #bae6fd;
      color: #0369a1 !important;
    }
    .pilula-status.enviando .ponto-status {
      background: #0ea5e9;
      box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.2);
    }

    .pilula-status.offline {
      background: #fffbeb;
      border-color: #fde68a;
      color: #b45309 !important;
    }
    .pilula-status.offline .ponto-status {
      background: #f59e0b;
      box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.2);
    }

    .pilula-status.alerta {
      background: #fef2f2;
      border-color: #fecaca;
      color: #b91c1c !important;
    }
    .pilula-status.alerta .ponto-status {
      background: #ef4444;
      box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
    }

    .btn-usuario {
      color: #475467;
    }
    .quem {
      padding: 10px 16px;
      display: flex;
      flex-direction: column;
      border-bottom: 1px solid #f1f5f9;
    }
    .nome-usuario {
      font-weight: 600;
      color: #0f172a;
      font-size: 14px;
    }
    .papel-usuario {
      font-size: 12px;
      color: #64748b;
    }

    .faixa-atualizacao {
      background: #eff6ff;
      border-bottom: 1px solid #bfdbfe;
      color: #1e40af;
      padding: 10px 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 14px;
      font-weight: 500;
    }
    .faixa-atualizacao button {
      background: #2563eb;
      color: #fff;
    }

    @media (max-width: 600px) {
      .subtitulo-app, .divisor-vertical { display: none; }
      .rotulo-status { display: none; }
      .icone-status { display: inline-flex; }
      .pilula-status { padding: 4px 10px; }
    }
  `,
})
export class App {
  protected auth = inject(AuthService);
  protected rede = inject(RedeService);
  protected sync = inject(SyncService);
  protected demo = inject(DemoService);
  private dialog = inject(MatDialog);
  protected atualizacaoDisponivel = signal(false);

  constructor() {
    // Pede ao navegador para não apagar os dados offline (importante no iOS).
    navigator.storage?.persist?.().catch(() => undefined);
    // Permissões podem ter mudado desde o login (ex.: liberada a revisão de formulários).
    this.auth.atualizarUsuario();

    const sw = inject(SwUpdate);
    if (sw.isEnabled) {
      sw.versionUpdates.subscribe((e) => {
        if (e.type === 'VERSION_READY') this.atualizacaoDisponivel.set(true);
      });
    }
  }

  protected iconeStatus(): string {
    if (this.sync.rejeitadas() > 0) return 'sync_problem';
    if (!this.rede.online()) return 'cloud_off';
    if (this.sync.pendentes() > 0) return 'cloud_sync';
    return 'cloud_done';
  }

  protected rotuloStatus(): string {
    const p = this.sync.pendentes();
    if (this.sync.rejeitadas() > 0) return `${this.sync.rejeitadas()} recusada(s)`;
    if (!this.rede.online()) return p ? `Offline · ${p} pendente(s)` : 'Offline';
    if (p) return `Enviando ${p}…`;
    return 'Sincronizado';
  }

  protected dicaStatus(): string {
    if (this.sync.rejeitadas() > 0) return 'O servidor recusou alterações. Abra a auditoria para resolver.';
    if (!this.rede.online()) return 'Sem conexão. Tudo fica salvo no aparelho e é enviado quando a rede voltar.';
    return 'Toque para sincronizar agora';
  }

  protected trocarSenha(): void {
    this.dialog.open(TrocarSenhaDialog, { width: '400px' });
  }

  protected async sair(): Promise<void> {
    if (this.sync.pendentes() > 0 && !confirm('Há alterações ainda não enviadas. Elas ficam no aparelho até você entrar de novo. Sair mesmo assim?')) {
      return;
    }
    await this.auth.sair();
  }

  protected recarregar(): void {
    document.location.reload();
  }
}
