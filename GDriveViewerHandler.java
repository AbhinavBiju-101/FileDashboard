import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
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
 *   - Text-like (md/txt/csv/json/code/...): fetched server-side (same
 *     GDriveClient.streamFile() call GDriveDownloadHandler uses) and
 *     rendered exactly the way ViewerHandler.java renders local files -
 *     MarkdownLite.render() for .md, hljs syntax highlighting for
 *     recognized code extensions, plain <pre> otherwise - rather than a
 *     lookalike that only ever produced a plain-text dump regardless of
 *     file type. .docx is the one exception: still fetched and rendered
 *     client-side via mammoth.js (see GDriveBrowseHandler.PREVIEW_SCRIPT),
 *     since there's no Java-side equivalent to convert it with here.
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
        boolean isMarkdown = textlike && ext.equals("md");
        boolean isCode = textlike && !isMarkdown && CodeLanguageUtil.shouldHighlight(ext);

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

        // Fetched right here, server-side, the same way GDriveDownloadHandler
        // does for a plain download - not left to a client-side fetch() the
        // way the old version of this page did - specifically so it can be
        // handed to MarkdownLite.render()/escaped into a hljs-ready <pre>
        // exactly like ViewerHandler.java does for local files, rather than
        // always dumping raw, unrendered, unhighlighted text into a <pre>
        // regardless of extension.
        String textContent = null;
        boolean textLoadFailed = false;
        if (textlike) {
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                GDriveClient.streamFile(id, buf);
                textContent = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                textLoadFailed = true;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>").append(PathUtil.htmlEscape(name)).append("</title>");
        sb.append(ViewerHandler.viewerStyles());
        sb.append(gdriveViewerStyles());
        if (!isMarkdown) {
            sb.append(PageScripts.CODE_HIGHLIGHT_RESOURCES);
        }
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
        if (isCode && textContent != null) {
            sb.append("<a href=\"#\" onclick=\"toggleGDriveCodeView(); return false;\" id='toggleRawBtn'>Show raw text</a>");
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
            if (textContent == null) {
                sb.append("<div class='viewer-unsupported'><p>Couldn't load this file from Google Drive").append(textLoadFailed ? "." : " - it may be empty.").append("</p>");
                if (downloadUrl != null) {
                    sb.append("<p><a href='").append(PathUtil.htmlEscape(downloadUrl)).append("'>Download it instead</a></p>");
                }
                sb.append("</div>");
            } else if (isMarkdown) {
                sb.append("<div class='viewer-reading markdown-body'>").append(MarkdownLite.render(textContent)).append("</div>");
            } else if (isCode) {
                String lang = CodeLanguageUtil.hljsLanguage(ext);
                String langClass = lang.isEmpty() ? "" : " class=\"language-" + lang + "\"";
                String escaped = PathUtil.htmlEscape(textContent);
                sb.append("<div class='viewer-reading code-viewer'>");
                sb.append("<pre class='code-highlighted'><code id='gdriveCodeBlock'").append(langClass).append(">").append(escaped).append("</code></pre>");
                sb.append("<pre class='code-raw plain-text' style='display:none;'>").append(escaped).append("</pre>");
                sb.append("</div>");
            } else {
                sb.append("<pre class='viewer-reading plain-text'>").append(PathUtil.htmlEscape(textContent)).append("</pre>");
            }
        } else {
            sb.append("<div class='viewer-unsupported'><p>This file type doesn't have a dedicated reading view.</p>");
            if (downloadUrl != null) {
                sb.append("<p><a href='").append(PathUtil.htmlEscape(downloadUrl)).append("'>Download it instead</a></p>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");

        if (isDocx) {
            sb.append("<script>");
            sb.append("fetch(").append(jsString(viewUrl)).append(")");
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
            sb.append("</script>");
        }
        if (isCode && textContent != null) {
            // Same hljs.highlightElement() + raw/formatted toggle
            // ViewerHandler.viewerScript() provides for local files -
            // duplicated in miniature here rather than shared, since that
            // method is private to ViewerHandler and bundled together with
            // local-only edit-mode logic (Ctrl+S save, textarea toggling)
            // that doesn't apply to this read-only Drive view.
            sb.append("<script>");
            sb.append("if(window.hljs){ var cb=document.getElementById('gdriveCodeBlock'); if(cb) hljs.highlightElement(cb); }");
            sb.append("function toggleGDriveCodeView(){");
            sb.append("var h=document.querySelector('.code-highlighted'), r=document.querySelector('.code-raw'), b=document.getElementById('toggleRawBtn');");
            sb.append("if(!h||!r) return;");
            sb.append("var showingRaw=r.style.display!=='none';");
            sb.append("if(showingRaw){ r.style.display='none'; h.style.display=''; b.textContent='Show raw text'; }");
            sb.append("else{ r.style.display=''; h.style.display='none'; b.textContent='Show formatted'; }");
            sb.append("}");
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
