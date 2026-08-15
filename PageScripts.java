/**
 * Everything client-side that's shared across content pages (Dashboard,
 * Browse, Search, Recycle Bin): the in-app preview modal, the selection
 * model (click/ctrl-click/shift-click/double-click), the right-click
 * context menu, the "Move to..." folder picker, type-filter chips, and
 * new-folder/rename/duplicate/move/delete actions. Included once per page
 * via MODAL_HTML + SCRIPT.
 */
public class PageScripts {

    public static final String CODE_HIGHLIGHT_RESOURCES =
        "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css'>" +
        "<script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js'></script>";

    // mammoth.js's browser build converts .docx bytes to HTML entirely
    // client-side (no server-side library, no build tool - consistent with
    // the rest of this app). Loaded unconditionally alongside the code
    // highlighter above, same trade-off: a small always-present CDN
    // include rather than trying to load it only when a docx is actually
    // opened.
    public static final String DOCX_RESOURCES =
        "<script src='https://cdnjs.cloudflare.com/ajax/libs/mammoth/1.6.0/mammoth.browser.min.js'></script>";

    public static final String MODAL_HTML =
        CODE_HIGHLIGHT_RESOURCES +
        DOCX_RESOURCES +
        "<div id='previewOverlay' class='preview-overlay' onclick=\"if(event.target===this) closePreview();\">" +
        "<button id='previewNavLeft' class='preview-nav-btn preview-nav-left' onclick='navigatePreview(-1)' aria-label='Previous file'>&#10094;</button>" +
        "<button id='previewNavRight' class='preview-nav-btn preview-nav-right' onclick='navigatePreview(1)' aria-label='Next file'>&#10095;</button>" +
        "<div class='preview-box'>" +
        "<div class='preview-header'>" +
        "<span id='previewTitle' class='preview-title'></span>" +
        "<div class='preview-header-actions'>" +
        "<span id='previewExtraAction'></span>" +
        "<span id='previewViewerAction'></span>" +
        "<a id='previewOpenFolderLink' href=\"#\" onclick=\"openPreviewFolder(); return false;\" class='preview-download'>Open folder</a>" +
        "<a id='previewDownloadLink' href='#' class='preview-download'>Download</a>" +
        "<button class='preview-close' onclick='closePreview()' aria-label='Close'>&times;</button>" +
        "</div></div>" +
        "<div id='previewBody' class='preview-body'></div>" +
        "</div></div>" +

        "<div id='contextMenu' class='context-menu'></div>" +

        "<div id='moveModalOverlay' class='move-modal-overlay' onclick=\"if(event.target===this) closeMoveModal();\">" +
        "<div class='move-modal-box'>" +
        "<h3>Move to...</h3>" +
        "<div id='moveBreadcrumb' class='move-breadcrumb'></div>" +
        "<div id='moveFolderList' class='move-folder-list'></div>" +
        "<div class='move-modal-actions'>" +
        "<button onclick='closeMoveModal()'>Cancel</button>" +
        "<button onclick='confirmMoveHere()' class='move-confirm'>Move here</button>" +
        "</div></div></div>" +

        "<div id='actionToastContainer' class='action-toast-container'></div>";

    public static final String SCRIPT =
        "<script>" +

        // ---- Preview modal ----
        "var PREVIEW_IMAGE_EXTS=['jpg','jpeg','png','gif','bmp','webp','svg','ico'];" +
        "var PREVIEW_AUDIO_EXTS=['mp3','wav','ogg','m4a','flac','aac'];" +
        "var PREVIEW_VIDEO_EXTS=['mp4','webm','mov','m4v'];" +
        "var PREVIEW_TEXT_EXTS=['txt','md','csv','json','xml','log','html','htm','css','js','ts','java','py','c','cpp','h','hpp','sh','yml','yaml','ini','conf','properties'];" +

        // trashId/trashSub are only present when previewing a file found
        // while browsing *inside* a trashed folder (see
        // TrashBrowseHandler) - everywhere else they're omitted/undefined,
        // and every trash-aware bit below just falls back to the normal
        // /file?path=... behavior.
        "var currentPreviewPath=null;" +
        "var currentPreviewTrashId=null;" +
        "var currentPreviewTrashSub='';" +
        "function openPreview(path, name, ext, viewable, textlike, trashId, trashSub){" +
          "currentPreviewPath=path;" +
          "currentPreviewTrashId=trashId||null;" +
          "currentPreviewTrashSub=trashSub||'';" +
          "var overlay=document.getElementById('previewOverlay');" +
          "var body=document.getElementById('previewBody');" +
          "document.getElementById('previewTitle').textContent=name;" +
          "var downloadUrl=currentPreviewTrashId?" +
            "('/trash-file?id='+encodeURIComponent(currentPreviewTrashId)+'&sub='+encodeURIComponent(currentPreviewTrashSub)+'&mode=download'):" +
            "('/file?path='+encodeURIComponent(path)+'&mode=download');" +
          "document.getElementById('previewDownloadLink').href=downloadUrl;" +
          "document.getElementById('previewExtraAction').innerHTML='';" +
          "updatePreviewNavVisibility();" +
          // Same file types the right-click "Open Viewer" menu item offers
          // (pdf, or anything text-like) - the viewer's dedicated reading
          // mode only really has something extra to offer for those.
          "var viewerAction=document.getElementById('previewViewerAction');" +
          "if(ext==='pdf'||textlike){" +
            "viewerAction.innerHTML='<a href=\"#\" onclick=\"openPreviewInViewer(); return false;\" class=\"preview-download\">Open in Viewer</a>';" +
          "}else{ viewerAction.innerHTML=''; }" +
          "var fileUrl=currentPreviewTrashId?" +
            "('/trash-file?id='+encodeURIComponent(currentPreviewTrashId)+'&sub='+encodeURIComponent(currentPreviewTrashSub)+'&mode=view'):" +
            "('/file?path='+encodeURIComponent(path)+'&mode=view');" +
          "body.innerHTML='';" +

          "if(!viewable){" +
            "body.innerHTML=\"<div class='preview-nopreview'><p>There's no in-browser preview for this file type.</p></div>\";" +
            "overlay.classList.add('open'); return;" +
          "}" +

