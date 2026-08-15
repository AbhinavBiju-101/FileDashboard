/**
 * Tab manager for the app shell (the "/" page). Each tab is one iframe
 * pointed at a content route (/dashboard, /browse?path=..., /search?...).
 * Tab labels are picked up automatically from each iframe's <title> after
 * it loads - so navigating inside a tab (e.g. clicking into a subfolder)
 * updates that tab's name on its own, no extra plumbing needed on the
 * content-page side.
 *
 * Open tabs are remembered in sessionStorage so a shell page reload doesn't
 * lose them (but a fresh browser session starts clean, which feels right
 * for "windows/tabs" rather than permanent bookmarks).
 */
public class ShellScript {

    public static final String SCRIPT =
        "<script>" +
        "var shellTabs=[];" +
        "var shellActiveTabId=null;" +
        "var shellTabCounter=0;" +

        "function shellSaveState(){" +
          "try{ sessionStorage.setItem('fileDashboardTabs', JSON.stringify({tabs:shellTabs, active:shellActiveTabId})); }catch(e){}" +
        "}" +

        "function shellLoadState(){" +
          "try{" +
            "var raw=sessionStorage.getItem('fileDashboardTabs');" +
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
              "shellUpdateTabTitle(id, newTitle);" +
            "}catch(e){}" +
          "};" +
          "document.getElementById('tabcontent').appendChild(iframe);" +
        "}" +

        "function shellUpdateTabTitle(id, newTitle){" +
          "var tabObj=shellTabs.find(function(t){ return t.id===id; });" +
          "if(tabObj) tabObj.title=newTitle;" +
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

        "document.addEventListener('DOMContentLoaded', function(){" +
          "if(!shellLoadState()){ openTab('/dashboard','Dashboard'); }" +
        "});" +
        "</script>";
}
