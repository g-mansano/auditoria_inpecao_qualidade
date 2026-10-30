import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './core/auth.service';

const logado: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.logado() || inject(Router).parseUrl('/login');
};

const admin: CanActivateFn = () => inject(AuthService).admin() || inject(Router).parseUrl('/');

const revisor: CanActivateFn = () => inject(AuthService).podeRevisar() || inject(Router).parseUrl('/');

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./paginas/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [logado],
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./paginas/formularios').then((m) => m.Formularios) },
      { path: 'auditorias', pathMatch: 'full', redirectTo: '' },
      { path: 'formularios/:tid', loadComponent: () => import('./paginas/lista').then((m) => m.Lista) },
      { path: 'formularios/:tid/nova', loadComponent: () => import('./paginas/nova').then((m) => m.Nova) },
      {
        path: 'formularios/:tid/revisao',
        canActivate: [revisor],
        canDeactivate: [(c: { sujo: () => boolean }) => !c.sujo() || confirm('Há alterações não salvas nesta revisão. Sair e descartá-las?')],
        loadComponent: () => import('./paginas/revisao').then((m) => m.RevisaoPagina),
      },
      {
        path: 'formularios/:tid/revisoes',
        loadComponent: () => import('./paginas/revisoes').then((m) => m.RevisoesPagina),
      },
      // FQ-106 (inspeção de OPs)
      { path: 'inspecoes/:tid', loadComponent: () => import('./paginas/inspecoes').then((m) => m.InspecoesPagina) },
      {
        path: 'inspecoes/:tid/dia/:id',
        loadComponent: () => import('./paginas/inspecao-dia').then((m) => m.InspecaoDiaPagina),
      },
      {
        path: 'inspecoes/:tid/dia/:id/op/:opId',
        loadComponent: () => import('./paginas/inspecao-op').then((m) => m.InspecaoOpPagina),
      },
      {
        path: 'inspecoes/:tid/dia/:id/historico',
        loadComponent: () => import('./paginas/inspecao-historico').then((m) => m.InspecaoHistoricoPagina),
      },
      { path: 'auditorias/:id', loadComponent: () => import('./paginas/auditoria').then((m) => m.AuditoriaPagina) },
      {
        path: 'auditorias/:id/itens/:numero',
        loadComponent: () => import('./paginas/item-editor').then((m) => m.ItemEditor),
      },
      {
        path: 'auditorias/:id/historico',
        loadComponent: () => import('./paginas/historico').then((m) => m.HistoricoPagina),
      },
      {
        path: 'usuarios',
        canActivate: [admin],
        loadComponent: () => import('./paginas/usuarios').then((m) => m.Usuarios),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
