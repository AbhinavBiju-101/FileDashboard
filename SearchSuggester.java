import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds ranked suggestions for the search box's autocomplete dropdown.
 * Combines two signals:
 *  1. Activity - files/folders the person has actually opened, downloaded,
 *     or browsed before (from RecentActivity), which get a relevance boost
 *     and are checked first since that lookup is instant.
 *  2. A capped, shallow filename search under the given folder, for things
 *     that haven't been touched yet.
 * Folders always sort above files when scores are equal, since jumping into
 * a folder is usually the more common intent while typing.
 */
public class SearchSuggester {

    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_WALK_RESULTS = 60;

    public static List<Suggestion> suggest(String query, File searchRoot) {
        String needle = query.toLowerCase().trim();
        if (needle.isEmpty()) return new ArrayList<>();

        List<Suggestion> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Signal 1: activity-based - recent/frequent paths the person actually uses.
        for (String relPath : RecentActivity.getAllKnownPaths()) {
            if (seen.contains(relPath)) continue;
            String displayName = relPath.contains("/") ? relPath.substring(relPath.lastIndexOf('/') + 1) : relPath;
            if (displayName.isEmpty() && relPath.isEmpty()) displayName = "Home";
            if (!displayName.toLowerCase().contains(needle)) continue;

            try {
                File f = PathUtil.resolve(relPath);
                if (!f.exists()) continue;
                seen.add(relPath);
                results.add(new Suggestion(relPath, displayName, f.isDirectory(), score(displayName, needle) + 50));
            } catch (IOException ignored) {
                // stale/invalid recorded path - skip it
            }
        }

        // Signal 2: a shallow filename walk under the current folder, for
        // anything relevant that hasn't been touched yet.
        if (results.size() < MAX_SUGGESTIONS && searchRoot.isDirectory()) {
            try (Stream<Path> walk = Files.walk(searchRoot.toPath(), 4)) {
                Path base = PathUtil.resolve("").toPath();
                walk.filter(p -> !p.equals(searchRoot.toPath()))
                    .filter(p -> !HiddenFileUtil.isHiddenPath(base, p))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(needle))
                    .limit(MAX_WALK_RESULTS)
                    .forEach(p -> {
                        String relPath = base.relativize(p).toString().replace(File.separatorChar, '/');
                        if (seen.contains(relPath)) return;
                        seen.add(relPath);
                        String displayName = p.getFileName().toString();
                        results.add(new Suggestion(relPath, displayName, Files.isDirectory(p), score(displayName, needle)));
                    });
            } catch (IOException ignored) {
                // best-effort suggestions - a walk failure just means fewer results
            }
        }

        results.sort((a, b) -> {
            if (a.isFolder != b.isFolder) return a.isFolder ? -1 : 1;
            return b.score - a.score;
        });

        return results.size() > MAX_SUGGESTIONS ? results.subList(0, MAX_SUGGESTIONS) : results;
    }

    // Prefix matches rank highest, then earlier substring matches, then shorter names.
    private static int score(String name, String needle) {
        String lower = name.toLowerCase();
        int idx = lower.indexOf(needle);
        int base = (idx == 0) ? 100 : Math.max(0, 60 - idx);
        return base - Math.min(name.length(), 30);
    }

    public static class Suggestion {
        public final String path;
        public final String name;
        public final boolean isFolder;
        public final int score;

        Suggestion(String path, String name, boolean isFolder, int score) {
            this.path = path;
            this.name = name;
            this.isFolder = isFolder;
            this.score = score;
        }
    }
}
