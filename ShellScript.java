/**
 * Tab manager for the app shell (the "/" page). Each tab is one iframe
 * pointed at a content route (/dashboard, /browse?path=..., /search?...).
 * Tab labels are picked up automatically from each iframe's <title> after
 * it loads - so navigating inside a tab (e.g. clicking into a subfolder)
 * updates that tab's name on its own, no extra plumbing needed on the
 * content-page side.
 *
 * Open tabs are remembered in localStorage so a page reload (or even
 * closing and reopening the browser) doesn't lose them.
 */
public class ShellScript {

    public static final String SCRIPT =
        "<script>" +
        "var shellTabs=[];" +
        "var shellActiveTabId=null;" +
        "var shellTabCounter=0;" +

        "function shellSaveState(){" +
          "try{ localStorage.setItem('fileDashboardTabs', JSON.stringify({tabs:shellTabs, active:shellActiveTabId})); }catch(e){}" +
        "}" +

        "function shellLoadState(){" +
          "try{" +
            "var raw=localStorage.getItem('fileDashboardTabs');" +
            "if(!raw) return false;" +
            "var state=JSON.parse(raw);" +
            "if(!state.tabs||!state.tabs.length) return false;" +
            "state.tabs.forEach(function(t){ shellCreateTabElement(t.id,t.url,t.title); });" +
            "shellTabs=state.tabs;" +
            "shellTabCounter=shellTabs.reduce(function(m,t){" +
              "var n=parseInt(t.id.replace('tab-',''),10); return isNaN(n)?m:Math.max(m,n);" +
            "},0);" +
            "shellSetActiveTab(state.active && shellTabs.some(function(t){return t.id===state.active;}) ? state.active : shellTabs[0].id);" +
            "return true;" +
          "}catch(e){ return false; }" +
        "}" +

        "function openTab(url, fallbackLabel){" +
          "var existing=shellTabs.find(function(t){ return t.url===url; });" +
          "if(existing){ shellSetActiveTab(existing.id); return false; }" +
          "var id='tab-'+(++shellTabCounter);" +
          "shellTabs.push({id:id, url:url, title:fallbackLabel||'Loading...'});" +
          "shellCreateTabElement(id, url, fallbackLabel||'Loading...');" +
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
          "var btn=document.getElementById(shellActiveTabId);" +
          "if(btn){ var span=btn.querySelector('.tab-title'); if(span) span.textContent='Loading...'; }" +
          "shellSaveState();" +
          "return false;" +
        "}" +

        "function shellCreateTabElement(id, url, title){" +
          "var tabbar=document.getElementById('tabbar');" +
          "var newTabBtn=document.getElementById('newTabBtn');" +
          "var btn=document.createElement('div');" +
          "btn.className='tab'; btn.id=id;" +
          "var titleSpan=document.createElement('span');" +
          "titleSpan.className='tab-title'; titleSpan.textContent=title;" +
          "titleSpan.onclick=function(){ shellSetActiveTab(id); };" +
          "var closeSpan=document.createElement('span');" +
          "closeSpan.className='tab-close'; closeSpan.innerHTML='&times;';" +
          "closeSpan.onclick=function(e){ e.stopPropagation(); shellCloseTab(id); };" +
          "btn.appendChild(titleSpan); btn.appendChild(closeSpan);" +
          "tabbar.insertBefore(btn, newTabBtn);" +

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
        "}" +

        // Keeps both title AND url in sync with wherever the tab actually
        // navigated to - not just when navigateCurrentTab() was called, but
        // also when navigation happened from a normal link click inside the
        // tab's own content (e.g. clicking into a subfolder). Otherwise a
        // restored tab would jump back to a stale starting location.
        "function shellUpdateTab(id, newTitle, newUrl){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===id; });" +
          "if(tabObj){ tabObj.title=newTitle; if(newUrl) tabObj.url=newUrl; }" +
          "var btn=document.getElementById(id);" +
          "if(btn){ var span=btn.querySelector('.tab-title'); if(span) span.textContent=newTitle; }" +
          "shellSaveState();" +
        "}" +

        "function shellSetActiveTab(id){" +
          "shellActiveTabId=id;" +
          "document.querySelectorAll('.tab').forEach(function(t){ t.classList.toggle('active', t.id===id); });" +
          "document.querySelectorAll('.tabcontent iframe').forEach(function(f){ f.classList.toggle('active', f.id===id+'-frame'); });" +
          "shellSaveState();" +
        "}" +

        "function shellCloseTab(id){" +
          "var idx=shellTabs.findIndex(function(t){ return t.id===id; });" +
          "if(idx===-1) return;" +
          "shellTabs.splice(idx,1);" +
          "var btn=document.getElementById(id); if(btn) btn.remove();" +
          "var frame=document.getElementById(id+'-frame'); if(frame) frame.remove();" +

          "if(shellTabs.length===0){ openTab('/dashboard','Dashboard'); return; }" +
          "if(shellActiveTabId===id){" +
            "var next=shellTabs[Math.max(0, idx-1)];" +
            "shellSetActiveTab(next.id);" +
          "}" +
          "shellSaveState();" +
        "}" +

        // ---- Address bar (press "/" to open, type a path, Enter to go) ----
        "function openAddressBar(){" +
          "var overlay=document.getElementById('addressBarOverlay');" +
          "var input=document.getElementById('addressBarInput');" +
          "var prefill='';" +
          "var activeTab=shellTabs.find(function(t){ return t.id===shellActiveTabId; });" +
          "if(activeTab && activeTab.url.indexOf('/browse?path=')===0){" +
            "prefill=decodeURIComponent(activeTab.url.substring('/browse?path='.length));" +
          "}" +
          "input.value=prefill;" +
          "overlay.classList.add('open');" +
          "input.focus();" +
          "input.select();" +
        "}" +
        "function closeAddressBar(){" +
          "document.getElementById('addressBarOverlay').classList.remove('open');" +
          "document.getElementById('addressBarInput').blur();" +
        "}" +
        "function goToAddressBarPath(){" +
          "var raw=document.getElementById('addressBarInput').value;" +
          "var normalized=raw.replace(/\\\\/g,'/').trim();" +
          "var rootNormalized=SHELL_ROOT_ABS.replace(/\\\\/g,'/');" +
          // Strip the root's own absolute path if someone pasted a full path
          // (e.g. "C:/Users/You/Documents" when root is "C:/Users/You").
          "if(normalized.toLowerCase().indexOf(rootNormalized.toLowerCase())===0){" +
            "normalized=normalized.substring(rootNormalized.length);" +
          "}" +
          "while(normalized.charAt(0)==='/') normalized=normalized.substring(1);" +
          "closeAddressBar();" +
          "navigateCurrentTab('/browse?path='+encodeURIComponent(normalized));" +
        "}" +
        "document.addEventListener('keydown', function(e){" +
          "if(e.key==='/' && document.activeElement.tagName!=='INPUT' && document.activeElement.tagName!=='TEXTAREA'){" +
            "e.preventDefault();" +
            "openAddressBar();" +
          "}" +
        "});" +
        "document.getElementById('addressBarInput') && document.getElementById('addressBarInput').addEventListener('keydown', function(e){" +
          "if(e.key==='Enter'){ goToAddressBarPath(); }" +
          "else if(e.key==='Escape'){ closeAddressBar(); }" +
          "e.stopPropagation();" +
        "});" +

        "document.addEventListener('DOMContentLoaded', function(){" +
          "if(!shellLoadState()){ openTab('/dashboard','Dashboard'); }" +
        "});" +
        "</script>";
}
