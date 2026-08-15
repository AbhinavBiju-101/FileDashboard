import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves "/gdrive?path=..." - browsing Google Drive, reusing the exact same
 * .grid/.card/.icon/.name/.meta CSS classes as local Browse/Dashboard/Trash
 * so it looks like part of the same app rather than a bolted-on extra page.
 *
 * Deliberately read-only for this first pass (no upload/rename/move/delete
 * against Drive) and deliberately does NOT load PageScripts.java: that
 * script's selection/context-menu/rename/move/delete logic all POST to
 * /fileops against local filesystem paths, and reusing it here would either
 * silently do nothing or - worse - do something to a local path that
 * happens to collide with a Drive-shaped one. Cards here are plain links
 * instead: click a folder to navigate, click "Open"/"Download" on a file.
 *
 * Drive has no real paths - files just have parent folder ids - so the
 * "path" query param is a synthetic breadcrumb trail this handler invented:
 * "id|urlencodedName" segments joined by "/", e.g.
 * "1AbcId|Projects/1XyzId|Reports". Carrying the name alongside each id
 * means rendering the breadcrumb never needs extra API calls just to know
 * what to label each level.
 *
 * Unverified against real Google endpoints - see GDriveAuth.java's class
 * comment.
 */
public class GDriveBrowseHandler implements HttpHandler {

    private static class Crumb {
        final String id;
        final String name;
        Crumb(String id, String name) { this.id = id; this.name = name; }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String rawPath = QueryUtil.getParam(query, "path");
        String only = QueryUtil.getParam(query, "only"); // "folders" | "files" | null - see SidebarRenderer.java's "Home folders"/"Home files" shortcuts
        List<Crumb> crumbs = parsePath(rawPath);
        String currentFolderId = crumbs.isEmpty() ? "root" : crumbs.get(crumbs.size() - 1).id;

        String html = buildPage(crumbs, currentFolderId, only);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private List<Crumb> parsePath(String rawPath) {
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

    private String pathFor(List<Crumb> crumbs, int uptoInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= uptoInclusive; i++) {
            if (i > 0) sb.append('/');
            Crumb c = crumbs.get(i);
            sb.append(PathUtil.urlEncode(c.id)).append('|').append(PathUtil.urlEncode(c.name));
        }
        return sb.toString();
    }

    private String buildPage(List<Crumb> crumbs, String currentFolderId, String only) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Google Drive</title>");
        sb.append(Styles.CSS);
        sb.append(gdriveStyles());
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>");
        sb.append("<a href='/gdrive?path='>My Drive</a>");
        for (int i = 0; i < crumbs.size(); i++) {
            sb.append(" / ");
            if (i == crumbs.size() - 1) {
                sb.append(PathUtil.htmlEscape(crumbs.get(i).name));
            } else {
                sb.append("<a href='/gdrive?path=").append(pathFor(crumbs, i)).append("'>")
                  .append(PathUtil.htmlEscape(crumbs.get(i).name)).append("</a>");
            }
        }
        sb.append("</div>");
        if ("folders".equals(only) || "files".equals(only)) {
            sb.append("<span class='gdrive-only-badge'>").append("folders".equals(only) ? "Folders" : "Files").append(" only</span>");
        }
        // Same visual pattern as local Browse's inline search box (see
        // BrowseHandler.buildToolbar()), pointed at /gdrive-search instead
        // of /search - a whole-Drive name search rather than one scoped to
        // this folder, since Drive items don't have one true path to scope
        // a search "under". Live suggestions reuse /gdrive-suggest, the
        // same endpoint the shell's "/" address bar uses in Drive mode.
        sb.append("<div class='search-suggest-wrap'>");
        sb.append("<form class='search-inline' method='GET' action='/gdrive-search'>")
          .append("<input type='text' name='q' class='js-gdrive-search-input' placeholder='Search Google Drive...' autocomplete='off'>")
          .append("<button type='submit'>Search</button></form>");
        sb.append("<div class='search-suggestions' id='gdriveSearchSuggestions'></div>");
        sb.append("</div>");
        sb.append("</div>");

