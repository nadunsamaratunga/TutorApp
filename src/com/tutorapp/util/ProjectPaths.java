package com.tutorapp.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectPaths {
    private static final int MAX_LEVELS_UP = 8;
    private static volatile Path cachedRoot;

    private ProjectPaths() {}

    public static Path findProjectRoot() {
        Path cached = cachedRoot;
        if (cached != null) return cached;

        List<Path> tried = new ArrayList<>();
        for (Path start : startingPoints()) {
            Path found = searchUpward(start, tried);
            if (found != null) {
                cachedRoot = found;
                return found;
            }
        }

        // Fallback: maybe an extra wrapping folder was opened (e.g. the zip's
        // outer extraction folder) and the real project sits one level down.
        for (Path start : startingPoints()) {
            Path found = searchOneLevelDown(start, tried);
            if (found != null) {
                cachedRoot = found;
                return found;
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("Could not find 'web/templates/layout.html' near any of these locations:\n");
        for (Path p : tried) message.append("  - ").append(p).append('\n');
        message.append(
                "Make sure the 'web' folder (from the project zip) sits next to 'src' in your project, " +
                "and that it hasn't been excluded from your IDE's run/build configuration.");
        throw new IllegalStateException(message.toString());
    }

    private static Path searchUpward(Path start, List<Path> tried) {
        Path current = start;
        for (int i = 0; current != null && i < MAX_LEVELS_UP; i++) {
            tried.add(current);
            if (Files.isRegularFile(current.resolve("web").resolve("templates").resolve("layout.html"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    // Checks direct subdirectories of `start` (non-recursively) for the project marker. 
    private static Path searchOneLevelDown(Path start, List<Path> tried) {
        if (start == null || !Files.isDirectory(start)) return null;
        try (var children = Files.list(start)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (!Files.isDirectory(child)) continue;
                tried.add(child);
                if (Files.isRegularFile(child.resolve("web").resolve("templates").resolve("layout.html"))) {
                    return child;
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return null;
    }

    // A handful of plausible starting points, de-duplicated, most likely first. 
    private static Set<Path> startingPoints() {
        Set<Path> points = new LinkedHashSet<>();

        // 1) Current working directory - correct for `java -cp out com.tutorapp.Main` from the project root.
        points.add(Paths.get("").toAbsolutePath().normalize());

        // 2) Wherever the compiled classes actually are (out/production/<module>, build/classes, target/classes, ...).
        try {
            URL location = ProjectPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path codeSource = Paths.get(location.toURI());
                points.add((Files.isDirectory(codeSource) ? codeSource : codeSource.getParent()).normalize());
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // fall through with whatever points we already have
        }

        return points;
    }
}
