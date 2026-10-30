import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { OperacaoInspecao, db } from './db';
import {
  AnexoInspecao,
  HistoricoInspecao,
  InspecaoDia,
  InspecaoResumo,
  OpInspecao,
  TopicoInspecao,
} from './modelos';
import { RedeService } from './rede.service';
import { SyncService } from './sync.service';
import { uuid } from './util';

const CHAVE_TOPICOS = 'checklist.topicos127.';

/** Data local do aparelho no formato do servidor (yyyy-MM-dd). */
export function hojeIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Inspeção de OPs (FQ-106), "offline primeiro" como as auditorias: tudo vai para o
 * IndexedDB e para a fila `filaInspecao`; o SyncService envia quando houver rede.
 * Ids de inspeção, OP, lote e foto nascem no aparelho (uuid), então criar funciona sem rede.
 */
@Injectable({ providedIn: 'root' })
export class InspecaoService {
  private http = inject(HttpClient);
  private rede = inject(RedeService);
  private sync = inject(SyncService);
  private auth = inject(AuthService);

  // ------------------------------------------------------------- leitura

  async listar(): Promise<{ dados: InspecaoResumo[]; doCache: boolean }> {
    if (this.rede.online()) {
      try {
        await this.sync.sincronizar();
        const dados = await firstValueFrom(this.http.get<InspecaoResumo[]>('/api/inspecoes'));
        await db.transaction('rw', db.resumosInspecao, async () => {
          await db.resumosInspecao.clear();
          await db.resumosInspecao.bulkPut(dados);
        });
        return { dados: await this.comLocais(dados), doCache: false };
      } catch {
        /* cai para o cache */
      }
    }
    return { dados: await this.comLocais(await db.resumosInspecao.toArray()), doCache: true };
  }

  /** Junta as inspeções que só existem no aparelho (criadas offline) à lista. */
  private async comLocais(dados: InspecaoResumo[]): Promise<InspecaoResumo[]> {
    const ids = new Set(dados.map((d) => d.id));
    const locais = (await db.inspecoes.toArray()).filter((d) => !ids.has(d.id) && d.local).map((d) => resumir(d));
    return [...locais, ...dados].sort((a, b) => b.data.localeCompare(a.data));
  }

  /** Com alterações pendentes, a cópia local é a verdade; sem pendências e com rede, a do servidor. */
  async abrir(id: string): Promise<{ dados: InspecaoDia | null; doCache: boolean }> {
    if (this.rede.online()) {
      await this.sync.sincronizar();
      if (!(await this.temPendencias(id))) {
        try {
          const dados = await firstValueFrom(this.http.get<InspecaoDia>(`/api/inspecoes/${id}`));
          await this.guardar(dados);
          return { dados: (await db.inspecoes.get(id))!, doCache: false };
        } catch (e) {
          if (e instanceof HttpErrorResponse && e.status === 403) throw e;
          if (e instanceof HttpErrorResponse && e.status === 404) {
            // Excluída (ou não existe mais) no servidor: a cópia deste aparelho não vale mais.
            await db.transaction('rw', db.inspecoes, db.resumosInspecao, db.arquivosInspecao, async () => {
              await db.inspecoes.delete(id);
              await db.resumosInspecao.delete(id);
              await db.arquivosInspecao.where('diaId').equals(id).delete();
            });
            return { dados: null, doCache: false };
          }
        }
      }
    }
    return { dados: (await db.inspecoes.get(id)) ?? null, doCache: true };
  }

  local(id: string): Promise<InspecaoDia | undefined> {
    return db.inspecoes.get(id);
  }

  async operacoes(diaId: string): Promise<OperacaoInspecao[]> {
    return db.filaInspecao.where('diaId').equals(diaId).sortBy('seq');
  }

  async temPendencias(diaId: string): Promise<boolean> {
    return (await db.filaInspecao.where('diaId').equals(diaId).count()) > 0;
  }

