import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Wraps the same Windows Scheduled Task mechanism used by
 * install-autostart.bat / uninstall-autostart.bat, so the toggle on the
 * Settings page can enable/disable autostart directly instead of requiring
 * those scripts to be run manually. Only does anything on Windows; reports
 * a clear "not supported" message elsewhere rather than failing oddly.
 */
public class AutostartManager {

    private static final String TASK_NAME = "FileDashboard";

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // Live-queries the actual Scheduled Task state rather than trusting a
    // stored flag, so this stays correct even if the task was created/removed
    // outside the app (via the .bat scripts, or Task Scheduler itself).
    public static boolean isEnabled() {
        if (!isWindows()) return false;
        try {
            Process p = new ProcessBuilder("schtasks", "/query", "/tn", TASK_NAME).start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Returns null on success, or a human-readable error message.
    public static String enable() {
        if (!isWindows()) return "Autostart management is only available on Windows.";
        try {
            String javaw = findJavaw();
            if (javaw == null) return "Could not find javaw.exe on PATH - make sure Java's bin folder is on PATH.";

            String jarPath = findJarPath();
            if (jarPath == null) {
                return "Only works when running as a jar (java -jar FileDashboard.jar). " +
                       "Use install-autostart.bat instead when running from source/BlueJ.";
            }

            String tr = "\"" + javaw + "\" -jar \"" + jarPath + "\"";
            Process p = new ProcessBuilder("schtasks", "/create", "/tn", TASK_NAME,
                "/tr", tr, "/sc", "onlogon", "/rl", "limited", "/f").redirectErrorStream(true).start();
            String output = readAll(p);
            int exit = p.waitFor();
            return exit == 0 ? null : "schtasks failed: " + output.trim();
        } catch (Exception e) {
            return "Could not enable autostart: " + e.getMessage();
        }
    }

    // Returns null on success, or a human-readable error message.
    public static String disable() {
        if (!isWindows()) return "Autostart management is only available on Windows.";
        try {
            Process p = new ProcessBuilder("schtasks", "/delete", "/tn", TASK_NAME, "/f")
                .redirectErrorStream(true).start();
            String output = readAll(p);
            int exit = p.waitFor();
            if (exit != 0 && !output.toLowerCase().contains("cannot find")) {
                return "schtasks failed: " + output.trim();
            }
            return null; // success, including "it already wasn't installed"
        } catch (Exception e) {
            return "Could not disable autostart: " + e.getMessage();
        }
    }

    // Tries java.home first - the actual install directory of the JVM that's
    // CURRENTLY RUNNING this code, which is correct regardless of PATH. This
    // matters because a jar can be launched via file association (double-click)
    // without java's bin folder ever being on PATH at all, even though Java
    // obviously works (we're running inside it). Falls back to a PATH search
    // only if that somehow doesn't pan out.
    private static String findJavaw() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File candidate = new File(javaHome, "bin" + File.separator + "javaw.exe");
            if (candidate.exists()) return candidate.getAbsolutePath();
        }
        try {
            Process p = new ProcessBuilder("where", "javaw").start();
            String output = readAll(p).trim();
            p.waitFor();
            if (!output.isEmpty()) return output.split("\\r?\\n")[0].trim();
        } catch (Exception ignored) {
            // fall through to null
        }
        return null;
    }

    // Finds the path of the currently-running jar via the classloader's own
    // code source - the standard way to answer "where am I actually running
    // from" in Java. Returns null if running from loose .class files
    // (BlueJ/dev mode) rather than a packaged jar.
    private static String findJarPath() {
        try {
            File location = new File(AutostartManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.getName().endsWith(".jar") ? location.getAbsolutePath() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(Process p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
