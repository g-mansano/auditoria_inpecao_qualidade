import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Operacao } from '../core/db';
import { mensagemErro } from '../core/interceptors';
import { AuditoriaDetalhe, Cabecalho, Conformidade, Item, Secao, opcoesDe, siglaDe } from '../core/modelos';
import { RedeService } from '../core/rede.service';
import { RepositorioService } from '../core/repositorio.service';
import { SyncService } from '../core/sync.service';
import { agruparConsecutivos, vazioParaNull } from '../core/util';
import { CabecalhoCampos } from './cabecalho-campos';
import { RelatorioImpressao } from './relatorio-impressao';
import { RemovidosDialog } from './removidos-dialog';

type Filtro = 'TODOS' | 'PENDENTES' | 'ALTERADAS' | Conformidade;

@Component({
  selector: 'app-auditoria',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    CabecalhoCampos,
    MatButtonToggleModule,
    RelatorioImpressao,
  ],
  template: `
    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else if (!a()) {
      <div class="aviso erro">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">Esta auditoria não está salva no aparelho e não há conexão com o servidor.</span>
        <button mat-button routerLink="/">Voltar</button>
      </div>
    } @else {
      @let aud = a()!;
      <div class="nao-imprimir">
      <div class="linha topo">
        <button mat-icon-button [routerLink]="['/formularios', aud.modelo.id]" class="btn-voltar" aria-label="Voltar">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="titulo">
          <h1>{{ aud.modelo.rotuloAuditoria ?? 'Auditoria Interna' }} Nº {{ aud.numero }}</h1>
          <div class="subtitulo">{{ aud.modelo.titulo }}</div>
        </div>
        <span class="espaco"></span>
        <button mat-icon-button [matMenuTriggerFor]="menu" class="btn-menu" aria-label="Mais opções">
          <mat-icon>more_vert</mat-icon>
        </button>
        <mat-menu #menu="matMenu">
          <button mat-menu-item [routerLink]="['/auditorias', aud.id, 'historico']" [disabled]="!rede.online()">
            <mat-icon>history</mat-icon>Histórico de alterações
          </button>
          <button mat-menu-item (click)="imprimir()">
            <mat-icon>print</mat-icon>Imprimir com fotos (Ctrl+P)
          </button>
          @if (podeExcluir()) {
            <button mat-menu-item (click)="excluir()" [disabled]="!rede.online()" class="perigo">
              <mat-icon>delete</mat-icon>Excluir auditoria
            </button>
          }
          @if (aud.status === 'CONCLUIDA' && auth.admin()) {
            <button mat-menu-item (click)="reabrir()" [disabled]="!rede.online()">
              <mat-icon>lock_open</mat-icon>Reabrir auditoria
            </button>
          }
        </mat-menu>
      </div>

      @if (doCache()) {
        <div class="aviso atencao">
          <mat-icon>cloud_off</mat-icon>
          <span class="texto">Modo offline. Exibindo os dados salvos neste aparelho.</span>
        </div>
      }

      @if (rejeitadas().length) {
        <div class="aviso erro">
          <mat-icon>sync_problem</mat-icon>
          <span class="texto">
            O servidor recusou {{ rejeitadas().length }} alteração(ões) feita(s) neste aparelho:
            <b>{{ rejeitadas()[0].erro }}</b>
            Os dados continuam salvos aqui.
          </span>
          <button mat-stroked-button (click)="tentarDeNovo()">Tentar de novo</button>
          <button mat-button (click)="descartar()">Descartar</button>
        </div>
      }

      <!-- Revisão do formulário aplicada a esta auditoria -->
      @if (alteradas().length && aud.status === 'EM_ANDAMENTO') {
        <div class="aviso atencao banner-status revisado">
          <mat-icon>published_with_changes</mat-icon>
          <span class="texto">
            O formulário mudou para a <b>Rev. {{ revisaoNova() }}</b>: {{ alteradas().length }} pergunta(s) já respondida(s)
            foram alteradas e precisam ser respondidas de novo.
          </span>
          <button mat-stroked-button (click)="filtro.set('ALTERADAS')">Ver alteradas</button>
        </div>
      }
      @if (aud.removidos?.length && aud.status === 'EM_ANDAMENTO') {
        <div class="aviso info removidos">
          <mat-icon>playlist_remove</mat-icon>
          <span class="texto">{{ aud.removidos!.length }} pergunta(s) com resposta saíram do formulário numa revisão.</span>
          <button mat-button (click)="verRemovidos()">Ver respostas e fotos</button>
        </div>
      }

      <!-- Situação da edição exclusiva -->
      @if (aud.status === 'CONCLUIDA') {
        <div class="aviso info banner-status concluido">
          <mat-icon>verified</mat-icon>
          <span class="texto">Auditoria concluída em {{ aud.concluidoEm | date: 'dd/MM/yyyy HH:mm' }}. Modo somente leitura.</span>
        </div>
      } @else if (souEditor()) {
        <div class="aviso info banner-status editando">
          <mat-icon>edit_note</mat-icon>
          <span class="texto">Você está editando neste aparelho. Funciona sem internet; os dados sincronizam automaticamente.</span>
          <div class="acoes-status">
            <button mat-stroked-button (click)="liberar()" [disabled]="!rede.online() || ocupado()">Liberar edição</button>
            <button mat-flat-button class="btn-concluir" (click)="concluir()" [disabled]="!rede.online() || ocupado()">Concluir auditoria</button>
          </div>
        </div>
      } @else if (aud.editor) {
        <div class="aviso atencao banner-status bloqueado">
          <mat-icon>lock</mat-icon>
          <span class="texto">
            @if (aud.editor.usuarioId === auth.usuario()?.id) {
              Você está editando esta auditoria em outro aparelho desde {{ aud.editor.desde | date: 'dd/MM HH:mm' }}.
            } @else {
              Em edição por <b>{{ aud.editor.nome }}</b> desde {{ aud.editor.desde | date: 'dd/MM HH:mm' }}. Somente leitura.
            }
          </span>
          @if (auth.admin() || aud.editor.usuarioId === auth.usuario()?.id) {
            <button mat-flat-button (click)="assumir()" [disabled]="!rede.online() || ocupado()">Assumir edição aqui</button>
          }
        </div>
      } @else if (auth.podeEditar()) {
        <div class="aviso atencao banner-status livre">
          <mat-icon>lock_open</mat-icon>
          <span class="texto">Ninguém está editando esta auditoria. Faça o check-out para preencher os itens neste aparelho.</span>
          <button mat-flat-button (click)="editar()" [disabled]="!rede.online() || ocupado()">Iniciar edição</button>
        </div>
      }
      @if (ocupado()) {
        <mat-progress-bar mode="indeterminate" class="progresso-ocupado" />
      }

      <!-- Cabeçalho da Auditoria -->
      <div class="cartao cartao-detalhe">
        @if (editandoCabecalho()) {
          <app-cabecalho-campos [(valor)]="cab" [rotuloNumero]="aud.modelo.rotuloAuditoria" />
          <div class="linha fim">
            <button mat-button (click)="editandoCabecalho.set(false)">Cancelar</button>
            <button mat-flat-button (click)="salvarCabecalho()">Salvar cabeçalho</button>
          </div>
        } @else {
          <div class="linha cabecalho-linha">
            <div class="info-auditoria">
              <div class="formulario-tag">{{ aud.formulario }}</div>
              <div class="linha meta-linha">
                <span class="meta-item">
                  <mat-icon inline>calendar_today</mat-icon>
                  <b>Data:</b> {{ aud.dataInicio | date: 'dd/MM/yyyy' }}
                  @if (aud.dataFim && aud.dataFim !== aud.dataInicio) {
                    a {{ aud.dataFim | date: 'dd/MM/yyyy' }}
                  }
                </span>
                <span class="meta-item">
                  <mat-icon inline>people_outline</mat-icon>
                  <b>Auditores:</b>
                  <span class="auditores-nomes">{{ aud.auditores }}</span>
                </span>
              </div>
            </div>
            <span class="espaco"></span>
            @if (podeEditar()) {
              <button mat-icon-button (click)="abrirCabecalho()" class="btn-editar-cabecalho" aria-label="Editar cabeçalho">
                <mat-icon>edit</mat-icon>
              </button>
            }
          </div>
          @if (aud.escopo) {
            <div class="escopo-box">
              <span class="escopo-rotulo">Escopo da auditoria:</span>
              <div class="escopo-texto">{{ aud.escopo }}</div>
            </div>
          }
        }
      </div>

      <!-- Progresso e filtros -->
      <div class="cartao cartao-progresso">
        @if (temCampo()) {
          <mat-button-toggle-group class="secoes" [value]="secao()" (change)="secao.set($event.value)" hideSingleSelectionIndicator>
            <mat-button-toggle value="CHECKLIST">Checklist de Itens</mat-button-toggle>
            <mat-button-toggle value="CAMPO">Roteiro de Campo</mat-button-toggle>
          </mat-button-toggle-group>
        }

        <div class="linha linha-metricas">
          <div class="total-respondidos">
            <span class="destaque-respondidos">{{ respondidos() }}</span> de {{ aud.itens.length }} itens respondidos
            <span class="porcentagem">({{ ((respondidos() / aud.itens.length) * 100).toFixed(0) }}%)</span>
          </div>
          <span class="espaco"></span>
          <div class="resumo-selos">
            @for (c of conformidades(); track c.valor) {
              <span class="selo" [class]="c.valor">{{ c.curto }} {{ contagem()[c.valor] }}</span>
            }
          </div>
        </div>

        <mat-progress-bar mode="determinate" [value]="(respondidos() / aud.itens.length) * 100" class="progresso" />

        <div class="linha linha-filtros">
          <mat-chip-listbox [value]="filtro()" (change)="filtro.set($event.value ?? 'TODOS')" aria-label="Filtro de itens">
            <mat-chip-option value="TODOS">Todos ({{ aud.itens.length }})</mat-chip-option>
            <mat-chip-option value="PENDENTES">Pendentes ({{ aud.itens.length - respondidos() }})</mat-chip-option>
            @if (alteradas().length) {
              <mat-chip-option value="ALTERADAS">Alteradas ({{ alteradas().length }})</mat-chip-option>
            }
            @for (c of conformidades(); track c.valor) {
              <mat-chip-option [value]="c.valor">{{ c.curto }} ({{ contagem()[c.valor] }})</mat-chip-option>
            }
          </mat-chip-listbox>
        </div>

        <mat-form-field class="cheio busca" appearance="outline" subscriptSizing="dynamic">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Buscar por cláusula, texto, evidência, setor, RNC…" [ngModel]="busca()" (ngModelChange)="busca.set($event)" />
          @if (busca()) {
            <button matSuffix mat-icon-button aria-label="Limpar busca" (click)="busca.set('')">
              <mat-icon>close</mat-icon>
            </button>
          }
        </mat-form-field>
      </div>

      <!-- Itens por grupo (capítulo, etapa ou setor) -->
      @for (g of grupos(); track g.nome) {
        <div class="secao-grupo">
          <div class="cabecalho-grupo">
            <span class="linha-decorativa"></span>
            <h2 class="capitulo">{{ g.nome }}</h2>
            <span class="qtd-itens">{{ g.itens.length }} item(ns)</span>
          </div>

          <div class="cartao lista">
            @for (i of g.itens; track i.id) {
              <a class="item" [routerLink]="['/auditorias', aud.id, 'itens', i.numero]">
                <div class="num-coluna">
                  <span class="num-badge">{{ i.numero }}</span>
                </div>
                <div class="corpo">
                  @if (i.clausula || i.responsavel) {
                    <span class="clausula">{{ i.clausula ?? i.responsavel }}</span>
                  }
                  <span class="pergunta">{{ i.pergunta }}</span>
                  @if (i.rnc) {
                    <span class="rnc-tag"><mat-icon inline>flag</mat-icon> {{ i.rnc }}</span>
                  }
                  @if (i.alteradaNaRevisao) {
                    <span class="alterada-tag"><mat-icon inline>published_with_changes</mat-icon> Alterada na Rev. {{ i.alteradaNaRevisao }} · responda de novo</span>
                  }
                </div>
                @if (i.anexos.length) {
                  <span class="anexos" [class.pendente]="temAnexoPendente(i)" [title]="i.anexos.length + ' anexo(s)'">
                    <mat-icon inline>attach_file</mat-icon> {{ i.anexos.length }}
                  </span>
                }
                <span class="selo" [class]="i.conformidade ?? 'vazio'">{{ i.conformidade ? sigla(i.conformidade) : '—' }}</span>
                <mat-icon class="seta-item">chevron_right</mat-icon>
              </a>
            }
          </div>
        </div>
      } @empty {
        <div class="cartao mudo estado-vazio-itens">Nenhum item encontrado para este filtro ou busca.</div>
      }
      </div>
      <app-relatorio-impressao [auditoria]="aud" [imagens]="imagens()" />
    }
  `,
  styles: `
    .topo {
      gap: 12px;
      flex-wrap: nowrap;
      margin-bottom: 16px;
      align-items: center;
    }
    .btn-voltar {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      color: #475467;
    }
    .btn-menu {
      color: #475467;
    }
    .titulo { min-width: 0; }
    .titulo h1 {
      margin: 0;
      font-size: 20px;
      color: var(--cor-primaria);
      line-height: 1.2;
    }
    .subtitulo {
      font-size: 13px;
      color: #64748b;
      margin-top: 2px;
    }
    .espaco { flex: 1; }
    .fim { justify-content: flex-end; gap: 8px; margin-top: 12px; }

    .banner-status {
      border-left-width: 4px;
      padding: 14px 18px;
    }
    .banner-status.concluido { border-left-color: #16a34a; background: #f0fdf4; color: #166534; }
    .banner-status.editando { border-left-color: var(--cor-secundaria); background: #f0fdfa; color: #115e59; }
    .banner-status.bloqueado { border-left-color: #f59e0b; background: #fffbeb; color: #92400e; }
    .banner-status.livre { border-left-color: var(--cor-primaria); background: #f8fafc; color: #1e293b; }

    .acoes-status {
      display: flex;
      gap: 8px;
      margin-left: auto;
    }
    .btn-concluir {
      background: #15803d !important;
      color: #ffffff !important;
    }
    .progresso-ocupado { margin-bottom: 12px; }

    .cartao-detalhe { padding: 18px 20px; }
    .cabecalho-linha { align-items: flex-start; }
    .info-auditoria { flex: 1; min-width: 0; }
    .formulario-tag {
      font-size: 13px;
      font-weight: 700;
      color: var(--cor-primaria);
      margin-bottom: 6px;
    }
    .meta-linha {
      gap: 16px;
      font-size: 13px;
      color: #334155;
    }
    .meta-item {
      display: inline-flex;
      align-items: flex-start;
      gap: 4px;
      line-height: 18px;
    }
    .meta-item mat-icon { font-size: 16px; width: 16px; height: 16px; color: #64748b; margin-top: 1px; flex-shrink: 0; }
    /* Um auditor por linha, alinhados ao lado do rótulo. */
    .auditores-nomes { white-space: pre-line; }
    .btn-editar-cabecalho { color: #64748b; }

    .escopo-box {
      margin-top: 12px;
      padding: 10px 14px;
      border-radius: 8px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      font-size: 13px;
    }
    .escopo-rotulo {
      font-weight: 600;
      color: #475467;
      display: block;
      margin-bottom: 2px;
    }
    .escopo-texto {
      color: #1e293b;
      white-space: pre-line;
      line-height: 1.4;
    }

    .cartao-progresso { padding: 18px 20px; }
    .secoes { margin-bottom: 16px; width: 100%; }
    .linha-metricas {
      justify-content: space-between;
      margin-bottom: 8px;
      align-items: baseline;
    }
    .total-respondidos {
      font-size: 14px;
      color: #334155;
      font-weight: 500;
    }
    .destaque-respondidos {
      font-size: 18px;
      font-weight: 700;
      color: var(--cor-primaria);
    }
    .porcentagem {
      color: var(--cor-secundaria);
      font-weight: 700;
      margin-left: 4px;
    }
    .resumo-selos {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .progresso {
      margin: 4px 0 16px;
      height: 8px !important;
      border-radius: 4px;
    }
    .linha-filtros { margin-bottom: 12px; }
    .busca { width: 100%; }

    .secao-grupo { margin-top: 24px; }
    .cabecalho-grupo {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 4px 10px;
    }
    .linha-decorativa {
      width: 4px;
      height: 18px;
      border-radius: 2px;
      background: var(--cor-secundaria);
      display: inline-block;
    }
    .capitulo {
      font-size: 15px;
      font-weight: 700;
      color: #1e293b;
      margin: 0;
      letter-spacing: -0.01em;
    }
    .qtd-itens {
      font-size: 12px;
      color: #64748b;
      margin-left: auto;
    }

    .lista {
      padding: 0;
      overflow: hidden;
    }
    .item {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 18px;
      min-height: 60px;
      color: inherit;
      text-decoration: none;
      border-bottom: 1px solid #f1f5f9;
      transition: background 0.15s ease;
    }
    .item:last-child { border-bottom: 0; }
    .item:hover { background: #f8fafc; }
    .item:active { background: #f1f5f9; }

    .num-coluna { flex-shrink: 0; }
    .num-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #f1f5f9;
      font-size: 13px;
      font-weight: 700;
      color: #475467;
    }

    .corpo {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
      gap: 2px;
    }
    .clausula {
      font-size: 12px;
      color: var(--cor-secundaria);
      font-weight: 700;
      letter-spacing: 0.01em;
    }
    .pergunta {
      font-size: 14px;
      color: #1e293b;
      line-height: 1.35;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .rnc-tag {
      font-size: 11px;
      font-weight: 700;
      color: #d92d20;
      background: #fef3f2;
      border: 1px solid #fecdca;
      padding: 2px 6px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      gap: 3px;
      margin-top: 4px;
      width: fit-content;
    }
    .rnc-tag mat-icon { font-size: 13px; width: 13px; height: 13px; }
    .alterada-tag {
      font-size: 11px;
      font-weight: 700;
      color: #b45309;
      background: #fffbeb;
      border: 1px solid #fde68a;
      padding: 2px 6px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      gap: 3px;
      margin-top: 4px;
      width: fit-content;
    }
    .alterada-tag mat-icon { font-size: 13px; width: 13px; height: 13px; }
    .banner-status.revisado { border-left-color: #f59e0b; }

    .anexos {
      color: #64748b;
      font-size: 13px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 4px 8px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
    }
    .anexos.pendente {
      color: #b45309;
      background: #fffbeb;
      border-color: #fde68a;
    }
    .seta-item {
      color: #cbd5e1;
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .estado-vazio-itens {
      padding: 32px 20px;
      text-align: center;
    }
    .perigo { color: var(--cor-nc); }
  `,
})
export class AuditoriaPagina {
  readonly id = input.required<string>();

