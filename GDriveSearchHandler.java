import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves "/gdrive-search?q=..." - a name search across the whole connected
 * Drive (see GDriveClient.search()'s comment on why this isn't scoped to a
 * starting folder the way local /search is). Reuses GDriveBrowseHandler's
 * card renderers, filter chips, preview modal, and scripts wholesale so a
 * hit looks and behaves exactly like it would sitting in a normal folder
 * listing; clicking into a folder hit treats it as a fresh top-level
 * breadcrumb of its own, same simplification GDriveSuggestHandler makes for
 * the address bar.
 */
public class GDriveSearchHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        q = q == null ? "" : URLDecoder.decode(q, "UTF-8");
        String account = QueryUtil.getParam(query, "account");
        account = account == null ? null : URLDecoder.decode(account, "UTF-8");
        String accountId = GDriveAuth.resolveAccount(account);

        String html = buildPage(q, accountId);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(String q, String accountId) {
        String acctQS = accountId == null ? "" : "&account=" + PathUtil.urlEncode(accountId);
        GDriveAuth.AccountInfo account = accountId == null ? null : GDriveAuth.getAccountInfo(accountId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Search Google Drive</title>");
        sb.append(Styles.CSS);
        sb.append(GDriveBrowseHandler.gdriveStyles());
        sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        sb.append(PageScripts.DOCX_RESOURCES);
        sb.append("<script>var GDRIVE_ACCOUNT=").append(accountId == null ? "null" : ("'" + accountId.replace("\\", "\\\\").replace("'", "\\'") + "'")).append(";")
          .append("function gdriveAcctQS(){ return GDRIVE_ACCOUNT?('&account='+encodeURIComponent(GDRIVE_ACCOUNT)):''; }</script>");
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'><a href='/gdrive?path=").append(acctQS).append("'>").append(DriveIcon.img(14)).append(" My Drive</a> / Search results for \u201c")
          .append(PathUtil.htmlEscape(q)).append("\u201d</div>");
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
        sb.append("<div class='toolbar'>");
        sb.append("<div class='search-suggest-wrap'>");
        sb.append("<form class='search-inline' method='GET' action='/gdrive-search'>")
          .append("<input type='hidden' name='account' value='").append(accountId == null ? "" : PathUtil.htmlEscape(accountId)).append("'>")
          .append("<input type='text' name='q' class='js-gdrive-search-input' placeholder='Search Google Drive...' autocomplete='off'>")
          .append("<button type='submit'>Search</button></form>");
        sb.append("<div class='search-suggestions' id='gdriveSearchSuggestions'></div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append(GDriveBrowseHandler.buildFilterChips());
        sb.append("</div>");

        if (accountId == null) {
            sb.append("<div class='gdrive-empty-state'><p>Google Drive isn't connected. <a href='/settings' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); return false; }\">Connect it in Settings</a>.</p></div>");
        } else if (q.trim().isEmpty()) {
            sb.append("<div class='gdrive-empty-state'><p>Type something to search for in Google Drive.</p></div>");
        } else {
            try {
                List<GDriveClient.DriveItem> items = GDriveClient.search(accountId, q);
                if (items.isEmpty()) {
                    sb.append("<div class='gdrive-empty-state'><p>No matches for \u201c").append(PathUtil.htmlEscape(q)).append("\u201d in Drive.</p></div>");
                } else {
                    sb.append("<div class='grid'>");
                    for (GDriveClient.DriveItem item : items) {
                        if (GDriveClient.isFolder(item.mimeType)) {
                            sb.append(GDriveBrowseHandler.folderCardForPath(
                                PathUtil.urlEncode(item.id) + "%7C" + PathUtil.urlEncode(item.name),
                                item.name, item.id, item.webViewLink, acctQS));
                        } else {
                            sb.append(GDriveBrowseHandler.fileCard(item, acctQS));
                        }
                    }
                    sb.append("</div>");
                }
            } catch (IOException e) {
                sb.append("<div class='gdrive-empty-state gdrive-error'><p>Couldn't search Google Drive.</p><p class='gdrive-error-detail'>")
                  .append(PathUtil.htmlEscape(e.getMessage())).append("</p></div>");
            }
        }

        sb.append(GDriveBrowseHandler.PREVIEW_MODAL_HTML);
        sb.append(GDriveBrowseHandler.SEARCH_SUGGEST_SCRIPT);
        sb.append(GDriveBrowseHandler.SELECTION_SCRIPT);
        sb.append(GDriveBrowseHandler.PREVIEW_SCRIPT);
        sb.append(GDriveBrowseHandler.CONTEXT_MENU_SCRIPT);
        sb.append(GDriveBrowseHandler.CHIP_FILTER_SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
