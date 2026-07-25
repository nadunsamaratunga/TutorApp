package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.*;
import com.tutorapp.store.DataStore;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class StudentHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        User current = SessionManager.getCurrentUser(exchange);
        if (!(current instanceof Student)) {
            HttpUtil.redirect(exchange, "/login");
            return;
        }
        Student student = (Student) current;
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/student/dashboard")) dashboard(exchange, student);
        else if (path.equals("/student/tutors")) tutorList(exchange, student);
        else if (path.equals("/student/hire") && method.equals("POST")) hireTutor(exchange, student);
        else if (path.equals("/student/sessions")) sessions(exchange, student);
        else if (path.equals("/student/materials")) materials(exchange, student);
        else HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", current, null));
    }

    private void dashboard(HttpExchange exchange, Student student) throws IOException {
        long upcoming = student.getSessions().stream()
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE || s.getStatus() == SessionStatus.PENDING)
                .count();
        long completed = student.getSessions().stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();

        String body = "<h1>Welcome back, " + Layout.escape(student.getName()) + "</h1>"
            + "<div class='grid'>"
            + "<div class='card'><h3>" + upcoming + "</h3><p class='muted'>Upcoming / pending sessions</p></div>"
            + "<div class='card'><h3>" + completed + "</h3><p class='muted'>Completed sessions</p></div>"
            + "<div class='card'><h3>" + DataStore.get().verifiedTutors().size() + "</h3><p class='muted'>Verified tutors available</p></div>"
            + "</div>"
            + "<div class='card'><div class='quick-links'>"
            + "<a href='/student/tutors'>Find a tutor &rarr;</a>"
            + "<a href='/student/sessions'>View my sessions &rarr;</a>"
            + "<a href='/student/materials'>View study materials &rarr;</a></div></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Dashboard", body, student, null));
    }

    private void tutorList(HttpExchange exchange, Student student) throws IOException {
        Map<String, String> q = HttpUtil.queryParams(exchange);
        String subjectFilter = q.get("subject");
        String nameFilter = q.get("name");

        StringBuilder body = new StringBuilder("<h1>Find a Tutor</h1>");
        body.append("<form method='GET' action='/student/tutors' style='flex-direction:row;max-width:none;margin-bottom:20px'>")
            .append("<input type='text' name='name' placeholder='Search tutors by name' value='")
            .append(nameFilter == null ? "" : Layout.escape(nameFilter)).append("'>")
            .append("<button type='submit' class='btn-search'>Search</button>")
            .append("<span style='display:flex;align-items:center;gap:8px;margin-left:auto'>")
            .append("<label style='margin:0'>Filter by:</label>")
            .append("<select name='subject' class='auto-submit'><option value=''>All subjects</option>");
        for (Subject s : DataStore.get().allSubjects()) {
            boolean selected = String.valueOf(s.getSubjectId()).equals(subjectFilter);
            body.append("<option value='").append(s.getSubjectId()).append("'")
                .append(selected ? " selected" : "").append(">").append(Layout.escape(s.getSubjectName())).append("</option>");
        }
        body.append("</select></span></form><div class='grid'>");

        Subject filterSubject = (subjectFilter != null && !subjectFilter.isEmpty())
                ? DataStore.get().findSubject(Integer.parseInt(subjectFilter)) : null;
        List<Tutor> tutors = student.searchTutor(filterSubject, nameFilter);
        for (Tutor t : tutors) {
            List<SessionOption> options = t.getSessionOptions();
            if (filterSubject != null) {
                options = options.stream()
                        .filter(o -> o.getSubject().getSubjectId() == filterSubject.getSubjectId())
                        .toList();
            }

            if (options.isEmpty()) {
                body.append("<div class='card tutor-card'>");
                appendTutorHeader(body, t);
                body.append("<p class='muted'>This tutor hasn't listed any bookable sessions yet.</p>");
                body.append("</div>");
                continue;
            }

            // Each session option gets its own box so tutors with several offerings don't get
            // squeezed into one long card.
            for (SessionOption o : options) {
                body.append("<div class='card tutor-card'>");
                appendTutorHeader(body, t);

                body.append("<div style='border-top:1px solid #e5ebf3;padding-top:12px;margin-top:12px'>")
                    .append("<p style='font-weight:700;font-size:15px;text-align:center'>").append(Layout.escape(o.getTitle()))
                    .append(" &middot; ").append(Layout.escape(o.getSubject().getSubjectName())).append("</p>")
                    .append("<ul class='option-details'>")
                    .append("<li>Time: ").append(o.getDurationMinutes()).append(" min</li>")
                    .append("<li>Price: Rs. ").append(String.format("%.2f", o.getPrice())).append("</li>")
                    .append("<li>Up to ").append(o.getMaxStudents()).append(" student(s) per session</li>")
                    .append("<li>").append(t.materialsFor(o).size()).append(" study material(s) for this session</li>")
                    .append("</ul>");

                if (student.hasRequested(o)) {
                    body.append("<p class='muted'>You've already booked this session.</p></div>");
                    body.append("</div>");
                    continue;
                }

                List<SessionRequest> slots = o.existingSlots();
                if (!slots.isEmpty()) {
                    body.append("<p class='muted' style='margin-bottom:4px'>Upcoming sessions - join one below, or propose a new time:</p>");
                    for (SessionRequest slot : slots) {
                        int booked = o.getBookedCount(slot.getRequestDate(), slot.getStartTime());
                        boolean full = o.isFull(slot.getRequestDate(), slot.getStartTime());
                        body.append("<div style='display:flex;align-items:center;gap:10px;margin:6px 0'>")
                            .append("<span>").append(slot.getRequestDate()).append(" &middot; ")
                            .append(slot.getStartTime()).append("-").append(slot.getEndTime()).append("</span>")
                            .append("<span class='muted'>").append(booked).append(" / ").append(o.getMaxStudents()).append(" joined</span>");
                        if (full) {
                            body.append("<span class='badge badge-cancelled'>Session is full</span>");
                        } else {
                            body.append("<form method='POST' action='/student/hire' style='display:inline;max-width:none'>")
                                .append("<input type='hidden' name='tutorId' value='").append(t.getUserId()).append("'>")
                                .append("<input type='hidden' name='optionId' value='").append(o.getOptionId()).append("'>")
                                .append("<input type='hidden' name='date' value='").append(slot.getRequestDate()).append("'>")
                                .append("<input type='hidden' name='startTime' value='").append(slot.getStartTime()).append("'>")
                                .append("<button type='submit'>Join this session</button></form>");
                        }
                        body.append("</div>");
                    }
                }

                body.append("<details style='margin-top:8px'><summary class='propose-time-btn'>Propose a new time</summary>")
                    .append("<form method='POST' action='/student/hire' style='margin-top:8px'>")
                    .append("<input type='hidden' name='tutorId' value='").append(t.getUserId()).append("'>")
                    .append("<input type='hidden' name='optionId' value='").append(o.getOptionId()).append("'>")
                    .append("<label>Date</label><input type='date' name='date' required>")
                    .append("<label>Start time</label><input type='time' name='startTime' required>")
                    .append("<button type='submit'>Request This Session</button></form></details>");
                body.append("</div>"); // close border-top section
                body.append("</div>"); // close tutor-card
            }
        }
        if (tutors.isEmpty()) {
            body.append("<div class='card'><p class='muted'>No tutors yet. Check back soon!</p></div>");
        }
        body.append("</div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Find a Tutor", body.toString(), student, q.get("msg")));
    }

    // Renders a tutor's name/avatar, verification badge, subject tags, and verified qualifications -
    // the header shared by every session-option box for that tutor.
    private void appendTutorHeader(StringBuilder body, Tutor t) {
        body.append("<h3>").append(Layout.avatarHtml(t, "avatar")).append(" ").append(Layout.escape(t.getName()))
            .append(t.isVerified() ? " &#9989;" : "").append("</h3>");
        body.append("<div class='subject-tags'>");
        for (Subject s : t.getSubjects()) {
            body.append("<span class='subject-tag'>").append(Layout.escape(s.getSubjectName())).append("</span>");
        }
        body.append("</div>");

        if (!t.getQualifications().isEmpty()) {
            body.append("<div style='margin:8px 0'>");
            for (Qualification qual : t.getQualifications()) {
                if (qual.getStatus() != QualificationStatus.VERIFIED) continue;
                body.append("<p class='muted'>&#9989; ").append(Layout.escape(qual.getTitle())).append("</p>");
            }
            body.append("</div>");
        }
    }

    private void hireTutor(HttpExchange exchange, Student student) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Tutor tutor = (Tutor) DataStore.get().findById(form.get("tutorId"));
        SessionOption option = tutor == null ? null : tutor.findSessionOption(Integer.parseInt(form.get("optionId")));
        if (tutor == null || option == null) {
            HttpUtil.redirect(exchange, "/student/tutors?msg=" + HttpUtil.encode("That session option is no longer available."));
            return;
        }
        LocalDate date = LocalDate.parse(form.get("date"));
        LocalTime startTime = LocalTime.parse(form.get("startTime"));

        if (student.hasRequested(option)) {
            HttpUtil.redirect(exchange, "/student/tutors?msg=" + HttpUtil.encode("You've already booked this session."));
            return;
        }
        if (option.isFull(date, startTime)) {
            HttpUtil.redirect(exchange, "/student/tutors?msg=" + HttpUtil.encode(
                    "Sorry, that specific time slot is now full. Try joining a different one, or propose a new time."));
            return;
        }

        SessionRequest request = student.hireTutor(tutor, option, date, startTime);
        if (request == null) {
            HttpUtil.redirect(exchange, "/student/tutors?msg=" + HttpUtil.encode(
                    "That session is no longer available. Please try again."));
            return;
        }

        HttpUtil.redirect(exchange, "/student/sessions?msg=" + HttpUtil.encode("Session request sent to " + tutor.getName() + "!"));
    }

    private void sessions(HttpExchange exchange, Student student) throws IOException {
        StringBuilder body = new StringBuilder("<h1>My Sessions</h1>");

        body.append("<div class='card'><h2>Pending Requests</h2><table><tr><th>Tutor</th><th>Option</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th></tr>");
        for (SessionRequest r : student.myRequests()) {
            body.append("<tr><td>").append(Layout.escape(r.getTutor().getName())).append("</td><td>")
                .append(Layout.escape(r.getOption().getTitle())).append("</td><td>")
                .append(Layout.escape(r.getSubject().getSubjectName())).append("</td><td>")
                .append(r.getRequestDate()).append("</td><td>").append(r.getRequestTime()).append("</td><td>")
                .append("Rs. ").append(String.format("%.2f", r.getPrice())).append("</td><td>")
                .append(badge(r.getStatus().name())).append("</td></tr>");
        }
        body.append("</table></div>");

        body.append("<div class='card' style='margin-top:20px'><h2>Sessions</h2><table><tr><th>Tutor</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th><th>Payment</th><th></th></tr>");
        for (Session s : student.getSessions()) {
            body.append("<tr><td>").append(Layout.escape(s.getTutor().getName())).append("</td><td>")
                .append(Layout.escape(s.getSubject().getSubjectName())).append("</td><td>")
                .append(s.getScheduledDate()).append("</td><td>").append(s.getStartTime()).append("-").append(s.getEndTime())
                .append("</td><td>Rs. ").append(String.format("%.2f", s.getPrice())).append("</td><td>")
                .append(badge(s.getStatus().name())).append("</td><td>");
            if (s.getPayment() == null) {
                body.append(badge("PENDING"));
            } else {
                body.append(badge(s.getPayment().getStatus().name()));
            }
            body.append("</td><td>");
            if (s.getStatus() == SessionStatus.PENDING && s.getPayment() == null) {
                body.append("<a class='btn-link' href='/payment/pay?sessionId=").append(s.getSessionId()).append("'>Pay now</a>");
            } else if (s.getPayment() != null && s.getPayment().getStatus() == PaymentStatus.PENDING) {
                body.append("<span class='muted'>Awaiting admin verification</span>");
            } else if (s.getStatus() == SessionStatus.ACTIVE) {
                body.append("<a class='btn-link' href='").append(Layout.escape(s.getMeetingLink())).append("' target='_blank'>Join</a>");
            }
            body.append("</td></tr>");
        }
        body.append("</table></div>");

        Map<String, String> q = HttpUtil.queryParams(exchange);
        HttpUtil.sendHtml(exchange, 200, Layout.page("My Sessions", body.toString(), student, q.get("msg")));
    }

    private void materials(HttpExchange exchange, Student student) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Study Materials</h1>");

        // Materials only unlock once a session is paid for and the tutor has activated it.
        List<Session> activeSessions = student.sessionsWithAccessibleMaterials();

        if (activeSessions.isEmpty()) {
            body.append("<div class='card'><p class='muted'>You'll see materials here once you've paid for a session and it becomes active.</p></div>");
        }

        for (Session s : activeSessions) {
            Tutor t = s.getTutor();
            List<StudyMaterial> materials = t.materialsFor(s.getOption());
            body.append("<div class='card'><h3>").append(Layout.escape(t.getName())).append(" &middot; ")
                .append(Layout.escape(s.getOption().getTitle())).append("</h3>")
                .append("<p class='muted'>").append(s.getScheduledDate()).append(" &middot; ")
                .append(s.getStartTime()).append("-").append(s.getEndTime()).append("</p>");
            if (materials.isEmpty()) {
                body.append("<p class='muted'>No materials uploaded yet for this session.</p>");
            } else {
                body.append("<table><tr><th>Title</th><th>Session</th><th>Uploaded</th><th></th></tr>");
                for (StudyMaterial m : materials) {
                    body.append("<tr><td>").append(Layout.escape(m.getTitle())).append("</td><td>")
                        .append(Layout.escape(s.getOption().getTitle())).append("</td><td>")
                        .append(m.getUploadDate()).append("</td><td><a class='btn-link' href='")
                        .append(Layout.escape(m.download())).append("' target='_blank'>Download</a></td></tr>");
                }
                body.append("</table>");
            }
            body.append("</div>");
        }
        HttpUtil.sendHtml(exchange, 200, Layout.page("Study Materials", body.toString(), student, null));
    }

    static String badge(String status) {
        return "<span class='badge badge-" + status.toLowerCase() + "'>" + status + "</span>";
    }
}
