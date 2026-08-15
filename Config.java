import java.io.File;
import java.nio.file.Path;

/**
 * Central place to configure the dashboard.
 */
public class Config {

    // The folder this server will let you browse. Everything inside it (and
    // its subfolders) will be visible; nothing outside it is reachable.
    //
    // This is the root of the drive/filesystem that contains your home
    // folder - "C:\" on Windows, "/" on Mac/Linux - not just your home
    // directory. That's deliberate: it means Desktop/Downloads/Documents
    // (under Users\You) AND anything else on that drive are all reachable,
    // not just your user profile.
    //
    // Heads up: this is real, broad filesystem access - the whole drive,
    // not a sandboxed folder. Rename/duplicate/delete/move all work
    // anywhere under it. Deletes go to the Recycle Bin (not permanent) as a
    // safety net, but be careful around system folders regardless. If you'd
    // rather scope this down to just your home folder like before, change
    // the line below to:
    //     public static final File ROOT_DIR = new File(System.getProperty("user.home"));
    public static final File ROOT_DIR = computeDiskRoot();

    // Which port to run the local server on. Visit http://localhost:PORT
    public static final int PORT = 8080;

    // Optional: set this to a secret string (e.g. "mysecret123") to require
    // "?token=mysecret123" once per browser before the dashboard is usable.
    // Leave as null to disable auth entirely (fine for pure localhost use).
    // Worth turning on now that ROOT_DIR covers the whole drive, if this
    // machine is ever reachable from other devices on your network.
    public static final String ACCESS_TOKEN = null;

    // Where app state (recent activity, etc.) is persisted between restarts.
    // Anchored to your actual home folder (not ROOT_DIR) so it's always
    // somewhere you have write permission, and hidden from listings/search.
    public static final File DATA_DIR = new File(System.getProperty("user.home"), ".filedashboard");

    // Where deleted files/folders go instead of being removed immediately.
    // Also anchored to your home folder rather than ROOT_DIR - on Windows,
    // regular accounts usually can't create new folders at the drive root
    // (C:\) without admin rights, so putting trash there would silently
    // fail. Your home folder is on the same drive as ROOT_DIR, so moves
    // into trash are still same-filesystem (fast), just not literally at
    // the drive root.
    public static final File TRASH_DIR = new File(System.getProperty("user.home"), ".trash");

    // How long a deleted item sits in the trash before being permanently
    // removed automatically (TrashManager checks for expired items on a
    // timer - see TrashManager.startAutoPurgeScheduler()). Trash cards show
    // a countdown based on this. Set to 0 or less to disable auto-purging
    // entirely and keep the traditional "only I empty the trash" behavior.
    public static final int TRASH_RETENTION_DAYS = 30;

    private static File computeDiskRoot() {
        File home = new File(System.getProperty("user.home"));
        Path root = home.toPath().getRoot();
        return root != null ? root.toFile() : home;
    }
}
