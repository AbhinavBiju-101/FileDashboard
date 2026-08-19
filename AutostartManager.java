import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Cross-platform autostart toggle used by the Settings page. Wraps two
 * completely separate OS mechanisms behind one API:
 *
 *   - Windows: the same HKCU Run-key registry entry used by
 *     install-autostart.bat / uninstall-autostart.bat.
 *   - Linux: a systemd --user unit (~/.config/systemd/user/filedashboard.service),
 *     the same one written by install-autostart.sh / uninstall-autostart.sh.
 *     A --user unit starts when your desktop/login session starts, which is
 *     the direct Linux equivalent of a per-user Windows Run key entry - not
 *     a system-wide service, and not running before anyone logs in unless
 *     you additionally opt in with `loginctl enable-linger $USER` (see
 *     install-autostart.sh's printed note).
 *
 * macOS isn't implemented (would be a LaunchAgent plist) - reports a clear
 * "not supported" message there, same as this used to report for every
 * non-Windows OS.
 *
 * Both platform branches live-query the actual OS state (registry / systemd)
 * rather than trusting a stored flag, so this stays correct even if it was
 * changed outside the app (via the shell/.bat scripts, systemctl directly,
 * or the registry directly).
 */
public class AutostartManager {

    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "FileDashboard";

    // Matches the unit name install-autostart.sh / uninstall-autostart.sh use.
    private static final String SYSTEMD_UNIT = "filedashboard.service";

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("nux") || os.contains("nix");
    }

    public static boolean isSupported() {
        return isWindows() || isLinux();
    }

    // Live-queries the actual OS autostart state rather than trusting a
    // stored flag, so this stays correct even if it was changed outside the
    // app (via the .bat/.sh scripts, systemctl, or the registry directly).
    public static boolean isEnabled() {
        if (isWindows()) return isEnabledWindows();
        if (isLinux()) return isEnabledLinux();
        return false;
    }

    // Returns null on success, or a human-readable error message.
    public static String enable() {
        if (isWindows()) return enableWindows();
        if (isLinux()) return enableLinux();
        return "Autostart management is only available on Windows and Linux here. " +
               "On macOS, add a Login Item pointed at `java -jar FileDashboard.jar` instead.";
    }

    // Returns null on success, or a human-readable error message.
    public static String disable() {
        if (isWindows()) return disableWindows();
        if (isLinux()) return disableLinux();
        return "Autostart management is only available on Windows and Linux here.";
    }

    // ---------------------------------------------------------------
    // Windows: HKCU Run-key registry entry.
    // ---------------------------------------------------------------

    private static boolean isEnabledWindows() {
        try {
            Process p = new ProcessBuilder("reg", "query", REG_KEY, "/v", VALUE_NAME)
                .redirectErrorStream(true).start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String enableWindows() {
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

    private static String disableWindows() {
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

    // ---------------------------------------------------------------
    // Linux: systemd --user unit.
    // ---------------------------------------------------------------

    private static boolean isEnabledLinux() {
        try {
            Process p = new ProcessBuilder("systemctl", "--user", "is-enabled", SYSTEMD_UNIT)
                .redirectErrorStream(true).start();
            String output = readAll(p).trim();
            p.waitFor();
            // is-enabled prints "enabled" and exits 0 when it is; anything
            // else ("disabled", "not-found", ...) means it isn't.
            return output.equals("enabled");
        } catch (Exception e) {
            return false;
        }
    }

    private static String enableLinux() {
        try {
            String java = findJavaLinux();
            if (java == null) return "Could not find a 'java' executable - make sure Java is installed and on your PATH.";

            String jarPath = findJarPath();
            if (jarPath == null) {
                return "Only works when running as a jar (java -jar FileDashboard.jar). " +
                       "Use install-autostart.sh instead when running from source/BlueJ.";
            }

            String workingDir = new File(jarPath).getParentFile().getAbsolutePath();
            File unitDir = new File(System.getProperty("user.home"), ".config/systemd/user");
            if (!unitDir.exists() && !unitDir.mkdirs()) {
                return "Could not create " + unitDir.getAbsolutePath();
            }
            File unitFile = new File(unitDir, SYSTEMD_UNIT);
            writeUnitFile(unitFile, java, jarPath, workingDir);

            String reloadErr = runSystemctl("--user", "daemon-reload");
            if (reloadErr != null) return reloadErr;

            return runSystemctl("--user", "enable", "--now", SYSTEMD_UNIT);
        } catch (Exception e) {
            return "Could not enable autostart: " + e.getMessage();
        }
    }

    private static String disableLinux() {
        // "disable --now" both stops any currently-running instance managed
        // by this unit and removes the enablement symlink; not finding the
        // unit at all (exit non-zero, "No such file or directory") counts
        // as success too, mirroring disableWindows()'s "already wasn't
        // installed" handling.
        String output;
        try {
            Process p = new ProcessBuilder("systemctl", "--user", "disable", "--now", SYSTEMD_UNIT)
                .redirectErrorStream(true).start();
            output = readAll(p);
            int exit = p.waitFor();
            if (exit != 0 && output.toLowerCase().indexOf("no such file") == -1
                    && output.toLowerCase().indexOf("does not exist") == -1) {
                return "systemctl disable failed: " + output.trim();
            }
        } catch (Exception e) {
            return "Could not disable autostart: " + e.getMessage();
        }
        return null;
    }

    private static void writeUnitFile(File unitFile, String javaPath, String jarPath, String workingDir) throws IOException {
        String unit =
            "[Unit]\n" +
            "Description=File Dashboard\n" +
            "After=network.target\n" +
            "\n" +
            "[Service]\n" +
            "Type=simple\n" +
            "ExecStart=\"" + javaPath + "\" -jar \"" + jarPath + "\"\n" +
            "WorkingDirectory=" + workingDir + "\n" +
            "Restart=on-failure\n" +
            "RestartSec=3\n" +
            "\n" +
            "[Install]\n" +
            "WantedBy=default.target\n";
        try (FileWriter w = new FileWriter(unitFile, StandardCharsets.UTF_8)) {
            w.write(unit);
        }
    }

    private static String runSystemctl(String... args) throws IOException, InterruptedException {
        String[] full = new String[args.length + 1];
        full[0] = "systemctl";
        System.arraycopy(args, 0, full, 1, args.length);
        Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
        String output = readAll(p);
        int exit = p.waitFor();
        return exit == 0 ? null : "systemctl " + String.join(" ", args) + " failed: " + output.trim();
    }

    // java.home is the JVM currently running this code, which is correct
    // regardless of PATH - same reasoning as findJavaw() above. Linux
    // JDKs/JREs don't ship a javaw equivalent; systemd doesn't need one
    // anyway since it never has a console window to hide in the first
    // place (output goes to the journal, same as any other service).
    private static String findJavaLinux() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File candidate = new File(javaHome, "bin/java");
            if (candidate.exists()) return candidate.getAbsolutePath();
        }
        try {
            Process p = new ProcessBuilder("which", "java").start();
            String output = readAll(p).trim();
            p.waitFor();
            if (!output.isEmpty()) return output.split("\\r?\\n")[0].trim();
        } catch (Exception ignored) {
            // fall through to null
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Shared helpers.
    // ---------------------------------------------------------------

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
