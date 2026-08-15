/**
 * A deliberately small Markdown renderer - headers, **bold**, *italic*,
 * `inline code`, "- " bullet lists, and paragraphs. Not CommonMark-complete
 * (no tables, fenced code blocks, links, or nested lists), but covers what
 * typical notes and README-style files actually use, without pulling in a
 * dependency for it.
 *
 * Input is HTML-escaped before any markdown transformation runs, so this is
 * safe to use on arbitrary file contents.
 */
public class MarkdownLite {

    public static String render(String markdown) {
        String escaped = PathUtil.htmlEscape(markdown);
        String[] lines = escaped.split("\n", -1);
        StringBuilder html = new StringBuilder();
        boolean inList = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("### ")) {
                inList = closeListIfOpen(html, inList);
                html.append("<h3>").append(inline(line.substring(4))).append("</h3>\n");
            } else if (line.startsWith("## ")) {
                inList = closeListIfOpen(html, inList);
                html.append("<h2>").append(inline(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("# ")) {
                inList = closeListIfOpen(html, inList);
                html.append("<h1>").append(inline(line.substring(2))).append("</h1>\n");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) { html.append("<ul>\n"); inList = true; }
                html.append("<li>").append(inline(line.substring(2))).append("</li>\n");
            } else if (line.isEmpty()) {
                inList = closeListIfOpen(html, inList);
            } else {
                inList = closeListIfOpen(html, inList);
                html.append("<p>").append(inline(line)).append("</p>\n");
            }
        }
        closeListIfOpen(html, inList);
        return html.toString();
    }

    private static boolean closeListIfOpen(StringBuilder html, boolean inList) {
        if (inList) html.append("</ul>\n");
        return false;
    }

    private static String inline(String text) {
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
        text = text.replaceAll("`(.+?)`", "<code>$1</code>");
        return text;
    }
}
