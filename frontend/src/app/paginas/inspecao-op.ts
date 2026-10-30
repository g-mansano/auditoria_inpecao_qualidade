import { TextFieldModule } from '@angular/cdk/text-field';
import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { comprimirSeImagem } from '../core/imagem';
import { InspecaoService } from '../core/inspecao.service';
import { mensagemErro } from '../core/interceptors';
import { AnexoInspecao, InspecaoDia, ItemInspecao, OpInspecao, escalaDe, naoConformeInspecao } from '../core/modelos';
import { SyncService } from '../core/sync.service';
import { uuid } from '../core/util';

const ESPERA_AUTOSAVE_MS = 700;
const ACEITOS = '.jpg,.jpeg,.png,.webp,.gif,.heic,.pdf,.mp4,.mov';

/** Uma OP do FQ-106: nº da OP, produto, tópicos (com observação e fotos) e lotes do insumo. */
@Component({
  selector: 'app-inspecao-op',
  imports: [FormsModule, RouterLink, TextFieldModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule],
  template: `
    @if (!op()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      @let o = op()!;
      <div class="linha topo">
        <button mat-icon-button [routerLink]="['/inspecoes', tid(), 'dia', id()]" class="btn-voltar" aria-label="Voltar ao dia">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="titulos">
          <h1>{{ o.numeroOp ? 'OP ' + o.numeroOp : 'Nova OP' }}</h1>
          <div class="subtitulo">OP {{ o.ordem }} do dia · {{ respondidos() }} de {{ o.itens.length }} tópicos</div>
        </div>
        <span class="estado">
          @if (!editavel()) {
            <span class="status-badge leitura"><mat-icon inline>lock</mat-icon> Somente leitura</span>
          } @else if (salvando()) {
            <span class="status-badge salvando"><mat-icon inline>sync</mat-icon> Salvando…</span>
          } @else if (salvo()) {
            <span class="status-badge salvo"><mat-icon inline>check_circle</mat-icon> Salvo</span>
          }
        </span>
      </div>

      <div class="cartao">
        <div class="grade">
          <mat-form-field appearance="outline">
            <mat-label>Nº da OP</mat-label>
            <input matInput [ngModel]="o.numeroOp" (ngModelChange)="mudar({ numeroOp: $event })" maxlength="60" [disabled]="!editavel()"
              inputmode="numeric" autocomplete="off" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Código do produto</mat-label>
            <input matInput [ngModel]="o.codigoProduto" (ngModelChange)="mudar({ codigoProduto: $event })" maxlength="120"
              [disabled]="!editavel()" autocomplete="off" />
          </mat-form-field>
        </div>
      </div>

      @for (i of o.itens; track i.numero) {
        <div class="cartao topico" [class.ocorrencia]="ocorrencia(i)">
          <div class="linha cabeca-topico">
            <span class="num">{{ i.numero }}</span>
            <span class="nome-topico">{{ i.topico }}</span>
          </div>
          <div class="escala" [class.epi]="i.escala === 'EPI'" role="radiogroup" [attr.aria-label]="i.topico">
            @for (v of escala(i); track v.valor) {
              <button type="button" role="radio" class="opcao" [class]="'v-' + v.valor" [class.marcada]="i.valor === v.valor"
                [attr.aria-checked]="i.valor === v.valor" [attr.title]="v.rotulo" [disabled]="!editavel()" (click)="marcar(i, v.valor)">
                <span class="sigla">{{ v.sigla }}</span>
                <span class="rotulo">{{ v.rotulo }}</span>
              </button>
            }
          </div>

          @if (i.escala === 'EPI' || i.observacao || obsAberta().has(i.numero) || ocorrencia(i)) {
            <mat-form-field class="cheio obs" appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ i.escala === 'EPI' ? 'Observações EPI' : 'Observação' }}</mat-label>
              <textarea matInput cdkTextareaAutosize cdkAutosizeMinRows="1" [ngModel]="i.observacao"
                (ngModelChange)="mudarItem(i, { observacao: $event })" maxlength="4000" [disabled]="!editavel()"></textarea>
            </mat-form-field>
          }

          @if (i.anexos.length) {
            <div class="fotos">
              @for (an of i.anexos; track an.id) {
                <div class="foto">
                  <button type="button" class="miniatura" (click)="abrirAnexo(an)" [attr.aria-label]="'Abrir ' + an.nomeOriginal">
                    @if (miniaturas()[an.id]; as url) {
                      <img [src]="url" [alt]="an.nomeOriginal" />
                    } @else {
                      <mat-icon>image</mat-icon>
                    }
                  </button>
                  @if (an.pendente) {
                    <span class="pendente" title="Ainda não enviada">●</span>
                  }
                  @if (editavel()) {
                    <button mat-icon-button class="remover" (click)="removerAnexo(an)" aria-label="Remover foto"><mat-icon>close</mat-icon></button>
                  }
                </div>
              }
            </div>
          }

          @if (editavel()) {
            <div class="acoes-topico">
              <button mat-button type="button" (click)="foto(i)"><mat-icon>photo_camera</mat-icon> Foto</button>
              <button mat-button type="button" (click)="arquivo(i)"><mat-icon>attach_file</mat-icon> Anexar</button>
              @if (i.escala !== 'EPI' && !i.observacao && !ocorrencia(i)) {
                <button mat-button type="button" (click)="abrirObs(i)"><mat-icon>notes</mat-icon> Observação</button>
              }
              @if (i.valor) {
                <span class="espaco"></span>
                <button mat-button type="button" class="limpar" (click)="marcar(i, null)">Limpar</button>
              }
            </div>
          }
        </div>
      }

      <div class="cartao">
        <div class="linha cabeca-lotes">
          <b>Lotes do insumo</b>
          <span class="mudo">(lote e data de fabricação)</span>
          <span class="espaco"></span>
          @if (editavel()) {
            <button mat-stroked-button (click)="novoLote()"><mat-icon>add</mat-icon> Lote</button>
          }
        </div>
        @for (l of o.lotes; track l.id) {
          <div class="linha lote">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="campo-lote">
              <mat-label>Lote</mat-label>
              <input matInput [ngModel]="l.lote" (ngModelChange)="mudarLote(l.id, { lote: $event })" maxlength="120" [disabled]="!editavel()" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="campo-data">
              <mat-label>Fabricação</mat-label>
              <input matInput [ngModel]="l.dataFabricacao" (ngModelChange)="mudarLote(l.id, { dataFabricacao: $event })" maxlength="40"
                placeholder="ex.: 10/2026" [disabled]="!editavel()" />
            </mat-form-field>
            @if (editavel()) {
              <button mat-icon-button (click)="removerLote(l.id)" aria-label="Remover lote"><mat-icon>delete_outline</mat-icon></button>
            }
          </div>
        } @empty {
          <div class="mudo sem-lote">Nenhum lote anotado.</div>
        }
      </div>

      @if (processando()) {
        <div class="aviso info"><mat-spinner diameter="18" /><span>Preparando a foto…</span></div>
      }
      <input #camera type="file" accept="image/*" capture="environment" hidden (change)="anexar($event, true)" />
      <input #seletor type="file" [accept]="aceitos" multiple hidden (change)="anexar($event, false)" />

      <div class="barra-fixa">
        <div class="barra-conteudo">
          @if (editavel()) {
            <button mat-stroked-button class="btn-barra excluir" (click)="excluir()"><mat-icon>delete_outline</mat-icon> Excluir OP</button>
          }
          <button mat-flat-button class="btn-barra" (click)="concluir()"><mat-icon>done</mat-icon> Concluir OP</button>
        </div>
      </div>
    }
  `,
  styles: `
    .topo { gap: 12px; flex-wrap: nowrap; margin-bottom: 16px; align-items: center; }
    .titulos { flex: 1; min-width: 0; }
    .titulos h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); overflow-wrap: anywhere; }
    .subtitulo { font-size: 13px; color: #64748b; margin-top: 2px; }
    .status-badge { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 9999px; white-space: nowrap; }
    .status-badge.salvo { background: #f0fdf4; border: 1px solid #bbf7d0; color: #15803d; }
    .status-badge.salvando { background: #f0f7ff; border: 1px solid #bae6fd; color: #0369a1; }
    .status-badge.leitura { background: #f8fafc; border: 1px solid #e2e8f0; color: #64748b; }
    .grade { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0 16px; align-items: start; }
    .grade mat-form-field { width: 100%; }
    .topico { padding: 14px 16px; margin-bottom: 12px; }
    .topico.ocorrencia { border-color: var(--cor-nc-borda); box-shadow: 0 0 0 2px rgba(217, 45, 32, 0.08); }
    .cabeca-topico { gap: 10px; margin-bottom: 10px; flex-wrap: nowrap; }
    .num { width: 28px; height: 28px; border-radius: 50%; background: #f1f5f9; color: #475467; display: inline-flex;
      align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
    .nome-topico { font-size: 15px; font-weight: 600; color: #0f172a; }
    .escala { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
    .escala.epi { grid-template-columns: repeat(3, 1fr); }
    .opcao {
      min-height: 54px; border: 2px solid #e2e8f0; background: #fff; border-radius: 10px; cursor: pointer; font: inherit;
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; color: #475467;
      padding: 4px 2px; touch-action: manipulation;
    }
    .opcao:disabled { cursor: default; opacity: 0.7; }
    .opcao .sigla { font-size: 17px; font-weight: 800; line-height: 1; }
    .opcao .rotulo { font-size: 10px; font-weight: 600; text-align: center; line-height: 1.1; }
    .opcao.marcada { color: #fff; border-color: transparent; }
    .opcao.marcada.v-C, .opcao.marcada.v-S { background: var(--cor-c); }
    .opcao.marcada.v-NC, .opcao.marcada.v-N { background: var(--cor-nc); }
    .opcao.marcada.v-P { background: var(--cor-om); }
    .opcao.marcada.v-NA, .opcao.marcada.v-NV, .opcao.marcada.v-NI, .opcao.marcada.v-NP { background: var(--cor-na); }
    .obs { margin-top: 10px; }
    .fotos { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
    .foto { position: relative; }
    .miniatura { width: 64px; height: 64px; border-radius: 10px; overflow: hidden; border: 0; padding: 0; background: #e2e8f0;
      display: flex; align-items: center; justify-content: center; cursor: pointer; }
    .miniatura img { width: 100%; height: 100%; object-fit: cover; }
    .pendente { position: absolute; left: 4px; top: 0; color: #f59e0b; font-size: 14px; }
    .remover { position: absolute; top: -10px; right: -10px; transform: scale(0.75); background: #fff !important; border: 1px solid #e2e8f0; }
    .acoes-topico { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 8px; align-items: center; }
    .espaco { flex: 1; }
    .limpar { color: #64748b; }
    .cabeca-lotes { gap: 8px; margin-bottom: 12px; }
    .lote { gap: 8px; flex-wrap: nowrap; margin-bottom: 8px; align-items: center; }
    .campo-lote { flex: 3; min-width: 0; }
    .campo-data { flex: 2; min-width: 0; }
    .sem-lote { padding: 4px 0 8px; }
    .barra-fixa {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 15; background: #fff; border-top: 1px solid #e2e8f0;
      box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.06); padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    }
    .barra-conteudo { max-width: 1100px; margin: 0 auto; display: flex; gap: 12px; justify-content: space-between; }
    .btn-barra { flex: 1; max-width: 260px; height: 52px; font-size: 15px; }
    .btn-barra.excluir { color: var(--cor-nc) !important; }
    @media (max-width: 480px) {
      .escala { grid-template-columns: repeat(3, 1fr); }
      .opcao { min-height: 50px; }
    }
  `,
})
export class InspecaoOpPagina {
  readonly tid = input.required<string>();
  readonly id = input.required<string>();
  readonly opId = input.required<string>();

