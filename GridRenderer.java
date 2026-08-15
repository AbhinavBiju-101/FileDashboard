import java.io.File;

/**
 * Renders a single folder or file as an HTML grid card. Shared between
 * DashboardHandler (browsing one folder) and SearchHandler (flat results
 * that may come from many different subfolders).
 */
public class GridRenderer {

    public static String folderCard(String childRel, String displayName) {
        String encoded = PathUtil.urlEncode(childRel);
        String name = PathUtil.htmlEscape(displayName);
        return "<a class='card folder' href='/?path=" + encoded + "'>" +
               "<div class='icon'>&#128193;</div><div class='name'>" + name + "</div></a>";
    }

    // showFolderPath: when true, prints the containing folder under the name
    // (used by search results, where files come from many different folders).
    public static String fileCard(File f, String childRel, boolean showFolderPath, String folderLabel) {
        String ext = getExtension(f.getName()).toLowerCase();
        boolean isImage = ext.matches("jpg|jpeg|png|gif|bmp|webp");
        String name = PathUtil.htmlEscape(f.getName());
        String encoded = PathUtil.urlEncode(childRel);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card file'>");
        if (isImage) {
            sb.append("<img class='thumb' src='/thumbnail?path=").append(encoded)
              .append("' loading='lazy' alt=''>");
        } else {
            sb.append("<div class='icon'>").append(iconFor(ext)).append("</div>");
        }
        sb.append("<div class='name' title='").append(name).append("'>").append(name).append("</div>");
        if (showFolderPath) {
            sb.append("<div class='meta path'>").append(PathUtil.htmlEscape(folderLabel)).append("</div>");
        }
        sb.append("<div class='meta'>").append(humanSize(f.length())).append("</div>");
        sb.append("<div class='actions'>");
        sb.append("<a href='/file?path=").append(encoded).append("&mode=view' target='_blank'>View</a>");
        sb.append("<a href='/file?path=").append(encoded).append("&mode=download'>Download</a>");
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
