# File Dashboard

A grid-based, local file explorer you open in your browser — built with plain
`com.sun.net.httpserver` (part of the JDK), no external libraries, no build
tool. Designed to open directly as a BlueJ project.

## The app window

Opening `http://localhost:8080` loads the **shell**: a pinned sidebar and a
tab bar, like a mini file-manager window. Each tab is independent - open
Downloads in one, Documents in another, and a search in a third, all at once.
Clicking something in the sidebar always opens a new tab (or focuses one
that's already open to that folder); clicking inside a tab navigates that
tab, same as clicking a link in a browser tab. Tab titles update themselves
automatically to match whatever folder you've navigated into.

- **Dashboard tab** — quick-launch: recently viewed files, recently
  downloaded files, your most-visited folders. Persisted to disk, so it
  survives a restart.
- **Browse tabs** — the folder grid, rooted at your home directory, with
  Desktop/Documents/Downloads/etc. reachable as normal subfolders.
- **Search** — recursive filename search with a live-suggestion dropdown as
  you type, ranked by your actual activity (a file you open often will
  outrank one you've never touched, even with the same partial match).

## What it does

- **Persistent activity** — recent views, downloads, and folder visits are
  saved to `~/.filedashboard/activity.json` (hand-written JSON, no
  libraries) and reloaded on startup.
- **In-app previews, Drive-style** — clicking "View" opens a modal overlay
  right there in the tab: images, PDFs (via the browser's built-in viewer),
  audio, video, and text/code all render inline. PDFs also get an **Edit ↗**
  button that opens the raw file in a new browser tab — that hands it off to
  whatever your browser does with PDFs natively (Firefox's PDF.js editing
  tools, Edge's markup tools, or an Adobe extension if you have one
  installed). A webpage can't force a *specific* app to open a file — that's
  a browser/OS-level association outside what JavaScript is allowed to
  control — so this is the honest version: it hands off to your browser's
  own PDF handling rather than pretending to launch Adobe directly. Nothing
  you can't actually preview ever gets silently downloaded as raw bytes —
  unsupported types show a clear "no preview" message with a Download button
  instead.
- **Rename, duplicate, delete** — every file and folder card has these
  actions built in (folders recursively, with automatic "(copy)", "(copy 2)"
  naming on duplicate). Your `.filedashboard` data folder is hidden from
  listings and protected from all three.
- **Smart search suggestions** — `/suggest` combines your recent activity
  with a live filesystem walk, so the dropdown gets more useful for you
  specifically the more you use the app.
- Real thumbnails for images, generated on the fly and cached.
- **Range requests** — video/audio can be seeked/scrubbed, downloads resume.
- **Sort** by name, size, or date. **Zip download** of a whole folder,
  streamed on the fly. **Live auto-refresh** per tab via `WatchService` +
  Server-Sent Events. **Upload** straight into the folder you're viewing.
- **Optional access token** (`Config.ACCESS_TOKEN`) if you ever expose this
  beyond `localhost`.
- Path-traversal protection, hidden-file filtering everywhere (listings,
  search, zip).

## Setup

1. Open the `FileDashboard` folder as a project in BlueJ (`package.bluej` is already there).
2. Open **`Config.java`** if you want to change the root folder, port, or data location.
3. Compile everything (BlueJ: right-click any class → *Compile*, or *Project → Compile*).
4. Right-click **`FileServer`** → `void main(String[] args)` → pass `null` (or leave it empty).
5. Open **http://localhost:8080** — you'll land on the app shell with one Dashboard tab open.

Leave the BlueJ run console open — closing it stops the server.

## How the interesting parts work

- **Tabs** (`AppShellHandler.java`, `ShellScript.java`) - each tab is an
  `<iframe>` pointed at a content route (`/dashboard`, `/browse?path=...`,
  `/search?...`). Since everything is same-origin, the shell can read each
  iframe's `document.title` after it loads and use that as the tab label -
  no extra plumbing needed on the content-page side; navigating inside a tab
  just updates its own title naturally. Open tabs are remembered in
  `sessionStorage` so a shell reload doesn't lose them. This logic was
  tested with a real DOM (jsdom) simulating open/switch/close/restore, not
  just eyeballed.
- **Persistence** (`RecentActivity.java`, `MiniJson.java`) - a small
  hand-written JSON reader/writer (proper string escaping, since file paths
  can contain quotes and unicode) with atomic writes (temp file + rename) so
  a crash mid-save never corrupts `activity.json`.
- **Search suggestions** (`SearchSuggester.java`) - checks your recorded
  activity first (near-instant, and it's the more relevant signal), then
  falls back to a depth-capped filesystem walk for anything you haven't
  touched yet. Folders rank above files; exact prefix matches rank above
  substring matches.
- **Preview modal & file actions** (`PageScripts.java`) - one shared script
  embedded on every content page. A single `data-action` attribute on each
  card drives a delegated click handler, so there's no per-card inline JS to
  maintain. Renaming/duplicating/deleting just calls `/fileops` and reloads;
  because `WatchService` is already watching the folder, the live-refresh
  connection would catch the change anyway even without the reload.
- **View vs. Preview** (`ViewabilityUtil.java`, `MimeUtil.java`) - every
  extension is checked against a known-safe list before "View" is even
  wired up to open inline; anything else shows the no-preview message
  instead of the browser attempting (and failing at) rendering raw bytes.
- **Range requests** (`FileViewHandler.java`) - seeks directly to a byte
  offset with `RandomAccessFile` and returns `206 Partial Content`, which is
  what actually lets a `<video>` tag jump to the middle of a file.
- **Zip download** (`ZipDownloadHandler.java`) - pipes files straight into a
  `ZipOutputStream` wrapped around the live HTTP response, nothing buffered
  to disk.
- **Live refresh** (`LiveUpdateHandler.java`) - a Server-Sent Events stream
  backed by `java.nio.file.WatchService`. This is why `FileServer.java` uses
  a real thread pool - an open SSE connection needs its own thread.

## Files

| File | Purpose |
|---|---|
| `FileServer.java` | Entry point — thread pool, routes |
| `Config.java` | Root folder, port, access token, data directory |
| `AppShellHandler.java` | The main window: sidebar + tab bar (`/`) |
| `ShellScript.java` | Client-side tab manager (open/switch/close/restore) |
| `SidebarRenderer.java` | Pinned sidebar; classic-folder shortcuts open tabs |
| `HomeHandler.java` | Dashboard content: recent/frequent quick-launch (`/dashboard`) |
| `BrowseHandler.java` | Folder grid, sort, search box, zip link (`/browse`) |
| `SearchHandler.java` | Recursive filename search results (`/search`) |
| `SuggestHandler.java` / `SearchSuggester.java` | Ranked live search suggestions (`/suggest`) |
| `GridRenderer.java` | Shared folder/file card rendering |
| `PageScripts.java` | Preview modal + rename/duplicate/delete, shared by content pages |
| `FileViewHandler.java` | Serves a file inline/download/preview, with Range support (`/file`) |
| `FileOpsHandler.java` | Rename, duplicate, delete (`/fileops`) |
| `ThumbnailHandler.java` | Image thumbnails, cached (`/thumbnail`) |
| `UploadHandler.java` / `MultipartParser.java` | File uploads (`/upload`) |
| `ZipDownloadHandler.java` | Streams a folder as `.zip` (`/zip`) |
| `LiveUpdateHandler.java` | SSE + WatchService live refresh (`/events`) |
| `RecentActivity.java` / `MiniJson.java` | Persisted activity tracking + JSON |
| `PathUtil.java` / `HiddenFileUtil.java` | Path safety, dotfile filtering |
| `ViewabilityUtil.java` / `MimeUtil.java` | View-vs-preview decision, Content-Type resolution |
| `AuthFilter.java` | Optional shared-token gate on every route |
| `Styles.java` | All CSS |
| `QueryUtil.java` | Query-string parsing helper |

## Notes / limitations

- PDFs/Office docs don't get real preview thumbnails (would need a library
  like Apache PDFBox, left out to keep this dependency-free).
- No auth by default — fine for `localhost`-only use.
- Tabs live in `sessionStorage`, so a brand-new browser session starts with
  a clean single Dashboard tab (by design - it's a window, not a bookmark set).
- Upload and rename/duplicate/delete handle one item at a time.

## Ideas for next time

- Drag-and-drop upload, multi-select actions
- In-browser text editor for `.txt`/`.md`/code files
- Move files between folders (drag onto a sidebar shortcut)
- Basic auth over HTTPS with a self-signed cert for real LAN sharing
