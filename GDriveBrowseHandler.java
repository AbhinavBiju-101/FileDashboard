import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serves "/gdrive?path=..." - browsing Google Drive, reusing the exact same
 * .grid/.card/.icon/.name/.meta CSS classes, toolbar/filter-chip layout,
 * and preview-modal look as local Browse/Dashboard/Trash so it looks like
 * part of the same app rather than a bolted-on extra page.
 *
 * Deliberately read-only for this first pass (no upload/rename/move/delete
 * against Drive) and deliberately does NOT load PageScripts.java: that
 * script's selection/context-menu/rename/move/delete logic all POST to
 * /fileops against local filesystem paths, and reusing it here would either
 * silently do nothing or - worse - do something to a local path that
 * happens to collide with a Drive-shaped one. Instead, this page's own
 * scripts (SELECTION_SCRIPT, CONTEXT_MENU_SCRIPT, PREVIEW_SCRIPT) mirror
 * PageScripts.java's behavior closely - same click/double-click/right-click
 * semantics, same visual chrome via the same shared CSS classes in
 * Styles.java - but are entirely separate code, keyed by Drive id
 * (data-gdrive-*) rather than local path (data-path), so the two can never
 * cross-contaminate.
 *
 * Drive has no real paths - files just have parent folder ids - so the
 * "path" query param is a synthetic breadcrumb trail this handler invented:
 * "id%7CurlencodedName" segments joined by "/", e.g.
 * "1AbcId%7CProjects/1XyzId%7CReports" (%7C = a percent-encoded "|" - it has
 * to be encoded, not a raw "|" character, or the request line becomes an
 * invalid URI; the JDK's built-in HTTP server rejects that outright with a
 * bare "400: Bad Request URI" before any handler even runs). Carrying the
 * name alongside each id means rendering the breadcrumb never needs extra
 * API calls just to know what to label each level.
 *
 * Unverified against real Google endpoints - see GDriveAuth.java's class
 * comment.
 */
public class GDriveBrowseHandler implements HttpHandler {

    static class Crumb {
        final String id;
        final String name;
        Crumb(String id, String name) { this.id = id; this.name = name; }
    }

    // Extensions treated as plain-text-renderable, same list
    // PageScripts.java's PREVIEW_TEXT_EXTS uses locally.
    private static final List<String> TEXT_LIKE_EXTS = Arrays.asList(
        "txt", "md", "csv", "json", "xml", "log", "html", "htm", "css", "js", "ts",
        "java", "py", "c", "cpp", "h", "hpp", "sh", "yml", "yaml", "ini", "conf", "properties"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String rawPath = QueryUtil.getParam(query, "path");
        String only = QueryUtil.getParam(query, "only"); // "folders" | "files" | null - see SidebarRenderer.java's "Home folders"/"Home files" shortcuts
        String account = QueryUtil.getParam(query, "account");
        account = account == null ? null : URLDecoder.decode(account, "UTF-8");
        String accountId = GDriveAuth.resolveAccount(account);
        List<Crumb> crumbs = parsePath(rawPath);
        String currentFolderId = crumbs.isEmpty() ? "root" : crumbs.get(crumbs.size() - 1).id;

        String html = buildPage(crumbs, currentFolderId, only, accountId);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static List<Crumb> parsePath(String rawPath) {
        List<Crumb> crumbs = new ArrayList<>();
        if (rawPath == null || rawPath.isEmpty()) return crumbs;
        try {
            rawPath = URLDecoder.decode(rawPath, "UTF-8");
        } catch (Exception ignored) {}
        for (String segment : rawPath.split("/")) {
            if (segment.isEmpty()) continue;
            int bar = segment.indexOf('|');
            if (bar == -1) continue;
            crumbs.add(new Crumb(segment.substring(0, bar), segment.substring(bar + 1)));
        }
        return crumbs;
    }

    static String pathFor(List<Crumb> crumbs, int uptoInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= uptoInclusive; i++) {
            if (i > 0) sb.append('/');
            Crumb c = crumbs.get(i);
            sb.append(PathUtil.urlEncode(c.id)).append("%7C").append(PathUtil.urlEncode(c.name));
        }
        return sb.toString();
    }

