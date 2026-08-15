/**
 * Tab manager for the app shell (the "/" page). Each tab is one iframe
 * pointed at a content route (/dashboard, /browse?path=..., /search?...).
 * Tab labels are picked up automatically from each iframe's <title> after
 * it loads - so navigating inside a tab (e.g. clicking into a subfolder)
 * updates that tab's name on its own, no extra plumbing needed on the
 * content-page side.
 *
 * A whole tab bar (tabs + groups + which one's active) is a "session" - see
 * the session block below. Every *browser* tab gets its own session
 * (sessionStorage is per-browser-tab, unlike localStorage), so opening a
 * new browser tab always starts fresh, while reloading the same browser tab
 * restores exactly what it had. All sessions - past and present - live in
 * one localStorage map so the Session Manager page (SessionsHandler.java,
 * "/sessions") can list, rename, delete, and reopen them.
 */
public class ShellScript {

    public static final String SCRIPT =
        "<script>" +
        "var shellTabs=[];" +
        "var shellActiveTabId=null;" +
        "var shellTabCounter=0;" +
        "var shellDragTabId=null;" +
        // Set while a group header itself is being dragged (see
        // shellBuildGroupHeader) - lets a whole group be reordered among
        // top-level tabbar items the same way an individual tab is, moving
        // its header and every one of its member tabs together as one
        // block. Kept as a separate variable from shellDragTabId so the
        // two drag modes (reordering a tab vs. reordering a whole group)
        // never get confused with each other mid-drag.
        "var shellDragGroupId=null;" +
        // ---- Tab groups ("folders") - collapsible clusters of tabs in the
        // bar. Every group is rendered with the same fixed accent color
        // (no per-group color picker - deliberately not a "labeling"
        // feature, just a way to fold related tabs together), and can be
        // renamed and collapsed/expanded. shellTabEls/shellGroupHeaderEls
        // hold the live DOM nodes so shellRenderTabBar() can reuse them
        // (matters because tabs inside a collapsed group are detached from
        // the tab bar rather than destroyed, so their iframe and any
        // in-flight state survives collapsing/expanding).
        "var shellGroups=[];" +
        "var shellGroupCounter=0;" +
        "var shellTabEls={};" +
        "var shellGroupHeaderEls={};" +
        "function shellEscapeHtml(s){ return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;'); }" +

        // ---- Sessions ----
        // SESSIONS_KEY holds every session ever created (id -> {id, name,
        // tabs, groups, active, createdAt, updatedAt}), shared across every
        // browser tab via localStorage - this is what the Session Manager
        // page reads to build its history list. shellSessionId is which
        // entry in that map THIS browser tab is currently hosting, and it
        // lives in sessionStorage rather than localStorage specifically
        // because sessionStorage is scoped to one browser tab: a genuinely
        // new browser tab finds nothing there and mints a fresh session,
        // while reloading (or navigating within) the SAME browser tab keeps
        // finding the same id and restores it. Same-origin iframes inside a
        // tab share its sessionStorage too, which is what lets the Sessions
        // page read shellSessionId without asking the parent frame for it.
        //
        // HEARTBEATS_KEY is how a session is known to be "currently open in
        // some browser tab" without any direct tab-to-tab messaging: every
        // open tab stamps its own session with the current time every few
        // seconds, and anything stamped within HEARTBEAT_STALE_MS counts as
        // active. That staleness window (rather than an exact open/closed
        // flag) is what makes a crashed/force-closed tab's session become
        // reopenable again on its own within a few seconds, instead of
        // being permanently stuck "active" with no tab left to release it.
        "var SESSIONS_KEY='fileDashboardSessions';" +
        "var HEARTBEATS_KEY='fileDashboardSessionHeartbeats';" +
        "var SESSION_ID_KEY='fd-session-id';" +
        "var HEARTBEAT_INTERVAL_MS=4000;" +
        "var HEARTBEAT_STALE_MS=10000;" +
        "var shellSessionId=null;" +
        "var shellHeartbeatTimer=null;" +
        // A fixed, well-known session id for the specialized "Google Drive"
        // session (see SessionsHandler.java's pinned row). Using a fixed id
        // rather than one shellGenerateSessionId() would produce means it
        // can be opened even before it's ever been saved once, by
        // synthesizing a default entry for it in shellLoadSession() below.
        // Its root is always Drive's "My Drive", never any local folder -
        // shellApplyDriveSidebar() is what makes the pinned sidebar reflect
        // that (swapping the classic OS-folder shortcuts for "Home
        // folders"/"Home files" against Drive instead) whenever this is the
        // session open in this browser tab.
        "var GDRIVE_SESSION_ID='session-gdrive';" +

        "function fdFormatDate(ts){" +
          "var d=new Date(ts);" +
          "return d.toLocaleDateString([], {month:'short', day:'numeric'}) + ' ' + d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});" +
        "}" +
        "function shellGenerateSessionId(){" +
          "return 's-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,7);" +
        "}" +
        "function shellLoadSessionsMap(){" +
          "try{ var raw=localStorage.getItem(SESSIONS_KEY); return raw?JSON.parse(raw):{}; }catch(e){ return {}; }" +
        "}" +
        "function shellSaveSessionsMap(map){" +
          "try{ localStorage.setItem(SESSIONS_KEY, JSON.stringify(map)); }catch(e){}" +
        "}" +
        "function shellLoadHeartbeats(){" +
          "try{ var raw=localStorage.getItem(HEARTBEATS_KEY); return raw?JSON.parse(raw):{}; }catch(e){ return {}; }" +
        "}" +
        "function shellSaveHeartbeats(map){" +
          "try{ localStorage.setItem(HEARTBEATS_KEY, JSON.stringify(map)); }catch(e){}" +
        "}" +
        "function shellIsSessionActive(id){" +
          "var hb=shellLoadHeartbeats()[id];" +
          "return !!hb && (Date.now()-hb)<HEARTBEAT_STALE_MS;" +
        "}" +
        "function shellTouchHeartbeat(){" +
          "if(!shellSessionId) return;" +
          "var hb=shellLoadHeartbeats();" +
          "hb[shellSessionId]=Date.now();" +
          "shellSaveHeartbeats(hb);" +
        "}" +
        "function shellReleaseHeartbeat(){" +
          "if(!shellSessionId) return;" +
          "var hb=shellLoadHeartbeats();" +
          "delete hb[shellSessionId];" +
          "shellSaveHeartbeats(hb);" +
        "}" +
        // Figures out which session this browser tab is on: an id already
        // sitting in sessionStorage means this is a reload (or an internal
        // navigation) of a browser tab we've already claimed, so keep using
        // it; finding nothing means this browser tab has never had one -
        // genuinely new - so mint a fresh id and claim it. Returns whether
        // it found an existing one, since that's what shellLoadState() uses
        // to decide whether there's anything worth restoring.
        "function shellInitSession(){" +
          "var existingId;" +
          "try{ existingId=sessionStorage.getItem(SESSION_ID_KEY); }catch(e){ existingId=null; }" +
          "if(existingId){ shellSessionId=existingId; return true; }" +
          "shellSessionId=shellGenerateSessionId();" +
          "try{ sessionStorage.setItem(SESSION_ID_KEY, shellSessionId); }catch(e){}" +
          "return false;" +
        "}" +

        // ---- Automatic cleanup of throwaway sessions ----
        // Every browser tab opened on File Dashboard starts a brand new
        // session (see shellInitSession()) - including ones opened purely
        // by accident and closed again a second later. Without this, every
        // one of those would sit forever in the Sessions list as a
        // generically-named "Session <date>" entry with nothing useful in
        // it. So: a session only actually gets written to storage once it
        // either (a) has a real name the person gave it (see
        // shellRenameCurrentSession()/SessionsHandler.java's Rename
        // button), or (b) has at least one tab that isn't just one of the
        // "nothing to lose" pages below. Until then it lives purely in
        // this tab's memory and vanishes with it when the tab closes -
        // which is exactly the desired behavior for an accidental open.
        "function shellIsUselessTabUrl(url){" +
          "if(!url) return true;" +
          "if(url.indexOf('/dashboard')===0) return true;" +
          "if(url.indexOf('/settings')===0) return true;" +
          "if(url.indexOf('/sessions')===0) return true;" +
          "if(url.indexOf('/trash')===0) return true;" + // covers /trash and /trash-browse
          "if(url.indexOf('/browse?path=')===0){ return url==='/browse?path='; }" +
          "if(url.indexOf('/gdrive?path=')===0){ return url==='/gdrive?path='; }" +
          "return false;" +
        "}" +
        "function shellSessionHasOnlyUselessTabs(){" +
          "return shellTabs.every(function(t){ return shellIsUselessTabUrl(t.url); });" +
        "}" +

