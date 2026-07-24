package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.util.ProjectPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Serves files that tutors/students have uploaded (qualification documents,study materials, bank payment receipts) from web/uploads/ on disk. Mirrors StaticFileHandler's approach (and path-traversal guard) for consistency, just pointed at the uploads folder instead of static assets.
public class UploadHandler implements HttpHandler {
    private static final String URL_PREFIX = "/uploads/";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(URL_PREFIX)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String relative = path.substring(URL_PREFIX.length());

        if (relative.contains("..")) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        Path file = resolveUploadsRoot().resolve(relative).normalize();
        if (!file.startsWith(resolveUploadsRoot()) || !Files.isRegularFile(file)) {
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
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }

    private static Path resolveUploadsRoot() {
        return ProjectPaths.findProjectRoot().resolve("web").resolve("uploads").normalize();
    }
}
