# TODO — continue here

## Just added, NOT tested at all — verify these first

Unlike previous sessions, this one had no working `javac`/JDK and no network
access in the sandbox, so nothing below was compiled or run — only carefully
reviewed by hand (brace/paren/quote balance, tracing call sites). Please
build it for real before trusting it. `build/` and `FileDashboard.jar` were
deleted since they're now stale (built from old source) — rebuild via
`build-jar.bat` or BlueJ.

1. **Favicon** — `Styles.FAVICON` (inline SVG data URI, change to a different better favicon suited to the theme),
prepended to `Styles.CSS` so every page picks it up automatically. Just
open the app and check the browser tab icon.
2. **Draggable/reorderable tabs** — `ShellScript.java`: tabs are now
`draggable=true` with `dragstart`/`dragover`/`dragend` handlers that move
the tab live during drag and re-sync `shellTabs` (and localStorage) from
the final DOM order on `dragend`. Verify: drag a tab to a new position,
reload the page, order should stick. Also verify dragging doesn't
interfere with clicking a tab or its close (×) button.
3. **"Open in Viewer" in the preview modal** — `PageScripts.java`:
`openPreview()` now takes a `textlike` param (5th arg) and shows an "Open
in Viewer" button next to Download for pdf/text-like files, wired to the
same `/viewer` route as the right-click menu. Updated all 4 call sites
(dblclick, `open-item` menu action, arrow-key nav, search suggestions).
Verify each call site still opens the right thing, and that the new
button doesn't show for non-viewer-eligible types (images/audio/video).
4. **Trash: browsing into a deleted folder without restoring it** — this was
a real gap, not just a missing nicety: `TrashBrowseHandler.java` (new,
`/trash-browse?id=\&sub=`) and `TrashFileHandler.java` (new,
`/trash-file?id=\&sub=\&mode=`) let you look inside (and download from) a
trashed folder in place, reading straight out of `Config.TRASH\_DIR`
without touching `PathUtil`'s ROOT\_DIR-anchored world. Registered in
`FileServer.java`. `TrashManager.get(id)` added as a public accessor.
`TrashHandler.java` cards now carry `data-isdir`; double-click and the
right-click "Open" item on a trashed folder now go to `/trash-browse`
instead of doing nothing. Verify: delete a folder with stuff inside it,
open it from the Recycle Bin without restoring, browse a few levels deep,
download a file from inside it, and confirm Restore/Delete forever still
work both from the Recycle Bin grid and from inside `/trash-browse` itself.
5. **"Recently downloaded" not updating** — root cause: every open Browse
tab holds a long-lived `/events` SSE connection (one per tab, for as long
as the tab exists), and browsers cap concurrent connections per origin at
6 over HTTP/1.1 (all `com.sun.net.httpserver.HttpServer` speaks — no
HTTP/2). With a few tabs open, the Dashboard's own long-held
`/dashboard-events` connection could get stuck queued behind them and
never actually open, so it would never receive the "something changed"
push. Rewrote `DashboardEventsHandler.java` from an SSE stream into a
plain version-poll endpoint (`{"version": N}`), and `HomeHandler.java`'s
`dashboardRefreshScript()` now polls it every 3s via `fetch()` instead of
holding a connection open. Verify: open 5-6 Browse tabs (to actually
reproduce the old failure), download a file from one of them, and check
the Dashboard tab's "Recently downloaded" updates within \~3s without a
manual reload. Also worth eyeballing whether `/events` (per-folder,
unchanged this session) has the same latent problem for heavy multi-tab
use — not fixed here since it wasn't the one reported broken, but same
underlying cause.
6. **Address bar hierarchical dropdown** — turned out to already be fully
built (`ShellScript.java` + `SubfoldersHandler.java`), and TODO.md said it
was tested last session. Nothing changed here.

## Known gaps / things to watch for

* `CODE\_LANG\_MAP` in `PageScripts.java` (JS) and `CodeLanguageUtil.java`
(server) are two separate hand-kept-in-sync lists — if you add a language
to one, add it to the other too.
* Address bar / Move-to modal both hit `/subfolders` a lot; fine for normal
folders but untested against a folder with thousands of entries.
* `/trash-browse` and `/trash-file` are read-only by design (no
rename/move/upload while inside the trash) — that's intentional, not an
oversight, but worth confirming it's still what's wanted once you've used
it for real.
* The `/events` per-folder SSE connection-limit issue (see item 5) is still
latent for Browse tabs themselves if someone routinely keeps many open.

## Testing method used previous sessions (not available this one)

Built + ran the real server in the sandbox, hit endpoints with curl, and for
JS logic used jsdom (Node) loading the actual rendered HTML/JS and firing
real DOM events. That caught several real bugs in earlier sessions and
would be worth doing again on this batch before considering it done —
this session could only do static review.

