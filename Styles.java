public class Styles {
    // A folder glyph on the app's own dark-sidebar backdrop, with two small
    // "cards" tucked inside it to echo the grid-of-cards look every page
    // uses - inlined as an SVG data URI so no extra file or route is
    // needed. Sits at the front of CSS (below) so every page that appends
    // Styles.CSS - which is all of them - gets it automatically, rather
    // than needing to remember to add it handler-by-handler.
    public static final String FAVICON =
        "<link rel='icon' href=\"data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20viewBox='0%200%2024%2024'%3E" +
        "%3Crect%20width='24'%20height='24'%20rx='6'%20fill='%231f2328'/%3E" +
        "%3Cpath%20fill='%232563eb'%20d='M5%208a2%202%200%200%201%202-2h3l1.7%201.8H17a2%202%200%200%201%202%202V17a2%202%200%200%201-2%202H7" +
        "a2%202%200%200%201-2-2V8z'/%3E" +
        "%3Crect%20x='8.3'%20y='11.3'%20width='3.1'%20height='3.1'%20rx='0.7'%20fill='%23ffffff'%20fill-opacity='0.95'/%3E" +
        "%3Crect%20x='12.1'%20y='11.3'%20width='3.1'%20height='3.1'%20rx='0.7'%20fill='%23ffffff'%20fill-opacity='0.6'/%3E" +
        "%3C/svg%3E\">";

    public static final String CSS =
        FAVICON +
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

        // ---- Session Manager ("/sessions") ----
        ".session-intro{margin:12px 24px 0;color:#666;font-size:13px;line-height:1.5;max-width:640px;}" +
        ".session-list{padding:16px 24px 24px;display:flex;flex-direction:column;gap:10px;}" +
        ".session-row{background:#fff;border:1px solid #e2e4e8;border-radius:10px;padding:12px 16px;" +
          "display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;}" +
        ".session-info{min-width:0;}" +
        ".session-name-row{display:flex;align-items:center;gap:8px;flex-wrap:wrap;}" +
        ".session-name{font-size:14px;font-weight:600;overflow:hidden;text-overflow:ellipsis;}" +
        ".session-meta{font-size:12px;color:#888;margin-top:3px;}" +
        ".session-badge{font-size:11px;font-weight:600;padding:2px 8px;border-radius:99px;white-space:nowrap;}" +
        ".session-badge-current{background:#e6f4ea;color:#1a7f37;}" +
        ".session-badge-active{background:#fff1e6;color:#b5590a;}" +
        ".session-badge-unsaved{background:#fff3d6;color:#8a5a00;}" +
        ".session-actions{display:flex;gap:8px;flex-shrink:0;}" +
        ".session-btn{background:#f4f5f7;border:1px solid #d7dae0;color:#333;padding:6px 12px;" +
          "border-radius:6px;font-size:12px;cursor:pointer;}" +
        ".session-btn:hover{background:#eceef1;}" +
        ".session-btn-primary{background:#2563eb;border-color:#2563eb;color:#fff;}" +
        ".session-btn-primary:hover{background:#1d4ed8;}" +
        ".session-btn-danger:hover{background:#fdecea;border-color:#f3b4ae;color:#c00;}" +
        ".session-btn-warning{background:#fff8e6;border-color:#f0d99a;color:#8a5a00;}" +
        ".session-btn-warning:hover{background:#fdf0cc;}" +
        ".session-icon{vertical-align:middle;}" +
        ".session-btn:disabled{opacity:.45;cursor:not-allowed;}" +
        ".session-btn:disabled:hover{background:#f4f5f7;color:#333;}" +
        ".session-btn-primary:disabled:hover{background:#2563eb;color:#fff;}" +
        ".search-inline{display:flex;gap:6px;}" +
        ".search-suggest-wrap{position:relative;margin-left:auto;}" +
        ".search-suggestions{display:none;position:absolute;top:100%;left:0;right:0;margin-top:4px;" +
          "background:#fff;border:1px solid #e2e4e8;border-radius:8px;box-shadow:0 4px 14px rgba(0,0,0,.1);" +
          "z-index:50;max-height:280px;overflow-y:auto;}" +
        ".search-suggestions.open{display:block;}" +
        ".search-suggestion-item{display:flex;align-items:center;gap:8px;padding:8px 12px;cursor:pointer;font-size:13px;}" +
        ".search-suggestion-item:hover{background:#f4f5f7;}" +
        ".search-suggestion-icon{font-size:15px;flex-shrink:0;}" +
        ".code-highlighted,.code-raw{width:100%;margin:0;text-align:left;overflow:auto;box-sizing:border-box;}" +
        ".code-viewer{max-width:1400px;margin:0 auto;padding:0;}" +
        ".search-suggestion-divider{padding:6px 12px 4px;font-size:11px;color:#999;text-transform:uppercase;" +
          "letter-spacing:.03em;border-top:1px solid #f0f0f0;margin-top:2px;}" +
        ".search-inline input[type=text]{padding:6px 8px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
        ".search-inline button{padding:6px 10px;border:none;background:#e5e7eb;border-radius:6px;cursor:pointer;font-size:13px;}" +
        ".meta.path{color:#999;font-size:10px;word-break:break-word;}" +
        ".trash-expiry{color:#888;}" +
        ".trash-expiry.urgent{color:#c2410c;font-weight:600;}" +
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
        // The session switcher pinned to the bottom of the sidebar (see
        // SidebarRenderer.render()/ShellScript.java's shellToggleSessionMenu()).
        ".sidebar-session-switcher{display:flex;align-items:center;gap:10px;padding:10px 16px;" +
            "border-top:1px solid #333;color:#d1d5db;cursor:pointer;flex-shrink:0;}" +
        ".sidebar-session-switcher:hover{background:#2d333b;color:#fff;}" +
        ".sidebar-session-dot{width:8px;height:8px;border-radius:50%;background:#3b82f6;flex-shrink:0;}" +
        ".sidebar-session-dot.drive{background:#0ea54c;}" +
        ".sidebar-session-dot.unsaved{background:#d97706;}" +
        ".sidebar-session-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px;}" +
        ".sidebar-session-chevron{font-size:10px;color:#9ca3af;flex-shrink:0;}" +
        ".sidebar.collapsed .sidebar-session-switcher{padding:10px 0;justify-content:center;}" +
        // The popover itself is position:fixed (not nested under the
        // sidebar) so it's never clipped by .sidebar's own overflow-y:auto -
        // its position is computed and set in JS right before opening.
        ".sidebar-session-menu{display:none;position:fixed;z-index:2000;background:#fff;border:1px solid #d5d8dc;" +
            "border-radius:8px;box-shadow:0 8px 24px rgba(0,0,0,.18);min-width:240px;max-width:320px;" +
            "max-height:60vh;overflow-y:auto;padding:6px;}" +
        ".sidebar-session-menu.open{display:block;}" +
        ".sidebar-session-menu-item{display:flex;align-items:center;gap:8px;padding:8px 10px;border-radius:6px;" +
            "cursor:pointer;font-size:13px;color:#222;}" +
        ".sidebar-session-menu-item:hover{background:#f0f5ff;}" +
        ".sidebar-session-menu-item.current{background:#f0f5ff;cursor:default;}" +
        ".sidebar-session-menu-item.current:hover{background:#f0f5ff;}" +
        ".sidebar-session-menu-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
        ".sidebar-session-menu-badge{font-size:10px;font-weight:600;padding:1px 6px;border-radius:99px;flex-shrink:0;}" +
        ".sidebar-session-menu-badge.elsewhere{background:#fff1e6;color:#b5590a;}" +
        ".sidebar-session-menu-badge.unsaved{background:#fff3d6;color:#8a5a00;}" +
        ".sidebar-session-menu-divider{height:1px;background:#eee;margin:6px 4px;}" +
        ".sidebar-session-menu-action{color:#2563eb;font-weight:500;}" +
        ".sidebar:not(.drive-mode) .sidebar-mode-drive{display:none;}" +
        ".sidebar.drive-mode .sidebar-mode-local{display:none;}" +
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
        // Visible edge arrow buttons - the same navigation the ArrowLeft/
        // ArrowRight keys already drive (see navigatePreview in
        // PageScripts), just reachable with a click/tap too. Positioned at
        // the screen edges (fixed, not relative to preview-box) so they sit
        // clear of the box itself on anything wider than a phone; z-index
        // above the box in case a narrow viewport overlaps them.
        ".preview-nav-btn{position:fixed;top:50%;transform:translateY(-50%);width:44px;height:44px;" +
          "border-radius:50%;background:rgba(0,0,0,.35);color:#fff;border:none;font-size:18px;" +
          "cursor:pointer;z-index:1100;display:flex;align-items:center;justify-content:center;" +
          "transition:background .15s;}" +
        ".preview-nav-btn:hover{background:rgba(0,0,0,.6);}" +
        ".preview-nav-left{left:20px;}" +
        ".preview-nav-right{right:20px;}" +
        // .docx preview - rendered by mammoth.js into plain HTML with no
        // styling of its own, so it needs a readable typographic treatment
        // here (loosely matching the Viewer's .viewer-reading style).
        ".docx-loading{padding:60px;color:#888;}" +
        ".docx-preview{max-width:760px;width:100%;margin:0 auto;padding:32px 40px;font-size:15px;" +
          "line-height:1.7;color:#1f2328;text-align:left;box-sizing:border-box;}" +
        ".docx-preview h1{font-size:1.7em;margin:1em 0 .5em;}" +
        ".docx-preview h2{font-size:1.35em;margin:1em 0 .5em;}" +
        ".docx-preview h3{font-size:1.1em;margin:1em 0 .4em;}" +
        ".docx-preview p{margin:0 0 1em;}" +
        ".docx-preview table{border-collapse:collapse;margin:1em 0;}" +
        ".docx-preview td,.docx-preview th{border:1px solid #d5d8dc;padding:6px 10px;}" +
        ".docx-preview img{max-width:100%;height:auto;}" +
        ".docx-preview ul,.docx-preview ol{padding-left:24px;margin:0 0 1em;}" +
        // Shell (tab bar + iframe layout)
        ".shell-main{display:flex;flex-direction:column;height:100vh;box-sizing:border-box;}" +
        ".page-content{margin:0;}" +
        ".tabbar{display:flex;align-items:flex-end;gap:4px;background:#eef0f2;border-bottom:1px solid #d5d8dc;" +
          "padding:6px 8px 0;overflow-x:auto;flex-shrink:0;}" +
        ".tab{display:flex;align-items:center;gap:8px;background:#e4e6e9;border:1px solid #d5d8dc;border-bottom:none;" +
          "border-radius:8px 8px 0 0;padding:7px 6px 7px 12px;font-size:13px;cursor:grab;white-space:nowrap;color:#555;}" +
        ".tab.active{background:#fff;color:#111;font-weight:600;}" +
        ".tab.dragging{opacity:.4;cursor:grabbing;}" +
        ".tab.grouped{border-top:2px solid #6366d1;}" +
        ".tab-title{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:130px;}" +
        ".tab-close{font-size:15px;line-height:1;color:#888;padding:0 4px;border-radius:4px;}" +
        ".tab-close:hover{color:#c00;background:rgba(0,0,0,.06);}" +
        ".tab-new{padding:7px 12px;font-size:16px;cursor:pointer;color:#555;background:none;border:none;align-self:center;}" +
        ".shell-unsaved-badge{align-self:center;margin-left:6px;padding:4px 10px;font-size:12px;color:#8a5a00;" +
            "background:#fff3d6;border:1px solid #f0d18a;border-radius:12px;cursor:pointer;white-space:nowrap;flex-shrink:0;}" +
        ".shell-unsaved-badge:hover{background:#ffe8b3;}" +
        ".tab-new:hover{color:#111;}" +
        // Tab group ("folder") header - deliberately a single fixed accent
        // for every group (no per-group color picker), consistent with
        // .tab.grouped's top border above.
        ".tab-group-header{display:flex;align-items:center;gap:6px;background:#e6e8fb;border:1px solid #c7cbf5;" +
          "border-bottom:none;border-radius:8px 8px 0 0;padding:7px 10px;font-size:12px;font-weight:600;" +
          "color:#4147c4;cursor:pointer;white-space:nowrap;user-select:none;}" +
        ".tab-group-header:hover{background:#dadefa;}" +
        ".tab-group-toggle{font-size:10px;transition:transform .12s;}" +
        ".tab-group-header.collapsed .tab-group-toggle{transform:rotate(-90deg);}" +
        ".tab-group-header.dragging{opacity:.4;cursor:grabbing;}" +
        ".tab-group-name{max-width:120px;overflow:hidden;text-overflow:ellipsis;}" +
        ".tab-group-count{color:#7278d6;font-weight:500;}" +
        ".tabcontent{flex:1;position:relative;background:#fff;}" +
        ".tabcontent iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:none;display:none;background:#fff;}" +
        ".tabcontent iframe.active{display:block;}" +
        // Address bar (press "/" to open)
        ".address-bar-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:3000;" +
          "align-items:flex-start;justify-content:center;padding-top:14vh;}" +
        ".address-bar-overlay.open{display:flex;}" +
        ".address-bar-box{background:#fff;border-radius:10px;box-shadow:0 10px 40px rgba(0,0,0,.25);" +
          "width:560px;max-width:90vw;overflow:hidden;}" +
        ".address-bar-row{display:flex;align-items:center;padding:14px 18px;gap:8px;}" +
        ".address-bar-prefix{color:#999;font-family:Menlo,Consolas,monospace;font-size:16px;}" +
        "#addressBarInput{flex:1;border:none;outline:none;font-size:16px;font-family:Menlo,Consolas,monospace;}" +
        ".address-bar-hint{color:#999;font-size:11px;white-space:nowrap;}" +
        ".address-suggestions{display:none;max-height:260px;overflow-y:auto;border-top:1px solid #eee;}" +
        ".address-suggestions.open{display:block;}" +
        ".address-suggestion-item{padding:9px 18px;cursor:pointer;font-size:14px;}" +
        ".address-suggestion-item:hover,.address-suggestion-item.active{background:#f0f5ff;}" +
        // Right-click context menu
        ".context-menu{display:none;position:fixed;z-index:2000;background:#fff;border:1px solid #d5d8dc;" +
          "border-radius:8px;box-shadow:0 6px 20px rgba(0,0,0,.15);min-width:180px;padding:6px 0;font-size:13px;}" +
        ".context-menu.open{display:block;}" +
        ".context-menu-item{padding:8px 16px;cursor:pointer;color:#222;}" +
        ".context-menu-item:hover{background:#f0f5ff;color:#2563eb;}" +
        ".context-menu-divider{height:1px;background:#eee;margin:5px 0;}" +
        ".context-menu-item-disabled{color:#aaa;cursor:not-allowed;}" +
        ".context-menu-item-disabled:hover{background:none;color:#aaa;}" +
        // Move-to folder picker modal
        ".move-modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:1500;align-items:center;justify-content:center;}" +
        ".move-modal-overlay.open{display:flex;}" +
        ".move-modal-box{background:#fff;border-radius:10px;width:380px;max-width:90vw;max-height:70vh;padding:18px;display:flex;flex-direction:column;}" +
        ".move-modal-box h3{margin:0 0 12px;font-size:15px;}" +
        ".move-modal-box input{padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;margin-bottom:10px;}" +
        ".move-folder-list{overflow-y:auto;flex:1;border:1px solid #eee;border-radius:6px;min-height:120px;}" +
        ".move-breadcrumb{font-size:13px;color:#555;margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #eee;}" +
        ".move-crumb{cursor:pointer;color:#2563eb;}" +
        ".move-crumb:hover{text-decoration:underline;}" +
        ".move-folder-item{padding:8px 12px;cursor:pointer;font-size:13px;}" +
        ".move-folder-item:hover{background:#f0f5ff;}" +
        ".move-folder-empty{padding:16px;color:#888;font-size:13px;text-align:center;}" +
        ".move-modal-actions{margin-top:12px;text-align:right;display:flex;justify-content:flex-end;gap:8px;}" +
        ".move-modal-actions button{padding:7px 14px;border:1px solid #c7cbd1;background:#fff;border-radius:6px;cursor:pointer;font-size:13px;}" +
        ".move-modal-actions .move-confirm{background:#2563eb;color:#fff;border-color:#2563eb;}" +
        ".move-modal-actions .move-confirm:hover{background:#1d4ed8;}" +
        // Type-filter chips
        ".filter-chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;}" +
        ".chip{padding:5px 12px;border-radius:14px;background:#eef0f2;color:#555;font-size:12px;cursor:pointer;}" +
        ".chip:hover{background:#e0e3e7;}" +
        ".chip.active{background:#2563eb;color:#fff;}" +
        // Undo/redo action toast (move, rename, delete) and the shell's
        // "closed tab" toast - both share this container/toast styling.
        // The container just stacks its children bottom-up with a gap;
        // each toast fades in on its own and is removed by JS after 5s (or
        // on click), so several can be visible together instead of one
        // slot overwriting itself.
        ".action-toast-container{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);" +
          "display:flex;flex-direction:column;align-items:center;gap:8px;z-index:2500;pointer-events:none;}" +
        ".action-toast{display:flex;align-items:center;pointer-events:auto;" +
          "background:#1f2328;color:#fff;padding:10px 10px 10px 18px;border-radius:8px;" +
          "gap:14px;font-size:13px;box-shadow:0 6px 20px rgba(0,0,0,.25);animation:actionToastIn .18s ease-out;}" +
        "@keyframes actionToastIn{from{opacity:0;transform:translateY(6px);}to{opacity:1;transform:translateY(0);}}" +
        ".action-toast-message{white-space:nowrap;}" +
        ".action-toast-btn{background:none;border:none;color:#7db2ff;cursor:pointer;font-size:13px;" +
          "font-weight:600;padding:0;white-space:nowrap;}" +
        ".action-toast-btn:hover{text-decoration:underline;}" +
        ".action-toast-close{background:none;border:none;color:#9aa1ab;cursor:pointer;font-size:16px;" +
          "line-height:1;padding:0;}" +
        // "On this day" / past-week timeline (dashboard home)
        ".timeline-section{padding:0 24px 8px;}" +
        ".timeline-list{display:flex;flex-direction:column;gap:2px;}" +
        ".timeline-row{display:flex;align-items:center;gap:10px;padding:8px 10px;border-radius:8px;" +
          "text-decoration:none;color:inherit;cursor:pointer;}" +
        ".timeline-row:hover{background:#fff;}" +
        ".timeline-icon{font-size:18px;flex-shrink:0;width:22px;text-align:center;}" +
        ".timeline-name{font-size:13px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
        ".timeline-meta{font-size:11px;color:#999;flex-shrink:0;}" +
        ".timeline-year{font-size:11px;color:#2563eb;font-weight:600;flex-shrink:0;background:#eef2ff;" +
          "padding:2px 8px;border-radius:10px;}" +
        // ---- "This week" stats chart ----
        // A small stacked bar chart (viewed/downloaded/uploaded, one bar per
        // of the last 7 days) that doubles as a filter for the file-card row
        // beneath it - clicking a day narrows the row to that day's cards,
        // clicking again (or "All days") clears the filter. Deliberately
        // plain divs/CSS rather than a charting library, consistent with
        // the rest of the app having no external JS dependencies besides
        // the one already-present highlight.js CDN include.
        ".week-wrap{padding:4px 24px 8px;}" +
        ".week-summary{font-size:13px;color:#666;margin-bottom:14px;}" +
        ".week-summary strong{color:#1f2328;}" +
        ".week-chart{display:flex;align-items:flex-end;gap:10px;height:110px;padding:0 2px 8px;border-bottom:1px solid #e2e4e8;}" +
        ".week-bar-col{display:flex;flex-direction:column;align-items:center;flex:1;cursor:pointer;height:100%;justify-content:flex-end;border-radius:6px;padding-top:4px;}" +
        ".week-bar-col:hover{background:#eef2ff;}" +
        ".week-bar-col.active{background:#e0e9ff;}" +
        ".week-bar-col.empty{cursor:default;}" +
        ".week-bar-col.empty:hover{background:none;}" +
        ".week-bar{width:22px;display:flex;flex-direction:column-reverse;border-radius:4px 4px 0 0;overflow:hidden;min-height:2px;}" +
        ".week-seg-viewed{background:#2563eb;}" +
        ".week-seg-downloaded{background:#16a34a;}" +
        ".week-seg-uploaded{background:#d97706;}" +
        ".week-bar-count{font-size:11px;color:#999;margin-top:4px;height:14px;}" +
        ".week-bar-col.active .week-bar-count{color:#2563eb;font-weight:600;}" +
        ".week-day-label{font-size:12px;color:#555;margin-top:6px;}" +
        ".week-bar-col.active .week-day-label{color:#2563eb;font-weight:600;}" +
        ".week-legend{display:flex;gap:16px;margin:12px 2px 0;font-size:12px;color:#666;align-items:center;}" +
        ".week-legend-item{display:flex;align-items:center;gap:6px;}" +
        ".week-legend-dot{width:9px;height:9px;border-radius:50%;display:inline-block;}" +
        ".week-clear-filter{margin-left:auto;color:#2563eb;text-decoration:none;font-size:12px;display:none;}" +
        ".week-clear-filter.visible{display:inline;}" +
        ".week-cards{margin-top:14px;}" +
        "</style>";
}
