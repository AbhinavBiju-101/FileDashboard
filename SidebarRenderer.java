import java.io.File;

/**
 * Renders the pinned, collapsible left sidebar used by the app shell: a link
 * back to the Dashboard, the Session Manager, a link to Home, and shortcuts
 * below it - which set of shortcuts depends on whether the browser tab's
 * current session is a Google Drive session (see ShellScript.java's
 * shellSessionIsDrive / shellApplyDriveSidebar()):
 *   - Normally: whichever classic OS folders (Desktop, Downloads,
 *     Documents, ...) actually exist under Config.ROOT_DIR, and Home/Recycle
 *     Bin point at the local filesystem.
 *   - In a Google Drive session: Home points at Drive's "My Drive" root,
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

    // 20px to match the other sidebar icons' effective size (see .sidebar-icon's font-size:17px).
    private static final String DRIVE_ICON_IMG = DriveIcon.img(18);

    public static String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id='sidebar' class='sidebar'>");
        sb.append("<button class='sidebar-toggle' onclick='toggleSidebar()' title='Collapse/expand'>&#9776;</button>");
        sb.append("<div class='sidebar-inner'>");

        sb.append(item("/dashboard", "&#127968;", "Dashboard", null));
        sb.append(item("/sessions", "&#128337;", "Sessions", null));

        // Home + Recycle Bin: local-mode only. Home itself (drive-mode
        // variant included) stays up here with Dashboard/Sessions since
        // it's a top-level destination either way - only the shortcuts
        // *below* the first divider differ by mode, same slot classic
        // Desktop/Downloads/etc shortcuts occupy locally.
        sb.append(item("/browse?path=", "&#127760;", "Home", "sidebar-mode-local"));
        sb.append(item("/trash", "&#128465;", "Recycle Bin", "sidebar-mode-local"));
        sb.append(item("/gdrive?path=", DRIVE_ICON_IMG, "Home", "sidebar-mode-drive"));

        sb.append("<div class='sidebar-divider'></div>");

        for (String[] folder : CLASSIC_FOLDERS) {
            File f = new File(Settings.rootDir(), folder[0]);
            if (f.isDirectory()) {
                sb.append(item("/browse?path=" + PathUtil.urlEncode(folder[0]), folder[1], folder[0], "sidebar-mode-local"));
            }
        }
        sb.append(item("/gdrive?path=&only=folders", "&#128193;", "Home folders", "sidebar-mode-drive"));
        sb.append(item("/gdrive?path=&only=files", "&#128196;", "Home files", "sidebar-mode-drive"));

        sb.append("<div class='sidebar-divider'></div>");
        sb.append(item("/settings", "&#9881;", "Settings", null));

        sb.append("</div>"); // closes .sidebar-inner

        // Pinned to the bottom of the sidebar (not inside the scrolling
        // .sidebar-inner above), so it's always reachable regardless of
        // scroll position or how many folder shortcuts are showing. Its
        // logic lives in ShellScript.java alongside the rest of the
        // session machinery (shellToggleSessionMenu() etc.) since it needs
        // direct access to shellTabs/shellSessionId/shellLoadSession -
        // this file only renders the markup and wires up onclick handlers
        // by name, the same way the "Unsaved session" badge in
        // AppShellHandler.java does.
        sb.append("<div id='sidebarSessionSwitcher' class='sidebar-session-switcher' onclick='shellToggleSessionMenu(event)' title='Switch session'>");
        sb.append("<span id='sidebarSessionDot' class='sidebar-session-dot'></span>");
        sb.append("<span class='sidebar-label sidebar-session-name' id='sidebarSessionName'>Session</span>");
        sb.append("<span class='sidebar-label sidebar-session-chevron'>&#9662;</span>");
        sb.append("</div>");
        sb.append("<div id='sidebarSessionMenu' class='sidebar-session-menu'></div>");

        sb.append("</div>"); // closes #sidebar
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
