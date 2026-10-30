/**
 * UUID v4. crypto.randomUUID só existe em contexto seguro (HTTPS); getRandomValues
 * funciona também em HTTP, útil em testes na rede interna.
 */
export function uuid(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Id fixo deste aparelho/navegador, enviado no header X-Dispositivo. */
export function dispositivoId(): string {
  const chave = 'checklist.dispositivo';
  let id: string | null = null;
  try {
    id = localStorage.getItem(chave);
    if (!id) {
      id = uuid();
      localStorage.setItem(chave, id);
    }
  } catch {
    id ??= uuid();
  }
  return id;
}

export function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export interface GrupoItens<T> {
  /** Ex.: "Requisitos gerais · Cap. V, seção I". */
  nome: string;
  itens: T[];
}

/**
 * Agrupa itens consecutivos de mesmo grupo, mantendo a ordem do formulário. O ANVISA repete
 * nomes de etapa em capítulos diferentes ("Requisitos gerais" no Cap. II e no Cap. V); quando
 * todos os itens do bloco têm a mesma referência, ela entra no título para diferenciar.
 */
export function agruparConsecutivos<T extends { grupo: string | null; clausula: string | null }>(
  itens: T[],
): GrupoItens<T>[] {
  const blocos: { grupo: string; itens: T[] }[] = [];
  for (const i of itens) {
    const g = i.grupo ?? 'Itens';
    const ultimo = blocos[blocos.length - 1];
    if (ultimo?.grupo === g) {
      ultimo.itens.push(i);
    } else {
      blocos.push({ grupo: g, itens: [i] });
    }
  }
  return blocos.map((b) => {
    const ref = b.itens[0].clausula;
    const mesmaRef = !!ref && b.itens.every((x) => x.clausula === ref);
    return { nome: mesmaRef ? `${b.grupo} · ${ref}` : b.grupo, itens: b.itens };
  });
}

export function vazioParaNull(s: string | null | undefined): string | null {
  const t = (s ?? '').trim();
  return t ? t : null;
}

/** Descrição curta do aparelho, só para exibir. */
export function nomeAparelho(): string {
  const ua = navigator.userAgent;
  if (/iPad/.test(ua)) return 'iPad';
  if (/iPhone/.test(ua)) return 'iPhone';
  if (/Android/.test(ua)) return /Mobile/.test(ua) ? 'Celular Android' : 'Tablet Android';
  if (/Windows/.test(ua)) return 'Computador Windows';
  return 'Navegador';
}
