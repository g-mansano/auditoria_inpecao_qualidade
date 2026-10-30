import { Component, DestroyRef, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { mensagemErro } from '../core/interceptors';
import { Anexo, Formulario, ItemRemovido, siglaDe } from '../core/modelos';
import { RepositorioService } from '../core/repositorio.service';

export interface DadosRemovidos {
  removidos: ItemRemovido[];
  modelo: Formulario;
}

/**
 * Perguntas que saíram numa revisão e já tinham resposta nesta auditoria. Mostra o que foi
 * respondido e oferece baixar as fotos (PC: Downloads; celular: Downloads ou, com HTTPS, a galeria).
 */
@Component({
  selector: 'app-removidos-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Perguntas removidas na revisão</h2>
    <mat-dialog-content>
      <p class="intro">
        O formulário foi revisado e estas perguntas saíram. As respostas ficam guardadas no histórico da auditoria.
        Baixe as fotos se quiser guardá-las no aparelho.
      </p>
      @for (r of dados.removidos; track r.item.id) {
        <div class="removido">
          <div class="topo">
            <span class="rev">Rev. {{ r.removidoNaRevisao }}</span>
            @if (r.item.conformidade) {
              <span class="selo" [class]="r.item.conformidade">{{ sigla(r) }}</span>
            }
          </div>
          @if (r.item.clausula) {
            <div class="clausula">{{ r.item.clausula }}</div>
          }
          <div class="pergunta">{{ r.item.pergunta }}</div>
          <dl>
            @if (r.item.rnc) {
              <dt>Registro</dt><dd>{{ r.item.rnc }}</dd>
            }
            @if (r.item.evidencias) {
              <dt>Evidências</dt><dd class="pre">{{ r.item.evidencias }}</dd>
            }
            @if (r.item.processo) {
              <dt>Processo</dt><dd>{{ r.item.processo }}</dd>
            }
            @if (r.item.documentos) {
              <dt>DOC's</dt><dd class="pre">{{ r.item.documentos }}</dd>
            }
            @if (r.item.local) {
              <dt>Local</dt><dd>{{ r.item.local }}</dd>
            }
          </dl>
          @if (r.item.anexos.length) {
            <div class="anexos">
              @for (an of r.item.anexos; track an.id) {
                @if (miniaturas()[an.id]; as url) {
                  <img [src]="url" [alt]="an.nomeOriginal" />
                } @else {
                  <span class="arquivo"><mat-icon inline>attach_file</mat-icon> {{ an.nomeOriginal }}</span>
                }
              }
            </div>
            @if (fotos(r).length) {
              <button mat-stroked-button (click)="baixar(fotos(r))" [disabled]="baixando()">
                <mat-icon>download</mat-icon> Baixar {{ fotos(r).length }} foto(s)
              </button>
            }
          }
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (todasFotos().length > 1) {
        <button mat-stroked-button (click)="baixar(todasFotos())" [disabled]="baixando()">
          @if (baixando()) {
            <mat-spinner diameter="16" />
          } @else {
            <mat-icon>download</mat-icon>
          }
          Baixar todas as fotos
        </button>
      }
      <button mat-flat-button [mat-dialog-close]="true">Entendi</button>
    </mat-dialog-actions>
  `,
  styles: `
    .intro { margin: 0 0 12px; font-size: 14px; color: #334155; }
    .removido { border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px 14px; margin-bottom: 12px; }
    .topo { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
    .rev { font-size: 11px; font-weight: 700; color: #b91c1c; background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; padding: 2px 8px; }
    .clausula { font-size: 12px; font-weight: 700; color: var(--cor-secundaria); }
    .pergunta { font-weight: 600; color: #0f172a; margin-bottom: 8px; overflow-wrap: anywhere; }
    dl { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin: 0 0 8px; font-size: 13px; }
    dt { color: #64748b; font-weight: 600; }
    dd { margin: 0; overflow-wrap: anywhere; }
    .pre { white-space: pre-line; }
    .anexos { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .anexos img { width: 72px; height: 72px; object-fit: cover; border-radius: 8px; }
    .arquivo { font-size: 12px; color: #475467; }
    mat-dialog-actions mat-spinner { display: inline-block; margin-right: 6px; }
  `,
})
export class RemovidosDialog {
  protected dados = inject<DadosRemovidos>(MAT_DIALOG_DATA);
  private repo = inject(RepositorioService);
  private snack = inject(MatSnackBar);
  protected miniaturas = signal<Record<string, string>>({});
  protected baixando = signal(false);
  private urls: string[] = [];

  constructor() {
    this.carregarMiniaturas();
    inject(DestroyRef).onDestroy(() => this.urls.forEach((u) => URL.revokeObjectURL(u)));
  }

  protected sigla(r: ItemRemovido): string {
    return r.item.conformidade ? siglaDe(this.dados.modelo, r.item.conformidade) : '';
  }

  protected fotos(r: ItemRemovido): Anexo[] {
    return r.item.anexos.filter((a) => a.contentType.startsWith('image/'));
  }

  protected todasFotos(): Anexo[] {
    return this.dados.removidos.flatMap((r) => this.fotos(r));
  }

  /**
   * Com HTTPS, o menu de compartilhar do sistema oferece "Salvar imagem" (galeria); sem ele,
   * ou se a pessoa cancelar, cada foto é baixada (Downloads, que a galeria do Android também mostra).
   */
  protected async baixar(anexos: Anexo[]): Promise<void> {
    this.baixando.set(true);
    try {
      const arquivos = await Promise.all(
        anexos.map(async (a) => new File([await this.repo.conteudoAnexo(a)], a.nomeOriginal, { type: a.contentType })),
      );
      if (navigator.canShare?.({ files: arquivos })) {
        try {
          await navigator.share({ files: arquivos, title: 'Fotos da auditoria' });
          return;
        } catch (e) {
          if ((e as DOMException)?.name === 'AbortError') return;
          /* compartilhamento indisponível: baixa */
        }
      }
      for (const f of arquivos) {
        const url = URL.createObjectURL(f);
        const link = document.createElement('a');
        link.href = url;
        link.download = f.name;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      }
    } catch (e) {
      this.snack.open(mensagemErro(e), 'OK', { duration: 6000 });
    } finally {
      this.baixando.set(false);
    }
  }

  private async carregarMiniaturas(): Promise<void> {
    for (const an of this.todasFotos()) {
      if (an.contentType === 'image/heic') continue;
      try {
        const url = URL.createObjectURL(await this.repo.conteudoAnexo(an));
        this.urls.push(url);
        this.miniaturas.update((m) => ({ ...m, [an.id]: url }));
      } catch {
        /* offline e ainda não baixada: fica o nome do arquivo */
      }
    }
  }
}
