import java.io.File;

/**
 * Renders the pinned, collapsible left sidebar used by the app shell: a link
 * back to the Dashboard, a link to the Home folder, and shortcuts to
 * whichever classic OS folders (Desktop, Downloads, Documents, ...) actually
 * exist under Config.ROOT_DIR. Collapsed/expanded state is remembered per
 * browser via localStorage.
 *
 * Every link opens (or focuses) a tab via the shell's openTab() function
 * (see ShellScript.java) rather than navigating away - the sidebar itself
 * lives only in the shell page, not inside any individual tab's iframe.
 */
public class SidebarRenderer {

    // name -> emoji icon. Only shown if the folder actually exists.
    private static final String[][] CLASSIC_FOLDERS = {
        {"Desktop", "&#128421;"},
        {"Downloads", "&#11015;"},
        {"Documents", "&#128196;"},
        {"Pictures", "&#128444;"},
        {"Music", "&#127925;"},
        {"Videos", "&#127916;"},
        {"Public", "&#127760;"}
    };

    public static String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id='sidebar' class='sidebar'>");
        sb.append("<button class='sidebar-toggle' onclick='toggleSidebar()' title='Collapse/expand'>&#9776;</button>");
        sb.append("<div class='sidebar-inner'>");

        sb.append(item("/dashboard", "&#127968;", "Dashboard"));
        sb.append(item("/browse?path=", "&#127760;", "Home"));
        sb.append("<div class='sidebar-divider'></div>");

        for (String[] folder : CLASSIC_FOLDERS) {
            File f = new File(Config.ROOT_DIR, folder[0]);
            if (f.isDirectory()) {
                sb.append(item("/browse?path=" + PathUtil.urlEncode(folder[0]), folder[1], folder[0]));
            }
        }

        sb.append("</div></div>");
        sb.append(SCRIPT);
        return sb.toString();
    }

    private static String item(String href, String icon, String label) {
        String jsHref = href.replace("\\", "\\\\").replace("'", "\\'");
        String jsLabel = label.replace("\\", "\\\\").replace("'", "\\'");
        return "<a class='sidebar-item' href='" + href + "' " +
               "onclick=\"return openTab('" + jsHref + "', '" + jsLabel + "');\">" +
               "<span class='sidebar-icon'>" + icon + "</span>" +
               "<span class='sidebar-label'>" + label + "</span></a>";
    }

    private static final String SCRIPT =
        "<script>" +
        "(function(){ if(localStorage.getItem('sidebarCollapsed')==='1'){" +
        "document.getElementById('sidebar').classList.add('collapsed'); } })();" +
        "function toggleSidebar(){" +
        "var el=document.getElementById('sidebar');" +
        "el.classList.toggle('collapsed');" +
        "localStorage.setItem('sidebarCollapsed', el.classList.contains('collapsed')?'1':'0');" +
        "}" +
        "</script>";
}
