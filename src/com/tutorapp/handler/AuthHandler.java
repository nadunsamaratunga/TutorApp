package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.*;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;
import java.util.Map;

public class AuthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        switch (path) {
            case "/register":
                if (method.equals("GET")) showRegisterForm(exchange, null);
                else handleRegister(exchange);
                break;
            case "/login":
                if (method.equals("GET")) showLoginForm(exchange, null);
                else handleLogin(exchange, false);
                break;
            case "/admin/login":
                if (method.equals("GET")) showAdminLoginForm(exchange, null);
                else handleLogin(exchange, true);
                break;
            case "/logout":
                SessionManager.destroySession(exchange);
                HttpUtil.redirect(exchange, "/");
                break;
            default:
                HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", null, null));
        }
    }

    private void showRegisterForm(HttpExchange exchange, String error) throws IOException {
        String body = "<h1>Create your account</h1>"
            + "<div class='card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<form method='POST' action='/register'>"
            + "<label>I am a</label>"
            + "<select name='role'><option value='STUDENT'>Student</option><option value='TUTOR'>Tutor</option></select>"
            + "<label>Full Name</label><input name='name' required>"
            + "<label>Email</label><input type='email' name='email' required>"
            + "<label>Password</label><input type='password' name='password' required>"
            + "<label>Phone</label><input name='phone' required>"
            + "<button type='submit'>Register</button>"
            + "</form>"
            + "<a class='btn-link' href='/login'>Already have an account? Log in</a>"
            + "</div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Register", body, null, null));
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        String role = form.getOrDefault("role", "STUDENT");
        String name = form.get("name");
        String email = form.get("email");
        String password = form.get("password");
        String phone = form.get("phone");

        if (name == null || email == null || password == null || phone == null
                || name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank()) {
            showRegisterForm(exchange, "All fields are required.");
            return;
        }

        User user = role.equals("TUTOR")
                ? Tutor.register(name, email, password, phone)
                : Student.register(name, email, password, phone);

        if (user == null) {
            showRegisterForm(exchange, "An account with that email already exists.");
            return;
        }
        SessionManager.createSession(exchange, user);

        if (user instanceof Tutor) {
            HttpUtil.redirect(exchange, "/tutor/dashboard");
        } else {
            HttpUtil.redirect(exchange, "/student/dashboard");
        }
    }

    private void showLoginForm(HttpExchange exchange, String error) throws IOException {
        String body = "<h1>Log in</h1>"
            + "<div class='card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<form method='POST' action='/login'>"
            + "<label>Email</label><input type='email' name='email' required>"
            + "<label>Password</label><input type='password' name='password' required>"
            + "<button type='submit'>Log in</button>"
            + "</form>"
            + "<a class='btn-link' href='/register'>Need an account? Register</a>"
            + "<div class='admin-entry'>"
            + "<a class='btn-admin' href='/admin/login'>&#128274; Admin Login</a>"
            + "</div>"
            + "</div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Login", body, null, null));
    }

    private void showAdminLoginForm(HttpExchange exchange, String error) throws IOException {
        String body = "<h1>&#128274; Admin Login</h1>"
            + "<div class='card admin-card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<p class='muted'>Restricted access. Administrator credentials required.</p>"
            + "<form method='POST' action='/admin/login'>"
            + "<label>Admin Email</label><input type='email' name='email' required>"
            + "<label>Password</label><input type='password' name='password' required>"
            + "<button type='submit'>Log in as Admin</button>"
            + "</form>"
            + "<a class='btn-link' href='/login'>&larr; Back to regular login</a>"
            + "</div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Admin Login", body, null, null));
    }

    private void handleLogin(HttpExchange exchange, boolean adminOnly) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        String email = form.get("email");
        String password = form.get("password");

        if (adminOnly) {
            Admin admin = Admin.authenticateAdmin(email, password);
            if (admin == null) {
                showAdminLoginForm(exchange, "Invalid administrator credentials.");
                return;
            }
            SessionManager.createSession(exchange, admin);
            HttpUtil.redirect(exchange, "/admin/dashboard");
            return;
        }

        User user = User.authenticate(email, password);
        if (user == null) {
            showLoginForm(exchange, "Invalid email or password.");
            return;
        }
        SessionManager.createSession(exchange, user);

        switch (user.getRole()) {
            case "TUTOR": HttpUtil.redirect(exchange, "/tutor/dashboard"); break;
            case "ADMIN": HttpUtil.redirect(exchange, "/admin/dashboard"); break;
            default: HttpUtil.redirect(exchange, "/student/dashboard");
        }
    }
}
