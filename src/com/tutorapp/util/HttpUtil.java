package com.tutorapp.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HttpUtil {

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            String key = decode(pair.substring(0, idx));
            String value = decode(pair.substring(idx + 1));
            map.put(key, value);
        }
        return map;
    }

    public static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(baos);
        String body = baos.toString(StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%20"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    public static Map<String, String> queryParams(HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getQuery());
    }

    // Multipart/form-data support (for file uploads). Added alongside the existing URL-encoded parseForm() above - unrelated forms are untouched and keep using parseForm()/parseQuery() exactly as before.
    

    // A single uploaded file's original name, declared content type, and raw bytes. 
    public static class UploadedFile {
        public final String filename;
        public final String contentType;
        public final byte[] data;

        public UploadedFile(String filename, String contentType, byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }

        public boolean isEmpty() {
            return filename == null || filename.isBlank() || data == null || data.length == 0;
        }
    }

    //Result of parsing a multipart/form-data body: plain text fields plus any uploaded files. 
    public static class MultipartForm {
        public final Map<String, String> fields = new HashMap<>();
        public final Map<String, UploadedFile> files = new HashMap<>();

        public String get(String key) { return fields.get(key); }
    }

    // Parses a multipart/form-data request body (used by the file-upload forms). 
    public static MultipartForm parseMultipart(HttpExchange exchange) throws IOException {
        MultipartForm result = new MultipartForm();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            return result;
        }
        String boundary = extractBoundary(contentType);
        if (boundary == null) return result;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(baos);
        byte[] body = baos.toByteArray();

        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = indexOf(body, delimiter, 0);
        while (pos != -1) {
            int afterDelimiter = pos + delimiter.length;
            if (afterDelimiter + 1 < body.length && body[afterDelimiter] == '-' && body[afterDelimiter + 1] == '-') {
                break; // final boundary
            }
            int contentStart = skipLeadingCrLf(body, afterDelimiter);
            int nextBoundary = indexOf(body, delimiter, contentStart);
            if (nextBoundary == -1) break;
            parsePart(body, contentStart, nextBoundary, result);
            pos = nextBoundary;
        }
        return result;
    }

    private static void parsePart(byte[] body, int start, int end, MultipartForm result) {
        byte[] headerSep = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int headerEnd = indexOf(body, headerSep, start);
        if (headerEnd == -1 || headerEnd > end) return;

        String headerText = new String(body, start, headerEnd - start, StandardCharsets.ISO_8859_1);
        int contentStart = headerEnd + headerSep.length;
        int contentEnd = end;
        if (contentEnd >= contentStart + 2 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
            contentEnd -= 2; // strip the CRLF that precedes the next boundary
        }
        if (contentEnd < contentStart) contentEnd = contentStart;

        String name = headerValue(headerText, "name");
        String filename = headerValue(headerText, "filename");
        if (name == null) return;

        byte[] data = Arrays.copyOfRange(body, contentStart, contentEnd);
        if (filename != null) {
            String partContentType = null;
            for (String line : headerText.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-type:")) {
                    partContentType = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            result.files.put(name, new UploadedFile(filename, partContentType, data));
        } else {
            result.fields.put(name, new String(data, StandardCharsets.UTF_8));
        }
    }

    private static String headerValue(String headerText, String param) {
        for (String line : headerText.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition:")) continue;
            String marker = param + "=\"";
            int idx = line.indexOf(marker);
            if (idx == -1) continue;
            int valueStart = idx + marker.length();
            int valueEnd = line.indexOf('"', valueStart);
            if (valueEnd == -1) continue;
            return line.substring(valueStart, valueEnd);
        }
        return null;
    }

    private static String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                String b = part.substring("boundary=".length());
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() >= 2) {
                    b = b.substring(1, b.length() - 1);
                }
                return b;
            }
        }
        return null;
    }

    private static int skipLeadingCrLf(byte[] data, int from) {
        int i = from;
        if (i + 1 < data.length && data[i] == '\r' && data[i + 1] == '\n') i += 2;
        return i;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
