import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Operacao, db } from './db';
import {
  Anexo,
  AuditoriaDetalhe,
  AuditoriaResumo,
  Cabecalho,
  FormularioResumo,
  Historico,
  ItemAlteracao,
  ResultadoRevisao,
  Revisao,
  SalvarRevisao,
} from './modelos';
import { RedeService } from './rede.service';
import { SyncService } from './sync.service';
import { AuthService } from './auth.service';
import { dispositivoId, uuid } from './util';

/**
 * Acesso a dados "offline primeiro": as edições vão para o banco local (IndexedDB) e
 * para a fila; o SyncService envia ao servidor quando houver conexão.
 */
@Injectable({ providedIn: 'root' })
export class RepositorioService {
  private http = inject(HttpClient);
  private rede = inject(RedeService);
  private sync = inject(SyncService);
  private auth = inject(AuthService);

  // ------------------------------------------------------------- leitura

  /** Formulários ativos (ISO 13485, ANVISA…) com contagem de auditorias; sem rede, a última cópia. */
  async formularios(): Promise<{ dados: FormularioResumo[]; doCache: boolean }> {
    const chave = 'checklist.formularios';
    if (this.rede.online()) {
      try {
        const dados = await firstValueFrom(this.http.get<FormularioResumo[]>('/api/templates'));
        try {
          localStorage.setItem(chave, JSON.stringify(dados));
        } catch {
          /* sem armazenamento: segue sem cache */
        }
        return { dados, doCache: false };
      } catch {
        /* cai para o cache */
      }
    }
    try {
      return { dados: JSON.parse(localStorage.getItem(chave) ?? '[]') as FormularioResumo[], doCache: true };
    } catch {
      return { dados: [], doCache: true };
    }
  }

  async formulario(id: number): Promise<FormularioResumo | null> {
    return (await this.formularios()).dados.find((f) => f.formulario.id === id) ?? null;
  }

  /** Lista do servidor (e guarda cópia); sem rede, a última cópia. */
  async listar(): Promise<{ dados: AuditoriaResumo[]; doCache: boolean }> {
    if (this.rede.online()) {
      try {
        const dados = await firstValueFrom(this.http.get<AuditoriaResumo[]>('/api/auditorias'));
        await db.transaction('rw', db.resumos, async () => {
          await db.resumos.clear();
          await db.resumos.bulkPut(dados);
        });
        return { dados, doCache: false };
      } catch {
        /* cai para o cache */
      }
    }
    const dados = await db.resumos.toArray();
    dados.sort((a, b) => b.dataInicio.localeCompare(a.dataInicio) || b.id - a.id);
    return { dados, doCache: true };
  }

  /**
   * Abre a auditoria. Com alterações pendentes, a cópia local é a verdade (envia antes de
   * atualizar). Sem pendências e com rede, busca a versão do servidor.
   */
  async abrir(id: number): Promise<{ dados: AuditoriaDetalhe | null; doCache: boolean }> {
    if (this.rede.online()) {
      await this.sync.sincronizar();
      if (!(await this.temPendencias(id))) {
        try {
          const dados = await firstValueFrom(this.http.get<AuditoriaDetalhe>(`/api/auditorias/${id}`));
          await this.guardar(dados);
          return { dados: (await db.auditorias.get(id))!, doCache: false };
        } catch {
          /* cai para o cache */
        }
      }
    }
    return { dados: (await db.auditorias.get(id)) ?? null, doCache: true };
  }

  async local(id: number): Promise<AuditoriaDetalhe | undefined> {
    return db.auditorias.get(id);
  }

  async historico(id: number): Promise<Historico[]> {
    return firstValueFrom(this.http.get<Historico[]>(`/api/auditorias/${id}/historico`));
  }

  async operacoes(auditoriaId: number): Promise<Operacao[]> {
    return db.fila.where('auditoriaId').equals(auditoriaId).sortBy('seq');
  }

  async temPendencias(auditoriaId: number): Promise<boolean> {
    return (await db.fila.where('auditoriaId').equals(auditoriaId).count()) > 0;
  }

  /** Este aparelho tem o check-out? */
  souEditor(a: AuditoriaDetalhe | AuditoriaResumo | null | undefined): boolean {
    const u = this.auth.usuario();
    return !!a?.editor && !!u && a.editor.usuarioId === u.id && a.editor.dispositivo === dispositivoId();
  }

  // ------------------------------------------------------------- ações que exigem rede

  async criar(dados: Cabecalho, templateId: number): Promise<AuditoriaDetalhe> {
    const a = await firstValueFrom(this.http.post<AuditoriaDetalhe>('/api/auditorias', { ...dados, templateId }));
    await this.guardar(a);
    return a;
  }

  async checkout(id: number, forcar = false): Promise<AuditoriaDetalhe> {
    const a = await firstValueFrom(
      this.http.post<AuditoriaDetalhe>(`/api/auditorias/${id}/checkout${forcar ? '?forcar=true' : ''}`, {}),
    );
    await this.guardar(a);
    return a;
  }

