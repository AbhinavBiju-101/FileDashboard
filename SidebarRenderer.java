import java.io.File;

/**
 * Renders the pinned, collapsible left sidebar used by the app shell: a link
 * back to the Dashboard, the Session Manager, a link to Home, and shortcuts
 * below it - which set of shortcuts depends on whether the browser tab's
 * current session is the specialized Google Drive session (see
 * ShellScript.java's GDRIVE_SESSION_ID / shellApplyDriveSidebar()):
 *   - Normally: whichever classic OS folders (Desktop, Downloads,
 *     Documents, ...) actually exist under Config.ROOT_DIR, and Home/Recycle
 *     Bin point at the local filesystem.
 *   - In the Google Drive session: Home points at Drive's "My Drive" root,
 *     Recycle Bin is hidden (Drive has its own trash, not wired up here
 *     yet), and the classic folders are replaced with just two shortcuts -
 *     "Home folders" and "Home files" - splitting whatever sits directly in
 *     My Drive into two lighter views, since unlike a local Desktop/
 *     Documents/etc a Drive root can easily hold thousands of loose files
 *     alongside folders. See GDriveBrowseHandler.java's "only" param.
 *
 * Both sets of shortcuts are always in the HTML (so no page reload is
 * needed to switch sessions); only one set is visible at a time, controlled
 * by the sidebar's own "drive-mode" CSS class (see Styles.java).
 *
 * Every link navigates the currently active tab in place (like clicking a
 * normal link would) via the shell's navigateCurrentTab() function - it does
 * NOT open a new tab each time. The "+" button in the tab bar is the only
 * thing that creates a genuinely new tab. The sidebar itself lives only in
 * the shell page, not inside any individual tab's iframe.
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

        sb.append(item("/dashboard", "&#127968;", "Dashboard", null));
        sb.append(item("/sessions", "&#128337;", "Sessions", null));

        // Home + Recycle Bin: local-mode only.
        sb.append(item("/browse?path=", "&#127760;", "Home", "sidebar-mode-local"));
        sb.append(item("/trash", "&#128465;", "Recycle Bin", "sidebar-mode-local"));

        // Home + the folders/files split: drive-mode only.
        sb.append(item("/gdrive?path=", "&#127760;", "Home", "sidebar-mode-drive"));
        sb.append(item("/gdrive?path=&only=folders", "&#128193;", "Home folders", "sidebar-mode-drive"));
        sb.append(item("/gdrive?path=&only=files", "&#128196;", "Home files", "sidebar-mode-drive"));

        sb.append("<div class='sidebar-divider'></div>");

        for (String[] folder : CLASSIC_FOLDERS) {
            File f = new File(Settings.rootDir(), folder[0]);
            if (f.isDirectory()) {
                sb.append(item("/browse?path=" + PathUtil.urlEncode(folder[0]), folder[1], folder[0], "sidebar-mode-local"));
            }
        }

        sb.append("<div class='sidebar-divider'></div>");
        sb.append(item("/settings", "&#9881;", "Settings", null));

        sb.append("</div></div>");
        sb.append(SCRIPT);
        return sb.toString();
    }

    private static String item(String href, String icon, String label, String extraClass) {
        String jsHref = href.replace("\\", "\\\\").replace("'", "\\'");
        String cls = "sidebar-item" + (extraClass != null ? " " + extraClass : "");
        return "<a class='" + cls + "' href='" + href + "' " +
               "onclick=\"return navigateCurrentTab('" + jsHref + "');\">" +
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