          "if(PREVIEW_IMAGE_EXTS.indexOf(ext)!==-1){" +
            "body.innerHTML='<img src=\"'+fileUrl+'\" alt=\"\">';" +
          "}else if(ext==='pdf'){" +
            "body.innerHTML='<iframe src=\"'+fileUrl+'\"></iframe>';" +
            "document.getElementById('previewExtraAction').innerHTML=" +
              "'<a href=\"'+fileUrl+'\" target=\"_blank\" class=\"preview-download\">Edit \\u2197</a>';" +
          "}else if(PREVIEW_AUDIO_EXTS.indexOf(ext)!==-1){" +
            "body.innerHTML='<audio controls autoplay src=\"'+fileUrl+'\"></audio>';" +
          "}else if(PREVIEW_VIDEO_EXTS.indexOf(ext)!==-1){" +
            "body.innerHTML='<video controls autoplay src=\"'+fileUrl+'\"></video>';" +
          "}else if(ext==='docx'){" +
            // Rendered entirely client-side via mammoth.js (loaded
            // unconditionally - see DOCX_RESOURCES). If that fails for any
            // reason (corrupt file, an unsupported .docx feature, or the
            // CDN script not loading), fall back to the same "no preview,
            // just download it" message every other non-viewable type
            // already shows, rather than a dead loading spinner.
            "body.innerHTML='<div class=\"docx-loading\">Loading document...</div>';" +
            "fetch(fileUrl).then(function(r){return r.arrayBuffer();}).then(function(buf){" +
              "if(!window.mammoth) throw new Error('renderer unavailable');" +
              "return mammoth.convertToHtml({arrayBuffer:buf});" +
            "}).then(function(result){" +
              "body.innerHTML='<div class=\"docx-preview\">'+result.value+'</div>';" +
            "}).catch(function(){" +
              "body.innerHTML='<div class=\"preview-nopreview\"><p>Couldn\\'t render a preview for this document.</p>" +
                "<p><a href=\"'+downloadUrl+'\" class=\"preview-download\">Download instead</a></p></div>';" +
            "});" +
          "}else{" +
            "body.innerHTML='<pre>Loading...</pre>';" +
            "fetch(fileUrl).then(function(r){return r.text();}).then(function(text){" +
              "var d=document.createElement('div'); d.textContent=text; var esc=d.innerHTML;" +
              "if(['txt','log','csv','md'].indexOf(ext)!==-1){" +
                "var pre=document.createElement('pre'); pre.textContent=text;" +
                "body.innerHTML=''; body.appendChild(pre);" +
              "}else{" +
                "var lang=CODE_LANG_MAP[ext]||'';" +
                "var lc=lang?' class=\"language-'+lang+'\"':'';" +
                "body.innerHTML='<pre class=\"code-highlighted\"><code id=\"previewCodeBlock\"'+lc+'>'+esc+'</code></pre>'+" +
                  "'<pre class=\"code-raw plain-text\" style=\"display:none;\">'+esc+'</pre>';" +
                "if(window.hljs){ hljs.highlightElement(document.getElementById('previewCodeBlock')); }" +
                "document.getElementById('previewExtraAction').innerHTML=" +
                  "'<a href=\"#\" onclick=\"toggleModalCodeView(); return false;\" id=\"modalToggleRawBtn\" class=\"preview-download\">Raw text</a>';" +
              "}" +
            "});" +
          "}" +
          "overlay.classList.add('open');" +
        "}" +
        "var CODE_LANG_MAP={java:'java',py:'python',c:'c',cpp:'cpp',h:'cpp',hpp:'cpp',js:'javascript',ts:'typescript',html:'xml',htm:'xml',css:'css',json:'json',xml:'xml',yml:'yaml',yaml:'yaml',sh:'bash',ini:'ini',conf:'ini',properties:'properties'};" +
        "function toggleModalCodeView(){" +
          "var h=document.querySelector('#previewBody .code-highlighted'), r=document.querySelector('#previewBody .code-raw'), b=document.getElementById('modalToggleRawBtn');" +
          "if(!h||!r) return;" +
          "var showingRaw=r.style.display!=='none';" +
          "if(showingRaw){ r.style.display='none'; h.style.display=''; b.textContent='Raw text'; }" +
          "else{ r.style.display=''; h.style.display='none'; b.textContent='Formatted'; }" +
        "}" +
        "function closePreview(){" +
          "document.getElementById('previewOverlay').classList.remove('open');" +
          "document.getElementById('previewBody').innerHTML='';" +
          "currentPreviewPath=null;" +
          "currentPreviewTrashId=null;" +
          "currentPreviewTrashSub='';" +
        "}" +
        // Hands off the file currently open in the quick-preview modal to
        // the full Viewer tab (same destination as the right-click "Open
        // Viewer" menu item), then closes the modal since the person is
        // continuing to look at the same file, just in a bigger view.
        "function openPreviewInViewer(){" +
          "if(!currentPreviewPath && !currentPreviewTrashId) return;" +
          "var name=document.getElementById('previewTitle').textContent;" +
          "var viewerUrl=currentPreviewTrashId?" +
            "('/viewer?trashId='+encodeURIComponent(currentPreviewTrashId)+'&trashSub='+encodeURIComponent(currentPreviewTrashSub)):" +
            "('/viewer?path='+encodeURIComponent(currentPreviewPath));" +
          "closePreview();" +
          "if(window.parent && window.parent.openTab){ window.parent.openTab(viewerUrl, name); }" +
          "else{ window.open(viewerUrl, '_blank'); }" +
        "}" +
        // "Open folder" - jumps to wherever the file currently open in the
        // preview modal actually lives, so previewing a hit from search
        // (or from the recycle bin) doesn't leave someone wondering where
        // it is. Navigates the shell's current tab when embedded in the
        // app frame (same pattern as the sidebar/brand links), otherwise
        // just changes this page's own location.
        "function openPreviewFolder(){" +
          "var url;" +
          "if(currentPreviewTrashId){" +
            "var sub=currentPreviewTrashSub||'';" +
            "var lastSlash=sub.lastIndexOf('/');" +
            "var parentSub=lastSlash===-1?'':sub.substring(0,lastSlash);" +
            "url='/trash-browse?id='+encodeURIComponent(currentPreviewTrashId)+(parentSub?('&sub='+encodeURIComponent(parentSub)):'');" +
          "}else if(currentPreviewPath){" +
            "var lastSlash=currentPreviewPath.lastIndexOf('/');" +
            "var folderPath=lastSlash===-1?'':currentPreviewPath.substring(0,lastSlash);" +
            "url='/browse?path='+encodeURIComponent(folderPath);" +
          "}else{ return; }" +
          "closePreview();" +
          "if(window.parent && window.parent.navigateCurrentTab){ window.parent.navigateCurrentTab(url); }" +
          "else if(window.navigateCurrentTab){ window.navigateCurrentTab(url); }" +
          "else{ location.href=url; }" +
        "}" +
        "function isViewableExt(ext){" +
          "return PREVIEW_IMAGE_EXTS.indexOf(ext)!==-1||PREVIEW_AUDIO_EXTS.indexOf(ext)!==-1||" +
            "PREVIEW_VIDEO_EXTS.indexOf(ext)!==-1||PREVIEW_TEXT_EXTS.indexOf(ext)!==-1||ext==='pdf'||ext==='docx';" +
        "}" +
        // Shared by the visible edge arrow buttons and the ArrowLeft/
        // ArrowRight keydown handler below - both just call
        // navigatePreview(direction), so clicking an arrow and pressing a
        // key behave identically.
        "function updatePreviewNavVisibility(){" +
          "var inTrash=!!currentPreviewTrashId;" +
          "var cards=Array.prototype.slice.call(document.querySelectorAll('.card.file[data-path]'))" +
            ".filter(function(c){ return c.style.display!=='none' && (inTrash?c.dataset.type==='trash':c.dataset.type!=='trash'); });" +
          "var show=cards.length>1;" +
          "var left=document.getElementById('previewNavLeft'), right=document.getElementById('previewNavRight');" +
          "if(left) left.style.display=show?'':'none';" +
          "if(right) right.style.display=show?'':'none';" +
        "}" +
        // Left/Right arrow keys step to the previous/next file card while a
        // preview is open, wrapping around at either end - lets someone
        // flick through a whole folder of images (or books) without
        // closing and reopening the modal each time.
        "function navigatePreview(direction){" +
          "if(!currentPreviewPath && !currentPreviewTrashId) return;" +
          "var inTrash=!!currentPreviewTrashId;" +
          "var cards=Array.prototype.slice.call(document.querySelectorAll('.card.file[data-path]'))" +
            ".filter(function(c){ return c.style.display!=='none' && (inTrash?c.dataset.type==='trash':c.dataset.type!=='trash'); });" +
          "var idx=cards.findIndex(function(c){" +
            "return inTrash?(c.dataset.trashId===currentPreviewTrashId && (c.dataset.trashSub||'')===currentPreviewTrashSub):" +
              "c.dataset.path===currentPreviewPath;" +
          "});" +
          "if(idx===-1||cards.length===0) return;" +
          "var nextIdx=(idx+direction+cards.length)%cards.length;" +
          "var next=cards[nextIdx];" +
          "openPreview(next.dataset.path, next.dataset.name, next.dataset.ext, next.dataset.viewable==='1', next.dataset.textlike==='1', next.dataset.trashId, next.dataset.trashSub);" +
          "if(typeof selectOnly==='function') selectOnly(next);" +
        "}" +

