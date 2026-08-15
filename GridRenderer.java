import java.io.File;

/**
 * Renders a single folder or file as an HTML grid card. Shared between
 * HomeHandler, BrowseHandler, and SearchHandler.
 *
 * "View" doesn't navigate away - it triggers the in-app preview modal
 * (see PageScripts.java) via a data-action attribute. The href is still a
 * real, correct URL as a fallback (e.g. right-click "open in new tab").
 */
public class GridRenderer {

    public static String folderCard(String childRel, String displayName) {
        String encoded = PathUtil.urlEncode(childRel);
        String name = PathUtil.htmlEscape(displayName);
        String dataPath = PathUtil.htmlEscape(childRel);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card folder\">");
        sb.append("<a class=\"card-link\" href=\"/browse?path=").append(encoded).append("\">");
        sb.append("<div class=\"icon\">&#128193;</div><div class=\"name\">").append(name).append("</div>");
        sb.append("</a>");
        sb.append("<div class=\"actions\">");
        sb.append("<a href=\"#\" data-action=\"rename\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name).append("\">Rename</a>");
        sb.append("<a href=\"#\" data-action=\"duplicate\" data-path=\"").append(dataPath).append("\">Duplicate</a>");
        sb.append("<a href=\"#\" data-action=\"delete\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name).append("\">Delete</a>");
        sb.append("</div></div>");
        return sb.toString();
    }

    // showFolderPath: when true, prints the containing folder under the name
    // (used by search results, where files come from many different folders).
    public static String fileCard(File f, String childRel, boolean showFolderPath, String folderLabel) {
        String ext = getExtension(f.getName()).toLowerCase();
        boolean isImage = ext.matches("jpg|jpeg|png|gif|bmp|webp");
        String name = PathUtil.htmlEscape(f.getName());
        String encoded = PathUtil.urlEncode(childRel);
        String dataPath = PathUtil.htmlEscape(childRel);
        boolean viewable = ViewabilityUtil.isViewable(ext);
        String hrefMode = viewable ? "view" : "preview";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file\">");
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

        sb.append("<div class=\"actions\">");
        sb.append("<a href=\"/file?path=").append(encoded).append("&mode=").append(hrefMode).append("\" target=\"_blank\" ")
          .append("data-action=\"view\" data-path=\"").append(dataPath).append("\" data-name=\"").append(name)
          .append("\" data-ext=\"").append(ext).append("\" data-viewable=\"").append(viewable ? "1" : "0").append("\">View</a>");
        sb.append("<a href=\"/file?path=").append(encoded).append("&mode=download\">Download</a>");
        sb.append("<a href=\"#\" data-action=\"rename\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name).append("\">Rename</a>");
        sb.append("<a href=\"#\" data-action=\"duplicate\" data-path=\"").append(dataPath).append("\">Duplicate</a>");
        sb.append("<a href=\"#\" data-action=\"delete\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name).append("\">Delete</a>");
        sb.append("</div></div>");
        return sb.toString();
    }

    public static String iconFor(String ext) {
        switch (ext) {
            case "pdf": return "&#128196;";
            case "doc": case "docx": return "&#128221;";
            case "xls": case "xlsx": case "csv": return "&#128202;";
            case "zip": case "rar": case "7z": return "&#128230;";
            case "mp3": case "wav": return "&#127925;";
            case "mp4": case "mov": case "avi": return "&#127916;";
            case "txt": case "md": return "&#128195;";
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
