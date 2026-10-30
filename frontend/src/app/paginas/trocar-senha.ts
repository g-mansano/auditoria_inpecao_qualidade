import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom } from 'rxjs';
import { mensagemErro } from '../core/interceptors';

@Component({
  selector: 'app-trocar-senha',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Trocar senha</h2>
    <mat-dialog-content>
      <mat-form-field class="cheio">
        <mat-label>Senha atual</mat-label>
        <input matInput type="password" [(ngModel)]="atual" autocomplete="current-password" />
      </mat-form-field>
      <mat-form-field class="cheio">
        <mat-label>Nova senha (mín. 8 caracteres)</mat-label>
        <input matInput type="password" [(ngModel)]="nova" autocomplete="new-password" />
      </mat-form-field>
      <mat-form-field class="cheio">
        <mat-label>Repita a nova senha</mat-label>
        <input matInput type="password" [(ngModel)]="repete" autocomplete="new-password" />
      </mat-form-field>
      @if (erro()) {
        <div class="aviso erro">{{ erro() }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button (click)="salvar()" [disabled]="!atual || nova.length < 8 || nova !== repete">
        Salvar
      </button>
    </mat-dialog-actions>
  `,
})
export class TrocarSenhaDialog {
  private http = inject(HttpClient);
  private ref = inject(MatDialogRef<TrocarSenhaDialog>);
  protected atual = '';
  protected nova = '';
  protected repete = '';
  protected erro = signal<string | null>(null);

  async salvar(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/senha', { senhaAtual: this.atual, novaSenha: this.nova }));
      this.ref.close(true);
    } catch (e) {
      this.erro.set(mensagemErro(e));
    }
  }
}
