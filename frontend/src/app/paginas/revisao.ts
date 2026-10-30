import { TextFieldModule } from '@angular/cdk/text-field';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { mensagemErro } from '../core/interceptors';
import { Formulario, PerguntaRevisao, ResultadoRevisao, Revisao, SECOES, Secao } from '../core/modelos';
import { RedeService } from '../core/rede.service';
import { RepositorioService } from '../core/repositorio.service';
import { uuid } from '../core/util';

/** Pergunta em edição; {@code k} identifica o cartão na tela (a pergunta nova ainda não tem id). */
interface PerguntaEdit extends PerguntaRevisao {
  k: string;
}

const CAMPOS: (keyof PerguntaRevisao)[] = [
  'secao',
  'grupo',
  'clausula',
  'texto',
  'processoPadrao',
  'documentosPadrao',
  'orientacaoPadrao',
  'responsavelPadrao',
];

/** Resumo do que o salvamento fará, antes de confirmar. */
@Component({
  selector: 'app-resumo-revisao',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Salvar Rev. {{ r.revisao }}?</h2>
    <mat-dialog-content>
      <ul class="resumo">
        <li><b>{{ r.alteradas }}</b> pergunta(s) alterada(s), <b>{{ r.novas }}</b> nova(s), <b>{{ r.removidas }}</b> removida(s).</li>
        <li><b>{{ r.auditorias }}</b> auditoria(s) em andamento serão atualizadas.</li>
        @if (r.itensReabertos) {
          <li><b>{{ r.itensReabertos }}</b> item(ns) já respondido(s) voltarão a pendente, com aviso para o inspetor.</li>
        }
        @if (r.rodape) {
          <li>Rodapé da impressão: <i>{{ r.rodape }}</i></li>
        }
      </ul>
      <p class="mudo">Auditorias concluídas não mudam.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Voltar a editar</button>
      <button mat-flat-button [mat-dialog-close]="true">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .resumo { padding-left: 18px; margin: 0 0 8px; line-height: 1.6; }
  `,
})
export class ResumoRevisaoDialog {
  protected r = inject<ResultadoRevisao>(MAT_DIALOG_DATA);
}

/** Abrir uma revisão nova (?nova=1) ou reescrever a revisão atual do formulário. */
@Component({
  selector: 'app-revisao',
  imports: [
    FormsModule,
    RouterLink,
    TextFieldModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  template: `
    <div class="linha topo">
      <button mat-icon-button [routerLink]="['/formularios', tid()]" class="btn-voltar" aria-label="Voltar">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <div class="titulos">
        <h1>{{ ehNova() ? 'Nova revisão' : 'Editar revisão' }} · {{ rev()?.nome }}</h1>
        @if (rev(); as r) {
          <div class="subtitulo">
            <span class="pilula-rev">Rev. {{ numero() }}</span>
            @if (ehNova()) {
              <span class="mudo">era Rev. {{ r.revisao }} · o número só muda ao salvar</span>
            } @else {
              <span class="mudo">salvar reescreve esta mesma revisão</span>
            }
          </div>
        }
      </div>
    </div>

    @if (!rede.online()) {
      <div class="aviso atencao">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">A revisão de formulário precisa de conexão. Suas alterações ficam nesta tela até a rede voltar.</span>
      </div>
    }

    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else if (erro()) {
      <div class="aviso erro">
        <mat-icon>error</mat-icon>
        <span class="texto">{{ erro() }}</span>
        <button mat-stroked-button (click)="carregar()">Recarregar</button>
      </div>
    } @else {
      <div class="cartao">
        <mat-form-field class="cheio" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Título do formulário</mat-label>
          <input matInput [ngModel]="titulo()" (ngModelChange)="titulo.set($event)" maxlength="200" />
        </mat-form-field>
        <div class="linha resumo-linha">
          <span class="mudo">{{ perguntas().length }} pergunta(s)</span>
          @if (contagem().alteradas || contagem().novas || contagem().removidas) {
            <span class="tag alterada">{{ contagem().alteradas }} alterada(s)</span>
            <span class="tag nova">{{ contagem().novas }} nova(s)</span>
            <span class="tag removida">{{ contagem().removidas }} removida(s)</span>
          }
        </div>
      </div>

      <div class="lista-perguntas">
        @for (p of perguntas(); track p.k; let idx = $index; let ultimo = $last) {
          @if (idx === 0 || p.grupo !== perguntas()[idx - 1].grupo) {
            <div class="grupo">{{ p.grupo || 'Sem grupo' }}</div>
          }
          <div class="cartao pergunta" [class.aberta]="aberta() === p.k">
            <button type="button" class="cabeca" (click)="alternar(p.k)" [attr.aria-expanded]="aberta() === p.k">
              <span class="num">{{ idx + 1 }}</span>
              <span class="corpo">
                @if (p.clausula) {
                  <span class="clausula">{{ p.clausula }}</span>
                }
                <span class="texto-pergunta">{{ p.texto || '(sem texto)' }}</span>
              </span>
              @if (estado(p); as e) {
                <span class="tag" [class]="e">{{ e }}</span>
              }
              <mat-icon class="seta">{{ aberta() === p.k ? 'expand_less' : 'expand_more' }}</mat-icon>
            </button>

            @if (aberta() === p.k) {
              <div class="edicao">
                <mat-form-field class="cheio" appearance="outline">
                  <mat-label>Pergunta</mat-label>
                  <textarea matInput cdkTextareaAutosize cdkAutosizeMinRows="2" [ngModel]="p.texto"
                    (ngModelChange)="mudar(p, 'texto', $event)" maxlength="2000" required></textarea>
                  @if (!p.texto.trim()) {
                    <mat-error>Obrigatório</mat-error>
                  }
                </mat-form-field>
                <div class="grade">
                  @if (form()?.rotuloReferencia; as rot) {
                    <mat-form-field appearance="outline">
                      <mat-label>{{ rot }}</mat-label>
                      <input matInput [ngModel]="p.clausula" (ngModelChange)="mudar(p, 'clausula', $event)" maxlength="60" />
                    </mat-form-field>
                  }
                  <mat-form-field appearance="outline">
                    <mat-label>Grupo (capítulo / etapa / setor)</mat-label>
                    <input matInput [ngModel]="p.grupo" (ngModelChange)="mudar(p, 'grupo', $event)" maxlength="200" list="grupos-rev" />
                  </mat-form-field>
                  @if (temCampo()) {
                    <mat-form-field appearance="outline">
                      <mat-label>Seção</mat-label>
                      <mat-select [ngModel]="p.secao" (ngModelChange)="mudar(p, 'secao', $event)">
                        @for (s of secoes; track s.valor) {
                          <mat-option [value]="s.valor">{{ s.rotulo }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                  }
                  @if (form()?.usaProcesso) {
                    <mat-form-field appearance="outline">
                      <mat-label>Processo padrão</mat-label>
                      <input matInput [ngModel]="p.processoPadrao" (ngModelChange)="mudar(p, 'processoPadrao', $event)" maxlength="500" />
                    </mat-form-field>
                  }
                  @if (form()?.usaDocumentos) {
                    <mat-form-field appearance="outline">
                      <mat-label>DOC's padrão</mat-label>
                      <textarea matInput cdkTextareaAutosize [ngModel]="p.documentosPadrao"
                        (ngModelChange)="mudar(p, 'documentosPadrao', $event)" maxlength="2000"></textarea>
                    </mat-form-field>
                  }
                  <mat-form-field appearance="outline">
                    <mat-label>Orientação padrão (como verificar)</mat-label>
                    <textarea matInput cdkTextareaAutosize [ngModel]="p.orientacaoPadrao"
                      (ngModelChange)="mudar(p, 'orientacaoPadrao', $event)" maxlength="2000"></textarea>
                  </mat-form-field>
                  @if (p.secao === 'CAMPO') {
                    <mat-form-field appearance="outline">
                      <mat-label>Responsável padrão</mat-label>
                      <input matInput [ngModel]="p.responsavelPadrao" (ngModelChange)="mudar(p, 'responsavelPadrao', $event)" maxlength="150" />
                    </mat-form-field>
                  }
                </div>
                @if (p.respondidas) {
                  <div class="aviso info">
                    <mat-icon>info</mat-icon>
                    <span class="texto">
                      {{ p.respondidas }} auditoria(s) em andamento já responderam esta pergunta. Mudar o texto ou a referência
                      faz o inspetor responder de novo.
                    </span>
                  </div>
                }
                <div class="acoes">
                  <button mat-stroked-button (click)="mover(idx, -1)" [disabled]="idx === 0" aria-label="Subir">
                    <mat-icon>arrow_upward</mat-icon> Subir
                  </button>
                  <button mat-stroked-button (click)="mover(idx, 1)" [disabled]="ultimo" aria-label="Descer">
                    <mat-icon>arrow_downward</mat-icon> Descer
                  </button>
                  <button mat-stroked-button (click)="incluir(idx + 1, p)"><mat-icon>add</mat-icon> Incluir abaixo</button>
                  <span class="espaco"></span>
                  <button mat-stroked-button class="perigo" (click)="remover(idx)"><mat-icon>delete_outline</mat-icon> Remover</button>
                </div>
              </div>
            }
          </div>
        }
        <datalist id="grupos-rev">
          @for (g of grupos(); track g) { <option [value]="g"></option> }
        </datalist>
        <button mat-stroked-button class="cheio incluir-fim" (click)="incluir(perguntas().length, ultima())">
          <mat-icon>add</mat-icon> Incluir pergunta no fim
        </button>
      </div>

      <div class="barra-acoes">
        <div class="barra-conteudo">
          <button mat-stroked-button class="btn-barra" (click)="cancelar()" [disabled]="salvando()">Cancelar</button>
          <span class="estado-barra mudo">{{ sujo() ? 'Alterações não salvas' : ehNova() ? 'Nada alterado ainda' : 'Sem alterações' }}</span>
          <button mat-flat-button class="btn-barra" (click)="salvar()"
            [disabled]="salvando() || !rede.online() || (!sujo() && !ehNova())">
            @if (salvando()) {
              <mat-spinner diameter="18" />
            } @else {
              <mat-icon>save</mat-icon>
            }
            Salvar Rev. {{ numero() }}
          </button>
        </div>
      </div>
    }
  `,
  styles: `
    .topo { gap: 12px; flex-wrap: nowrap; margin-bottom: 16px; align-items: center; }
    .titulos { flex: 1; min-width: 0; }
    .titulos h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); line-height: 1.25; }
    .subtitulo { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 4px; font-size: 13px; }
    .pilula-rev {
      background: var(--cor-secundaria-clara); color: #4c1d95; font-weight: 700;
      border-radius: 9999px; padding: 2px 10px; font-size: 13px;
    }
    .resumo-linha { gap: 8px; margin-top: 8px; }
    .tag {
      font-size: 11px; font-weight: 700; border-radius: 6px; padding: 2px 8px; flex-shrink: 0;
      border: 1px solid transparent; text-transform: lowercase;
    }
    .tag.alterada { background: #fffbeb; border-color: #fde68a; color: #b45309; }
    .tag.nova { background: #f0fdf4; border-color: #bbf7d0; color: #15803d; }
    .tag.removida { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }

    .grupo {
      font-size: 13px; font-weight: 700; color: #1e293b; margin: 20px 4px 8px;
      border-left: 4px solid var(--cor-secundaria); padding-left: 8px;
    }
    .pergunta { padding: 0; margin-bottom: 8px; overflow: hidden; }
    .pergunta.aberta { border-color: #cbd5e1; box-shadow: var(--sombra-card-hover); }
    .cabeca {
      width: 100%; display: flex; align-items: center; gap: 12px; padding: 12px 16px; min-height: 56px;
      background: none; border: 0; font: inherit; color: inherit; text-align: left; cursor: pointer;
    }
    .num {
      width: 32px; height: 32px; border-radius: 50%; background: #f1f5f9; color: #475467;
      display: inline-flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; flex-shrink: 0;
    }
    .corpo { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .clausula { font-size: 12px; font-weight: 700; color: var(--cor-secundaria); }
    .texto-pergunta {
      font-size: 14px; line-height: 1.35; color: #1e293b; overflow-wrap: anywhere;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .aberta .texto-pergunta { -webkit-line-clamp: unset; }
    .seta { color: #94a3b8; flex-shrink: 0; }
    .edicao { padding: 4px 16px 16px; border-top: 1px solid #f1f5f9; }
    .edicao > mat-form-field { margin-top: 12px; }
    .grade { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 0 16px; align-items: start; }
    .grade mat-form-field { width: 100%; }
    .acoes { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 4px; }
    .espaco { flex: 1; }
    .perigo { color: var(--cor-nc) !important; }
    .incluir-fim { margin: 8px 0 16px; height: 48px; border-style: dashed !important; }

    .barra-acoes {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 15; background: #fff;
      border-top: 1px solid #e2e8f0; box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.06);
      padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    }
    .barra-conteudo {
      max-width: 1100px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; gap: 12px;
    }
    .btn-barra { height: 48px; min-width: 120px; }
    .btn-barra mat-spinner { display: inline-block; margin-right: 6px; }
    @media (max-width: 480px) {
      .estado-barra { display: none; }
      .btn-barra { flex: 1; min-width: 0; }
    }
  `,
})
export class RevisaoPagina {
  readonly tid = input.required<string>();
  /** Query ?nova=1: abre a revisão seguinte. */
  readonly nova = input<string | undefined>();

  private repo = inject(RepositorioService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);
  protected rede = inject(RedeService);

  protected carregando = signal(true);
  protected erro = signal<string | null>(null);
  protected salvando = signal(false);
  protected rev = signal<Revisao | null>(null);
  protected form = signal<Formulario | null>(null);
  protected titulo = signal('');
  protected perguntas = signal<PerguntaEdit[]>([]);
  protected aberta = signal<string | null>(null);
  protected secoes = (Object.keys(SECOES) as Secao[]).map((valor) => ({ valor, rotulo: SECOES[valor] }));

  protected ehNova = computed(() => this.nova() === '1' || this.nova() === 'true');
  protected numero = computed(() => {
    const r = this.rev()?.revisao ?? '';
    const n = parseInt(r, 10);
    return this.ehNova() && !isNaN(n) ? String(n + 1).padStart(2, '0') : r;
  });
  private originais = computed(() => new Map((this.rev()?.perguntas ?? []).map((p) => [p.id, p])));
  protected temCampo = computed(() => this.perguntas().some((p) => p.secao === 'CAMPO') || (this.rev()?.perguntas ?? []).some((p) => p.secao === 'CAMPO'));
  protected grupos = computed(() => [...new Set(this.perguntas().map((p) => p.grupo).filter((g): g is string => !!g))]);
  protected contagem = computed(() => {
    const ps = this.perguntas();
    const ids = new Set(ps.map((p) => p.id));
    return {
      alteradas: ps.filter((p) => this.estado(p) === 'alterada').length,
      novas: ps.filter((p) => p.id === null).length,
      removidas: (this.rev()?.perguntas ?? []).filter((p) => !ids.has(p.id)).length,
    };
  });
  readonly sujo = computed(() => {
    const r = this.rev();
    if (!r) return false;
    if (this.titulo().trim() !== r.titulo.trim()) return true;
    const ps = this.perguntas();
    if (ps.length !== r.perguntas.length) return true;
    return ps.some((p, i) => p.id !== r.perguntas[i].id || CAMPOS.some((c) => norm(p[c]) !== norm(r.perguntas[i][c])));
  });

  ngOnInit(): void {
    this.carregar();
  }

  /** Fechar a aba / recarregar com alterações não salvas: o navegador pede confirmação. */
  @HostListener('window:beforeunload', ['$event'])
  protected aoSairDaPagina(e: BeforeUnloadEvent): void {
    if (this.sujo()) e.preventDefault();
  }

  protected async carregar(): Promise<void> {
    this.carregando.set(true);
    this.erro.set(null);
    try {
      const [r, f] = await Promise.all([this.repo.revisao(+this.tid()), this.repo.formulario(+this.tid())]);
      this.rev.set(r);
      this.form.set(f?.formulario ?? null);
      this.titulo.set(r.titulo);
      this.perguntas.set(r.perguntas.map((p) => ({ ...p, k: String(p.id) })));
      this.aberta.set(null);
    } catch (e) {
      this.erro.set(mensagemErro(e));
    } finally {
      this.carregando.set(false);
    }
  }

  /** Situação da pergunta em relação à revisão carregada (nova / alterada / sem marca). */
  protected estado(p: PerguntaEdit): 'nova' | 'alterada' | null {
    if (p.id === null) return 'nova';
    const o = this.originais().get(p.id);
    return o && CAMPOS.some((c) => norm(p[c]) !== norm(o[c])) ? 'alterada' : null;
  }

  protected alternar(k: string): void {
    this.aberta.update((a) => (a === k ? null : k));
  }

  protected mudar(p: PerguntaEdit, campo: keyof PerguntaRevisao, valor: string): void {
    this.perguntas.update((ps) => ps.map((x) => (x.k === p.k ? { ...x, [campo]: valor } : x)));
  }

  protected mover(idx: number, passo: number): void {
    this.perguntas.update((ps) => {
      const n = [...ps];
      const [p] = n.splice(idx, 1);
      n.splice(idx + passo, 0, p);
      return n;
    });
  }

  /** Inclui uma pergunta em branco na posição, herdando seção e grupo da vizinha. */
  protected incluir(posicao: number, vizinha: PerguntaEdit | null): void {
    const nova: PerguntaEdit = {
      k: uuid(),
      id: null,
      secao: vizinha?.secao ?? 'CHECKLIST',
      grupo: vizinha?.grupo ?? null,
      clausula: null,
      texto: '',
      processoPadrao: null,
      documentosPadrao: null,
      orientacaoPadrao: null,
      responsavelPadrao: null,
    };
    this.perguntas.update((ps) => [...ps.slice(0, posicao), nova, ...ps.slice(posicao)]);
    this.aberta.set(nova.k);
  }

  protected ultima(): PerguntaEdit | null {
    const ps = this.perguntas();
    return ps[ps.length - 1] ?? null;
  }

  protected remover(idx: number): void {
    const p = this.perguntas()[idx];
    const aviso = p.respondidas
      ? `\n\n${p.respondidas} auditoria(s) em andamento já responderam esta pergunta. A resposta sai da auditoria e fica no histórico.`
      : '';
    if (!confirm(`Remover a pergunta ${idx + 1}?${aviso}`)) return;
    this.perguntas.update((ps) => ps.filter((_, i) => i !== idx));
  }

  protected cancelar(): void {
    // A confirmação de descarte fica com o guard da rota.
    this.router.navigate(['/formularios', this.tid()]);
  }

  protected async salvar(): Promise<void> {
    const r = this.rev();
    if (!r) return;
    const vazia = this.perguntas().findIndex((p) => !p.texto.trim());
    if (vazia >= 0) {
      this.aberta.set(this.perguntas()[vazia].k);
      this.snack.open(`A pergunta ${vazia + 1} está sem texto.`, 'OK', { duration: 5000 });
      return;
    }
    if (!this.perguntas().length) {
      this.snack.open('O formulário precisa de pelo menos uma pergunta.', 'OK', { duration: 5000 });
      return;
    }
    if (!this.titulo().trim()) {
      this.snack.open('Preencha o título do formulário.', 'OK', { duration: 5000 });
      return;
    }
    const req = {
      nova: this.ehNova(),
      versao: r.versao,
      titulo: this.titulo().trim(),
      perguntas: this.perguntas().map(({ k, respondidas, numero, ...p }) => p),
    };
    this.salvando.set(true);
    try {
      const previa = await this.repo.previaRevisao(+this.tid(), req);
      const ok = await firstValueFrom(
        this.dialog.open(ResumoRevisaoDialog, { data: previa, width: '480px', maxWidth: '94vw' }).afterClosed(),
      );
      if (!ok) return;
      const res = await this.repo.salvarRevisao(+this.tid(), req);
      this.snack.open(`Rev. ${res.revisao} salva.`, undefined, { duration: 4000 });
      // Já salvo: sai sem perguntar e volta para a edição da revisão atual se quiser ajustar algo.
      this.rev.set(null);
      await this.router.navigate(['/formularios', this.tid()]);
    } catch (e) {
      const conflito = e instanceof HttpErrorResponse && e.status === 409;
      this.snack.open(mensagemErro(e), conflito ? 'Recarregar' : 'OK', { duration: 10000 })
        .onAction()
        .subscribe(() => conflito && this.carregar());
    } finally {
      this.salvando.set(false);
    }
  }
}

function norm(v: unknown): string {
  return v == null ? '' : String(v).trim();
}
