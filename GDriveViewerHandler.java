import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/gdrive-viewer?id=...&name=...&mime=..." - the Drive counterpart
 * to ViewerHandler.java's "/viewer": a dedicated full-tab reading view,
 * opened via Drive cards' right-click "Open Viewer" menu item or the
 * preview modal's "Open in Viewer" link (see GDriveBrowseHandler.java),
 * rather than the small overlay - meant for actually reading something at
 * length. Reuses ViewerHandler's exact CSS (viewerStyles()) so it looks
 * like the same feature, not a lookalike.
 *
 * No Previous/Next sibling navigation the way the local viewer has - Drive
 * items don't have one true "folder listing" this handler already knows
 * (it's only ever given a single file id), and building that would mean an
 * extra API call just to look up siblings. A reasonable scope cut for now.
 *
 * Three rendering paths:
 *   - Native Google Docs/Sheets/Slides/Forms: an iframe onto Google's own
 *     embeddable "/preview" URL (see GDriveBrowseHandler.embeddablePreviewUrl()).
 *   - PDF: an iframe onto this server's own inline-mode file stream.
 *   - Text-like (md/txt/csv/json/code/...) and .docx: fetched client-side
 *     and rendered the same way the preview modal does (plain text in a
 *     <pre>, or mammoth.js for .docx) - see GDriveBrowseHandler.PREVIEW_SCRIPT,
 *     which this intentionally mirrors rather than shares outright, since
 *     the surrounding page chrome differs (full tab vs. overlay modal).
 * Anything else falls back to a "no reading view for this file type" page
 * with a download link, matching ViewerHandler's own .viewer-unsupported.
 */
public class GDriveViewerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String id = QueryUtil.getParam(query, "id");
        String name = QueryUtil.getParam(query, "name");
        String mime = QueryUtil.getParam(query, "mime");
        id = id == null ? "" : URLDecoder.decode(id, "UTF-8");
        name = name == null ? "(untitled)" : URLDecoder.decode(name, "UTF-8");
        mime = mime == null ? "" : URLDecoder.decode(mime, "UTF-8");

        String html;
        try {
            html = buildPage(id, name, mime);
        } catch (Exception e) {
            html = errorPage("Couldn't open this file: " + e.getMessage());
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(String id, String name, String mime) {
        boolean isNative = GDriveClient.isNativeGoogleDoc(mime);
        String ext = GridRenderer.getExtension(name).toLowerCase();
        boolean textlike = GDriveBrowseHandler.isTextLike(mime, name);
        boolean isDocx = ext.equals("docx");
        boolean isPdf = ext.equals("pdf");

        String webViewLink = null;
        if (isNative) {
            try {
                GDriveClient.DriveItem meta = GDriveClient.getMetadata(id);
                if (meta != null) webViewLink = meta.webViewLink;
            } catch (IOException ignored) {}
        }

        String downloadUrl = isNative ? null : "/gdrive-file?id=" + PathUtil.urlEncode(id)
              + "&name=" + PathUtil.urlEncode(name) + "&mime=" + PathUtil.urlEncode(mime);
        String viewUrl = isNative ? GDriveBrowseHandler.embeddablePreviewUrl(webViewLink)
              : (downloadUrl + "&mode=view");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>").append(PathUtil.htmlEscape(name)).append("</title>");
        sb.append(ViewerHandler.viewerStyles());
        sb.append(gdriveViewerStyles());
        sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        sb.append(PageScripts.DOCX_RESOURCES);
        sb.append("</head><body class='viewer-body'>");

        sb.append("<div class='viewer-topbar'>");
        sb.append("<span class='viewer-filename'>").append(DriveIcon.img(14)).append(" ").append(PathUtil.htmlEscape(name)).append("</span>");
        sb.append("<div class='viewer-nav'>");
        if (webViewLink != null) {
            sb.append("<a href='").append(PathUtil.htmlEscape(webViewLink)).append("' target='_blank' rel='noopener'>Open in Google Drive</a>");
        }
        if (downloadUrl != null) {
            sb.append("<a href='").append(PathUtil.htmlEscape(downloadUrl)).append("'>Download</a>");
        }
        sb.append("</div></div>");

        sb.append("<div class='viewer-content' id='gdriveViewerContent'>");
        if (isNative) {
            sb.append("<iframe class='viewer-pdf-frame' src='").append(PathUtil.htmlEscape(viewUrl)).append("'></iframe>");
        } else if (isPdf) {
            sb.append("<iframe class='viewer-pdf-frame' src='").append(PathUtil.htmlEscape(viewUrl)).append("'></iframe>");
        } else if (isDocx) {
            sb.append("<div class='viewer-reading docx-loading' id='gdriveDocxLoading'>Loading document...</div>");
        } else if (textlike) {
            sb.append("<div class='viewer-reading docx-loading' id='gdriveTextLoading'>Loading...</div>");
        } else {
            sb.append("<div class='viewer-unsupported'><p>This file type doesn't have a dedicated reading view.</p>");
            if (downloadUrl != null) {
                sb.append("<p><a href='").append(PathUtil.htmlEscape(downloadUrl)).append("'>Download it instead</a></p>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");

        if (isDocx || textlike) {
            sb.append("<script>");
            sb.append("fetch(").append(jsString(viewUrl)).append(")");
            if (isDocx) {
                sb.append(".then(function(r){return r.arrayBuffer();}).then(function(buf){")
                  .append("if(!window.mammoth) throw new Error('renderer unavailable');")
                  .append("return mammoth.convertToHtml({arrayBuffer:buf});")
                  .append("}).then(function(result){")
                  .append("document.getElementById('gdriveViewerContent').innerHTML='<div class=\"viewer-reading docx-preview\">'+result.value+'</div>';")
                  .append("}).catch(function(){")
                  .append("document.getElementById('gdriveViewerContent').innerHTML=")
                  .append("\"<div class='viewer-unsupported'><p>Could not render a preview for this document.</p>")
                  .append(downloadUrl != null ? ("<p><a href=\\'" + downloadUrl + "\\'>Download instead</a></p>") : "")
                  .append("</div>\";});");
            } else {
                sb.append(".then(function(r){return r.text();}).then(function(text){")
                  .append("var pre=document.createElement('pre');")
                  .append("pre.className='viewer-reading plain-text';")
                  .append("pre.textContent=text;")
                  .append("var el=document.getElementById('gdriveViewerContent'); el.innerHTML=''; el.appendChild(pre);")
                  .append("}).catch(function(){")
                  .append("document.getElementById('gdriveViewerContent').innerHTML=\"<div class='viewer-unsupported'><p>Couldn't load this file.</p></div>\";")
                  .append("});");
            }
            sb.append("</script>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String jsString(String s) {
        return "'" + (s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'")) + "'";
    }

    private String gdriveViewerStyles() {
        return "<style>.docx-loading{padding:60px;text-align:center;color:#888;}</style>";
    }

    private String errorPage(String message) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" + ViewerHandler.viewerStyles() +
               "</head><body class='viewer-body'><div class='viewer-unsupported'><p>" +
               PathUtil.htmlEscape(message) + "</p></div></body></html>";
    }
}