    private String buildPage(List<Crumb> crumbs, String currentFolderId, String only, String accountId) {
        String acctQS = accountId == null ? "" : "&account=" + PathUtil.urlEncode(accountId);
        GDriveAuth.AccountInfo account = accountId == null ? null : GDriveAuth.getAccountInfo(accountId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Google Drive</title>");
        sb.append(Styles.CSS);
        sb.append(gdriveStyles());
        // Same CDN includes local's preview modal uses for code
        // highlighting and client-side .docx rendering (mammoth.js) - see
        // PageScripts.java's own comment on DOCX_RESOURCES. Just external
        // <script>/<link> tags, nothing local-path-specific, so reusing
        // these two constants directly is safe.
        sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        sb.append(PageScripts.DOCX_RESOURCES);
        sb.append("<script>var GDRIVE_ACCOUNT=").append(jsStringLiteral(accountId)).append(";")
          .append("function gdriveAcctQS(){ return GDRIVE_ACCOUNT?('&account='+encodeURIComponent(GDRIVE_ACCOUNT)):''; }</script>");
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>");
        sb.append("<a href='/gdrive?path=").append(acctQS).append("'>").append(DriveIcon.img(14)).append(" My Drive</a>");
        for (int i = 0; i < crumbs.size(); i++) {
            sb.append(" / ");
            if (i == crumbs.size() - 1) {
                sb.append(PathUtil.htmlEscape(crumbs.get(i).name));
            } else {
                sb.append("<a href='/gdrive?path=").append(pathFor(crumbs, i)).append(acctQS).append("'>")
                  .append(PathUtil.htmlEscape(crumbs.get(i).name)).append("</a>");
            }
        }
        if ("folders".equals(only) || "files".equals(only)) {
            sb.append(" <span class='gdrive-only-badge'>").append("folders".equals(only) ? "Folders only" : "Files only").append("</span>");
        }
        sb.append("</div>");

        // "Signed in as" strip right above the search/filter row - who this
        // whole page's contents belong to, at a glance, so browsing one of
        // several connected accounts' Drives never leaves any doubt about
        // which one is on screen (see ShellScript.java's account picker,
        // which is what let more than one become possible to have open at
        // once in the first place).
        if (account != null) {
            sb.append("<div class='gdrive-account-strip'>");
            if (account.picture != null) {
                sb.append("<img class='gdrive-account-avatar' src='").append(PathUtil.htmlEscape(account.picture)).append("' alt=''>");
            }
            sb.append("<span class='gdrive-account-name'>").append(PathUtil.htmlEscape(account.displayName())).append("</span>");
            if (account.email != null && account.name != null) {
                sb.append("<span class='gdrive-account-email'>").append(PathUtil.htmlEscape(account.email)).append("</span>");
            }
            sb.append("</div>");
        }

        // Same visual slot/markup as local Browse's toolbar (see
        // BrowseHandler.buildToolbar()): a .toolbar row (just the search
        // box here - no sort links, since Drive API ordering isn't
        // client-choosable the same way) followed by a separate
        // .filter-chips row. Pointed at /gdrive-search instead of /search -
        // a whole-Drive name search rather than one scoped to this folder,
        // since Drive items don't have one true path to scope a search
        // "under". Live suggestions reuse /gdrive-suggest, the same
        // endpoint the shell's "/" address bar uses in Drive mode.
        sb.append("<div class='toolbar'>");
        sb.append("<div class='search-suggest-wrap'>");
        sb.append("<form class='search-inline' method='GET' action='/gdrive-search'>")
          .append("<input type='hidden' name='account' value='").append(accountId == null ? "" : PathUtil.htmlEscape(accountId)).append("'>")
          .append("<input type='text' name='q' class='js-gdrive-search-input' placeholder='Search Google Drive...' autocomplete='off'>")
          .append("<button type='submit'>Search</button></form>");
        sb.append("<div class='search-suggestions' id='gdriveSearchSuggestions'></div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append(buildFilterChips());
        sb.append("</div>");

        if (accountId == null) {
            sb.append("<div class='gdrive-empty-state'>");
            sb.append("<p>Google Drive isn't connected yet.</p>");
            sb.append("<p><a href='/settings' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); return false; }\">Connect an account in Settings</a> to browse its Drive files here.</p>");
            sb.append("</div>");
        } else {
            try {
                List<GDriveClient.DriveItem> items = GDriveClient.listChildren(accountId, currentFolderId);
                if ("folders".equals(only)) {
                    items = filterItems(items, true);
                } else if ("files".equals(only)) {
                    items = filterItems(items, false);
                }
                sb.append("<div class='grid'>");
                for (GDriveClient.DriveItem item : items) {
                    if (GDriveClient.isFolder(item.mimeType)) {
                        sb.append(folderCard(crumbs, item, acctQS));
                    } else {
                        sb.append(fileCard(item, acctQS));
                    }
                }
                if (items.isEmpty()) {
                    sb.append("</div><p class='empty'>")
                      .append("folders".equals(only) ? "No folders here." : "files".equals(only) ? "No loose files here." : "This folder is empty.")
                      .append("</p>");
                } else {
                    sb.append("</div>");
                    if (items.size() >= 200) {
                        sb.append("<p class='empty gdrive-more-note'>Showing the first 200 items - larger folders aren't paginated yet.</p>");
                    }
                }
            } catch (IOException e) {
                sb.append("<div class='gdrive-empty-state gdrive-error'>");
                sb.append("<p>Couldn't load this folder from Google Drive.</p>");
                sb.append("<p class='gdrive-error-detail'>").append(PathUtil.htmlEscape(e.getMessage())).append("</p>");
                sb.append("</div>");
            }
        }

        sb.append(PREVIEW_MODAL_HTML);
        sb.append(SEARCH_SUGGEST_SCRIPT);
        sb.append(SELECTION_SCRIPT);
        sb.append(PREVIEW_SCRIPT);
        sb.append(CONTEXT_MENU_SCRIPT);
        sb.append(CHIP_FILTER_SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // A JS string literal (single-quoted, escaped) for embedding accountId
    // (or 'null' when there isn't one) into the inline <script> above -
    // shared spelling with jsString() in GDriveViewerHandler.java, kept as
    // its own tiny copy here rather than a shared util for one line.
    private static String jsStringLiteral(String s) {
        return s == null ? "null" : "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private List<GDriveClient.DriveItem> filterItems(List<GDriveClient.DriveItem> items, boolean foldersOnly) {
        List<GDriveClient.DriveItem> out = new ArrayList<>();
        for (GDriveClient.DriveItem item : items) {
            boolean isFolder = GDriveClient.isFolder(item.mimeType);
            if (isFolder == foldersOnly) out.add(item);
        }
        return out;
    }

    // Same 8 groups as local's chips (GridRenderer.categoryFor() /
    // BrowseHandler.buildFilterChips()) - "Docs" folds Google Docs/Sheets/
    // Slides in alongside regular .doc/.xlsx/.pptx files, same as local
    // folds .doc/.xls/.ppt together, so filtering by file type works the
    // same muscle-memory way in both places.
    static String buildFilterChips() {
        String[][] chips = {
            {"all", "All"}, {"image", "Images"}, {"pdf", "PDFs"}, {"document", "Docs"},
            {"video", "Video"}, {"audio", "Audio"}, {"archive", "Archives"}, {"other", "Other"}
        };
        StringBuilder sb = new StringBuilder("<div class='filter-chips'>");
        for (int i = 0; i < chips.length; i++) {
            String activeClass = i == 0 ? " active" : "";
            sb.append("<span class='chip").append(activeClass).append("' data-filter='").append(chips[i][0])
              .append("'>").append(chips[i][1]).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    // Same grouping GridRenderer.categoryFor() uses locally, extended with
    // Drive's own native mime types up front (a Google Doc/Sheet/Slide has
    // no file extension to fall back on).
    static String categoryFor(String mimeType, String name) {
        if (mimeType != null) {
            if (mimeType.endsWith(".document")) return "document";
            if (mimeType.endsWith(".spreadsheet")) return "spreadsheet";
            if (mimeType.endsWith(".presentation")) return "presentation";
            if (mimeType.endsWith(".form")) return "document";
            if (mimeType.equals("application/pdf")) return "pdf";
            if (mimeType.startsWith("image/")) return "image";
            if (mimeType.startsWith("video/")) return "video";
            if (mimeType.startsWith("audio/")) return "audio";
        }
        return GridRenderer.categoryFor(GridRenderer.getExtension(name).toLowerCase());
    }

    static boolean isTextLike(String mimeType, String name) {
        String ext = GridRenderer.getExtension(name).toLowerCase();
        if (TEXT_LIKE_EXTS.contains(ext)) return true;
        return mimeType != null && mimeType.startsWith("text/");
    }

    // Whether GDrivePreviewHandler's in-grid modal (and /gdrive-viewer) has
    // anything to actually show for this file, vs. just a "download it
    // instead" message - mirrors ViewabilityUtil.isViewable()'s local
    // logic (image/pdf/audio/video/text-like/docx), plus native Google
    // Docs/Sheets/Slides, which preview via Google's own embeddable
    // "/preview" URL (see embeddablePreviewUrl() below).
    static boolean isViewable(String mimeType, String name) {
        if (GDriveClient.isNativeGoogleDoc(mimeType)) return true;
        String ext = GridRenderer.getExtension(name).toLowerCase();
        if (ext.equals("pdf") || ext.equals("docx")) return true;
        if (isTextLike(mimeType, name)) return true;
        if (mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/"))) return true;
        return false;
    }

    // Google Docs/Sheets/Slides' own webViewLink opens their full editor
    // UI, which refuses to render inside an iframe (same anti-clickjacking
    // protection covered in SettingsHandler.java's comment on the OAuth
    // popup). Google separately publishes a read-only, iframe-embeddable
    // "preview" variant of the same URL - this is that well-known,
    // documented convention (swap the trailing "/edit..." for "/preview"),
    // not a guess - used for the in-grid preview modal and the
    // /gdrive-viewer tab. "Open"/"Open in new tab" still use the original
    // webViewLink so editing still works there.
    static String embeddablePreviewUrl(String webViewLink) {
        if (webViewLink == null) return null;
        int editIdx = webViewLink.indexOf("/edit");
        if (editIdx != -1) return webViewLink.substring(0, editIdx) + "/preview";
        return webViewLink;
    }

    private String folderCard(List<Crumb> crumbs, GDriveClient.DriveItem item, String acctQS) {
        List<Crumb> withThis = new ArrayList<>(crumbs);
        withThis.add(new Crumb(item.id, item.name));
        return folderCardForPath(pathFor(withThis, withThis.size() - 1), item.name, item.id, item.webViewLink, acctQS);
    }

    // Shared by folderCard() above (navigating deeper from a known
    // ancestry) and GDriveSearchHandler.java (a search hit has no known
    // ancestry - Drive items don't have one true parent path - so it just
    // treats the hit as if it were a fresh top-level breadcrumb of its own,
    // same as GDriveSuggestHandler.java's address-bar jump-to-folder does).
    //
    // A plain <div>, not an <a> - matching GridRenderer.folderCard()'s
    // local behavior exactly: a single click just selects the card (see
    // SELECTION_SCRIPT), double-click is what actually navigates (see
    // data-gdrive-navurl, read by CONTEXT_MENU_SCRIPT's shared "open-here"
    // logic).
    static String folderCardForPath(String path, String rawName, String id, String webViewLink, String acctQS) {
        String name = PathUtil.htmlEscape(rawName);
        return "<div class=\"card folder\" " +
               "data-gdrive-id=\"" + PathUtil.htmlEscape(id) + "\" data-gdrive-name=\"" + name + "\" " +
               "data-gdrive-kind=\"folder\" data-gdrive-webviewlink=\"" + (webViewLink == null ? "" : PathUtil.htmlEscape(webViewLink)) + "\" " +
               "data-gdrive-navurl=\"/gdrive?path=" + path + acctQS + "\">" +
               "<div class=\"icon\">&#128193;</div>" +
               "<div class=\"name\" title=\"" + name + "\">" + name + "</div>" +
               "</div>";
    }

    static String fileCard(GDriveClient.DriveItem item, String acctQS) {
        String name = PathUtil.htmlEscape(item.name);
        boolean nativeDoc = GDriveClient.isNativeGoogleDoc(item.mimeType);
        String category = categoryFor(item.mimeType, item.name);
        String icon = iconForMime(item.mimeType, item.name);
        String sizeLabel = nativeDoc ? nativeDocLabel(item.mimeType) : GridRenderer.humanSize(item.size);
        boolean viewable = isViewable(item.mimeType, item.name);
        boolean textlike = isTextLike(item.mimeType, item.name);
        String mime = item.mimeType == null ? "" : item.mimeType;

        String downloadUrl = nativeDoc ? "" : "/gdrive-file?id=" + PathUtil.urlEncode(item.id)
              + "&name=" + PathUtil.urlEncode(item.name) + "&mime=" + PathUtil.urlEncode(mime) + acctQS;
        String viewUrl = nativeDoc ? embeddablePreviewUrl(item.webViewLink)
              : (downloadUrl.isEmpty() ? "" : downloadUrl + "&mode=view");

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file\" data-gdrive-id=\"").append(PathUtil.htmlEscape(item.id))
          .append("\" data-gdrive-name=\"").append(name)
          .append("\" data-gdrive-kind=\"file\" data-gdrive-mime=\"").append(PathUtil.htmlEscape(mime))
          .append("\" data-gdrive-category=\"").append(category)
          .append("\" data-gdrive-webviewlink=\"").append(item.webViewLink == null ? "" : PathUtil.htmlEscape(item.webViewLink))
          .append("\" data-gdrive-downloadurl=\"").append(PathUtil.htmlEscape(downloadUrl))
          .append("\" data-gdrive-viewurl=\"").append(viewUrl == null ? "" : PathUtil.htmlEscape(viewUrl))
          .append("\" data-gdrive-viewable=\"").append(viewable ? "1" : "0")
          .append("\" data-gdrive-textlike=\"").append(textlike ? "1" : "0")
          .append("\" data-gdrive-native=\"").append(nativeDoc ? "1" : "0")
          .append("\">");

        // Real thumbnails for images only (matching GridRenderer's own
        // isImage-only thumbnail behavior locally). Proxied through this
        // server's own /gdrive-file (viewUrl) rather than embedding Drive's
        // thumbnailLink directly - thumbnailLink points straight at
        // Google's own domain and needs the *browser's* Google session to
        // be signed into the same account the file belongs to, which for
        // most people viewing this app isn't the case; the browser would
        // just get Google's own "You need access" permission page back
        // instead of a thumbnail. Routing through viewUrl means the image
        // bytes come back already authenticated with this app's own Drive
        // connection, the same way the full preview and /gdrive-viewer
        // already work. onerror still falls back to the plain icon for any
        // other failure (deleted file, transient network error, etc).
        if ("image".equals(category) && !viewUrl.isEmpty()) {
            sb.append("<img class=\"thumb\" src=\"").append(PathUtil.htmlEscape(viewUrl))
              .append("\" loading=\"lazy\" alt=\"\" onerror=\"this.replaceWith(Object.assign(document.createElement('div'),{className:'icon',innerHTML:'").append(icon).append("'}));\">");
        } else {
            sb.append("<div class=\"icon\">").append(icon).append("</div>");
        }
        sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
        sb.append("<div class=\"meta\">").append(sizeLabel).append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    static String nativeDocLabel(String mimeType) {
        if (mimeType == null) return "Google file";
        if (mimeType.endsWith(".document")) return "Google Doc";
        if (mimeType.endsWith(".spreadsheet")) return "Google Sheet";
        if (mimeType.endsWith(".presentation")) return "Google Slides";
        if (mimeType.endsWith(".form")) return "Google Form";
        if (mimeType.endsWith(".drawing")) return "Google Drawing";
        return "Google file";
    }

    static String iconForMime(String mimeType, String name) {
        if (mimeType != null) {
            if (mimeType.endsWith(".document")) return GridRenderer.iconFor("doc");
            if (mimeType.endsWith(".spreadsheet")) return GridRenderer.iconFor("xlsx");
            if (mimeType.endsWith(".presentation")) return GridRenderer.iconFor("pptx");
        }
        String ext = GridRenderer.getExtension(name).toLowerCase();
        return GridRenderer.iconFor(ext);
    }

    // Shared by this page's own search box and GDriveSearchHandler's -
    // live-suggests via /gdrive-suggest as you type; clicking a folder hit
    // jumps straight into it, clicking a file hit re-runs a full search for
    // its exact name (rather than trying to "open" it from just an id/name,
    // which /gdrive-suggest doesn't return enough about to do safely).
    static final String SEARCH_SUGGEST_SCRIPT =
        "<script>" +
        "(function(){" +
        "var input=document.querySelector('.js-gdrive-search-input');" +
        "if(!input) return;" +
        "var box=document.getElementById('gdriveSearchSuggestions');" +
        "var q0=new URLSearchParams(location.search).get('q');" +
        "if(q0) input.value=q0;" +
        "var debounce=null;" +
        "input.addEventListener('input', function(){" +
          "clearTimeout(debounce);" +
          "var q=input.value.trim();" +
          "if(!q){ box.innerHTML=''; box.classList.remove('open'); return; }" +
          "debounce=setTimeout(function(){" +
            "fetch('/gdrive-suggest?q='+encodeURIComponent(q)+gdriveAcctQS()).then(function(r){return r.json();}).then(function(items){" +
              "if(!items.length){ box.innerHTML=''; box.classList.remove('open'); return; }" +
              "box.innerHTML=items.map(function(it){" +
                "var idAttr=String(it.id).replace(/\"/g,'&quot;');" +
                "var nameAttr=String(it.name).replace(/\"/g,'&quot;');" +
                "var icon=it.type==='folder'?'&#128193;':'&#128196;';" +
                "return '<div class=\"search-suggestion-item\" data-id=\"'+idAttr+'\" data-name=\"'+nameAttr+'\" data-type=\"'+it.type+'\">'+" +
                  "'<span class=\"search-suggestion-icon\">'+icon+'</span>'+it.name+'</div>';" +
              "}).join('');" +
              "box.classList.add('open');" +
            "}).catch(function(){});" +
          "}, 150);" +
        "});" +
        "box.addEventListener('click', function(e){" +
          "var item=e.target.closest('.search-suggestion-item');" +
          "if(!item) return;" +
          "var nav=parent&&parent.navigateCurrentTab?parent.navigateCurrentTab:function(u){location.href=u;};" +
          "if(item.dataset.type==='folder'){" +
            "nav('/gdrive?path='+encodeURIComponent(item.dataset.id)+'%7C'+encodeURIComponent(item.dataset.name)+gdriveAcctQS());" +
          "}else{" +
            "nav('/gdrive-search?q='+encodeURIComponent(item.dataset.name)+gdriveAcctQS());" +
          "}" +
        "});" +
        "document.addEventListener('click', function(e){" +
          "if(!e.target.closest('.search-suggest-wrap')){ box.classList.remove('open'); }" +
        "});" +
        "})();" +
        "</script>";

    // Mirrors PageScripts.java's selection model exactly (same click /
    // ctrl-click / shift-click / double-click semantics, same ".selected"
    // CSS class), just keyed by data-gdrive-id instead of data-path. There
    // are no bulk actions wired up to a multi-selection yet (nothing
    // destructive is enabled against Drive in this pass), but the visual
    // behavior matches so the app doesn't feel different to interact with.
    static final String SELECTION_SCRIPT =
        "<script>" +
        "(function(){" +
        "var selectedIds=[];" +
        "var lastSelectedCard=null;" +
        "function allCards(){ return Array.prototype.slice.call(document.querySelectorAll('.card[data-gdrive-id]')); }" +
        "function clearSelection(){ allCards().forEach(function(c){ c.classList.remove('selected'); }); selectedIds=[]; }" +
        "function selectOnly(card){ clearSelection(); card.classList.add('selected'); selectedIds=[card.dataset.gdriveId]; lastSelectedCard=card; }" +
        "function toggleSelect(card){" +
          "var idx=selectedIds.indexOf(card.dataset.gdriveId);" +
          "if(idx===-1){ card.classList.add('selected'); selectedIds.push(card.dataset.gdriveId); }" +
          "else{ card.classList.remove('selected'); selectedIds.splice(idx,1); }" +
          "lastSelectedCard=card;" +
        "}" +
        "function rangeSelect(card){" +
          "var cards=allCards();" +
          "var from=lastSelectedCard?cards.indexOf(lastSelectedCard):0;" +
          "var to=cards.indexOf(card);" +
          "if(from===-1) from=0;" +
          "var start=Math.min(from,to), end=Math.max(from,to);" +
          "clearSelection();" +
          "for(var i=start;i<=end;i++){ cards[i].classList.add('selected'); selectedIds.push(cards[i].dataset.gdriveId); }" +
          "lastSelectedCard=card;" +
        "}" +
        "document.addEventListener('click', function(e){" +
          "var card=e.target.closest('.card[data-gdrive-id]');" +
          "if(!card){" +
            "if(!e.target.closest('.context-menu') && !e.target.closest('.preview-overlay')) clearSelection();" +
            "return;" +
          "}" +
          "if(e.shiftKey){ rangeSelect(card); }" +
          "else if(e.ctrlKey||e.metaKey){ toggleSelect(card); }" +
          "else{ selectOnly(card); }" +
        "});" +
        "document.addEventListener('dblclick', function(e){" +
          "var card=e.target.closest('.card[data-gdrive-id]');" +
          "if(!card) return;" +
          "if(card.dataset.gdriveKind==='folder'){" +
            "var nav=parent&&parent.navigateCurrentTab?parent.navigateCurrentTab:function(u){location.href=u;};" +
            "nav(card.dataset.gdriveNavurl);" +
          "}else if(card.dataset.gdriveViewable==='1'){" +
            "openGDrivePreview(card);" +
          "}" +
        "});" +
        "})();" +
        "</script>";

    // Same visual chrome as PageScripts.MODAL_HTML's #previewOverlay (same
    // CSS classes, all defined globally in Styles.java), own ids so this
    // never collides with anything - not that PageScripts.MODAL_HTML is
    // ever included on the same page anyway. No prev/next-file navigation
    // (PageScripts.java's preview modal steps through sibling cards) - a
    // reasonable scope cut for now, not wired up here.
    static final String PREVIEW_MODAL_HTML =
        "<div id='gdrivePreviewOverlay' class='preview-overlay' onclick=\"if(event.target===this) closeGDrivePreview();\">" +
        "<div class='preview-box'>" +
        "<div class='preview-header'>" +
        "<span id='gdrivePreviewTitle' class='preview-title'></span>" +
        "<div class='preview-header-actions'>" +
        "<span id='gdrivePreviewViewerAction'></span>" +
        "<a id='gdrivePreviewOpenLink' href='#' target='_blank' rel='noopener' class='preview-download'>Open</a>" +
        "<a id='gdrivePreviewDownloadLink' href='#' class='preview-download'>Download</a>" +
        "<button class='preview-close' onclick='closeGDrivePreview()' aria-label='Close'>&times;</button>" +
        "</div></div>" +
        "<div id='gdrivePreviewBody' class='preview-body'></div>" +
        "</div></div>";

    // Adapted from PageScripts.java's openPreview() - same per-type
    // rendering (image/pdf/audio/video/docx-via-mammoth/text-like), plus a
    // native-Google-Docs/Sheets/Slides case those don't have (embedded via
    // embeddablePreviewUrl()'s "/preview" URL - see its comment).
    static final String PREVIEW_SCRIPT =
        "<script>" +
        "var PREVIEW_IMAGE_EXTS=['jpg','jpeg','png','gif','bmp','webp','svg','ico'];" +
        "var PREVIEW_AUDIO_EXTS=['mp3','wav','ogg','m4a','flac','aac'];" +
        "var PREVIEW_VIDEO_EXTS=['mp4','webm','mov','m4v'];" +
        // Same mapping PageScripts.java's preview modal uses for hljs
        // language classes - redeclared here rather than shared, since
        // this page deliberately doesn't load PageScripts.SCRIPT wholesale
        // (see the class comment up top), only its CODE_HIGHLIGHT_RESOURCES
        // (the hljs <script> tags themselves).
        "var CODE_LANG_MAP={java:'java',py:'python',c:'c',cpp:'cpp',h:'cpp',hpp:'cpp',js:'javascript',ts:'typescript',html:'xml',htm:'xml',css:'css',json:'json',xml:'xml',yml:'yaml',yaml:'yaml',sh:'bash',ini:'ini',conf:'ini',properties:'properties'};" +
        "function gdriveExtOf(name){ var i=name.lastIndexOf('.'); return i===-1?'':name.substring(i+1).toLowerCase(); }" +
        "function openGDrivePreview(card){" +
          "var overlay=document.getElementById('gdrivePreviewOverlay');" +
          "var body=document.getElementById('gdrivePreviewBody');" +
          "var name=card.dataset.gdriveName, mime=card.dataset.gdriveMime, category=card.dataset.gdriveCategory;" +
          "var viewUrl=card.dataset.gdriveViewurl, downloadUrl=card.dataset.gdriveDownloadurl, webViewLink=card.dataset.gdriveWebviewlink;" +
          "var isNative=card.dataset.gdriveNative==='1';" +
          "var textlike=card.dataset.gdriveTextlike==='1';" +
          "var ext=gdriveExtOf(name);" +
          "document.getElementById('gdrivePreviewTitle').textContent=name;" +
          "var openLink=document.getElementById('gdrivePreviewOpenLink');" +
          "openLink.href=webViewLink||'#'; openLink.style.display=webViewLink?'':'none';" +
          "var dlLink=document.getElementById('gdrivePreviewDownloadLink');" +
          "dlLink.href=downloadUrl||'#'; dlLink.style.display=downloadUrl?'':'none';" +
          "var viewerAction=document.getElementById('gdrivePreviewViewerAction');" +
          "if(ext==='pdf'||textlike||isNative){" +
            "viewerAction.innerHTML='<a href=\"#\" onclick=\"openGDrivePreviewInViewer(); return false;\" class=\"preview-download\">Open in Viewer</a>';" +
          "}else{ viewerAction.innerHTML=''; }" +
          "gdrivePreviewViewerHref='/gdrive-viewer?id='+encodeURIComponent(card.dataset.gdriveId)+'&name='+encodeURIComponent(name)+'&mime='+encodeURIComponent(mime||'')+gdriveAcctQS();" +
          "body.innerHTML='';" +
          "if(isNative){" +
            "body.innerHTML='<iframe src=\"'+viewUrl+'\"></iframe>';" +
          "}else if(category==='image'){" +
            "body.innerHTML='<img src=\"'+viewUrl+'\" alt=\"\">';" +
          "}else if(ext==='pdf'){" +
            "body.innerHTML='<iframe src=\"'+viewUrl+'\"></iframe>';" +
          "}else if(category==='audio'){" +
            "body.innerHTML='<audio controls autoplay src=\"'+viewUrl+'\"></audio>';" +
          "}else if(category==='video'){" +
            "body.innerHTML='<video controls autoplay src=\"'+viewUrl+'\"></video>';" +
          "}else if(ext==='docx'){" +
            "body.innerHTML='<div class=\"docx-loading\">Loading document...</div>';" +
            "fetch(viewUrl).then(function(r){return r.arrayBuffer();}).then(function(buf){" +
              "if(!window.mammoth) throw new Error('renderer unavailable');" +
              "return mammoth.convertToHtml({arrayBuffer:buf});" +
            "}).then(function(result){ body.innerHTML='<div class=\"docx-preview\">'+result.value+'</div>'; })" +
            ".catch(function(){" +
              "body.innerHTML='<div class=\"preview-nopreview\"><p>Could not render a preview for this document.</p>" +
                "<p><a href=\"'+downloadUrl+'\" class=\"preview-download\">Download instead</a></p></div>';" +
            "});" +
          "}else if(textlike){" +
            "body.innerHTML='<div class=\"docx-loading\">Loading...</div>';" +
            "fetch(viewUrl).then(function(r){return r.text();}).then(function(text){" +
              "if(['txt','log','csv','md'].indexOf(ext)!==-1){" +
                "var pre=document.createElement('pre'); pre.textContent=text; body.innerHTML=''; body.appendChild(pre);" +
              "}else{" +
                "var d=document.createElement('div'); d.textContent=text; var esc=d.innerHTML;" +
                "var lang=CODE_LANG_MAP[ext]||'';" +
                "var lc=lang?' class=\"language-'+lang+'\"':'';" +
                "body.innerHTML='<pre class=\"code-highlighted\"><code id=\"gdrivePreviewCodeBlock\"'+lc+'>'+esc+'</code></pre>'+" +
                  "'<pre class=\"code-raw plain-text\" style=\"display:none;\">'+esc+'</pre>';" +
                "if(window.hljs){ hljs.highlightElement(document.getElementById('gdrivePreviewCodeBlock')); }" +
                "viewerAction.innerHTML+='<a href=\"#\" onclick=\"toggleGDrivePreviewCodeView(); return false;\" id=\"gdrivePreviewToggleRawBtn\" class=\"preview-download\">Raw text</a>';" +
              "}" +
            "}).catch(function(){" +
              "body.innerHTML=\"<div class='preview-nopreview'><p>Couldn't load this file.</p></div>\";" +
            "});" +
          "}else{" +
            "body.innerHTML=\"<div class='preview-nopreview'><p>There's no in-browser preview for this file type.</p></div>\";" +
          "}" +
          "overlay.classList.add('open');" +
        "}" +
        "var gdrivePreviewViewerHref='';" +
        "function toggleGDrivePreviewCodeView(){" +
          "var h=document.querySelector('#gdrivePreviewBody .code-highlighted'), r=document.querySelector('#gdrivePreviewBody .code-raw'), b=document.getElementById('gdrivePreviewToggleRawBtn');" +
          "if(!h||!r) return;" +
          "var showingRaw=r.style.display!=='none';" +
          "if(showingRaw){ r.style.display='none'; h.style.display=''; b.textContent='Raw text'; }" +
          "else{ r.style.display=''; h.style.display='none'; b.textContent='Formatted'; }" +
        "}" +
        "function openGDrivePreviewInViewer(){" +
          "var nav=parent&&parent.navigateCurrentTab?parent.navigateCurrentTab:function(u){location.href=u;};" +
          "closeGDrivePreview();" +
          "if(parent&&parent.openTab){ parent.openTab(gdrivePreviewViewerHref,'Loading...',true); } else { nav(gdrivePreviewViewerHref); }" +
        "}" +
        "function closeGDrivePreview(){" +
          "document.getElementById('gdrivePreviewOverlay').classList.remove('open');" +
          "document.getElementById('gdrivePreviewBody').innerHTML='';" +
        "}" +
        "document.addEventListener('keydown', function(e){" +
          "if(e.key==='Escape') closeGDrivePreview();" +
          // Same "/" -> address bar forwarding PageScripts.java's local
          // pages do (see its keydown listener) - this page just never had
          // its own copy, so pressing "/" while a Drive tab had focus did
          // nothing instead of opening the shell's address bar in Drive
          // mode, unlike every local Browse/Dashboard/Trash tab.
          "if(e.key==='/' && document.activeElement.tagName!=='INPUT' && document.activeElement.tagName!=='TEXTAREA'){" +
            "if(window.parent && window.parent!==window && window.parent.openAddressBar){" +
              "e.preventDefault();" +
              "window.parent.openAddressBar();" +
            "}" +
          "}" +
        "});" +
        "</script>";

    // Right-click menu for Drive cards. Reuses the same .context-menu /
    // .context-menu-item CSS already defined globally in Styles.java (the
    // same classes local browsing's PageScripts.java uses), so it looks
    // identical. Read operations (Open, Open in new tab, Preview, Open
    // Viewer, Download, Copy link, Refresh) are fully wired up; write
    // operations (Rename, Move to..., Delete, New folder, Upload) are
    // shown but disabled with a "read-only for now" tooltip, ready to be
    // enabled once Drive write support exists.
    static final String CONTEXT_MENU_SCRIPT =
        "<div id='gdriveContextMenu' class='context-menu'></div>" +
        "<script>" +
        "(function(){" +
        "var menu=document.getElementById('gdriveContextMenu');" +
        "var DISABLED_TITLE='Coming soon - Google Drive is read-only here for now';" +
        "function closeMenu(){ menu.classList.remove('open'); menu.innerHTML=''; }" +
        "function menuItem(label, action, enabled){" +
          "if(enabled){ return '<div class=\"context-menu-item\" data-gdrive-action=\"'+action+'\">'+label+'</div>'; }" +
          "return '<div class=\"context-menu-item context-menu-item-disabled\" title=\"'+DISABLED_TITLE+'\">'+label+'</div>';" +
        "}" +
        "function openMenuAt(x, y, html){" +
          "menu.innerHTML=html;" +
          "menu.style.left=x+'px';" +
          "menu.style.top=y+'px';" +
          "menu.classList.add('open');" +
          "var rect=menu.getBoundingClientRect();" +
          "if(rect.right>window.innerWidth){ menu.style.left=Math.max(0,x-rect.width)+'px'; }" +
          "if(rect.bottom>window.innerHeight){ menu.style.top=Math.max(0,y-rect.height)+'px'; }" +
        "}" +
        "document.addEventListener('contextmenu', function(e){" +
          "var card=e.target.closest('.card[data-gdrive-id]');" +
          "if(card){" +
            "e.preventDefault();" +
            "if(!card.classList.contains('selected')){" +
              "document.querySelectorAll('.card.selected').forEach(function(c){c.classList.remove('selected');});" +
              "card.classList.add('selected');" +
            "}" +
            "var kind=card.dataset.gdriveKind;" +
            "var webViewLink=card.dataset.gdriveWebviewlink;" +
            "var viewable=card.dataset.gdriveViewable==='1';" +
            "var ext=gdriveExtOf(card.dataset.gdriveName||'');" +
            "var textlike=card.dataset.gdriveTextlike==='1';" +
            "var isNative=card.dataset.gdriveNative==='1';" +
            "var items=[];" +
            "if(kind==='folder'){ items.push(menuItem('Open', 'open-here', true)); }" +
            "else if(viewable){ items.push(menuItem('Preview', 'preview', true)); }" +
            "if(webViewLink){ items.push(menuItem('Open in Google Drive', 'open-external', true)); }" +
            "if(kind==='file' && (ext==='pdf'||textlike||isNative)){ items.push(menuItem('Open Viewer', 'open-viewer', true)); }" +
            "if(kind==='file' && card.dataset.gdriveDownloadurl){ items.push(menuItem('Download', 'download', true)); }" +
            "if(webViewLink){ items.push(menuItem('Copy link', 'copy-link', true)); }" +
            "items.push('<div class=\"context-menu-divider\"></div>');" +
            "items.push(menuItem('Rename', 'rename', false));" +
            "items.push(menuItem('Move to...', 'move', false));" +
            "items.push(menuItem('Delete', 'delete', false));" +
            "items.push('<div class=\"context-menu-divider\"></div>');" +
            "items.push(menuItem('Refresh', 'refresh', true));" +
            "menu.dataset.gdriveTargetCardId=card.dataset.gdriveId+'|'+kind;" +
            "openMenuAt(e.clientX, e.clientY, items.join(''));" +
            "return;" +
          "}" +
          "if(e.target.closest('.grid')){" +
            "e.preventDefault();" +
            "var emptyItems=[" +
              "menuItem('New folder', 'new-folder', false)," +
              "menuItem('Upload here', 'upload', false)," +
              "'<div class=\"context-menu-divider\"></div>'," +
              "menuItem('Refresh', 'refresh', true)" +
            "];" +
            "menu.dataset.gdriveTargetCardId='';" +
            "openMenuAt(e.clientX, e.clientY, emptyItems.join(''));" +
          "}" +
        "});" +
        "menu.addEventListener('click', function(e){" +
          "var itemEl=e.target.closest('[data-gdrive-action]');" +
          "if(!itemEl) return;" +
          "var action=itemEl.dataset.gdriveAction;" +
          "var targetId=(menu.dataset.gdriveTargetCardId||'').split('|')[0];" +
          "var card=targetId?document.querySelector('.card[data-gdrive-id=\"'+CSS.escape(targetId)+'\"]'):null;" +
          "var nav=parent&&parent.navigateCurrentTab?parent.navigateCurrentTab:function(u){location.href=u;};" +
          "if(action==='open-here' && card){ nav(card.dataset.gdriveNavurl); }" +
          "else if(action==='preview' && card){ openGDrivePreview(card); }" +
          "else if(action==='open-viewer' && card){" +
            "var href='/gdrive-viewer?id='+encodeURIComponent(card.dataset.gdriveId)+'&name='+encodeURIComponent(card.dataset.gdriveName)+'&mime='+encodeURIComponent(card.dataset.gdriveMime||'')+gdriveAcctQS();" +
            "if(parent&&parent.openTab){ parent.openTab(href,'Loading...',true); } else { nav(href); }" +
          "}" +
          "else if(action==='open-external' && card){ window.open(card.dataset.gdriveWebviewlink,'_blank','noopener'); }" +
          "else if(action==='download' && card){ location.href=card.dataset.gdriveDownloadurl; }" +
          "else if(action==='copy-link' && card){" +
            "if(navigator.clipboard){ navigator.clipboard.writeText(card.dataset.gdriveWebviewlink).catch(function(){}); }" +
          "}" +
          "else if(action==='refresh'){ location.reload(); }" +
          "closeMenu();" +
        "});" +
        "document.addEventListener('click', function(e){ if(!e.target.closest('.context-menu')){ closeMenu(); } });" +
        "document.addEventListener('scroll', closeMenu, true);" +
        "window.addEventListener('blur', closeMenu);" +
        "})();" +
        "</script>";

    // Same filter-chip mechanism as PageScripts.java's (folders always stay
    // visible regardless of the active chip; only ".card.file" gets
    // shown/hidden) - a small standalone copy rather than sharing code with
    // PageScripts.java, consistent with why this whole page doesn't include
    // that script (see class comment).
    static final String CHIP_FILTER_SCRIPT =
        "<script>" +
        "var GDRIVE_CHIP_GROUPS={all:null,image:['image'],pdf:['pdf'],document:['document','spreadsheet','presentation']," +
          "video:['video'],audio:['audio'],archive:['archive'],other:['other']};" +
        "document.addEventListener('click', function(e){" +
          "var chip=e.target.closest('.chip');" +
          "if(!chip) return;" +
          "document.querySelectorAll('.chip').forEach(function(c){ c.classList.remove('active'); });" +
          "chip.classList.add('active');" +
          "var group=GDRIVE_CHIP_GROUPS[chip.dataset.filter];" +
          "document.querySelectorAll('.card.file').forEach(function(card){" +
            "var show=!group||group.indexOf(card.dataset.gdriveCategory)!==-1;" +
            "card.style.display=show?'':'none';" +
          "});" +
        "});" +
        "</script>";

    static String gdriveStyles() {
        return "<style>" +
            ".gdrive-empty-state{padding:48px 24px;text-align:center;color:#666;font-size:14px;line-height:1.7;}" +
            ".gdrive-empty-state a{color:#2563eb;text-decoration:none;}" +
            ".gdrive-empty-state a:hover{text-decoration:underline;}" +
            ".gdrive-error{color:#9c1f1f;}" +
            ".gdrive-error-detail{font-size:12px;color:#888;}" +
            ".gdrive-more-note{padding:0 24px 24px;}" +
            ".gdrive-only-badge{font-size:12px;color:#666;background:#eef0f2;padding:3px 9px;border-radius:10px;}" +
            // "Signed in as" strip above the search/filter row (see
            // buildPage()) - small enough not to compete with the toolbar,
            // but present on every Drive page so which account's files are
            // on screen is never ambiguous, especially once more than one
            // is connected.
            ".gdrive-account-strip{display:flex;align-items:center;gap:8px;padding:8px 24px;color:#555;font-size:12px;}" +
            ".gdrive-account-avatar{width:20px;height:20px;border-radius:50%;flex-shrink:0;}" +
            ".gdrive-account-name{font-weight:600;color:#333;}" +
            ".gdrive-account-email{color:#888;}" +
            "</style>";
    }
}
