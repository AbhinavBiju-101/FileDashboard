import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Serves "POST /gdrive-access-request" with a form-encoded "email" param.
 *
 * There's no SMTP configured anywhere in this app - on purpose, since that'd
 * mean shipping mail credentials with a downloadable jar. Instead this:
 *   1. Appends a line to Config.DATA_DIR/gdrive-access-requests.log, so
 *      whoever is actually running this server (most usefully: the hosted
 *      portfolio demo instance) has a durable local record even if the
 *      email itself never gets sent or read.
 *   2. Returns a mailto: link addressed to the developer - actually
 *      *sending* it is left to the visitor's own mail client, same as any
 *      plain "mailto:" link elsewhere on the web.
 *
 * This only exists because Google Drive access is capped at 100 manually-
 * approved test users while this app's OAuth client sits in Testing status
 * (see Settings.isGdriveExperimentalEnabled()'s doc comment) - it goes away
 * entirely if the app is ever verified for production, or ends up behind
 * the Picker API + drive.file instead of the current broad "drive" scope.
 */
public class GDriveAccessRequestHandler implements HttpHandler {

    private static final File LOG_FILE = new File(Config.DATA_DIR, "gdrive-access-requests.log");
    // Deliberately loose - this only gates what gets written to a local
    // log and stuffed into a mailto: link, not anything security-relevant.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String DEVELOPER_EMAIL = "abhinav.bijupk@gmail.com";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, "{\"ok\":false,\"error\":\"Use POST.\"}");
            return;
        }

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            body = buf.toString(StandardCharsets.UTF_8.name());
        }
        String email = QueryUtil.getParam(body, "email");
        if (email != null) {
            try { email = java.net.URLDecoder.decode(email, "UTF-8"); } catch (Exception ignored) {}
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeJson(exchange, 400, "{\"ok\":false,\"error\":\"Enter a valid email address.\"}");
            return;
        }
        email = email.trim();

        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
            String line = Instant.now() + "\t" + email + System.lineSeparator();
            Files.write(LOG_FILE.toPath(), line.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // A logging failure shouldn't block the person from still getting
            // a working mailto: link - the log is a nice-to-have, not the
            // actual delivery mechanism.
        }

        String subject = java.net.URLEncoder.encode("File Dashboard - Google Drive tester access", "UTF-8").replace("+", "%20");
        String bodyText = "Please add this Google account as a verified tester for File Dashboard's Google Drive integration:\n\n"
              + email + "\n";
        String encodedBody = java.net.URLEncoder.encode(bodyText, "UTF-8").replace("+", "%20");
        String mailto = "mailto:" + DEVELOPER_EMAIL + "?subject=" + subject + "&body=" + encodedBody;

        writeJson(exchange, 200, "{\"ok\":true,\"mailto\":\"" + MiniJson.escape(mailto) + "\"}");
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
