import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { InspecaoService } from '../core/inspecao.service';
import { mensagemErro } from '../core/interceptors';
import { HistoricoInspecao } from '../core/modelos';

const EVENTOS: Record<string, string> = {
  CRIACAO: 'Inspeção aberta',
  OP_INCLUIDA: 'OP incluída',
  OP_ALTERADA: 'OP alterada',
  OP_EXCLUIDA: 'OP excluída',
  LOTE_INCLUIDO: 'Lote incluído',
  LOTE_REMOVIDO: 'Lote removido',
  ANEXO_INCLUIDO: 'Foto/arquivo incluído',
  ANEXO_EXCLUIDO: 'Foto/arquivo removido',
  FINALIZACAO: 'Inspeção finalizada',
  REABERTURA: 'Inspeção reaberta',
};

/** Trilha da inspeção: quem fez o quê, quando (gravado automaticamente pelo login). */
@Component({
  selector: 'app-inspecao-historico',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="linha topo">
      <button mat-icon-button [routerLink]="['/inspecoes', tid(), 'dia', id()]" class="btn-voltar" aria-label="Voltar">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>Histórico da inspeção</h1>
    </div>
    @if (erro()) {
      <div class="aviso erro">{{ erro() }}</div>
    } @else if (!registros()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      <div class="cartao lista">
        @for (h of registros(); track h.id) {
          <div class="registro">
            <div class="linha">
              <b>{{ evento(h.evento) }}</b>
              @if (h.numeroOp) {
                <span class="mudo">· OP {{ h.numeroOp }}</span>
              }
              @if (h.campo) {
                <span class="mudo">· {{ h.campo }}</span>
              }
              <span class="espaco"></span>
              <span class="mudo">{{ h.registradoEm | date: 'dd/MM/yyyy HH:mm:ss' }} · {{ h.usuario }}</span>
            </div>
            @if (h.valorAnterior || h.valorNovo) {
              <div class="valores">
                @if (h.valorAnterior) {
                  <span class="antes">{{ h.valorAnterior }}</span>
                  <mat-icon inline>arrow_forward</mat-icon>
                }
                <span class="depois">{{ h.valorNovo ?? '(vazio)' }}</span>
              </div>
            }
          </div>
        } @empty {
          <div class="registro mudo">Sem registros.</div>
        }
      </div>
    }
  `,
  styles: `
    .topo { flex-wrap: nowrap; gap: 12px; margin-bottom: 16px; }
    .topo h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); }
    .espaco { flex: 1; }
    .lista { padding: 0; }
    .registro { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; overflow-wrap: anywhere; }
    .registro:last-child { border-bottom: 0; }
    .valores { margin-top: 4px; white-space: pre-line; }
    .valores mat-icon { vertical-align: middle; font-size: 16px; width: 16px; height: 16px; color: #94a3b8; margin: 0 4px; }
    .antes { color: #9e9e9e; text-decoration: line-through; }
    .depois { color: var(--cor-c); }
  `,
})
export class InspecaoHistoricoPagina {
  readonly tid = input.required<string>();
  readonly id = input.required<string>();
  private inspecoes = inject(InspecaoService);
  protected registros = signal<HistoricoInspecao[] | null>(null);
  protected erro = signal<string | null>(null);

  ngOnInit(): void {
    this.inspecoes
      .historico(this.id())
      .then((r) => this.registros.set(r))
      .catch((e) => this.erro.set(mensagemErro(e)));
  }

  protected evento(e: string): string {
    return EVENTOS[e] ?? e;
  }
}