  private inspecoes = inject(InspecaoService);
  private sync = inject(SyncService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  protected aceitos = ACEITOS;
  protected dia = signal<InspecaoDia | null>(null);
  protected op = signal<OpInspecao | null>(null);
  protected salvando = signal(false);
  protected salvo = signal(false);
  protected processando = signal(false);
  protected miniaturas = signal<Record<string, string>>({});
  protected obsAberta = signal<Set<number>>(new Set());
  protected editavel = computed(() => this.inspecoes.podeEditar(this.dia()));
  protected respondidos = computed(() => this.op()?.itens.filter((i) => i.valor).length ?? 0);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private sujo = false;
  private alvoFoto: ItemInspecao | null = null;
  private urls: string[] = [];
  private camera = viewChild<ElementRef<HTMLInputElement>>('camera');
  private seletor = viewChild<ElementRef<HTMLInputElement>>('seletor');

  constructor() {
    effect(() => {
      // Foto confirmada pelo servidor: atualiza só os anexos, sem perder o que está sendo digitado.
      if (this.sync.confirmacoes()) untracked(() => this.atualizarAnexos());
    });
    inject(DestroyRef).onDestroy(() => {
      this.salvarAgora();
      this.urls.forEach((u) => URL.revokeObjectURL(u));
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  private async carregar(): Promise<void> {
    let d = await this.inspecoes.local(this.id());
    if (!d) d = (await this.inspecoes.abrir(this.id())).dados ?? undefined;
    const op = d?.ops.find((o) => o.id === this.opId());
    if (!d || !op) {
      this.snack.open('OP não encontrada neste aparelho.', 'OK', { duration: 5000 });
      this.router.navigate(['/inspecoes', this.tid(), 'dia', this.id()]);
      return;
    }
    this.dia.set(d);
    this.op.set(structuredClone(op));
    this.carregarMiniaturas();
  }

  protected escala(i: ItemInspecao) {
    return escalaDe(i.escala);
  }

  protected ocorrencia(i: ItemInspecao): boolean {
    return naoConformeInspecao(i.escala, i.valor);
  }

  // ------------------------------------------------------------- edição (autosave)

  protected mudar(parte: Partial<OpInspecao>): void {
    this.op.update((o) => (o ? { ...o, ...parte } : o));
    this.agendar();
  }

  protected mudarItem(i: ItemInspecao, parte: Partial<ItemInspecao>): void {
    this.op.update((o) => (o ? { ...o, itens: o.itens.map((x) => (x.numero === i.numero ? { ...x, ...parte } : x)) } : o));
    this.agendar();
  }

  protected marcar(i: ItemInspecao, valor: string | null): void {
    this.mudarItem(i, { valor: i.valor === valor ? null : valor });
    this.salvarAgora();
  }

  protected abrirObs(i: ItemInspecao): void {
    this.obsAberta.update((s) => new Set(s).add(i.numero));
  }

  protected novoLote(): void {
    this.mudar({ lotes: [...this.op()!.lotes, { id: uuid(), lote: null, dataFabricacao: null }] });
  }

  protected mudarLote(id: string, parte: { lote?: string; dataFabricacao?: string }): void {
    this.mudar({ lotes: this.op()!.lotes.map((l) => (l.id === id ? { ...l, ...parte } : l)) });
  }

  protected removerLote(id: string): void {
    this.mudar({ lotes: this.op()!.lotes.filter((l) => l.id !== id) });
    this.salvarAgora();
  }

  private agendar(): void {
    this.sujo = true;
    this.salvo.set(false);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.salvarAgora(), ESPERA_AUTOSAVE_MS);
  }

  protected async salvarAgora(): Promise<void> {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    const o = this.op();
    if (!this.sujo || !o || !this.editavel()) return;
    this.sujo = false;
    this.salvando.set(true);
    try {
      await this.inspecoes.salvarOp(this.id(), limparOp(o));
      this.salvo.set(true);
    } catch (e) {
      this.sujo = true;
      this.snack.open('Não foi possível salvar no aparelho: ' + mensagemErro(e), 'OK');
    } finally {
      this.salvando.set(false);
    }
  }

  protected async concluir(): Promise<void> {
    await this.salvarAgora();
    await this.router.navigate(['/inspecoes', this.tid(), 'dia', this.id()]);
  }

  protected async excluir(): Promise<void> {
    if (!confirm('Excluir esta OP da inspeção de hoje? A exclusão fica registrada no histórico.')) return;
    this.sujo = false;
    await this.inspecoes.excluirOp(this.id(), this.opId());
    await this.router.navigate(['/inspecoes', this.tid(), 'dia', this.id()], { replaceUrl: true });
  }

  // ------------------------------------------------------------- fotos e arquivos

  protected foto(i: ItemInspecao): void {
    this.alvoFoto = i;
    this.camera()?.nativeElement.click();
  }

  protected arquivo(i: ItemInspecao): void {
    this.alvoFoto = i;
    this.seletor()?.nativeElement.click();
  }

  protected async anexar(ev: Event, daCamera: boolean): Promise<void> {
    const input = ev.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    const i = this.alvoFoto;
    if (!i || !arquivos.length) return;
    await this.salvarAgora();
    this.processando.set(true);
    try {
      for (const original of arquivos) {
        const arq = await comprimirSeImagem(original);
        const nome = daCamera ? nomeFotoOp(this.op()!, i, arq.name) : arq.name;
        await this.inspecoes.adicionarAnexo(this.id(), this.opId(), i.numero, arq, nome);
      }
      await this.atualizarAnexos();
    } catch (e) {
      this.snack.open('Erro ao anexar: ' + mensagemErro(e), 'OK');
    } finally {
      this.processando.set(false);
    }
  }

  protected async removerAnexo(an: AnexoInspecao): Promise<void> {
    if (!confirm(`Remover "${an.nomeOriginal}"? A remoção fica registrada no histórico.`)) return;
    await this.inspecoes.excluirAnexo(this.id(), an);
    await this.atualizarAnexos();
  }

  protected async abrirAnexo(an: AnexoInspecao): Promise<void> {
    try {
      const url = URL.createObjectURL(new Blob([await this.inspecoes.conteudoAnexo(an)], { type: an.contentType }));
      this.urls.push(url);
      window.open(url, '_blank');
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 5000 });
    }
  }

  /** Copia só os anexos da cópia local para o rascunho (o resto do rascunho é do usuário). */
  private async atualizarAnexos(): Promise<void> {
    const d = await this.inspecoes.local(this.id());
    const local = d?.ops.find((o) => o.id === this.opId());
    if (!d || !local) return;
    this.dia.set(d);
    this.op.update((o) =>
      o ? { ...o, itens: o.itens.map((x) => ({ ...x, anexos: local.itens.find((y) => y.numero === x.numero)?.anexos ?? [] })) } : o,
    );
    this.carregarMiniaturas();
  }

  private async carregarMiniaturas(): Promise<void> {
    for (const an of this.op()?.itens.flatMap((i) => i.anexos) ?? []) {
      if (!an.contentType.startsWith('image/') || an.contentType === 'image/heic' || this.miniaturas()[an.id]) continue;
      try {
        const url = URL.createObjectURL(await this.inspecoes.conteudoAnexo(an));
        this.urls.push(url);
        this.miniaturas.update((m) => ({ ...m, [an.id]: url }));
      } catch {
        /* offline e ainda não baixada: fica o ícone */
      }
    }
  }
}

function limpo(s: string | null | undefined): string | null {
  const t = (s ?? '').trim();
  return t ? t : null;
}

function limparOp(o: OpInspecao): OpInspecao {
  return {
    ...o,
    numeroOp: limpo(o.numeroOp),
    codigoProduto: limpo(o.codigoProduto),
    itens: o.itens.map((i) => ({ ...i, observacao: limpo(i.observacao) })),
    lotes: o.lotes.map((l) => ({ ...l, lote: limpo(l.lote), dataFabricacao: limpo(l.dataFabricacao) })),
  };
}

/** Ex.: "op-4521-topico-02-20261021-101530.jpg". */
function nomeFotoOp(op: OpInspecao, i: ItemInspecao, original: string): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  const ext = original.includes('.') ? original.slice(original.lastIndexOf('.')) : '.jpg';
  const opTxt = (op.numeroOp ?? `ordem${op.ordem}`).replace(/[^\w-]/g, '');
  return `op-${opTxt}-topico-${p(i.numero)}-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}${ext}`;
}