        // ---- Selection model ----
        "var selectedPaths=[];" +
        "var lastSelectedCard=null;" +
        "function allCards(){ return Array.prototype.slice.call(document.querySelectorAll('.card[data-path]')); }" +
        "function clearSelection(){" +
          "allCards().forEach(function(c){ c.classList.remove('selected'); });" +
          "selectedPaths=[];" +
        "}" +
        "function selectOnly(card){ clearSelection(); card.classList.add('selected'); selectedPaths=[card.dataset.path]; lastSelectedCard=card; }" +
        "function toggleSelect(card){" +
          "var idx=selectedPaths.indexOf(card.dataset.path);" +
          "if(idx===-1){ card.classList.add('selected'); selectedPaths.push(card.dataset.path); }" +
          "else{ card.classList.remove('selected'); selectedPaths.splice(idx,1); }" +
          "lastSelectedCard=card;" +
        "}" +
        "function rangeSelect(card){" +
          "var cards=allCards();" +
          "var from=lastSelectedCard?cards.indexOf(lastSelectedCard):0;" +
          "var to=cards.indexOf(card);" +
          "if(from===-1) from=0;" +
          "var start=Math.min(from,to), end=Math.max(from,to);" +
          "clearSelection();" +
          "for(var i=start;i<=end;i++){ cards[i].classList.add('selected'); selectedPaths.push(cards[i].dataset.path); }" +
          "lastSelectedCard=card;" +
        "}" +

        "document.addEventListener('click', function(e){" +
          "var card=e.target.closest('.card[data-path]');" +
          "if(!card){" +
            "if(!e.target.closest('.context-menu') && !e.target.closest('.move-modal-overlay')) clearSelection();" +
            "return;" +
          "}" +
          "if(e.target.closest('a,button')) return;" + // let real links/buttons behave normally
          "if(e.shiftKey){ rangeSelect(card); }" +
          "else if(e.ctrlKey||e.metaKey){ toggleSelect(card); }" +
          "else{ selectOnly(card); }" +
        "});" +

        "document.addEventListener('dblclick', function(e){" +
          "var card=e.target.closest('.card[data-path]');" +
          "if(!card) return;" +
          "if(card.dataset.type==='trash'){" +
            // Trashed items live outside the normal browse tree, so a
            // trashed folder can't just reuse /browse?path=... - it needs
            // its own read-only listing (see /trash-browse) that can see
            // inside the recycle bin without restoring it first. Works the
            // same whether this card is a top-level Recycle Bin entry
            // (trashSub is empty) or a folder found while already browsing
            // inside one (trashSub points deeper).
            "if(card.dataset.isdir==='1'){" +
              "var sub=card.dataset.trashSub;" +
              "location.href='/trash-browse?id='+encodeURIComponent(card.dataset.trashId)+(sub?('&sub='+encodeURIComponent(sub)):'');" +
            "}else{" +
              "openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1', card.dataset.textlike==='1', card.dataset.trashId, card.dataset.trashSub);" +
            "}" +
            "return;" +
          "}" +
          "if(card.dataset.type==='folder'){ location.href='/browse?path='+encodeURIComponent(card.dataset.path); }" +
          "else{ openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1', card.dataset.textlike==='1'); }" +
        "});" +

        // ---- Context menu ----
        "function menuItem(label, action){ return '<div class=\"context-menu-item\" data-menu-action=\"'+action+'\">'+label+'</div>'; }" +
        "function menuDivider(){ return '<div class=\"context-menu-divider\"></div>'; }" +

        "document.addEventListener('contextmenu', function(e){" +
          "var card=e.target.closest('.card[data-path]');" +
          "if(card){" +
            "e.preventDefault();" +
            "if(selectedPaths.indexOf(card.dataset.path)===-1){ selectOnly(card); }" +
            "showContextMenu(e.clientX, e.clientY);" +
            "return;" +
          "}" +
          "var grid=e.target.closest('.grid[data-current-path]');" +
          "if(grid){" +
            "e.preventDefault();" +
            "clearSelection();" +
            "showFolderContextMenu(e.clientX, e.clientY, grid.dataset.currentPath);" +
          "}" +
        "});" +

