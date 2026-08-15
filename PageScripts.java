/**
 * Everything client-side that's shared across content pages (Dashboard,
 * Browse, Search, Recycle Bin): the in-app preview modal, the selection
 * model (click/ctrl-click/shift-click/double-click), the right-click
 * context menu, the "Move to..." folder picker, type-filter chips, and
 * new-folder/rename/duplicate/move/delete actions. Included once per page
 * via MODAL_HTML + SCRIPT.
 */
public class PageScripts {

    public static final String MODAL_HTML =
        "<div id='previewOverlay' class='preview-overlay' onclick=\"if(event.target===this) closePreview();\">" +
        "<div class='preview-box'>" +
        "<div class='preview-header'>" +
        "<span id='previewTitle' class='preview-title'></span>" +
        "<div class='preview-header-actions'>" +
        "<span id='previewExtraAction'></span>" +
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
        "</div></div></div>";

    public static final String SCRIPT =
        "<script>" +

        // ---- Preview modal ----
        "var PREVIEW_IMAGE_EXTS=['jpg','jpeg','png','gif','bmp','webp','svg','ico'];" +
        "var PREVIEW_AUDIO_EXTS=['mp3','wav','ogg','m4a','flac','aac'];" +
        "var PREVIEW_VIDEO_EXTS=['mp4','webm','mov','m4v'];" +
        "var PREVIEW_TEXT_EXTS=['txt','md','csv','json','xml','log','html','htm','css','js','ts','java','py','c','cpp','h','hpp','sh','yml','yaml','ini','conf','properties'];" +

        "var currentPreviewPath=null;" +
        "function openPreview(path, name, ext, viewable){" +
          "currentPreviewPath=path;" +
          "var overlay=document.getElementById('previewOverlay');" +
          "var body=document.getElementById('previewBody');" +
          "document.getElementById('previewTitle').textContent=name;" +
          "document.getElementById('previewDownloadLink').href='/file?path='+encodeURIComponent(path)+'&mode=download';" +
          "document.getElementById('previewExtraAction').innerHTML='';" +
          "var fileUrl='/file?path='+encodeURIComponent(path)+'&mode=view';" +
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
          "}else{" +
            // Anything else the server marked viewable must be text-like -
            // that's the only remaining category (including custom/unknown
            // extensions detected by content-sniffing rather than a
            // hardcoded list, e.g. a homegrown .vcanvas format).
            "body.innerHTML='<pre>Loading...</pre>';" +
            "fetch(fileUrl).then(function(r){return r.text();}).then(function(text){" +
              "var pre=document.createElement('pre'); pre.textContent=text;" +
              "body.innerHTML=''; body.appendChild(pre);" +
            "});" +
          "}" +
          "overlay.classList.add('open');" +
        "}" +
        "function closePreview(){" +
          "document.getElementById('previewOverlay').classList.remove('open');" +
          "document.getElementById('previewBody').innerHTML='';" +
          "currentPreviewPath=null;" +
        "}" +
        "function isViewableExt(ext){" +
          "return PREVIEW_IMAGE_EXTS.indexOf(ext)!==-1||PREVIEW_AUDIO_EXTS.indexOf(ext)!==-1||" +
            "PREVIEW_VIDEO_EXTS.indexOf(ext)!==-1||PREVIEW_TEXT_EXTS.indexOf(ext)!==-1||ext==='pdf';" +
        "}" +
        // Left/Right arrow keys step to the previous/next file card while a
        // preview is open, wrapping around at either end - lets someone
        // flick through a whole folder of images (or books) without
        // closing and reopening the modal each time.
        "function navigatePreview(direction){" +
          "if(!currentPreviewPath) return;" +
          "var cards=Array.prototype.slice.call(document.querySelectorAll('.card.file[data-path]'))" +
            ".filter(function(c){ return c.style.display!=='none'; });" +
          "var idx=cards.findIndex(function(c){ return c.dataset.path===currentPreviewPath; });" +
          "if(idx===-1||cards.length===0) return;" +
          "var nextIdx=(idx+direction+cards.length)%cards.length;" +
          "var next=cards[nextIdx];" +
          "openPreview(next.dataset.path, next.dataset.name, next.dataset.ext, next.dataset.viewable==='1');" +
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
          "if(!card || card.dataset.type==='trash') return;" +
          "if(card.dataset.type==='folder'){ location.href='/browse?path='+encodeURIComponent(card.dataset.path); }" +
          "else{ openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1'); }" +
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
            "menuItem('New folder here','new-folder-here')+" +
            "menuItem('Download this folder as .zip','zip-current-folder');" +
          "menu.dataset.folderPath=folderPath;" +
          "menu.classList.add('open');" +
          "var maxX=window.innerWidth-menu.offsetWidth-8, maxY=window.innerHeight-menu.offsetHeight-8;" +
          "menu.style.left=Math.min(x,maxX)+'px';" +
          "menu.style.top=Math.min(y,maxY)+'px';" +
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
              "html+=menuDivider();" +
              "html+=menuItem('Rename','rename-item');" +
              "html+=menuItem('Duplicate','duplicate-item');" +
              "html+=menuItem('Move to...','move-item');" +
              "html+=menuDivider();" +
              "html+=menuItem('Delete','delete-item');" +
            "}else{" +
              "html+=menuItem('Open','open-item');" +
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
            "else{ openPreview(card.dataset.path, card.dataset.name, card.dataset.ext, card.dataset.viewable==='1'); }" +
          "}else if(action==='open-new-tab'){" +
            "var mode=isViewableExt(card.dataset.ext)?'view':'preview';" +
            "window.open('/file?path='+encodeURIComponent(card.dataset.path)+'&mode='+mode, '_blank');" +
          "}else if(action==='open-viewer'){" +
            "var viewerUrl='/viewer?path='+encodeURIComponent(card.dataset.path);" +
            "if(window.parent && window.parent.openTab){ window.parent.openTab(viewerUrl, card.dataset.name); }" +
            "else{ window.open(viewerUrl, '_blank'); }" +
          "}else if(action==='download-item'){" +
            "window.location.href='/file?path='+encodeURIComponent(card.dataset.path)+'&mode=download';" +
          "}else if(action==='rename-item'){ renameItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='duplicate-item'){ duplicateItem(card.dataset.path); }" +
          "else if(action==='delete-item'){ deleteItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='move-item'){ openMoveModal([card.dataset.path]); }" +
          "else if(action==='zip-selection'){" +
            "window.location.href='/zip-selection?paths='+encodeURIComponent(selectedPaths.join('|'));" +
          "}else if(action==='move-selection'){ openMoveModal(selectedPaths.slice()); }" +
          "else if(action==='delete-selection'){ deleteSelection(); }" +
          "else if(action==='new-folder-here'){ newFolder(document.getElementById('contextMenu').dataset.folderPath); }" +
          "else if(action==='zip-current-folder'){" +
            "window.location.href='/zip?path='+encodeURIComponent(document.getElementById('contextMenu').dataset.folderPath);" +
          "}else if(action==='restore-item'){ restoreItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='permanent-delete-item'){ permanentDeleteItem(card.dataset.path, card.dataset.name); }" +
          "else if(action==='restore-selection'){" +
            "Promise.all(selectedPaths.map(function(id){" +
              "return fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'restore',id:id}).toString()}).then(function(r){return r.json();});" +
            "})).then(function(results){" +
              "var failed=results.filter(function(r){return !r.success;});" +
              "if(failed.length){ alert('Some items could not be restored: '+failed.map(function(r){return r.message;}).join('; ')); }" +
              "location.reload();" +
            "});" +
          "}else if(action==='permanent-delete-selection'){" +
            "if(!confirm('Permanently delete '+selectedPaths.length+' items? This cannot be undone.')) return;" +
            "Promise.all(selectedPaths.map(function(id){" +
              "return fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'permanent-delete',id:id}).toString()}).then(function(r){return r.json();});" +
            "})).then(function(results){" +
              "var failed=results.filter(function(r){return !r.success;});" +
              "if(failed.length){ alert('Some items could not be deleted: '+failed.map(function(r){return r.message;}).join('; ')); }" +
              "location.reload();" +
            "});" +
          "}" +
        "}" +

