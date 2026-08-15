import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Serves "/trash-browse?id=...&sub=..." - a read-only listing of whatever is
 * INSIDE a trashed folder, without restoring it first.
 *
 * Before this existed, a trashed folder's contents were only reachable by
 * restoring it back to its original location - there was no way to just
 * look inside. That's because trashed items live in Config.TRASH_DIR (under
 * a random id-prefixed name, not the original folder structure), which is
 * completely outside the ROOT_DIR-anchored world that BrowseHandler and
 * PathUtil.resolve() work in. This handler is BrowseHandler's read-only
 * cousin, scoped to a single trashed folder instead of the whole root: "id"
 * identifies which trash entry, "sub" is the path inside it (relative,
 * forward-slashed, same convention as everywhere else).
 *
 * Deliberately read-only - no upload/rename/move/new-folder here. Anything
 * that needs full editing capability is exactly one Restore away.
 */
public class TrashBrowseHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String id = QueryUtil.getParam(query, "id");
        String sub = QueryUtil.getParam(query, "sub");
        id = id == null ? "" : URLDecoder.decode(id, "UTF-8");
        sub = sub == null ? "" : URLDecoder.decode(sub, "UTF-8");

        TrashManager.Entry entry = TrashManager.get(id);
        if (entry == null) {
            sendText(exchange, 404, "That item is no longer in the trash.");
            return;
        }
        if (!entry.isDirectory) {
            sendText(exchange, 400, "\"" + entry.originalName + "\" is a file, not a folder.");
            return;
        }

        File base = new File(Config.TRASH_DIR, entry.trashedName);
        File dir;
        try {
            dir = PathUtil.resolveWithinBase(base, sub);
        } catch (IOException e) {
            sendText(exchange, 403, "Forbidden: " + e.getMessage());
            return;
        }

        if (!dir.exists() || !dir.isDirectory()) {
            sendText(exchange, 404, "Not found inside the trashed folder: " + sub);
            return;
        }

        String html = buildPage(entry, dir, sub);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(TrashManager.Entry entry, File dir, String sub) {
        File[] entries = dir.listFiles((d, name) -> !HiddenFileUtil.isHiddenName(name));
        if (entries == null) entries = new File[0];
        Arrays.sort(entries, Comparator.<File, Boolean>comparing(f -> !f.isDirectory())
            .thenComparing(f -> f.getName().toLowerCase()));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        String tabTitle = sub.isEmpty() ? entry.originalName : dir.getName();
        sb.append("<title>").append(PathUtil.htmlEscape(tabTitle)).append(" (Trash)</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append(buildBreadcrumb(entry, sub));
        sb.append("<div class='toolbar'>");
        sb.append("<span class='toolbar-label'>Viewing inside the recycle bin - read-only.</span>");
        if (sub.isEmpty()) {
            sb.append(" <a href=\"#\" data-action='restore' data-id='").append(PathUtil.htmlEscape(entry.id))
              .append("' data-name='").append(PathUtil.htmlEscape(entry.originalName)).append("' class='toolbar-action'>Restore this folder</a>");
            sb.append(" <a href=\"#\" data-action='permanent-delete' data-id='").append(PathUtil.htmlEscape(entry.id))
              .append("' data-name='").append(PathUtil.htmlEscape(entry.originalName)).append("' class='toolbar-action'>Delete forever</a>");
        }
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div class='grid'>");
        for (File f : entries) {
            String childSub = sub.isEmpty() ? f.getName() : sub + "/" + f.getName();
            if (f.isDirectory()) {
                sb.append(trashFolderCard(entry.id, childSub, f.getName()));
            } else {
                sb.append(trashFileCard(entry.id, childSub, f));
            }
        }
        if (entries.length == 0) {
            sb.append("<p class='empty'>This folder is empty.</p>");
        }
        sb.append("</div>");

        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // Folders/files inside a trashed folder now render as the same kind of
    // selectable .card div GridRenderer produces everywhere else, just
    // tagged data-type="trash" (matching the top-level Recycle Bin cards)
    // plus data-trash-id/data-trash-sub so PageScripts.js can build
    // /trash-file and /trash-browse URLs instead of the ROOT_DIR-relative
    // ones it uses for data-type="folder"/"file" cards. This is what gives
    // items in here the same click-to-select, double-click-to-open,
    // right-click menu (preview, open in viewer, restore, delete forever)
    // and multi-select behavior as every other folder - previously these
    // were plain <a href> links, which is why clicking a file just
    // downloaded it instead of previewing it.
    private String trashFolderCard(String id, String childSub, String displayName) {
        String name = PathUtil.htmlEscape(displayName);
        String idEsc = PathUtil.htmlEscape(id);
        String subEsc = PathUtil.htmlEscape(childSub);
        String dataPath = PathUtil.htmlEscape(id + "/" + childSub);
        return "<div class=\"card folder\" data-path=\"" + dataPath + "\" data-name=\"" + name +
               "\" data-type=\"trash\" data-isdir=\"1\" data-trash-id=\"" + idEsc +
               "\" data-trash-sub=\"" + subEsc + "\">" +
               "<div class=\"icon\">&#128193;</div>" +
               "<div class=\"name\" title=\"" + name + "\">" + name + "</div>" +
               "</div>";
    }

    private String trashFileCard(String id, String childSub, File f) {
        String ext = GridRenderer.getExtension(f.getName()).toLowerCase();
        String name = PathUtil.htmlEscape(f.getName());
        String idEsc = PathUtil.htmlEscape(id);
        String subEsc = PathUtil.htmlEscape(childSub);
        String dataPath = PathUtil.htmlEscape(id + "/" + childSub);
        boolean viewable = ViewabilityUtil.isViewable(f, ext);
        boolean textlike = ViewabilityUtil.isTextLike(f, ext);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name)
          .append("\" data-type=\"trash\" data-isdir=\"0\" data-ext=\"").append(ext)
          .append("\" data-viewable=\"").append(viewable ? "1" : "0")
          .append("\" data-textlike=\"").append(textlike ? "1" : "0")
          .append("\" data-trash-id=\"").append(idEsc)
          .append("\" data-trash-sub=\"").append(subEsc).append("\">");
        sb.append("<div class=\"icon\">").append(GridRenderer.iconFor(ext)).append("</div>");
        sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
        sb.append("<div class=\"meta\">").append(GridRenderer.humanSize(f.length())).append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildBreadcrumb(TrashManager.Entry entry, String sub) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='breadcrumb'><a href='/trash'>Recycle Bin</a> / <a href='/trash-browse?id=")
          .append(PathUtil.urlEncode(entry.id)).append("'>").append(PathUtil.htmlEscape(entry.originalName)).append("</a>");
        if (!sub.isEmpty()) {
            String[] parts = sub.split("/");
            StringBuilder acc = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (acc.length() > 0) acc.append("/");
                acc.append(part);
                sb.append(" / <a href='/trash-browse?id=").append(PathUtil.urlEncode(entry.id))
                  .append("&sub=").append(PathUtil.urlEncode(acc.toString())).append("'>")
                  .append(PathUtil.htmlEscape(part)).append("</a>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
