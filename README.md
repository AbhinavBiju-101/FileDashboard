# File Dashboard

A grid-based, local file explorer you open in your browser — built with plain
`com.sun.net.httpserver` (part of the JDK), no external libraries, no build
tool. Designed to open directly as a BlueJ project, or run as a standalone
jar with optional Windows autostart.

## The app window

Opening `http://localhost:8080` loads the **shell**: a pinned sidebar and a
tab bar. Sidebar links (Home, Dashboard, Recycle Bin, Settings, classic
folders) navigate the **current tab in place** - clicking around doesn't
spawn new tabs. Only the **+** button creates a genuinely new tab, opening
the **Quick Start** page. Tab titles update themselves to match whatever
you've navigated into. Press **`/`** anywhere to open a floating address bar
and jump straight to a folder by typing its path.

- **Dashboard tab** — quick-launch: **Frequently viewed** files (ranked by
  actual view count, not just recency), Recently downloaded files, and your
  most-visited folders — each a horizontally-scrolling row, capped at a
  configurable max (20 by default).
- **Browse tabs** — the folder grid, rooted at **Settings → Home folder**
  (your whole home drive by default, but changeable without recompiling).
- **Search** — recursive filename search with a live-suggestion dropdown,
  ranked by your actual activity.
- **Viewer tabs** — a dedicated full-tab reading view for PDFs and any
  text-based file (see below) - opened via right-click "Open Viewer".
- **Recycle Bin tab** — everything you've deleted, restorable, until emptied.
- **Settings tab** — change the Home folder, toggle autostart, adjust
  dashboard limits, and more (see below).

## Cards, selection, and the right-click menu

Cards show just an icon/thumbnail and a name. Every action lives in a
right-click menu.

- **Single click** selects. **Ctrl/Cmd+click** adds to selection.
  **Shift+click** selects a range. Click empty space to clear selection.
- **Double-click** opens it.
- **Right-click a card** shows: Open, Open Viewer (if applicable), Open in
  new tab, Download, Rename, Duplicate, Move to..., Delete. Multi-select
  first for group actions (zip, move, delete).
- **Right-click empty grid space** shows folder-level actions: New folder
  here, Download this folder as `.zip`.
- The menu closes reliably on outside clicks, scrolling, or focus leaving
  the tab entirely (e.g. clicking the sidebar) - it doesn't get "stuck".
- **Type filter chips** (All / Images / PDFs / Docs / Video / Audio /
  Archives / Other) above the grid.

## Previews and the Viewer