  protected repo = inject(RepositorioService);
  protected auth = inject(AuthService);
  protected rede = inject(RedeService);
  private sync = inject(SyncService);
  private snack = inject(MatSnackBar);

  protected conformidades = computed(() => opcoesDe(this.a()?.modelo));
  protected sigla = (c: Conformidade) => siglaDe(this.a()?.modelo, c);
  protected a = signal<AuditoriaDetalhe | null>(null);
  /** URLs locais das fotos anexadas, usadas na impressão (anexoId -> blob URL). */
  protected imagens = signal<Record<string, string>>({});
  protected carregando = signal(true);
  protected doCache = signal(false);
  protected ocupado = signal(false);
  protected ops = signal<Operacao[]>([]);
  protected filtro = signal<Filtro>('TODOS');
  protected busca = signal('');
  protected editandoCabecalho = signal(false);
  protected cab = signal<Cabecalho>({ numero: '', dataInicio: '', dataFim: null, auditores: '', escopo: null });

  protected souEditor = computed(() => this.repo.souEditor(this.a()));
  protected podeEditar = computed(() => this.souEditor() && this.a()?.status === 'EM_ANDAMENTO');
  protected rejeitadas = computed(() => this.ops().filter((o) => o.estado === 'rejeitada'));
  protected respondidos = computed(() => this.a()?.itens.filter((i) => i.conformidade).length ?? 0);
  protected contagem = computed(() => {
    const c: Record<Conformidade, number> = { C: 0, NC: 0, OM: 0, NA: 0 };
    this.a()?.itens.forEach((i) => i.conformidade && c[i.conformidade]++);
    return c;
  });
  protected temCampo = computed(() => this.a()?.itens.some((i) => i.secao === 'CAMPO') ?? false);
  /** Itens cuja pergunta mudou numa revisão depois de respondidos. */
  protected alteradas = computed(() => this.a()?.itens.filter((i) => i.alteradaNaRevisao) ?? []);
  protected revisaoNova = computed(() => this.alteradas()[0]?.alteradaNaRevisao ?? null);
  protected removidosNaoVistos = computed(() => this.a()?.removidos?.filter((r) => !r.visto).length ?? 0);
  private dialog = inject(MatDialog);
  private janelaAberta = false;
  protected secao = signal<Secao>('CHECKLIST');
  protected podeExcluir = computed(() => {
    const a = this.a();
    const u = this.auth.usuario();
    return !!a && !!u && a.status === 'EM_ANDAMENTO' && (u.papel === 'ADMIN' || a.criadoPor === u.username);
  });
  protected grupos = computed(() => {
    const f = this.filtro();
    const q = this.busca().trim().toLowerCase();
    const secao = this.temCampo() ? this.secao() : 'CHECKLIST';
    const itens = (this.a()?.itens ?? []).filter((i) => {
      if ((i.secao ?? 'CHECKLIST') !== secao) return false;
      if (f === 'PENDENTES' && i.conformidade) return false;
      if (f === 'ALTERADAS' && !i.alteradaNaRevisao) return false;
      if (f !== 'TODOS' && f !== 'PENDENTES' && f !== 'ALTERADAS' && i.conformidade !== f) return false;
      if (!q) return true;
      return [i.clausula, i.grupo, i.pergunta, i.processo, i.documentos, i.orientacao, i.responsavel, i.evidencias, i.rnc, String(i.numero)]
        .some((t) => t?.toLowerCase().includes(q));
    });
    return agruparConsecutivos(itens);
  });