        "function shellSaveState(){" +
          "try{" +
            "var sessions=shellLoadSessionsMap();" +
            "var existing=sessions[shellSessionId];" +
            "var isNamed=(shellSessionId===GDRIVE_SESSION_ID)||!!(existing&&existing.named);" +
            "if(!isNamed && shellSessionHasOnlyUselessTabs()){" +
              // Nothing worth keeping (yet) - if an earlier, since-emptied
              // version of this session had been persisted, drop it too,
              // rather than leaving a stale leftover entry behind.
              "if(existing){ delete sessions[shellSessionId]; shellSaveSessionsMap(sessions); }" +
              "shellUpdateUnsavedBadge();" +
              "shellUpdateSessionSwitcher();" +
              "return;" +
            "}" +
            "sessions[shellSessionId]={" +
              "id:shellSessionId," +
              "name:(existing&&existing.name)||('Session '+fdFormatDate(Date.now()))," +
              "named:!!(existing&&existing.named)," +
              "tabs:shellTabs," +
              "active:shellActiveTabId," +
              "groups:shellGroups," +
              "createdAt:(existing&&existing.createdAt)||Date.now()," +
              "updatedAt:Date.now()" +
            "};" +
            "shellSaveSessionsMap(sessions);" +
            "shellUpdateUnsavedBadge();" +
            "shellUpdateSessionSwitcher();" +
          "}catch(e){}" +
        "}" +

        // The little "\u26a0 Unsaved session" pill next to the "+" button
        // (see AppShellHandler.java) - the in-context alternative to
        // hunting it down on the Sessions page, and considerably more
        // reliable than the beforeunload warning below (see its comment
        // for why that one can't be counted on alone).
        "function shellUpdateUnsavedBadge(){" +
          "var badge=document.getElementById('shellUnsavedBadge');" +
          "if(!badge) return;" +
          "if(shellSessionId===GDRIVE_SESSION_ID){ badge.style.display='none'; return; }" +
          "var sessions=shellLoadSessionsMap();" +
          "var existing=sessions[shellSessionId];" +
          "var isNamed=!!(existing&&existing.named);" +
          "badge.style.display=(!isNamed && !shellSessionHasOnlyUselessTabs())?'inline-flex':'none';" +
        "}" +

        // Shared by the unsaved-session badge's click handler and
        // SessionsHandler.java's own Rename button (which reaches this tab
        // via window.parent). Builds a complete, valid session entry from
        // the LIVE in-memory tabs/groups if none has ever been persisted
        // yet (see shellSaveState()'s skip-saving branch above) rather
        // than writing a bare stub with no tabs into storage.
        // ---- Bottom-of-sidebar session switcher (see SidebarRenderer.java) ----
        // Reuses shellLoadSession(id, true)'s existing "close & open here"
        // logic exactly - picking a session that's open in another browser
        // tab just force-closes it there and loads it here, same as the
        // Sessions page's own "Close & open here" button, after a matching
        // confirm() (see SessionsHandler.java's own copy of this same
        // confirm text - kept identical so it reads as one consistent
        // feature, not two different ones that happen to do the same
        // thing).
        "function shellSessionDisplayName(){" +
          "if(shellSessionId===GDRIVE_SESSION_ID) return 'Google Drive';" +
          "var existing=shellLoadSessionsMap()[shellSessionId];" +
          "return (existing&&existing.name)||'Unnamed session';" +
        "}" +
        "function shellUpdateSessionSwitcher(){" +
          "var dot=document.getElementById('sidebarSessionDot');" +
          "var nameEl=document.getElementById('sidebarSessionName');" +
          "if(!dot||!nameEl) return;" +
          "nameEl.textContent=shellSessionDisplayName();" +
          "var existing=shellLoadSessionsMap()[shellSessionId];" +
          "var isNamed=(shellSessionId===GDRIVE_SESSION_ID)||!!(existing&&existing.named);" +
          "dot.className='sidebar-session-dot'+(shellSessionId===GDRIVE_SESSION_ID?' drive':(!isNamed?' unsaved':''));" +
        "}" +
        "function shellToggleSessionMenu(e){" +
          "e.stopPropagation();" +
          "var menu=document.getElementById('sidebarSessionMenu');" +
          "if(menu.classList.contains('open')){ shellCloseSessionMenu(); return; }" +
          "shellRenderSessionMenu();" +
          "var btn=document.getElementById('sidebarSessionSwitcher');" +
          "var rect=btn.getBoundingClientRect();" +
          "menu.style.left=Math.max(8,rect.left)+'px';" +
          "menu.style.bottom=(window.innerHeight-rect.top)+'px';" +
          "menu.style.top='';" +
          "menu.classList.add('open');" +
          "setTimeout(function(){ document.addEventListener('click', shellSessionMenuOutsideClick); }, 0);" +
        "}" +
        "function shellCloseSessionMenu(){" +
          "var menu=document.getElementById('sidebarSessionMenu');" +
          "if(menu) menu.classList.remove('open');" +
          "document.removeEventListener('click', shellSessionMenuOutsideClick);" +
        "}" +
        "function shellSessionMenuOutsideClick(e){" +
          "var menu=document.getElementById('sidebarSessionMenu');" +
          "if(menu && !menu.contains(e.target) && !e.target.closest('#sidebarSessionSwitcher')){ shellCloseSessionMenu(); }" +
        "}" +
        "function shellRenderSessionMenu(){" +
          "var menu=document.getElementById('sidebarSessionMenu');" +
          "var sessions=Object.assign({}, shellLoadSessionsMap());" +
          "if(!sessions[GDRIVE_SESSION_ID]){ sessions[GDRIVE_SESSION_ID]={id:GDRIVE_SESSION_ID,name:'Google Drive',tabs:[],groups:[],createdAt:0,updatedAt:0}; }" +
          "if(!sessions[shellSessionId]){ sessions[shellSessionId]={id:shellSessionId,name:'',tabs:shellTabs,groups:shellGroups,createdAt:0,updatedAt:0}; }" +
          "var ids=Object.keys(sessions);" +
          "ids.sort(function(a,b){" +
            "if(a===GDRIVE_SESSION_ID) return -1;" +
            "if(b===GDRIVE_SESSION_ID) return 1;" +
            "if(a===shellSessionId) return -1;" +
            "if(b===shellSessionId) return 1;" +
            "return (sessions[b].updatedAt||0)-(sessions[a].updatedAt||0);" +
          "});" +
          "var rows=ids.map(function(id){" +
            "var s=sessions[id];" +
            "var isMine=(id===shellSessionId);" +
            "var activeElsewhere=!isMine && shellIsSessionActive(id);" +
            "var name=shellEscapeHtml(s.name||'Unnamed session');" +
            "var badges='';" +
            "if(activeElsewhere){ badges+=\"<span class='sidebar-session-menu-badge elsewhere'>Open elsewhere</span>\"; }" +
            "if(id!==GDRIVE_SESSION_ID && !s.named){ badges+=\"<span class='sidebar-session-menu-badge unsaved'>Unsaved</span>\"; }" +
            "var tabCount=(s.tabs||[]).length;" +
            "return \"<div class='sidebar-session-menu-item\"+(isMine?' current':'')+\"' data-session-id='\"+id+\"' title='\"+tabCount+\" tab\"+(tabCount===1?'':'s')+(isMine?' - this tab':'')+\"'>\" +" +
              "\"<span class='sidebar-session-menu-name'>\"+name+(isMine?' (this tab)':'')+\"</span>\"+badges+" +
            "\"</div>\";" +
          "}).join('');" +
          "rows+=\"<div class='sidebar-session-menu-divider'></div>\";" +
          "rows+=\"<div class='sidebar-session-menu-item sidebar-session-menu-action' data-session-action='new'>+ New session</div>\";" +
          "rows+=\"<div class='sidebar-session-menu-item sidebar-session-menu-action' data-session-action='manage'>Manage sessions...</div>\";" +
          "menu.innerHTML=rows;" +
        "}" +
        "document.addEventListener('click', function(e){" +
          "var actionItem=e.target.closest('[data-session-action]');" +
          "if(actionItem){" +
            "shellCloseSessionMenu();" +
            "if(actionItem.dataset.sessionAction==='new'){ shellHandleForcedClose(); }" +
            "else if(actionItem.dataset.sessionAction==='manage'){ navigateCurrentTab('/sessions'); }" +
            "return;" +
          "}" +
          "var row=e.target.closest('.sidebar-session-menu-item[data-session-id]');" +
          "if(!row || row.classList.contains('current')) return;" +
          "var id=row.dataset.sessionId;" +
          "shellCloseSessionMenu();" +
          "if(shellIsSessionActive(id)){" +
            "if(!confirm('This session is open in another browser tab. Close it there and open it here?')) return;" +
          "}" +
          "shellLoadSession(id, true);" +
        "});" +

