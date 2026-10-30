import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { mensagemErro } from '../core/interceptors';
import { Cabecalho, FormularioResumo } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';
import { vazioParaNull } from '../core/util';
import { CabecalhoCampos } from './cabecalho-campos';

@Component({
  selector: 'app-nova',
  imports: [FormsModule, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, CabecalhoCampos],
  template: `
    <div class="linha topo">
      <button mat-icon-button [routerLink]="['/formularios', tid()]" class="btn-voltar" aria-label="Voltar">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <div class="titulos">
        <h1>Nova auditoria · {{ form()?.formulario?.nome }}</h1>
        <div class="subtitulo">{{ form()?.formulario?.titulo }} ({{ form()?.formulario?.identificacao }})</div>
      </div>
    </div>

    <div class="cartao">
      <div class="aviso info">
        <mat-icon>info</mat-icon>
        <span class="texto">
          A auditoria será inicializada com as {{ form()?.totalPerguntas }} perguntas da norma selecionada e ficará em modo de edição exclusiva neste aparelho.
        </span>
      </div>

      <app-cabecalho-campos [(valor)]="cab" />

      @if (erro()) {
        <div class="aviso erro">{{ erro() }}</div>
      }

      <div class="linha acoes">
        <button mat-button class="btn-cancelar" [routerLink]="['/formularios', tid()]">Cancelar</button>
        <button mat-flat-button class="btn-criar" (click)="criar()" [disabled]="salvando() || !valido() || !form()">
          <mat-icon>add_circle_outline</mat-icon> Criar auditoria
        </button>
      </div>
    </div>
  `,
  styles: `
    .topo {
      margin-bottom: 20px;
      gap: 12px;
      align-items: center;
      flex-wrap: nowrap;
    }
    .titulos { flex: 1; min-width: 0; }
    .btn-voltar {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      color: #475467;
    }
    .topo h1 {
      margin: 0;
      font-size: 22px;
      color: var(--cor-primaria);
    }
    .subtitulo {
      font-size: 13px;
      color: #64748b;
      margin-top: 2px;
    }
    .acoes {
      justify-content: flex-end;
      gap: 12px;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #f1f5f9;
    }
    .btn-cancelar {
      color: #64748b !important;
    }
    .btn-criar {
      background: var(--cor-primaria) !important;
      color: #ffffff !important;
      height: 44px !important;
      border-radius: 10px !important;
      font-weight: 600 !important;
    }
  `,
})
export class Nova {
  readonly tid = input.required<string>();

  private repo = inject(RepositorioService);
  private router = inject(Router);
  protected form = signal<FormularioResumo | null>(null);
  protected erro = signal<string | null>(null);
  protected salvando = signal(false);
  protected cab = signal<Cabecalho>({
    numero: '',
    dataInicio: new Date().toISOString().slice(0, 10),
    dataFim: null,
    auditores: '',
    escopo: null,
  });

  async ngOnInit(): Promise<void> {
    const f = await this.repo.formulario(+this.tid());
    this.form.set(f);
    if (f?.formulario.escopoPadrao) this.cab.update((c) => ({ ...c, escopo: f.formulario.escopoPadrao }));
  }

  protected valido(): boolean {
    const c = this.cab();
    return !!c.numero.trim() && !!c.dataInicio && !!c.auditores.trim();
  }

  async criar(): Promise<void> {
    this.salvando.set(true);
    this.erro.set(null);
    try {
      const c = this.cab();
      const a = await this.repo.criar(
        { ...c, dataFim: vazioParaNull(c.dataFim), escopo: vazioParaNull(c.escopo) },
        +this.tid(),
      );
      await this.router.navigate(['/auditorias', a.id], { replaceUrl: true });
    } catch (e) {
      this.erro.set(mensagemErro(e));
    } finally {
      this.salvando.set(false);
    }
  }
}
