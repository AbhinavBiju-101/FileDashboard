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
    public static List<DriveItem> listChildren(String accountId, String folderId) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
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
    public static DriveItem getMetadata(String accountId, String fileId) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
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
    public static void streamFile(String accountId, String fileId, OutputStream dest) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
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
    public static List<DriveItem> search(String accountId, String nameQuery) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
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

    // Extension -> the exact MIME type browsers actually expect for
    // <video>/<audio>/<img> playback - used by GDriveDownloadHandler.java
    // to override whatever Drive's own metadata reports for the file
    // whenever the extension is one we recognize. Google Drive's reported
    // mimeType for a given upload isn't always the canonical one a browser
    // wants (uploads via different clients/OSes can land with a generic
    // "application/octet-stream", a slightly different variant, or
    // inconsistent casing) - a <video> element is far stricter about an
    // exact Content-Type match than, say, a plain file download is, so a
    // mismatch here is enough to silently fail playback for exactly one
    // file type while a neighboring one (whose upload happened to get
    // tagged correctly) works fine - which is exactly the kind of
    // per-file, hard-to-reproduce inconsistency this maps around rather
    // than trying to diagnose one specific account's Drive metadata.
    private static final java.util.Map<String, String> EXTENSION_MIME_OVERRIDES = new java.util.HashMap<>();
    static {
        EXTENSION_MIME_OVERRIDES.put("mp4", "video/mp4");
        EXTENSION_MIME_OVERRIDES.put("m4v", "video/mp4");
        EXTENSION_MIME_OVERRIDES.put("mov", "video/quicktime");
        EXTENSION_MIME_OVERRIDES.put("webm", "video/webm");
        EXTENSION_MIME_OVERRIDES.put("mkv", "video/x-matroska");
        EXTENSION_MIME_OVERRIDES.put("avi", "video/x-msvideo");
        EXTENSION_MIME_OVERRIDES.put("mp3", "audio/mpeg");
        EXTENSION_MIME_OVERRIDES.put("wav", "audio/wav");
        EXTENSION_MIME_OVERRIDES.put("ogg", "audio/ogg");
        EXTENSION_MIME_OVERRIDES.put("m4a", "audio/mp4");
        EXTENSION_MIME_OVERRIDES.put("flac", "audio/flac");
        EXTENSION_MIME_OVERRIDES.put("aac", "audio/aac");
        EXTENSION_MIME_OVERRIDES.put("jpg", "image/jpeg");
        EXTENSION_MIME_OVERRIDES.put("jpeg", "image/jpeg");
        EXTENSION_MIME_OVERRIDES.put("png", "image/png");
        EXTENSION_MIME_OVERRIDES.put("gif", "image/gif");
        EXTENSION_MIME_OVERRIDES.put("webp", "image/webp");
        EXTENSION_MIME_OVERRIDES.put("svg", "image/svg+xml");
        EXTENSION_MIME_OVERRIDES.put("pdf", "application/pdf");
    }

    // Note this is deliberately NOT limited to only the "generic/missing"
    // case - it always prefers the extension-based type when the extension
    // is one we recognize, even over a Drive-reported mimeType that looks
    // superficially plausible (e.g. "video/mpeg" instead of "video/mp4"),
    // since browsers can be picky about the exact subtype for playback and
    // the extension is the more reliable signal of what the file actually
    // is.
    public static String bestMimeForName(String reportedMime, String name) {
        String ext = GridRenderer.getExtension(name == null ? "" : name).toLowerCase();
        String override = EXTENSION_MIME_OVERRIDES.get(ext);
        if (override != null) return override;
        return (reportedMime == null || reportedMime.isEmpty()) ? "application/octet-stream" : reportedMime;
    }

    // Creates a new, empty folder directly under parentId (typically
    // "root") and returns its new Drive-assigned id.
    public static String createFolder(String accountId, String parentId, String name) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
        String body = "{\"name\":\"" + jsonEscape(name) + "\",\"mimeType\":\"" + FOLDER_MIME + "\",\"parents\":[\"" + jsonEscape(parentId) + "\"]}";
        Map<String, Object> resp = GDriveAuth.postJson("https://www.googleapis.com/drive/v3/files?fields=" + urlEncode("id"), token, body);
        String id = str(resp.get("id"));
        if (id == null) throw new IOException("Google didn't return a new folder id.");
        return id;
    }

    // Renames a file or folder in place - a plain files.update PATCH with
    // just the "name" field, no reparenting involved.
    public static void renameItem(String accountId, String fileId, String newName) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
        String body = "{\"name\":\"" + jsonEscape(newName) + "\"}";
        GDriveAuth.patchJson("https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId), token, body);
    }

    // Moves a file/folder to Drive's own trash (trashed:true) - the direct
    // equivalent of TrashManager.moveToTrash() for local files, except
    // Drive tracks its own trash server-side rather than this app managing
    // a trash folder itself, so there's no local TrashManager.Entry/undo-id
    // to hand back; "restore from Drive trash" isn't wired into this app's
    // UI (Drive's own web trash already covers that, one click away via
    // "Open in Google Drive").
    public static void trashItem(String accountId, String fileId) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
        GDriveAuth.patchJson("https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId), token, "{\"trashed\":true}");
    }

    // Reparents a file/folder - Drive's files.update takes the new parent
    // to add and the old one to remove as query params rather than a body
    // field (a file can technically have multiple parents in Drive's model,
    // though this app never creates one that way, so removing exactly the
    // one parent it was browsed under is always correct here).
    public static void moveItem(String accountId, String fileId, String oldParentId, String newParentId) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
        String url = "https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId)
              + "?addParents=" + urlEncode(newParentId) + "&removeParents=" + urlEncode(oldParentId);
        GDriveAuth.patchJson(url, token, "{}");
    }

    // Drive's own copy endpoint - handles both files and folders, though
    // folder copies are shallow (Drive doesn't recursively copy children by
    // default), which is fine here since GDriveOpsHandler.java only offers
    // "Duplicate" for files, matching local's own Duplicate (which does
    // recurse for local folders - a gap worth knowing about rather than
    // silently mismatched, so the menu simply doesn't offer it for Drive
    // folders at all rather than deep-copying only sometimes).
    public static String copyItem(String accountId, String fileId, String newName) throws IOException {
        String token = GDriveAuth.getValidAccessToken(accountId);
        String body = "{\"name\":\"" + jsonEscape(newName) + "\"}";
        Map<String, Object> resp = GDriveAuth.postJson("https://www.googleapis.com/drive/v3/files/" + urlEncode(fileId) + "/copy?fields=" + urlEncode("id"), token, body);
        String id = str(resp.get("id"));
        if (id == null) throw new IOException("Google didn't return the copy's id.");
        return id;
    }

    private static String jsonEscape(String s) {
        return MiniJson.escape(s);
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
