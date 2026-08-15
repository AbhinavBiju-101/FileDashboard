import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Wraps the same Windows registry autostart mechanism used by
 * install-autostart.bat / uninstall-autostart.bat (HKCU Run key), so the
 * toggle on the Settings page enables/disables the exact same thing those
 * scripts do - not a separate, possibly-inconsistent mechanism. Only does
 * anything on Windows; reports a clear "not supported" message elsewhere.
 *
 * This intentionally uses a Run-key registry entry rather than a Scheduled
 * Task: schtasks has a command-line length limit that nested install paths
 * can exceed, and some antivirus software (e.g. McAfee) flags Scheduled
 * Task creation as suspicious more readily than a normal Run key entry.
 */
public class AutostartManager {

    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "FileDashboard";

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // Live-queries the actual registry value rather than trusting a stored
    // flag, so this stays correct even if it was changed outside the app
    // (via the .bat scripts, or the registry directly).
    public static boolean isEnabled() {
        if (!isWindows()) return false;
        try {
            Process p = new ProcessBuilder("reg", "query", REG_KEY, "/v", VALUE_NAME)
                .redirectErrorStream(true).start();
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
            if (javaw == null) return "Could not find javaw.exe - make sure Java is installed correctly.";

            String jarPath = findJarPath();
            if (jarPath == null) {
                return "Only works when running as a jar (java -jar FileDashboard.jar). " +
                       "Use install-autostart.bat instead when running from source/BlueJ.";
            }

            String command = "\"" + javaw + "\" -jar \"" + jarPath + "\"";
            Process p = new ProcessBuilder("reg", "add", REG_KEY, "/v", VALUE_NAME,
                "/t", "REG_SZ", "/d", command, "/f").redirectErrorStream(true).start();
            String output = readAll(p);
            int exit = p.waitFor();
            return exit == 0 ? null : "reg add failed: " + output.trim();
        } catch (Exception e) {
            return "Could not enable autostart: " + e.getMessage();
        }
    }

    // Returns null on success, or a human-readable error message.
    public static String disable() {
        if (!isWindows()) return "Autostart management is only available on Windows.";
        try {
            Process p = new ProcessBuilder("reg", "delete", REG_KEY, "/v", VALUE_NAME, "/f")
                .redirectErrorStream(true).start();
            String output = readAll(p);
            int exit = p.waitFor();
            if (exit != 0 && !output.toLowerCase().contains("unable to find")) {
                return "reg delete failed: " + output.trim();
            }
            return null; // success, including "it already wasn't installed"
        } catch (Exception e) {
            return "Could not disable autostart: " + e.getMessage();
        }
    }

    // Tries java.home first - the actual install directory of the JVM that's
    // CURRENTLY RUNNING this code, which is correct regardless of PATH. Falls
    // back to a PATH search only if that somehow doesn't pan out.
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
    // code source. Returns null if running from loose .class files
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
