package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.util.ProjectPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Serves plain static assets (CSS/JS/images) from web/static/ on disk. No framework - just enough to keep HTML/CSS/JS as real separate files instead of Java string constants.
 
public class StaticFileHandler implements HttpHandler {
    private static final String URL_PREFIX = "/static/";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(URL_PREFIX)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String relative = path.substring(URL_PREFIX.length());

        // Basic path-traversal guard.
        if (relative.contains("..")) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        Path file = resolveStaticRoot().resolve(relative).normalize();
        if (!file.startsWith(resolveStaticRoot()) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentTypeFor(file.toString()));
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    // Same robust, IDE-agnostic project-root lookup used by Layout's template loader.
    private static Path resolveStaticRoot() {
        return ProjectPaths.findProjectRoot().resolve("web").resolve("static").normalize();
    }
}