        "function showFolderContextMenu(x, y, folderPath){" +
          "var menu=document.getElementById('contextMenu');" +
          "menu.innerHTML=" +
            "menuItem('Refresh','refresh-folder')+" +
            "menuItem('New folder here','new-folder-here')+" +
            "menuItem('Download this folder as .zip','zip-current-folder');" +
          "menu.dataset.folderPath=folderPath;" +
          "menu.classList.add('open');" +
          "var maxX=window.innerWidth-menu.offsetWidth-8, maxY=window.innerHeight-menu.offsetHeight-8;" +
          "menu.style.left=Math.min(x,maxX)+'px';" +
          "menu.style.top=Math.min(y,maxY)+'px';" +
        "}" +

        // Re-fetches just this tab's own URL and swaps in the refreshed
        // .grid element, instead of location.reload() - a full reload hits
        // the top-level shell page, tearing down and re-loading every
        // OTHER open tab's iframe along with it (and collapsing whatever
        // preview state they had). This only touches the current tab.
        "function refreshCurrentFolder(){" +
          "var grid=document.querySelector('.grid[data-current-path]');" +
          "if(!grid) return;" +
          "fetch(window.location.href).then(function(r){ return r.text(); }).then(function(html){" +
            "var doc=new DOMParser().parseFromString(html, 'text/html');" +
            "var newGrid=doc.querySelector('.grid[data-current-path]');" +
            "if(newGrid){ grid.outerHTML=newGrid.outerHTML; }" +
            "if(typeof clearSelection==='function') clearSelection();" +
          "}).catch(function(){});" +
        "}" +

        "function showContextMenu(x, y){" +
          "var menu=document.getElementById('contextMenu');" +
          "var html='';" +
          "if(selectedPaths.length>1){" +
            "var allTrash=allCards().filter(function(c){return selectedPaths.indexOf(c.dataset.path)!==-1;})" +
              ".every(function(c){return c.dataset.type==='trash';});" +
            "if(allTrash){" +
              "html+=menuItem('Restore selected','restore-selection');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete selected forever','permanent-delete-selection');" +
            "}else{" +
              "html+=menuItem('Download selected as .zip','zip-selection');" +
              "html+=menuItem('Move selected to...','move-selection');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete selected','delete-selection');" +
            "}" +
          "}else{" +
            "var card=lastSelectedCard;" +
            "var type=card.dataset.type;" +
            "if(type==='trash'){" +
              "if(card.dataset.isdir==='1'){" +
                "html+=menuItem('Open','open-trash-folder');" +
                "html+=menuDivider();" +
              "}else{" +
                "html+=menuItem('Open','open-item');" +
                "if(card.dataset.ext==='pdf'||card.dataset.textlike==='1'){" +
                  "html+=menuItem('Open Viewer','open-viewer');" +
                "}" +
                "html+=menuItem('Open in new tab','open-new-tab');" +
                "html+=menuItem('Download','download-item');" +
                "html+=menuDivider();" +
              "}" +
              "html+=menuItem('Restore','restore-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete forever','permanent-delete-item');" +
            "}else if(type==='file'){" +
              "html+=menuItem('Open','open-item');" +
              "if(card.dataset.ext==='pdf'||card.dataset.textlike==='1'){" +
                "html+=menuItem('Open Viewer','open-viewer');" +
              "}" +
              "html+=menuItem('Open in new tab','open-new-tab');" +
              "html+=menuItem('Download','download-item');" +
              "html+=menuItem('Reveal in file manager','reveal-item');" +
              "html+=menuItem('Copy path','copy-path-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Rename','rename-item');" +
              "html+=menuItem('Duplicate','duplicate-item');" +
              "html+=menuItem('Move to...','move-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete','delete-item');" +
            "}else{" +
              "html+=menuItem('Open','open-item');" +
              "html+=menuItem('Reveal in file manager','reveal-item');" +
              "html+=menuItem('Copy path','copy-path-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Rename','rename-item');" +
              "html+=menuItem('Duplicate','duplicate-item');" +
              "html+=menuItem('Move to...','move-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete','delete-item');" +
            "}" +
          "}" +
          "menu.innerHTML=html;" +
          "menu.classList.add('open');" +
          "var maxX=window.innerWidth-menu.offsetWidth-8, maxY=window.innerHeight-menu.offsetHeight-8;" +
          "menu.style.left=Math.min(x,maxX)+'px';" +
          "menu.style.top=Math.min(y,maxY)+'px';" +
        "}" +
        "function hideContextMenu(){ document.getElementById('contextMenu').classList.remove('open'); }" +

        // Closing the menu needs to handle more than "clicked elsewhere in
        // this document": since each page lives inside a shell iframe,
        // clicking the sidebar/tab bar/scrollbar happens in a DIFFERENT
        // document entirely and would never reach a plain same-document
        // click listener. 'blur' on window fires whenever focus leaves this
        // iframe for any reason (another frame, the browser chrome,
        // scrollbar interaction, etc.), which is the reliable cross-frame
        // signal. Scrolling and any mousedown outside the menu close it too.
        "window.addEventListener('blur', hideContextMenu);" +
        "document.addEventListener('scroll', hideContextMenu, true);" +
        "document.addEventListener('mousedown', function(e){" +
          "if(!e.target.closest('.context-menu')) hideContextMenu();" +
        "});" +

        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('.context-menu-item');" +
          "if(item){ runMenuAction(item.dataset.menuAction); hideContextMenu(); return; }" +
          "if(!e.target.closest('.context-menu')) hideContextMenu();" +
        "});" +
        "document.addEventListener('keydown', function(e){" +
          "if(e.key==='Escape'){ hideContextMenu(); closeMoveModal(); closePreview(); return; }" +
          "if(currentPreviewPath && (e.key==='ArrowLeft'||e.key==='ArrowRight')){" +
            "e.preventDefault();" +
            "navigatePreview(e.key==='ArrowLeft'?-1:1);" +
            "return;" +
          "}" +
          "if(e.key==='/' && document.activeElement.tagName!=='INPUT' && document.activeElement.tagName!=='TEXTAREA'){" +
            "if(window.parent && window.parent!==window && window.parent.openAddressBar){" +
              "e.preventDefault();" +
              "window.parent.openAddressBar();" +
            "}" +
          "}" +
        "});" +

