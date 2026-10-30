package dev.mansano.auditoria.inspecao;

import java.util.LinkedHashMap;
import java.util.Map;

/** Escalas do FQ-106, na ordem da legenda da folha: valor gravado -> {sigla da folha, rótulo}. */
public final class InspecaoValores {

    private InspecaoValores() {
    }

    public static final Map<String, String[]> TOPICO = new LinkedHashMap<>();
    /** EPI's: S/N/P (P = Parcial: o operador está com parte dos EPIs). */
    public static final Map<String, String[]> EPI = new LinkedHashMap<>();

    static {
        TOPICO.put("C", new String[] { "✓", "Conforme" });
        TOPICO.put("NC", new String[] { "N/C", "Não Conforme" });
        TOPICO.put("NA", new String[] { "N/A", "Não se Aplica" });
        TOPICO.put("NV", new String[] { "N/V", "Não Visível" });
        TOPICO.put("NI", new String[] { "N/I", "Não Inspecionado" });
        TOPICO.put("NP", new String[] { "N/P", "Não Presente" });
        EPI.put("S", new String[] { "S", "Sim" });
        EPI.put("N", new String[] { "N", "Não" });
        EPI.put("P", new String[] { "P", "Parcial" });
    }

    public static Map<String, String[]> escala(String escala) {
        return "EPI".equals(escala) ? EPI : TOPICO;
    }

    public static boolean aceita(String escala, String valor) {
        return valor == null || escala(escala).containsKey(valor);
    }

    /** Ocorrência para o PCP: N/C nos tópicos; N (sem EPI) ou P (parcial) no EPI. */
    public static boolean naoConforme(String escala, String valor) {
        return "EPI".equals(escala) ? "N".equals(valor) || "P".equals(valor) : "NC".equals(valor);
    }

    public static String sigla(String escala, String valor) {
        String[] s = valor == null ? null : escala(escala).get(valor);
        return s == null ? "" : s[0];
    }
}