        "function shellRenameCurrentSession(){" +
          "var sessions=shellLoadSessionsMap();" +
          "var existing=sessions[shellSessionId];" +
          "var name=prompt('Name this session:', (existing&&existing.name)||'');" +
          "if(!name) return;" +
          "var toSave=existing||{createdAt:Date.now()};" +
          "toSave.id=shellSessionId;" +
          "toSave.name=name;" +
          "toSave.named=true;" +
          "toSave.tabs=shellTabs;" +
          "toSave.groups=shellGroups;" +
          "toSave.active=shellActiveTabId;" +
          "toSave.updatedAt=Date.now();" +
          "if(!toSave.createdAt) toSave.createdAt=Date.now();" +
          "sessions[shellSessionId]=toSave;" +
          "shellSaveSessionsMap(sessions);" +
          "shellUpdateUnsavedBadge();" +
          "shellUpdateSessionSwitcher();" +
        "}" +

        // Best-effort warning on an actual browser tab/window close (or
        // navigating this browser tab away to a different site entirely).
        // Modern browsers deliberately ignore any custom message text or
        // buttons here and show their own generic "Leave site?"
        // Leave/Cancel prompt instead - a long-standing anti-phishing
        // restriction, not something this app can work around - so this
        // can't literally offer a "rename or cancel" choice the way the
        // unsaved-session badge above can. It's still worth wiring up as a
        // last line of defense, since the badge only helps if it's been
        // noticed before the tab gets closed.
        "window.addEventListener('beforeunload', function(e){" +
          "if(shellSessionId===GDRIVE_SESSION_ID) return;" +
          "var sessions=shellLoadSessionsMap();" +
          "var existing=sessions[shellSessionId];" +
          "if(existing && existing.named) return;" +
          "if(shellSessionHasOnlyUselessTabs()) return;" +
          "e.preventDefault();" +
          "e.returnValue='';" +
          "return '';" +
        "});" +

        "function shellLoadState(){" +
          "var hadExistingSession=shellInitSession();" +
          "shellTouchHeartbeat();" +
          "if(!shellHeartbeatTimer){ shellHeartbeatTimer=setInterval(shellTouchHeartbeat, HEARTBEAT_INTERVAL_MS); }" +
          "if(!hadExistingSession) return false;" +
          "try{" +
            "var sessions=shellLoadSessionsMap();" +
            "var s=sessions[shellSessionId];" +
            "if(!s||!s.tabs||!s.tabs.length) return false;" +
            "shellGroups=s.groups||[];" +
            "shellGroupCounter=shellGroups.reduce(function(m,g){" +
              "var n=parseInt(g.id.replace('group-',''),10); return isNaN(n)?m:Math.max(m,n);" +
            "},0);" +
            "shellTabs=s.tabs;" +
            "shellTabs.forEach(function(t){ shellCreateTab(t.id, t.url, t.title); });" +
            "shellTabCounter=shellTabs.reduce(function(m,t){" +
              "var n=parseInt(t.id.replace('tab-',''),10); return isNaN(n)?m:Math.max(m,n);" +
            "},0);" +
            "shellSetActiveTab(s.active && shellTabs.some(function(t){return t.id===s.active;}) ? s.active : shellTabs[0].id);" +
            "return true;" +
          "}catch(e){ return false; }" +
        "}" +


        // Cross-tab messaging for "Close & open here" (Session Manager's
        // force-reopen action): posting a force-close message is how one
        // browser tab tells another, currently-active one to give up its
        // session. BroadcastChannel isn't universally available (older
        // Safari), so everything here treats it as optional - the request
        // still gets sent as best-effort via localStorage's own change
        // (see the optimistic heartbeat clear in shellLoadSession), it just
        // won't be instant if the channel itself is unsupported.
        "var shellSessionChannel=null;" +
        "try{ shellSessionChannel=new BroadcastChannel('fd-sessions'); }catch(e){ shellSessionChannel=null; }" +

        // Shared by shellLoadSession() (switching to a different session
        // deliberately) and shellHandleForcedClose() (being kicked off this
        // session by another tab) - both need to fully clear out the
        // current tab bar/iframes/toasts before loading something else in
        // their place.
        "function shellTeardownCurrentTabs(){" +
          "Object.keys(shellToastEls||{}).forEach(function(cid){ shellRemoveReopenToast(cid); });" +
          "shellClosedTabsStack=[];" +
          "Object.keys(shellTabEls).forEach(function(id){ var el=shellTabEls[id]; if(el&&el.parentNode) el.parentNode.removeChild(el); });" +
          "shellTabEls={};" +
          "shellGroupHeaderEls={};" +
          "Array.prototype.slice.call(document.querySelectorAll('.tabcontent iframe')).forEach(function(f){ f.remove(); });" +
        "}" +

        // Called from the Session Manager page (via
        // window.parent.shellLoadSession(id[, force])) to switch THIS
        // browser tab onto a different session - tearing down every
        // tab/iframe it has open now and rebuilding from the target
        // session's saved tabs/groups. The session this tab is leaving
        // behind isn't deleted - it stays in history exactly as last saved,
        // just idle (its heartbeat entry is released) until something
        // reopens it.
        //
        // force=true is "Close & open here": if the target is active in
        // another browser tab right now, that tab is told to give up the
        // session (via BroadcastChannel, handled by shellHandleForcedClose
        // below) rather than refusing the whole operation. Its heartbeat is
        // also cleared right away as a fallback for browsers without
        // BroadcastChannel, or in case that tab is backgrounded/throttled
        // and slow to react - meaning there's a brief window where both
        // tabs could believe they own the session until the other one
        // catches up. Acceptable for what this is; not a real distributed
        // lock.
        "function shellLoadSession(targetId, force){" +
          "if(!targetId || targetId===shellSessionId) return false;" +
          "var activeElsewhere=shellIsSessionActive(targetId);" +
          "if(activeElsewhere && !force){ alert('That session is currently open in another tab.'); return false; }" +
          "var sessions=shellLoadSessionsMap();" +
          "var s=sessions[targetId];" +
          "if(!s && targetId===GDRIVE_SESSION_ID){" +
            "s={id:GDRIVE_SESSION_ID, name:'Google Drive', tabs:[{id:'tab-gdrive-1', url:'/gdrive?path=', title:'Google Drive', groupId:null}], groups:[], active:'tab-gdrive-1'};" +
          "}" +
          "if(!s){ alert('That session no longer exists.'); return false; }" +

          "if(activeElsewhere && force){" +
            "if(shellSessionChannel){ shellSessionChannel.postMessage({type:'force-close', sessionId:targetId}); }" +
            "var hb=shellLoadHeartbeats(); delete hb[targetId]; shellSaveHeartbeats(hb);" +
          "}" +

          "shellReleaseHeartbeat();" +
          "shellTeardownCurrentTabs();" +

          "shellSessionId=targetId;" +
          "try{ sessionStorage.setItem(SESSION_ID_KEY, shellSessionId); }catch(e){}" +
          "shellApplyDriveSidebar(shellSessionId===GDRIVE_SESSION_ID);" +
          "shellGroups=s.groups||[];" +
          "shellGroupCounter=shellGroups.reduce(function(m,g){" +
            "var n=parseInt(g.id.replace('group-',''),10); return isNaN(n)?m:Math.max(m,n);" +
          "},0);" +
          "shellTabs=s.tabs||[];" +
          "shellTabCounter=shellTabs.reduce(function(m,t){" +
            "var n=parseInt((t.id||'').replace('tab-',''),10); return isNaN(n)?m:Math.max(m,n);" +
          "},0);" +
          "if(shellTabs.length){" +
            "shellTabs.forEach(function(t){ shellCreateTab(t.id, t.url, t.title); });" +
            "shellSetActiveTab(s.active && shellTabs.some(function(t){return t.id===s.active;}) ? s.active : shellTabs[0].id);" +
          "}else{" +
            "openTab('/dashboard','Dashboard');" +
          "}" +
          "shellTouchHeartbeat();" +
          "shellSaveState();" +
          "return true;" +
        "}" +