        "function runMenuAction(action){" +
          "var card=lastSelectedCard;" +
          "if(action==='open-item'){" +
            "if(card.dataset.type==='folder'){ location.href='/browse?path='+encodeURIComponent(card.dataset.path); }" +
            "else if(card.dataset.type==='trash'){" +
              "if(card.dataset.isdir==='1'){" +
                "var sub=card.dataset.trashSub;" +
                "location.href='/trash-browse?id='+encodeURIComponent(card.dataset.trashId)+(sub?('&sub='+encodeURIComponent(sub)):'');" +
              "}else{" +
                "openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1', card.dataset.textlike==='1', card.dataset.trashId, card.dataset.trashSub);" +
              "}" +
            "}else{ openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1', card.dataset.textlike==='1'); }" +
          "}else if(action==='open-new-tab'){" +
            "if(card.dataset.type==='trash'){" +
              "var tmode=isViewableExt(card.dataset.ext)?'view':'download';" +
              "window.open('/trash-file?id='+encodeURIComponent(card.dataset.trashId)+'&sub='+encodeURIComponent(card.dataset.trashSub||'')+'&mode='+tmode, '_blank');" +
            "}else{" +
              "var mode=isViewableExt(card.dataset.ext)?'view':'preview';" +
              "window.open('/file?path='+encodeURIComponent(card.dataset.path)+'&mode='+mode, '_blank');" +
            "}" +
          "}else if(action==='open-viewer'){" +
            "var viewerUrl=card.dataset.type==='trash'?" +
              "('/viewer?trashId='+encodeURIComponent(card.dataset.trashId)+'&trashSub='+encodeURIComponent(card.dataset.trashSub||'')):" +
              "('/viewer?path='+encodeURIComponent(card.dataset.path));" +
            "if(window.parent && window.parent.openTab){ window.parent.openTab(viewerUrl, card.dataset.name); }" +
            "else{ window.open(viewerUrl, '_blank'); }" +
          "}else if(action==='download-item'){" +
            "if(card.dataset.type==='trash'){" +
              "window.location.href='/trash-file?id='+encodeURIComponent(card.dataset.trashId)+'&sub='+encodeURIComponent(card.dataset.trashSub||'')+'&mode=download';" +
            "}else{" +
              "window.location.href='/file?path='+encodeURIComponent(card.dataset.path)+'&mode=download';" +
            "}" +
          "}else if(action==='rename-item'){ renameItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='duplicate-item'){ duplicateItem(card.dataset.path); }" +
          "else if(action==='delete-item'){ deleteItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='move-item'){ openMoveModal([card.dataset.path]); }" +
          "else if(action==='reveal-item'){ revealInFileManager(card.dataset.path); }" +
          "else if(action==='copy-path-item'){ copyPathToClipboard(card.dataset.path); }" +
          "else if(action==='zip-selection'){" +
            "window.location.href='/zip-selection?paths='+encodeURIComponent(selectedPaths.join('|'));" +
          "}else if(action==='move-selection'){ openMoveModal(selectedPaths.slice()); }" +
          "else if(action==='delete-selection'){ deleteSelection(); }" +
          "else if(action==='new-folder-here'){ newFolder(document.getElementById('contextMenu').dataset.folderPath); }" +
          "else if(action==='refresh-folder'){ refreshCurrentFolder(); }" +
          "else if(action==='zip-current-folder'){" +
            "window.location.href='/zip?path='+encodeURIComponent(document.getElementById('contextMenu').dataset.folderPath);" +
          "}else if(action==='restore-item'){ restoreItem(card.dataset.trashId, card.dataset.trashSub, card.dataset.name); }" +
          "else if(action==='open-trash-folder'){" +
            "var sub=card.dataset.trashSub;" +
            "location.href='/trash-browse?id='+encodeURIComponent(card.dataset.trashId)+(sub?('&sub='+encodeURIComponent(sub)):'');" +
          "}else if(action==='permanent-delete-item'){ permanentDeleteItem(card.dataset.trashId, card.dataset.trashSub, card.dataset.name); }" +
          "else if(action==='restore-selection'){" +
            "var cards=allCards().filter(function(c){return selectedPaths.indexOf(c.dataset.path)!==-1;});" +
            "Promise.all(cards.map(function(c){" +
              "return fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'restore',id:c.dataset.trashId,sub:c.dataset.trashSub||''}).toString()}).then(function(r){return r.json();});" +
            "})).then(function(results){" +
              "var failed=results.filter(function(r){return !r.success;});" +
              "if(failed.length){ alert('Some items could not be restored: '+failed.map(function(r){return r.message;}).join('; ')); }" +
              "location.reload();" +
            "});" +
          "}else if(action==='permanent-delete-selection'){" +
            "if(!confirm('Permanently delete '+selectedPaths.length+' items? This cannot be undone.')) return;" +
            "var cards=allCards().filter(function(c){return selectedPaths.indexOf(c.dataset.path)!==-1;});" +
            "Promise.all(cards.map(function(c){" +
              "return fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'permanent-delete',id:c.dataset.trashId,sub:c.dataset.trashSub||''}).toString()}).then(function(r){return r.json();});" +
            "})).then(function(results){" +
              "var failed=results.filter(function(r){return !r.success;});" +
              "if(failed.length){ alert('Some items could not be deleted: '+failed.map(function(r){return r.message;}).join('; ')); }" +
              "location.reload();" +
            "});" +
          "}" +
        "}" +