Clicking "View" opens a modal right there in the tab: images, PDFs, audio,
video, and **any text-based file** render inline. **Left/Right arrow keys**
step to the previous/next file in the folder. PDFs get an **Edit ↗** button
opening the file in a new browser tab (your browser's own PDF handling -
Firefox's editor tools, an Adobe extension if installed, etc.).

**"Open Viewer"** (right-click) opens a **full app tab** instead - wide,
minimal padding, meant for actually reading. Includes Previous/Next
navigation between sibling files of the same kind, and a small Markdown
renderer for `.md` files.

**Any text-based file gets full support - not just a hardcoded list.**
Unrecognized extensions (a custom format, a config file with no extension,
etc.) are sniffed by reading the actual file content and checking whether
it looks like text (no NUL bytes, high printable-character ratio) - so
something like a homegrown `.vcanvas` format works exactly like `.txt` or
`.py` would, no code changes needed for every possible extension.

## Safety: the Recycle Bin

Deleting (single or bulk) moves items to a hidden trash folder instead of
removing them immediately. Restore any time, or empty the bin permanently.

## Settings

Open via the sidebar. Currently configurable:
- **Home folder** — override which folder "Home" opens and browsing is
  scoped to, without editing `Config.java` or recompiling.
- **Autostart at login** (Windows) — toggle directly from the page; wraps
  the same Scheduled Task mechanism as `install-autostart.bat`.
- **Dashboard limits** — max items per Dashboard section.
- **Live folder refresh** — turn the per-tab auto-refresh on/off.

Ideas for more settings (listed on the page, not yet built): dark mode,
default sort order, thumbnail size, in-UI access token management,
Recycle Bin auto-purge, default upload folder, port number.

## Running as a jar, and autostarting on Windows

1. **`build-jar.bat`** — compiles and packages `FileDashboard.jar`. (Or in
   BlueJ: *Project → Create Jar File*, `FileServer` as main class.)
2. **`install-autostart.bat`** — registers a per-user logon Scheduled Task
   (`javaw`, no console window), starts it immediately too. No admin needed.
3. Toggle it later from the Settings page, or with `uninstall-autostart.bat`.
4. **`stop.bat`** stops whatever's listening on port 8080.

Logging goes to `~/.filedashboard/server.log` in addition to any console,
since a `javaw`-launched process has none. Launching a second copy while one
is already running is detected and reported cleanly rather than crashing.

## Setup (BlueJ / from source)

1. Open the `FileDashboard` folder as a BlueJ project (`package.bluej` included).
2. Compile everything (*Project → Compile*).
3. Right-click **`FileServer`** → `void main(String[] args)` → pass `null`.
4. Open **http://localhost:8080**.

## How the interesting parts work

- **The "Forbidden" bug** (`PathUtil.java`) - the path-containment check used
  to canonicalize both the root and the requested path (`File.getCanonicalFile()`),
  which also follows symlinks/junctions. Windows very often has Desktop/
  Documents/Pictures redirected via NTFS junction points (e.g. OneDrive's
  "Known Folder Move"), and canonicalizing could make an ordinary subfolder
  resolve somewhere that no longer looked "under" the root, incorrectly
  tripping the check. Fixed by switching to `Path.normalize()` - lexical
  collapsing of `..`/`.` segments with no filesystem access or symlink
  resolution at all. Verified by actually reproducing the bug (built a
  symlink pointing outside the root, confirmed the old code threw
  "Forbidden"), then confirming the fix resolves it while real `../..`
  traversal attempts are still correctly blocked.
- **Settings** (`Settings.java`) - runtime-configurable, persisted to
  `settings.json`, same JSON pattern as `RecentActivity`. Every place that
  used to reference `Config.ROOT_DIR` directly now goes through
  `Settings.rootDir()` instead, so an override actually takes effect
  everywhere consistently (sidebar shortcuts, search, zip, trash restore,
  not just the security boundary).
- **Autostart from Settings** (`AutostartManager.java`) - wraps the same
  `schtasks` commands as the `.bat` scripts via `ProcessBuilder`, finding its
  own jar path through `getProtectionDomain().getCodeSource()` (the standard
  "where am I actually running from" technique in Java) and live-querying
  Task Scheduler rather than trusting a possibly-stale stored flag.
- **Address bar** (`ShellScript.java`) - pressing `/` works from inside any
  tab's iframe via `parent.openAddressBar()`, guarded against hijacking
  normal typing in text inputs. Typed paths get the current root's absolute
  path stripped if present (handles pasting a full path, Windows backslashes
  included), so both relative and absolute-style input work.
- **Frequency-based ranking** (`RecentActivity.java`) - view counts are
  tracked separately from the recency-based MRU cache (which still feeds
  search suggestions, where "just looked at this" is a useful signal even
  for a single view) - so "Frequently viewed" genuinely ranks by how often
  a file is opened, not disguised recency.
- **Universal text viewer** (`TextSniffer.java`, `ViewabilityUtil.java`) -
  images/PDF/audio/video are still decided by extension (sniffing wouldn't
  help identify a binary format), but text detection falls back to reading
  the actual file content when the extension isn't recognized, rather than
  requiring every possible text extension to be hardcoded.
- **Hardened context menu dismissal** (`PageScripts.java`) - since each page
  lives inside a shell iframe, a same-document "click outside" listener
  alone misses clicks on the sidebar/tab bar (different document entirely).
  `window.addEventListener('blur', ...)` catches focus leaving the iframe
  for any reason; scroll and mousedown-outside handle the rest.

## Files

| File | Purpose |
|---|---|
| `FileServer.java` | Entry point — thread pool, routes, startup logging |
| `Config.java` | Compile-time defaults (root, port, data/trash locations) |
| `Settings.java` | Runtime-configurable overrides, persisted |
| `AutostartManager.java` | Windows Scheduled Task management via `schtasks` |
| `AppShellHandler.java` / `ShellScript.java` | Main window: sidebar, tabs, address bar |
| `SidebarRenderer.java` | Pinned sidebar; navigates current tab |
| `HomeHandler.java` | Dashboard: frequency-ranked, scrollable, capped (`/dashboard`) |
| `QuickstartHandler.java` | Orientation page opened by the "+" button (`/quickstart`) |
| `SettingsHandler.java` | Settings page (`/settings`) |
| `BrowseHandler.java` | Folder grid, sort, filters, search box (`/browse`) |
| `SearchHandler.java` | Recursive filename search results (`/search`) |
| `SuggestHandler.java` / `SearchSuggester.java` | Ranked live search suggestions (`/suggest`) |
| `ViewerHandler.java` / `MarkdownLite.java` | Dedicated reading tab (`/viewer`) |
| `GridRenderer.java` | Shared minimal folder/file card rendering |
| `PageScripts.java` | Preview modal, selection, context menu, move picker, filters |
| `FileViewHandler.java` | Serves a file inline/download/preview, Range support (`/file`) |
| `FileOpsHandler.java` | Rename, duplicate, delete (→trash), move, create-folder (`/fileops`) |
| `TrashManager.java` / `TrashHandler.java` / `TrashOpsHandler.java` | Recycle Bin |
| `ZipDownloadHandler.java` / `ZipSelectionHandler.java` | Zip a folder or a selection |
| `FoldersHandler.java` | Folder list for the Move-to picker (`/folders`) |
| `ThumbnailHandler.java` | Image thumbnails, cached (`/thumbnail`) |
| `UploadHandler.java` / `MultipartParser.java` | File uploads (`/upload`) |
| `LiveUpdateHandler.java` | SSE + WatchService live refresh, respects Settings toggle |
| `RecentActivity.java` / `MiniJson.java` | Persisted activity tracking + JSON |
| `PathUtil.java` / `HiddenFileUtil.java` | Path safety (lexical normalize), dotfile filtering |
| `ViewabilityUtil.java` / `TextSniffer.java` / `MimeUtil.java` | View decisions, content sniffing, Content-Type |
| `AuthFilter.java` | Optional shared-token gate on every route |
| `Styles.java` | All CSS |
| `QueryUtil.java` | Query-string parsing helper |
| `build-jar.bat` / `install-autostart.bat` / `uninstall-autostart.bat` / `stop.bat` | Windows tooling |

## Notes / limitations

- PDFs/Office docs don't get real preview thumbnails.
- `MarkdownLite` is intentionally small — no tables, fenced code blocks, or links.
- Tabs live in `sessionStorage`; a new browser session starts with one clean Dashboard tab.
- Text-sniffing reads the first ~8KB of unrecognized files - reasonably fast, but a folder with hundreds of custom-extension files will do a little more I/O per listing than one with only recognized extensions.

## Ideas for next time

- Drag-and-drop upload
- Fenced code blocks in the Markdown renderer
- Dark mode, and the other settings listed on the Settings page
- Basic auth over HTTPS for real LAN sharing
