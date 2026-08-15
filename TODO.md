# TODO — continue here

## Just added, NOT tested at all — verify these first
This session had no working `javac`/JDK in the sandbox either, so nothing
below was compiled or run as a real server. Node.js *was* available this
time, so every embedded-JS string (`ShellScript.java`'s `SCRIPT`,
`PageScripts.java`'s `SCRIPT`, and `SessionsHandler.java`'s `SCRIPT`) was
mechanically extracted and passed through `node --check` to at least confirm
they're syntactically valid JS - that catches typos/unbalanced
brackets/etc. but says nothing about runtime correctness (DOM ids that
don't exist, wrong argument order, and so on).
Please build it for real and click through these before trusting them.
`build/` and `FileDashboard.jar` are stale if present - rebuild via
`build-jar.bat` or BlueJ.

10. **Sessions** — every *browser tab* of File Dashboard is now its own
    "session": its own tab bar, its own groups, kept completely separate
    from any other browser tab of the app you have open. New
    `SessionsHandler.java` serves `/sessions`, a new sidebar entry between
    Dashboard and Home ("Sessions", clock icon) - lists every session ever
    created (`localStorage['fileDashboardSessions']`), lets you rename any
    of them, delete inactive ones from history, and reopen an inactive one
    into the current browser tab. The "Open" and "Delete" buttons are
    disabled for whichever session is open in *this* tab ("This tab" badge)
    or detected open in some *other* tab ("Open in another tab" badge) - the
    single-instance rule asked for. How it works: `ShellScript.java` now
    keys its saved tab/group state off `sessionStorage['fd-session-id']`
    rather than a single shared `localStorage` key - sessionStorage is
    scoped to one browser tab, so a brand-new browser tab always finds
    nothing there and mints a fresh session id (openTab('/dashboard')
    fires like a first run), while reloading (or navigating within) the
    SAME browser tab keeps finding its own id and restores normally. Which
    sessions are "active" is tracked via a lightweight heartbeat
    (`localStorage['fileDashboardSessionHeartbeats']`) each tab stamps
    every 4s and releases on `pagehide` - a session is only considered
    active if stamped within the last 10s, so a crashed/force-closed tab's
    session frees itself back up within about 10 seconds rather than being
    permanently stuck. Reopening a session calls
    `window.parent.shellLoadSession(id)` from the Sessions page's iframe
    into the shell frame, which tears down the current tab's iframes and
    rebuilds from the target session's saved tabs. Verify: open the app in
    two separate browser tabs, confirm they show as two different sessions
    in Sessions with independent tab bars; from one, try to reopen the
    other's session and confirm Open is disabled while it's active; close
    that other browser tab entirely, wait ~10s, refresh Sessions, and
    confirm Open becomes enabled and actually swaps this tab onto it;
    rename a session (including the one currently open in this tab) and
    confirm the name sticks and doesn't get clobbered by the next
    autosave; reload a single browser tab a few times and confirm it keeps
    landing on the same session rather than creating a new one each time.
    **Known gap, not solved this session:** some browsers copy
    sessionStorage when you explicitly "duplicate" a tab (not a normal new
    tab/window) - that would hand two real browser tabs the same session
    id and defeat single-instance enforcement between exactly those two,
    since neither would ever see the other as "another" tab locally. Worth
    a second pass if that turns out to matter in practice (e.g. detecting a
    second heartbeat writer for the same id and forking a fresh id for the
    later one).

7. **Collapsible tab groups ("folders")** — `ShellScript.java`: tabs can now
   be clustered into named, collapsible groups in the tab bar. Right-click a
   tab for "New group" / "Add to '<existing group>'" / "Rename group" /
   "Remove from group" / "Ungroup"; right-click a group's header for
   "Rename group" / "Ungroup" directly. Double-click a header to rename.
   Click a header to collapse/expand. Drag a tab onto a header to join that
   group; dragging tabs around otherwise still reorders as before, with
   `shellNormalizeGroups()` pulling each group's members back into a
   contiguous run afterward so a group can't end up visually split apart.
   Every group uses the same fixed accent color (`.tab-group-header` /
   `.tab.grouped` in `Styles.java`) - deliberately not a per-group color

   picker, per what was asked for. Persisted in `localStorage` alongside
   `shellTabs` (see `shellSaveState`/`shellLoadState`). The tab-render path
   was rewritten around this: `shellCreateTabElement` (old, direct DOM
   insertion) became `shellBuildTabElement` (builds the node) +
   `shellCreateTab` (creates the iframe, then calls `shellRenderTabBar()`,
   which now rebuilds the whole bar's chip order from `shellTabs`/
   `shellGroups` every time rather than patching the DOM by hand). Verify:
   create a group with 2-3 tabs, collapse/expand it (including while the
   active tab is inside it - should hop to another tab on collapse), rename
   it, reload the page and confirm it's still there, drag a fourth tab onto
   the header to add it, close a grouped tab and reopen it via the toast
   (should land back in the group), and confirm closing every tab in a
   group deletes the (now-empty) group automatically.

8. **"Refresh" on right-click empty grid space** — `PageScripts.java`:
   `showFolderContextMenu()` now has a "Refresh" item above "New folder
   here", wired to a new `refreshCurrentFolder()` that `fetch()`es the
   current tab's own URL, parses it with `DOMParser`, and swaps in just the
   refreshed `.grid[data-current-path]` element - deliberately *not*
   `location.reload()`, since that reloads the top-level shell page and
   tears down every other open tab's iframe (and whatever preview state
   they had) along with it. Verify: open two Browse tabs on different
   folders, add a file to one folder from outside the app, right-click empty
   space in that tab and choose Refresh - the new file should appear and
   the *other* tab should be completely undisturbed (scroll position, any
   open preview, etc. all intact).

