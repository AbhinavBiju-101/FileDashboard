import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves "/thumbnail?path=..." - a small scaled-down PNG for image files.
 * Thumbnails are cached in memory, keyed by path + last-modified time, so
 * scrolling a folder repeatedly doesn't re-decode images every time.
 */
public class ThumbnailHandler implements HttpHandler {

    private static final int SIZE = 200;
    private static final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File file;
        try {
            file = PathUtil.resolve(relPath);
        } catch (IOException e) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!file.exists() || file.isDirectory()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String cacheKey = file.getAbsolutePath() + ":" + file.lastModified();
        byte[] out = cache.get(cacheKey);

        if (out == null) {
            BufferedImage original = ImageIO.read(file);
            if (original == null) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }
            out = scaleToBytes(original);
            cache.put(cacheKey, out);
        }

        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private byte[] scaleToBytes(BufferedImage original) throws IOException {
        int w = original.getWidth();
        int h = original.getHeight();
        double scale = Math.min((double) SIZE / w, (double) SIZE / h);
        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", baos);
        return baos.toByteArray();
    }
}
