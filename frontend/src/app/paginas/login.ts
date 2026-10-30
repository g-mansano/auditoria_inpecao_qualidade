import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ContaDemo, DemoService } from '../core/demo.service';
import { mensagemErro } from '../core/interceptors';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatFormFieldModule, MatIconModule, MatInputModule, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="container-login">
      <div class="caixa cartao">
        <div class="logo-container">
          <img src="/logo-acme.svg" alt="Acme Indústria (empresa fictícia)" class="logo-login" />
          <div class="subtitulo-login">Qualidade · Auditoria Interna</div>
        </div>

        <form (ngSubmit)="entrar()" class="form-login">
          <mat-form-field class="cheio" appearance="outline">
            <mat-label>Usuário</mat-label>
            <mat-icon matPrefix>person</mat-icon>
            <input matInput name="u" [(ngModel)]="usuario" autocomplete="username" autocapitalize="off" required />
          </mat-form-field>

          <mat-form-field class="cheio" appearance="outline">
            <mat-label>Senha</mat-label>
            <mat-icon matPrefix>lock</mat-icon>
            <input matInput name="s" type="password" [(ngModel)]="senha" autocomplete="current-password" required />
          </mat-form-field>

          @if (erro()) {
            <div class="aviso erro">{{ erro() }}</div>
          }

          @if (carregando()) {
            <mat-progress-bar mode="indeterminate" class="barra-progresso" />
          }

          <button mat-flat-button class="cheio btn-entrar" type="submit" [disabled]="carregando() || !usuario || !senha">
            Entrar no sistema
          </button>
        </form>

        @if (demo.ativo()) {
          <div class="demo">
            <div class="demo-titulo"><mat-icon>science</mat-icon> Demonstração com dados fictícios</div>
            <p class="demo-texto">
              Escolha um perfil para entrar (senha <code>{{ demo.contas()[0].senha }}</code>).
              Os dados voltam ao estado inicial todo dia.
            </p>
            @for (c of demo.contas(); track c.username) {
              <button type="button" class="demo-conta" (click)="entrarComo(c)" [disabled]="carregando()">
                <span class="demo-nome">{{ c.nome }} <small>{{ c.username }}</small></span>
                <span class="demo-desc">{{ c.descricao }}</span>
              </button>
            }
          </div>
        }

        <div class="rodape-normas">
          <span class="normas-titulo">Checklists disponíveis</span>
          <div class="normas-lista">
            <span>ISO 13485</span> · <span>ANVISA</span> · <span>ISO 9001</span> · <span>INMETRO</span> · <span>Housekeeping</span>
            · <span>Inspeção de OPs</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: `
    .container-login {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 16px;
    }
    .caixa {
      max-width: 400px;
      width: 100%;
      padding: 32px 28px;
      margin: 0;
      box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.08), 0 8px 10px -6px rgba(15, 23, 42, 0.04);
    }
    .logo-container {
      text-align: center;
      margin-bottom: 24px;
    }
    .logo-login {
      height: 38px;
      width: auto;
      object-fit: contain;
      display: inline-block;
      margin-bottom: 8px;
    }
    .subtitulo-login {
      font-size: 14px;
      font-weight: 600;
      color: #64748b;
    }
    .form-login {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .btn-entrar {
      margin-top: 12px;
      height: 48px !important;
      border-radius: 12px !important;
      font-size: 15px !important;
      font-weight: 600 !important;
      background: var(--cor-primaria) !important;
      color: #ffffff !important;
    }
    .barra-progresso {
      margin-bottom: 8px;
      border-radius: 4px;
    }
    .rodape-normas {
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #f1f5f9;
      text-align: center;
      font-size: 11px;
      color: #94a3b8;
    }
    .normas-titulo {
      display: block;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin-bottom: 4px;
    }
    .normas-lista span {
      font-weight: 500;
    }
    .demo {
      margin-top: 20px;
      padding: 14px;
      border-radius: 12px;
      background: var(--cor-secundaria-clara);
      border: 1px solid #ddd0fb;
    }
    .demo-titulo {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 700;
      font-size: 14px;
      color: var(--cor-primaria);
    }
    .demo-titulo mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      color: var(--cor-secundaria);
    }
    .demo-texto {
      margin: 6px 0 10px;
      font-size: 12.5px;
      color: #475467;
    }
    .demo-conta {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      width: 100%;
      margin-top: 6px;
      padding: 10px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      background: #ffffff;
      text-align: left;
      font: inherit;
      cursor: pointer;
    }
    .demo-conta:hover:not(:disabled) {
      border-color: var(--cor-secundaria);
    }
    .demo-nome {
      font-weight: 600;
      font-size: 14px;
      color: var(--cor-primaria);
    }
    .demo-nome small {
      font-weight: 500;
      color: #94a3b8;
      margin-left: 4px;
    }
    .demo-desc {
      font-size: 12px;
      color: #64748b;
    }
  `,
})
export class Login implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  protected demo = inject(DemoService);
  protected usuario = '';
  protected senha = '';
  protected erro = signal<string | null>(null);
  protected carregando = signal(false);

  ngOnInit(): void {
    void this.demo.carregar();
  }

  async entrarComo(c: ContaDemo): Promise<void> {
    this.usuario = c.username;
    this.senha = c.senha;
    await this.entrar();
  }

  async entrar(): Promise<void> {
    this.erro.set(null);
    this.carregando.set(true);
    try {
      await this.auth.entrar(this.usuario.trim(), this.senha);
      await this.router.navigateByUrl('/');
    } catch (e) {
      this.erro.set(mensagemErro(e));
    } finally {
      this.carregando.set(false);
    }
  }
}
