import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { InspecaoService, hojeIso } from '../core/inspecao.service';
import { mensagemErro } from '../core/interceptors';
import { FormularioResumo, InspecaoResumo } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';

/** FQ-106: inspeções de OPs, uma por inspetor por dia. */
@Component({
  selector: 'app-inspecoes',
  imports: [DatePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="linha topo">
      <button mat-icon-button routerLink="/" class="btn-voltar" aria-label="Voltar aos formulários">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <div class="titulos">
        <h1>{{ form()?.formulario?.nome ?? 'Inspeção de OPs' }}</h1>
        @if (form(); as f) {
          <div class="subtitulo">{{ f.formulario.identificacao }}</div>
        }
      </div>
    </div>

    @if (podePreencher()) {
      <button mat-flat-button class="cheio btn-hoje" (click)="abrirHoje()" [disabled]="abrindo()">
        @if (abrindo()) {
          <mat-spinner diameter="20" />
        } @else {
          <mat-icon>today</mat-icon>
        }
        {{ temHoje() ? 'Continuar a inspeção de hoje' : 'Abrir a inspeção de hoje' }}
      </button>
    }

    <div class="linha barra-filtro">
      <mat-button-toggle-group [value]="filtro()" (change)="filtro.set($event.value)" hideSingleSelectionIndicator>
        <mat-button-toggle value="TODAS">Todas</mat-button-toggle>
        <mat-button-toggle value="EM_ANDAMENTO">Em andamento</mat-button-toggle>
        <mat-button-toggle value="FINALIZADA">Finalizadas</mat-button-toggle>
      </mat-button-toggle-group>
      <span class="espaco"></span>
      @if (auth.podeRevisar()) {
        <a mat-button [routerLink]="['/formularios', tid(), 'revisoes']"><mat-icon>history</mat-icon> Revisões</a>
      }
    </div>

    @if (doCache()) {
      <div class="aviso atencao">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">Modo offline. Exibindo as inspeções salvas neste aparelho.</span>
      </div>
    }

    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      @for (d of filtradas(); track d.id) {
        <a class="cartao dia" [routerLink]="['/inspecoes', tid(), 'dia', d.id]">
          <div class="linha cabecalho-dia">
            <div class="data">{{ d.data | date: 'dd/MM/yyyy' }}</div>
            <span class="mudo">{{ d.inspetor.nome }}</span>
            <span class="espaco"></span>
            @if (d.status === 'FINALIZADA') {
              <span class="etiqueta finalizada"><mat-icon inline>verified</mat-icon> Finalizada</span>
            } @else {
              <span class="etiqueta andamento"><mat-icon inline>edit</mat-icon> Em andamento</span>
            }
          </div>
          <div class="linha numeros">
            <span class="num"><b>{{ d.totalOps }}</b> OP(s)</span>
            <span class="num conforme"><b>{{ d.contagem['C'] ?? 0 }}</b> ✓</span>
            <span class="num" [class.nc]="d.naoConformes > 0"><b>{{ d.naoConformes }}</b> ocorrência(s)</span>
            <span class="espaco"></span>
            <mat-icon class="seta">chevron_right</mat-icon>
          </div>
        </a>
      } @empty {
        <div class="cartao estado-vazio">
          <mat-icon>fact_check</mat-icon>
          <div>Nenhuma inspeção {{ filtro() === 'TODAS' ? 'registrada' : 'neste filtro' }}.</div>
        </div>
      }
    }
  `,
  styles: `
    .topo { gap: 12px; flex-wrap: nowrap; margin-bottom: 16px; align-items: center; }
    .titulos { flex: 1; min-width: 0; }
    .titulos h1 { margin: 0; font-size: 22px; color: var(--cor-primaria); }
    .subtitulo { font-size: 13px; color: #64748b; margin-top: 2px; }
    .btn-hoje { height: 56px; font-size: 16px; border-radius: 14px; margin-bottom: 16px; }
    .btn-hoje mat-spinner { display: inline-block; margin-right: 8px; }
    .barra-filtro { gap: 8px; margin-bottom: 16px; }
    .espaco { flex: 1; }
    .dia { display: block; color: inherit; text-decoration: none; padding: 16px 18px; }
    .dia:active { background: #f8fafc; }
    .cabecalho-dia { gap: 10px; margin-bottom: 10px; }
    .data { font-size: 18px; font-weight: 700; color: var(--cor-primaria); }
    .etiqueta {
      font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 9999px;
      display: inline-flex; align-items: center; gap: 4px; border: 1px solid transparent;
    }
    .etiqueta.andamento { background: #f0f7ff; border-color: #bae6fd; color: #0369a1; }
    .etiqueta.finalizada { background: #f0fdf4; border-color: #bbf7d0; color: #15803d; }
    .numeros { gap: 8px; }
    .num { font-size: 13px; color: #334155; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 4px 10px; }
    .num.conforme { color: var(--cor-c); }
    .num.nc { color: var(--cor-nc); background: var(--cor-nc-fundo); border-color: var(--cor-nc-borda); }
    .seta { color: #cbd5e1; }
    .estado-vazio { text-align: center; padding: 40px 20px; color: #94a3b8; }
    .estado-vazio mat-icon { font-size: 40px; width: 40px; height: 40px; }
  `,
})
export class InspecoesPagina {
  readonly tid = input.required<string>();

  protected auth = inject(AuthService);
  private repo = inject(RepositorioService);
  private inspecoes = inject(InspecaoService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  protected form = signal<FormularioResumo | null>(null);
  protected lista = signal<InspecaoResumo[]>([]);
  protected carregando = signal(true);
  protected doCache = signal(false);
  protected abrindo = signal(false);
  protected filtro = signal<'TODAS' | 'EM_ANDAMENTO' | 'FINALIZADA'>('TODAS');

  protected podePreencher = computed(() => (this.form()?.nivel ?? 'PREENCHER') === 'PREENCHER');
  protected filtradas = computed(() =>
    this.lista()
      .filter((d) => d.templateId === +this.tid())
      .filter((d) => this.filtro() === 'TODAS' || d.status === this.filtro()),
  );
  protected temHoje = computed(() =>
    this.lista().some((d) => d.templateId === +this.tid() && d.data === hojeIso() && d.inspetor.id === this.auth.usuario()?.id),
  );

  ngOnInit(): void {
    this.repo.formulario(+this.tid()).then((f) => this.form.set(f));
    this.carregar();
  }

  private async carregar(): Promise<void> {
    const r = await this.inspecoes.listar();
    this.lista.set(r.dados);
    this.doCache.set(r.doCache);
    this.carregando.set(false);
  }

  protected async abrirHoje(): Promise<void> {
    this.abrindo.set(true);
    try {
      const d = await this.inspecoes.abrirHoje(+this.tid());
      await this.router.navigate(['/inspecoes', this.tid(), 'dia', d.id]);
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.abrindo.set(false);
    }
  }
}
