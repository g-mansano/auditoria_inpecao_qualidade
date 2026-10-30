export type Conformidade = 'C' | 'NC' | 'OM' | 'NA';
export type StatusAuditoria = 'EM_ANDAMENTO' | 'CONCLUIDA';
export type Papel = 'ADMIN' | 'AUDITOR' | 'LEITOR';

export const CONFORMIDADES: { valor: Conformidade; rotulo: string; curto: string }[] = [
  { valor: 'C', rotulo: 'Conforme', curto: 'C' },
  { valor: 'NC', rotulo: 'Não Conforme', curto: 'NC' },
  { valor: 'OM', rotulo: 'Oportunidade de Melhoria', curto: 'OM' },
  { valor: 'NA', rotulo: 'Não Aplicável', curto: 'NA' },
];

export interface Usuario {
  id: number;
  username: string;
  nome: string;
  papel: Papel;
  ativo: boolean;
  /** Pode abrir e editar revisão dos formulários (admin sempre pode). Ausente em sessões antigas. */
  podeRevisar?: boolean;
}

export interface Editor {
  usuarioId: number;
  nome: string;
  dispositivo: string;
  desde: string;
}

export type Secao = 'CHECKLIST' | 'CAMPO';

/** Uma resposta da escala do formulário: valor gravado (C/NC/OM/NA) e o que aparece (ex.: S - Sim). */
export interface Opcao {
  valor: Conformidade;
  sigla: string;
  rotulo: string;
}

/** Como o formulário (ISO 13485, ANVISA, ISO 9001, INMETRO, Housekeeping…) se apresenta. */
export interface Formulario {
  id: number;
  nome: string;
  titulo: string;
  identificacao: string;
  /** "Auditoria Interna" ou "Auditoria Housekeeping". Ausente em cópias locais salvas antes da v3. */
  rotuloAuditoria?: string;
  /** Nulo quando o formulário não tem coluna de referência normativa. */
  rotuloReferencia: string | null;
  usaProcesso: boolean;
  usaDocumentos: boolean;
  usaLocal: boolean;
  exigeRegistroNc: boolean;
  escopoPadrao: string | null;
  sugestoesEvidencia: string[];
  opcoes: Opcao[];
  /** Rodapé de cada folha impressa, igual ao do formulário original (ex.: "FQ-101 - Rev. 06 - Data: 02/03/2026"). */
  rodape?: string | null;
  /** AUDITORIA ou INSPECAO_OP (FQ-106). Ausente em cópias antigas = AUDITORIA. */
  tipo?: string;
}

/** Escala de resposta do formulário, no formato usado pelas telas (curto = sigla). */
export function opcoesDe(f: Formulario | null | undefined): { valor: Conformidade; rotulo: string; curto: string }[] {
  if (!f?.opcoes?.length) return CONFORMIDADES;
  return f.opcoes.map((o) => ({ valor: o.valor, rotulo: o.rotulo, curto: o.sigla }));
}

/** Sigla exibida para um valor (C vira "S" no INMETRO). */
export function siglaDe(f: Formulario | null | undefined, c: Conformidade): string {
  return f?.opcoes?.find((o) => o.valor === c)?.sigla ?? c;
}

/** Acesso do usuário a um formulário. Sem acesso, o formulário nem aparece. */
export type NivelAcesso = 'VER' | 'PREENCHER';

export interface FormularioResumo {
  formulario: Formulario;
  totalPerguntas: number;
  emAndamento: number;
  /** Auditorias concluídas ou inspeções finalizadas. */
  concluidas: number;
  /** Ausente em cópias locais antigas (antes das permissões): trate como PREENCHER. */
  nivel?: NivelAcesso;
}

// ------------------------------------------------------------------ FQ-106 (inspeção de OPs)

export type StatusInspecao = 'EM_ANDAMENTO' | 'FINALIZADA';

