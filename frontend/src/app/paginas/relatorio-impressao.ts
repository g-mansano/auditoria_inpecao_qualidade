import { DOCUMENT, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input } from '@angular/core';
import { Anexo, AuditoriaDetalhe, Conformidade, Item, SECOES, Secao, opcoesDe, siglaDe } from '../core/modelos';
import { agruparConsecutivos, tamanhoLegivel } from '../core/util';

/** Caixas de margem do @page (texto no rodapé de cada folha) existem no Chromium a partir da versão 131. */
function suportaMargemDePagina(): boolean {
  const m = navigator.userAgent.match(/Chrom(?:e|ium)\/(\d+)/);
  return !!m && +m[1] >= 131;
}

/**
 * Versão para papel da auditoria. Fica invisível na tela e aparece só na impressão
 * (Ctrl+P ou menu "Imprimir"), com as respostas e as fotos anexadas embaixo de cada item.
 */
@Component({
  selector: 'app-relatorio-impressao',
  imports: [DatePipe],
  template: `
    @let a = auditoria();
    @let m = a.modelo;
    <header class="cabecalho">
      <div class="titulo">
        <div class="nome">{{ m.titulo }}</div>
        <div class="ident">{{ m.identificacao }}</div>
      </div>
      <table class="dados">
        <tr><th>{{ m.rotuloAuditoria ?? 'Auditoria Interna' }} Nº</th><td>{{ a.numero }}</td></tr>
        <tr>
          <th>Data</th>
          <td>
            {{ a.dataInicio | date: 'dd/MM/yyyy' }}
            @if (a.dataFim && a.dataFim !== a.dataInicio) { a {{ a.dataFim | date: 'dd/MM/yyyy' }} }
          </td>
        </tr>
        <tr><th>Auditores</th><td class="quebra">{{ a.auditores }}</td></tr>
        @if (a.escopo) {
          <tr><th>Escopo</th><td class="quebra">{{ a.escopo }}</td></tr>
        }
        <tr>
          <th>Situação</th>
          <td>
            {{ a.status === 'CONCLUIDA' ? 'Concluída em ' + (a.concluidoEm | date: 'dd/MM/yyyy HH:mm') : 'Em andamento' }}
            · {{ respondidos() }}/{{ a.itens.length }} respondidos ·
            @for (c of conformidades(); track c.valor) {
              {{ c.curto }}: {{ contagem()[c.valor] }}&nbsp;
            }
          </td>
        </tr>
      </table>
      <div class="legenda">
        @for (c of conformidades(); track c.valor; let ultimo = $last) {
          {{ c.curto }} - {{ c.rotulo }}{{ ultimo ? '' : ' · ' }}
        }
      </div>
    </header>

    @for (s of secoes(); track s.secao) {
      @if (secoes().length > 1) {
        <h2 class="secao">{{ nomeSecao[s.secao] }}</h2>
      }
      <table class="itens">
        <thead>
          <tr>
            <th class="pt">PT</th>
            @if (s.secao === 'CAMPO') {
              <th class="ref">Pessoa</th>
            } @else if (m.rotuloReferencia) {
              <th class="ref">{{ m.rotuloReferencia }}</th>
            }
            <th>Pergunta</th>
            @if (m.usaProcesso && s.secao === 'CHECKLIST') {
              <th class="doc">Processos</th>
            }
            @if (s.secao === 'CAMPO') {
              <th class="doc">Orientação</th>
            } @else if (m.usaDocumentos) {
              <th class="doc">DOC's</th>
            }
            @if (m.usaLocal && s.secao === 'CHECKLIST') {
              <th class="doc">Local</th>
            }
            <th class="ev">Comentários / Evidências</th>
            <th class="conf">Conformidade</th>
          </tr>
        </thead>
        @for (g of s.grupos; track g.nome) {
          <tbody>
            <tr class="grupo"><td [attr.colspan]="colunas(s.secao)">{{ g.nome }}</td></tr>
            @for (i of g.itens; track i.id) {
              <tr class="item">
                <td class="pt">{{ i.numero }}</td>
                @if (s.secao === 'CAMPO') {
                  <td class="ref">{{ i.responsavel ?? '-' }}</td>
                } @else if (m.rotuloReferencia) {
                  <td class="ref">{{ i.clausula ?? '-' }}</td>
                }
                <td class="quebra">{{ i.pergunta }}</td>
                @if (m.usaProcesso && s.secao === 'CHECKLIST') {
                  <td class="quebra">{{ i.processo ?? '-' }}</td>
                }
                @if (s.secao === 'CAMPO') {
                  <td class="quebra">{{ i.orientacao ?? '-' }}</td>
                } @else if (m.usaDocumentos) {
                  <td class="quebra">{{ i.documentos ?? '-' }}</td>
                }
                @if (m.usaLocal && s.secao === 'CHECKLIST') {
                  <td class="quebra">{{ i.local ?? '-' }}</td>
                }
                <td class="quebra">
                  {{ i.evidencias ?? (s.secao === 'CHECKLIST' ? i.orientacao : null) ?? '-' }}
                </td>
                <td class="conf"><b>{{ conformidade(i) }}</b></td>
              </tr>
              @if (i.anexos.length) {
                <tr class="fotos">
                  <td [attr.colspan]="colunas(s.secao)">
                    <div class="grade">
                      @for (an of fotos(i); track an.id) {
                        <figure>
                          @if (imagens()[an.id]; as url) {
                            <img [src]="url" [alt]="an.nomeOriginal" />
                          } @else {
                            <div class="sem-imagem">Imagem não disponível neste aparelho</div>
                          }
                          <figcaption>PT {{ i.numero }} · {{ an.nomeOriginal }}</figcaption>
                        </figure>
                      }
                    </div>
                    @if (outros(i).length) {
                      <div class="outros">
                        Outros anexos:
                        @for (an of outros(i); track an.id) {
                          <span>{{ an.nomeOriginal }} ({{ tamanho(an.tamanho) }})</span>
                        }
                      </div>
                    }
                  </td>
                </tr>
              }
            }
          </tbody>
        }
      </table>
    }

    <section class="assinaturas">
      <div><span></span>Auditor(es)</div>
      <div><span></span>Responsável pela Qualidade</div>
      <div><span></span>Data</div>
    </section>
    <footer class="rodape">
      Impresso em {{ agora | date: 'dd/MM/yyyy HH:mm' }} · {{ m.identificacao }} · Auditoria Nº {{ a.numero }}
    </footer>
    @if (m.rodape && !margemDePagina) {
      <!-- Navegador sem @page/@bottom-center: rodapé fixo, que o navegador repete em cada folha. -->
      <div class="rodape-pagina">{{ m.rodape }}</div>
    }
  `,
  styles: `
    :host { display: none; }
    @media print {
      :host { display: block; color: #000; font: 10pt/1.3 Roboto, Arial, sans-serif; }
    }
    .cabecalho { border: 1px solid #000; padding: 6px 8px; margin-bottom: 8px; }
    .titulo { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 4px; }
    .nome { font-size: 14pt; font-weight: 700; text-transform: uppercase; }
    .ident { font-size: 9pt; }
    .dados { border-collapse: collapse; width: 100%; }
    .dados th { text-align: left; width: 150px; padding: 1px 6px 1px 0; vertical-align: top; }
    .dados td { padding: 1px 0; }
    .legenda { font-size: 8pt; margin-top: 4px; }
    .secao { font-size: 12pt; margin: 12px 0 4px; page-break-after: avoid; break-after: avoid; }
    .itens { border-collapse: collapse; width: 100%; table-layout: fixed; }
    .itens th, .itens td { border: 1px solid #000; padding: 3px 4px; vertical-align: top; }
    .itens thead th { background: #d9d9d9; font-size: 9pt; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .itens thead { display: table-header-group; }
    .pt { width: 28px; text-align: center; }
    .ref { width: 80px; }
    .doc { width: 110px; }
    .ev { width: 32%; }
    .conf { width: 82px; text-align: center; }
    .grupo td {
      background: #f0f0f0; font-weight: 700; -webkit-print-color-adjust: exact; print-color-adjust: exact;
    }
    .item { break-inside: avoid; page-break-inside: avoid; }
    .quebra { white-space: pre-line; word-break: break-word; }
    .fotos td { border-top: 0; }
    .grade { display: flex; flex-wrap: wrap; gap: 6px; }
    figure { margin: 0; width: 62mm; break-inside: avoid; page-break-inside: avoid; }
    figure img { width: 62mm; height: 46mm; object-fit: contain; border: 1px solid #999; display: block; }
    figcaption { font-size: 7pt; word-break: break-all; }
    .sem-imagem {
      width: 62mm; height: 20mm; border: 1px dashed #999; display: flex; align-items: center;
      justify-content: center; font-size: 8pt; text-align: center;
    }
    .outros { font-size: 8pt; margin-top: 4px; }
    .outros span { margin-left: 8px; }
    .assinaturas { display: flex; gap: 24px; margin-top: 32px; break-inside: avoid; page-break-inside: avoid; }
    .assinaturas div { flex: 1; text-align: center; font-size: 9pt; }
    .assinaturas span { display: block; border-bottom: 1px solid #000; height: 32px; margin-bottom: 2px; }
    .rodape { font-size: 7pt; margin-top: 8px; text-align: right; }
    .rodape-pagina { position: fixed; left: 0; right: 0; bottom: 0; text-align: center; font-size: 8pt; background: #fff; }
  `,
})
export class RelatorioImpressao {
  readonly auditoria = input.required<AuditoriaDetalhe>();
  /** anexoId -> URL local da imagem, já baixada pela página. */
  readonly imagens = input<Record<string, string>>({});

