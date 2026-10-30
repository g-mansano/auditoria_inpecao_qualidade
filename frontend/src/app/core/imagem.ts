const LADO_MAXIMO = 2560;
const QUALIDADE = 0.85;

/**
 * Reduz fotos da câmera (às vezes 8–12 MB) para JPEG de até 2560 px antes de guardar
 * no aparelho e enviar. Se o navegador não decodificar o formato (ex.: HEIC fora do
 * Safari), devolve o arquivo original.
 */
export async function comprimirSeImagem(arquivo: File): Promise<File> {
  if (!/^image\/(jpeg|png|webp|heic|heif)$/i.test(arquivo.type) || arquivo.size < 400 * 1024) {
    return arquivo;
  }
  try {
    const bmp = await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
    const escala = Math.min(1, LADO_MAXIMO / Math.max(bmp.width, bmp.height));
    const w = Math.round(bmp.width * escala);
    const h = Math.round(bmp.height * escala);
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    canvas.getContext('2d')!.drawImage(bmp, 0, 0, w, h);
    bmp.close();
    const blob = await new Promise<Blob | null>((ok) => canvas.toBlob(ok, 'image/jpeg', QUALIDADE));
    if (!blob || blob.size >= arquivo.size) return arquivo;
    const nome = arquivo.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([blob], nome, { type: 'image/jpeg', lastModified: arquivo.lastModified });
  } catch {
    return arquivo;
  }
}

/** Nome padrão para foto tirada na hora: "foto-item-07-20261013-143205.jpg". */
export function nomeFoto(numeroItem: number, original: string): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  const ts = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
  const ext = (original.match(/\.[^.]+$/)?.[0] ?? '.jpg').toLowerCase();
  return `foto-item-${p(numeroItem)}-${ts}${ext}`;
}
