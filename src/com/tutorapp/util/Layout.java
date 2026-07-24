package com.tutorapp.util;

import com.tutorapp.model.User;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Layout {

    private static final String TEMPLATE = loadTemplate();

    public static String page(String title, String bodyHtml, User currentUser, String flash) {
        String nav = buildNav(currentUser);
        String flashHtml = (flash == null || flash.isEmpty())
                ? ""
                : "<div class='flash'>" + escape(flash) + "</div>";

        return TEMPLATE
                .replace("{{TITLE}}", escape(title))
                .replace("{{NAV}}", nav)
                .replace("{{FLASH}}", flashHtml)
                .replace("{{BODY}}", bodyHtml);
    }

    private static String buildNav(User currentUser) {
        StringBuilder nav = new StringBuilder();
        if (currentUser == null) {
            nav.append("<a href='/login'>Login</a><a class='btn-nav' href='/register'>Register</a>");
        } else {
            String role = currentUser.getRole();
            if (role.equals("STUDENT")) {
                nav.append("<a href='/student/dashboard'>Dashboard</a>")
                   .append("<a href='/student/tutors'>Find a Tutor</a>")
                   .append("<a href='/student/sessions'>My Sessions</a>");
            } else if (role.equals("TUTOR")) {
                nav.append("<a href='/tutor/dashboard'>Dashboard</a>")
                   .append("<a href='/tutor/options'>Pricing</a>")
                   .append("<a href='/tutor/requests'>Requests</a>")
                   .append("<a href='/tutor/sessions'>Sessions</a>")
                   .append("<a href='/tutor/materials'>Materials</a>");
            } else if (role.equals("ADMIN")) {
                nav.append("<a href='/admin/dashboard'>Dashboard</a>")
                   .append("<a href='/admin/tutors'>Tutors</a>")
                   .append("<a href='/admin/payments'>Payments</a>")
                   .append("<a href='/admin/subjects'>Subjects</a>");
            }
            nav.append("<a href='/profile'>Profile</a>");
            nav.append("<span class='who'>").append(escape(currentUser.getName())).append("</span>");
            nav.append("<a class='btn-nav' href='/logout'>Logout</a>");
        }
        return nav.toString();
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    //Renders a user's profile picture, or a fallback circle with their initials if they haven't uploaded one. 
    public static String avatarHtml(User user, String sizeClass) {
        if (user.getProfilePictureUrl() != null) {
            return "<img src='" + escape(user.getProfilePictureUrl()) + "' alt='" + escape(user.getName())
                    + "' class='avatar " + sizeClass + "'>";
        }
        return "<div class='avatar " + sizeClass + " avatar-placeholder'>" + escape(initials(user.getName())) + "</div>";
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String result = String.valueOf(parts[0].charAt(0));
        if (parts.length > 1) result += parts[parts.length - 1].charAt(0);
        return result.toUpperCase();
    }

    private static String loadTemplate() {
        Path file = ProjectPaths.findProjectRoot().resolve("web").resolve("templates").resolve("layout.html");
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read layout template at " + file, e);
        }
    }
}
