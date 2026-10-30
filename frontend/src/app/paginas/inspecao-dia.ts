import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { OperacaoInspecao } from '../core/db';
import { InspecaoService } from '../core/inspecao.service';
import { mensagemErro } from '../core/interceptors';
import { InspecaoDia, OpInspecao, naoConformeInspecao } from '../core/modelos';
import { RedeService } from '../core/rede.service';
import { SyncService } from '../core/sync.service';

/** Inspeção do dia: as OPs inspecionadas, finalizar e baixar o PDF para o PCP. */
@Component({
  selector: 'app-inspecao-dia',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule, MatProgressSpinnerModule],
  template: `
    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else if (!d()) {
      <div class="aviso erro">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">{{ erro() ?? 'Esta inspeção não está salva no aparelho e não há conexão com o servidor.' }}</span>
        <button mat-button [routerLink]="['/inspecoes', tid()]">Voltar</button>
      </div>
    } @else {
      @let dia = d()!;
      <div class="linha topo">
        <button mat-icon-button [routerLink]="['/inspecoes', tid()]" class="btn-voltar" aria-label="Voltar">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="titulos">
          <h1>Inspeção de {{ dia.data | date: 'dd/MM/yyyy' }}</h1>
          <div class="subtitulo">{{ dia.inspetor.nome }} · {{ dia.formulario }}</div>
        </div>
        <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Mais opções"><mat-icon>more_vert</mat-icon></button>
        <mat-menu #menu="matMenu">
          <button mat-menu-item (click)="baixarPdf()" [disabled]="!rede.online()">
            <mat-icon>picture_as_pdf</mat-icon>Baixar relatório (PDF)
          </button>
          <button mat-menu-item [routerLink]="['/inspecoes', tid(), 'dia', dia.id, 'historico']" [disabled]="!rede.online()">
            <mat-icon>history</mat-icon>Histórico de alterações
          </button>
          @if (dia.status === 'FINALIZADA' && auth.admin()) {
            <button mat-menu-item (click)="reabrir()" [disabled]="!rede.online()"><mat-icon>lock_open</mat-icon>Reabrir inspeção</button>
          }
          @if (auth.admin()) {
            <button mat-menu-item class="perigo" (click)="excluir()" [disabled]="!rede.online()">
              <mat-icon>delete</mat-icon>Excluir inspeção
            </button>
          }
        </mat-menu>
      </div>

      @if (doCache()) {
        <div class="aviso atencao">
          <mat-icon>cloud_off</mat-icon>
          <span class="texto">Modo offline. Tudo fica salvo neste aparelho e é enviado quando a rede voltar.</span>
        </div>
      }
      @if (rejeitadas().length) {
        <div class="aviso erro">
          <mat-icon>sync_problem</mat-icon>
          <span class="texto">O servidor recusou {{ rejeitadas().length }} alteração(ões): <b>{{ rejeitadas()[0].erro }}</b></span>
          <button mat-stroked-button (click)="tentarDeNovo()">Tentar de novo</button>
          <button mat-button (click)="descartar()">Descartar</button>
        </div>
      }

      @if (dia.status === 'FINALIZADA') {
        <div class="aviso info banner concluido">
          <mat-icon>verified</mat-icon>
          <span class="texto">Inspeção finalizada em {{ dia.finalizadaEm | date: 'dd/MM/yyyy HH:mm' }}. Somente leitura.</span>
          <button mat-flat-button (click)="baixarPdf()" [disabled]="!rede.online() || ocupado()">
            <mat-icon>picture_as_pdf</mat-icon> Baixar PDF para o PCP
          </button>
        </div>
      } @else if (!editavel()) {
        <div class="aviso atencao banner">
          <mat-icon>visibility</mat-icon>
          <span class="texto">Inspeção de {{ dia.inspetor.nome }}. Você pode consultar, mas só ela/ele altera.</span>
        </div>
      }
      @if (ocupado()) {
        <mat-progress-bar mode="indeterminate" class="progresso" />
      }

      <div class="cartao resumo">
        <div class="linha">
          <span class="grande">{{ dia.ops.length }}</span><span class="mudo">OP(s) inspecionada(s)</span>
          <span class="espaco"></span>
          <span class="chip nc" [class.zero]="!ocorrencias()">{{ ocorrencias() }} ocorrência(s)</span>
          <span class="chip branco" [class.zero]="!brancos()">{{ brancos() }} em branco</span>
        </div>
      </div>

      @for (op of opsOrdenadas(); track op.id) {
        <a class="cartao op" [routerLink]="['/inspecoes', tid(), 'dia', dia.id, 'op', op.id]">
          <span class="ordem">{{ op.ordem }}</span>
          <span class="corpo">
            <span class="numero-op">{{ op.numeroOp ? 'OP ' + op.numeroOp : 'OP sem número' }}</span>
            <span class="mudo">{{ op.codigoProduto ?? 'Produto não informado' }}</span>
          </span>
          <span class="contagens">
            <span class="mini c">{{ conta(op, 'C') }} ✓</span>
            @if (ocorrenciasDa(op)) {
              <span class="mini nc">{{ ocorrenciasDa(op) }} N/C</span>
            }
            @if (brancosDa(op)) {
              <span class="mini branco">{{ brancosDa(op) }} vazio(s)</span>
            }
          </span>
          <mat-icon class="seta">chevron_right</mat-icon>
        </a>
      } @empty {
        <div class="cartao estado-vazio">
          <mat-icon>add_task</mat-icon>
          <div>Nenhuma OP inspecionada ainda.</div>
          @if (editavel()) {
            <div class="mudo">Toque em <b>Nova OP</b> quando chegar a uma linha de produção.</div>
          }
        </div>
      }

      @if (editavel()) {
        <div class="barra-fixa">
          <div class="barra-conteudo">
            <button mat-stroked-button class="btn-barra" (click)="finalizar()" [disabled]="!rede.online() || ocupado() || !dia.ops.length">
              <mat-icon>task_alt</mat-icon> Finalizar dia
            </button>
            <button mat-flat-button class="btn-barra" (click)="novaOp()" [disabled]="ocupado()">
              <mat-icon>add</mat-icon> Nova OP
            </button>
          </div>
        </div>
      }
    }
  `,
  styles: `
    .topo { gap: 12px; flex-wrap: nowrap; margin-bottom: 16px; align-items: center; }
    .titulos { flex: 1; min-width: 0; }
    .titulos h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); }
    .subtitulo { font-size: 13px; color: #64748b; margin-top: 2px; }
    .espaco { flex: 1; }
    .banner { border-left-width: 4px; }
    .banner.concluido { border-left-color: #16a34a; background: #f0fdf4; color: #166534; }
    .progresso { margin-bottom: 12px; }
    .resumo { padding: 14px 18px; }
    .resumo .linha { gap: 8px; }
    .grande { font-size: 24px; font-weight: 700; color: var(--cor-primaria); }
    .chip { font-size: 12px; font-weight: 700; border-radius: 9999px; padding: 4px 10px; border: 1px solid transparent; }
    .chip.nc { background: var(--cor-nc-fundo); color: var(--cor-nc); border-color: var(--cor-nc-borda); }
    .chip.branco { background: #fffbeb; color: #b45309; border-color: #fde68a; }
    .chip.zero { background: #f8fafc; color: #94a3b8; border-color: #e2e8f0; }
    .op {
      display: flex; align-items: center; gap: 14px; padding: 14px 16px; margin-bottom: 10px;
      color: inherit; text-decoration: none; min-height: 64px;
    }
    .op:active { background: #f8fafc; }
    .ordem {
      width: 34px; height: 34px; border-radius: 50%; background: var(--cor-primaria-clara); color: var(--cor-primaria);
      display: inline-flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0;
    }
    .corpo { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .numero-op { font-size: 16px; font-weight: 700; color: #0f172a; overflow-wrap: anywhere; }
    .contagens { display: flex; flex-wrap: wrap; gap: 4px; justify-content: flex-end; max-width: 45%; }
    .mini { font-size: 11px; font-weight: 700; border-radius: 6px; padding: 2px 6px; border: 1px solid #e2e8f0; background: #f8fafc; }
    .mini.c { color: var(--cor-c); }
    .mini.nc { color: var(--cor-nc); background: var(--cor-nc-fundo); border-color: var(--cor-nc-borda); }
    .mini.branco { color: #b45309; background: #fffbeb; border-color: #fde68a; }
    .seta { color: #cbd5e1; flex-shrink: 0; }
    .estado-vazio { text-align: center; padding: 36px 20px; color: #94a3b8; }
    .estado-vazio mat-icon { font-size: 40px; width: 40px; height: 40px; }
    .barra-fixa {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 15; background: #fff; border-top: 1px solid #e2e8f0;
      box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.06); padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    }
    .barra-conteudo { max-width: 1100px; margin: 0 auto; display: flex; gap: 12px; justify-content: space-between; }
    .btn-barra { flex: 1; max-width: 260px; height: 52px; font-size: 15px; }
    .perigo { color: var(--cor-nc); }
  `,
})
export class InspecaoDiaPagina {
  readonly tid = input.required<string>();
  readonly id = input.required<string>();

