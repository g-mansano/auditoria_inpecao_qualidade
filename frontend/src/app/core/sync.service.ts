import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ArquivoLocal, Operacao, OperacaoInspecao, db } from './db';
import { mensagemErro } from './interceptors';
import { Anexo, AnexoInspecao, Item } from './modelos';
import { RedeService } from './rede.service';

/**
 * Envia a fila de alterações locais ao servidor, em ordem. Falha de rede: para e tenta
 * depois. Recusa do servidor (ex.: perdeu o check-out): marca a operação como rejeitada,
 * mantém os dados no aparelho e segue para as próximas.
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private http = inject(HttpClient);
  private rede = inject(RedeService);

  readonly pendentes = signal(0);
  readonly rejeitadas = signal(0);
  readonly sincronizando = signal(false);
  /** Incrementa quando o servidor confirma algo; telas abertas recarregam a cópia local. */
  readonly confirmacoes = signal(0);
  private rodando: Promise<void> | null = null;

  constructor() {
    this.rede.aoReconectar(() => this.sincronizar());
    setInterval(() => this.sincronizar(), 60_000);
    this.atualizarContagem();
  }

  /** Dispara o envio; chamadas concorrentes aguardam a mesma execução. */
  sincronizar(): Promise<void> {
    if (!this.rodando) {
      this.rodando = this.executar().finally(() => {
        this.rodando = null;
        this.atualizarContagem();
        // Edição feita durante o envio (ex.: a mesma OP mudou): roda de novo em vez de esperar 60 s.
        if (this.pedidoDuranteEnvio) {
          this.pedidoDuranteEnvio = false;
          this.sincronizar();
        }
      });
    } else {
      this.pedidoDuranteEnvio = true;
    }
    return this.rodando;
  }

  private pedidoDuranteEnvio = false;

  async atualizarContagem(): Promise<void> {
    const todas: { estado: string }[] = [...(await db.fila.toArray()), ...(await db.filaInspecao.toArray())];
    this.pendentes.set(todas.filter((o) => o.estado === 'pendente').length);
    this.rejeitadas.set(todas.filter((o) => o.estado === 'rejeitada').length);
  }

  private async executar(): Promise<void> {
    if (!this.rede.online()) return;
    this.sincronizando.set(true);
    try {
      const ops = (await db.fila.orderBy('seq').toArray()).filter((o) => o.estado === 'pendente');
      for (const op of ops) {
        try {
          await this.enviar(op);
          await this.concluir(op);
          this.confirmacoes.update((n) => n + 1);
        } catch (e) {
          if (e instanceof HttpErrorResponse && (e.status === 0 || e.status === 401 || e.status >= 500)) {
            return; // sem rede, sessão expirada ou servidor fora: tenta depois
          }
          if (e instanceof HttpErrorResponse && e.status === 410 && e.error?.codigo === 'ITEM_REMOVIDO') {
            // A pergunta saiu numa revisão: a resposta já está guardada no servidor (janela de removidas).
            await db.fila.delete(op.seq!);
            this.confirmacoes.update((n) => n + 1);
            continue;
          }
          await db.fila.update(op.seq!, { estado: 'rejeitada', erro: mensagemErro(e) });
        }
      }
      await this.executarInspecao();
    } finally {
      this.sincronizando.set(false);
    }
  }

  // ------------------------------------------------------------- inspeção de OPs (FQ-106)

  private async executarInspecao(): Promise<void> {
    const ops = (await db.filaInspecao.orderBy('seq').toArray()).filter((o) => o.estado === 'pendente');
    for (const op of ops) {
      try {
        await this.enviarInspecao(op);
        await db.transaction('rw', db.filaInspecao, async () => {
          const atual = await db.filaInspecao.get(op.seq!);
          if (atual && atual.rev === op.rev) await db.filaInspecao.delete(op.seq!);
        });
        this.confirmacoes.update((n) => n + 1);
      } catch (e) {
        if (
          e instanceof HttpErrorResponse &&
          (e.status === 0 || e.status === 401 || e.status >= 500 || e.error?.codigo === 'CONFLITO')
        ) {
          return; // sem rede, sessão expirada, servidor fora ou gravação simultânea: tenta depois
        }
        if (e instanceof HttpErrorResponse && e.status === 409 && e.error?.codigo === 'INSPECAO_EXISTENTE') {
          // O servidor já tem a inspeção de hoje deste inspetor (outro aparelho, ou resposta perdida):
          // passa tudo o que foi feito aqui para ela, em vez de recusar.
          const existente = /\(([0-9a-fA-F-]{36})\)/.exec(e.error?.mensagem ?? '')?.[1];
          if (existente) {
            await this.juntarInspecao(op.diaId, existente, op.seq!);
            continue;
          }
        }
        if (e instanceof HttpErrorResponse && e.status === 410) {
          await db.filaInspecao.delete(op.seq!); // OP já excluída no servidor: nada a enviar
          continue;
        }
        await db.filaInspecao.update(op.seq!, { estado: 'rejeitada', erro: mensagemErro(e) });
      }
    }
  }

  /** Move a inspeção criada neste aparelho (e a fila dela) para a que já existe no servidor. */
  private async juntarInspecao(deId: string, paraId: string, seqDia: number): Promise<void> {
    await db.transaction('rw', db.inspecoes, db.filaInspecao, db.arquivosInspecao, async () => {
      await db.filaInspecao.delete(seqDia);
      for (const o of await db.filaInspecao.where('diaId').equals(deId).toArray()) {
        await db.filaInspecao.update(o.seq!, { diaId: paraId });
      }
      for (const a of await db.arquivosInspecao.where('diaId').equals(deId).toArray()) {
        await db.arquivosInspecao.update(a.id, { diaId: paraId });
      }
      const local = await db.inspecoes.get(deId);
      if (local) {
        const destino = await db.inspecoes.get(paraId);
        const ops = [...(destino?.ops ?? []), ...local.ops.filter((o) => !destino?.ops.some((x) => x.id === o.id))];
        await db.inspecoes.put({ ...(destino ?? local), id: paraId, ops, local: false });
        await db.inspecoes.delete(deId);
      }
    });
    this.confirmacoes.update((n) => n + 1);
  }

  private async enviarInspecao(op: OperacaoInspecao): Promise<void> {
    const base = `/api/inspecoes/${op.diaId}`;
    switch (op.tipo) {
      case 'dia':
        await firstValueFrom(this.http.put(base, op.dados));
        await db.transaction('rw', db.inspecoes, async () => {
          const d = await db.inspecoes.get(op.diaId);
          if (d?.local) await db.inspecoes.put({ ...d, local: false });
        });
        return;
      case 'op':
        await firstValueFrom(this.http.put(`${base}/ops/${op.opId}`, op.dados));
        return;
      case 'excluirOp':
        await firstValueFrom(this.http.delete(`${base}/ops/${op.opId}`));
        return;
      case 'anexo': {
        const arq = await db.arquivosInspecao.get(op.anexoId!);
        if (!arq) return;
        const form = new FormData();
        form.append('anexoId', arq.id);
        form.append('arquivo', arq.blob, arq.nome);
        const salvo = await firstValueFrom(
          this.http.post<AnexoInspecao>(`${base}/ops/${op.opId}/itens/${op.itemNumero}/anexos`, form),
        );
        await db.transaction('rw', db.inspecoes, db.arquivosInspecao, async () => {
          const d = await db.inspecoes.get(op.diaId);
          const item = d?.ops.find((o) => o.id === op.opId)?.itens.find((i) => i.numero === op.itemNumero);
          if (d && item) {
            item.anexos = item.anexos.map((x) => (x.id === salvo.id ? { ...salvo, pendente: false } : x));
            await db.inspecoes.put(d);
          }
          await db.arquivosInspecao.delete(arq.id);
        });
        return;
      }
      case 'excluirAnexo':
        await firstValueFrom(this.http.delete(`/api/inspecoes/anexos/${op.anexoId}`));
        return;
    }
  }

  private async enviar(op: Operacao): Promise<void> {
    const base = `/api/auditorias/${op.auditoriaId}`;
    switch (op.tipo) {
      case 'item': {
        const item = await firstValueFrom(this.http.put<Item>(`${base}/itens/${op.itemId}`, op.dados));
        await this.aplicarItemServidor(op.auditoriaId, item);
        return;
      }
      case 'cabecalho':
        await firstValueFrom(this.http.put(base, op.dados));
        return;
      case 'anexo': {
        const arq = await db.arquivos.get(op.anexoId!);
        if (!arq) return; // anexo removido antes de subir
        const form = new FormData();
        form.append('id', arq.id);
        form.append('arquivo', arq.blob, arq.nome);
        const salvo = await firstValueFrom(this.http.post<Anexo>(`${base}/itens/${op.itemId}/anexos`, form));
        await this.marcarAnexoEnviado(arq, salvo);
        return;
      }
      case 'excluirAnexo':
        await firstValueFrom(this.http.delete(`/api/anexos/${op.anexoId}`));
        return;
    }
  }

  /** Remove a operação, a menos que o usuário tenha editado o mesmo alvo durante o envio. */
  private async concluir(op: Operacao): Promise<void> {
    await db.transaction('rw', db.fila, async () => {
      const atual = await db.fila.get(op.seq!);
      if (atual && atual.rev === op.rev) await db.fila.delete(op.seq!);
    });
  }

  private async aplicarItemServidor(auditoriaId: number, srv: Item): Promise<void> {
    await db.transaction('rw', db.auditorias, async () => {
      const a = await db.auditorias.get(auditoriaId);
      const i = a?.itens.find((x) => x.id === srv.id);
      if (!a || !i) return;
      i.atualizadoEm = srv.atualizadoEm;
      i.atualizadoPor = srv.atualizadoPor;
      // O servidor pode ter recusado uma resposta dada antes de uma revisão: vale o aviso dele.
      i.alteradaNaRevisao = srv.alteradaNaRevisao ?? null;
      if (srv.alteradaNaRevisao) i.conformidade = srv.conformidade;
      await db.auditorias.put(a);
    });
  }

  private async marcarAnexoEnviado(arq: ArquivoLocal, salvo: Anexo): Promise<void> {
    await db.transaction('rw', db.auditorias, db.arquivos, async () => {
      const a = await db.auditorias.get(arq.auditoriaId);
      const i = a?.itens.find((x) => x.id === arq.itemId);
      if (a && i) {
        i.anexos = i.anexos.map((x) => (x.id === salvo.id ? { ...salvo, pendente: false } : x));
        await db.auditorias.put(a);
      }
      await db.arquivos.delete(arq.id);
    });
  }
}
