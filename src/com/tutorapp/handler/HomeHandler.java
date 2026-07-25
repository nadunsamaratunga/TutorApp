package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.Session;
import com.tutorapp.model.Subject;
import com.tutorapp.model.Tutor;
import com.tutorapp.model.User;
import com.tutorapp.store.DataStore;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        User current = SessionManager.getCurrentUser(exchange);

        if (!exchange.getRequestURI().getPath().equals("/")) {
            HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404 - Page not found</h1><a class='btn-link' href='/'>Go home</a>", current, null));
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class='hero'><h1>Find the right tutor, on your schedule</h1>")
            .append("<p>TutorApp connects students with verified tutors across subjects, ")
            .append("handles booking &amp; payment, and keeps study materials in one place.</p>");
        if (current == null) {
            html.append("<a class='btn-nav' href='/register'>Get Started</a>");
        }
        html.append("</div>");

        html.append("<h2>Popular Subjects</h2><div class='grid'>");
        List<Tutor> allTutors = DataStore.get().allTutors();
        List<Session> allSessions = DataStore.get().allSessions();

        Map<Subject, Long> tutorCounts = new HashMap<>();
        Map<Subject, Long> sessionCounts = new HashMap<>();
        for (Subject s : DataStore.get().allSubjects()) {
            tutorCounts.put(s, allTutors.stream().filter(t -> t.getSubjects().contains(s)).count());
            sessionCounts.put(s, allSessions.stream().filter(sess -> sess.getSubject() == s).count());
        }

        List<Subject> popularSubjects = DataStore.get().allSubjects().stream()
                .sorted((a, b) -> {
                    int byTutors = Long.compare(tutorCounts.get(b), tutorCounts.get(a));
                    if (byTutors != 0) return byTutors;
                    return Long.compare(sessionCounts.get(b), sessionCounts.get(a));
                })
                .limit(3)
                .toList();

        for (Subject s : popularSubjects) {
            html.append("<div class='card'><h3>").append(Layout.escape(s.getSubjectName())).append("</h3>")
                .append("<p class='muted'>").append(Layout.escape(s.getDescription())).append("</p>")
                .append("<p class='subject-stats'>").append(tutorCounts.get(s)).append(" tutor(s) teaching right now<br>")
                .append(sessionCounts.get(s)).append(" session(s) held</p></div>");
        }
        html.append("</div>");

        long totalTutors = allTutors.size();
        html.append("<h2>Tutors</h2>")
            .append("<div class='hero'>")
            .append("<h2>Want to start your teaching career? Join us!</h2>")
            .append("<p>Share your knowledge, set your own schedule, and get paid for the sessions you teach.</p>")
            .append("<p class='hero-stat'>").append(totalTutors).append(" tutor(s) already teaching on TutorApp</p>")
            .append("<a class='btn-nav' href='/register?role=TUTOR'>Join as a tutor</a>")
            .append("</div>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("Home", html.toString(), current, null));
    }
}