/** Escalas da folha, na ordem da legenda. Valor gravado -> sigla e rótulo. */
export const ESCALA_TOPICO: { valor: string; sigla: string; rotulo: string }[] = [
  { valor: 'C', sigla: '✓', rotulo: 'Conforme' },
  { valor: 'NC', sigla: 'N/C', rotulo: 'Não Conforme' },
  { valor: 'NA', sigla: 'N/A', rotulo: 'Não se Aplica' },
  { valor: 'NV', sigla: 'N/V', rotulo: 'Não Visível' },
  { valor: 'NI', sigla: 'N/I', rotulo: 'Não Inspecionado' },
  { valor: 'NP', sigla: 'N/P', rotulo: 'Não Presente' },
];
export const ESCALA_EPI: { valor: string; sigla: string; rotulo: string }[] = [
  { valor: 'S', sigla: 'S', rotulo: 'Sim' },
  { valor: 'N', sigla: 'N', rotulo: 'Não' },
  { valor: 'P', sigla: 'P', rotulo: 'Parcial' },
];

export function escalaDe(escala: string | null | undefined) {
  return escala === 'EPI' ? ESCALA_EPI : ESCALA_TOPICO;
}

export function siglaInspecao(escala: string | null | undefined, valor: string | null | undefined): string {
  return escalaDe(escala).find((o) => o.valor === valor)?.sigla ?? '';
}

/** Ocorrência para o PCP: N/C nos tópicos; N ou P (parcial) no EPI. */
export function naoConformeInspecao(escala: string | null | undefined, valor: string | null | undefined): boolean {
  return escala === 'EPI' ? valor === 'N' || valor === 'P' : valor === 'NC';
}

export interface Inspetor {
  id: number;
  nome: string;
  username: string;
}

export interface TopicoInspecao {
  numero: number;
  texto: string;
  escala: string | null;
}

export interface AnexoInspecao {
  id: string;
  opId: string;
  itemNumero: number;
  nomeOriginal: string;
  contentType: string;
  tamanho: number;
  sha256: string | null;
  enviadoPor: string;
  enviadoEm: string;
  /** Só existe no aparelho; ainda não subiu. */
  pendente?: boolean;
}

export interface ItemInspecao {
  numero: number;
  topico: string;
  escala: string | null;
  valor: string | null;
  observacao: string | null;
  anexos: AnexoInspecao[];
}

export interface LoteInspecao {
  id: string;
  lote: string | null;
  dataFabricacao: string | null;
}

export interface OpInspecao {
  id: string;
  ordem: number;
  numeroOp: string | null;
  codigoProduto: string | null;
  alteradoEmCliente: string | null;
  itens: ItemInspecao[];
  lotes: LoteInspecao[];
}

export interface InspecaoDia {
  id: string;
  templateId: number;
  formulario: string;
  rodape: string | null;
  data: string;
  inspetor: Inspetor;
  status: StatusInspecao;
  finalizadaEm: string | null;
  criadoEm: string;
  atualizadoEm: string;
  topicos: TopicoInspecao[];
  ops: OpInspecao[];
  /** Criada neste aparelho e ainda não confirmada pelo servidor. */
  local?: boolean;
}

export interface InspecaoResumo {
  id: string;
  templateId: number;
  data: string;
  inspetor: Inspetor;
  status: StatusInspecao;
  totalOps: number;
  contagem: Record<string, number>;
  naoConformes: number;
  atualizadoEm: string;
}

export interface HistoricoInspecao {
  id: number;
  opId: string | null;
  numeroOp: string | null;
  evento: string;
  campo: string | null;
  valorAnterior: string | null;
  valorNovo: string | null;
  usuario: string;
  dispositivo: string | null;
  registradoEm: string;
}

// ------------------------------------------------------------------ permissões e histórico de revisões

export interface PermissaoFormulario {
  templateId: number;
  nome: string;
  identificacao: string;
  tipo: string;
  nivel: NivelAcesso | null;
}

export interface RegistroPermissao {
  templateId: number;
  formulario: string;
  nivelAnterior: NivelAcesso | null;
  nivelNovo: NivelAcesso | null;
  alteradoPor: string;
  alteradoEm: string;
}

export interface HistoricoRevisao {
  id: number;
  revisao: string;
  nova: boolean;
  alteradas: number;
  novas: number;
  removidas: number;
  auditorias: number;
  salvoPor: string;
  salvoEm: string;
  detalhes: { numero: number | null; tipo: 'ALTERADA' | 'NOVA' | 'REMOVIDA' | 'TITULO'; antes: string | null; depois: string | null }[];
}