  private router = inject(Router);

  constructor() {
    effect(() => {
      if (this.sync.confirmacoes()) untracked(() => this.recarregarLocal());
    });
    // Lembra a seção (checklist / roteiro de campo) ao voltar do editor de item.
    effect(() => {
      const s = this.secao();
      try {
        sessionStorage.setItem(`checklist.secao.${untracked(() => this.id())}`, s);
      } catch {
        /* sem armazenamento */
      }
    });
    inject(DestroyRef).onDestroy(() => Object.values(this.imagens()).forEach((u) => URL.revokeObjectURL(u)));
  }

  ngOnInit(): void {
    try {
      const s = sessionStorage.getItem(`checklist.secao.${this.id()}`);
      if (s === 'CAMPO' || s === 'CHECKLIST') this.secao.set(s);
    } catch {
      /* sem armazenamento */
    }
    this.carregar();
  }

  private async recarregarLocal(): Promise<void> {
    const local = await this.repo.local(+this.id());
    if (local) this.a.set(local);
    this.ops.set(await this.repo.operacoes(+this.id()));
    this.carregarImagens();
  }

  private async carregar(): Promise<void> {
    const r = await this.repo.abrir(+this.id());
    this.a.set(r.dados);
    this.doCache.set(r.doCache);
    this.ops.set(await this.repo.operacoes(+this.id()));
    this.carregando.set(false);
    this.carregarImagens();
    this.avisarRemovidos();
  }

