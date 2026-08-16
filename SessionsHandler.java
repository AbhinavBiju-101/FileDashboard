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
          .append("\"Close &amp; open here\" closes it there first). Use \u2601 New Google Drive session below to start ")
          .append("as many separate Drive-browsing sessions as you like, alongside your normal ones - connect an account ")
          .append("from Settings first if you haven't yet.</p>");

        sb.append("<button id='newDriveSessionBtn' class='session-btn session-btn-primary session-new-drive-btn' onclick='createDriveSession()'>")
          .append("<img src='").append(DriveIcon.DATA_URI).append("' width='14' height='14' alt=''> New Google Drive session</button>");
        sb.append("<div class='session-list-divider'></div>");

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
        "var DRIVE_ICON_SRC='" + DriveIcon.DATA_URI + "';" +
        // Legacy fixed id the old singleton Google Drive session always
        // had - kept only so an entry saved under this id from before
        // this page supported multiple Drive sessions still shows up and
        // still displays with the Drive icon/treatment. New Drive sessions
        // (see createDriveSession() below) get a normal generated id and
        // are told apart purely by their own drive:true flag instead.
        "var GDRIVE_SESSION_ID='session-gdrive';" +
        "function isDriveSession(s){ return !!(s && (s.drive || s.id===GDRIVE_SESSION_ID)); }" +
        // Fetched once at page load, keyed by account id, so each Drive
        // session row can show "Signed in as <avatar> name/email" (see
        // renderSessions() below) without a round trip per row. Re-fetched
        // whenever the accounts list might have changed - after using the
        // account picker (createDriveSession() -> shellOpenDrivePicker() in
        // ShellScript.java) - by re-rendering on window focus, since this
        // page can't otherwise know when that popup-based flow finishes.
        "var gdriveAccountsById={};" +
        "function refreshGdriveAccounts(){" +
          "return fetch('/gdrive-accounts').then(function(r){return r.json();}).then(function(accounts){" +
            "gdriveAccountsById={};" +
            "accounts.forEach(function(a){ gdriveAccountsById[a.id]=a; });" +
            "renderSessions();" +
          "}).catch(function(){});" +
        "}" +
        "window.addEventListener('focus', refreshGdriveAccounts);" +

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
          "var sessions=Object.assign({}, loadSessionsMap());" +
          // Same idea as before for whichever session is open in THIS
          // browser tab: if it only has "useless" tabs so far and has
          // never been named, shellSaveState() deliberately never wrote it
          // to storage (see its comment in ShellScript.java) - but the
          // person still needs to be able to find and name it here, or it
          // can never be kept. Pulled live from the parent shell frame
          // since nothing's been persisted to read it back from otherwise.
          "var mine0=currentSessionId();" +
          "if(mine0 && !sessions[mine0] && window.parent && window.parent.shellTabs){" +
            "sessions[mine0]={id:mine0, name:'', drive:!!window.parent.shellSessionIsDrive, accountId:window.parent.shellCurrentDriveAccountId&&window.parent.shellCurrentDriveAccountId(), tabs:window.parent.shellTabs, groups:window.parent.shellGroups||[], createdAt:0, updatedAt:0};" +
          "}" +
          "var ids=Object.keys(sessions);" +
          "if(!ids.length){ list.innerHTML=\"<p class='empty'>No sessions yet - open a new browser tab, or start a Drive session above.</p>\"; return; }" +
          "var mine=currentSessionId();" +
          "ids.sort(function(a,b){" +
            "return (sessions[b].updatedAt||0)-(sessions[a].updatedAt||0);" +
          "});" +
          "list.innerHTML=ids.map(function(id){" +
            "var s=sessions[id];" +
            "var isDrive=isDriveSession(s);" +
            "var active=isSessionActive(id);" +
            "var isMine=(id===mine);" +
            "var badge=isMine?\"<span class='session-badge session-badge-current'>This tab</span>\":" +
              "(active?\"<span class='session-badge session-badge-active'>Open in another tab</span>\":'');" +
            "var unsavedBadge=(!s.named)?\"<span class='session-badge session-badge-unsaved' title='Not named - it may get cleaned up automatically, and you will not be warned before losing it'>Unsaved</span>\":'';" +
            "var icon=isDrive?\"<img class='session-icon' src='\"+DRIVE_ICON_SRC+\"' width='16' height='16' alt='Google Drive' title='Google Drive'>\":'';" +
            "var tabCount=(s.tabs||[]).length;" +
            "var name=escapeHtml(s.name||(isDrive?'Google Drive':'Unnamed session'));" +
            "var locked=(isMine||active);" +
            // "Signed in as ..." chip - see gdriveAccountsById above. Shown
            // whenever the session remembers an accountId that's still
            // connected (a session created before this app supported
            // multiple accounts, or one whose account got disconnected
            // since, simply won't have a match here and the chip is
            // skipped - no dangling/broken account reference shown).
            "var acct=isDrive?gdriveAccountsById[s.accountId]:null;" +
            "var acctChip='';" +
            "if(acct){" +
              "var acctLabel=acct.name||acct.email||'';" +
              "var acctAvatar=acct.picture?\"<img class='session-account-avatar' src='\"+acct.picture+\"' alt=''>\":'';" +
              "acctChip=\"<div class='session-account-chip'>\"+acctAvatar+\"<span>Signed in as \"+escapeHtml(acctLabel)+\"</span></div>\";" +
            "}" +
            "var metaLine=tabCount+' tab'+(tabCount===1?'':'s')+' &middot; '+(s.updatedAt?('updated '+fdFormatDate(s.updatedAt)):'not saved yet');" +
            "return \"<div class='session-row' data-session-id=\\\"\"+id+\"\\\">\" +" +
              "\"<div class='session-info'>\" +" +
                "\"<div class='session-name-row'>\"+icon+\"<span class='session-name'>\"+name+\"</span>\"+badge+unsavedBadge+\"</div>\" +" +
                "acctChip +" +
                "\"<div class='session-meta'>\"+metaLine+\"</div>\" +" +
              "\"</div>\" +" +
              "\"<div class='session-actions'>\" +" +
                "\"<button class='session-btn' data-session-action='rename'>\"+(s.name?'Rename':'Name this session')+\"</button>\" +" +
                "(active&&!isMine?\"<button class='session-btn session-btn-warning' data-session-action='close-open-here' title='Close it in that tab and open it here'>Close &amp; open here</button>\":'') +" +
                "\"<button class='session-btn session-btn-primary' data-session-action='open'\"+(locked?' disabled':'')+\" title=\\\"\"+(locked?(isMine?'This is the session currently open in this tab':'Already open in another tab'):'')+\"\\\">Open</button>\" +" +
                "\"<button class='session-btn session-btn-danger' data-session-action='delete'\"+(locked?' disabled':'')+\">Delete</button>\" +" +
              "\"</div>\" +" +
            "\"</div>\";" +
          "}).join('');" +
        "}" +

        "function createDriveSession(){" +
          "if(window.parent && window.parent.shellOpenDrivePicker){ window.parent.shellOpenDrivePicker(); }" +
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
                "s={id:id, name:'', drive:!!window.parent.shellSessionIsDrive, accountId:window.parent.shellCurrentDriveAccountId&&window.parent.shellCurrentDriveAccountId(), tabs:window.parent.shellTabs, groups:window.parent.shellGroups||[], createdAt:Date.now(), updatedAt:Date.now()};" +
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
        "refreshGdriveAccounts();" +
        "setInterval(renderSessions, 3000);" +
        "</script>";
}