        // ---- Rename / duplicate / delete / new folder ----
        "function postFileOp(params){" +
          "return fetch('/fileops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:params.toString()})" +
          ".then(function(r){return r.json();});" +
        "}" +
        "function renameItem(path, currentName){" +
          "var newName=prompt('Rename to:', currentName);" +
          "if(!newName||newName===currentName) return;" +
          "postFileOp(new URLSearchParams({action:'rename',path:path,newName:newName})).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Rename failed: '+res.message); }" +
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
            "if(res.success){ location.reload(); } else { alert('Delete failed: '+res.message); }" +
          "});" +
        "}" +
        "function deleteSelection(){" +
          "if(!confirm('Move '+selectedPaths.length+' items to the recycle bin?')) return;" +
          "var paths=selectedPaths.slice();" +
          "Promise.all(paths.map(function(p){" +
            "return postFileOp(new URLSearchParams({action:'delete',path:p}));" +
          "})).then(function(){ location.reload(); });" +
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
          "Promise.all(moveTargetPaths.map(function(p){" +
            "return postFileOp(new URLSearchParams({action:'move',path:p,destPath:dest}));" +
          "})).then(function(results){" +
            "var failed=results.filter(function(r){return !r.success;});" +
            "if(failed.length){ alert('Some items could not be moved: '+failed.map(function(r){return r.message;}).join('; ')); }" +
            "closeMoveModal(); location.reload();" +
          "});" +
        "}" +
        "document.addEventListener('click', function(e){" +
          "var item=e.target.closest('.move-folder-item, .move-crumb');" +
          "if(!item) return;" +
          "loadMoveFolderList(item.dataset.moveInto);" +
        "});" +

        // ---- Trash actions ----
        "function restoreItem(id, name){" +
          "fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'restore',id:id}).toString()})" +
          ".then(function(r){return r.json();}).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Restore failed: '+res.message); }" +
          "});" +
        "}" +
        "function permanentDeleteItem(id, name){" +
          "if(!confirm('Permanently delete \"'+name+'\"? This cannot be undone.')) return;" +
          "fetch('/trashops',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({action:'permanent-delete',id:id}).toString()})" +
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
          "else if(action==='restore'){ restoreItem(id,name); }" +
          "else if(action==='permanent-delete'){ permanentDeleteItem(id,name); }" +
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
                "openPreview(path,name,ext,isViewableExt(ext));" +
              "}" +
              "return;" +
            "}" +
            "document.querySelectorAll('.search-suggestions.open').forEach(function(d){ d.classList.remove('open'); });" +
          "});" +
        "})();" +
        "</script>";
}