  /** Envia tudo e libera a edição para outro inspetor. */
  async checkin(id: number): Promise<AuditoriaDetalhe> {
    await this.exigirFilaVazia(id);
    const a = await firstValueFrom(this.http.post<AuditoriaDetalhe>(`/api/auditorias/${id}/checkin`, {}));
    await this.guardar(a);
    return a;
  }

  async concluir(id: number): Promise<AuditoriaDetalhe> {
    await this.exigirFilaVazia(id);
    const a = await firstValueFrom(this.http.post<AuditoriaDetalhe>(`/api/auditorias/${id}/concluir`, {}));
    await this.guardar(a);
    return a;
  }

  /** Exclui no servidor e apaga a cópia, a fila e os arquivos guardados neste aparelho. */
  async excluir(id: number): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/auditorias/${id}`));
    await db.transaction('rw', db.auditorias, db.resumos, db.fila, db.arquivos, async () => {
      await db.auditorias.delete(id);
      await db.resumos.delete(id);
      await db.fila.where('auditoriaId').equals(id).delete();
      await db.arquivos.where('auditoriaId').equals(id).delete();
    });
    await this.sync.atualizarContagem();
  }

  async reabrir(id: number): Promise<AuditoriaDetalhe> {
    const a = await firstValueFrom(this.http.post<AuditoriaDetalhe>(`/api/auditorias/${id}/reabrir`, {}));
    await this.guardar(a);
    return a;
  }

  /** O inspetor viu a janela de perguntas removidas por uma revisão. */
  async marcarRemovidosVistos(id: number): Promise<AuditoriaDetalhe> {
    const a = await firstValueFrom(this.http.post<AuditoriaDetalhe>(`/api/auditorias/${id}/removidos/vistos`, {}));
    await this.guardar(a);
    return (await db.auditorias.get(id)) ?? a;
  }

  // ------------------------------------------------------------- revisão do formulário (só online)

  revisao(templateId: number): Promise<Revisao> {
    return firstValueFrom(this.http.get<Revisao>(`/api/templates/${templateId}/revisao`));
  }

  /** O que o salvamento faria (resumo antes de confirmar), sem gravar. */
  previaRevisao(templateId: number, req: SalvarRevisao): Promise<ResultadoRevisao> {
    return firstValueFrom(this.http.post<ResultadoRevisao>(`/api/templates/${templateId}/revisao/previa`, req));
  }

  salvarRevisao(templateId: number, req: SalvarRevisao): Promise<ResultadoRevisao> {
    return firstValueFrom(this.http.put<ResultadoRevisao>(`/api/templates/${templateId}/revisao`, req));
  }

  // ------------------------------------------------------------- edição local (funciona offline)

  async salvarItem(auditoriaId: number, itemId: number, alt: Omit<ItemAlteracao, 'alteradoEmCliente'>): Promise<void> {
    const dados: ItemAlteracao = { ...alt, alteradoEmCliente: new Date().toISOString() };
    await db.transaction('rw', db.auditorias, db.fila, async () => {
      const a = await db.auditorias.get(auditoriaId);
      const i = a?.itens.find((x) => x.id === itemId);
      if (!a || !i) throw new Error('Item não encontrado no aparelho.');
      Object.assign(i, dados);
      // Respondeu de novo uma pergunta alterada numa revisão: o aviso some (o servidor faz o mesmo).
      if (dados.conformidade) i.alteradaNaRevisao = null;
      await db.auditorias.put(a);
      await this.enfileirar({ auditoriaId, chave: `item:${itemId}`, tipo: 'item', itemId, dados });
    });
    this.disparar();
  }

  async salvarCabecalho(auditoriaId: number, cab: Cabecalho): Promise<void> {
    await db.transaction('rw', db.auditorias, db.fila, async () => {
      const a = await db.auditorias.get(auditoriaId);
      if (!a) throw new Error('Auditoria não encontrada no aparelho.');
      Object.assign(a, cab);
      await db.auditorias.put(a);
      await this.enfileirar({ auditoriaId, chave: 'cabecalho', tipo: 'cabecalho', dados: cab });
    });
    this.disparar();
  }

  async adicionarAnexo(auditoriaId: number, itemId: number, arquivo: File, nome?: string): Promise<Anexo> {
    const id = uuid();
    const anexo: Anexo = {
      id,
      itemId,
      nomeOriginal: nome ?? arquivo.name,
      contentType: arquivo.type || 'application/octet-stream',
      tamanho: arquivo.size,
      sha256: null,
      enviadoPor: this.auth.usuario()?.username ?? '',
      enviadoEm: new Date().toISOString(),
      pendente: true,
    };
    await db.transaction('rw', db.auditorias, db.fila, db.arquivos, async () => {
      const a = await db.auditorias.get(auditoriaId);
      const i = a?.itens.find((x) => x.id === itemId);
      if (!a || !i) throw new Error('Item não encontrado no aparelho.');
      await db.arquivos.put({ id, auditoriaId, itemId, nome: anexo.nomeOriginal, tipo: anexo.contentType, blob: arquivo });
      i.anexos = [...i.anexos, anexo];
      await db.auditorias.put(a);
      await this.enfileirar({ auditoriaId, chave: `anexo:${id}`, tipo: 'anexo', itemId, anexoId: id });
    });
    this.disparar();
    return anexo;
  }

  async excluirAnexo(auditoriaId: number, anexo: Anexo): Promise<void> {
    await db.transaction('rw', db.auditorias, db.fila, db.arquivos, async () => {
      const a = await db.auditorias.get(auditoriaId);
      const i = a?.itens.find((x) => x.id === anexo.itemId);
      if (a && i) {
        i.anexos = i.anexos.filter((x) => x.id !== anexo.id);
        await db.auditorias.put(a);
      }
      const upload = await db.fila.where('chave').equals(`anexo:${anexo.id}`).first();
      if (upload) {
        // Ainda não subiu: basta esquecer.
        await db.fila.delete(upload.seq!);
        await db.arquivos.delete(anexo.id);
      } else {
        await this.enfileirar({ auditoriaId, chave: `excluir:${anexo.id}`, tipo: 'excluirAnexo', anexoId: anexo.id });
      }
    });
    this.disparar();
  }

  /** Conteúdo do anexo: do aparelho, se ainda não subiu; senão do servidor. */
  async conteudoAnexo(anexo: Anexo): Promise<Blob> {
    const local = await db.arquivos.get(anexo.id);
    if (local) return local.blob;
    return firstValueFrom(this.http.get(`/api/anexos/${anexo.id}`, { responseType: 'blob' }));
  }

  // ------------------------------------------------------------- fila

  async tentarDeNovo(auditoriaId: number): Promise<void> {
    const ops = await this.operacoes(auditoriaId);
    await Promise.all(ops.map((o) => db.fila.update(o.seq!, { estado: 'pendente', erro: undefined })));
    await this.sync.sincronizar();
  }

  /** Descarta as alterações deste aparelho que o servidor recusou e recarrega do servidor. */
  async descartarRejeitadas(auditoriaId: number): Promise<void> {
    const ops = (await this.operacoes(auditoriaId)).filter((o) => o.estado === 'rejeitada');
    await db.transaction('rw', db.fila, db.arquivos, async () => {
      for (const o of ops) {
        await db.fila.delete(o.seq!);
        if (o.tipo === 'anexo' && o.anexoId) await db.arquivos.delete(o.anexoId);
      }
    });
    await this.sync.atualizarContagem();
    if (!(await this.temPendencias(auditoriaId))) await db.auditorias.delete(auditoriaId);
  }

  private async enfileirar(op: Omit<Operacao, 'rev' | 'criadoEm' | 'estado'>): Promise<void> {
    const existente =
      op.tipo === 'item' || op.tipo === 'cabecalho'
        ? (await db.fila.where('chave').equals(op.chave).toArray()).find(
            (o) => o.auditoriaId === op.auditoriaId && o.estado === 'pendente',
          )
        : undefined;
    if (existente) {
      await db.fila.update(existente.seq!, { dados: op.dados, rev: existente.rev + 1 });
    } else {
      await db.fila.add({ ...op, rev: 1, criadoEm: new Date().toISOString(), estado: 'pendente' });
    }
  }

  private disparar(): void {
    this.sync.atualizarContagem();
    this.sync.sincronizar();
  }

  private async exigirFilaVazia(id: number): Promise<void> {
    await this.sync.sincronizar();
    const ops = await this.operacoes(id);
    if (ops.length) {
      const rej = ops.some((o) => o.estado === 'rejeitada');
      throw new Error(
        rej
          ? 'Há alterações recusadas pelo servidor. Resolva-as antes de continuar.'
          : 'Ainda há alterações não enviadas. Verifique a conexão e tente de novo.',
      );
    }
  }

  /** Guarda a versão do servidor, preservando anexos que ainda só existem no aparelho. */
  private async guardar(a: AuditoriaDetalhe): Promise<void> {
    await db.transaction('rw', db.auditorias, db.arquivos, async () => {
      const locais = await db.arquivos.where('auditoriaId').equals(a.id).toArray();
      for (const arq of locais) {
        // O item pode ter saído numa revisão: o anexo vai junto para a janela de removidas.
        const i =
          a.itens.find((x) => x.id === arq.itemId) ?? a.removidos?.find((r) => r.item.id === arq.itemId)?.item;
        if (i && !i.anexos.some((x) => x.id === arq.id)) {
          i.anexos.push({
            id: arq.id,
            itemId: arq.itemId,
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
      await db.auditorias.put(a);
    });
  }
}
