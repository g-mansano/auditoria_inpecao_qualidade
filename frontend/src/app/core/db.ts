import Dexie, { Table } from 'dexie';
import { AuditoriaDetalhe, AuditoriaResumo, Cabecalho, InspecaoDia, InspecaoResumo, ItemAlteracao } from './modelos';

export type TipoOperacao = 'item' | 'cabecalho' | 'anexo' | 'excluirAnexo';

/** Uma alteração feita no aparelho e ainda não confirmada pelo servidor. */
export interface Operacao {
  seq?: number;
  auditoriaId: number;
  /** Identifica o alvo (ex.: "item:12"); edições seguidas no mesmo alvo viram uma operação só. */
  chave: string;
  tipo: TipoOperacao;
  itemId?: number;
  anexoId?: string;
  dados?: ItemAlteracao | Cabecalho;
  /** Incrementa a cada edição; o sync só apaga a operação se ela não mudou durante o envio. */
  rev: number;
  criadoEm: string;
  estado: 'pendente' | 'rejeitada';
  erro?: string;
}

/** Arquivo anexado offline, guardado até o upload. */
export interface ArquivoLocal {
  id: string;
  auditoriaId: number;
  itemId: number;
  nome: string;
  tipo: string;
  blob: Blob;
}

// ------------------------------------------------------------------ inspeção de OPs (FQ-106)

export type TipoOperacaoInspecao = 'dia' | 'op' | 'excluirOp' | 'anexo' | 'excluirAnexo';

/** Alteração de inspeção ainda não confirmada pelo servidor (mesmas regras da fila das auditorias). */
export interface OperacaoInspecao {
  seq?: number;
  diaId: string;
  /** "dia", "op:<id>", "anexo:<id>"…: edições seguidas na mesma OP viram uma operação só. */
  chave: string;
  tipo: TipoOperacaoInspecao;
  opId?: string;
  itemNumero?: number;
  anexoId?: string;
  dados?: unknown;
  rev: number;
  criadoEm: string;
  estado: 'pendente' | 'rejeitada';
  erro?: string;
}

export interface ArquivoInspecaoLocal {
  id: string;
  diaId: string;
  opId: string;
  itemNumero: number;
  nome: string;
  tipo: string;
  blob: Blob;
}

class BancoLocal extends Dexie {
  auditorias!: Table<AuditoriaDetalhe, number>;
  resumos!: Table<AuditoriaResumo, number>;
  fila!: Table<Operacao, number>;
  arquivos!: Table<ArquivoLocal, string>;
  inspecoes!: Table<InspecaoDia, string>;
  resumosInspecao!: Table<InspecaoResumo, string>;
  filaInspecao!: Table<OperacaoInspecao, number>;
  arquivosInspecao!: Table<ArquivoInspecaoLocal, string>;

  constructor() {
    super('checklist-auditoria');
    this.version(1).stores({
      auditorias: 'id',
      resumos: 'id',
      fila: '++seq, auditoriaId, chave',
      arquivos: 'id, auditoriaId',
    });
    // v2: FQ-106 (inspeção de OPs).
    this.version(2).stores({
      inspecoes: 'id',
      resumosInspecao: 'id',
      filaInspecao: '++seq, diaId, chave',
      arquivosInspecao: 'id, diaId',
    });
  }
}

export const db = new BancoLocal();
