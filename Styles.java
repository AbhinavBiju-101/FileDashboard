public class Styles {
    public static final String CSS =
        "<style>" +
        "*{box-sizing:border-box;}" +
        "body{font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;background:#f4f5f7;margin:0;padding:0;color:#1f2328;}" +
        ".topbar{background:#fff;padding:16px 24px;border-bottom:1px solid #e2e4e8;position:sticky;top:0;}" +
        ".topbar h1{margin:0 0 8px 0;font-size:20px;}" +
        ".breadcrumb{font-size:14px;color:#555;}" +
        ".breadcrumb a{color:#2563eb;text-decoration:none;}" +
        ".breadcrumb a:hover{text-decoration:underline;}" +
        ".upload-form{margin:16px 24px;padding:12px;background:#fff;border:1px dashed #c7cbd1;border-radius:8px;display:flex;gap:10px;align-items:center;}" +
        ".upload-form button{background:#2563eb;color:#fff;border:none;padding:8px 14px;border-radius:6px;cursor:pointer;font-size:13px;}" +
        ".upload-form button:hover{background:#1d4ed8;}" +
        ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px;padding:24px;}" +
        ".dash-row{display:flex;flex-wrap:nowrap;gap:14px;overflow-x:auto;padding:4px 24px 22px;scroll-behavior:smooth;}" +
        ".dash-row .card{flex:0 0 150px;width:150px;}" +
        ".card{background:#fff;border:1px solid #e2e4e8;border-radius:10px;padding:12px;text-align:center;transition:box-shadow .15s,border-color .15s;text-decoration:none;color:inherit;display:flex;flex-direction:column;align-items:center;cursor:pointer;user-select:none;}" +
        ".card:hover{box-shadow:0 2px 10px rgba(0,0,0,.08);}" +
        ".icon{font-size:40px;margin-bottom:8px;}" +
        ".thumb{width:100%;height:100px;object-fit:cover;border-radius:6px;margin-bottom:8px;background:#eee;}" +
        ".name{font-size:13px;word-break:break-word;max-width:100%;}" +
        ".meta{font-size:11px;color:#888;margin-top:4px;}" +
        ".card.selected{border-color:#2563eb;box-shadow:0 0 0 2px rgba(37,99,235,.25);background:#f0f5ff;}" +
        ".empty{padding:24px;color:#888;}" +
        ".toolbar{display:flex;flex-wrap:wrap;align-items:center;gap:14px;margin-top:10px;font-size:13px;}" +
        ".toolbar-label{color:#888;}" +
        ".toolbar-action{color:#2563eb;text-decoration:none;}" +
        ".toolbar-action:hover{text-decoration:underline;}" +
        ".toolbar-action.active{font-weight:600;}" +
        ".search-inline{display:flex;gap:6px;}" +
        ".search-suggest-wrap{position:relative;margin-left:auto;}" +
        ".search-suggestions{display:none;position:absolute;top:100%;left:0;right:0;margin-top:4px;" +
          "background:#fff;border:1px solid #e2e4e8;border-radius:8px;box-shadow:0 4px 14px rgba(0,0,0,.1);" +
          "z-index:50;max-height:280px;overflow-y:auto;}" +
        ".search-suggestions.open{display:block;}" +
        ".search-suggestion-item{display:flex;align-items:center;gap:8px;padding:8px 12px;cursor:pointer;font-size:13px;}" +
        ".search-suggestion-item:hover{background:#f4f5f7;}" +
        ".search-suggestion-icon{font-size:15px;flex-shrink:0;}" +
        ".search-inline input[type=text]{padding:6px 8px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
        ".search-inline button{padding:6px 10px;border:none;background:#e5e7eb;border-radius:6px;cursor:pointer;font-size:13px;}" +
        ".meta.path{color:#999;font-size:10px;word-break:break-word;}" +
        // Sidebar
        ".sidebar{position:fixed;top:0;left:0;bottom:0;width:190px;background:#1f2328;overflow-y:auto;" +
          "transition:width .15s;z-index:100;padding-top:52px;}" +
        ".sidebar.collapsed{width:56px;}" +
        ".sidebar.collapsed .sidebar-label{display:none;}" +
        ".sidebar-toggle{position:absolute;top:10px;left:12px;background:none;border:none;color:#fff;" +
          "font-size:20px;cursor:pointer;padding:0;}" +
        ".sidebar-item{display:flex;align-items:center;gap:10px;padding:9px 16px;color:#d1d5db;" +
          "text-decoration:none;font-size:14px;white-space:nowrap;}" +
        ".sidebar-item:hover{background:#2d333b;color:#fff;}" +
        ".sidebar-icon{font-size:17px;width:20px;text-align:center;flex-shrink:0;}" +
        ".sidebar-divider{height:1px;background:#333;margin:8px 16px;}" +
        ".main-content{margin-left:190px;transition:margin-left .15s;}" +
        "#sidebar.collapsed ~ .main-content{margin-left:56px;}" +
        ".brand-link{color:inherit;text-decoration:none;}" +
        ".section-title{padding:20px 24px 0;font-size:15px;color:#333;}" +
        // Preview modal
        ".preview-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:1000;align-items:center;justify-content:center;}" +
        ".preview-overlay.open{display:flex;}" +
        ".preview-box{background:#fff;border-radius:10px;max-width:92vw;max-height:90vh;width:900px;display:flex;flex-direction:column;overflow:hidden;}" +
        ".preview-header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #e2e4e8;gap:12px;}" +
        ".preview-title{font-weight:600;font-size:14px;word-break:break-word;}" +
        ".preview-header-actions{display:flex;align-items:center;gap:14px;flex-shrink:0;}" +
        ".preview-download{color:#2563eb;text-decoration:none;font-size:13px;}" +
        ".preview-close{background:none;border:none;font-size:22px;line-height:1;cursor:pointer;color:#555;padding:0;}" +
        ".preview-body{padding:0;overflow:auto;flex:1;display:flex;align-items:center;justify-content:center;background:#fafafa;min-height:200px;}" +
        ".preview-body img{max-width:100%;max-height:80vh;object-fit:contain;}" +
        ".preview-body video{max-width:100%;max-height:80vh;}" +
        ".preview-body audio{width:90%;margin:40px;}" +
        ".preview-body iframe{width:100%;height:80vh;border:none;}" +
        ".preview-body pre{white-space:pre-wrap;word-break:break-word;padding:20px;text-align:left;width:100%;box-sizing:border-box;font-family:Menlo,Consolas,monospace;font-size:13px;margin:0;}" +
        ".preview-nopreview{padding:60px 30px;text-align:center;color:#666;}" +
        // Shell (tab bar + iframe layout)
        ".shell-main{display:flex;flex-direction:column;height:100vh;box-sizing:border-box;}" +
        ".page-content{margin:0;}" +
        ".tabbar{display:flex;align-items:flex-end;gap:4px;background:#eef0f2;border-bottom:1px solid #d5d8dc;" +
          "padding:6px 8px 0;overflow-x:auto;flex-shrink:0;}" +
        ".tab{display:flex;align-items:center;gap:8px;background:#e4e6e9;border:1px solid #d5d8dc;border-bottom:none;" +
          "border-radius:8px 8px 0 0;padding:7px 6px 7px 12px;font-size:13px;cursor:pointer;white-space:nowrap;color:#555;}" +
        ".tab.active{background:#fff;color:#111;font-weight:600;}" +
        ".tab-title{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:130px;}" +
        ".tab-close{font-size:15px;line-height:1;color:#888;padding:0 4px;border-radius:4px;}" +
        ".tab-close:hover{color:#c00;background:rgba(0,0,0,.06);}" +
        ".tab-new{padding:7px 12px;font-size:16px;cursor:pointer;color:#555;background:none;border:none;align-self:center;}" +
        ".tab-new:hover{color:#111;}" +
        ".tabcontent{flex:1;position:relative;background:#fff;}" +
        ".tabcontent iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:none;display:none;background:#fff;}" +
        ".tabcontent iframe.active{display:block;}" +
        // Address bar (press "/" to open)
        ".address-bar-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:3000;" +
          "align-items:flex-start;justify-content:center;padding-top:14vh;}" +
        ".address-bar-overlay.open{display:flex;}" +
        ".address-bar-box{background:#fff;border-radius:10px;box-shadow:0 10px 40px rgba(0,0,0,.25);" +
          "width:560px;max-width:90vw;display:flex;align-items:center;padding:14px 18px;gap:8px;}" +
        ".address-bar-prefix{color:#999;font-family:Menlo,Consolas,monospace;font-size:16px;}" +
        "#addressBarInput{flex:1;border:none;outline:none;font-size:16px;font-family:Menlo,Consolas,monospace;}" +
        ".address-bar-hint{color:#999;font-size:11px;white-space:nowrap;}" +
        // Right-click context menu
        ".context-menu{display:none;position:fixed;z-index:2000;background:#fff;border:1px solid #d5d8dc;" +
          "border-radius:8px;box-shadow:0 6px 20px rgba(0,0,0,.15);min-width:180px;padding:6px 0;font-size:13px;}" +
        ".context-menu.open{display:block;}" +
        ".context-menu-item{padding:8px 16px;cursor:pointer;color:#222;}" +
        ".context-menu-item:hover{background:#f0f5ff;color:#2563eb;}" +
        ".context-menu-divider{height:1px;background:#eee;margin:5px 0;}" +
        // Move-to folder picker modal
        ".move-modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:1500;align-items:center;justify-content:center;}" +
        ".move-modal-overlay.open{display:flex;}" +
        ".move-modal-box{background:#fff;border-radius:10px;width:380px;max-width:90vw;max-height:70vh;padding:18px;display:flex;flex-direction:column;}" +
        ".move-modal-box h3{margin:0 0 12px;font-size:15px;}" +
        ".move-modal-box input{padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;margin-bottom:10px;}" +
        ".move-folder-list{overflow-y:auto;flex:1;border:1px solid #eee;border-radius:6px;min-height:120px;}" +
        ".move-folder-item{padding:8px 12px;cursor:pointer;font-size:13px;}" +
        ".move-folder-item:hover{background:#f0f5ff;}" +
        ".move-folder-empty{padding:16px;color:#888;font-size:13px;text-align:center;}" +
        ".move-modal-actions{margin-top:12px;text-align:right;}" +
        ".move-modal-actions button{padding:7px 14px;border:1px solid #c7cbd1;background:#fff;border-radius:6px;cursor:pointer;font-size:13px;}" +
        // Type-filter chips
        ".filter-chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;}" +
        ".chip{padding:5px 12px;border-radius:14px;background:#eef0f2;color:#555;font-size:12px;cursor:pointer;}" +
        ".chip:hover{background:#e0e3e7;}" +
        ".chip.active{background:#2563eb;color:#fff;}" +
        "</style>";
}
