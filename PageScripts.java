/**
 * Everything client-side that's shared across content pages (Dashboard,
 * Browse, Search): the in-app preview modal (images/PDF/text/audio/video,
 * Google-Drive style, instead of leaving the page) and the rename/duplicate/
 * delete actions on each card. Included once per page via MODAL_HTML + SCRIPT.
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
        "</div></div>";

    public static final String SCRIPT =
        "<script>" +

        // ---- Preview modal ----
        "var PREVIEW_IMAGE_EXTS=['jpg','jpeg','png','gif','bmp','webp','svg','ico'];" +
        "var PREVIEW_AUDIO_EXTS=['mp3','wav','ogg','m4a','flac','aac'];" +
        "var PREVIEW_VIDEO_EXTS=['mp4','webm','mov','m4v'];" +
        "var PREVIEW_TEXT_EXTS=['txt','md','csv','json','xml','log','html','htm','css','js','ts','java','py','c','cpp','h','hpp','sh','yml','yaml','ini','conf','properties'];" +

        "function openPreview(path, name, ext, viewable){" +
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
          "}else if(PREVIEW_TEXT_EXTS.indexOf(ext)!==-1){" +
            "body.innerHTML='<pre>Loading...</pre>';" +
            "fetch(fileUrl).then(function(r){return r.text();}).then(function(text){" +
              "var pre=document.createElement('pre'); pre.textContent=text;" +
              "body.innerHTML=''; body.appendChild(pre);" +
            "});" +
          "}else{" +
            "body.innerHTML=\"<div class='preview-nopreview'><p>Preview not available.</p></div>\";" +
          "}" +
          "overlay.classList.add('open');" +
        "}" +

        "function closePreview(){" +
          "document.getElementById('previewOverlay').classList.remove('open');" +
          "document.getElementById('previewBody').innerHTML='';" + // stops audio/video playback
        "}" +
        "document.addEventListener('keydown', function(e){ if(e.key==='Escape') closePreview(); });" +

        // ---- Rename / duplicate / delete ----
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
          "if(!confirm('Delete \"'+name+'\"? This cannot be undone.')) return;" +
          "postFileOp(new URLSearchParams({action:'delete',path:path})).then(function(res){" +
            "if(res.success){ location.reload(); } else { alert('Delete failed: '+res.message); }" +
          "});" +
        "}" +

        // ---- Click delegation for every card on the page ----
        "document.addEventListener('click', function(e){" +
          "var t=e.target.closest('[data-action]');" +
          "if(!t) return;" +
          "e.preventDefault();" +
          "var action=t.dataset.action, path=t.dataset.path, name=t.dataset.name;" +
          "if(action==='view'){ openPreview(path,name,t.dataset.ext,t.dataset.viewable==='1'); }" +
          "else if(action==='rename'){ renameItem(path,name); }" +
          "else if(action==='duplicate'){ duplicateItem(path); }" +
          "else if(action==='delete'){ deleteItem(path,name); }" +
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
                "dropdown.innerHTML=items.map(function(item){" +
                  "var icon=item.type==='folder'?'&#128193;':'&#128196;';" +
                  "var p=item.path.replace(/\"/g,'&quot;'), n=item.name.replace(/\"/g,'&quot;');" +
                  "return '<div class=\"search-suggestion-item\" data-suggest-path=\"'+p+'\" data-suggest-type=\"'+item.type+'\" data-suggest-name=\"'+n+'\">'+" +
                    "'<span class=\"search-suggestion-icon\">'+icon+'</span><span>'+item.name+'</span></div>';" +
                "}).join('');" +
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
                "var viewable=PREVIEW_IMAGE_EXTS.indexOf(ext)!==-1||PREVIEW_AUDIO_EXTS.indexOf(ext)!==-1||" +
                  "PREVIEW_VIDEO_EXTS.indexOf(ext)!==-1||PREVIEW_TEXT_EXTS.indexOf(ext)!==-1||ext==='pdf';" +
                "openPreview(path,name,ext,viewable);" +
              "}" +
              "return;" +
            "}" +
            "document.querySelectorAll('.search-suggestions.open').forEach(function(d){ d.classList.remove('open'); });" +
          "});" +
        "})();" +
        "</script>";
}