        // ---- Undo/redo action toast (move, rename, and delete - deletes
        // undo via the recycle bin: TrashManager still gives it a real
        // safety net if the toast is missed, but it no longer means delete
        // is left out of undo entirely) ----
        // Stacks persist in sessionStorage (not JS variables) because every
        // action here reloads the page/iframe afterward, which would
        // otherwise wipe them. Same-origin iframes in the same tab share
        // sessionStorage, so this survives the reload just fine.
        "var UNDO_STACK_KEY='fd-undo-stack', REDO_STACK_KEY='fd-redo-stack', PENDING_TOASTS_KEY='fd-pending-toasts';" +
        "var UNDO_STACK_CAP=10;" +
        "function loadStack(key){" +
          "try{ var raw=sessionStorage.getItem(key); return raw?JSON.parse(raw):[]; }catch(e){ return []; }" +
        "}" +
        "function saveStack(key, stack){" +
          "try{ sessionStorage.setItem(key, JSON.stringify(stack.slice(-UNDO_STACK_CAP))); }catch(e){}" +
        "}" +
        "function parentOfPath(p){ var i=p.lastIndexOf('/'); return i===-1?'':p.substring(0,i); }" +
        "function baseNameOfPath(p){ var i=p.lastIndexOf('/'); return i===-1?p:p.substring(i+1); }" +
        // op = {action:'move'|'rename'|'delete', items:[...]} hits
        // /fileops as usual; op.action==='trash-restore' is the special
        // case for undoing a delete - it hits /trashops instead, since
        // restoring out of the recycle bin isn't a /fileops action.
        "function runOp(op){" +
          "if(op.action==='trash-restore'){" +
            "return Promise.all(op.items.map(function(it){" +
              "return fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'restore',id:it.id}).toString()})" +
                ".then(function(r){return r.json();});" +
            "}));" +
          "}" +
          "return Promise.all(op.items.map(function(it){" +
            "var params=Object.assign({action:op.action}, it);" +
            "return postFileOp(new URLSearchParams(params));" +
          "}));" +
        "}" +
        // Records a just-completed move/rename/delete (undoOp reverses it,
        // redoOp reapplies it), clears the redo history - a fresh action
        // invalidates any pending redo, same as any editor - and queues the
        // toast to appear once the page (about to be reloaded by the
        // caller) finishes loading.
        "function pushUndoEntry(message, undoOp, redoOp){" +
          "var stack=loadStack(UNDO_STACK_KEY);" +
          "stack.push({message:message, undoOp:undoOp, redoOp:redoOp});" +
          "saveStack(UNDO_STACK_KEY, stack);" +
          "saveStack(REDO_STACK_KEY, []);" +
          "queueToast(message, 'undo');" +
        "}" +
        // Queues onto an array (rather than a single slot) so a page load
        // that somehow has more than one toast waiting - none of today's
        // flows produce that, but nothing stops a future batched action
        // from doing so - shows all of them stacked instead of clobbering
        // each other.
        "function queueToast(message, mode){" +
          "try{" +
            "var raw=sessionStorage.getItem(PENDING_TOASTS_KEY);" +
            "var list=raw?JSON.parse(raw):[];" +
            "list.push({message:message, mode:mode});" +
            "sessionStorage.setItem(PENDING_TOASTS_KEY, JSON.stringify(list));" +
          "}catch(e){}" +
        "}" +
        "function clickUndo(){" +
          "var stack=loadStack(UNDO_STACK_KEY);" +
          "var entry=stack.pop();" +
          "if(!entry) return;" +
          "runOp(entry.undoOp).then(function(results){" +
            "var failed=results.filter(function(r){return !r.success;});" +
            "if(failed.length){ alert('Could not undo: '+failed.map(function(r){return r.message;}).join('; ')); return; }" +
            "saveStack(UNDO_STACK_KEY, stack);" +
            "var redoStack=loadStack(REDO_STACK_KEY);" +
            "redoStack.push(entry);" +
            "saveStack(REDO_STACK_KEY, redoStack);" +
            "queueToast('Undone', 'redo');" +
            "location.reload();" +
          "});" +
        "}" +
        "function clickRedo(){" +
          "var stack=loadStack(REDO_STACK_KEY);" +
          "var entry=stack.pop();" +
          "if(!entry) return;" +
          "runOp(entry.redoOp).then(function(results){" +
            "var failed=results.filter(function(r){return !r.success;});" +
            "if(failed.length){ alert('Could not redo: '+failed.map(function(r){return r.message;}).join('; ')); return; }" +
            // Re-deleting assigns a brand-new trash id, so the undo side of
            // this entry has to be updated to point at it - otherwise a
            // later "Undo" would try to restore an id that was already
            // restored once and is no longer in the recycle bin.
            "if(entry.redoOp.action==='delete'){" +
              "entry.undoOp={action:'trash-restore', items:results.map(function(r){ return {id:r.trashId}; })};" +
            "}" +
            "saveStack(REDO_STACK_KEY, stack);" +
            "var undoStack=loadStack(UNDO_STACK_KEY);" +
            "undoStack.push(entry);" +
            "saveStack(UNDO_STACK_KEY, undoStack);" +
            "queueToast(entry.message, 'undo');" +
            "location.reload();" +
          "});" +
        "}" +
        // ---- Toast rendering: a stacking container rather than a single
        // slot, so e.g. deleting a file and then immediately undoing it
        // shows both toasts at once instead of the second silently
        // replacing the first. Each toast is its own DOM node with its own
        // 5-second timer (paused while hovered, so reading a longer message
        // or aiming for the Undo button doesn't race the fade-out), and
        // dismissing one never affects the others.
        "function showActionToast(message, mode){" +
          "var container=document.getElementById('actionToastContainer');" +
          "if(!container) return;" +
          "var toast=document.createElement('div');" +
          "toast.className='action-toast open';" +
          "var msg=document.createElement('span');" +
          "msg.className='action-toast-message'; msg.textContent=message;" +
          "var btn=document.createElement('button');" +
          "btn.className='action-toast-btn'; btn.textContent=mode==='undo'?'Undo':'Redo';" +
          "btn.onclick=function(){ (mode==='undo'?clickUndo:clickRedo)(); remove(); };" +
          "var closeBtn=document.createElement('button');" +
          "closeBtn.className='action-toast-close'; closeBtn.innerHTML='&times;'; closeBtn.setAttribute('aria-label','Dismiss');" +
          "closeBtn.onclick=function(){ remove(); };" +
          "toast.appendChild(msg); toast.appendChild(btn); toast.appendChild(closeBtn);" +
          "container.appendChild(toast);" +
          "var timer;" +
          "function remove(){ clearTimeout(timer); if(toast.parentNode) toast.parentNode.removeChild(toast); }" +
          "function arm(){ timer=setTimeout(remove, 5000); }" +
          "toast.addEventListener('mouseenter', function(){ clearTimeout(timer); });" +
          "toast.addEventListener('mouseleave', arm);" +
          "arm();" +
        "}" +
        "(function(){" +
          "var list=[];" +
          "try{ var raw=sessionStorage.getItem(PENDING_TOASTS_KEY); list=raw?JSON.parse(raw):[]; }catch(e){ list=[]; }" +
          "if(list.length){" +
            "sessionStorage.removeItem(PENDING_TOASTS_KEY);" +
            "list.forEach(function(p){ showActionToast(p.message, p.mode); });" +
          "}" +
        "})();" +
        "document.addEventListener('keydown', function(e){" +
          "var tag=document.activeElement?document.activeElement.tagName:'';" +
          "if(tag==='INPUT'||tag==='TEXTAREA') return;" +
          "if((e.ctrlKey||e.metaKey) && !e.shiftKey && e.key.toLowerCase()==='z'){ e.preventDefault(); clickUndo(); }" +
          "else if((e.ctrlKey||e.metaKey) && (e.key.toLowerCase()==='y' || (e.shiftKey && e.key.toLowerCase()==='z'))){ e.preventDefault(); clickRedo(); }" +
        "});" +

