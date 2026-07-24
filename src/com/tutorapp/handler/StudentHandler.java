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
            + "<div class='card'><a class='btn-link' href='/student/tutors'>Find a tutor &rarr;</a><br>"
            + "<a class='btn-link' href='/student/sessions'>View my sessions &rarr;</a><br>"
            + "<a class='btn-link' href='/student/materials'>View study materials &rarr;</a></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Dashboard", body, student, null));
    }

    private void tutorList(HttpExchange exchange, Student student) throws IOException {
        Map<String, String> q = HttpUtil.queryParams(exchange);
        String subjectFilter = q.get("subject");

        StringBuilder body = new StringBuilder("<h1>Find a Tutor</h1>");
        body.append("<form method='GET' action='/student/tutors' style='flex-direction:row;max-width:none;margin-bottom:20px'>")
            .append("<select name='subject' class='auto-submit'><option value=''>All subjects</option>");
        for (Subject s : DataStore.get().allSubjects()) {
            boolean selected = String.valueOf(s.getSubjectId()).equals(subjectFilter);
            body.append("<option value='").append(s.getSubjectId()).append("'")
                .append(selected ? " selected" : "").append(">").append(Layout.escape(s.getSubjectName())).append("</option>");
        }
        body.append("</select></form><div class='grid'>");

        Subject filterSubject = (subjectFilter != null && !subjectFilter.isEmpty())
                ? DataStore.get().findSubject(Integer.parseInt(subjectFilter)) : null;
        List<Tutor> tutors = student.searchTutor(filterSubject);
        for (Tutor t : tutors) {
            List<SessionOption> options = t.getSessionOptions();
            if (filterSubject != null) {
                options = options.stream()
                        .filter(o -> o.getSubject().getSubjectId() == filterSubject.getSubjectId())
                        .toList();
            }

            body.append("<div class='card'><h3>").append(Layout.avatarHtml(t, "avatar")).append(" ").append(Layout.escape(t.getName()))
                .append(t.isVerified() ? " &#9989;" : "").append("</h3>");
            for (Subject s : t.getSubjects()) {
                body.append("<span class='subject-tag'>").append(Layout.escape(s.getSubjectName())).append("</span>");
            }
            body.append("<p class='muted'>").append(t.getStudyMaterials().size()).append(" material(s) shared</p>");

            if (!t.getQualifications().isEmpty()) {
                body.append("<div style='margin:8px 0'>");
                for (Qualification qual : t.getQualifications()) {
                    if (qual.getStatus() != QualificationStatus.VERIFIED) continue;
                    body.append("<p class='muted'>&#9989; ").append(Layout.escape(qual.getTitle())).append("</p>");
                }
                body.append("</div>");
            }

            if (options.isEmpty()) {
                body.append("<p class='muted'>This tutor hasn't listed any bookable sessions yet.</p>");
            } else {
                for (SessionOption o : options) {
                    body.append("<div style='border-top:1px solid #e5ebf3;padding-top:12px;margin-top:12px'>")
                        .append("<p><b>").append(Layout.escape(o.getTitle())).append("</b> &middot; ")
                        .append(Layout.escape(o.getSubject().getSubjectName())).append(" &middot; ")
                        .append(o.getDurationMinutes()).append(" min &middot; <b>Rs. ")
                        .append(String.format("%.2f", o.getPrice())).append("</b> &middot; ")
                        .append("<span class='muted'>up to ").append(o.getMaxStudents()).append(" student(s) per session</span></p>");

                    if (student.hasRequested(o)) {
                        body.append("<p class='muted'>You've already booked this session.</p></div>");
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

                    body.append("<details style='margin-top:8px'><summary class='muted' style='cursor:pointer'>Propose a new time</summary>")
                        .append("<form method='POST' action='/student/hire' style='margin-top:8px'>")
                        .append("<input type='hidden' name='tutorId' value='").append(t.getUserId()).append("'>")
                        .append("<input type='hidden' name='optionId' value='").append(o.getOptionId()).append("'>")
                        .append("<label>Date</label><input type='date' name='date' required>")
                        .append("<label>Start time</label><input type='time' name='startTime' required>")
                        .append("<button type='submit'>Request This Session</button></form></details>");
                    body.append("</div>");
                }
            }
            body.append("</div>");
        }
        if (tutors.isEmpty()) {
            body.append("<div class='card'><p class='muted'>No tutors yet. Check back soon!</p></div>");
        }
        body.append("</div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Find a Tutor", body.toString(), student, q.get("msg")));
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

        body.append("<h2>Pending Requests</h2><table><tr><th>Tutor</th><th>Option</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th></tr>");
        for (SessionRequest r : student.myRequests()) {
            body.append("<tr><td>").append(Layout.escape(r.getTutor().getName())).append("</td><td>")
                .append(Layout.escape(r.getOption().getTitle())).append("</td><td>")
                .append(Layout.escape(r.getSubject().getSubjectName())).append("</td><td>")
                .append(r.getRequestDate()).append("</td><td>").append(r.getRequestTime()).append("</td><td>")
                .append("Rs. ").append(String.format("%.2f", r.getPrice())).append("</td><td>")
                .append(badge(r.getStatus().name())).append("</td></tr>");
        }
        body.append("</table>");

        body.append("<h2 style='margin-top:30px'>Sessions</h2><table><tr><th>Tutor</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th><th>Payment</th><th></th></tr>");
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
        body.append("</table>");

        Map<String, String> q = HttpUtil.queryParams(exchange);
        HttpUtil.sendHtml(exchange, 200, Layout.page("My Sessions", body.toString(), student, q.get("msg")));
    }

    private void materials(HttpExchange exchange, Student student) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Study Materials</h1>");

        List<Tutor> myTutors = student.tutorsBooked();

        if (myTutors.isEmpty()) {
            body.append("<div class='card'><p class='muted'>You'll see materials here once you've booked a session with a tutor.</p></div>");
        }
        for (Tutor t : myTutors) {
            body.append("<div class='card'><h3>").append(Layout.escape(t.getName())).append("</h3>");
            if (t.getStudyMaterials().isEmpty()) {
                body.append("<p class='muted'>No materials uploaded yet.</p>");
            } else {
                body.append("<table><tr><th>Title</th><th>Uploaded</th><th></th></tr>");
                for (StudyMaterial m : t.getStudyMaterials()) {
                    body.append("<tr><td>").append(Layout.escape(m.getTitle())).append("</td><td>")
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