  // ---------------------------------------------------------- impressão

  /**
   * Baixa as fotos em segundo plano logo ao abrir a auditoria, para que o Ctrl+P já
   * saia com as imagens (o navegador não espera downloads depois que a impressão começa).
   */
  private async carregarImagens(): Promise<void> {
    const anexos = (this.a()?.itens ?? []).flatMap((i) => i.anexos)
      .filter((x) => x.contentType.startsWith('image/') && x.contentType !== 'image/heic');
    for (const an of anexos) {
      if (this.imagens()[an.id]) continue;
      try {
        const url = URL.createObjectURL(await this.repo.conteudoAnexo(an));
        this.imagens.update((m) => ({ ...m, [an.id]: url }));
      } catch {
        /* offline e ainda não baixada: sai só o nome do arquivo */
      }
    }
  }

  protected async imprimir(): Promise<void> {
    this.ocupado.set(true);
    await this.carregarImagens();
    this.ocupado.set(false);
    // Dá um quadro para as imagens novas entrarem no DOM antes de abrir a impressão.
    setTimeout(() => window.print(), 300);
  }

  // ---------------------------------------------------------- exclusão

  protected async excluir(): Promise<void> {
    const a = this.a()!;
    if (!confirm(`Excluir a auditoria Nº ${a.numero}? Ela sai da lista (o registro fica guardado no histórico do servidor).`)) {
      return;
    }
    this.ocupado.set(true);
    try {
      await this.repo.excluir(a.id);
      this.snack.open('Auditoria excluída.', undefined, { duration: 3000 });
      await this.router.navigate(['/formularios', a.modelo.id], { replaceUrl: true });
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  protected temAnexoPendente(i: Item): boolean {
    return i.anexos.some((x) => x.pendente);
  }

  // ---------------------------------------------------------- edição exclusiva

  protected editar(): Promise<void> {
    return this.acao(() => this.repo.checkout(+this.id()), 'Auditoria em edição neste aparelho.');
  }

  protected assumir(): Promise<void> {
    const aud = this.a()!;
    const msg =
      aud.editor?.usuarioId === this.auth.usuario()?.id
        ? 'Assumir a edição neste aparelho? Alterações não enviadas do outro aparelho serão recusadas.'
        : `Retirar a edição de ${aud.editor?.nome}? Alterações que ainda estão só no aparelho dele(a) serão recusadas pelo servidor.`;
    if (!confirm(msg)) return Promise.resolve();
    return this.acao(() => this.repo.checkout(+this.id(), true), 'Edição assumida.');
  }

  protected liberar(): Promise<void> {
    return this.acao(() => this.repo.checkin(+this.id()), 'Edição liberada para outros inspetores.');
  }

  protected concluir(): Promise<void> {
    if (!confirm('Concluir a auditoria? Depois disso ela fica somente leitura.')) return Promise.resolve();
    return this.acao(() => this.repo.concluir(+this.id()), 'Auditoria concluída.');
  }

  protected reabrir(): Promise<void> {
    if (!confirm('Reabrir esta auditoria concluída? A reabertura fica registrada no histórico.')) {
      return Promise.resolve();
    }
    return this.acao(() => this.repo.reabrir(+this.id()), 'Auditoria reaberta.');
  }

  private async acao(fn: () => Promise<AuditoriaDetalhe>, sucesso: string): Promise<void> {
    this.ocupado.set(true);
    try {
      this.a.set(await fn());
      this.ops.set(await this.repo.operacoes(+this.id()));
      this.snack.open(sucesso, undefined, { duration: 3000 });
      this.avisarRemovidos();
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
      await this.carregar();
    } finally {
      this.ocupado.set(false);
    }
  }

  // ---------------------------------------------------------- cabeçalho

  protected abrirCabecalho(): void {
    const a = this.a()!;
    this.cab.set({ numero: a.numero, dataInicio: a.dataInicio, dataFim: a.dataFim, auditores: a.auditores, escopo: a.escopo });
    this.editandoCabecalho.set(true);
  }

  protected async salvarCabecalho(): Promise<void> {
    const c = this.cab();
    if (!c.numero.trim() || !c.dataInicio || !c.auditores.trim()) {
      this.snack.open('Preencha número, data inicial e auditores.', 'OK', { duration: 5000 });
      return;
    }
    await this.repo.salvarCabecalho(+this.id(), {
      numero: c.numero.trim(),
      dataInicio: c.dataInicio,
      dataFim: vazioParaNull(c.dataFim),
      auditores: c.auditores.trim(),
      escopo: vazioParaNull(c.escopo),
    });
    this.a.set((await this.repo.local(+this.id())) ?? null);
    this.editandoCabecalho.set(false);
  }

  // ---------------------------------------------------------- fila recusada

  protected async tentarDeNovo(): Promise<void> {
    await this.repo.tentarDeNovo(+this.id());
    await this.carregar();
  }

  protected async descartar(): Promise<void> {
    if (!confirm('Descartar as alterações recusadas? Elas serão apagadas deste aparelho.')) return;
    await this.repo.descartarRejeitadas(+this.id());
    await this.sync.atualizarContagem();
    await this.carregar();
  }

  // ---------------------------------------------------------- revisão do formulário

  /** Ao abrir: quem está editando vê uma vez as perguntas removidas que tinham resposta. */
  private avisarRemovidos(): void {
    if (this.janelaAberta || !this.podeEditar() || !this.removidosNaoVistos()) return;
    this.verRemovidos();
  }

  protected verRemovidos(): void {
    const a = this.a();
    if (!a?.removidos?.length || this.janelaAberta) return;
    this.janelaAberta = true;
    this.dialog
      .open(RemovidosDialog, { data: { removidos: a.removidos, modelo: a.modelo }, width: '640px', maxWidth: '96vw' })
      .afterClosed()
      .subscribe(async () => {
        this.janelaAberta = false;
        if (!this.removidosNaoVistos() || !this.rede.online() || !this.souEditor()) return;
        try {
          this.a.set(await this.repo.marcarRemovidosVistos(+this.id()));
        } catch {
          /* tenta de novo na próxima abertura */
        }
      });
  }
}