  /** Só o dono (quem abriu) e o admin alteram, e só enquanto não finalizada. */
  podeEditar(d: InspecaoDia | null | undefined): boolean {
    const u = this.auth.usuario();
    return !!d && !!u && d.status === 'EM_ANDAMENTO' && (d.inspetor.id === u.id || this.auth.admin());
  }

  historico(id: string): Promise<HistoricoInspecao[]> {
    return firstValueFrom(this.http.get<HistoricoInspecao[]>(`/api/inspecoes/${id}/historico`));
  }

  // ------------------------------------------------------------- inspeção do dia

  /**
   * Abre a inspeção de hoje do usuário logado: a que já existe (servidor ou aparelho) ou uma nova.
   * Sem rede, cria no aparelho com os tópicos guardados da última vez que houve conexão.
   */
  async abrirHoje(templateId: number): Promise<InspecaoDia> {
    const eu = this.auth.usuario()!;
    const hoje = hojeIso();
    // Mesmo id em todas as tentativas: se o servidor criou e a resposta se perdeu, o reenvio cai na mesma inspeção.
    const id = uuid();
    const existente =
      (await db.inspecoes.toArray()).find((d) => d.templateId === templateId && d.data === hoje && d.inspetor.id === eu.id) ??
      null;
    if (this.rede.online()) {
      const lista = await this.listar();
      const doServidor = lista.dados.find((d) => d.templateId === templateId && d.data === hoje && d.inspetor.id === eu.id);
      if (doServidor) return (await this.abrir(doServidor.id)).dados!;
      if (existente) return existente;
      try {
        const d = await firstValueFrom(this.http.put<InspecaoDia>(`/api/inspecoes/${id}`, { templateId, data: hoje }));
        await this.guardar(d);
        return (await db.inspecoes.get(id))!;
      } catch (e) {
        if (!(e instanceof HttpErrorResponse) || e.status !== 0) throw e;
        /* rede caiu no meio: segue offline */
      }
    }
    if (existente) return existente;
    const topicos = this.topicosGuardados(templateId);
    if (!topicos.length) {
      throw new Error('Conecte-se à rede uma vez para baixar o formulário de inspeção para este aparelho.');
    }
    const d: InspecaoDia = {
      id,
      templateId,
      formulario: 'Inspeção de OPs',
      rodape: null,
      data: hoje,
      inspetor: { id: eu.id, nome: eu.nome, username: eu.username },
      status: 'EM_ANDAMENTO',
      finalizadaEm: null,
      criadoEm: new Date().toISOString(),
      atualizadoEm: new Date().toISOString(),
      topicos,
      ops: [],
      local: true,
    };
    await db.transaction('rw', db.inspecoes, db.filaInspecao, async () => {
      await db.inspecoes.put(d);
      await this.enfileirar({ diaId: d.id, chave: 'dia', tipo: 'dia', dados: { templateId, data: hoje } });
    });
    this.disparar();
    return d;
  }

  async finalizar(id: string): Promise<InspecaoDia> {
    await this.exigirFilaVazia(id);
    const d = await firstValueFrom(this.http.post<InspecaoDia>(`/api/inspecoes/${id}/finalizar`, {}));
    await this.guardar(d);
    return (await db.inspecoes.get(id))!;
  }

