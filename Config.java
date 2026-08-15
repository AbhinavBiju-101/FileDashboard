import java.io.File;

/**
 * Central place to configure the dashboard.
 * Change ROOT_DIR to whichever folder you want exposed in the browser.
 */
public class Config {

    // The folder this server will let you browse. Everything inside it (and its
    // subfolders) will be visible; nothing outside it is reachable.
    public static final File ROOT_DIR = new File(System.getProperty("user.home"), "");

    // Which port to run the local server on. Visit http://localhost:PORT
    public static final int PORT = 8080;

    // Optional: set this to a secret string (e.g. "mysecret123") to require
    // "?token=mysecret123" once per browser before the dashboard is usable.
    // Leave as null to disable auth entirely (fine for pure localhost use).
    public static final String ACCESS_TOKEN = null;
}