        "function shellApplyDriveSidebar(isDrive){" +
          "var sb=document.getElementById('sidebar');" +
          "if(sb){ sb.classList.toggle('drive-mode', !!isDrive); }" +
          "shellUpdateSessionSwitcher();" +
        "}" +

        // The other end of "Close & open here": some other browser tab has
        // just claimed this tab's session out from under it. Rather than
        // leaving this tab dead/blank, it's treated exactly like opening a
        // genuinely new browser tab would be - a fresh, empty session on
        // Dashboard - so it stays fully usable, just no longer hosting the
        // session that moved elsewhere.
        "function shellHandleForcedClose(){" +
          "shellReleaseHeartbeat();" +
          "shellTeardownCurrentTabs();" +
          "shellSessionId=shellGenerateSessionId();" +
          "try{ sessionStorage.setItem(SESSION_ID_KEY, shellSessionId); }catch(e){}" +
          "shellApplyDriveSidebar(false);" +
          "shellGroups=[]; shellGroupCounter=0; shellTabs=[]; shellTabCounter=0;" +
          "openTab('/dashboard','Dashboard');" +
          "shellTouchHeartbeat();" +
        "}" +
        "if(shellSessionChannel){" +
          "shellSessionChannel.addEventListener('message', function(ev){" +
            "var msg=ev.data;" +
            "if(msg && msg.type==='force-close' && msg.sessionId===shellSessionId){ shellHandleForcedClose(); }" +
          "});" +
        "}" +
        "window.addEventListener('pagehide', shellReleaseHeartbeat);" +

        "function openTab(url, fallbackLabel){" +
          "var existing=shellTabs.find(function(t){ return t.url===url; });" +
          "if(existing){ shellSetActiveTab(existing.id); return false; }" +
          "var id='tab-'+(++shellTabCounter);" +
          "shellTabs.push({id:id, url:url, title:fallbackLabel||'Loading...', groupId:null});" +
          "shellCreateTab(id, url, fallbackLabel||'Loading...');" +
          "shellSetActiveTab(id);" +
          "shellSaveState();" +
          "return false;" +
        "}" +

        // Navigates whichever tab is currently active to a new URL, in
        // place - no new tab created. This is what sidebar links use: like
        // clicking a link normally navigates the current page, sidebar
        // navigation updates the current tab rather than spawning a new
        // one every time. openTab() (above) is reserved for things that
        // should genuinely open alongside what you're already looking at,
        // like the "+" button or "Open Viewer".
        "function navigateCurrentTab(url){" +
          "if(!shellActiveTabId){ return openTab(url); }" +
          "var iframe=document.getElementById(shellActiveTabId+'-frame');" +
          "if(!iframe){ return openTab(url); }" +
          "iframe.src=url;" +
          "var tabObj=shellTabs.find(function(t){ return t.id===shellActiveTabId; });" +
          "if(tabObj){ tabObj.url=url; tabObj.title='Loading...'; }" +
          "var btn=shellTabEls[shellActiveTabId];" +
          "if(btn){ var span=btn.querySelector('.tab-title'); if(span) span.textContent='Loading...'; }" +
          "shellSaveState();" +
          "return false;" +
        "}" +

        // Builds (once) the tab button's DOM node and wires up its
        // listeners against a stable id, storing it in shellTabEls so later
        // re-renders (e.g. toggling a group open/closed) can move the same
        // node around instead of recreating it. Actual placement into the
        // bar - including where group headers land - is shellRenderTabBar's
        // job, called after every mutation.
        "function shellBuildTabElement(id, title){" +
          "var btn=document.createElement('div');" +
          "btn.className='tab'; btn.id=id;" +
          "var titleSpan=document.createElement('span');" +
          "titleSpan.className='tab-title'; titleSpan.textContent=title;" +
          "titleSpan.onclick=function(){ shellSetActiveTab(id); };" +
          "var closeSpan=document.createElement('span');" +
          "closeSpan.className='tab-close'; closeSpan.innerHTML='&times;';" +
          "closeSpan.onclick=function(e){ e.stopPropagation(); shellCloseTab(id); };" +
          "btn.appendChild(titleSpan); btn.appendChild(closeSpan);" +

          // Drag-to-reorder: dragover moves the dragged tab's DOM node live
          // (classic sortable-list trick), so the person sees tabs shuffle
          // as they drag rather than only on drop. shellTabs (the array
          // that actually gets persisted) is re-synced from the resulting
          // DOM order once the drag ends, whatever element it ends over.
          // Dropping directly onto a group header instead (see
          // shellBuildGroupHeader) joins that group rather than reordering.
          "btn.draggable=true;" +
          "btn.addEventListener('dragstart', function(e){" +
            "shellDragTabId=id;" +
            "btn.classList.add('dragging');" +
            "e.dataTransfer.effectAllowed='move';" +
            "try{ e.dataTransfer.setData('text/plain', id); }catch(err){}" +
          "});" +
          "btn.addEventListener('dragover', function(e){" +
            "if(shellDragGroupId){ shellGroupDragOverTab(e, id); } else { shellTabDragOver(e, id); }" +
          "});" +
          "btn.addEventListener('dragend', function(){" +
            "btn.classList.remove('dragging');" +
            "shellDragTabId=null;" +
            "shellSyncTabOrderFromDom();" +
          "});" +
          "btn.addEventListener('contextmenu', function(e){" +
            "e.preventDefault();" +
            "showTabContextMenu(e.clientX, e.clientY, id);" +
          "});" +
          "shellTabEls[id]=btn;" +
          "return btn;" +
        "}" +

        // Creates the tab's iframe (unaffected by grouping) and its button,
        // then re-renders the whole bar so it lands in the right spot -
        // right after its group's header if it belongs to one.
        "function shellCreateTab(id, url, title){" +
          "var iframe=document.createElement('iframe');" +
          "iframe.id=id+'-frame'; iframe.src=url;" +
          "iframe.onload=function(){" +
            "try{" +
              "var doc=iframe.contentDocument;" +
              "var newTitle=(doc && doc.title) ? doc.title : title;" +
              "var newUrl=iframe.contentWindow.location.pathname+iframe.contentWindow.location.search;" +
              "shellUpdateTab(id, newTitle, newUrl);" +
            "}catch(e){}" +
          "};" +
          "document.getElementById('tabcontent').appendChild(iframe);" +
          "shellBuildTabElement(id, title);" +
          "shellRenderTabBar();" +
        "}" +

        // Keeps both title AND url in sync with wherever the tab actually
        // navigated to - not just when navigateCurrentTab() was called, but
        // also when navigation happened from a normal link click inside the
        // tab's own content (e.g. clicking into a subfolder). Otherwise a
        // restored tab would jump back to a stale starting location.
        "function shellUpdateTab(id, newTitle, newUrl){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===id; });" +
          "if(tabObj){ tabObj.title=newTitle; if(newUrl) tabObj.url=newUrl; }" +
          "var btn=shellTabEls[id];" +
          "if(btn){ var span=btn.querySelector('.tab-title'); if(span) span.textContent=newTitle; }" +
          "shellSaveState();" +
        "}" +

        "function shellTabDragOver(e, overId){" +
          "e.preventDefault();" +
          "if(!shellDragTabId || shellDragTabId===overId) return;" +
          "var dragging=shellTabEls[shellDragTabId];" +
          "var over=shellTabEls[overId];" +
          "if(!dragging||!over) return;" +
          "var rect=over.getBoundingClientRect();" +
          "var before=(e.clientX-rect.left)<rect.width/2;" +
          "var tabbar=document.getElementById('tabbar');" +
          "tabbar.insertBefore(dragging, before?over:over.nextSibling);" +
        "}" +

        // Reorders shellTabs (the array that gets persisted to
        // localStorage) to match whatever order the tab elements ended up
        // in after dragging, then pulls each group's tabs back together
        // (shellNormalizeGroups) since a drag can otherwise drop an
        // unrelated tab in the middle of a group's run, and finally
        // re-renders so the bar reflects the normalized order - group
        // headers included - rather than the raw drop position.
        "function shellSyncTabOrderFromDom(){" +
          "var order=Array.prototype.map.call(document.querySelectorAll('#tabbar .tab'), function(el){ return el.id; });" +
          "shellTabs.sort(function(a,b){ return order.indexOf(a.id)-order.indexOf(b.id); });" +
          "shellNormalizeGroups();" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +

        // ---- Dragging a whole group ("folder") to reorder it among the
        // top-level tabbar items, same as dragging an individual tab does.
        // Unlike tab-dragging (which only ever moves one element), a group
        // drag has to move its header AND every one of its member tabs
        // together as a single block - shellMoveGroupBlockBefore() collects
        // those nodes and re-inserts them, in their existing relative
        // order, right before whatever the drag is currently hovering
        // over. A collapsed group's member tabs aren't in the DOM at all
        // (see shellRenderTabBar) so the "block" is just its header in that
        // case, which is exactly the visual behavior wanted anyway.
        "function shellMoveGroupBlockBefore(groupId, refNode){" +
          "var tabbar=document.getElementById('tabbar');" +
          "var header=shellGroupHeaderEls[groupId];" +
          "var nodes=[];" +
          "if(header) nodes.push(header);" +
          "Array.prototype.forEach.call(document.querySelectorAll('#tabbar .tab'), function(el){" +
            "var t=shellTabs.find(function(tt){ return tt.id===el.id; });" +
            "if(t && t.groupId===groupId) nodes.push(el);" +
          "});" +
          "nodes.forEach(function(el){ tabbar.insertBefore(el, refNode); });" +
        "}" +
        "function shellGroupDragOverHeader(e, overGroupId){" +
          "if(!shellDragGroupId || shellDragGroupId===overGroupId) return;" +
          "e.preventDefault();" +
          "var overHeader=shellGroupHeaderEls[overGroupId];" +
          "if(!overHeader) return;" +
          "var rect=overHeader.getBoundingClientRect();" +
          "var before=(e.clientX-rect.left)<rect.width/2;" +
          "shellMoveGroupBlockBefore(shellDragGroupId, before?overHeader:overHeader.nextSibling);" +
        "}" +
        "function shellGroupDragOverTab(e, overTabId){" +
          "if(!shellDragGroupId) return;" +
          "var overTabObj=shellTabs.find(function(t){ return t.id===overTabId; });" +
          // Hovering over one of the dragged group's own (expanded)
          // member tabs is a no-op - it's already sitting right there.
          "if(overTabObj && overTabObj.groupId===shellDragGroupId) return;" +
          "e.preventDefault();" +
          "var overEl=shellTabEls[overTabId];" +
          "if(!overEl) return;" +
          "var rect=overEl.getBoundingClientRect();" +
          "var before=(e.clientX-rect.left)<rect.width/2;" +
          "shellMoveGroupBlockBefore(shellDragGroupId, before?overEl:overEl.nextSibling);" +
        "}" +
        // The group-drag equivalent of shellSyncTabOrderFromDom(): reads
        // back the top-level order of headers/ungrouped tabs the live
        // dragover reordering above left the DOM in, and rebuilds shellTabs
        // to match - each group's own member tabs keep whatever relative
        // order they already had, only the group's position among its
        // siblings changes.
        "function shellSyncBlockOrderFromDom(){" +
          "var newOrder=[];" +
          "var handledGroups={};" +
          "var nodes=document.querySelectorAll('#tabbar .tab-group-header, #tabbar .tab');" +
          "Array.prototype.forEach.call(nodes, function(el){" +
            "if(el.classList.contains('tab-group-header')){" +
              "var gid=el.dataset.groupId;" +
              "if(handledGroups[gid]) return;" +
              "handledGroups[gid]=true;" +
              "shellTabs.forEach(function(t){ if(t.groupId===gid) newOrder.push(t); });" +
              "return;" +
            "}" +
            "var t=shellTabs.find(function(tt){ return tt.id===el.id; });" +
            "if(!t) return;" +
            "if(t.groupId){" +
              "if(handledGroups[t.groupId]) return;" +
              "handledGroups[t.groupId]=true;" +
              "shellTabs.forEach(function(tt){ if(tt.groupId===t.groupId) newOrder.push(tt); });" +
              "return;" +
            "}" +
            "newOrder.push(t);" +
          "});" +
          // Defensive: anything somehow missed (shouldn't happen) is
          // appended rather than silently dropped.
          "shellTabs.forEach(function(t){ if(newOrder.indexOf(t)===-1) newOrder.push(t); });" +
          "shellTabs=newOrder;" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +

        // ---- Group ("folder") management ----
        // Every group's header uses the same fixed .tab-group-header
        // styling - intentionally no per-group color, just name + collapse
        // state - so "folders" stay visually consistent no matter how many
        // exist.
        "function shellNormalizeGroups(){" +
          "var firstIndex={};" +
          "shellTabs.forEach(function(t,i){ if(t.groupId && !(t.groupId in firstIndex)) firstIndex[t.groupId]=i; });" +
          "var withIdx=shellTabs.map(function(t,i){ return {t:t, i:i}; });" +
          "withIdx.sort(function(a,b){" +
            "var ga=a.t.groupId ? firstIndex[a.t.groupId] : a.i;" +
            "var gb=b.t.groupId ? firstIndex[b.t.groupId] : b.i;" +
            "if(ga!==gb) return ga-gb;" +
            "return a.i-b.i;" +
          "});" +
          "shellTabs=withIdx.map(function(x){ return x.t; });" +
        "}" +
        "function shellCleanupEmptyGroup(groupId){" +
          "var stillUsed=shellTabs.some(function(t){ return t.groupId===groupId; });" +
          "if(!stillUsed){" +
            "shellGroups=shellGroups.filter(function(g){ return g.id!==groupId; });" +
            "delete shellGroupHeaderEls[groupId];" +
          "}" +
        "}" +
        "function shellCreateGroupWithTab(tabId){" +
          "var name=prompt('Group name:', 'New Group');" +
          "if(!name) return;" +
          "var gid='group-'+(++shellGroupCounter);" +
          "shellGroups.push({id:gid, name:name, collapsed:false});" +
          "shellAssignTabToGroup(tabId, gid);" +
        "}" +
        "function shellAssignTabToGroup(tabId, groupId){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===tabId; });" +
          "if(!tabObj) return;" +
          "var oldGroup=tabObj.groupId;" +
          "if(oldGroup===groupId) return;" +
          "tabObj.groupId=groupId;" +
          "shellNormalizeGroups();" +
          "if(oldGroup) shellCleanupEmptyGroup(oldGroup);" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +
        "function shellRemoveTabFromGroup(tabId){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===tabId; });" +
          "if(!tabObj || !tabObj.groupId) return;" +
          "var oldGroup=tabObj.groupId;" +
          "tabObj.groupId=null;" +
          "shellCleanupEmptyGroup(oldGroup);" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +
        "function shellUngroupAll(groupId){" +
          "shellTabs.forEach(function(t){ if(t.groupId===groupId) t.groupId=null; });" +
          "shellGroups=shellGroups.filter(function(g){ return g.id!==groupId; });" +
          "delete shellGroupHeaderEls[groupId];" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +
        "function shellRenameGroup(groupId){" +
          "var g=shellGroups.find(function(gr){ return gr.id===groupId; });" +
          "if(!g) return;" +
          "var name=prompt('Rename group:', g.name);" +
          "if(!name) return;" +
          "g.name=name;" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +
        // Collapsing a group that contains the active tab switches to
        // another visible tab first, same as it would look odd for the
        // highlighted tab to vanish into a collapsed group with nothing in
        // the bar showing as active. If every open tab is in this one
        // group there's nowhere else to go, so it just collapses in place.
        "function toggleGroupCollapse(groupId){" +
          "var g=shellGroups.find(function(gr){ return gr.id===groupId; });" +
          "if(!g) return;" +
          "g.collapsed=!g.collapsed;" +
          "if(g.collapsed){" +
            "var activeTabObj=shellTabs.find(function(t){ return t.id===shellActiveTabId; });" +
            "if(activeTabObj && activeTabObj.groupId===groupId){" +
              "var fallback=shellTabs.find(function(t){ return t.groupId!==groupId; });" +
              "if(fallback){ shellSetActiveTab(fallback.id); }" +
            "}" +
          "}" +
          "shellSaveState();" +
          "shellRenderTabBar();" +
        "}" +
        "function shellBuildGroupHeader(groupId){" +
          "var header=document.createElement('div');" +
          "header.className='tab-group-header';" +
          "header.dataset.groupId=groupId;" +
          "var toggle=document.createElement('span');" +
          "toggle.className='tab-group-toggle'; toggle.textContent='\\u25BE';" +
          "var nameSpan=document.createElement('span');" +
          "nameSpan.className='tab-group-name';" +
          "var countSpan=document.createElement('span');" +
          "countSpan.className='tab-group-count';" +
          "header.appendChild(toggle); header.appendChild(nameSpan); header.appendChild(countSpan);" +
          "header.addEventListener('click', function(){ toggleGroupCollapse(groupId); });" +
          "header.addEventListener('dblclick', function(e){ e.stopPropagation(); shellRenameGroup(groupId); });" +
          "header.addEventListener('contextmenu', function(e){ e.preventDefault(); e.stopPropagation(); showGroupContextMenu(e.clientX, e.clientY, groupId); });" +
          // Draggable exactly like an individual tab (see
          // shellBuildTabElement) - dragging the header moves the whole
          // group (header + member tabs) as one block; see
          // shellMoveGroupBlockBefore()/shellSyncBlockOrderFromDom() above.
          "header.draggable=true;" +
          "header.addEventListener('dragstart', function(e){" +
            "shellDragGroupId=groupId;" +
            "header.classList.add('dragging');" +
            "e.dataTransfer.effectAllowed='move';" +
            "try{ e.dataTransfer.setData('text/plain', 'group:'+groupId); }catch(err){}" +
            "e.stopPropagation();" +
          "});" +
          "header.addEventListener('dragend', function(){" +
            "header.classList.remove('dragging');" +
            "shellDragGroupId=null;" +
            "shellSyncBlockOrderFromDom();" +
          "});" +
          "header.addEventListener('dragover', function(e){" +
            "if(shellDragTabId){ e.preventDefault(); return; }" +
            "if(shellDragGroupId){ shellGroupDragOverHeader(e, groupId); }" +
          "});" +
          "header.addEventListener('drop', function(e){" +
            "e.preventDefault();" +
            "if(shellDragTabId) shellAssignTabToGroup(shellDragTabId, groupId);" +
          "});" +
          "shellGroupHeaderEls[groupId]=header;" +
          "return header;" +
        "}" +
        // Full rebuild of the tab bar's chip order from shellTabs/shellGroups
        // - the single source of truth - rather than hand-patching the DOM,
        // so group headers, collapse state, and member tabs can never drift
        // out of sync with each other. Tab and header DOM nodes themselves
        // are reused (see shellTabEls/shellGroupHeaderEls), only their
        // position and visibility change.
        "function shellRenderTabBar(){" +
          "var tabbar=document.getElementById('tabbar');" +
          "var newTabBtn=document.getElementById('newTabBtn');" +
          "if(!tabbar||!newTabBtn) return;" +
          "Array.prototype.slice.call(tabbar.querySelectorAll('.tab, .tab-group-header')).forEach(function(el){ el.remove(); });" +
          "var renderedGroup=null;" +
          "shellTabs.forEach(function(t){" +
            "if(t.groupId){" +
              "var g=shellGroups.find(function(gr){ return gr.id===t.groupId; });" +
              "if(g && renderedGroup!==g.id){" +
                "var header=shellGroupHeaderEls[g.id]||shellBuildGroupHeader(g.id);" +
                "var count=shellTabs.filter(function(tt){ return tt.groupId===g.id; }).length;" +
                "header.querySelector('.tab-group-name').textContent=g.name;" +
                "header.querySelector('.tab-group-count').textContent=g.collapsed?('('+count+')'):'';" +
                "header.classList.toggle('collapsed', !!g.collapsed);" +
                "tabbar.insertBefore(header, newTabBtn);" +
                "renderedGroup=g.id;" +
              "}" +
            "}" +
            "var el=shellTabEls[t.id];" +
            "if(!el) return;" +
            "var g2=t.groupId?shellGroups.find(function(gr){ return gr.id===t.groupId; }):null;" +
            "el.classList.toggle('grouped', !!t.groupId);" +
            "if(!(g2 && g2.collapsed)){ tabbar.insertBefore(el, newTabBtn); }" +
          "});" +
        "}" +

