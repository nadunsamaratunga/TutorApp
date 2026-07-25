package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.*;
import com.tutorapp.store.DataStore;
import com.tutorapp.store.SqlPersistence;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AuthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        switch (path) {
            case "/register":
                if (method.equals("GET")) showRegisterForm(exchange, null, HttpUtil.queryParams(exchange).get("role"));
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

    private void showRegisterForm(HttpExchange exchange, String error, String preselectedRole) throws IOException {
        boolean tutorSelected = "TUTOR".equalsIgnoreCase(preselectedRole);
        StringBuilder subjectChecklist = new StringBuilder();
        subjectChecklist.append("<div id='subjectsField'").append(tutorSelected ? "" : " style='display:none'").append(">")
            .append("<label>Subjects you teach</label>")
            .append("<div style='display:flex;flex-wrap:wrap;gap:10px;margin-bottom:12px'>");
        for (Subject s : DataStore.get().allSubjects()) {
            subjectChecklist.append("<label style='font-weight:400;display:flex;align-items:center;gap:4px'>")
                .append("<input type='checkbox' name='subjectIds' value='").append(s.getSubjectId()).append("'> ")
                .append(Layout.escape(s.getSubjectName())).append("</label>");
        }
        subjectChecklist.append("</div></div>");

        String body = "<h1>Create your account</h1>"
            + "<div class='card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<form method='POST' action='/register'>"
            + "<label>I am a</label>"
            + "<select name='role' id='roleSelect'>"
            + "<option value='STUDENT'" + (tutorSelected ? "" : " selected") + ">Student</option>"
            + "<option value='TUTOR'" + (tutorSelected ? " selected" : "") + ">Tutor</option>"
            + "</select>"
            + "<label>Full Name</label><input name='name' required>"
            + "<label>Email</label><input type='email' name='email' required>"
            + "<label>Password</label><input type='password' name='password' required>"
            + "<label>Phone</label><input name='phone' required>"
            + subjectChecklist
            + "<button type='submit'>Register</button>"
            + "</form>"
            + "<a class='btn-link' href='/login'>Already have an account? Log in</a>"
            + "</div>"
            + "<script>document.getElementById('roleSelect').addEventListener('change',function(){"
            + "document.getElementById('subjectsField').style.display=this.value==='TUTOR'?'':'none';});</script>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Register", body, null, null));
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, List<String>> rawForm = HttpUtil.parseFormMulti(exchange);
        String role = firstValue(rawForm, "role", "STUDENT");
        String name = firstValue(rawForm, "name", null);
        String email = firstValue(rawForm, "email", null);
        String password = firstValue(rawForm, "password", null);
        String phone = firstValue(rawForm, "phone", null);
        List<String> subjectIds = rawForm.getOrDefault("subjectIds", List.of());

        if (name == null || email == null || password == null || phone == null
                || name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank()) {
            showRegisterForm(exchange, "All fields are required.", role);
            return;
        }

        User user = role.equals("TUTOR")
                ? Tutor.register(name, email, password, phone)
                : Student.register(name, email, password, phone);

        if (user == null) {
            showRegisterForm(exchange, "An account with that email already exists.", role);
            return;
        }

        // Subjects picked at registration are added the exact same way as the "Add subject"
        // form on the tutor dashboard - tutors can still add more there later at any time.
        if (user instanceof Tutor tutor) {
            for (String subjectIdStr : subjectIds) {
                Subject s = DataStore.get().findSubject(Integer.parseInt(subjectIdStr));
                if (s == null) continue;
                tutor.addSubjectToTeach(s);
                SqlPersistence.saveTutorSubject(tutor.getUserId(), s.getSubjectId());
            }
        }

        SessionManager.createSession(exchange, user);

        if (user instanceof Tutor) {
            HttpUtil.redirect(exchange, "/tutor/dashboard");
        } else {
            HttpUtil.redirect(exchange, "/student/dashboard");
        }
    }

    private static String firstValue(Map<String, List<String>> form, String key, String fallback) {
        List<String> values = form.get(key);
        return (values == null || values.isEmpty()) ? fallback : values.get(0);
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
