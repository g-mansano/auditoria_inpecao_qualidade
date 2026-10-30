import { Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Cabecalho } from '../core/modelos';

/** Campos do cabeçalho da auditoria (nº, datas, auditores, escopo). */
@Component({
  selector: 'app-cabecalho-campos',
  imports: [FormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="grade">
      <mat-form-field appearance="outline">
        <mat-label>{{ rotuloNumero() ?? 'Auditoria Interna' }} Nº</mat-label>
        <input matInput [ngModel]="valor().numero" (ngModelChange)="mudar('numero', $event)" maxlength="30" [disabled]="somenteLeitura()" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Data inicial</mat-label>
        <input matInput type="date" [ngModel]="valor().dataInicio" (ngModelChange)="mudar('dataInicio', $event)" [disabled]="somenteLeitura()" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Data final</mat-label>
        <input matInput type="date" [ngModel]="valor().dataFim" (ngModelChange)="mudar('dataFim', $event)" [disabled]="somenteLeitura()" />
      </mat-form-field>
    </div>
    <mat-form-field class="cheio" appearance="outline">
      <mat-label>Auditores (um por linha)</mat-label>
      <textarea matInput rows="3" [ngModel]="valor().auditores" (ngModelChange)="mudar('auditores', $event)" maxlength="1000" [disabled]="somenteLeitura()"></textarea>
    </mat-form-field>
    <mat-form-field class="cheio" appearance="outline">
      <mat-label>Escopo</mat-label>
      <textarea matInput rows="4" [ngModel]="valor().escopo" (ngModelChange)="mudar('escopo', $event)" maxlength="4000" [disabled]="somenteLeitura()"></textarea>
    </mat-form-field>
  `,
  styles: `
    .grade { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0 12px; }
  `,
})
export class CabecalhoCampos {
  readonly valor = model.required<Cabecalho>();
  readonly somenteLeitura = input(false);
  readonly rotuloNumero = input<string | null | undefined>('Auditoria Interna');

  protected mudar<K extends keyof Cabecalho>(campo: K, v: Cabecalho[K]): void {
    this.valor.set({ ...this.valor(), [campo]: v });
  }
}