  protected auth = inject(AuthService);
  protected rede = inject(RedeService);
  private inspecoes = inject(InspecaoService);
  private sync = inject(SyncService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  protected d = signal<InspecaoDia | null>(null);
  protected carregando = signal(true);
  protected doCache = signal(false);
  protected ocupado = signal(false);
  protected erro = signal<string | null>(null);
  protected ops = signal<OperacaoInspecao[]>([]);

  protected editavel = computed(() => this.inspecoes.podeEditar(this.d()));
  protected rejeitadas = computed(() => this.ops().filter((o) => o.estado === 'rejeitada'));
  protected opsOrdenadas = computed(() => [...(this.d()?.ops ?? [])].sort((a, b) => a.ordem - b.ordem));
  protected ocorrencias = computed(() => (this.d()?.ops ?? []).reduce((s, o) => s + this.ocorrenciasDa(o), 0));
  protected brancos = computed(() => (this.d()?.ops ?? []).reduce((s, o) => s + this.brancosDa(o), 0));

  constructor() {
    effect(() => {
      if (this.sync.confirmacoes()) untracked(() => this.recarregarLocal());
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  private async carregar(): Promise<void> {
    try {
      const r = await this.inspecoes.abrir(this.id());
      this.d.set(r.dados);
      this.doCache.set(r.doCache);
      if (!r.dados && !r.doCache) this.erro.set('Esta inspeção foi excluída ou não existe mais.');
    } catch (e) {
      this.erro.set(mensagemErro(e));
    }
    this.ops.set(await this.inspecoes.operacoes(this.id()));
    this.carregando.set(false);
  }

  private async recarregarLocal(): Promise<void> {
    const local = await this.inspecoes.local(this.id());
    if (local) this.d.set(local);
    this.ops.set(await this.inspecoes.operacoes(this.id()));
  }

  protected conta(op: OpInspecao, valor: string): number {
    return op.itens.filter((i) => i.valor === valor).length;
  }

  protected ocorrenciasDa(op: OpInspecao): number {
    return op.itens.filter((i) => naoConformeInspecao(i.escala, i.valor)).length;
  }

  protected brancosDa(op: OpInspecao): number {
    return op.itens.filter((i) => !i.valor).length;
  }

  protected async novaOp(): Promise<void> {
    try {
      const op = await this.inspecoes.novaOp(this.id());
      await this.router.navigate(['/inspecoes', this.tid(), 'dia', this.id(), 'op', op.id]);
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 6000 });
    }
  }

  protected async finalizar(): Promise<void> {
    const dia = this.d()!;
    const brancos = this.brancos();
    const semNumero = dia.ops.filter((o) => !o.numeroOp).length;
    const avisos = [
      brancos ? `${brancos} tópico(s) em branco` : '',
      semNumero ? `${semNumero} OP(s) sem número` : '',
    ].filter(Boolean);
    const texto = `Finalizar a inspeção de hoje (${dia.ops.length} OP(s))?${avisos.length ? '\n\nAtenção: ' + avisos.join(' e ') + '.' : ''}\n\nDepois ela fica somente leitura.`;
    if (!confirm(texto)) return;
    this.ocupado.set(true);
    try {
      this.d.set(await this.inspecoes.finalizar(this.id()));
      this.snack.open('Inspeção finalizada. Baixe o PDF e envie ao PCP.', 'Baixar PDF', { duration: 10000 })
        .onAction()
        .subscribe(() => this.baixarPdf());
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async reabrir(): Promise<void> {
    if (!confirm('Reabrir esta inspeção finalizada? A reabertura fica registrada no histórico.')) return;
    this.ocupado.set(true);
    try {
      this.d.set(await this.inspecoes.reabrir(this.id()));
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async excluir(): Promise<void> {
    const dia = this.d()!;
    const [a, m, d] = dia.data.split('-');
    if (!confirm(`Excluir a inspeção de ${d}/${m}/${a} (${dia.inspetor.nome}, ${dia.ops.length} OP(s))?\n\nEla sai da lista e do PDF; o registro fica guardado no histórico do servidor.`)) return;
    this.ocupado.set(true);
    try {
      await this.inspecoes.excluir(this.id());
      this.snack.open('Inspeção excluída.', undefined, { duration: 3000 });
      await this.router.navigate(['/inspecoes', this.tid()], { replaceUrl: true });
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async baixarPdf(): Promise<void> {
    this.ocupado.set(true);
    try {
      await this.inspecoes.baixarRelatorio(this.id());
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 8000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async tentarDeNovo(): Promise<void> {
    await this.inspecoes.tentarDeNovo(this.id());
    await this.carregar();
  }

  protected async descartar(): Promise<void> {
    if (!confirm('Descartar as alterações recusadas? Elas serão apagadas deste aparelho.')) return;
    await this.inspecoes.descartarRejeitadas(this.id());
    await this.carregar();
  }
}
