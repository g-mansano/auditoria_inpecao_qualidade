import { TextFieldModule } from '@angular/cdk/text-field';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { comprimirSeImagem, nomeFoto } from '../core/imagem';
import { mensagemErro } from '../core/interceptors';
import { Anexo, AuditoriaDetalhe, Conformidade, Item, opcoesDe } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';
import { SyncService } from '../core/sync.service';
import { tamanhoLegivel, vazioParaNull } from '../core/util';

interface Rascunho {
  processo: string;
  documentos: string;
  evidencias: string;
  conformidade: Conformidade | null;
  rnc: string;
  orientacao: string;
  responsavel: string;
  local: string;
}

const ACEITOS = '.jpg,.jpeg,.png,.webp,.gif,.heic,.pdf,.txt,.csv,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.mp4,.mov';
const ESPERA_AUTOSAVE_MS = 700;

@Component({
  selector: 'app-item-editor',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TextFieldModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    @if (!item()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      @let i = item()!;
      <div class="linha topo">
        <button mat-icon-button [routerLink]="['/auditorias', id()]" class="btn-voltar" aria-label="Voltar à lista">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="titulos-item">
          <div class="linha meta-posicao">
            <span class="pill-posicao">Item {{ posicao() }} de {{ total() }}</span>
            @if (i.secao === 'CAMPO') {
              <span class="pill-secao">Roteiro de campo</span>
            } @else if (i.clausula && auditoria()?.modelo?.rotuloReferencia) {
              <span class="clausula-destaque">{{ auditoria()?.modelo?.rotuloReferencia }} {{ i.clausula }}</span>
            }
          </div>
          @if (i.grupo) {
            <div class="grupo-nome">{{ i.grupo }}</div>
          }
        </div>
        <span class="espaco"></span>
        <span class="estado-salvamento">
          @if (!editavel()) {
            <span class="status-badge bloqueado"><mat-icon inline>lock</mat-icon> Somente leitura</span>
          } @else if (salvando()) {
            <span class="status-badge salvando"><mat-icon inline>sync</mat-icon> Salvando…</span>
          } @else if (salvoEm()) {
            <span class="status-badge salvo"><mat-icon inline>check_circle</mat-icon> Salvo</span>
          }
        </span>
      </div>

      <div class="cartao cartao-pergunta">
        <div class="pergunta-texto">{{ i.pergunta }}</div>

        @if (i.alteradaNaRevisao && !r().conformidade) {
          <div class="aviso atencao aviso-revisao">
            <mat-icon>published_with_changes</mat-icon>
            <span class="texto">
              <b>Esta pergunta foi alterada na Rev. {{ i.alteradaNaRevisao }}.</b> Revise e responda de novo.
              Evidências, registro e fotos anteriores foram mantidos.
            </span>
          </div>
        }

        @if (i.secao === 'CAMPO' || r().orientacao) {
          <div class="grade campos-auxiliares">
            <mat-form-field appearance="outline">
              <mat-label>Como verificar / orientação</mat-label>
              <textarea matInput cdkTextareaAutosize [ngModel]="r().orientacao" (ngModelChange)="mudar('orientacao', $event)" maxlength="2000" [disabled]="!editavel()"></textarea>
            </mat-form-field>
            @if (i.secao === 'CAMPO') {
              <mat-form-field appearance="outline">
                <mat-label>Responsável</mat-label>
                <input matInput [ngModel]="r().responsavel" (ngModelChange)="mudar('responsavel', $event)" maxlength="150" [disabled]="!editavel()" list="responsaveis" />
                <datalist id="responsaveis">
                  @for (v of sugestoes().responsaveis; track v) { <option [value]="v"></option> }
                </datalist>
              </mat-form-field>
            }
          </div>
        }

        <!-- Seletor de Conformidade Tablet/Mobile First -->
        <div class="conformidade-wrapper">
          <div class="rotulo-seletor">Classificação do Item:</div>
          <div class="conformidade-grade" role="radiogroup" aria-label="Conformidade" [style.grid-template-columns]="'repeat(' + conformidades().length + ', 1fr)'">
            @for (c of conformidades(); track c.valor) {
              <button
                type="button"
                role="radio"
                class="opcao-touch"
                [attr.aria-label]="c.rotulo"
                [class]="c.valor"
                [class.marcada]="r().conformidade === c.valor"
                [attr.aria-checked]="r().conformidade === c.valor"
                [disabled]="!editavel()"
                (click)="marcar(c.valor)"
              >
                <div class="sigla-container">
                  <span class="sigla">{{ c.curto }}</span>
                  @if (r().conformidade === c.valor) {
                    <mat-icon class="icone-check">check</mat-icon>
                  }
                </div>
                <span class="nome">{{ c.rotulo }}</span>
              </button>
            }
          </div>
        </div>

        @if (r().conformidade === 'NC' || r().rnc) {
          <div class="caixa-rnc">
            <mat-form-field class="cheio" appearance="outline">
              <mat-label>Nº do registro de Não Conformidade (RNC, NC, FI…){{ auditoria()?.modelo?.exigeRegistroNc === false ? ' — opcional' : '' }}</mat-label>
              <mat-icon matPrefix class="icone-rnc">flag</mat-icon>
              <input matInput [ngModel]="r().rnc" (ngModelChange)="mudar('rnc', $event)" placeholder="ex.: RNC 014/26, NC 031/26" maxlength="60" [disabled]="!editavel()" list="rncs" />
              <datalist id="rncs">
                @for (v of sugestoes().rncs; track v) { <option [value]="v"></option> }
              </datalist>
            </mat-form-field>
          </div>
        }

        <mat-form-field class="cheio campo-evidencias" appearance="outline">
          <mat-label>Comentários / Evidências Objetivas</mat-label>
          <textarea
            matInput
            cdkTextareaAutosize
            cdkAutosizeMinRows="4"
            [ngModel]="r().evidencias"
            (ngModelChange)="mudar('evidencias', $event)"
            placeholder="Descreva as evidências observadas, documentos verificados ou apontamentos..."
            maxlength="20000"
            [disabled]="!editavel()"
          ></textarea>
        </mat-form-field>

        @if (editavel() && sugestoesEvidencia().length) {
          <div class="rapidos-container">
            <span class="rapidos-rotulo">Inserção rápida:</span>
            <div class="rapidos">
              @for (s of sugestoesEvidencia(); track s) {
                <button mat-stroked-button type="button" class="btn-rapido" (click)="inserirEvidencia(s)">+ {{ s }}</button>
              }
            </div>
          </div>
        }

        @if (auditoria()?.modelo?.usaLocal) {
          <mat-form-field class="cheio" appearance="outline">
            <mat-label>Local / área verificada</mat-label>
            <mat-icon matPrefix>location_on</mat-icon>
            <input matInput [ngModel]="r().local" (ngModelChange)="mudar('local', $event)" maxlength="200" [disabled]="!editavel()" list="locais" placeholder="ex.: Almoxarifado" />
            <datalist id="locais">
              @for (v of sugestoes().locais; track v) { <option [value]="v"></option> }
            </datalist>
          </mat-form-field>
        }

        @if (i.secao !== 'CAMPO' && ((auditoria()?.modelo?.usaProcesso ?? true) || (auditoria()?.modelo?.usaDocumentos ?? true))) {
          <div class="grade campos-documentos">
            @if (auditoria()?.modelo?.usaProcesso ?? true) {
              <mat-form-field appearance="outline">
                <mat-label>Processo</mat-label>
                <input matInput [ngModel]="r().processo" (ngModelChange)="mudar('processo', $event)" maxlength="500" [disabled]="!editavel()" list="processos" />
                <datalist id="processos">
                  @for (v of sugestoes().processos; track v) { <option [value]="v"></option> }
                </datalist>
              </mat-form-field>
            }
            @if (auditoria()?.modelo?.usaDocumentos ?? true) {
              <mat-form-field appearance="outline">
                <mat-label>Documentos verificados (DOC's)</mat-label>
                <textarea matInput cdkTextareaAutosize [ngModel]="r().documentos" (ngModelChange)="mudar('documentos', $event)" maxlength="2000" [disabled]="!editavel()"></textarea>
              </mat-form-field>
            }
          </div>
        }
      </div>

      <!-- Seção de Anexos e Evidências Fotográficas -->
      <div class="cartao cartao-anexos">
        <div class="linha cabecalho-anexos">
          <div>
            <b>Evidências e Fotos</b>
            <span class="mudo contador-anexos">({{ anexos().length }})</span>
          </div>
          <span class="espaco"></span>
          @if (editavel()) {
            <button mat-stroked-button class="btn-anexo" (click)="camera.click()">
              <mat-icon>photo_camera</mat-icon> Tirar Foto
            </button>
            <button mat-stroked-button class="btn-anexo" (click)="arquivo.click()">
              <mat-icon>attach_file</mat-icon> Anexar
            </button>
          }
          <input #camera type="file" accept="image/*" capture="environment" hidden (change)="anexar($event, true)" />
          <input #arquivo type="file" [accept]="aceitos" multiple hidden (change)="anexar($event, false)" />
        </div>

        @if (processandoAnexo()) {
          <div class="aviso info processando-aviso">
            <mat-spinner diameter="18" />
            <span>Compactando e preparando imagem…</span>
          </div>
        }

        <div class="anexos-grid">
          @for (an of anexos(); track an.id) {
            <div class="anexo-card">
              <button type="button" class="miniatura" (click)="abrir(an)" [attr.aria-label]="'Abrir ' + an.nomeOriginal">
                @if (miniaturas()[an.id]; as url) {
                  <img [src]="url" [alt]="an.nomeOriginal" />
                } @else {
                  <mat-icon>{{ icone(an) }}</mat-icon>
                }
              </button>
              <div class="info-anexo">
                <div class="nome-arquivo" [title]="an.nomeOriginal">{{ an.nomeOriginal }}</div>
                <div class="mudo meta-arquivo">
                  {{ tamanho(an.tamanho) }} · {{ an.enviadoEm | date: 'dd/MM HH:mm' }}
                  @if (an.pendente) {
                    <span class="tag-pendente">pendente de envio</span>
                  }
                </div>
              </div>
              @if (editavel()) {
                <button mat-icon-button (click)="remover(an)" class="btn-remover-anexo" aria-label="Remover anexo">
                  <mat-icon>delete_outline</mat-icon>
                </button>
              }
            </div>
          } @empty {
            <div class="sem-anexos">
              <mat-icon>add_a_photo</mat-icon>
              <span>
                Nenhuma evidência anexada a este item.
                @if (editavel()) {
                  Toque em <b>Tirar Foto</b> para registrar agora.
                }
              </span>
            </div>
          }
        </div>
      </div>

      @if (i.atualizadoPor) {
        <div class="mudo rodape-registro">
          Última alteração sincronizada: <b>{{ i.atualizadoPor }}</b> em {{ i.atualizadoEm | date: 'dd/MM/yyyy HH:mm' }}
        </div>
      }

      <!-- Barra Fixa de Navegação Ergonômica para Tablet/Mobile -->
      <div class="navegacao-fixa">
        <div class="navegacao-conteudo">
          <button mat-stroked-button class="btn-nav anterior" [disabled]="!anterior()" (click)="ir(anterior())">
            <mat-icon>chevron_left</mat-icon> Anterior
          </button>
          <span class="indicador-passo">{{ posicao() }} / {{ total() }}</span>
          <button mat-flat-button class="btn-nav proximo" [disabled]="!proximo()" (click)="ir(proximo())">
            Próximo <mat-icon iconPositionEnd>chevron_right</mat-icon>
          </button>
        </div>
      </div>
    }
  `,
  styles: `
    .topo {
      flex-wrap: nowrap;
      gap: 12px;
      margin-bottom: 16px;
      align-items: center;
    }
    .btn-voltar {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      color: #475467;
    }
    .titulos-item { min-width: 0; }
    .meta-posicao {
      gap: 8px;
      margin-bottom: 2px;
    }
    .pill-posicao {
      font-size: 13px;
      font-weight: 700;
      color: var(--cor-primaria);
    }
    .pill-secao {
      font-size: 12px;
      color: #475467;
      background: #f1f5f9;
      padding: 2px 8px;
      border-radius: 6px;
    }
    .clausula-destaque {
      font-size: 13px;
      font-weight: 700;
      color: var(--cor-secundaria);
    }
    .grupo-nome {
      font-size: 13px;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .espaco { flex: 1; }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 9999px;
    }
    .status-badge.salvo {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #15803d;
    }
    .status-badge.salvando {
      background: #f0f7ff;
      border: 1px solid #bae6fd;
      color: #0369a1;
    }
    .status-badge.bloqueado {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      color: #64748b;
    }

    .cartao-pergunta { padding: 22px; }
    .pergunta-texto {
      font-size: 17px;
      font-weight: 600;
      color: #0f172a;
      line-height: 1.45;
      margin-bottom: 20px;
    }

    /* Campos lado a lado: mesma largura e topo alinhado; empilham em telas estreitas. */
    .grade {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 0 16px;
      align-items: start;
    }
    .grade mat-form-field { width: 100%; }
    .campos-auxiliares { margin-bottom: 12px; }
    .conformidade-wrapper { margin: 16px 0 20px; }
    .rotulo-seletor {
      font-size: 13px;
      font-weight: 700;
      color: #475467;
      margin-bottom: 10px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .conformidade-grade {
      display: grid;
      gap: 12px;
    }

    .opcao-touch {
      border: 2px solid #e2e8f0;
      background: #ffffff;
      border-radius: 12px;
      min-height: 72px;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      font: inherit;
      color: #475467;
      padding: 8px 6px;
      transition: all 0.15s ease;
      touch-action: manipulation;
    }
    .opcao-touch:hover:not(:disabled) {
      border-color: #cbd5e1;
      background: #f8fafc;
    }
    .opcao-touch:active:not(:disabled) {
      transform: scale(0.98);
    }
    .opcao-touch:disabled {
      cursor: default;
      opacity: 0.65;
    }

    .sigla-container {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .opcao-touch .sigla {
      font-size: 22px;
      font-weight: 800;
      line-height: 1;
    }
    .icone-check {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .opcao-touch .nome {
      font-size: 12px;
      font-weight: 600;
      text-align: center;
      line-height: 1.2;
    }

    /* Estados Ativos da Conformidade */
    .opcao-touch.marcada.C {
      background: var(--cor-c-fundo);
      border-color: var(--cor-c);
      color: var(--cor-c);
      box-shadow: 0 0 0 2px rgba(18, 124, 54, 0.15);
    }
    .opcao-touch.marcada.NC {
      background: var(--cor-nc-fundo);
      border-color: var(--cor-nc);
      color: var(--cor-nc);
      box-shadow: 0 0 0 2px rgba(217, 45, 32, 0.15);
    }
    .opcao-touch.marcada.OM {
      background: var(--cor-om-fundo);
      border-color: var(--cor-om);
      color: var(--cor-om);
      box-shadow: 0 0 0 2px rgba(181, 71, 8, 0.15);
    }
    .opcao-touch.marcada.NA {
      background: var(--cor-na-fundo);
      border-color: var(--cor-na);
      color: var(--cor-na);
      box-shadow: 0 0 0 2px rgba(71, 84, 103, 0.15);
    }

    .caixa-rnc {
      background: #fef3f2;
      border: 1px solid #fecdca;
      border-radius: 12px;
      padding: 12px 14px 4px;
      margin-bottom: 16px;
    }
    .icone-rnc { color: #d92d20; }

    .campo-evidencias { margin-top: 8px; }

    .rapidos-container {
      margin: -4px 0 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .rapidos-rotulo {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
    }
    .rapidos {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .btn-rapido {
      border-radius: 9999px !important;
      border-color: #cbd5e1 !important;
      color: #334155 !important;
      font-size: 13px !important;
      font-weight: 500 !important;
    }

    .campos-documentos { margin-top: 12px; }

    .cartao-anexos { padding: 20px; }
    .cabecalho-anexos {
      align-items: center;
      margin-bottom: 16px;
    }
    .contador-anexos { font-weight: normal; margin-left: 4px; }
    .btn-anexo {
      border-radius: 10px !important;
      border-color: #cbd5e1 !important;
    }

    .processando-aviso {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;
      font-size: 13px;
    }

    .anexos-grid {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .anexo-card {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 10px 14px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
    }
    .miniatura {
      width: 56px;
      height: 56px;
      border-radius: 10px;
      overflow: hidden;
      background: #e2e8f0;
      border: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      cursor: pointer;
      padding: 0;
    }
    .miniatura img { width: 100%; height: 100%; object-fit: cover; }
    .info-anexo { flex: 1; min-width: 0; }
    .nome-arquivo {
      font-size: 14px;
      font-weight: 600;
      color: #1e293b;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .meta-arquivo { font-size: 12px; margin-top: 2px; }
    .tag-pendente {
      color: #b45309;
      font-weight: 600;
      background: #fffbeb;
      border: 1px solid #fde68a;
      padding: 1px 6px;
      border-radius: 4px;
      margin-left: 4px;
    }
    .btn-remover-anexo { color: #94a3b8; }
    .btn-remover-anexo:hover { color: #d92d20; }

    .sem-anexos {
      padding: 24px;
      text-align: center;
      color: #94a3b8;
      font-size: 14px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
    }
    .sem-anexos mat-icon { font-size: 32px; width: 32px; height: 32px; }

    .rodape-registro {
      margin: 4px 4px 16px;
      font-size: 12px;
      color: #94a3b8;
    }

    .navegacao-fixa {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 15;
      background: #ffffff;
      border-top: 1px solid #e2e8f0;
      padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
      box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.06);
    }
    /* Mesma largura do conteúdo (main), para os botões não irem às bordas em telas largas. */
    .navegacao-conteudo {
      max-width: 1100px;
      margin: 0 auto;
      box-sizing: border-box;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .btn-nav {
      flex: 1;
      max-width: 240px;
      height: 48px !important;
      border-radius: 12px !important;
      font-size: 15px !important;
      font-weight: 600 !important;
    }
    .btn-nav.anterior {
      border-color: #cbd5e1 !important;
      color: #334155 !important;
    }
    .btn-nav.proximo {
      background: var(--cor-primaria) !important;
      color: #ffffff !important;
    }
    .indicador-passo {
      font-size: 14px;
      font-weight: 700;
      color: #64748b;
    }

    @media (max-width: 480px) {
      .opcao-touch .nome { display: none; }
      .opcao-touch { min-height: 58px; }
      .indicador-passo { display: none; }
    }
  `,
})
export class ItemEditor {
  readonly id = input.required<string>();
  readonly numero = input.required<string>();

  private repo = inject(RepositorioService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);
  private sync = inject(SyncService);

  protected conformidades = computed(() => opcoesDe(this.auditoria()?.modelo));
  protected aceitos = ACEITOS;
  protected tamanho = tamanhoLegivel;

  protected auditoria = signal<AuditoriaDetalhe | null>(null);
  protected r = signal<Rascunho>({
    processo: '',
    documentos: '',
    evidencias: '',
    conformidade: null,
    rnc: '',
    orientacao: '',
    responsavel: '',
    local: '',
  });
  protected salvando = signal(false);
  protected salvoEm = signal<Date | null>(null);
  protected processandoAnexo = signal(false);
  protected miniaturas = signal<Record<string, string>>({});
  /** Separado do item: o item é reaproveitado para não apagar o rascunho em edição. */
  protected anexos = signal<Anexo[]>([]);

  protected item = computed(() => this.auditoria()?.itens.find((i) => i.numero === +this.numero()) ?? null);
  /** Itens da mesma seção (checklist ou roteiro de campo): a navegação fica dentro dela. */
  protected itensSecao = computed(() => {
    const secao = this.item()?.secao ?? 'CHECKLIST';
    return (this.auditoria()?.itens ?? []).filter((i) => (i.secao ?? 'CHECKLIST') === secao);
  });
  protected total = computed(() => this.itensSecao().length);
  protected posicao = computed(() => this.itensSecao().findIndex((i) => i.numero === +this.numero()) + 1);
  protected sugestoesEvidencia = computed(() => this.auditoria()?.modelo?.sugestoesEvidencia ?? []);
  protected editavel = computed(() => this.repo.souEditor(this.auditoria()) && this.auditoria()?.status === 'EM_ANDAMENTO');
  protected anterior = computed(() => this.vizinho(-1));
  protected proximo = computed(() => this.vizinho(1));
  protected sugestoes = computed(() => {
    const itens = this.auditoria()?.itens ?? [];
    const unicos = (v: (string | null)[]) => [...new Set(v.filter((x): x is string => !!x))].sort();
    return {
      processos: unicos(itens.map((i) => i.processo)),
      rncs: unicos(itens.map((i) => i.rnc)),
      responsaveis: unicos(itens.map((i) => i.responsavel)),
      locais: unicos(itens.map((i) => i.local)),
    };
  });

  private timer: ReturnType<typeof setTimeout> | null = null;
  private sujo = false;
  private urls: string[] = [];

  constructor() {
    // Ao trocar de item (anterior/próximo), carrega o rascunho do novo item.
    effect(() => {
      const i = this.item();
      if (i) untracked(() => this.preencher(i));
    });
    effect(() => {
      this.numero();
      untracked(() => this.carregar());
    });
    // Anexo/alteração confirmados pelo servidor: atualiza a tela sem perder o que está sendo digitado.
    effect(() => {
      if (this.sync.confirmacoes()) untracked(() => this.atualizarLocal());
    });
    inject(DestroyRef).onDestroy(() => {
      this.salvarAgora();
      this.urls.forEach((u) => URL.revokeObjectURL(u));
    });
  }

  private async carregar(): Promise<void> {
    const id = +this.id();
    let a = await this.repo.local(id);
    if (!a) a = (await this.repo.abrir(id)).dados ?? undefined;
    if (!a) {
      this.snack.open('Auditoria indisponível offline.', 'OK', { duration: 5000 });
      this.router.navigate(['/']);
      return;
    }
    this.auditoria.set(a);
  }

  private preencher(i: Item): void {
    this.r.set({
      processo: i.processo ?? '',
      documentos: i.documentos ?? '',
      evidencias: i.evidencias ?? '',
      conformidade: i.conformidade,
      rnc: i.rnc ?? '',
      orientacao: i.orientacao ?? '',
      responsavel: i.responsavel ?? '',
      local: i.local ?? '',
    });
    this.salvoEm.set(null);
    this.anexos.set(i.anexos);
    this.carregarMiniaturas(i);
  }

  protected marcar(c: Conformidade): void {
    this.mudar('conformidade', this.r().conformidade === c ? null : c);
    this.salvarAgora();
  }

  protected mudar<K extends keyof Rascunho>(campo: K, valor: Rascunho[K]): void {
    this.r.update((x) => ({ ...x, [campo]: valor }));
    this.sujo = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.salvarAgora(), ESPERA_AUTOSAVE_MS);
  }

  protected async salvarAgora(): Promise<void> {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    const i = this.item();
    if (!this.sujo || !i || !this.editavel()) return;
    this.sujo = false;
    const r = this.r();
    this.salvando.set(true);
    try {
      await this.repo.salvarItem(+this.id(), i.id, {
        processo: vazioParaNull(r.processo),
        documentos: vazioParaNull(r.documentos),
        evidencias: vazioParaNull(r.evidencias),
        conformidade: r.conformidade,
        rnc: vazioParaNull(r.rnc),
        orientacao: vazioParaNull(r.orientacao),
        responsavel: vazioParaNull(r.responsavel),
        local: vazioParaNull(r.local),
        perguntaVista: i.pergunta,
      });
      await this.atualizarLocal();
      this.salvoEm.set(new Date());
    } catch (e) {
      this.sujo = true;
      this.snack.open('Não foi possível salvar no aparelho: ' + mensagemErro(e), 'OK');
    } finally {
      this.salvando.set(false);
    }
  }

  /** Recarrega do IndexedDB sem sobrescrever o rascunho que está sendo digitado. */
  private async atualizarLocal(): Promise<void> {
    const a = await this.repo.local(+this.id());
    if (!a) return;
    const atual = this.item();
    const novo = a.itens.find((x) => x.id === atual?.id);
    if (atual && novo) {
      // Mantém o mesmo objeto de item para não disparar o preenchimento do rascunho.
      Object.assign(atual, { ...novo, ...this.paraItem() });
      a.itens = a.itens.map((x) => (x.id === atual.id ? atual : x));
      this.anexos.set([...atual.anexos]);
    }
    this.auditoria.set({ ...a });
  }

  private paraItem(): Partial<Item> {
    const r = this.r();
    return {
      processo: vazioParaNull(r.processo),
      documentos: vazioParaNull(r.documentos),
      evidencias: vazioParaNull(r.evidencias),
      conformidade: r.conformidade,
      rnc: vazioParaNull(r.rnc),
      orientacao: vazioParaNull(r.orientacao),
      responsavel: vazioParaNull(r.responsavel),
      local: vazioParaNull(r.local),
    };
  }

  /** Acrescenta um texto rápido (ex.: "Visual") ao campo de evidências. */
  protected inserirEvidencia(texto: string): void {
    const atual = this.r().evidencias.trim();
    this.mudar('evidencias', atual ? `${atual}\n${texto}` : texto);
  }

  protected async ir(numero: number | null): Promise<void> {
    if (numero == null) return;
    await this.salvarAgora();
    await this.router.navigate(['/auditorias', this.id(), 'itens', numero], { replaceUrl: true });
    window.scrollTo({ top: 0 });
  }

  private vizinho(passo: number): number | null {
    const itens = this.itensSecao();
    const idx = itens.findIndex((i) => i.numero === +this.numero());
    return itens[idx + passo]?.numero ?? null;
  }

  // ---------------------------------------------------------- anexos

  protected async anexar(ev: Event, daCamera: boolean): Promise<void> {
    const input = ev.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    const i = this.item();
    if (!i || !arquivos.length) return;
    await this.salvarAgora();
    this.processandoAnexo.set(true);
    try {
      for (const original of arquivos) {
        const arq = await comprimirSeImagem(original);
        const nome = daCamera ? nomeFoto(i.numero, arq.name) : arq.name;
        await this.repo.adicionarAnexo(+this.id(), i.id, arq, nome);
      }
      await this.atualizarLocal();
      this.carregarMiniaturas(this.item()!);
    } catch (e) {
      this.snack.open('Erro ao anexar: ' + mensagemErro(e), 'OK');
    } finally {
      this.processandoAnexo.set(false);
    }
  }

  protected async remover(an: Anexo): Promise<void> {
    if (!confirm(`Remover o anexo "${an.nomeOriginal}"? A remoção fica registrada no histórico.`)) return;
    await this.repo.excluirAnexo(+this.id(), an);
    await this.atualizarLocal();
  }

  protected async abrir(an: Anexo): Promise<void> {
    try {
      const blob = await this.repo.conteudoAnexo(an);
      const url = URL.createObjectURL(new Blob([blob], { type: an.contentType }));
      this.urls.push(url);
      if (an.contentType.startsWith('image/') || an.contentType === 'application/pdf') {
        window.open(url, '_blank');
      } else {
        const a = document.createElement('a');
        a.href = url;
        a.download = an.nomeOriginal;
        a.click();
      }
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 5000 });
    }
  }

  protected icone(an: Anexo): string {
    const t = an.contentType;
    if (t.startsWith('image/')) return 'image';
    if (t.startsWith('video/')) return 'movie';
    if (t === 'application/pdf') return 'picture_as_pdf';
    if (t.includes('sheet') || t.includes('excel') || t === 'text/csv') return 'table_chart';
    if (t.includes('word') || t.includes('text')) return 'description';
    return 'insert_drive_file';
  }

  private async carregarMiniaturas(i: Item): Promise<void> {
    for (const an of i.anexos) {
      if (!an.contentType.startsWith('image/') || an.contentType === 'image/heic' || this.miniaturas()[an.id]) continue;
      try {
        const blob = await this.repo.conteudoAnexo(an);
        const url = URL.createObjectURL(blob);
        this.urls.push(url);
        this.miniaturas.update((m) => ({ ...m, [an.id]: url }));
      } catch {
        /* offline e ainda não baixado: fica o ícone */
      }
    }
  }
}