export interface AuditoriaResumo {
  id: number;
  numero: string;
  dataInicio: string;
  dataFim: string | null;
  auditores: string;
  status: StatusAuditoria;
  templateId: number;
  formulario: string;
  editor: Editor | null;
  totalItens: number;
  respondidos: number;
  contagem: Record<Conformidade, number>;
  atualizadoEm: string;
}

export interface Anexo {
  id: string;
  itemId: number;
  nomeOriginal: string;
  contentType: string;
  tamanho: number;
  sha256: string | null;
  enviadoPor: string;
  enviadoEm: string;
  /** Só existe no aparelho; ainda não subiu para o servidor. */
  pendente?: boolean;
}

export interface Item {
  id: number;
  numero: number;
  secao: Secao;
  /** Capítulo (ISO), etapa (ANVISA) ou setor (roteiro de campo). */
  grupo: string | null;
  clausula: string | null;
  pergunta: string;
  processo: string | null;
  documentos: string | null;
  /** Como verificar (ex.: Visual, Documentação SGQ). */
  orientacao: string | null;
  /** Auditor responsável (roteiro de campo). */
  responsavel: string | null;
  /** Local / área (Housekeeping). */
  local: string | null;
  evidencias: string | null;
  conformidade: Conformidade | null;
  rnc: string | null;
  atualizadoEm: string | null;
  atualizadoPor: string | null;
  alteradoEmCliente: string | null;
  anexos: Anexo[];
  /** Revisão em que a pergunta mudou depois de respondida: precisa de nova resposta. */
  alteradaNaRevisao?: string | null;
}

/** Item cuja pergunta saiu numa revisão: só aparece na janela de removidas e no histórico. */
export interface ItemRemovido {
  item: Item;
  removidoNaRevisao: string;
  visto: boolean;
}

export interface AuditoriaDetalhe {
  id: number;
  numero: string;
  dataInicio: string;
  dataFim: string | null;
  auditores: string;
  escopo: string | null;
  status: StatusAuditoria;
  formulario: string;
  modelo: Formulario;
  editor: Editor | null;
  criadoPor: string;
  criadoEm: string;
  atualizadoEm: string;
  concluidoEm: string | null;
  itens: Item[];
  /** Ausente em cópias locais salvas antes da revisão de formulários. */
  removidos?: ItemRemovido[];
}

export interface Cabecalho {
  numero: string;
  dataInicio: string;
  dataFim: string | null;
  auditores: string;
  escopo: string | null;
}

export interface ItemAlteracao {
  processo: string | null;
  documentos: string | null;
  evidencias: string | null;
  conformidade: Conformidade | null;
  rnc: string | null;
  orientacao: string | null;
  responsavel: string | null;
  local: string | null;
  alteradoEmCliente: string;
  /** Texto da pergunta que o aparelho mostrava: o servidor recusa resposta dada sobre texto já revisado. */
  perguntaVista?: string;
}

export interface Historico {
  id: number;
  itemNumero: number | null;
  evento: string;
  campo: string | null;
  valorAnterior: string | null;
  valorNovo: string | null;
  usuario: string;
  dispositivo: string | null;
  registradoEm: string;
}

/** Pergunta na tela de revisão. {@code id} nulo = pergunta nova (ainda não gravada). */
export interface PerguntaRevisao {
  id: number | null;
  numero?: number;
  secao: Secao;
  grupo: string | null;
  clausula: string | null;
  texto: string;
  processoPadrao: string | null;
  documentosPadrao: string | null;
  orientacaoPadrao: string | null;
  responsavelPadrao: string | null;
  /** Quantas auditorias em andamento já responderam esta pergunta. */
  respondidas?: number;
}

export interface Revisao {
  templateId: number;
  nome: string;
  codigo: string;
  revisao: string;
  titulo: string;
  rodape: string | null;
  versao: number;
  perguntas: PerguntaRevisao[];
}

export interface SalvarRevisao {
  nova: boolean;
  versao: number;
  titulo: string;
  perguntas: PerguntaRevisao[];
}

export interface ResultadoRevisao {
  revisao: string;
  rodape: string | null;
  alteradas: number;
  novas: number;
  removidas: number;
  auditorias: number;
  itensReabertos: number;
}

export interface ErroApi {
  codigo: string;
  mensagem: string;
}

export const SECOES: Record<Secao, string> = {
  CHECKLIST: 'Checklist',
  CAMPO: 'Roteiro de campo',
};
