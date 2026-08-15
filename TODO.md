# TODO — continue here

## Just added, NOT fully tested (do this first)
Syntax highlighting via highlight.js CDN (cdnjs.cloudflare.com) — could not
test in the build sandbox (that domain isn't reachable from there). Verify
on your machine:
- Open a `.java`/`.py`/`.cpp`/etc. file → preview modal → should show colored
  syntax, with a "Raw text" toggle button next to Download.
- Right-click → Open Viewer on a code file → same, but the toggle button is
  "Show raw text" in the top nav bar.
- `.txt`/`.log`/`.csv`/`.md` should NOT get code styling (md gets its own
  markdown rendering; the others stay plain).
- If nothing shows, check DevTools console for a blocked CDN request — that's
  the main risk since it wasn't verified end-to-end.
- Code: `CodeLanguageUtil.java` (the ext→language map), the code-highlighted/
  code-raw branches in `ViewerHandler.buildPage()` and `PageScripts.openPreview()`.

## Not started yet
- Nothing else outstanding from the last two messages — everything else
  requested (autostart registry fix, Move-to hierarchical picker, dashboard
  live-refresh, search suggestion divider, trash card redesign, address bar
  live suggestions, PDF icon) was built AND tested this session.

## Known gaps / things to watch for
- `CODE_LANG_MAP` in `PageScripts.java` (JS) and `CodeLanguageUtil.java`
  (server) are two separate hand-kept-in-sync lists — if you add a language
  to one, add it to the other too.
- highlight.js auto-detects language for extensions not in the map (custom
  formats like `.vcanvas`) — untested whether auto-detect guesses well or
  just shows plain monospace for those.
- Address bar / Move-to modal both hit `/subfolders` a lot; fine for normal
  folders but untested against a folder with thousands of entries.

## Testing method used all session
Built + ran the real server in the sandbox, hit endpoints with curl, and for
JS logic used jsdom (Node) loading the actual rendered HTML/JS and firing
real DOM events — not just reasoning about the code. Worth continuing that
pattern rather than trusting code review alone; it caught several real bugs
this session (context menu inline-style bug, stale tab URLs, etc).