  /** Admin: exclui no servidor (lógico) e apaga a cópia, a fila e as fotos guardadas neste aparelho. */
  async excluir(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/inspecoes/${id}`));
    await db.transaction('rw', db.inspecoes, db.resumosInspecao, db.filaInspecao, db.arquivosInspecao, async () => {
      await db.inspecoes.delete(id);
      await db.resumosInspecao.delete(id);
      await db.filaInspecao.where('diaId').equals(id).delete();
      await db.arquivosInspecao.where('diaId').equals(id).delete();
    });
    await this.sync.atualizarContagem();
  }

  async reabrir(id: string): Promise<InspecaoDia> {
    const d = await firstValueFrom(this.http.post<InspecaoDia>(`/api/inspecoes/${id}/reabrir`, {}));
    await this.guardar(d);
    return (await db.inspecoes.get(id))!;
  }

  // ------------------------------------------------------------- OPs (funciona offline)

  async novaOp(diaId: string): Promise<OpInspecao> {
    let criada!: OpInspecao;
    await db.transaction('rw', db.inspecoes, db.filaInspecao, async () => {
      const d = await db.inspecoes.get(diaId);
      if (!d) throw new Error('Inspeção não encontrada no aparelho.');
      criada = {
        id: uuid(),
        ordem: Math.max(0, ...d.ops.map((o) => o.ordem)) + 1,
        numeroOp: null,
        codigoProduto: null,
        alteradoEmCliente: new Date().toISOString(),
        itens: d.topicos.map((t) => ({ numero: t.numero, topico: t.texto, escala: t.escala, valor: null, observacao: null, anexos: [] })),
        lotes: [],
      };
      d.ops.push(criada);
      await db.inspecoes.put(d);
      await this.enfileirar({ diaId, chave: `op:${criada.id}`, tipo: 'op', opId: criada.id, dados: corpoOp(criada) });
    });
    this.disparar();
    return criada;
  }

  /** Grava a OP inteira no aparelho e enfileira o envio (edições seguidas viram um envio só). */
  async salvarOp(diaId: string, op: OpInspecao): Promise<void> {
    const alterada = { ...op, alteradoEmCliente: new Date().toISOString() };
    await db.transaction('rw', db.inspecoes, db.filaInspecao, async () => {
      const d = await db.inspecoes.get(diaId);
      const i = d?.ops.findIndex((o) => o.id === op.id) ?? -1;
      if (!d || i < 0) throw new Error('OP não encontrada no aparelho.');
      // Os anexos são mantidos pelo próprio aparelho (adicionarAnexo/excluirAnexo), não pelo rascunho.
      alterada.itens = alterada.itens.map((it) => ({ ...it, anexos: d.ops[i].itens.find((x) => x.numero === it.numero)?.anexos ?? [] }));
      d.ops[i] = alterada;
      d.atualizadoEm = new Date().toISOString();
      await db.inspecoes.put(d);
      await this.enfileirar({ diaId, chave: `op:${op.id}`, tipo: 'op', opId: op.id, dados: corpoOp(alterada) });
    });
    this.disparar();
  }

  async excluirOp(diaId: string, opId: string): Promise<void> {
    await db.transaction('rw', db.inspecoes, db.filaInspecao, db.arquivosInspecao, async () => {
      const d = await db.inspecoes.get(diaId);
      if (d) {
        d.ops = d.ops.filter((o) => o.id !== opId);
        await db.inspecoes.put(d);
      }
      // O que ainda não subiu desta OP (exceto a criação) não precisa mais ir.
      const pendentes = (await db.filaInspecao.where('diaId').equals(diaId).toArray()).filter(
        (o) => o.opId === opId && o.tipo !== 'op' && o.estado === 'pendente',
      );
      for (const o of pendentes) {
        await db.filaInspecao.delete(o.seq!);
        if (o.tipo === 'anexo' && o.anexoId) await db.arquivosInspecao.delete(o.anexoId);
      }
      await this.enfileirar({ diaId, chave: `excluirOp:${opId}`, tipo: 'excluirOp', opId });
    });
    this.disparar();
  }

  // ------------------------------------------------------------- fotos e arquivos

  async adicionarAnexo(diaId: string, opId: string, itemNumero: number, arquivo: File, nome: string): Promise<AnexoInspecao> {
    const anexo: AnexoInspecao = {
      id: uuid(),
      opId,
      itemNumero,
      nomeOriginal: nome,
      contentType: arquivo.type || 'application/octet-stream',
      tamanho: arquivo.size,
      sha256: null,
      enviadoPor: this.auth.usuario()?.username ?? '',
      enviadoEm: new Date().toISOString(),
      pendente: true,
    };
    await db.transaction('rw', db.inspecoes, db.filaInspecao, db.arquivosInspecao, async () => {
      const d = await db.inspecoes.get(diaId);
      const item = d?.ops.find((o) => o.id === opId)?.itens.find((i) => i.numero === itemNumero);
      if (!d || !item) throw new Error('Tópico não encontrado no aparelho.');
      await db.arquivosInspecao.put({ id: anexo.id, diaId, opId, itemNumero, nome, tipo: anexo.contentType, blob: arquivo });
      item.anexos = [...item.anexos, anexo];
      await db.inspecoes.put(d);
      await this.enfileirar({ diaId, chave: `anexo:${anexo.id}`, tipo: 'anexo', opId, itemNumero, anexoId: anexo.id });
    });
    this.disparar();
    return anexo;
  }

  async excluirAnexo(diaId: string, anexo: AnexoInspecao): Promise<void> {
    await db.transaction('rw', db.inspecoes, db.filaInspecao, db.arquivosInspecao, async () => {
      const d = await db.inspecoes.get(diaId);
      const item = d?.ops.find((o) => o.id === anexo.opId)?.itens.find((i) => i.numero === anexo.itemNumero);
      if (d && item) {
        item.anexos = item.anexos.filter((x) => x.id !== anexo.id);
        await db.inspecoes.put(d);
      }
      const upload = await db.filaInspecao.where('chave').equals(`anexo:${anexo.id}`).first();
      if (upload) {
        await db.filaInspecao.delete(upload.seq!);
        await db.arquivosInspecao.delete(anexo.id);
      }
      // Sempre pede a exclusão: o upload pode estar a caminho agora; se a foto nunca chegou, o servidor aceita assim mesmo.
      await this.enfileirar({ diaId, chave: `excluirAnexo:${anexo.id}`, tipo: 'excluirAnexo', anexoId: anexo.id });
    });
    this.disparar();
  }

  async conteudoAnexo(anexo: AnexoInspecao): Promise<Blob> {
    const local = await db.arquivosInspecao.get(anexo.id);
    if (local) return local.blob;
    return firstValueFrom(this.http.get(`/api/inspecoes/anexos/${anexo.id}`, { responseType: 'blob' }));
  }

  // ------------------------------------------------------------- relatório (PDF para o PCP)

  /**
   * Baixa o PDF do dia. Com HTTPS, oferece o compartilhamento do sistema (dá para mandar direto no Teams);
   * em HTTP, salva em Downloads para anexar no Teams.
   */
  async baixarRelatorio(id: string): Promise<void> {
    await this.sync.sincronizar();
    const r: HttpResponse<Blob> = await firstValueFrom(
      this.http.get(`/api/inspecoes/${id}/relatorio.pdf`, { responseType: 'blob', observe: 'response' }),
    );
    const disp = r.headers.get('Content-Disposition') ?? '';
    const nome = decodeURIComponent(disp.match(/filename\*=UTF-8''([^;]+)/)?.[1] ?? 'inspecao-ops.pdf');
    const arquivo = new File([r.body!], nome, { type: 'application/pdf' });
    if (navigator.canShare?.({ files: [arquivo] })) {
      try {
        await navigator.share({ files: [arquivo], title: nome });
        return;
      } catch (e) {
        if ((e as DOMException)?.name === 'AbortError') return;
      }
    }
    const url = URL.createObjectURL(arquivo);
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }

  // ------------------------------------------------------------- fila

  async tentarDeNovo(diaId: string): Promise<void> {
    const ops = await this.operacoes(diaId);
    await Promise.all(ops.map((o) => db.filaInspecao.update(o.seq!, { estado: 'pendente', erro: undefined })));
    await this.sync.sincronizar();
  }

  async descartarRejeitadas(diaId: string): Promise<void> {
    const ops = (await this.operacoes(diaId)).filter((o) => o.estado === 'rejeitada');
    await db.transaction('rw', db.filaInspecao, db.arquivosInspecao, async () => {
      for (const o of ops) {
        await db.filaInspecao.delete(o.seq!);
        if (o.tipo === 'anexo' && o.anexoId) await db.arquivosInspecao.delete(o.anexoId);
      }
    });
    await this.sync.atualizarContagem();
    if (!(await this.temPendencias(diaId))) await db.inspecoes.delete(diaId);
  }

  private async enfileirar(op: Omit<OperacaoInspecao, 'rev' | 'criadoEm' | 'estado'>): Promise<void> {
    const existente =
      op.tipo === 'op'
        ? (await db.filaInspecao.where('chave').equals(op.chave).toArray()).find(
            (o) => o.diaId === op.diaId && o.estado === 'pendente',
          )
        : undefined;
    if (existente) {
      await db.filaInspecao.update(existente.seq!, { dados: op.dados, rev: existente.rev + 1 });
    } else {
      await db.filaInspecao.add({ ...op, rev: 1, criadoEm: new Date().toISOString(), estado: 'pendente' });
    }
  }

  private disparar(): void {
    this.sync.atualizarContagem();
    this.sync.sincronizar();
  }

  private async exigirFilaVazia(id: string): Promise<void> {
    await this.sync.sincronizar();
    const ops = await this.operacoes(id);
    if (ops.length) {
      throw new Error(
        ops.some((o) => o.estado === 'rejeitada')
          ? 'Há alterações recusadas pelo servidor. Resolva-as antes de continuar.'
          : 'Ainda há alterações não enviadas. Verifique a conexão e tente de novo.',
      );
    }
  }

  private topicosGuardados(templateId: number): TopicoInspecao[] {
    try {
      return JSON.parse(localStorage.getItem(CHAVE_TOPICOS + templateId) ?? '[]') as TopicoInspecao[];
    } catch {
      return [];
    }
  }

  /** Guarda a versão do servidor, preservando fotos que ainda só existem no aparelho. */
  private async guardar(d: InspecaoDia): Promise<void> {
    try {
      localStorage.setItem(CHAVE_TOPICOS + d.templateId, JSON.stringify(d.topicos));
    } catch {
      /* sem armazenamento */
    }
    await db.transaction('rw', db.inspecoes, db.arquivosInspecao, async () => {
      const locais = await db.arquivosInspecao.where('diaId').equals(d.id).toArray();
      for (const arq of locais) {
        const item = d.ops.find((o) => o.id === arq.opId)?.itens.find((i) => i.numero === arq.itemNumero);
        if (item && !item.anexos.some((x) => x.id === arq.id)) {
          item.anexos.push({
            id: arq.id,
            opId: arq.opId,
            itemNumero: arq.itemNumero,
            nomeOriginal: arq.nome,
            contentType: arq.tipo,
            tamanho: arq.blob.size,
            sha256: null,
            enviadoPor: this.auth.usuario()?.username ?? '',
            enviadoEm: new Date().toISOString(),
            pendente: true,
          });
        }
      }
      await db.inspecoes.put({ ...d, local: false });
    });
  }
}

/** Corpo do PUT da OP: o estado inteiro (o envio é idempotente). */
export function corpoOp(op: OpInspecao) {
  return {
    ordem: op.ordem,
    numeroOp: op.numeroOp,
    codigoProduto: op.codigoProduto,
    alteradoEmCliente: op.alteradoEmCliente,
    // topico/escala: os que o aparelho mostra; o servidor usa ao criar a OP (pode ter sido criada offline).
    itens: op.itens.map((i) => ({ numero: i.numero, valor: i.valor, observacao: i.observacao, topico: i.topico, escala: i.escala })),
    lotes: op.lotes.map((l) => ({ id: l.id, lote: l.lote, dataFabricacao: l.dataFabricacao })),
  };
}

function resumir(d: InspecaoDia): InspecaoResumo {
  const contagem: Record<string, number> = {};
  let nc = 0;
  for (const o of d.ops) {
    for (const i of o.itens) {
      if (!i.valor) continue;
      contagem[i.valor] = (contagem[i.valor] ?? 0) + 1;
      if (i.escala === 'EPI' ? i.valor === 'N' || i.valor === 'P' : i.valor === 'NC') nc++;
    }
  }
  return {
    id: d.id,
    templateId: d.templateId,
    data: d.data,
    inspetor: d.inspetor,
    status: d.status,
    totalOps: d.ops.length,
    contagem,
    naoConformes: nc,
    atualizadoEm: d.atualizadoEm,
  };
}
