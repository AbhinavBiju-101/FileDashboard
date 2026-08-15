# File Dashboard

A grid-based, local file explorer you open in your browser — built with plain
`com.sun.net.httpserver` (part of the JDK), no external libraries, no build tool.
Designed to open directly as a BlueJ project.

## What it does

- Browses a folder (and its subfolders) as a visual grid, like a mini Google Drive.
- Real thumbnails for images (jpg/png/gif/bmp/webp), generated on the fly and cached.
- Other file types (PDF, docx, zip, mp3, mp4, txt, etc.) get a labeled icon tile.
- **View** opens a file inline in a new tab. **Download** forces a save-as.
- **Range requests** — video/audio can be seeked/scrubbed in the browser, and
  downloads can resume instead of restarting from zero.
- **Upload** drops a file straight into the folder you're viewing.
- **Sort** by name, size, or date, ascending or descending.
- **Recursive search** — find a file by name anywhere under the current folder.
- **Download a whole folder as a `.zip`**, streamed on the fly (no temp files,
  no practical size limit — it never holds the whole archive in memory).
- **Live auto-refresh** — if a file is added/changed/removed in the folder
  you're looking at (e.g. you drag something into it from Finder/Explorer),
  the page updates itself. No manual reload.
- **Optional access token** — one line in `Config.java` locks the whole
  dashboard behind a shared secret, if you ever expose it beyond `localhost`.
- Path-traversal protection — you can never browse outside the configured root.

## Setup

1. Open the `FileDashboard` folder as a project in BlueJ (`package.bluej` is already there).
2. Open **`Config.java`** and set `ROOT_DIR` to the folder you want to expose. By default
   it points at `~/Downloads`. Change the port if 8080 is taken.
3. Compile everything (BlueJ: right-click any class → *Compile*, or *Project → Compile*).
4. Right-click **`FileServer`** → `void main(String[] args)` → pass `null` (or leave it empty).
5. Open **http://localhost:8080** in your browser.

Leave the BlueJ run console open — closing it stops the server.

## How the interesting parts work

- **Live refresh** (`LiveUpdateHandler.java`) opens a Server-Sent Events stream
  and registers a `java.nio.file.WatchService` on the folder you're viewing.
  The OS notifies Java the instant something in that folder changes; Java
  pushes a one-line `data: refresh` message down the open connection; a tiny
  bit of JS on the page reloads it. This is why `FileServer.java` uses a real
  `Executors.newCachedThreadPool()` instead of the JDK's single-threaded
  default — an open SSE connection needs its own thread so it doesn't block
  everyone else.
- **Range requests** (`FileViewHandler.java`) parse the `Range: bytes=...`
  header, seek directly to that byte offset with `RandomAccessFile`, and
  return `206 Partial Content`. This is the actual mechanism that lets a
  `<video>` tag jump to the middle of a file instead of downloading
  everything up to that point first.
- **Zip download** (`ZipDownloadHandler.java`) walks the folder with
  `Files.walk` and pipes each file straight into a `ZipOutputStream` wrapped
  around the live HTTP response body — nothing is buffered on disk, so this
  scales to folders much bigger than available RAM.
- **Search** (`SearchHandler.java`) uses `Files.walk` with a filename filter,
  capped at 200 results, and reuses `GridRenderer` so results look identical
  to the normal grid, just labeled with which subfolder each match came from.
- **Auth** (`AuthFilter.java`) is a `com.sun.net.httpserver.Filter` attached
  to every route. If `Config.ACCESS_TOKEN` is set, it checks a query param or
  cookie before letting any handler run — otherwise it's a complete no-op.

## Files

| File | Purpose |
|---|---|
| `FileServer.java` | Entry point — starts the server, thread pool, and routes |
| `Config.java` | Root folder, port, optional access token |
| `PathUtil.java` | Resolves URL paths to real files, blocks directory traversal |
| `QueryUtil.java` | Tiny helper for reading query-string params |
| `Styles.java` | Inline CSS for the grid UI |
| `GridRenderer.java` | Shared folder/file card rendering (dashboard + search) |
| `DashboardHandler.java` | Renders the folder grid, sort toolbar, search box (`/`) |
| `FileViewHandler.java` | Serves a file inline/download, with Range support (`/file`) |
| `ThumbnailHandler.java` | Generates + caches image thumbnails (`/thumbnail`) |
| `UploadHandler.java` | Handles file uploads (`/upload`) |
| `MultipartParser.java` | Hand-written multipart/form-data parser |
| `SearchHandler.java` | Recursive filename search (`/search`) |
| `ZipDownloadHandler.java` | Streams a folder as `.zip` (`/zip`) |
| `LiveUpdateHandler.java` | SSE + WatchService live refresh (`/events`) |
| `AuthFilter.java` | Optional shared-token gate on every route |

## Notes / limitations

- PDFs/Office docs don't get real preview thumbnails (would need a library
  like Apache PDFBox, left out to keep this dependency-free) — they still
  open fine via "View".
- No auth by default — fine for `localhost`-only use. Set `Config.ACCESS_TOKEN`
  before exposing this on a shared network.
- Upload handles one file at a time; live-refresh watches one folder
  (non-recursively) at a time — the one currently open in the browser.

## Ideas for next time

- Drag-and-drop multi-file upload
- Rename / delete / new-folder actions
- In-browser text editor for `.txt`/`.md`/code files
- Basic auth over HTTPS with a self-signed cert for actual LAN sharing
