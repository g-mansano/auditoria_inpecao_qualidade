import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Location } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { mensagemErro } from '../core/interceptors';
import { FormularioResumo, HistoricoRevisao } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';

const TIPOS: Record<string, string> = { ALTERADA: 'Alterada', NOVA: 'Nova', REMOVIDA: 'Removida', TITULO: 'Título' };

/** Histórico de revisões do formulário: quem salvou, quando, e cada pergunta antes -> depois. */
@Component({
  selector: 'app-revisoes',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="linha topo">
      <button mat-icon-button (click)="voltar()" class="btn-voltar" aria-label="Voltar"><mat-icon>arrow_back</mat-icon></button>
      <div class="titulos">
        <h1>Histórico de revisões</h1>
        @if (form(); as f) {
          <div class="subtitulo">{{ f.formulario.nome }} · revisão atual: {{ f.formulario.identificacao }}</div>
        }
      </div>
    </div>
    @if (erro()) {
      <div class="aviso erro">{{ erro() }}</div>
    } @else if (!lista()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      @for (r of lista(); track r.id) {
        <div class="cartao revisao">
          <div class="linha cabeca">
            <span class="rev">Rev. {{ r.revisao }}</span>
            <span class="mudo">{{ r.nova ? 'revisão aberta' : 'revisão reescrita' }}</span>
            <span class="espaco"></span>
            <span class="mudo">{{ r.salvoEm | date: 'dd/MM/yyyy HH:mm' }} · {{ r.salvoPor }}</span>
          </div>
          <div class="mudo totais">
            {{ r.alteradas }} alterada(s) · {{ r.novas }} nova(s) · {{ r.removidas }} removida(s) · {{ r.auditorias }} auditoria(s) em
            andamento atualizada(s)
          </div>
          @for (d of r.detalhes; track $index) {
            <div class="detalhe">
              <span class="tipo" [class]="d.tipo">{{ tipo(d.tipo) }}{{ d.numero ? ' · nº ' + d.numero : '' }}</span>
              @if (d.antes) {
                <div class="antes">{{ d.antes }}</div>
              }
              @if (d.depois) {
                <div class="depois">{{ d.depois }}</div>
              }
            </div>
          }
        </div>
      } @empty {
        <div class="cartao mudo vazio">Este formulário ainda não foi revisado pelo app.</div>
      }
    }
  `,
  styles: `
    .topo { gap: 12px; flex-wrap: nowrap; margin-bottom: 16px; align-items: center; }
    .titulos { flex: 1; min-width: 0; }
    .titulos h1 { margin: 0; font-size: 20px; color: var(--cor-primaria); }
    .subtitulo { font-size: 13px; color: #64748b; margin-top: 2px; }
    .espaco { flex: 1; }
    .revisao { padding: 14px 16px; overflow-wrap: anywhere; }
    .cabeca { gap: 8px; }
    .rev { background: var(--cor-secundaria-clara); color: #4c1d95; font-weight: 700; border-radius: 9999px; padding: 2px 10px; }
    .totais { margin: 6px 0 4px; }
    .detalhe { border-top: 1px solid #f1f5f9; padding: 8px 0; font-size: 14px; }
    .tipo { font-size: 11px; font-weight: 700; border-radius: 6px; padding: 2px 6px; }
    .tipo.ALTERADA { background: #fffbeb; color: #b45309; }
    .tipo.NOVA { background: #f0fdf4; color: #15803d; }
    .tipo.REMOVIDA { background: #fef2f2; color: #b91c1c; }
    .tipo.TITULO { background: #f1f5f9; color: #475467; }
    .antes { color: #9e9e9e; text-decoration: line-through; margin-top: 4px; }
    .depois { color: #0f172a; margin-top: 2px; }
    .vazio { text-align: center; padding: 32px; }
  `,
})
export class RevisoesPagina {
  readonly tid = input.required<string>();
  private http = inject(HttpClient);
  private repo = inject(RepositorioService);
  private location = inject(Location);
  protected lista = signal<HistoricoRevisao[] | null>(null);
  protected form = signal<FormularioResumo | null>(null);
  protected erro = signal<string | null>(null);

  ngOnInit(): void {
    this.repo.formulario(+this.tid()).then((f) => this.form.set(f));
    firstValueFrom(this.http.get<HistoricoRevisao[]>(`/api/templates/${this.tid()}/revisoes`))
      .then((r) => this.lista.set(r))
      .catch((e) => this.erro.set(mensagemErro(e)));
  }

  protected tipo(t: string): string {
    return TIPOS[t] ?? t;
  }

  protected voltar(): void {
    this.location.back();
  }
}
