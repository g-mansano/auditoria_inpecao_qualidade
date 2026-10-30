import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { mensagemErro } from '../core/interceptors';
import { Historico } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';

const EVENTOS: Record<string, string> = {
  CRIACAO: 'Auditoria criada',
  CABECALHO: 'Cabeçalho alterado',
  ITEM: 'Item alterado',
  CHECKOUT: 'Check-out (início da edição)',
  CHECKIN: 'Edição liberada',
  CHECKOUT_FORCADO: 'Edição assumida à força',
  ANEXO_INCLUIDO: 'Anexo incluído',
  ANEXO_EXCLUIDO: 'Anexo removido',
  CONCLUSAO: 'Auditoria concluída',
  REABERTURA: 'Auditoria reaberta',
  EXCLUSAO: 'Auditoria excluída',
};

@Component({
  selector: 'app-historico',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="linha topo">
      <button mat-icon-button [routerLink]="['/auditorias', id()]" class="btn-voltar" aria-label="Voltar"><mat-icon>arrow_back</mat-icon></button>
      <h1>Histórico de alterações</h1>
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
              @if (h.itemNumero) {
                <span class="mudo">· item {{ h.itemNumero }}</span>
              }
              @if (h.campo) {
                <span class="mudo">· {{ h.campo }}</span>
              }
              <span class="espaco"></span>
              <span class="mudo">{{ h.registradoEm | date: 'dd/MM/yyyy HH:mm:ss' }} · {{ h.usuario }}</span>
            </div>
            @if (h.campo) {
              <div class="valores">
                <span class="antes">{{ h.valorAnterior ?? '(vazio)' }}</span>
                <mat-icon inline>arrow_forward</mat-icon>
                <span class="depois">{{ h.valorNovo ?? '(vazio)' }}</span>
              </div>
            } @else if (h.valorNovo) {
              <div class="mudo">{{ h.valorNovo }}</div>
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
    .registro { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; }
    .registro:last-child { border-bottom: 0; }
    /* Hashes SHA-256 e nomes de arquivo longos quebram em vez de alargar a página. */
    .registro { overflow-wrap: anywhere; }
    .valores { margin-top: 4px; white-space: pre-line; }
    .valores mat-icon { vertical-align: middle; font-size: 16px; width: 16px; height: 16px; color: #94a3b8; margin: 0 4px; }
    .antes { color: #9e9e9e; text-decoration: line-through; }
    .depois { color: var(--cor-c); }
  `,
})
export class HistoricoPagina {
  readonly id = input.required<string>();
  private repo = inject(RepositorioService);
  protected registros = signal<Historico[] | null>(null);
  protected erro = signal<string | null>(null);

  ngOnInit(): void {
    this.repo
      .historico(+this.id())
      .then((r) => this.registros.set(r))
      .catch((e) => this.erro.set(mensagemErro(e)));
  }

  protected evento(e: string): string {
    return EVENTOS[e] ?? e;
  }
}
