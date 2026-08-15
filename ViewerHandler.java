import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves "/viewer?path=..." - a dedicated full-tab reading view for PDFs and
 * text/markdown files, as an alternative to the quick preview modal. Opened
 * via the right-click "Open Viewer" menu item, which loads it as a new app
 * tab (via parent.openTab) rather than a small overlay - meant for actually
 * reading something at length rather than a quick glance.
 *
 * Includes Previous/Next links (and Left/Right arrow keys) that step through
 * sibling files of the same kind in the same folder, so flipping between
 * chapters/books in a folder doesn't require going back to the grid each time.
 */
public class ViewerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        String trashId = QueryUtil.getParam(query, "trashId");
        String trashSub = QueryUtil.getParam(query, "trashSub");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");
        trashId = trashId == null ? "" : URLDecoder.decode(trashId, "UTF-8");
        trashSub = trashSub == null ? "" : URLDecoder.decode(trashSub, "UTF-8");

        // Two mutually exclusive modes: the usual ROOT_DIR-relative "path",
        // or "trashId"+"trashSub" for a file found while browsing inside a
        // trashed folder (see TrashBrowseHandler) - those live outside
        // ROOT_DIR entirely, under Config.TRASH_DIR, so they can't be
        // reached via PathUtil.resolve().
        boolean isTrash = !trashId.isEmpty();
        TrashManager.Entry trashEntry = null;
        File file;

        if (isTrash) {
            trashEntry = TrashManager.get(trashId);
            if (trashEntry == null) {
                sendText(exchange, 404, "That item is no longer in the trash.");
                return;
            }
            File base = new File(Config.TRASH_DIR, trashEntry.trashedName);
            try {
                file = PathUtil.resolveWithinBase(base, trashSub);
            } catch (IOException e) {
                sendText(exchange, 403, "Forbidden");
                return;
            }
        } else {
            try {
                file = PathUtil.resolve(relPath);
            } catch (IOException e) {
                sendText(exchange, 403, "Forbidden");
                return;
            }
        }

        if (!file.exists() || file.isDirectory()) {
            sendText(exchange, 404, "Not found");
            return;
        }

        if (!isTrash) {
            RecentActivity.recordView(relPath);
            ActivityLog.record(relPath, "viewed");
        }

        String ext = GridRenderer.getExtension(file.getName()).toLowerCase();
        String html;
        try {
            html = isTrash ? buildTrashPage(file, trashEntry, trashSub, ext) : buildPage(file, relPath, ext);
        } catch (Exception e) {
            sendText(exchange, 500, "Couldn't open this file: " + e.getMessage());
            return;
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(File file, String relPath, String ext) throws IOException {
        String[] neighbors = findNeighbors(file, relPath, ext);
        String prevPath = neighbors[0];
        String nextPath = neighbors[1];
        // Anything the viewer can already read as text, it can also edit -
        // markdown/code/plain text - via the textarea+/save-text pairing
        // below. Deliberately not offered on the trash-browsing counterpart
        // (buildTrashPage never sets this), so trashed items stay read-only.
        boolean editable = ViewabilityUtil.isTextLike(file, ext);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>").append(PathUtil.htmlEscape(file.getName())).append("</title>");
        sb.append(viewerStyles());
        if (CodeLanguageUtil.shouldHighlight(ext) && !ext.equals("md")) {
            sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        }
        sb.append("</head><body class='viewer-body'");
        if (editable) {
            // Handed to the client via a data attribute (HTML-escaped like
            // any other attribute) rather than embedded as a JS string
            // literal, so filenames with quotes/backslashes can't break out
            // of an inline <script> - same pattern the folder-context-menu
            // grid already uses for data-current-path.
            sb.append(" data-edit-path=\"").append(PathUtil.htmlEscape(relPath)).append("\"");
        }
        sb.append(">");

        sb.append("<div class='viewer-topbar'>");
        sb.append("<span class='viewer-filename'>").append(PathUtil.htmlEscape(file.getName())).append("</span>");
        sb.append("<div class='viewer-nav'>");
        if (prevPath != null) {
            sb.append("<a href='/viewer?path=").append(PathUtil.urlEncode(prevPath)).append("'>&larr; Previous</a>");
        }
        if (editable) {
            sb.append("<a href=\"#\" onclick=\"toggleEditMode(); return false;\" id='editToggleBtn'>Edit</a>");
        }
        sb.append("<a href='/file?path=").append(PathUtil.urlEncode(relPath)).append("&mode=download'>Download</a>");
        if (editable && !ext.equals("md") && CodeLanguageUtil.shouldHighlight(ext)) {
            sb.append("<a href=\"#\" onclick=\"toggleCodeView(); return false;\" id='toggleRawBtn'>Show raw text</a>");
        }
        if (nextPath != null) {
            sb.append("<a href='/viewer?path=").append(PathUtil.urlEncode(nextPath)).append("'>Next &rarr;</a>");
        }
        sb.append("</div></div>");
        String prevHref = prevPath == null ? null : "/viewer?path=" + PathUtil.urlEncode(prevPath);
        String nextHref = nextPath == null ? null : "/viewer?path=" + PathUtil.urlEncode(nextPath);

        sb.append("<div class='viewer-content' id='viewerContent'>");
        if (ext.equals("pdf")) {
            sb.append("<iframe class='viewer-pdf-frame' src='/file?path=")
              .append(PathUtil.urlEncode(relPath)).append("&mode=view'></iframe>");
        } else if (editable) {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String escaped = PathUtil.htmlEscape(content);
            sb.append("<div id='readView'>");
            if (ext.equals("md")) {
                sb.append("<div class='viewer-reading markdown-body'>").append(MarkdownLite.render(content)).append("</div>");
            } else if (CodeLanguageUtil.shouldHighlight(ext)) {
                String lang = CodeLanguageUtil.hljsLanguage(ext);
                String langClass = lang.isEmpty() ? "" : " class=\"language-" + lang + "\"";
                sb.append("<div class='viewer-reading code-viewer'>");
                sb.append("<pre class='code-highlighted'><code id='codeBlock'").append(langClass).append(">").append(escaped).append("</code></pre>");
                sb.append("<pre class='code-raw plain-text' style='display:none;'>").append(escaped).append("</pre>");
                sb.append("</div>");
            } else {
                sb.append("<pre class='viewer-reading plain-text'>").append(escaped).append("</pre>");
            }
            sb.append("</div>");
            // Populated from the same escaped text as the read view above -
            // a <textarea>'s content is parsed as HTML text, so ordinary
            // HTML-entity escaping round-trips it back to the exact
            // original characters when the browser reads .value.
            sb.append("<textarea id='editArea' class='viewer-edit-area' style='display:none;' spellcheck='false'>")
              .append(escaped).append("</textarea>");
        } else {
            sb.append("<div class='viewer-unsupported'><p>This file type doesn't have a dedicated reading view.</p>")
              .append("<p><a href='/file?path=").append(PathUtil.urlEncode(relPath)).append("&mode=download'>Download it instead</a></p></div>");
        }
        sb.append("</div>");

        if (editable) {
            sb.append("<div id='saveBar' class='viewer-save-bar' style='display:none;'>")
              .append("<button id='saveBtn' class='viewer-save-btn' onclick='saveEdit()'>Save</button>")
              .append("<a href=\"#\" onclick=\"cancelEdit(); return false;\">Cancel</a>")
              .append("<span id='saveStatus' class='viewer-save-status'></span>")
              .append("</div>");
        }

        sb.append(viewerScript(prevHref, nextHref));
        sb.append("</body></html>");
        return sb.toString();
    }

    // Trash-browsing counterpart to buildPage() above: same reading view,
    // but every link points at /trash-file and /viewer?trashId=... instead
    // of /file and /viewer?path=..., since a file found via
    // TrashBrowseHandler lives under Config.TRASH_DIR rather than
    // ROOT_DIR. findNeighbors() itself is unchanged (it only cares about
    // the real filesystem parent directory and reuses "sub" the same way
    // it reuses "relPath" - just to build sibling paths, not to resolve
    // anything), so no need for a separate trash-aware version.
    private String buildTrashPage(File file, TrashManager.Entry entry, String sub, String ext) throws IOException {
        String[] neighbors = findNeighbors(file, sub, ext);
        String prevSub = neighbors[0];
        String nextSub = neighbors[1];
        String idEnc = PathUtil.urlEncode(entry.id);
        String subEnc = PathUtil.urlEncode(sub);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>").append(PathUtil.htmlEscape(file.getName())).append(" (Trash)</title>");
        sb.append(viewerStyles());
        if (CodeLanguageUtil.shouldHighlight(ext) && !ext.equals("md")) {
            sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        }
        sb.append("</head><body class='viewer-body'>");

        sb.append("<div class='viewer-topbar'>");
        sb.append("<span class='viewer-filename'>").append(PathUtil.htmlEscape(file.getName()))
          .append(" <span style='color:#999;font-weight:400;'>&middot; Recycle Bin (read-only)</span></span>");
        sb.append("<div class='viewer-nav'>");
        String prevHref = prevSub == null ? null : "/viewer?trashId=" + idEnc + "&trashSub=" + PathUtil.urlEncode(prevSub);
        String nextHref = nextSub == null ? null : "/viewer?trashId=" + idEnc + "&trashSub=" + PathUtil.urlEncode(nextSub);
        if (prevHref != null) {
            sb.append("<a href='").append(prevHref).append("'>&larr; Previous</a>");
        }
        sb.append("<a href='/trash-file?id=").append(idEnc).append("&sub=").append(subEnc).append("&mode=download'>Download</a>");
        if (ViewabilityUtil.isTextLike(file, ext) && !ext.equals("md") && CodeLanguageUtil.shouldHighlight(ext)) {
            sb.append("<a href=\"#\" onclick=\"toggleCodeView(); return false;\" id='toggleRawBtn'>Show raw text</a>");
        }
        if (nextHref != null) {
            sb.append("<a href='").append(nextHref).append("'>Next &rarr;</a>");
        }
        sb.append("</div></div>");

        sb.append("<div class='viewer-content'>");
        if (ext.equals("pdf")) {
            sb.append("<iframe class='viewer-pdf-frame' src='/trash-file?id=").append(idEnc)
              .append("&sub=").append(subEnc).append("&mode=view'></iframe>");
        } else if (ViewabilityUtil.isTextLike(file, ext)) {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String escaped = PathUtil.htmlEscape(content);
            if (ext.equals("md")) {
                sb.append("<div class='viewer-reading markdown-body'>").append(MarkdownLite.render(content)).append("</div>");
            } else if (CodeLanguageUtil.shouldHighlight(ext)) {
                String lang = CodeLanguageUtil.hljsLanguage(ext);
                String langClass = lang.isEmpty() ? "" : " class=\"language-" + lang + "\"";
                sb.append("<div class='viewer-reading code-viewer'>");
                sb.append("<pre class='code-highlighted'><code id='codeBlock'").append(langClass).append(">").append(escaped).append("</code></pre>");
                sb.append("<pre class='code-raw plain-text' style='display:none;'>").append(escaped).append("</pre>");
                sb.append("</div>");
            } else {
                sb.append("<pre class='viewer-reading plain-text'>").append(escaped).append("</pre>");
            }
        } else {
            sb.append("<div class='viewer-unsupported'><p>This file type doesn't have a dedicated reading view.</p>")
              .append("<p><a href='/trash-file?id=").append(idEnc).append("&sub=").append(subEnc).append("&mode=download'>Download it instead</a></p></div>");
        }
        sb.append("</div>");

        sb.append(viewerScript(prevHref, nextHref));
        sb.append("</body></html>");
        return sb.toString();
    }

    // Finds the previous/next sibling file of the same broad kind (pdf, or
    // any text-like type) in the same folder, sorted alphabetically.
    private String[] findNeighbors(File file, String relPath, String ext) {
        File parent = file.getParentFile();
        File[] siblings = parent.listFiles((d, name) -> !HiddenFileUtil.isHiddenName(name));
        if (siblings == null) return new String[]{null, null};

        boolean wantPdf = ext.equals("pdf");
        List<File> candidates = new ArrayList<>();
        for (File f : siblings) {
            if (f.isDirectory()) continue;
            String e = GridRenderer.getExtension(f.getName()).toLowerCase();
            boolean matches = wantPdf ? e.equals("pdf") : ViewabilityUtil.isTextLike(f, e);
            if (matches) candidates.add(f);
        }
        candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        int idx = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getName().equals(file.getName())) { idx = i; break; }
        }
        if (idx == -1) return new String[]{null, null};

        String parentRel = relPath.contains("/") ? relPath.substring(0, relPath.lastIndexOf('/')) : "";
        String prev = idx > 0 ? joinPath(parentRel, candidates.get(idx - 1).getName()) : null;
        String next = idx < candidates.size() - 1 ? joinPath(parentRel, candidates.get(idx + 1).getName()) : null;
        return new String[]{prev, next};
    }

    private String joinPath(String parentRel, String name) {
        return parentRel.isEmpty() ? name : parentRel + "/" + name;
    }

    // Shared by GDriveViewerHandler.java too, so Drive files viewed via
    // /gdrive-viewer get the exact same reading-view chrome as local files
    // do here, rather than a look-alike duplicate stylesheet.
    static String viewerStyles() {
        return "<style>" +
            "*{box-sizing:border-box;}" +
            "body.viewer-body{margin:0;height:100vh;display:flex;flex-direction:column;background:#fff;" +
              "font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;}" +
            ".viewer-topbar{display:flex;align-items:center;justify-content:space-between;padding:10px 18px;" +
              "border-bottom:1px solid #e2e4e8;flex-shrink:0;gap:16px;}" +
            ".viewer-filename{font-weight:600;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
            ".viewer-nav{display:flex;gap:16px;font-size:13px;flex-shrink:0;}" +
            ".viewer-nav a{color:#2563eb;text-decoration:none;}" +
            ".viewer-nav a:hover{text-decoration:underline;}" +
            ".viewer-content{flex:1;overflow:auto;}" +
            ".viewer-pdf-frame{width:100%;height:100%;border:none;display:block;}" +
            ".viewer-reading{max-width:1400px;margin:0 auto;padding:24px 40px;font-size:16px;line-height:1.7;color:#1f2328;}" +
            ".plain-text{white-space:pre-wrap;word-break:break-word;font-family:Menlo,Consolas,monospace;font-size:14px;}" +
            ".markdown-body h1{font-size:1.8em;margin-top:1.2em;}" +
            ".markdown-body h2{font-size:1.4em;margin-top:1.2em;}" +
            ".markdown-body h3{font-size:1.15em;margin-top:1em;}" +
            ".markdown-body code{background:#f4f5f7;padding:2px 5px;border-radius:4px;font-family:Menlo,Consolas,monospace;font-size:0.9em;}" +
            ".markdown-body ul{padding-left:24px;}" +
            ".viewer-unsupported{padding:60px;text-align:center;color:#666;}" +
            ".viewer-edit-area{width:100%;height:100%;min-height:60vh;border:none;resize:none;outline:none;" +
              "padding:24px 40px;box-sizing:border-box;font-family:Menlo,Consolas,monospace;font-size:14px;line-height:1.6;}" +
            ".viewer-save-bar{display:flex;align-items:center;gap:12px;padding:10px 18px;" +
              "border-top:1px solid #e2e4e8;background:#fafafa;font-size:13px;flex-shrink:0;}" +
            ".viewer-save-btn{background:#2563eb;color:#fff;border:none;padding:6px 16px;border-radius:6px;" +
              "cursor:pointer;font-size:13px;}" +
            ".viewer-save-btn:hover{background:#1d4ed8;}" +
            ".viewer-save-btn:disabled{background:#9ca3af;cursor:default;}" +
            ".viewer-save-bar a{color:#555;text-decoration:none;}" +
            ".viewer-save-bar a:hover{text-decoration:underline;}" +
            ".viewer-save-status{color:#888;}" +
            "</style>";
    }

    // prevHref/nextHref are already-complete URLs (either "/viewer?path="
    // or "/viewer?trashId=...&trashSub=" - see buildPage/buildTrashPage
    // above) so this one function covers the keyboard nav for both.
    private String viewerScript(String prevHref, String nextHref) {
        String prevJs = prevHref == null ? "null" : "'" + prevHref + "'";
        String nextJs = nextHref == null ? "null" : "'" + nextHref + "'";
        return "<script>" +
            "var VIEWER_PREV=" + prevJs + ", VIEWER_NEXT=" + nextJs + ";" +
            "document.addEventListener('keydown', function(e){" +
              // While actively editing, arrow keys need to move the text
              // cursor, not flip to the previous/next file - only Ctrl/Cmd+S
              // (save) is intercepted here.
              "if(document.activeElement && document.activeElement.id==='editArea'){" +
                "if((e.ctrlKey||e.metaKey) && e.key==='s'){ e.preventDefault(); saveEdit(); }" +
                "return;" +
              "}" +
              "if(e.key==='ArrowLeft' && VIEWER_PREV){ location.href=VIEWER_PREV; }" +
              "else if(e.key==='ArrowRight' && VIEWER_NEXT){ location.href=VIEWER_NEXT; }" +
            "});" +
            "if(window.hljs){ var cb=document.getElementById('codeBlock'); if(cb) hljs.highlightElement(cb); }" +
            "function toggleCodeView(){" +
              "var h=document.querySelector('.code-highlighted'), r=document.querySelector('.code-raw'), b=document.getElementById('toggleRawBtn');" +
              "if(!h||!r) return;" +
              "var showingRaw=r.style.display!=='none';" +
              "if(showingRaw){ r.style.display='none'; h.style.display=''; b.textContent='Show raw text'; }" +
              "else{ r.style.display=''; h.style.display='none'; b.textContent='Show formatted'; }" +
            "}" +
            // ---- Lightweight edit mode (text/markdown/code files only -
            // see buildPage's "editable" check; buildTrashPage never sets
            // data-edit-path, so these all safely no-op for trashed items) ----
            "function toggleEditMode(){" +
              "var editPath=document.body.dataset.editPath;" +
              "if(!editPath) return;" +
              "var editArea=document.getElementById('editArea');" +
              "if(!editArea) return;" +
              "if(editArea.style.display!=='none'){ cancelEdit(); return; }" +
              "var readView=document.getElementById('readView'), saveBar=document.getElementById('saveBar'), btn=document.getElementById('editToggleBtn');" +
              "if(readView) readView.style.display='none';" +
              "editArea.style.display='block';" +
              "if(saveBar) saveBar.style.display='flex';" +
              "if(btn) btn.textContent='View';" +
              "editArea.focus();" +
            "}" +
            "function cancelEdit(){" +
              "var readView=document.getElementById('readView'), editArea=document.getElementById('editArea'), saveBar=document.getElementById('saveBar'), btn=document.getElementById('editToggleBtn');" +
              "if(!readView||!editArea) return;" +
              "editArea.style.display='none';" +
              "readView.style.display='';" +
              "if(saveBar) saveBar.style.display='none';" +
              "if(btn) btn.textContent='Edit';" +
              "var status=document.getElementById('saveStatus'); if(status) status.textContent='';" +
            "}" +
            "function saveEdit(){" +
              "var editPath=document.body.dataset.editPath;" +
              "var editArea=document.getElementById('editArea');" +
              "var btn=document.getElementById('saveBtn');" +
              "var status=document.getElementById('saveStatus');" +
              "if(!editPath||!editArea) return;" +
              "btn.disabled=true;" +
              "if(status) status.textContent='Saving...';" +
              "fetch('/save-text',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                "body:new URLSearchParams({path:editPath, content:editArea.value}).toString()})" +
                ".then(function(r){return r.json();})" +
                ".then(function(res){" +
                  "btn.disabled=false;" +
                  // Reload rather than patch the DOM in place - simplest way
                  // to get markdown re-rendered / code re-highlighted from
                  // the freshly-saved content.
                  "if(res.success){ location.reload(); }" +
                  "else if(status){ status.textContent='Save failed: '+res.message; }" +
                "})" +
                ".catch(function(){ btn.disabled=false; if(status) status.textContent='Save failed - network error.'; });" +
            "}" +
            "</script>";
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