        if (!GDriveAuth.isConnected()) {
            sb.append("<div class='gdrive-empty-state'>");
            sb.append("<p>Google Drive isn't connected yet.</p>");
            sb.append("<p><a href='/settings' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); return false; }\">Connect it in Settings</a> to browse your Drive files here.</p>");
            sb.append("</div>");
        } else {
            try {
                List<GDriveClient.DriveItem> items = GDriveClient.listChildren(currentFolderId);
                if ("folders".equals(only)) {
                    items = filterItems(items, true);
                } else if ("files".equals(only)) {
                    items = filterItems(items, false);
                }
                sb.append("<div class='grid'>");
                for (GDriveClient.DriveItem item : items) {
                    if (GDriveClient.isFolder(item.mimeType)) {
                        sb.append(folderCard(crumbs, item));
                    } else {
                        sb.append(fileCard(item));
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

        sb.append(SEARCH_SUGGEST_SCRIPT);
        sb.append(CONTEXT_MENU_SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private List<GDriveClient.DriveItem> filterItems(List<GDriveClient.DriveItem> items, boolean foldersOnly) {
        List<GDriveClient.DriveItem> out = new ArrayList<>();
        for (GDriveClient.DriveItem item : items) {
            boolean isFolder = GDriveClient.isFolder(item.mimeType);
            if (isFolder == foldersOnly) out.add(item);
        }
        return out;
    }

    // Shared by this page's own search box and GDriveSearchHandler's -
    // live-suggests via /gdrive-suggest as you type; clicking a folder hit
    // jumps straight into it, clicking a file hit re-runs a full search for
    // its exact name (rather than trying to "open" it from just an id/name,
    // which /gdrive-suggest doesn't return enough about to do safely).
    static final String SEARCH_SUGGEST_SCRIPT =        "<script>" +
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
            "fetch('/gdrive-suggest?q='+encodeURIComponent(q)).then(function(r){return r.json();}).then(function(items){" +
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
            "nav('/gdrive?path='+encodeURIComponent(item.dataset.id)+'|'+encodeURIComponent(item.dataset.name));" +
          "}else{" +
            "nav('/gdrive-search?q='+encodeURIComponent(item.dataset.name));" +
          "}" +
        "});" +
        "document.addEventListener('click', function(e){" +
          "if(!e.target.closest('.search-suggest-wrap')){ box.classList.remove('open'); }" +
        "});" +
        "})();" +
        "</script>";

    // Right-click menu for Drive cards. Reuses the same .context-menu /
    // .context-menu-item CSS already defined globally in Styles.java (the
    // same classes local browsing's PageScripts.java uses), so it looks
    // identical - but it's a separate, smaller script rather than actually
    // sharing code with PageScripts.java, since that one is deeply built
    // around local filesystem paths (data-path, /fileops, /subfolders,
    // etc.) throughout, and Drive items are addressed by id, not path.
    // Only read operations are wired up (Open, Open in new tab, Download,
    // Copy link, Refresh); write operations (Rename, Move to..., Delete,
    // New folder, Upload) are shown but disabled with a "read-only for
    // now" tooltip, ready to be enabled once Drive write support exists.
    static final String CONTEXT_MENU_SCRIPT =
        "<div id='gdriveContextMenu' class='context-menu'></div>" +
        "<script>" +
        "(function(){" +
        "var menu=document.getElementById('gdriveContextMenu');" +
        "var DISABLED_TITLE='Coming soon - Google Drive is read-only here for now';" +
        "function closeMenu(){ menu.classList.remove('open'); menu.innerHTML=''; }" +
        "function menuItem(label, action, enabled){" +
          "if(enabled){" +
            "return '<div class=\"context-menu-item\" data-gdrive-action=\"'+action+'\">'+label+'</div>';" +
          "}" +
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
            "var kind=card.dataset.gdriveKind;" +
            "var webViewLink=card.dataset.gdriveWebviewlink;" +
            "var items=[];" +
            "if(webViewLink){ items.push(menuItem('Open', 'open', true)); items.push(menuItem('Open in new tab', 'open-new-tab', true)); }" +
            "if(kind==='folder'){ items.push(menuItem('Open here', 'open-here', true)); }" +
            "if(kind==='file' && card.dataset.gdriveDownloadurl){ items.push(menuItem('Download', 'download', true)); }" +
            "if(webViewLink){ items.push(menuItem('Copy link', 'copy-link', true)); }" +
            "items.push('<div class=\"context-menu-divider\"></div>');" +
            "items.push(menuItem('Rename', 'rename', false));" +
            "items.push(menuItem('Move to...', 'move', false));" +
            "items.push(menuItem('Delete', 'delete', false));" +
            "items.push('<div class=\"context-menu-divider\"></div>');" +
            "items.push(menuItem('Refresh', 'refresh', true));" +
            "menu.dataset.gdriveTargetId=card.dataset.gdriveId;" +
            "menu.dataset.gdriveTargetName=card.dataset.gdriveName;" +
            "menu.dataset.gdriveTargetWebviewlink=webViewLink||'';" +
            "menu.dataset.gdriveTargetDownloadurl=card.dataset.gdriveDownloadurl||'';" +
            "menu.dataset.gdriveTargetNavurl=card.dataset.gdriveNavurl||'';" +
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
            "menu.dataset.gdriveTargetId='';" +
            "openMenuAt(e.clientX, e.clientY, emptyItems.join(''));" +
          "}" +
        "});" +
        "menu.addEventListener('click', function(e){" +
          "var item=e.target.closest('[data-gdrive-action]');" +
          "if(!item) return;" +
          "var action=item.dataset.gdriveAction;" +
          "var webViewLink=menu.dataset.gdriveTargetWebviewlink;" +
          "var downloadUrl=menu.dataset.gdriveTargetDownloadurl;" +
          "var navUrl=menu.dataset.gdriveTargetNavurl;" +
          "var nav=parent&&parent.navigateCurrentTab?parent.navigateCurrentTab:function(u){location.href=u;};" +
          "if(action==='open'||action==='open-new-tab'){ window.open(webViewLink,'_blank','noopener'); }" +
          "else if(action==='open-here'){ nav(navUrl); }" +
          "else if(action==='download'){ location.href=downloadUrl; }" +
          "else if(action==='copy-link'){" +
            "if(navigator.clipboard){ navigator.clipboard.writeText(webViewLink).catch(function(){}); }" +
          "}" +
          "else if(action==='refresh'){ location.reload(); }" +
          "closeMenu();" +
        "});" +
        "document.addEventListener('click', function(e){ if(!e.target.closest('.context-menu')){ closeMenu(); } });" +
        "document.addEventListener('scroll', closeMenu, true);" +
        "window.addEventListener('blur', closeMenu);" +
        "})();" +
        "</script>";

    private String folderCard(List<Crumb> crumbs, GDriveClient.DriveItem item) {
        List<Crumb> withThis = new ArrayList<>(crumbs);
        withThis.add(new Crumb(item.id, item.name));
        return folderCardForPath(pathFor(withThis, withThis.size() - 1), item.name, item.id, item.webViewLink);
    }

    // Shared by folderCard() above (navigating deeper from a known
    // ancestry) and GDriveSearchHandler.java (a search hit has no known
    // ancestry - Drive items don't have one true parent path - so it just
    // treats the hit as if it were a fresh top-level breadcrumb of its own,
    // same as GDriveSuggestHandler.java's address-bar jump-to-folder does).
    // The data-gdrive-* attributes are what GDRIVE_CONTEXT_MENU_SCRIPT
    // reads to build its right-click menu.
    static String folderCardForPath(String path, String rawName, String id, String webViewLink) {
        String name = PathUtil.htmlEscape(rawName);
        return "<a class=\"card folder\" href=\"/gdrive?path=" + path + "\" " +
               "data-gdrive-id=\"" + PathUtil.htmlEscape(id) + "\" data-gdrive-name=\"" + name + "\" " +
               "data-gdrive-kind=\"folder\" data-gdrive-webviewlink=\"" + (webViewLink == null ? "" : PathUtil.htmlEscape(webViewLink)) + "\" " +
               "data-gdrive-navurl=\"/gdrive?path=" + path + "\">" +
               "<div class=\"icon\">&#128193;</div>" +
               "<div class=\"name\" title=\"" + name + "\">" + name + "</div>" +
               "</a>";
    }

    static String fileCard(GDriveClient.DriveItem item) {
        String name = PathUtil.htmlEscape(item.name);
        boolean nativeDoc = GDriveClient.isNativeGoogleDoc(item.mimeType);
        String icon = iconForMime(item.mimeType, item.name);
        String sizeLabel = nativeDoc ? nativeDocLabel(item.mimeType) : GridRenderer.humanSize(item.size);

        StringBuilder sb = new StringBuilder();
        String downloadUrl = nativeDoc ? "" : "/gdrive-file?id=" + PathUtil.urlEncode(item.id)
              + "&name=" + PathUtil.urlEncode(item.name)
              + "&mime=" + PathUtil.urlEncode(item.mimeType == null ? "" : item.mimeType);
        sb.append("<div class=\"card file\" data-gdrive-id=\"").append(PathUtil.htmlEscape(item.id))
          .append("\" data-gdrive-name=\"").append(name)
          .append("\" data-gdrive-kind=\"file\" data-gdrive-webviewlink=\"")
          .append(item.webViewLink == null ? "" : PathUtil.htmlEscape(item.webViewLink))
          .append("\" data-gdrive-downloadurl=\"").append(PathUtil.htmlEscape(downloadUrl)).append("\">");
        sb.append("<div class=\"icon\">").append(icon).append("</div>");
        sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
        sb.append("<div class=\"meta\">").append(sizeLabel).append("</div>");
        sb.append("<div class=\"meta gdrive-actions\">");
        if (item.webViewLink != null) {
            sb.append("<a href=\"").append(PathUtil.htmlEscape(item.webViewLink)).append("\" target=\"_blank\" rel=\"noopener\">Open</a>");
        }
        if (!nativeDoc) {
            if (item.webViewLink != null) sb.append(" &middot; ");
            sb.append("<a href=\"").append(PathUtil.htmlEscape(downloadUrl)).append("\">Download</a>");
        }
        sb.append("</div>");
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

    private String gdriveStyles() {
        return "<style>" +
            ".gdrive-empty-state{padding:48px 24px;text-align:center;color:#666;font-size:14px;line-height:1.7;}" +
            ".gdrive-empty-state a{color:#2563eb;text-decoration:none;}" +
            ".gdrive-empty-state a:hover{text-decoration:underline;}" +
            ".gdrive-error{color:#9c1f1f;}" +
            ".gdrive-error-detail{font-size:12px;color:#888;}" +
            ".gdrive-actions{margin-top:2px;}" +
            ".gdrive-actions a{color:#2563eb;text-decoration:none;}" +
            ".gdrive-actions a:hover{text-decoration:underline;}" +
            ".gdrive-more-note{padding:0 24px 24px;}" +
            ".gdrive-only-badge{margin-left:12px;font-size:12px;color:#666;background:#eef0f2;padding:3px 9px;border-radius:10px;}" +
            "</style>";
    }
}