        // Looks tabs up in shellTabEls rather than document.getElementById
        // because a tab sitting inside a collapsed group is deliberately
        // detached from the bar (not just hidden), so the plain DOM lookup
        // would miss it.
        "function shellSetActiveTab(id){" +
          "shellActiveTabId=id;" +
          "var tabObj=shellTabs.find(function(t){ return t.id===id; });" +
          "if(tabObj && tabObj.groupId){" +
            "var g=shellGroups.find(function(gr){ return gr.id===tabObj.groupId; });" +
            "if(g && g.collapsed){ g.collapsed=false; shellRenderTabBar(); }" +
          "}" +
          "Object.keys(shellTabEls).forEach(function(tid){ shellTabEls[tid].classList.toggle('active', tid===id); });" +
          "document.querySelectorAll('.tabcontent iframe').forEach(function(f){ f.classList.toggle('active', f.id===id+'-frame'); });" +
          "shellSaveState();" +
        "}" +

        "function shellCloseTab(id){" +
          "var idx=shellTabs.findIndex(function(t){ return t.id===id; });" +
          "if(idx===-1) return;" +
          "var closedTab=shellTabs[idx];" +
          // Remember enough to fully restore it: url, title, group
          // membership, and its original position (so "undo" puts it back
          // where it was, not just tacked onto the end). Capped like the
          // undo/redo stack in PageScripts.js, same reasoning - unbounded
          // history for something this cheap to re-derive isn't worth
          // keeping around.
          "var closeId=++shellCloseIdCounter;" +
          "var record={url:closedTab.url, title:closedTab.title, index:idx, groupId:closedTab.groupId||null, closeId:closeId};" +
          "shellClosedTabsStack.push(record);" +
          "if(shellClosedTabsStack.length>SHELL_MAX_CLOSED_TABS){ var dropped=shellClosedTabsStack.shift(); shellRemoveReopenToast(dropped.closeId); }" +
          "shellTabs.splice(idx,1);" +
          "var btn=shellTabEls[id]; if(btn) btn.remove();" +
          "delete shellTabEls[id];" +
          "var frame=document.getElementById(id+'-frame'); if(frame) frame.remove();" +
          "if(closedTab.groupId) shellCleanupEmptyGroup(closedTab.groupId);" +
          "shellShowReopenToast(record);" +

          "if(shellTabs.length===0){ shellRenderTabBar(); openTab('/dashboard','Dashboard'); return; }" +
          "if(shellActiveTabId===id){" +
            "var next=shellTabs[Math.max(0, idx-1)];" +
            "shellSetActiveTab(next.id);" +
          "}" +
          "shellRenderTabBar();" +
          "shellSaveState();" +
        "}" +

        // ---- Undo tab close: a "Closed 'X' · Reopen" toast (same look as
        // the file-ops undo toast in PageScripts.js, but this shell page
        // doesn't load PageScripts.js - see the tab-context-menu comment
        // below for why - so it gets its own small copy), plus the
        // standard browser shortcut Ctrl/Cmd+Shift+T. Reopening restores
        // the tab to its original position (and group) in the bar, not
        // just at the end, and repeated Ctrl+Shift+T walks back through
        // however many tabs were closed, most-recent-first - same LIFO
        // behavior as a real browser's "reopen closed tab". Toasts stack
        // (one per closed tab, each independently dismissible/timed) rather
        // than a single slot overwriting itself, so closing several tabs in
        // a row doesn't silently swallow the earlier ones - each toast's
        // Reopen button is bound to that specific closed tab, not just
        // "whatever's most recent", so clicking an older toast still
        // reopens the right one even if a newer toast is still showing. ----
        "var shellClosedTabsStack=[];" +
        "var SHELL_MAX_CLOSED_TABS=10;" +
        "var shellCloseIdCounter=0;" +
        "var shellToastEls={};" +
        "function shellShowReopenToast(record){" +
          "var container=document.getElementById('shellToastContainer');" +
          "if(!container) return;" +
          "var toast=document.createElement('div');" +
          "toast.className='action-toast open';" +
          "var msg=document.createElement('span');" +
          "msg.className='action-toast-message';" +
          "msg.textContent='Closed \"'+record.title+'\"';" +
          "var btn=document.createElement('button');" +
          "btn.className='action-toast-btn'; btn.textContent='Reopen';" +
          "var closeBtn=document.createElement('button');" +
          "closeBtn.className='action-toast-close'; closeBtn.innerHTML='&times;'; closeBtn.setAttribute('aria-label','Dismiss');" +
          "toast.appendChild(msg); toast.appendChild(btn); toast.appendChild(closeBtn);" +
          "container.appendChild(toast);" +
          "shellToastEls[record.closeId]=toast;" +
          "btn.onclick=function(){ shellReopenSpecific(record.closeId); };" +
          "closeBtn.onclick=function(){ shellRemoveReopenToast(record.closeId); };" +
          "setTimeout(function(){ shellRemoveReopenToast(record.closeId); }, 5000);" +
        "}" +
        "function shellRemoveReopenToast(closeId){" +
          "var t=shellToastEls[closeId];" +
          "if(t && t.parentNode) t.parentNode.removeChild(t);" +
          "delete shellToastEls[closeId];" +
        "}" +
        "function shellDoReopen(record){" +
          "var id='tab-'+(++shellTabCounter);" +
          "var groupId=record.groupId;" +
          "if(groupId && !shellGroups.some(function(g){ return g.id===groupId; })) groupId=null;" +
          "var insertAt=Math.min(record.index, shellTabs.length);" +
          "shellTabs.splice(insertAt, 0, {id:id, url:record.url, title:record.title, groupId:groupId});" +
          "if(groupId) shellNormalizeGroups();" +
          "shellCreateTab(id, record.url, record.title);" +
          "shellSetActiveTab(id);" +
          "shellSaveState();" +
        "}" +
        "function shellReopenClosedTab(){" +
          "if(!shellClosedTabsStack.length) return;" +
          "var closed=shellClosedTabsStack.pop();" +
          "shellDoReopen(closed);" +
          "shellRemoveReopenToast(closed.closeId);" +
        "}" +
        "function shellReopenSpecific(closeId){" +
          "var idx=shellClosedTabsStack.findIndex(function(r){ return r.closeId===closeId; });" +
          "if(idx===-1) return;" +
          "var closed=shellClosedTabsStack[idx];" +
          "shellClosedTabsStack.splice(idx,1);" +
          "shellDoReopen(closed);" +
          "shellRemoveReopenToast(closeId);" +
        "}" +