        // ---- Rename / duplicate / delete / new folder ----
        "function postFileOp(params){" +
          "return fetch('/fileops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:params.toString()})" +
          ".then(function(r){return r.json();});" +
        "}" +
        "function renameItem(path, currentName){" +
          "var newName=prompt('Rename to:', currentName);" +
          "if(!newName||newName===currentName) return;" +
          "postFileOp(new URLSearchParams({action:'rename',path:path,newName:newName})).then(function(res){" +
            "if(!res.success){ alert('Rename failed: '+res.message); return; }" +
            "var newPath=res.newPath||(parentOfPath(path)?parentOfPath(path)+'/'+newName:newName);" +
            "pushUndoEntry('Renamed to \"'+newName+'\"'," +
              "{action:'rename', items:[{path:newPath, newName:currentName}]}," +
              "{action:'rename', items:[{path:path, newName:newName}]});" +
            "location.reload();" +
          "});" +
        "}" +
        "function duplicateItem(path){" +
          "postFileOp(new URLSearchParams({action:'duplicate',path:path})).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Duplicate failed: '+res.message); }" +
          "});" +
        "}" +
        "function deleteItem(path, name){" +
          "if(!confirm('Move \"'+name+'\" to the recycle bin?')) return;" +
          "postFileOp(new URLSearchParams({action:'delete',path:path})).then(function(res){" +
            "if(!res.success){ alert('Delete failed: '+res.message); return; }" +
            "pushUndoEntry('Moved \"'+name+'\" to the recycle bin'," +
              "{action:'trash-restore', items:[{id:res.trashId}]}," +
              "{action:'delete', items:[{path:path}]});" +
            "location.reload();" +
          "});" +
        "}" +
        // Asks the server to shell out to the OS's native file manager
        // (Explorer/Finder/xdg-open) for this item - only meaningful
        // because the app and browser are running on the same machine.
        "function revealInFileManager(path){" +
          "fetch('/reveal',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({path:path}).toString()})" +
          ".then(function(r){return r.json();})" +
          ".then(function(res){ if(!res.success){ alert('Could not open file manager: '+res.message); } });" +
        "}" +
        // Copies the item's real, absolute filesystem path (not the app's
        // own ROOT_DIR-relative one) so it's paste-able straight into a
        // terminal or another app.
        "function copyPathToClipboard(path){" +
          "fetch('/abspath?path='+encodeURIComponent(path))" +
          ".then(function(r){return r.json();})" +
          ".then(function(res){" +
            "if(!res.success){ alert('Could not get path: '+res.message); return; }" +
            "if(navigator.clipboard && navigator.clipboard.writeText){" +
              "navigator.clipboard.writeText(res.path).catch(function(){ window.prompt('Copy this path:', res.path); });" +
            "}else{ window.prompt('Copy this path:', res.path); }" +
          "});" +
        "}" +
        "function deleteSelection(){" +
          "if(!confirm('Move '+selectedPaths.length+' items to the recycle bin?')) return;" +
          "var paths=selectedPaths.slice();" +
          "Promise.all(paths.map(function(p){" +
            "return postFileOp(new URLSearchParams({action:'delete',path:p})).then(function(res){ return {path:p, res:res}; });" +
          "})).then(function(outcomes){" +
            "var failed=outcomes.filter(function(o){return !o.res.success;});" +
            "var succeeded=outcomes.filter(function(o){return o.res.success;});" +
            "if(failed.length){ alert('Some items could not be deleted: '+failed.map(function(o){return o.res.message;}).join('; ')); }" +
            "if(succeeded.length){" +
              "var undoItems=succeeded.map(function(o){ return {id:o.res.trashId}; });" +
              "var redoItems=succeeded.map(function(o){ return {path:o.path}; });" +
              "var message=succeeded.length===1?" +
                "('Moved \"'+baseNameOfPath(succeeded[0].path)+'\" to the recycle bin'):" +
                "('Moved '+succeeded.length+' items to the recycle bin');" +
              "pushUndoEntry(message, {action:'trash-restore', items:undoItems}, {action:'delete', items:redoItems});" +
            "}" +
            "location.reload();" +
          "});" +
        "}" +
        "function newFolder(parentPath){" +
          "var name=prompt('New folder name:', 'New Folder');" +
          "if(!name) return;" +
          "postFileOp(new URLSearchParams({action:'create-folder',path:parentPath,newName:name})).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Could not create folder: '+res.message); }" +
          "});" +
        "}" +

        // ---- Move to... modal ----
        "var moveTargetPaths=[];" +
        "var moveCurrentPath='';" +
        "function openMoveModal(paths){" +
          "moveTargetPaths=paths;" +
          "document.getElementById('moveModalOverlay').classList.add('open');" +
          "loadMoveFolderList('');" +
        "}" +
        "function closeMoveModal(){ document.getElementById('moveModalOverlay').classList.remove('open'); }" +
        "function loadMoveFolderList(path){" +
          "moveCurrentPath=path;" +
          "renderMoveBreadcrumb(path);" +
          "var list=document.getElementById('moveFolderList');" +
          "list.innerHTML=\"<div class='move-folder-empty'>Loading...</div>\";" +
          "fetch('/subfolders?path='+encodeURIComponent(path)).then(function(r){return r.json();}).then(function(folders){" +
            "if(!folders.length){ list.innerHTML=\"<div class='move-folder-empty'>No subfolders here.</div>\"; return; }" +
            "list.innerHTML=folders.map(function(f){" +
              "var childPath=path?path+'/'+f.name:f.name;" +
              "var p=childPath.replace(/\"/g,'&quot;');" +
              "return '<div class=\"move-folder-item\" data-move-into=\"'+p+'\">&#128193; '+f.name+'</div>';" +
            "}).join('');" +
          "});" +
        "}" +
        "function renderMoveBreadcrumb(path){" +
          "var bc=document.getElementById('moveBreadcrumb');" +
          "var parts=path.split('/').filter(function(p){return p.length>0;});" +
          "var html=\"<span class='move-crumb' data-move-into=''>Home</span>\";" +
          "var acc='';" +
          "parts.forEach(function(part){" +
            "acc=acc?acc+'/'+part:part;" +
            "var a=acc.replace(/\"/g,'&quot;');" +
            "html+=' / <span class=\"move-crumb\" data-move-into=\"'+a+'\">'+part+'</span>';" +
          "});" +
          "bc.innerHTML=html;" +
        "}" +
        "function confirmMoveHere(){" +
          "var dest=moveCurrentPath;" +
          "if(moveTargetPaths.indexOf(dest)!==-1){ alert(\"That's the folder being moved - pick a different destination.\"); return; }" +
          "var paths=moveTargetPaths.slice();" +
          "Promise.all(paths.map(function(p){" +
            "return postFileOp(new URLSearchParams({action:'move',path:p,destPath:dest})).then(function(res){ return {path:p, res:res}; });" +
          "})).then(function(outcomes){" +
            "var failed=outcomes.filter(function(o){return !o.res.success;});" +
            "var succeeded=outcomes.filter(function(o){return o.res.success;});" +
            "if(failed.length){ alert('Some items could not be moved: '+failed.map(function(o){return o.res.message;}).join('; ')); }" +
            "if(succeeded.length){" +
              "var undoItems=succeeded.map(function(o){" +
                "var newPath=o.res.newPath||(dest?dest+'/'+baseNameOfPath(o.path):baseNameOfPath(o.path));" +
                "return {path:newPath, destPath:parentOfPath(o.path)};" +
              "});" +
              "var redoItems=succeeded.map(function(o){ return {path:o.path, destPath:dest}; });" +
              "var message=succeeded.length===1?('Moved \"'+baseNameOfPath(succeeded[0].path)+'\"'):('Moved '+succeeded.length+' items');" +
              "pushUndoEntry(message, {action:'move', items:undoItems}, {action:'move', items:redoItems});" +
            "}" +
            "closeMoveModal(); location.reload();" +
          "});" +
        "}" +
        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('.move-folder-item, .move-crumb');" +
          "if(!item) return;" +
          "loadMoveFolderList(item.dataset.moveInto);" +
        "});" +

