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
        "var shellDragTabId=null;" +

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

          // Drag-to-reorder: dragover moves the dragged tab's DOM node live
          // (classic sortable-list trick), so the person sees tabs shuffle
          // as they drag rather than only on drop. shellTabs (the array
          // that actually gets persisted) is re-synced from the resulting
          // DOM order once the drag ends, whatever element it ends over.
          "btn.draggable=true;" +
          "btn.addEventListener('dragstart', function(e){" +
            "shellDragTabId=id;" +
            "btn.classList.add('dragging');" +
            "e.dataTransfer.effectAllowed='move';" +
            "try{ e.dataTransfer.setData('text/plain', id); }catch(err){}" +
          "});" +
          "btn.addEventListener('dragover', function(e){ shellTabDragOver(e, id); });" +
          "btn.addEventListener('dragend', function(){" +
            "btn.classList.remove('dragging');" +
            "shellDragTabId=null;" +
            "shellSyncTabOrderFromDom();" +
          "});" +

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

        "function shellTabDragOver(e, overId){" +
          "e.preventDefault();" +
          "if(!shellDragTabId || shellDragTabId===overId) return;" +
          "var dragging=document.getElementById(shellDragTabId);" +
          "var over=document.getElementById(overId);" +
          "if(!dragging||!over) return;" +
          "var rect=over.getBoundingClientRect();" +
          "var before=(e.clientX-rect.left)<rect.width/2;" +
          "var tabbar=document.getElementById('tabbar');" +
          "tabbar.insertBefore(dragging, before?over:over.nextSibling);" +
        "}" +

        // Reorders shellTabs (the array that gets persisted to
        // localStorage) to match whatever order the tab elements ended up
        // in after dragging, so a reload restores tabs in the new order.
        "function shellSyncTabOrderFromDom(){" +
          "var order=Array.prototype.map.call(document.querySelectorAll('#tabbar .tab'), function(el){ return el.id; });" +
          "shellTabs.sort(function(a,b){ return order.indexOf(a.id)-order.indexOf(b.id); });" +
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

        // ---- Address bar (press "/" to open, type/click to browse, Enter to go) ----
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
          "var normalized=normalizeAddressBarInput(document.getElementById('addressBarInput').value);" +
          "while(normalized.endsWith('/')) normalized=normalized.slice(0,-1);" +
          "closeAddressBar();" +
          "navigateCurrentTab('/browse?path='+encodeURIComponent(normalized));" +
        "}" +

        // Splits the current input into "the folder we're listing" (up to
        // the last slash) and "what's typed after it" (used as a filter
        // prefix), fetches that folder's real subfolders, and renders
        // whichever ones match. Works whether the input ends in a slash
        // (show everything in that folder) or not (filter-as-you-type).
        "var addressSuggestDebounce=null;" +
        "function updateAddressSuggestions(){" +
          "clearTimeout(addressSuggestDebounce);" +
          "addressSuggestDebounce=setTimeout(function(){" +
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
          "if(!folders.length){ box.innerHTML=''; box.classList.remove('open'); return; }" +
          "box.innerHTML=folders.map(function(f){" +
            "var full=dirPart?dirPart+'/'+f.name:f.name;" +
            "var p=full.replace(/\"/g,'&quot;');" +
            "return '<div class=\"address-suggestion-item\" data-address-path=\"'+p+'\">&#128193; '+f.name+'</div>';" +
          "}).join('');" +
          "box.classList.add('open');" +
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
        "document.getElementById('addressBarInput') && document.getElementById('addressBarInput').addEventListener('input', function(){" +
          "updateAddressSuggestions();" +
        "});" +
        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('.address-suggestion-item');" +
          "if(!item) return;" +
          "var input=document.getElementById('addressBarInput');" +
          "input.value='/'+item.dataset.addressPath+'/';" +
          "input.focus();" +
          "updateAddressSuggestions();" +
        "});" +

        "document.addEventListener('DOMContentLoaded', function(){" +
          "if(!shellLoadState()){ openTab('/dashboard','Dashboard'); }" +
        "});" +
        "</script>";
}