  protected conformidades = computed(() => opcoesDe(this.auditoria().modelo));
  protected nomeSecao = SECOES;
  protected tamanho = tamanhoLegivel;
  protected agora = new Date();

  /** Chrome/Edge 131+ desenham texto na margem da página (@page @bottom-center). */
  protected margemDePagina = suportaMargemDePagina();
  private estilo: HTMLStyleElement | null = null;

  constructor() {
    const doc = inject(DOCUMENT);
    // Rodapé de cada folha A4 igual ao do formulário original (ex.: "FQ-101 - Rev. 06 - Data: 02/03/2026").
    effect(() => {
      const texto = this.auditoria().modelo.rodape;
      this.estilo?.remove();
      this.estilo = null;
      if (!texto || !this.margemDePagina) return;
      const css = texto.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
      this.estilo = doc.createElement('style');
      this.estilo.id = 'rodape-impressao';
      this.estilo.textContent =
        `@page { margin-bottom: 14mm; @bottom-center { content: "${css}"; font: 8pt Roboto, Arial, sans-serif; color: #000; } }`;
      doc.head.appendChild(this.estilo);
    });
    inject(DestroyRef).onDestroy(() => this.estilo?.remove());
  }

  protected respondidos = computed(() => this.auditoria().itens.filter((i) => i.conformidade).length);
  protected contagem = computed(() => {
    const c: Record<Conformidade, number> = { C: 0, NC: 0, OM: 0, NA: 0 };
    this.auditoria().itens.forEach((i) => i.conformidade && c[i.conformidade]++);
    return c;
  });
  protected secoes = computed(() =>
    (['CHECKLIST', 'CAMPO'] as Secao[])
      .map((s) => ({
        secao: s,
        grupos: agruparConsecutivos(this.auditoria().itens.filter((i) => (i.secao ?? 'CHECKLIST') === s)),
      }))
      .filter((s) => s.grupos.length > 0),
  );

  /** Total de colunas da tabela (para o colspan das linhas de grupo e de fotos). */
  protected colunas(secao: Secao): number {
    const m = this.auditoria().modelo;
    if (secao === 'CAMPO') return 6;
    return 4 + (m.rotuloReferencia ? 1 : 0) + (m.usaProcesso ? 1 : 0) + (m.usaDocumentos ? 1 : 0) + (m.usaLocal ? 1 : 0);
  }

  protected fotos(i: Item): Anexo[] {
    return i.anexos.filter((a) => a.contentType.startsWith('image/'));
  }

  protected outros(i: Item): Anexo[] {
    return i.anexos.filter((a) => !a.contentType.startsWith('image/'));
  }

  protected conformidade(i: Item): string {
    if (!i.conformidade) return '';
    const status = siglaDe(this.auditoria().modelo, i.conformidade);
    if (!i.rnc) return status;
    const ref = /^\d+$/.test(i.rnc) ? `RNC ${i.rnc}` : i.rnc;
    return ref.toUpperCase().startsWith(status.toUpperCase() + ' ') ? ref : `${status} - ${ref}`;
  }
}