9. **Toast stacking, 5s auto-fade, and undo for delete** —
   `PageScripts.java` + `ShellScript.java` + `Styles.java`. Two separate
   toast systems (file-ops undo/redo in `PageScripts.java`, "closed tab" in
   `ShellScript.java`) both moved from a single fixed-position `<div>` that
   the next toast would silently overwrite, to a `.action-toast-container`
   that toasts get appended into and independently `setTimeout`-removed
   from (5000ms now, was 7000/6000ms; timer pauses on hover for the
   file-ops one). Delete now participates in undo/redo for the first time -
   previously excluded on purpose ("Trash is already a safety net"), but
   that reasoning left `deleteItem`/`deleteSelection` toast-less, which
   wasn't the intent. `TrashManager.moveToTrash`'s return value (already had
   an `Entry.id`) is now threaded back through `FileOpsHandler`'s new
   `OpResult(message, newPath, trashId)` 3-arg constructor and a new
   `respondJson(..., trashId)` overload, so the client gets the trash id
   back and can undo a delete via `POST /trashops action=restore` (new
   `runOp()` branch for `op.action==='trash-restore'`) instead of the usual
   `/fileops`. Redoing a delete assigns a *new* trash id, so `clickRedo()`
   rewrites `entry.undoOp` with the fresh id(s) before pushing the entry
   back onto the undo stack - otherwise a second Undo after a Redo would
   try to restore an id that's already been restored once. Verify: delete a
   single file and a multi-select of files, confirm both show an Undo toast
   that actually restores them; delete two different files in quick
   succession and confirm both toasts are visible stacked rather than the
   second replacing the first; let a toast sit untouched and confirm it
   fades around 5s; Undo then Redo then Undo again on a deleted file and
   confirm it doesn't error out the second time.

## Known gaps / things to watch for
- `CODE_LANG_MAP` in `PageScripts.java` (JS) and `CodeLanguageUtil.java`
  (server) are two separate hand-kept-in-sync lists — if you add a language
  to one, add it to the other too.
- Address bar / Move-to modal both hit `/subfolders` a lot; fine for normal
  folders but untested against a folder with thousands of entries.
- `/trash-browse` and `/trash-file` are read-only by design (no
  rename/move/upload while inside the trash) — that's intentional, not an
  oversight, but worth confirming it's still what's wanted once you've used
  it for real.
- The `/events` per-folder SSE connection-limit issue (see old item 5 below)
  is still latent for Browse tabs themselves if someone routinely keeps
  many open.
- Tab groups (item 7) don't support dragging a whole collapsed group as a
  block to reorder it relative to other tabs/groups - only individual tabs
  drag. Reordering a collapsed group means expanding it first.
- A closed tab's "Reopen" toast (item 9) remembers which group it was in by
  id; if that group was fully closed/ungrouped in the meantime the tab just
  reopens ungrouped rather than recreating the group - seemed like the
  saner default but worth a second opinion.
- Sessions (item 10) don't migrate the old pre-session `localStorage`
  key (`fileDashboardTabs`) - if that key still exists from before this
  change, it's just dead now and gets ignored; whatever tabs were saved
  there are effectively lost on first load after upgrading. Didn't seem
  worth writing one-time migration code for a dev tool with no real users
  yet, but flagging it in case that assumption is wrong.

## Testing method used previous sessions (not available this one)
Built + ran the real server in the sandbox, hit endpoints with curl, and for
JS logic used jsdom (Node) loading the actual rendered HTML/JS and firing
real DOM events. That caught several real bugs in earlier sessions and
would be worth doing again on this batch before considering it done - this
session could only do static review plus `node --check` syntax validation
(see top of file).

## Previously added (kept for history)

1. **Favicon** — `Styles.FAVICON` (inline SVG data URI, blue folder glyph),
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
   `/trash-browse?id=&sub=`) and `TrashFileHandler.java` (new,
   `/trash-file?id=&sub=&mode=`) let you look inside (and download from) a
   trashed folder in place, reading straight out of `Config.TRASH_DIR`
   without touching `PathUtil`'s ROOT_DIR-anchored world. Registered in
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
   the Dashboard tab's "Recently downloaded" updates within ~3s without a
   manual reload. Also worth eyeballing whether `/events` (per-folder,
   unchanged this session) has the same latent problem for heavy multi-tab
   use — not fixed here since it wasn't the one reported broken, but same
   underlying cause.

6. **Address bar hierarchical dropdown** — turned out to already be fully
   built (`ShellScript.java` + `SubfoldersHandler.java`), and TODO.md said it
   was tested last session. Nothing changed here.