        // ---- Tab context menu (right-click a tab, or a group header) -
        // "Duplicate" plus group actions, reusing the same
        // .context-menu/.context-menu-item styling PageScripts.js uses for
        // file/folder cards, since this shell page (unlike the tabs' own
        // iframe content) doesn't load PageScripts.js itself and needs its
        // own small copy of the show/hide/position logic. ----
        "function shellMenuItem(label, action){ return '<div class=\"context-menu-item\" data-menu-action=\"'+action+'\">'+label+'</div>'; }" +
        "function showTabContextMenu(x, y, tabId){" +
          "var menu=document.getElementById('tabContextMenu');" +
          "if(!menu) return;" +
          "var tabObj=shellTabs.find(function(t){ return t.id===tabId; });" +
          "var html=shellMenuItem('Duplicate','duplicate-tab');" +
          "html+='<div class=\"context-menu-divider\"></div>';" +
          "if(tabObj && tabObj.groupId){" +
            "html+=shellMenuItem('Rename group','rename-group');" +
            "html+=shellMenuItem('Remove from group','remove-from-group');" +
            "html+=shellMenuItem('Ungroup','ungroup-all');" +
          "}else{" +
            "html+=shellMenuItem('New group','new-group');" +
            "shellGroups.forEach(function(g){" +
              "html+=shellMenuItem('Add to \"'+shellEscapeHtml(g.name)+'\"', 'add-to-group:'+g.id);" +
            "});" +
          "}" +
          "menu.innerHTML=html;" +
          "menu.dataset.tabId=tabId;" +
          "menu.dataset.groupId='';" +
          "menu.classList.add('open');" +
          "var maxX=window.innerWidth-menu.offsetWidth-8, maxY=window.innerHeight-menu.offsetHeight-8;" +
          "menu.style.left=Math.min(x,maxX)+'px';" +
          "menu.style.top=Math.min(y,maxY)+'px';" +
        "}" +
        "function showGroupContextMenu(x, y, groupId){" +
          "var menu=document.getElementById('tabContextMenu');" +
          "if(!menu) return;" +
          "menu.innerHTML=shellMenuItem('Rename group','header-rename-group')+shellMenuItem('Ungroup','header-ungroup-all');" +
          "menu.dataset.tabId='';" +
          "menu.dataset.groupId=groupId;" +
          "menu.classList.add('open');" +
          "var maxX=window.innerWidth-menu.offsetWidth-8, maxY=window.innerHeight-menu.offsetHeight-8;" +
          "menu.style.left=Math.min(x,maxX)+'px';" +
          "menu.style.top=Math.min(y,maxY)+'px';" +
        "}" +
        "function hideTabContextMenu(){" +
          "var menu=document.getElementById('tabContextMenu');" +
          "if(menu) menu.classList.remove('open');" +
        "}" +
        // Opens a brand-new tab pointed at the same URL (and joins the same
        // group, if any) - the shared "already-open URL just gets focused"
        // shortcut inside openTab() is deliberately bypassed here (a
        // straight openTab() call would just refocus the original instead
        // of duplicating it), since duplicating is the whole point of this
        // menu item.
        "function duplicateTab(tabId){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===tabId; });" +
          "if(!tabObj) return;" +
          "var id='tab-'+(++shellTabCounter);" +
          "shellTabs.push({id:id, url:tabObj.url, title:tabObj.title, groupId:tabObj.groupId||null});" +
          "if(tabObj.groupId) shellNormalizeGroups();" +
          "shellCreateTab(id, tabObj.url, tabObj.title);" +
          "shellSetActiveTab(id);" +
          "shellSaveState();" +
        "}" +
        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('#tabContextMenu .context-menu-item');" +
          "if(item){" +
            "var menu=document.getElementById('tabContextMenu');" +
            "var action=item.dataset.menuAction;" +
            "var tabId=menu.dataset.tabId;" +
            "var groupId=menu.dataset.groupId;" +
            "if(action==='duplicate-tab'){ duplicateTab(tabId); }" +
            "else if(action==='new-group'){ shellCreateGroupWithTab(tabId); }" +
            "else if(action==='rename-group'){ var t1=shellTabs.find(function(x){return x.id===tabId;}); if(t1&&t1.groupId) shellRenameGroup(t1.groupId); }" +
            "else if(action==='remove-from-group'){ shellRemoveTabFromGroup(tabId); }" +
            "else if(action==='ungroup-all'){ var t2=shellTabs.find(function(x){return x.id===tabId;}); if(t2&&t2.groupId) shellUngroupAll(t2.groupId); }" +
            "else if(action==='header-rename-group'){ shellRenameGroup(groupId); }" +
            "else if(action==='header-ungroup-all'){ shellUngroupAll(groupId); }" +
            "else if(action.indexOf('add-to-group:')===0){ shellAssignTabToGroup(tabId, action.substring('add-to-group:'.length)); }" +
            "hideTabContextMenu();" +
            "return;" +
          "}" +
          "if(!e.target.closest('#tabContextMenu')) hideTabContextMenu();" +
        "});" +
        "window.addEventListener('blur', hideTabContextMenu);" +

        // ---- Address bar (press "/" to open, type/click to browse, Enter to go) ----
        // In the Google Drive session, this branches into a different mode
        // (see the shellSessionId===GDRIVE_SESSION_ID checks below): Drive
        // items don't have one true hierarchical path the way local files
        // do (see GDriveBrowseHandler.java's class comment), so rather than
        // faking a path picker, typing here live-searches Drive by name
        // (via /gdrive-suggest) and picking a folder jumps straight to it;
        // hitting Enter with nothing picked runs a full /gdrive-search
        // instead of a folder navigation.
        "function openAddressBar(){" +
          "var overlay=document.getElementById('addressBarOverlay');" +
          "var input=document.getElementById('addressBarInput');" +
          "var prefill='';" +
          "if(shellSessionId!==GDRIVE_SESSION_ID){" +
            "var activeTab=shellTabs.find(function(t){ return t.id===shellActiveTabId; });" +
            "if(activeTab && activeTab.url.indexOf('/browse?path=')===0){" +
              "prefill=decodeURIComponent(activeTab.url.substring('/browse?path='.length));" +
            "}" +
          "}" +
          "input.placeholder=(shellSessionId===GDRIVE_SESSION_ID)?'Search Google Drive by name...':'';" +
          "input.value=prefill;" +
          "overlay.classList.add('open');" +
          "input.focus();" +
          "input.select();" +
          "updateAddressSuggestions();" +
        "}" +
        "function closeAddressBar(){" +
          "document.getElementById('addressBarOverlay').classList.remove('open');" +
          "document.getElementById('addressBarInput').blur();" +
          "var box=document.getElementById('addressBarSuggestions');" +
          "box.innerHTML=''; box.classList.remove('open');" +
        "}" +

