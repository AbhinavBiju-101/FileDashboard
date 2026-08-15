import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/sessions" - the Session Manager page. Unlike every other page in
 * this app, this one has no server-side data to render at all: a "session"
 * (see ShellScript.java) is a browser tab's own set of open tabs/groups, and
 * which browser tabs exist is something only the browser knows - the server
 * never sees it. So this handler just serves the page shell; SCRIPT below
 * reads localStorage/sessionStorage directly (the same storage ShellScript.java
 * writes to) and renders the list client-side, re-polling every few seconds
 * so "open elsewhere" badges stay accurate as other browser tabs come and go.
 *
 * Renaming and deleting a session just edit the shared localStorage map
 * directly - safe from any frame, since ShellScript.java's own save always
 * merges rather than overwrites. Reopening one, though, has to go through
 * window.parent.shellLoadSession(id): swapping which session THIS browser
 * tab is hosting means tearing down and rebuilding the live tab bar/iframes,
 * and that state only exists in the parent shell frame's memory.
 */
public class SessionsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = buildPage();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Sessions</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>Sessions</div>");
        sb.append("</div>");

        sb.append("<p class='session-intro'>Every new browser tab you open on File Dashboard starts its own session - ")
          .append("its own set of tabs, kept separate from whatever else you have open. A session can only be open in ")
          .append("one browser tab at a time, so reopening one below moves it here (or, if it's open somewhere else, ")
          .append("\"Close &amp; open here\" closes it there first). The pinned \u2601 Google Drive session browses a connected ")
          .append("Drive account - connect one from Settings.</p>");

        sb.append("<div id='sessionList' class='session-list'><p class='empty'>Loading...</p></div>");

        sb.append(SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static final String SCRIPT =
        "<script>" +
        "var SESSIONS_KEY='fileDashboardSessions';" +
        "var HEARTBEATS_KEY='fileDashboardSessionHeartbeats';" +
        "var SESSION_ID_KEY='fd-session-id';" +
        // Matches ShellScript.java's own HEARTBEAT_STALE_MS - a session
        // counts as "open elsewhere" if its last heartbeat is more recent
        // than this, otherwise it's treated as safely reopenable even if
        // its owning tab never got a clean shutdown (crash, force-quit).
        "var HEARTBEAT_STALE_MS=10000;" +
        // A fixed, well-known id (rather than one shellGenerateSessionId()
        // would produce) so this row can always be found/recognized, even
        // before it's ever actually been opened once - see the synthesized
        // placeholder in renderSessions() below.
        "var GDRIVE_SESSION_ID='session-gdrive';" +

        "function fdFormatDate(ts){" +
          "var d=new Date(ts);" +
          "return d.toLocaleDateString([], {month:'short', day:'numeric'}) + ' ' + d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});" +
        "}" +
        "function loadSessionsMap(){" +
          "try{ var raw=localStorage.getItem(SESSIONS_KEY); return raw?JSON.parse(raw):{}; }catch(e){ return {}; }" +
        "}" +
        "function saveSessionsMap(map){" +
          "try{ localStorage.setItem(SESSIONS_KEY, JSON.stringify(map)); }catch(e){}" +
        "}" +
        "function loadHeartbeats(){" +
          "try{ var raw=localStorage.getItem(HEARTBEATS_KEY); return raw?JSON.parse(raw):{}; }catch(e){ return {}; }" +
        "}" +
        "function isSessionActive(id){" +
          "var hb=loadHeartbeats()[id];" +
          "return !!hb && (Date.now()-hb)<HEARTBEAT_STALE_MS;" +
        "}" +
        // Same-origin iframes share sessionStorage with their top-level
        // browser tab, so this reads the exact same value ShellScript.java
        // set in the parent frame - no cross-frame call needed just to know
        // which session THIS browser tab is currently on.
        "function currentSessionId(){" +
          "try{ return sessionStorage.getItem(SESSION_ID_KEY); }catch(e){ return null; }" +
        "}" +

        "function escapeHtml(s){ return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }" +

        "function renderSessions(){" +
          "var list=document.getElementById('sessionList');" +
          "var sessions=loadSessionsMap();" +
          "sessions=Object.assign({}, sessions);" +
          // The Google Drive session is always shown, even the very first
          // time - before it's ever actually been opened, it won't exist in
          // the saved map yet, so a placeholder is synthesized here purely
          // for display (never written back) until shellLoadSession()
          // creates the real entry the first time someone opens it.
          "if(!sessions[GDRIVE_SESSION_ID]){" +
            "sessions[GDRIVE_SESSION_ID]={id:GDRIVE_SESSION_ID, name:'Google Drive', tabs:[], groups:[], createdAt:0, updatedAt:0};" +
          "}" +
          // Same idea for whichever session is open in THIS browser tab:
          // if it only has "useless" tabs so far and has never been named,
          // shellSaveState() deliberately never wrote it to storage (see
          // its comment in ShellScript.java) - but the person still needs
          // to be able to find and name it here, or it can never be kept.
          // Pulled live from the parent shell frame since nothing's been
          // persisted to read it back from otherwise.
          "var mine0=currentSessionId();" +
          "if(mine0 && !sessions[mine0] && window.parent && window.parent.shellTabs){" +
            "sessions[mine0]={id:mine0, name:'', tabs:window.parent.shellTabs, groups:window.parent.shellGroups||[], createdAt:0, updatedAt:0};" +
          "}" +
          "var ids=Object.keys(sessions);" +
          "if(!ids.length){ list.innerHTML=\"<p class='empty'>No sessions yet.</p>\"; return; }" +
          "var mine=currentSessionId();" +
          "ids.sort(function(a,b){" +
            "if(a===GDRIVE_SESSION_ID) return -1;" +
            "if(b===GDRIVE_SESSION_ID) return 1;" +
            "return (sessions[b].updatedAt||0)-(sessions[a].updatedAt||0);" +
          "});" +
          "list.innerHTML=ids.map(function(id){" +
            "var s=sessions[id];" +
            "var pinned=(id===GDRIVE_SESSION_ID);" +
            "var active=isSessionActive(id);" +
            "var isMine=(id===mine);" +
            "var badge=isMine?\"<span class='session-badge session-badge-current'>This tab</span>\":" +
              "(active?\"<span class='session-badge session-badge-active'>Open in another tab</span>\":'');" +
            "var unsavedBadge=(!pinned && !s.named)?\"<span class='session-badge session-badge-unsaved' title='Not named - it may get cleaned up automatically, and you will not be warned before losing it'>Unsaved</span>\":'';" +
            "var icon=pinned?\"<span class='session-icon' title='Google Drive'>&#9729;</span>\":'';" +
            "var tabCount=(s.tabs||[]).length;" +
            "var name=escapeHtml(s.name||(pinned?'Google Drive':'Unnamed session'));" +
            "var locked=(isMine||active);" +
            "var metaLine=pinned&&!s.updatedAt?" +
              "'Browse a connected Google Drive account':" +
              "(tabCount+' tab'+(tabCount===1?'':'s')+' &middot; '+(s.updatedAt?('updated '+fdFormatDate(s.updatedAt)):'not saved yet'));" +
            "return \"<div class='session-row' data-session-id=\\\"\"+id+\"\\\">\" +" +
              "\"<div class='session-info'>\" +" +
                "\"<div class='session-name-row'>\"+icon+\"<span class='session-name'>\"+name+\"</span>\"+badge+unsavedBadge+\"</div>\" +" +
                "\"<div class='session-meta'>\"+metaLine+\"</div>\" +" +
              "\"</div>\" +" +
              "\"<div class='session-actions'>\" +" +
                "\"<button class='session-btn' data-session-action='rename'>\"+(s.name?'Rename':'Name this session')+\"</button>\" +" +
                "(active&&!isMine?\"<button class='session-btn session-btn-warning' data-session-action='close-open-here' title='Close it in that tab and open it here'>Close &amp; open here</button>\":'') +" +
                "\"<button class='session-btn session-btn-primary' data-session-action='open'\"+(locked?' disabled':'')+\" title=\\\"\"+(locked?(isMine?'This is the session currently open in this tab':'Already open in another tab'):'')+\"\\\">Open</button>\" +" +
                "(pinned?'':\"<button class='session-btn session-btn-danger' data-session-action='delete'\"+(locked?' disabled':'')+\">Delete</button>\") +" +
              "\"</div>\" +" +
            "\"</div>\";" +
          "}).join('');" +
        "}" +

        "document.addEventListener('click', function(e){" +
          "var btn=e.target.closest('[data-session-action]');" +
          "if(!btn||btn.disabled) return;" +
          "var row=btn.closest('.session-row');" +
          "var id=row.dataset.sessionId;" +
          "var action=btn.dataset.sessionAction;" +
          "if(action==='rename'){" +
            "var sessions=loadSessionsMap();" +
            "var s=sessions[id];" +
            "if(!s){" +
              // Not persisted yet - either this tab's own still-trivial
              // session (see the synthesized entry above; pull the real
              // live tabs/groups so naming it actually saves something
              // real rather than an empty stub), or (defensively) some
              // other id that's simply gone.
              "if(id===currentSessionId() && window.parent && window.parent.shellTabs){" +
                "s={id:id, name:'', tabs:window.parent.shellTabs, groups:window.parent.shellGroups||[], createdAt:Date.now(), updatedAt:Date.now()};" +
              "}else{" +
                "s={id:id, name:(id==='session-gdrive'?'Google Drive':''), tabs:[], groups:[], createdAt:Date.now(), updatedAt:Date.now()};" +
              "}" +
            "}" +
            "var name=prompt('Name this session:', s.name||'');" +
            "if(!name) return;" +
            "s.name=name;" +
            "s.named=true;" +
            "s.updatedAt=Date.now();" +
            "sessions[id]=s;" +
            "saveSessionsMap(sessions);" +
            "renderSessions();" +
          "}else if(action==='open'){" +
            // Defensive re-check right at click time, in case the badge
            // went stale between the last render and now (another tab
            // could have grabbed it in the last couple seconds).
            "if(isSessionActive(id) || id===currentSessionId()){ renderSessions(); return; }" +
            "if(window.parent && window.parent.shellLoadSession){ window.parent.shellLoadSession(id); }" +
          "}else if(action==='close-open-here'){" +
            "if(!confirm('This session is open in another browser tab right now. Close it there and open it here instead?\\n\\nAny unsaved work in that tab (e.g. an unsaved text edit) could be lost.')) return;" +
            "if(window.parent && window.parent.shellLoadSession){ window.parent.shellLoadSession(id, true); }" +
          "}else if(action==='delete'){" +
            "if(isSessionActive(id) || id===currentSessionId()) return;" +
            "if(!confirm('Delete this session from history? This cannot be undone.')) return;" +
            "var sessions=loadSessionsMap();" +
            "delete sessions[id];" +
            "saveSessionsMap(sessions);" +
            "renderSessions();" +
          "}" +
        "});" +

        "renderSessions();" +
        "setInterval(renderSessions, 3000);" +
        "</script>";
}
