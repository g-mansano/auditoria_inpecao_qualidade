import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { FormularioResumo } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';

/** Tela inicial: um cartão por formulário (ISO 13485, ANVISA…). */
@Component({
  selector: 'app-formularios',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="cabecalho-pagina">
      <h1>Checklists de Auditoria Interna</h1>
      <p class="subtitulo">Selecione uma norma para visualizar ou iniciar auditorias em campo</p>
    </div>

    @if (doCache()) {
      <div class="aviso atencao">
        <mat-icon>cloud_off</mat-icon>
        <span class="texto">Modo offline. Exibindo os números salvos neste aparelho.</span>
      </div>
    }

    @if (carregando()) {
      <div class="carregando"><mat-spinner diameter="40" /></div>
    } @else {
      <div class="grade">
        @for (f of formularios(); track f.formulario.id) {
          <a class="cartao formulario" [routerLink]="[f.formulario.tipo === 'INSPECAO_OP' ? '/inspecoes' : '/formularios', f.formulario.id]">
            <div class="topo-card">
              <div class="icone-norma">
                <mat-icon>{{ iconeNorma(f.formulario.nome) }}</mat-icon>
              </div>
              <div class="titulos-card">
                <div class="nome">{{ f.formulario.nome }}</div>
                <div class="identificacao">{{ f.formulario.identificacao }} · {{ f.totalPerguntas }} {{ f.formulario.tipo === 'INSPECAO_OP' ? 'tópicos' : 'itens' }}</div>
              </div>
            </div>

            <div class="descricao">{{ f.formulario.titulo }}</div>

            <div class="rodape-card">
              <div class="contadores">
                <span class="contador-pill andamento">
                  <b>{{ f.emAndamento }}</b> em andamento
                </span>
                <span class="contador-pill concluida">
                  <b>{{ f.concluidas }}</b> {{ f.formulario.tipo === 'INSPECAO_OP' ? 'finalizada(s)' : 'concluída(s)' }}
                </span>
              </div>
              <div class="seta-circulo">
                <mat-icon>arrow_forward</mat-icon>
              </div>
            </div>
          </a>
        } @empty {
          <div class="cartao mudo">Nenhum formulário disponível offline. Conecte-se uma vez para carregar.</div>
        }
      </div>
    }
  `,
  styles: `
    .cabecalho-pagina { margin-bottom: 20px; }
    .cabecalho-pagina h1 { margin-bottom: 4px; }
    .subtitulo { margin: 0; color: #64748b; font-size: 15px; }

    .grade {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
    }

    .formulario {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      color: inherit;
      text-decoration: none;
      margin: 0;
      min-height: 150px;
      padding: 20px;
      cursor: pointer;
    }

    .formulario:hover {
      border-color: #cbd5e1;
      box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
    }

    .formulario:active {
      transform: scale(0.99);
      background: #f8fafc;
    }

    .topo-card {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }

    .icone-norma {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: var(--cor-primaria-clara);
      color: var(--cor-primaria);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .icone-norma mat-icon { font-size: 24px; width: 24px; height: 24px; }

    .titulos-card { min-width: 0; flex: 1; }
    .nome {
      font-size: 19px;
      font-weight: 700;
      color: var(--cor-primaria);
      line-height: 1.2;
    }
    .identificacao {
      font-size: 12px;
      color: #64748b;
      margin-top: 2px;
      font-weight: 500;
    }

    .descricao {
      font-size: 14px;
      color: #334155;
      font-weight: 500;
      margin: 8px 0 16px;
      line-height: 1.4;
    }

    .rodape-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin-top: auto;
      padding-top: 12px;
      border-top: 1px solid #f1f5f9;
    }

    .contadores {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .contador-pill {
      font-size: 12px;
      font-weight: 500;
      padding: 4px 10px;
      border-radius: 9999px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .contador-pill.andamento {
      background: #f0f7ff;
      color: #0369a1;
      border: 1px solid #bae6fd;
    }
    .contador-pill.concluida {
      background: #f0fdf4;
      color: #15803d;
      border: 1px solid #bbf7d0;
    }

    .seta-circulo {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #64748b;
      flex-shrink: 0;
      transition: all 0.2s ease;
    }
    .formulario:hover .seta-circulo {
      background: var(--cor-primaria);
      border-color: var(--cor-primaria);
      color: #fff;
    }
    .seta-circulo mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `,
})
export class Formularios {
  private repo = inject(RepositorioService);
  protected formularios = signal<FormularioResumo[]>([]);
  protected carregando = signal(true);
  protected doCache = signal(false);

  constructor() {
    this.repo.formularios().then((r) => {
      this.formularios.set(r.dados);
      this.doCache.set(r.doCache);
      this.carregando.set(false);
    });
  }

  protected iconeNorma(nome: string): string {
    const n = nome.toUpperCase();
    if (n.includes('13485')) return 'medical_services';
    if (n.includes('ANVISA')) return 'medication';
    if (n.includes('9001')) return 'verified';
    if (n.includes('INMETRO')) return 'straighten';
    if (n.includes('HOUSEKEEPING')) return 'cleaning_services';
    if (n.includes('INSPE')) return 'precision_manufacturing';
    return 'assignment';
  }
}
