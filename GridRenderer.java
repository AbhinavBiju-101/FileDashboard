import java.io.File;

/**
 * Renders a single folder or file as an HTML grid card. Shared between
 * HomeHandler, BrowseHandler, SearchHandler, and TrashHandler.
 *
 * Cards are deliberately minimal now - just an icon/thumbnail and a name.
 * Every action (view, download, rename, duplicate, move, delete) lives in
 * the right-click context menu instead of a row of always-visible links -
 * see PageScripts.java for the selection model and menu logic. Single click
 * selects a card (or extends a selection with Ctrl/Shift); double-click
 * opens it; right-click shows the menu.
 */
public class GridRenderer {

    public static String folderCard(String childRel, String displayName) {
        String name = PathUtil.htmlEscape(displayName);
        String dataPath = PathUtil.htmlEscape(childRel);

        return "<div class=\"card folder\" data-path=\"" + dataPath + "\" data-name=\"" + name +
               "\" data-type=\"folder\">" +
               "<div class=\"icon\">&#128193;</div>" +
               "<div class=\"name\" title=\"" + name + "\">" + name + "</div>" +
               "</div>";
    }

    // showFolderPath: when true, prints the containing folder under the name
    // (used by search results and the recycle bin, where items come from
    // many different folders).
    public static String fileCard(File f, String childRel, boolean showFolderPath, String folderLabel) {
        String ext = getExtension(f.getName()).toLowerCase();
        boolean isImage = ext.matches("jpg|jpeg|png|gif|bmp|webp");
        String name = PathUtil.htmlEscape(f.getName());
        String encoded = PathUtil.urlEncode(childRel);
        String dataPath = PathUtil.htmlEscape(childRel);
        boolean viewable = ViewabilityUtil.isViewable(f, ext);
        boolean textlike = ViewabilityUtil.isTextLike(f, ext);
        String category = categoryFor(ext);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name)
          .append("\" data-type=\"file\" data-ext=\"").append(ext)
          .append("\" data-viewable=\"").append(viewable ? "1" : "0")
          .append("\" data-textlike=\"").append(textlike ? "1" : "0")
          .append("\" data-category=\"").append(category).append("\">");

        if (isImage) {
            sb.append("<img class=\"thumb\" src=\"/thumbnail?path=").append(encoded)
              .append("\" loading=\"lazy\" alt=\"\">");
        } else {
            sb.append("<div class=\"icon\">").append(iconFor(ext)).append("</div>");
        }
        sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
        if (showFolderPath) {
            sb.append("<div class=\"meta path\">").append(PathUtil.htmlEscape(folderLabel)).append("</div>");
        }
        sb.append("<div class=\"meta\">").append(humanSize(f.length())).append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    // Coarse grouping used by the type-filter chips in the toolbar.
    public static String categoryFor(String ext) {
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp": case "svg": case "ico":
                return "image";
            case "pdf":
                return "pdf";
            case "doc": case "docx": case "txt": case "md": case "rtf":
                return "document";
            case "xls": case "xlsx": case "csv":
                return "spreadsheet";
            case "ppt": case "pptx":
                return "presentation";
            case "mp4": case "mov": case "avi": case "webm": case "mkv": case "m4v":
                return "video";
            case "mp3": case "wav": case "ogg": case "m4a": case "flac": case "aac":
                return "audio";
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return "archive";
            default:
                return "other";
        }
    }

    public static String iconFor(String ext) {
        switch (categoryFor(ext)) {
            case "pdf": return "&#128196;";
            case "document": return "&#128221;";
            case "spreadsheet": return "&#128202;";
            case "presentation": return "&#128200;";
            case "archive": return "&#128230;";
            case "audio": return "&#127925;";
            case "video": return "&#127916;";
            default: return "&#128196;";
        }
    }

    public static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, 6);
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
