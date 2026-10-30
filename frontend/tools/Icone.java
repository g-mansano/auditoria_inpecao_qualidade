import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Gera os ícones do PWA: fundo azul marinho, prancheta branca, check ciano. */
public class Icone {
    static final Color NAVY = new Color(0x312e81);
    static final Color TEAL = new Color(0xa78bfa);
    static final Color BRANCO = Color.WHITE;

    /** full = fundo ocupando o quadrado inteiro (maskable); senão quadrado arredondado (favicon). */
    static BufferedImage desenhar(int s, boolean full) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s / 512.0, s / 512.0);

        g.setColor(NAVY);
        if (full) g.fillRect(0, 0, 512, 512);
        else g.fill(new RoundRectangle2D.Double(0, 0, 512, 512, 112, 112));

        // Maskable: o desenho fica na zona segura (círculo central de 80%). O favicon usa área maior.
        double k = full ? 0.78 : 1.0;
        g.translate(256, 256);
        g.scale(k, k);
        g.translate(-256, -256);

        // Prancheta
        g.setColor(BRANCO);
        g.fill(new RoundRectangle2D.Double(126, 104, 260, 330, 44, 44));
        // Presilha
        g.setColor(TEAL);
        g.fill(new RoundRectangle2D.Double(186, 72, 140, 72, 28, 28));
        g.setColor(BRANCO);
        g.fill(new Ellipse2D.Double(240, 86, 32, 32));
        g.setColor(TEAL);
        g.fill(new Ellipse2D.Double(248, 94, 16, 16));

        // Check
        g.setColor(TEAL);
        g.setStroke(new BasicStroke(46, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D c = new Path2D.Double();
        c.moveTo(186, 284);
        c.lineTo(236, 334);
        c.lineTo(330, 222);
        g.draw(c);
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage i) throws IOException {
        var o = new ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    public static void main(String[] a) throws Exception {
        Path pub = Paths.get(a[0]);
        for (int s : new int[] { 72, 96, 128, 144, 152, 192, 384, 512 }) {
            Files.write(pub.resolve("icons/icon-" + s + "x" + s + ".png"), png(desenhar(s, true)));
        }
        Files.write(pub.resolve("icons/favicon-32x32.png"), png(desenhar(32, false)));

        // favicon.ico com PNGs embutidos (16, 32, 48)
        int[] tam = { 16, 32, 48 };
        byte[][] dados = new byte[tam.length][];
        for (int i = 0; i < tam.length; i++) dados[i] = png(desenhar(tam[i], false));
        var o = new ByteArrayOutputStream();
        var d = new DataOutputStream(o);
        d.write(new byte[] { 0, 0, 1, 0, (byte) tam.length, 0 });
        int off = 6 + 16 * tam.length;
        for (int i = 0; i < tam.length; i++) {
            d.write(tam[i]); d.write(tam[i]); d.write(0); d.write(0);
            d.write(new byte[] { 1, 0, 32, 0 });
            d.write(le(dados[i].length)); d.write(le(off));
            off += dados[i].length;
        }
        for (byte[] b : dados) d.write(b);
        Files.write(pub.resolve("favicon.ico"), o.toByteArray());
    }

    static byte[] le(int v) { return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) }; }
}