        // Strips the root's own absolute path if someone pasted a full path
        // (e.g. "C:/Users/You/Documents" when root is "C:/Users/You"), and
        // normalizes backslashes/leading slashes. Shared by both live
        // suggestions and final Enter-navigation, so they can never disagree
        // about what a given typed string actually means.
        "function normalizeAddressBarInput(raw){" +
          "var normalized=raw.replace(/\\\\/g,'/').trim();" +
          "var rootNormalized=SHELL_ROOT_ABS.replace(/\\\\/g,'/');" +
          "if(normalized.toLowerCase().indexOf(rootNormalized.toLowerCase())===0){" +
            "normalized=normalized.substring(rootNormalized.length);" +
          "}" +
          "while(normalized.charAt(0)==='/') normalized=normalized.substring(1);" +
          "return normalized;" +
        "}" +

        "function goToAddressBarPath(){" +
          "if(shellSessionId===GDRIVE_SESSION_ID){" +
            "var q=document.getElementById('addressBarInput').value.trim();" +
            "closeAddressBar();" +
            "if(q){ navigateCurrentTab('/gdrive-search?q='+encodeURIComponent(q)); }" +
            "return;" +
          "}" +
          "var normalized=normalizeAddressBarInput(document.getElementById('addressBarInput').value);" +
          "while(normalized.endsWith('/')) normalized=normalized.slice(0,-1);" +
          "closeAddressBar();" +
          "navigateCurrentTab('/browse?path='+encodeURIComponent(normalized));" +
        "}" +

        // Drive mode: jump straight into a specific folder found by name
        // search - see the class-level comment on this whole address bar
        // section for why this doesn't try to build a multi-level path.
        "function goToDriveAddressItem(id, name){" +
          "closeAddressBar();" +
          "navigateCurrentTab('/gdrive?path='+encodeURIComponent(id)+'%7C'+encodeURIComponent(name));" +
        "}" +

        // Splits the current input into "the folder we're listing" (up to
        // the last slash) and "what's typed after it" (used as a filter
        // prefix), fetches that folder's real subfolders, and renders
        // whichever ones match. Works whether the input ends in a slash
        // (show everything in that folder) or not (filter-as-you-type).
        "var addressSuggestDebounce=null;" +
        "var addressActiveIndex=-1;" +
        "function updateAddressSuggestions(){" +
          "clearTimeout(addressSuggestDebounce);" +
          "addressSuggestDebounce=setTimeout(function(){" +
            "if(shellSessionId===GDRIVE_SESSION_ID){" +
              "var q=document.getElementById('addressBarInput').value.trim();" +
              "if(!q){ renderAddressSuggestions([], ''); return; }" +
              "fetch('/gdrive-suggest?q='+encodeURIComponent(q)+'&foldersOnly=1').then(function(r){return r.json();})" +
                ".then(function(items){ renderDriveAddressSuggestions(items); })" +
                ".catch(function(){ renderDriveAddressSuggestions([]); });" +
              "return;" +
            "}" +
            "var normalized=normalizeAddressBarInput(document.getElementById('addressBarInput').value);" +
            "var lastSlash=normalized.lastIndexOf('/');" +
            "var dirPart=lastSlash===-1?'':normalized.substring(0,lastSlash);" +
            "var partial=lastSlash===-1?normalized:normalized.substring(lastSlash+1);" +
            "fetch('/subfolders?path='+encodeURIComponent(dirPart)).then(function(r){return r.json();}).then(function(folders){" +
              "var filtered=folders.filter(function(f){" +
                "return f.name.toLowerCase().indexOf(partial.toLowerCase())===0;" +
              "});" +
              "renderAddressSuggestions(filtered, dirPart);" +
            "}).catch(function(){ renderAddressSuggestions([], dirPart); });" +
          "}, 120);" +
        "}" +
        "function renderAddressSuggestions(folders, dirPart){" +
          "var box=document.getElementById('addressBarSuggestions');" +
          "addressActiveIndex=-1;" +
          "if(!folders.length){ box.innerHTML=''; box.classList.remove('open'); return; }" +
          "box.innerHTML=folders.map(function(f){" +
            "var full=dirPart?dirPart+'/'+f.name:f.name;" +
            "var p=full.replace(/\"/g,'&quot;');" +
            "return '<div class=\"address-suggestion-item\" data-address-path=\"'+p+'\">&#128193; '+f.name+'</div>';" +
          "}).join('');" +
          "box.classList.add('open');" +
        "}" +
        // Drive mode: each result carries its own Drive id (needed to
        // navigate straight to it - see goToDriveAddressItem() above) and
        // shows a file icon rather than always a folder one, since a
        // name search naturally turns up files too even though only
        // folders get the foldersOnly=1 filter here... actually foldersOnly
        // is passed, so these are always folders, but the icon stays
        // explicit rather than assumed in case that filter is ever relaxed.
        "function renderDriveAddressSuggestions(items){" +
          "var box=document.getElementById('addressBarSuggestions');" +
          "addressActiveIndex=-1;" +
          "if(!items.length){ box.innerHTML=''; box.classList.remove('open'); return; }" +
          "box.innerHTML=items.map(function(it){" +
            "var idAttr=String(it.id).replace(/\"/g,'&quot;');" +
            "var nameAttr=String(it.name).replace(/\"/g,'&quot;');" +
            "return '<div class=\"address-suggestion-item\" data-address-drive-id=\"'+idAttr+'\" data-address-drive-name=\"'+nameAttr+'\">'+" +
              "(it.type==='folder'?'&#128193; ':'&#128196; ')+PathUtil_escapeHtmlJs(it.name)+'</div>';" +
          "}).join('');" +
          "box.classList.add('open');" +
        "}" +
        "function PathUtil_escapeHtmlJs(s){" +
          "var d=document.createElement('div'); d.textContent=String(s); return d.innerHTML;" +
        "}" +
        // Highlights whichever suggestion Up/Down has landed on and keeps
        // it scrolled into view, so navigating a long folder list by
        // keyboard behaves like any native dropdown/autocomplete.
        "function updateAddressActiveHighlight(items){" +
          "items.forEach(function(it,i){ it.classList.toggle('active', i===addressActiveIndex); });" +
          "if(items[addressActiveIndex]) items[addressActiveIndex].scrollIntoView({block:'nearest'});" +
        "}" +

        "document.addEventListener('keydown', function(e){" +
          "if(e.key==='Escape'){ hideTabContextMenu(); }" +
          "if((e.ctrlKey||e.metaKey) && e.shiftKey && (e.key==='T'||e.key==='t')){" +
            "e.preventDefault();" +
            "shellReopenClosedTab();" +
          "}" +
          "if(e.key==='/' && document.activeElement.tagName!=='INPUT' && document.activeElement.tagName!=='TEXTAREA'){" +
            "e.preventDefault();" +
            "openAddressBar();" +
          "}" +
        "});" +
        "document.getElementById('addressBarInput') && document.getElementById('addressBarInput').addEventListener('keydown', function(e){" +
          "var box=document.getElementById('addressBarSuggestions');" +
          "var items=box.classList.contains('open')?Array.prototype.slice.call(box.querySelectorAll('.address-suggestion-item')):[];" +
          "if(e.key==='ArrowDown'){" +
            "if(items.length){ e.preventDefault(); addressActiveIndex=(addressActiveIndex+1)%items.length; updateAddressActiveHighlight(items); }" +
          "}else if(e.key==='ArrowUp'){" +
            "if(items.length){ e.preventDefault(); addressActiveIndex=(addressActiveIndex-1+items.length)%items.length; updateAddressActiveHighlight(items); }" +
          "}else if(e.key==='Enter'){" +
            "if(items.length && addressActiveIndex>-1){ e.preventDefault(); items[addressActiveIndex].click(); }" +
            "else{ goToAddressBarPath(); }" +
          "}else if(e.key==='Escape'){ closeAddressBar(); }" +
          "e.stopPropagation();" +
        "});" +
        "document.getElementById('addressBarInput') && document.getElementById('addressBarInput').addEventListener('input', function(){" +
          "updateAddressSuggestions();" +
        "});" +
        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('.address-suggestion-item');" +
          "if(!item) return;" +
          "if(item.dataset.addressDriveId!==undefined){" +
            "goToDriveAddressItem(item.dataset.addressDriveId, item.dataset.addressDriveName);" +
            "return;" +
          "}" +
          "var input=document.getElementById('addressBarInput');" +
          "input.value='/'+item.dataset.addressPath+'/';" +
          "input.focus();" +
          "updateAddressSuggestions();" +
        "});" +

        "document.addEventListener('DOMContentLoaded', function(){" +
          "if(!shellLoadState()){ openTab('/dashboard','Dashboard'); }" +
          "shellApplyDriveSidebar(shellSessionId===GDRIVE_SESSION_ID);" +
        "});" +
        "</script>";
}
