import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * If Config.ACCESS_TOKEN is set, every request must carry a matching
 * "?token=..." query param (once) or a "dash_token" cookie (set automatically
 * after the first successful token check) - otherwise the request is
 * rejected with 401. If ACCESS_TOKEN is null, this filter is a no-op.
 */
public class AuthFilter extends Filter {

    @Override
    public String description() {
        return "Optional shared-token auth filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        if (Config.ACCESS_TOKEN == null || Config.ACCESS_TOKEN.isEmpty()) {
            chain.doFilter(exchange);
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        String tokenParam = QueryUtil.getParam(query, "token");
        String cookieToken = getCookie(exchange, "dash_token");

        if (Config.ACCESS_TOKEN.equals(tokenParam) || Config.ACCESS_TOKEN.equals(cookieToken)) {
            if (tokenParam != null) {
                exchange.getResponseHeaders().add("Set-Cookie", "dash_token=" + Config.ACCESS_TOKEN + "; Path=/; HttpOnly");
            }
            chain.doFilter(exchange);
            return;
        }

        String msg = "<html><body style='font-family:sans-serif;padding:40px;'>" +
                "<h2>Access token required</h2>" +
                "<p>Add <code>?token=YOUR_TOKEN</code> to the URL once - it will be remembered after that.</p>" +
                "</body></html>";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(401, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getCookie(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) return null;
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
        }
        return null;
    }
}
