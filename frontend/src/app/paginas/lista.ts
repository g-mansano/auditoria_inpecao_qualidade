import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AuditoriaResumo, Formulario, NivelAcesso, opcoesDe } from '../core/modelos';
import { RedeService } from '../core/rede.service';
import { RepositorioService } from '../core/repositorio.service';

@Component({
  selector: 'app-lista',
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="linha topo">
      <button mat-icon-button routerLink="/" class="btn-voltar" aria-label="Voltar aos formulários">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <div class="titulos-topo">
        <h1>{{ formulario()?.nome ?? 'Auditorias' }}</h1>
        @if (formulario(); as f) {
          <div class="subtitulo">{{ f.titulo }} · {{ f.identificacao }}</div>
        }
      </div>
      <span class="espaco"></span>
      <mat-button-toggle-group [value]="filtro()" (change)="filtro.set($event.value)" class="grupo-filtro" hideSingleSelectionIndicator>
        <mat-button-toggle value="EM_ANDAMENTO">Em andamento</mat-button-toggle>
        <mat-button-toggle value="CONCLUIDA">Concluídas</mat-button-toggle>
        <mat-button-toggle value="TODAS">Todas</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    @if (auth.podeRevisar()) {
      <div class="linha revisao">
        <mat-icon class="icone-revisao">fact_check</mat-icon>
        <span class="mudo rotulo-revisao">Revisão do formulário</span>
        <span class="espaco"></span>
        <a mat-button [routerLink]="['/formularios', tid(), 'revisoes']"><mat-icon>history</mat-icon> Histórico</a>
        <button mat-stroked-button [routerLink]="['/formularios', tid(), 'revisao']" [disabled]="!rede.online()">
          <mat-icon>edit</mat-icon> Editar revisão atual
        </button>
        <button mat-flat-button [routerLink]="['/formularios', tid(), 'revisao']" [queryParams]="{ nova: 1 }"
          [disabled]="!rede.online()">
          <mat-icon>note_add</mat-icon> Abrir nova revisão
        </button>
      </div>
    }

    @if (doCache()) {
      <div class="aviso atencao">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">Modo offline. Exibindo a última lista salva no aparelho.</span>
      </div>
    }

    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      @for (a of filtradas(); track a.id) {
        <a class="cartao auditoria" [routerLink]="['/auditorias', a.id]">
          <div class="linha linha-cabecalho">
            <div class="numero">Nº {{ a.numero }}</div>
            <div class="datas">
              <mat-icon inline>calendar_today</mat-icon>
              {{ a.dataInicio | date: 'dd/MM/yyyy' }}
              @if (a.dataFim && a.dataFim !== a.dataInicio) {
                a {{ a.dataFim | date: 'dd/MM/yyyy' }}
              }
            </div>
            <span class="espaco"></span>
            @if (a.status === 'CONCLUIDA') {
              <span class="etiqueta concluida"><mat-icon inline>verified</mat-icon> Concluída</span>
            } @else if (a.editor) {
              <span class="etiqueta" [class.minha]="repo.souEditor(a)">
                <mat-icon inline>edit</mat-icon>
                {{ repo.souEditor(a) ? 'Com você' : a.editor.nome }}
              </span>
            } @else {
              <span class="etiqueta livre"><mat-icon inline>lock_open</mat-icon> Livre</span>
            }
          </div>

          <div class="auditores">
            <mat-icon inline>people_outline</mat-icon>
            <span>{{ a.auditores }}</span>
          </div>

          <div class="bloco-progresso">
            <div class="linha info-progresso">
              <span class="mudo">{{ a.respondidos }} de {{ a.totalItens }} itens respondidos</span>
              <span class="percentual">{{ pct(a).toFixed(0) }}%</span>
            </div>
            <mat-progress-bar mode="determinate" [value]="pct(a)" class="barra-progresso" />
          </div>

          <div class="linha contagem">
            @for (c of conformidades(); track c.valor) {
              @if (a.contagem[c.valor]) {
                <span class="selo" [class]="c.valor">{{ c.curto }} {{ a.contagem[c.valor] }}</span>
              }
            }
            <span class="espaco"></span>
            <span class="abrir-link">Abrir auditoria <mat-icon inline>chevron_right</mat-icon></span>
          </div>
        </a>
      } @empty {
        <div class="cartao estado-vazio">
          <mat-icon>assignment_late</mat-icon>
          <div class="texto-vazio">Nenhuma auditoria encontrada para este filtro.</div>
        </div>
      }
    }

    @if (auth.podeEditar() && podePreencher()) {
      <button
        mat-fab
        extended
        class="fab"
        [routerLink]="['/formularios', tid(), 'nova']"
        [disabled]="!rede.online()"
        [attr.title]="rede.online() ? null : 'Criar auditoria exige conexão'"
      >
        <mat-icon>add</mat-icon> Nova auditoria
      </button>
    }
  `,
  styles: `
    .topo {
      margin-bottom: 20px;
      gap: 12px;
      align-items: center;
    }
    .btn-voltar {
      color: #475467;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
    }
    /* Divide a linha com o botão voltar; o filtro desce para a linha de baixo no celular. */
    .titulos-topo { flex: 1 1 220px; min-width: 0; }
    .titulos-topo h1 {
      margin: 0;
      font-size: 22px;
      line-height: 1.2;
    }
    .subtitulo {
      font-size: 13px;
      color: #64748b;
      margin-top: 2px;
    }
    .espaco { flex: 1; }

    .revisao {
      gap: 8px;
      margin: -8px 0 16px;
      padding: 10px 14px;
      border: 1px dashed #cbd5e1;
      border-radius: 12px;
      background: #ffffff;
    }
    .icone-revisao { color: var(--cor-secundaria); }
    .rotulo-revisao { font-weight: 600; }

    .grupo-filtro {
      border-radius: 12px;
      border: 1px solid #e2e8f0;
      overflow: hidden;
      background: #ffffff;
    }

    .auditoria {
      display: block;
      color: inherit;
      text-decoration: none;
      padding: 18px 20px;
      cursor: pointer;
    }
    .auditoria:hover {
      border-color: #cbd5e1;
      box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
    }
    .auditoria:active {
      transform: scale(0.995);
      background: #f8fafc;
    }

    .linha-cabecalho {
      gap: 10px;
      margin-bottom: 8px;
    }
    .numero {
      font-size: 18px;
      font-weight: 700;
      color: var(--cor-primaria);
    }
    .datas {
      font-size: 13px;
      color: #64748b;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .datas mat-icon { font-size: 16px; width: 16px; height: 16px; }

    .auditores {
      font-size: 13px;
      color: #334155;
      margin: 4px 0 14px;
      display: flex;
      align-items: center;
      gap: 6px;
      white-space: pre-line;
    }
    .auditores mat-icon {
      font-size: 17px;
      width: 17px;
      height: 17px;
      color: #64748b;
      flex-shrink: 0;
    }

    .bloco-progresso {
      margin-bottom: 12px;
    }
    .info-progresso {
      justify-content: space-between;
      margin-bottom: 6px;
      font-size: 12px;
    }
    .percentual {
      font-weight: 700;
      color: var(--cor-secundaria);
    }
    .barra-progresso {
      height: 6px !important;
      border-radius: 3px;
    }

    .contagem {
      gap: 6px;
      padding-top: 10px;
      border-top: 1px solid #f1f5f9;
    }
    .abrir-link {
      font-size: 13px;
      font-weight: 600;
      color: var(--cor-primaria);
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }

    .etiqueta {
      font-size: 12px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 9999px;
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #b45309;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .etiqueta.minha {
      background: #f0f7ff;
      border-color: #bae6fd;
      color: #0369a1;
    }
    .etiqueta.livre {
      background: #f8fafc;
      border-color: #e2e8f0;
      color: #475467;
    }
    .etiqueta.concluida {
      background: #f0fdf4;
      border-color: #bbf7d0;
      color: #15803d;
    }

    .estado-vazio {
      text-align: center;
      padding: 48px 20px;
      color: #94a3b8;
    }
    .estado-vazio mat-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      margin-bottom: 12px;
    }
    .texto-vazio {
      font-size: 15px;
      font-weight: 500;
    }
  `,
})
export class Lista {
  /** Id do formulário (rota /formularios/:tid). */
  readonly tid = input.required<string>();

  protected repo = inject(RepositorioService);
  protected auth = inject(AuthService);
  protected rede = inject(RedeService);
  protected carregando = signal(true);
  protected doCache = signal(false);
  protected todas = signal<AuditoriaResumo[]>([]);
  protected filtro = signal<'EM_ANDAMENTO' | 'CONCLUIDA' | 'TODAS'>('EM_ANDAMENTO');
  protected formulario = signal<Formulario | null>(null);
  /** Ver = só consulta (sem "Nova auditoria"). */
  protected nivel = signal<NivelAcesso>('PREENCHER');
  protected podePreencher = computed(() => this.nivel() === 'PREENCHER');
  protected conformidades = computed(() => opcoesDe(this.formulario()));
  protected filtradas = computed(() =>
    this.todas()
      .filter((a) => a.templateId === +this.tid())
      .filter((a) => this.filtro() === 'TODAS' || a.status === this.filtro()),
  );

  ngOnInit(): void {
    this.carregar();
  }

  protected pct(a: AuditoriaResumo): number {
    return a.totalItens ? (a.respondidos / a.totalItens) * 100 : 0;
  }

  private async carregar(): Promise<void> {
    this.repo.formulario(+this.tid()).then((f) => {
      this.formulario.set(f?.formulario ?? null);
      // Sem o formulário na lista = sem acesso (o servidor recusa de qualquer forma).
      this.nivel.set(f ? (f.nivel ?? 'PREENCHER') : 'VER');
    });
    const r = await this.repo.listar();
    this.todas.set(r.dados);
    this.doCache.set(r.doCache);
    this.carregando.set(false);
  }
}
