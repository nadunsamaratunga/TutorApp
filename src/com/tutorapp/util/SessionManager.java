package com.tutorapp.util;

import com.sun.net.httpserver.HttpExchange;
import com.tutorapp.model.User;
import com.tutorapp.store.DataStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//Very small cookie-based session manager: maps a random token (stored in a cookie) to a userId in memory. No external dependencies.

public class SessionManager {
    private static final Map<String, String> TOKEN_TO_USER_ID = new ConcurrentHashMap<>();
    private static final String COOKIE_NAME = "TUTORAPP_SESSION";

    public static String createSession(HttpExchange exchange, User user) {
        String token = UUID.randomUUID().toString();
        TOKEN_TO_USER_ID.put(token, user.getUserId());
        exchange.getResponseHeaders().add("Set-Cookie", COOKIE_NAME + "=" + token + "; Path=/; HttpOnly");
        return token;
    }

    public static void destroySession(HttpExchange exchange) {
        String token = getToken(exchange);
        if (token != null) TOKEN_TO_USER_ID.remove(token);
        exchange.getResponseHeaders().add("Set-Cookie", COOKIE_NAME + "=deleted; Path=/; Max-Age=0");
    }

    public static User getCurrentUser(HttpExchange exchange) {
        String token = getToken(exchange);
        if (token == null) return null;
        String userId = TOKEN_TO_USER_ID.get(token);
        if (userId == null) return null;
        return DataStore.get().findById(userId);
    }

    private static String getToken(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String cookieHeader : cookies) {
            for (String part : cookieHeader.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(COOKIE_NAME + "=")) {
                    return trimmed.substring((COOKIE_NAME + "=").length());
                }
            }
        }
        return null;
    }
}
