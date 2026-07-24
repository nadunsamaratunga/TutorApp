package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.Subject;
import com.tutorapp.model.Tutor;
import com.tutorapp.model.User;
import com.tutorapp.store.DataStore;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;

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
        for (Subject s : DataStore.get().allSubjects()) {
            html.append("<div class='card'><h3>").append(Layout.escape(s.getSubjectName())).append("</h3>")
                .append("<p class='muted'>").append(Layout.escape(s.getDescription())).append("</p></div>");
        }
        html.append("</div>");

        long verifiedCount = DataStore.get().verifiedTutors().size();
        html.append("<h2>Tutors</h2><div class='card'><p>")
            .append(verifiedCount)
            .append(" verified tutor(s) ready to teach. <a class='btn-link' href='/register'>Join as a tutor</a></p></div>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("Home", html.toString(), current, null));
    }
}
