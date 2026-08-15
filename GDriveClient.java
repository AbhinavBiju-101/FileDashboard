import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the handful of Drive API v3 calls this integration
 * needs: list a folder's children, and stream a file's bytes back through
 * this server (GDriveDownloadHandler.java). Every call goes through
 * GDriveAuth.getValidAccessToken(), which handles refreshing an expired
 * access token transparently - callers here never see or manage tokens
 * directly.
 *
 * Unverified against real Google endpoints - see GDriveAuth.java's class
 * comment.
 */
public class GDriveClient {

    public static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String NATIVE_DOC_PREFIX = "application/vnd.google-apps.";

    public static boolean isFolder(String mimeType) {
        return FOLDER_MIME.equals(mimeType);
    }

    // Native Google Docs/Sheets/Slides/etc. have no raw bytes to download -
    // they'd need the separate /export endpoint (with a target mime type
    // chosen up front), which this first, read-mostly pass doesn't
    // implement. They're still browsable and "Open in Drive" always works
    // for them; only the Download link is hidden.
    public static boolean isNativeGoogleDoc(String mimeType) {
        return mimeType != null && mimeType.startsWith(NATIVE_DOC_PREFIX) && !isFolder(mimeType);
    }

    public static class DriveItem {
        public String id;
        public String name;
        public String mimeType;
        public long size; // 0 for folders and native Google Docs (Drive doesn't report a byte size for either)
        public String webViewLink;
        public String thumbnailLink;
    }

    // Lists the direct children of folderId, folders first then files, each
    // alphabetically - same ordering convention BrowseHandler.java uses for
    // local folders. Capped at 200 items in this pass (see class comment);
    // larger folders just show their first 200 rather than paginating.
    @SuppressWarnings("unchecked")
    public static List<DriveItem> listChildren(String folderId) throws IOException {
        String token = GDriveAuth.getValidAccessToken();
        String q = "'" + folderId.replace("'", "\\'") + "' in parents and trashed = false";
        String url = "https://www.googleapis.com/drive/v3/files"
            + "?q=" + urlEncode(q)
            + "&fields=" + urlEncode("files(id,name,mimeType,size,webViewLink,thumbnailLink)")
            + "&orderBy=" + urlEncode("folder,name")
            + "&pageSize=200"
            + "&spaces=drive";
        Map<String, Object> resp = GDriveAuth.getJson(url, token);
        List<DriveItem> out = new ArrayList<>();
        Object filesObj = resp.get("files");
        if (filesObj instanceof List) {
            for (Object o : (List<Object>) filesObj) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                DriveItem item = new DriveItem();
                item.id = str(m.get("id"));
                item.name = str(m.get("name"));
                item.mimeType = str(m.get("mimeType"));
                item.webViewLink = str(m.get("webViewLink"));
                item.thumbnailLink = str(m.get("thumbnailLink"));
                String sizeStr = str(m.get("size"));
                try { item.size = sizeStr == null ? 0 : Long.parseLong(sizeStr); } catch (NumberFormatException ignored) {}
                out.add(item);
            }
        }
        return out;
    }

    // Metadata for a single item, used to build the breadcrumb trail (each
    // path segment carries the folder's own name so /gdrive doesn't need an
    // extra round trip per breadcrumb level - see GDriveBrowseHandler.java)
    // and to double-check a file's mime type right before proxying its
    // bytes in GDriveDownloadHandler.java, rather than trusting whatever
    // mime/name the query string claims.
    @SuppressWarnings("unchecked")
    public static DriveItem getMetadata(String fileId) throws IOException {
        String token = GDriveAuth.getValidAccessToken();
        String url = "https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId)
            + "?fields=" + urlEncode("id,name,mimeType,size,webViewLink,thumbnailLink");
        Map<String, Object> m = GDriveAuth.getJson(url, token);
        DriveItem item = new DriveItem();
        item.id = str(m.get("id"));
        item.name = str(m.get("name"));
        item.mimeType = str(m.get("mimeType"));
        item.webViewLink = str(m.get("webViewLink"));
        item.thumbnailLink = str(m.get("thumbnailLink"));
        String sizeStr = str(m.get("size"));
        try { item.size = sizeStr == null ? 0 : Long.parseLong(sizeStr); } catch (NumberFormatException ignored) {}
        return item;
    }

    // Streams a file's raw bytes straight through to the given
    // OutputStream - deliberately not buffering the whole file in memory,
    // since Drive files can be large. Caller is responsible for having
    // already sent response headers (status/content-type/length) before
    // calling this.
    public static void streamFile(String fileId, OutputStream dest) throws IOException {
        String token = GDriveAuth.getValidAccessToken();
        String url = "https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId) + "?alt=media";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Google Drive returned HTTP " + status + " while downloading this file.");
        }
        try (InputStream is = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) dest.write(buf, 0, n);
        }
    }

    // Name-contains search across the whole connected Drive (not scoped to
    // any particular folder). Unlike local search, this doesn't walk
    // descendants of a starting folder - Drive API's "'X' in parents"
    // scoping only covers direct children, not a whole subtree, and
    // building real folder-scoped search would mean first walking the
    // entire folder tree to collect every descendant id. Searching the
    // whole Drive by name is what Google's own Drive search box does too,
    // so it's a reasonable read on "search" for this data model rather
    // than a compromise - see GDriveSearchHandler.java.
    @SuppressWarnings("unchecked")
    public static List<DriveItem> search(String nameQuery) throws IOException {
        String token = GDriveAuth.getValidAccessToken();
        String escaped = nameQuery.replace("\\", "\\\\").replace("'", "\\'");
        String q = "name contains '" + escaped + "' and trashed = false";
        String url = "https://www.googleapis.com/drive/v3/files"
            + "?q=" + urlEncode(q)
            + "&fields=" + urlEncode("files(id,name,mimeType,size,webViewLink,thumbnailLink)")
            + "&orderBy=" + urlEncode("folder,name")
            + "&pageSize=50"
            + "&spaces=drive";
        Map<String, Object> resp = GDriveAuth.getJson(url, token);
        List<DriveItem> out = new ArrayList<>();
        Object filesObj = resp.get("files");
        if (filesObj instanceof List) {
            for (Object o : (List<Object>) filesObj) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                DriveItem item = new DriveItem();
                item.id = str(m.get("id"));
                item.name = str(m.get("name"));
                item.mimeType = str(m.get("mimeType"));
                item.webViewLink = str(m.get("webViewLink"));
                item.thumbnailLink = str(m.get("thumbnailLink"));
                String sizeStr = str(m.get("size"));
                try { item.size = sizeStr == null ? 0 : Long.parseLong(sizeStr); } catch (NumberFormatException ignored) {}
                out.add(item);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