        // ---- Trash actions ----
        // sub identifies one item *inside* a trashed folder (empty/omitted
        // restores or permanently deletes the whole trash entry instead -
        // see TrashManager.restore/permanentlyDelete(id, sub)).
        "function restoreItem(id, sub, name){" +
          "fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'restore',id:id,sub:sub||''}).toString()})" +
          ".then(function(r){return r.json();}).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Restore failed: '+res.message); }" +
          "});" +
        "}" +
        "function permanentDeleteItem(id, sub, name){" +
          "if(!confirm('Permanently delete \"'+name+'\"? This cannot be undone.')) return;" +
          "fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'permanent-delete',id:id,sub:sub||''}).toString()})" +
          ".then(function(r){return r.json();}).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Delete failed: '+res.message); }" +
          "});" +
        "}" +
        "function emptyTrash(){" +
          "if(!confirm('Permanently delete everything in the recycle bin? This cannot be undone.')) return;" +
          "fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'empty'}).toString()})" +
          ".then(function(r){return r.json();}).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Failed: '+res.message); }" +
          "});" +
        "}" +

        // ---- Type-filter chips ----
        "var CHIP_GROUPS={all:null,image:['image'],pdf:['pdf'],document:['document','spreadsheet','presentation']," +
          "video:['video'],audio:['audio'],archive:['archive'],other:['other']};" +
        "document.addEventListener('click', function(e){" +
          "var chip=e.target.closest('.chip');" +
          "if(!chip) return;" +
          "document.querySelectorAll('.chip').forEach(function(c){ c.classList.remove('active'); });" +
          "chip.classList.add('active');" +
          "var group=CHIP_GROUPS[chip.dataset.filter];" +
          "document.querySelectorAll('.card.file').forEach(function(card){" +
            "var show=!group||group.indexOf(card.dataset.category)!==-1;" +
            "card.style.display=show?'':'none';" +
          "});" +
        "});" +

        // ---- Click delegation for simple data-action links (new folder, trash) ----
        "document.addEventListener('click', function(e){" +
          "var t=e.target.closest('[data-action]');" +
          "if(!t) return;" +
          "e.preventDefault();" +
          "var action=t.dataset.action, path=t.dataset.path, name=t.dataset.name, id=t.dataset.id;" +
          "if(action==='new-folder'){ newFolder(path); }" +
          "else if(action==='restore'){ restoreItem(id,'',name); }" +
          "else if(action==='permanent-delete'){ permanentDeleteItem(id,'',name); }" +
          "else if(action==='empty-trash'){ emptyTrash(); }" +
        "});" +

        // ---- Live search suggestions ----
        "(function(){" +
          "var debounceTimer;" +
          "document.addEventListener('input', function(e){" +
            "if(!e.target.classList.contains('js-search-input')) return;" +
            "var input=e.target;" +
            "var wrap=input.closest('.search-suggest-wrap');" +
            "var dropdown=wrap?wrap.querySelector('.search-suggestions'):null;" +
            "if(!dropdown) return;" +
            "clearTimeout(debounceTimer);" +
            "var q=input.value.trim();" +
            "if(q.length===0){ dropdown.classList.remove('open'); dropdown.innerHTML=''; return; }" +
            "debounceTimer=setTimeout(function(){" +
              "var ctx=input.dataset.contextPath||'';" +
              "fetch('/suggest?q='+encodeURIComponent(q)+'&path='+encodeURIComponent(ctx))" +
              ".then(function(r){return r.json();})" +
              ".then(function(items){" +
                "if(!items.length){ dropdown.classList.remove('open'); dropdown.innerHTML=''; return; }" +
                "var html='';" +
                "var dividerShown=false;" +
                "items.forEach(function(item, idx){" +
                  "if(!item.inCurrentFolder && !dividerShown && idx>0){" +
                    "html+=\"<div class='search-suggestion-divider'>Elsewhere</div>\";" +
                    "dividerShown=true;" +
                  "}" +
                  "var icon=item.type==='folder'?'&#128193;':'&#128196;';" +
                  "var p=item.path.replace(/\"/g,'&quot;'), n=item.name.replace(/\"/g,'&quot;');" +
                  "html+='<div class=\"search-suggestion-item\" data-suggest-path=\"'+p+'\" data-suggest-type=\"'+item.type+'\" data-suggest-name=\"'+n+'\">'+" +
                    "'<span class=\"search-suggestion-icon\">'+icon+'</span><span>'+item.name+'</span></div>';" +
                "});" +
                "dropdown.innerHTML=html;" +
                "dropdown.classList.add('open');" +
              "});" +
            "}, 180);" +
          "});" +

          "document.addEventListener('click', function(e){" +
            "var item=e.target.closest('.search-suggestion-item');" +
            "if(item){" +
              "var path=item.dataset.suggestPath, type=item.dataset.suggestType, name=item.dataset.suggestName;" +
              "if(type==='folder'){ location.href='/browse?path='+encodeURIComponent(path); }" +
              "else{" +
                "var ext=name.indexOf('.')!==-1?name.split('.').pop().toLowerCase():'';" +
                "openPreview(path,name,ext,isViewableExt(ext),PREVIEW_TEXT_EXTS.indexOf(ext)!==-1);" +
              "}" +
              "return;" +
            "}" +
            "document.querySelectorAll('.search-suggestions.open').forEach(function(d){ d.classList.remove('open'); });" +
          "});" +
        "})();" +
        "</script>";
}
