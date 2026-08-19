import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/settings" - GET renders the page, POST applies changes and
 * redirects back to GET with a status message. A plain form-POST page
 * rather than an AJAX one, since settings changes are infrequent and a full
 * reload on save is perfectly fine here.
 */
public class SettingsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePost(exchange);
            return;
        }
        handleGet(exchange);
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String msg = QueryUtil.getParam(query, "msg");
        String err = QueryUtil.getParam(query, "err");
        boolean gauthResult = "1".equals(QueryUtil.getParam(query, "gauth"));
        msg = msg == null ? null : URLDecoder.decode(msg, "UTF-8");
        err = err == null ? null : URLDecoder.decode(err, "UTF-8");

        String html = buildPage(msg, err, gauthResult);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String result = null; // error message, null = success

        if ("save-root-dir".equals(action)) {
            String path = formParam(body, "rootDir");
            result = Settings.setRootDirOverride(path);
        } else if ("save-dashboard".equals(action)) {
            String raw = formParam(body, "maxItems");
            try {
                Settings.setDashboardMaxItems(Integer.parseInt(raw.trim()));
            } catch (Exception e) {
                result = "Enter a whole number.";
            }
        } else if ("toggle-live-refresh".equals(action)) {
            Settings.setLiveRefreshEnabled(!Settings.isLiveRefreshEnabled());
        } else if ("toggle-gdrive-experimental".equals(action)) {
            Settings.setGdriveExperimentalEnabled(!Settings.isGdriveExperimentalEnabled());
        } else if ("toggle-network-access".equals(action)) {
            Settings.setNetworkAccessEnabled(!Settings.isNetworkAccessEnabled());
            exchange.getResponseHeaders().set("Location", "/settings?msg=" +
                PathUtil.urlEncode("Saved - restart File Dashboard for this to take effect."));
            exchange.sendResponseHeaders(303, -1);
            exchange.close();
            return;
        } else if ("regenerate-access-token".equals(action)) {
            Settings.regenerateAccessToken();
            exchange.getResponseHeaders().set("Location", "/settings?msg=" +
                PathUtil.urlEncode("New token generated - restart File Dashboard for it to take effect."));
            exchange.sendResponseHeaders(303, -1);
            exchange.close();
            return;
        } else if ("enable-autostart".equals(action)) {
            result = AutostartManager.enable();
        } else if ("disable-autostart".equals(action)) {
            result = AutostartManager.disable();
        } else if ("save-gdrive-credentials".equals(action)) {
            String clientId = formParam(body, "gdriveClientId");
            String clientSecret = formParam(body, "gdriveClientSecret");
            if (clientId == null || clientId.trim().isEmpty()) {
                result = "Enter a Client ID first.";
            } else {
                GDriveAuth.setClientCredentials(clientId, clientSecret);
            }
        } else if ("disconnect-gdrive".equals(action)) {
            String accountId = formParam(body, "accountId");
            if (accountId != null && !accountId.isEmpty()) GDriveAuth.disconnect(accountId);
        }

        String redirect = result == null
            ? "/settings?msg=" + PathUtil.urlEncode("Saved.")
            : "/settings?err=" + PathUtil.urlEncode(result);
        exchange.getResponseHeaders().set("Location", redirect);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private String buildPage(String msg, String err, boolean gauthResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Settings</title>");
        sb.append(Styles.CSS);
        sb.append(settingsStyles());
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>Settings</div>");
        sb.append("</div>");

        sb.append("<div class='settings-wrap'>");

        if (msg != null) sb.append("<div class='settings-flash ok'>").append(PathUtil.htmlEscape(msg)).append("</div>");
        if (err != null) sb.append("<div class='settings-flash err'>").append(PathUtil.htmlEscape(err)).append("</div>");

        // Network Access - off by default. See FileServer.java: with this
        // off, the server only ever binds to 127.0.0.1 (loopback), which
        // means there's no network path to it from any other device at
        // all, not "a password in front of an open door" but no door -
        // the fix that actually matters on a shared network like a school
        // or office. Turning it on is for the opposite, deliberate case:
        // reaching your own dashboard from your own phone/tablet on your
        // own home Wi-Fi.
        boolean networked = Settings.isNetworkAccessEnabled();
        sb.append("<div class='settings-section").append(networked ? " settings-section-warn" : "").append("'>");
        sb.append("<h2>Network Access</h2>");
        sb.append("<p class='settings-desc'>Off by default: this server only listens on this computer (127.0.0.1) - no other ")
          .append("device can reach it, on any network, regardless of anyone knowing this machine's IP address. Turning this on makes it ")
          .append("reachable from <strong>any device on the same network</strong> - fine on a home Wi-Fi you trust, risky on something ")
          .append("shared like a school or office network, since anyone else on it could browse, edit, or delete your files too.</p>");
        sb.append("<p class='settings-current'>Currently: <strong>").append(networked ? "On - reachable from this network" : "Off - this computer only").append("</strong></p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='toggle-network-access'>");
        sb.append("<button type='submit'").append(networked ? " class='settings-btn-danger'" : "").append(">Turn ").append(networked ? "off" : "on").append("</button>");
        sb.append("</form>");
        if (networked) {
            String token = Settings.getAccessToken();
            String lanIp = FileServer.detectLanAddress();
            sb.append("<p class='settings-hint'>An access token is required from any device other than this one - without it, requests are rejected outright, ")
              .append("not just hidden behind a login page. Share this URL only with devices you actually want to have access:</p>");
            sb.append("<p class='settings-current'><code>http://").append(lanIp != null ? PathUtil.htmlEscape(lanIp) : "this-computer's-LAN-IP")
              .append(":").append(Config.PORT).append("/?token=").append(token != null ? PathUtil.htmlEscape(token) : "").append("</code></p>");
            sb.append("<form method='POST' action='/settings' class='settings-form'>");
            sb.append("<input type='hidden' name='action' value='regenerate-access-token'>");
            sb.append("<button type='submit' class='settings-btn-subtle'>Regenerate token</button>");
            sb.append("</form>");
            sb.append("<p class='settings-hint'>Regenerating immediately invalidates the old one - every device using it will need the new URL, including this browser's own remembered access.</p>");
        }
        sb.append("<p class='settings-hint settings-hint-restart'>Changing this needs a restart of File Dashboard to take effect - the server picks its bind address once, at startup.</p>");
        sb.append("</div>");

        // Home folder
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Home folder</h2>");
        sb.append("<p class='settings-desc'>Which folder the sidebar's \"Home\" opens, and the outer boundary for browsing, search, and file operations.</p>");
        sb.append("<p class='settings-current'>Currently: <code>").append(PathUtil.htmlEscape(Settings.rootDir().getAbsolutePath())).append("</code>");
        if (Settings.getRootDirOverride() == null) sb.append(" <span class='settings-tag'>default</span>");
        sb.append("</p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='save-root-dir'>");
        sb.append("<input type='text' name='rootDir' placeholder='e.g. C:\\Users\\Abhinav or /home/abhinav' value='")
          .append(Settings.getRootDirOverride() != null ? PathUtil.htmlEscape(Settings.getRootDirOverride()) : "").append("'>");
        sb.append("<button type='submit'>Save</button>");
        sb.append("</form>");
        sb.append("<p class='settings-hint'>Leave blank and save to reset to the default. This is real filesystem access to whatever folder you point it at - rename/duplicate/delete/move all work there, so choose something you trust the whole tree of.</p>");
        sb.append("</div>");

        // Autostart
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Start automatically at login</h2>");
        if (AutostartManager.isSupported()) {
            boolean enabled = AutostartManager.isEnabled();
            String mechanism = AutostartManager.isLinux()
                ? "a systemd --user unit (~/.config/systemd/user/filedashboard.service)"
                : "a per-user Windows Registry Run key entry";
            sb.append("<p class='settings-desc'>Uses ").append(mechanism).append(" - the same one the ")
              .append(AutostartManager.isLinux() ? "install-autostart.sh" : "install-autostart.bat")
              .append(" script sets up.</p>");
            sb.append("<p class='settings-desc'>Currently: <strong>").append(enabled ? "Enabled" : "Disabled").append("</strong></p>");
            sb.append("<form method='POST' action='/settings' class='settings-form'>");
            sb.append("<input type='hidden' name='action' value='").append(enabled ? "disable-autostart" : "enable-autostart").append("'>");
            sb.append("<button type='submit'>").append(enabled ? "Disable autostart" : "Enable autostart").append("</button>");
            sb.append("</form>");
            if (AutostartManager.isLinux()) {
                sb.append("<p class='settings-hint'>This starts File Dashboard when you log in to your desktop session, same as the Windows version. ")
                  .append("To have it start at boot even before anyone logs in, additionally run <code>loginctl enable-linger $USER</code> once from a terminal.</p>");
            }
        } else {
            sb.append("<p class='settings-desc'>Autostart management here is only available on Windows and Linux. ")
              .append("On macOS, use a Login Item pointed at <code>java -jar FileDashboard.jar</code> instead.</p>");
        }
        sb.append("</div>");

        // Dashboard
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Dashboard</h2>");
        sb.append("<p class='settings-desc'>Maximum items shown in each Dashboard section (Frequently viewed, Recently downloaded, Frequent folders).</p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='save-dashboard'>");
        sb.append("<input type='number' name='maxItems' min='1' max='100' value='").append(Settings.getDashboardMaxItems()).append("' style='width:80px;'>");
        sb.append("<button type='submit'>Save</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Live refresh
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Live folder refresh</h2>");
        sb.append("<p class='settings-desc'>Automatically reload a Browse tab when files change in that folder (uses a background file-watcher per open tab). Currently: <strong>")
          .append(Settings.isLiveRefreshEnabled() ? "On" : "Off").append("</strong></p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='toggle-live-refresh'>");
        sb.append("<button type='submit'>Turn ").append(Settings.isLiveRefreshEnabled() ? "off" : "on").append("</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Google Drive - one combined consent flow for both identity and
        // Drive access (see GDriveAuth.java's class comment for why an
        // earlier version that split these into two flows/buttons got
        // merged back - it added a second consent screen and a pricier
        // OAuth client-type requirement without actually reducing the
        // Google Cloud Console setup work, which is the same either way).
        //
        // Off by default and gated behind its own toggle: File Dashboard's
        // actual point is browsing your own local files, zero-upload,
        // nothing leaving the machine (see the project write-up) - Drive
        // integration is a "well, it's the same shape of problem, might as
        // well" bonus on top of that, not the point, so it stays out of
        // the way entirely (sidebar, Session Manager, and every /gdrive*
        // route itself via GDriveGateFilter - see FileServer.java) unless
        // someone deliberately switches it on here.
        boolean gdriveEnabled = Settings.isGdriveExperimentalEnabled();
        sb.append("<div class='settings-section'>");
        sb.append("<h2>").append(DriveIcon.img(22)).append(" Google Drive <span class='settings-tag settings-tag-experimental'>Experimental</span></h2>");
        sb.append("<p class='settings-desc'>Browse a Google Drive account through the same card-grid UI as your local files. ")
          .append("Off by default - this app's main job is local files; Drive support is a bonus on top of that, not the point.</p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='toggle-gdrive-experimental'>");
        sb.append("<button type='submit'>Turn ").append(gdriveEnabled ? "off" : "on").append("</button>");
        sb.append("</form>");

        if (gdriveEnabled) {
        sb.append("<p class='settings-desc' style='margin-top:14px;'>Connect one or more Google accounts to browse their Drive files, ")
          .append("from their own Drive sessions in the <a href=\"/sessions\" onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/sessions'); return false; }\">Session Manager</a>. ")
          .append("Read-only for now - browsing and downloading, no uploads/renames/deletes yet.</p>");

        // "Request tester access" - see GDriveAccessRequestHandler.java's
        // class comment for the whole story: this app's OAuth client is
        // sitting in Google's "Testing" publishing status (unverified,
        // manually-approved testers only, 100-slot cap), so this is the
        // step before Connect actually works for anyone who isn't already
        // on that list. Temporary by nature - goes away if/when the app
        // either gets verified for production or moves to the Picker API +
        // a narrower scope that doesn't need verification at all.
        sb.append("<div class='settings-access-request'>");
        sb.append("<p class='settings-desc' style='margin:0 0 8px;'><strong>Not added as a tester yet?</strong> This app's Google ")
          .append("integration is still in Google's \"Testing\" stage while it's unverified - only up to 100 manually-approved ")
          .append("Google accounts can connect at all. Enter the Google account email you'll be connecting with, and it'll ")
          .append("open an email (via your own mail app) asking for tester access - nothing is sent automatically.</p>");
        sb.append("<div class='settings-form settings-access-request-form'>");
        sb.append("<input type='text' id='gdriveAccessEmail' placeholder='you@gmail.com'>");
        sb.append("<button type='button' id='gdriveAccessRequestBtn'>Request access</button>");
        sb.append("</div>");
        sb.append("<p class='settings-hint' id='gdriveAccessRequestHint'></p>");
        sb.append("</div>");

        java.util.List<GDriveAuth.AccountInfo> accounts = GDriveAuth.listAccounts();
        if (!accounts.isEmpty()) {
            sb.append("<div class='gaccount-list'>");
            for (GDriveAuth.AccountInfo acct : accounts) {
                sb.append("<div class='gaccount-identity'>");
                if (acct.picture != null) {
                    sb.append("<img class='gaccount-avatar' src='").append(PathUtil.htmlEscape(acct.picture)).append("' alt=''>");
                }
                sb.append("<div class='gaccount-identity-text'><strong>").append(PathUtil.htmlEscape(acct.displayName())).append("</strong>");
                if (acct.name != null && acct.email != null) sb.append("<div class='gaccount-email'>").append(PathUtil.htmlEscape(acct.email)).append("</div>");
                sb.append("</div>");
                sb.append("<form method='POST' action='/settings' class='settings-form gaccount-disconnect-form'>");
                sb.append("<input type='hidden' name='action' value='disconnect-gdrive'>");
                sb.append("<input type='hidden' name='accountId' value='").append(PathUtil.htmlEscape(acct.id)).append("'>");
                sb.append("<button type='submit' class='settings-btn-subtle'>Disconnect</button>");
                sb.append("</form>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (!GDriveAuth.isConfigured()) {
            sb.append("<p class='settings-hint'>This app has no Google credentials of its own - connecting means creating your own free ")
              .append("Google Cloud OAuth client and pointing it here:</p>");
            sb.append("<ol class='settings-steps'>");
            sb.append("<li>In <a href=\"https://console.cloud.google.com/apis/credentials\" target=\"_blank\" rel=\"noopener\">Google Cloud Console \u2192 Credentials</a>, create an OAuth client of type <strong>Desktop app</strong>.</li>");
            sb.append("<li>Enable the <strong>Google Drive API</strong> for that project.</li>");
            sb.append("<li>Add this under <strong>Authorized redirect URIs</strong>: <code>")
              .append(PathUtil.htmlEscape(GDriveAuth.redirectUri())).append("</code></li>");
            sb.append("<li>Paste the resulting Client ID <strong>and</strong> Client Secret below. Per Google's docs, PKCE should let a Desktop app client skip the secret entirely - in practice Google's token endpoint rejects the exchange without one anyway, so enter both.</li>");
            sb.append("</ol>");
        } else {
            sb.append("<p class='settings-current'>OAuth client configured. <a href='#' onclick=\"document.getElementById('gClientForm').classList.toggle('settings-hidden'); return false;\">Edit</a></p>");
        }
        sb.append("<form method='POST' action='/settings' class='settings-form settings-form-stacked")
          .append(GDriveAuth.isConfigured() ? " settings-hidden" : "").append("' id='gClientForm'>");
        sb.append("<input type='hidden' name='action' value='save-gdrive-credentials'>");
        sb.append("<input type='text' name='gdriveClientId' placeholder='Client ID' value='")
          .append(GDriveAuth.getClientId() != null ? PathUtil.htmlEscape(GDriveAuth.getClientId()) : "").append("'>");
        sb.append("<input type='text' name='gdriveClientSecret' placeholder='Client Secret' value='")
          .append(GDriveAuth.getClientSecret() != null ? PathUtil.htmlEscape(GDriveAuth.getClientSecret()) : "").append("'>");
        sb.append("<button type='submit'>Save</button>");
        sb.append("</form>");
        if (GDriveAuth.isConfigured()) {
            // A real popup window (window.open), not target="_blank"/a plain link:
            // this Settings page is itself rendered inside one of the app shell's
            // iframes (see ShellScript.java), and Google's sign-in refuses to render
            // inside any iframe at all (anti-clickjacking) - a bare <a target> click
            // would still work since it forces a top-level navigation, but a popup
            // is nicer here since it doesn't navigate this tab away at all. The
            // popup keeps its own address bar (location=yes) since Google also
            // rejects chrome-less/embedded-looking windows. handleCallback() tags
            // its redirect back to /settings with &gauth=1, which the script below
            // uses to know "this /settings load is a popup reporting its result" -
            // it reloads the opener (this page) so it picks up the new Connected
            // state, then closes itself after a moment so the person doesn't have
            // to close the popup by hand. (No ?context= here - defaults to
            // "settings" in GDriveAuth.beginAuth(), which is exactly this
            // reload-the-opener behavior; ShellScript.java's own account picker
            // passes ?context=picker instead precisely to avoid it - see
            // GoogleAuthHandler.java's class comment.)
            sb.append("<p class='settings-hint'><a class='settings-connect-link' href='/gauth/start' onclick=\"")
              .append("window.open('/gauth/start','gdriveConnect','width=520,height=680,menubar=no,toolbar=no,location=yes,status=no,resizable=yes,scrollbars=yes');")
              .append("return false;\">").append(DriveIcon.img(16)).append(accounts.isEmpty() ? " Connect Google Drive \u2192" : " Add another Google account \u2192").append("</a></p>");
        }
        } // end if (gdriveEnabled)
        sb.append("</div>");

        // Other settings ideas (not implemented, just documented)
        sb.append("<div class='settings-section settings-ideas'>");
        sb.append("<h2>Other settings ideas</h2>");
        sb.append("<p class='settings-desc'>Not built yet, but reasonable next additions:</p>");
        sb.append("<ul>");
        sb.append("<li>Dark mode toggle</li>");
        sb.append("<li>Default sort order/field for new Browse tabs</li>");
        sb.append("<li>Thumbnail size / quality</li>");
        sb.append("<li>Auto-empty the Recycle Bin after N days</li>");
        sb.append("<li>Default upload destination</li>");
        sb.append("<li>Port number (would need a restart to take effect)</li>");
        sb.append("</ul>");
        sb.append("</div>");

        sb.append("</div></div>");
        if (gdriveEnabled) {
            // Plain fetch() + a status line rather than a form POST/redirect,
            // since there's nothing to persist server-side worth a full page
            // reload over - see GDriveAccessRequestHandler.java for what
            // actually happens with the email (a local log line, plus a
            // mailto: link handed back for the visitor's own mail client to
            // send - no SMTP anywhere in this app).
            sb.append("<script>(function(){")
              .append("var btn=document.getElementById('gdriveAccessRequestBtn');")
              .append("if(!btn) return;")
              .append("btn.addEventListener('click', function(){")
                .append("var input=document.getElementById('gdriveAccessEmail');")
                .append("var hint=document.getElementById('gdriveAccessRequestHint');")
                .append("var email=(input.value||'').trim();")
                .append("if(!email){ hint.textContent='Enter an email first.'; hint.className='settings-hint settings-hint-err'; return; }")
                .append("btn.disabled=true; hint.textContent='...'; hint.className='settings-hint';")
                .append("fetch('/gdrive-access-request',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},")
                  .append("body:'email='+encodeURIComponent(email)})")
                  .append(".then(function(r){return r.json().then(function(data){ return {status:r.status, data:data}; });})")
                  .append(".then(function(res){")
                    .append("btn.disabled=false;")
                    .append("if(res.data && res.data.ok){")
                      .append("hint.textContent='Opening your mail app...'; hint.className='settings-hint';")
                      .append("window.location.href=res.data.mailto;")
                    .append("}else{")
                      .append("hint.textContent=(res.data&&res.data.error)||'Something went wrong.'; hint.className='settings-hint settings-hint-err';")
                    .append("}")
                  .append("})")
                  .append(".catch(function(){ btn.disabled=false; hint.textContent='Could not reach the server.'; hint.className='settings-hint settings-hint-err'; });")
              .append("});")
              .append("})();</script>");
        }
        if (gauthResult) {
            // This load of /settings is the gdriveConnect popup reporting its result
            // (see the "gauth=1" tag GoogleAuthHandler.java adds to its redirects).
            // If window.opener is actually there and still open - i.e. we really are
            // that popup, not e.g. someone bookmarking this exact URL - reload it so
            // the real Settings page behind us picks up the new Connected/error state,
            // then close ourselves after a moment so the flash message above is still
            // readable for a beat first rather than vanishing instantly.
            sb.append("<script>(function(){")
              .append("if(window.opener && !window.opener.closed){")
              .append("try{window.opener.location.reload();}catch(e){}")
              .append("setTimeout(function(){ window.close(); }, 1200);")
              .append("}")
              .append("})();</script>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String settingsStyles() {
        return "<style>" +
            ".settings-wrap{max-width:640px;margin:0 auto;padding:32px 24px;}" +
            ".settings-flash{padding:10px 14px;border-radius:6px;margin-bottom:20px;font-size:14px;}" +
            ".settings-flash.ok{background:#eafbea;color:#186a18;border:1px solid #b7e4b7;}" +
            ".settings-flash.err{background:#fdeaea;color:#9c1f1f;border:1px solid #f0b8b8;}" +
            ".settings-section{border-bottom:1px solid #eee;padding:22px 0;}" +
            ".settings-section:last-child{border-bottom:none;}" +
            ".settings-section-warn{background:#fffaf0;margin:0 -24px;padding:22px 24px;border-bottom-color:#f5e3c3;}" +
            ".settings-section h2{font-size:16px;margin:0 0 6px;}" +
            ".settings-desc{color:#555;font-size:13px;margin:0 0 10px;line-height:1.5;}" +
            ".settings-current{font-size:13px;margin:0 0 10px;}" +
            ".settings-current code{background:#f4f5f7;padding:2px 6px;border-radius:4px;font-size:12px;}" +
            ".settings-tag{background:#eef0f2;color:#666;font-size:11px;padding:1px 8px;border-radius:10px;}" +
            ".settings-tag-experimental{background:#fff4e0;color:#a85d00;margin-left:6px;vertical-align:middle;}" +
            ".settings-access-request{background:#f8f9fb;border:1px solid #e2e4e8;border-radius:8px;padding:14px;margin:14px 0;}" +
            ".settings-access-request-form{gap:8px;}" +
            ".settings-access-request-form input[type=text]{flex:1;}" +
            ".settings-hint-err{color:#9c1f1f;}" +
            ".settings-form{display:flex;gap:8px;}" +
            ".settings-form input[type=text]{flex:1;padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
            ".settings-form input[type=number]{padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
            ".settings-form button{padding:8px 16px;border:none;background:#2563eb;color:#fff;border-radius:6px;cursor:pointer;font-size:13px;}" +
            ".settings-form button:hover{background:#1d4ed8;}" +
            ".settings-hint{color:#888;font-size:12px;margin:10px 0 0;line-height:1.5;}" +
            ".settings-steps{color:#555;font-size:13px;line-height:1.8;margin:0 0 14px;padding-left:20px;}" +
            ".settings-steps code{background:#f4f5f7;padding:1px 5px;border-radius:4px;font-size:12px;word-break:break-all;}" +
            ".settings-form-stacked{flex-direction:column;align-items:stretch;max-width:420px;}" +
            ".settings-form-stacked input{width:100%;margin-bottom:2px;}" +
            ".settings-form-stacked button{align-self:flex-start;}" +
            ".settings-connect-link{display:inline-block;background:#2563eb;color:#fff;padding:8px 16px;border-radius:6px;" +
              "text-decoration:none;font-size:13px;margin-top:4px;}" +
            ".settings-connect-link:hover{background:#1d4ed8;}" +
            ".settings-hidden{display:none;}" +
            ".gaccount-list{display:flex;flex-direction:column;gap:2px;margin-bottom:14px;}" +
            ".gaccount-identity{display:flex;align-items:center;gap:10px;margin-bottom:10px;}" +
            ".gaccount-identity-text{flex:1;}" +
            ".gaccount-avatar{width:36px;height:36px;border-radius:50%;flex-shrink:0;}" +
            ".gaccount-email{font-size:12px;color:#888;margin-top:1px;}" +
            ".gaccount-disconnect-form{margin:0;}" +
            ".settings-btn-subtle{background:none;border:1px solid #ddd;color:#666;border-radius:6px;padding:6px 12px;font-size:13px;cursor:pointer;}" +
            ".settings-btn-subtle:hover{background:#fdeaea;color:#9c1f1f;border-color:#f0b8b8;}" +
            ".settings-btn-danger{background:#c0362c;}" +
            ".settings-btn-danger:hover{background:#a12e25;}" +
            ".settings-hint-restart{color:#a85d00;font-weight:600;}" +
            ".settings-ideas ul{margin:0;padding-left:20px;color:#555;font-size:13px;line-height:1.9;}" +
            "</style>";
    }

    private String formParam(String body, String key) {
        if (body == null) return null;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            if (pair.substring(0, idx).equals(key)) {
                try {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } catch (Exception e) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }
}
