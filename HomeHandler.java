import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves "/dashboard" - the landing dashboard. This is deliberately separate
 * from the folder browser at "/browse": it's a quick-launch surface
 * (frequently viewed files, recently downloaded files, frequently visited
 * folders), not a directory listing. It's loaded as a tab inside the app
 * shell ("/") rather than being the shell itself.
 *
 * All three sections are horizontally-scrollable rows rather than wrapping
 * grids, and capped at Settings.getDashboardMaxItems() (20 by default) -
 * keeps the page a fixed, predictable height no matter how much activity
 * has piled up.
 */
public class HomeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = buildPage();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage() {
        int max = Settings.getDashboardMaxItems();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Dashboard</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'><h1>Dashboard</h1>");
        sb.append("<div class='breadcrumb'>Quick launch for your activity</div></div>");

        sb.append(fileSection("Frequently viewed", RecentActivity.getFrequentlyViewed(max),
            "Files you open often will show up here."));
        sb.append(fileSection("Recently downloaded", RecentActivity.getRecentDownloaded(),
            "Nothing yet - files you download will show up here."));
        sb.append(frequentFoldersSection(max));
        sb.append(onThisDaySection());
        sb.append(weekSection(max));

        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append(dashboardRefreshScript());
        sb.append(weekChartScript());
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // "On this day" - anything touched on today's month/day in a previous
    // year. Rendered above the past-week list (rarer, more of a surprise)
    // and omitted entirely when there's nothing to show, rather than a
    // permanent "nothing yet" placeholder - unlike the always-relevant
    // sections above, most days simply won't have a match, and a
    // frequently-empty section is just noise.
    private String onThisDaySection() {
        List<ActivityLog.Event> events = ActivityLog.getOnThisDay();
        if (events.isEmpty()) return "";

        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a");
        StringBuilder rows = new StringBuilder();
        int shown = 0;
        for (ActivityLog.Event e : events) {
            String row = timelineRow(e, timeFmt, true);
            if (row != null) {
                rows.append(row);
                shown++;
            }
        }
        if (shown == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>On this day</h2>");
        sb.append("<div class='timeline-section'><div class='timeline-list'>").append(rows).append("</div></div>");
        return sb.toString();
    }

    // One bucket of the 7-day chart: total counts per event type for a
    // single calendar day, keyed the same way as the "yyyy-MM-dd" dayKey
    // stamped onto each file card below - that's what lets a click on a
    // bar filter the card row to just that day, client-side, with no
    // extra request.
    private static class DayStat {
        String dayKey;
        String label;
        int viewed, downloaded, uploaded;
        int total() { return viewed + downloaded + uploaded; }
    }

    // Replaces the old "Past week" timeline-row list with: a small
    // interactive stacked-bar chart (one bar per of the last 7 days,
    // segmented by viewed/downloaded/uploaded) above a horizontally
    // scrolling row of real file cards - clicking a bar filters the cards
    // to that day (see weekChartScript()). Cards reuse GridRenderer's
    // shared card markup, so they get the same click-to-select,
    // double-click-to-open, and right-click menu as every other card in
    // the app for free, exactly like the timeline rows used to.
    private String weekSection(int max) {
        List<ActivityLog.Event> events = ActivityLog.getPastWeek(); // most-recent-first
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>This week</h2>");

        if (events.isEmpty()) {
            sb.append("<p class='empty'>Nothing viewed, downloaded, or uploaded in the last 7 days.</p>");
            return sb.toString();
        }

        List<DayStat> days = buildDayStats(events);
        int totalViewed = 0, totalDownloaded = 0, totalUploaded = 0;
        for (DayStat d : days) { totalViewed += d.viewed; totalDownloaded += d.downloaded; totalUploaded += d.uploaded; }

        sb.append("<div class='week-wrap'>");
        sb.append(weekSummary(totalViewed, totalDownloaded, totalUploaded));
        sb.append(weekChart(days));
        sb.append(weekLegend());
        sb.append("<div class='week-cards'>");
        sb.append(weekCardsRow(events, max));
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String weekSummary(int viewed, int downloaded, int uploaded) {
        int total = viewed + downloaded + uploaded;
        return "<div class='week-summary'><strong>" + total + "</strong> file "
            + (total == 1 ? "action" : "actions") + " this week &middot; "
            + viewed + " viewed, " + downloaded + " downloaded, " + uploaded + " uploaded</div>";
    }

    private String weekLegend() {
        return "<div class='week-legend'>"
            + "<span class='week-legend-item'><span class='week-legend-dot week-seg-viewed'></span>Viewed</span>"
            + "<span class='week-legend-item'><span class='week-legend-dot week-seg-downloaded'></span>Downloaded</span>"
            + "<span class='week-legend-item'><span class='week-legend-dot week-seg-uploaded'></span>Uploaded</span>"
            + "<a href='#' class='week-clear-filter' id='weekClearFilter' onclick='weekClearFilter(); return false;'>Show all days</a>"
            + "</div>";
    }

    // Buckets pastWeek events into the last 7 calendar days (oldest first,
    // ending with today), so the chart reads left-to-right chronologically.
    private List<DayStat> buildDayStats(List<ActivityLog.Event> events) {
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE");

        Map<String, DayStat> byKey = new LinkedHashMap<>();
        List<DayStat> ordered = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            DayStat d = new DayStat();
            d.dayKey = keyFmt.format(cal.getTime());
            d.label = (i == 0) ? "Today" : labelFmt.format(cal.getTime());
            byKey.put(d.dayKey, d);
            ordered.add(d);
        }

        for (ActivityLog.Event e : events) {
            String key = keyFmt.format(new Date(e.time));
            DayStat d = byKey.get(key);
            if (d == null) continue; // outside the 7-day bucket window (edge of the cutoff) - skip
            if ("downloaded".equals(e.type)) d.downloaded++;
            else if ("uploaded".equals(e.type)) d.uploaded++;
            else d.viewed++;
        }
        return ordered;
    }

    private String weekChart(List<DayStat> days) {
        int maxTotal = 1;
        for (DayStat d : days) maxTotal = Math.max(maxTotal, d.total());

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='week-chart'>");
        for (DayStat d : days) {
            boolean empty = d.total() == 0;
            sb.append("<div class='week-bar-col").append(empty ? " empty" : "")
              .append("' data-day='").append(d.dayKey).append("'")
              .append(empty ? "" : " onclick='weekFilterDay(this)'")
              .append(" title='").append(d.label).append(": ")
              .append(d.viewed).append(" viewed, ").append(d.downloaded).append(" downloaded, ")
              .append(d.uploaded).append(" uploaded'>");
            sb.append("<div class='week-bar-count'>").append(empty ? "" : d.total()).append("</div>");
            sb.append("<div class='week-bar' style='height:").append(barHeightPx(d.total(), maxTotal)).append("px'>");
            if (d.uploaded > 0) sb.append("<div class='week-seg-uploaded' style='height:").append(barHeightPx(d.uploaded, maxTotal)).append("px'></div>");
            if (d.downloaded > 0) sb.append("<div class='week-seg-downloaded' style='height:").append(barHeightPx(d.downloaded, maxTotal)).append("px'></div>");
            if (d.viewed > 0) sb.append("<div class='week-seg-viewed' style='height:").append(barHeightPx(d.viewed, maxTotal)).append("px'></div>");
            sb.append("</div>");
            sb.append("<div class='week-day-label'>").append(d.label).append("</div>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static final int WEEK_CHART_MAX_PX = 64;

    private int barHeightPx(int count, int maxTotal) {
        if (count == 0) return 0;
        return Math.max(3, (int) Math.round((count / (double) maxTotal) * WEEK_CHART_MAX_PX));
    }

    // Dedupes to one card per file (keeping the most recent event, since
    // events arrive most-recent-first), capped at max - same "capped,
    // predictable height" rule the other Dashboard rows follow. Each card
    // carries data-day so weekFilterDay() can show/hide by day client-side.
    private String weekCardsRow(List<ActivityLog.Event> events, int max) {
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeFmt = new SimpleDateFormat("EEE h:mm a");

        Map<String, ActivityLog.Event> byPath = new LinkedHashMap<>();
        for (ActivityLog.Event e : events) {
            if (!byPath.containsKey(e.path)) byPath.put(e.path, e); // first occurrence = most recent
        }

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (ActivityLog.Event e : byPath.values()) {
            if (shown >= max) break;
            File f;
            try {
                f = PathUtil.resolve(e.path);
            } catch (IOException ex) {
                continue;
            }
            if (!f.isFile()) continue; // moved/deleted/renamed since - same graceful skip as elsewhere

            String dayKey = keyFmt.format(new Date(e.time));
            String metaLine = actionLabel(e.type) + " &middot; " + timeFmt.format(new Date(e.time));
            String extraAttrs = "data-day=\"" + dayKey + "\"";
            cards.append(GridRenderer.fileCard(f, e.path, false, null, metaLine, extraAttrs));
            shown++;
        }

        if (shown == 0) {
            return "<p class='empty'>Nothing viewed, downloaded, or uploaded in the last 7 days.</p>";
        }
        return "<div class='dash-row'>" + cards + "</div>";
    }

    // Click-to-filter for the week chart: clicking a non-empty bar shows
    // only that day's cards and highlights the bar; clicking the same bar
    // again (or "Show all days") clears the filter. Pure client-side -
    // every card is already rendered, this just toggles display:none.
    private String weekChartScript() {
        return "<script>" +
               "function weekFilterDay(col){" +
                 "var day=col.dataset.day;" +
                 "var alreadyActive=col.classList.contains('active');" +
                 "document.querySelectorAll('.week-bar-col').forEach(function(c){ c.classList.remove('active'); });" +
                 "var clear=document.getElementById('weekClearFilter');" +
                 "if(alreadyActive){" +
                   "document.querySelectorAll('.week-cards .card').forEach(function(c){ c.style.display=''; });" +
                   "if(clear) clear.classList.remove('visible');" +
                   "return;" +
                 "}" +
                 "col.classList.add('active');" +
                 "document.querySelectorAll('.week-cards .card').forEach(function(c){" +
                   "c.style.display=(c.dataset.day===day)?'':'none';" +
                 "});" +
                 "if(clear) clear.classList.add('visible');" +
               "}" +
               "function weekClearFilter(){" +
                 "document.querySelectorAll('.week-bar-col').forEach(function(c){ c.classList.remove('active'); });" +
                 "document.querySelectorAll('.week-cards .card').forEach(function(c){ c.style.display=''; });" +
                 "var clear=document.getElementById('weekClearFilter');" +
                 "if(clear) clear.classList.remove('visible');" +
               "}" +
               "</script>";
    }

    // Renders one timeline entry as a full data-* "card" (data-path/data-type/
    // data-ext/etc., same shape GridRenderer produces) styled as a list row
    // instead of a grid tile via the .timeline-row CSS override - that's what
    // gives these entries the exact same click-to-preview, double-click-to-
    // open, and right-click menu behavior as every other card in the app,
    // for free, with no changes needed in PageScripts.js. showYear controls
    // whether the little year badge is printed (relevant for "On this day"
    // across multiple past years, not for "Past week").
    private String timelineRow(ActivityLog.Event event, SimpleDateFormat timeFmt, boolean showYear) {
        File f;
        try {
            f = PathUtil.resolve(event.path);
        } catch (IOException e) {
            return null;
        }
        // Gracefully skip entries whose file has since moved/been renamed/
        // deleted - same trade-off the other dashboard sections already make
        // for RecentActivity's lists.
        if (!f.isFile()) return null;

        String ext = GridRenderer.getExtension(f.getName()).toLowerCase();
        String name = PathUtil.htmlEscape(f.getName());
        String dataPath = PathUtil.htmlEscape(event.path);
        boolean viewable = ViewabilityUtil.isViewable(f, ext);
        boolean textlike = ViewabilityUtil.isTextLike(f, ext);
        String category = GridRenderer.categoryFor(ext);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file timeline-row\" data-path=\"").append(dataPath)
          .append("\" data-name=\"").append(name)
          .append("\" data-type=\"file\" data-ext=\"").append(ext)
          .append("\" data-viewable=\"").append(viewable ? "1" : "0")
          .append("\" data-textlike=\"").append(textlike ? "1" : "0")
          .append("\" data-category=\"").append(category).append("\">");
        sb.append("<div class=\"timeline-icon\">").append(GridRenderer.iconFor(ext)).append("</div>");
        sb.append("<div class=\"timeline-name\" title=\"").append(name).append("\">").append(name).append("</div>");
        sb.append("<div class=\"timeline-meta\">").append(actionLabel(event.type)).append(" &middot; ")
          .append(timeFmt.format(new Date(event.time))).append("</div>");
        if (showYear) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(event.time);
            sb.append("<span class=\"timeline-year\">").append(cal.get(Calendar.YEAR)).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String actionLabel(String type) {
        if ("downloaded".equals(type)) return "Downloaded";
        if ("uploaded".equals(type)) return "Uploaded";
        return "Viewed";
    }

    private String fileSection(String title, List<String> relPaths, String emptyMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>").append(title).append("</h2>");

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (String rel : relPaths) {
            try {
                File f = PathUtil.resolve(rel);
                if (f.isFile()) {
                    cards.append(GridRenderer.fileCard(f, rel, true, parentLabel(rel)));
                    shown++;
                }
            } catch (IOException ignored) {
                // file may have moved/been deleted since it was recorded - skip it
            }
        }

        if (shown == 0) {
            sb.append("<p class='empty'>").append(emptyMessage).append("</p>");
        } else {
            sb.append("<div class='dash-row'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    private String frequentFoldersSection(int max) {
        List<String> folders = RecentActivity.getFrequentFolders(max);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>Frequent folders</h2>");

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (String rel : folders) {
            try {
                File f = PathUtil.resolve(rel);
                if (f.isDirectory()) {
                    String label = rel.isEmpty() ? "Home" : f.getName();
                    cards.append(GridRenderer.folderCard(rel, label));
                    shown++;
                }
            } catch (IOException ignored) {
                // folder may have moved/been deleted since it was recorded - skip it
            }
        }

        if (shown == 0) {
            sb.append("<p class='empty'>Folders you browse often will show up here.</p>");
        } else {
            sb.append("<div class='dash-row'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    // Polls /dashboard-events every few seconds for RecentActivity's version
    // counter and reloads the page when it changes. See DashboardEventsHandler
    // for why this is a poll rather than a long-held SSE connection - the
    // short version: SSE meant one more persistent connection competing with
    // every open Browse tab's own "/events" stream for the browser's 6-per-origin
    // connection cap, which is how "Recently downloaded" ended up silently
    // stuck. A poll opens and closes immediately each time, so it can't get
    // queued behind those longer-lived connections.
    private String dashboardRefreshScript() {
        return "<script>" +
               "(function(){" +
               "var lastVersion=null;" +
               "function poll(){" +
                 "fetch('/dashboard-events').then(function(r){ return r.json(); }).then(function(data){" +
                   "if(data.version===-1) return;" + // live refresh turned off in Settings
                   "if(lastVersion===null){ lastVersion=data.version; return; }" +
                   "if(data.version!==lastVersion){ location.reload(); }" +
                 "}).catch(function(){ /* offline or server restarting - try again next tick */ });" +
               "}" +
               "poll();" +
               "setInterval(poll, 3000);" +
               "})();" +
               "</script>";
    }

    private String parentLabel(String rel) {
        return rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "/";
    }
}
