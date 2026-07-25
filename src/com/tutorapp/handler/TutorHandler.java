package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.*;
import com.tutorapp.store.DataStore;
import com.tutorapp.store.SqlPersistence;
import com.tutorapp.util.FileStorage;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;
import java.util.Map;

import static com.tutorapp.handler.StudentHandler.badge;

public class TutorHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        User current = SessionManager.getCurrentUser(exchange);
        if (!(current instanceof Tutor)) {
            HttpUtil.redirect(exchange, "/login");
            return;
        }
        Tutor tutor = (Tutor) current;
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/tutor/dashboard")) dashboard(exchange, tutor);
        else if (path.equals("/tutor/qualifications") && method.equals("GET")) qualificationsForm(exchange, tutor, null);
        else if (path.equals("/tutor/qualifications") && method.equals("POST")) uploadQualification(exchange, tutor);
        else if (path.equals("/tutor/subjects") && method.equals("POST")) addSubject(exchange, tutor);
        else if (path.equals("/tutor/materials") && method.equals("GET")) materialsPage(exchange, tutor);
        else if (path.equals("/tutor/materials") && method.equals("POST")) uploadMaterial(exchange, tutor);
        else if (path.equals("/tutor/options") && method.equals("GET")) optionsPage(exchange, tutor, null);
        else if (path.equals("/tutor/options") && method.equals("POST")) addSessionOption(exchange, tutor);
        else if (path.equals("/tutor/options/remove") && method.equals("POST")) removeSessionOption(exchange, tutor);
        else if (path.equals("/tutor/requests")) requestsPage(exchange, tutor);
        else if (path.equals("/tutor/requests/accept") && method.equals("POST")) acceptRequest(exchange, tutor);
        else if (path.equals("/tutor/requests/reject") && method.equals("POST")) rejectRequest(exchange, tutor);
        else if (path.equals("/tutor/sessions")) sessionsPage(exchange, tutor);
        else if (path.equals("/tutor/sessions/complete") && method.equals("POST")) completeSession(exchange, tutor);
        else HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", current, null));
    }

    private void dashboard(HttpExchange exchange, Tutor tutor) throws IOException {
        long pendingRequests = tutor.pendingRequests().size();
        long activeSessions = tutor.allSessions().stream().filter(s -> s.getStatus() == SessionStatus.ACTIVE).count();

        String verifiedBadge = tutor.isVerified()
                ? "<span class='badge badge-verified'>VERIFIED</span>"
                : "<span class='badge badge-pending'>AWAITING VERIFICATION</span>";

        String body = "<h1>Welcome, " + Layout.escape(tutor.getName()) + " " + verifiedBadge + "</h1>";
        if (!tutor.isVerified()) {
            body += "<div class='card'><p>Your account is awaiting admin verification. Upload your qualifications so an admin can verify you and students can find you.</p>"
                  + "<div class='quick-links'><a href='/tutor/qualifications'>Upload qualifications &rarr;</a></div></div>";
        }
        if (tutor.getSessionOptions().isEmpty()) {
            body += "<div class='card'><p>You haven't listed any session options yet, so students can't book you. "
                  + "Add at least one priced session to appear as bookable.</p>"
                  + "<a class='btn-link' href='/tutor/options'>Add a session option &rarr;</a></div>";
        }
        body += "<div class='grid'>"
            + "<div class='card'><h3>" + pendingRequests + "</h3><p class='muted'>Pending session requests</p></div>"
            + "<div class='card'><h3>" + activeSessions + "</h3><p class='muted'>Active sessions</p></div>"
            + "<div class='card'><h3>" + tutor.getSessionOptions().size() + "</h3><p class='muted'>Session options listed</p></div>"
            + "<div class='card'><h3>" + tutor.getStudyMaterials().size() + "</h3><p class='muted'>Materials shared</p></div>"
            + "</div>"
            + "<div class='card'><div class='quick-links'>"
            + "<a href='/tutor/options'>Manage session options &amp; pricing &rarr;</a>"
            + "<a href='/tutor/requests'>View session requests &rarr;</a>"
            + "<a href='/tutor/sessions'>View my sessions &rarr;</a>"
            + "<a href='/tutor/materials'>Manage study materials &rarr;</a>"
            + "<a href='/tutor/qualifications'>Manage qualifications &amp; subjects &rarr;</a></div></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Dashboard", body, tutor, null));
    }

    private void qualificationsForm(HttpExchange exchange, Tutor tutor, String msg) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Qualifications &amp; Subjects</h1>");
        body.append("<div class='two-col'>");

        body.append("<div class='card'><h2>Upload Qualification</h2>")
            .append("<form method='POST' action='/tutor/qualifications' enctype='multipart/form-data'>")
            .append("<label>Title</label><input name='title' required>")
            .append("<label>Document (PDF or image)</label><input type='file' name='document' accept='.pdf,.png,.jpg,.jpeg' required>")
            .append("<button type='submit'>Upload</button></form>");
        if (!tutor.getQualifications().isEmpty()) {
            body.append("<table><tr><th>Title</th><th>Status</th></tr>");
            for (Qualification q : tutor.getQualifications()) {
                body.append("<tr><td>").append(Layout.escape(q.getTitle())).append("</td><td>")
                    .append(badge(q.getStatus().name())).append("</td></tr>");
            }
            body.append("</table>");
        }
        body.append("</div>");

        body.append("<div class='card'><h2>Subjects You Teach</h2>")
            .append("<form method='POST' action='/tutor/subjects'>")
            .append("<label>Subject</label><select name='subjectId'>");
        for (Subject s : DataStore.get().allSubjects()) {
            body.append("<option value='").append(s.getSubjectId()).append("'>").append(Layout.escape(s.getSubjectName())).append("</option>");
        }
        body.append("</select><button type='submit'>Add subject</button></form>");
        body.append("<div style='margin-top:12px'>");
        for (Subject s : tutor.getSubjects()) {
            body.append("<span class='subject-tag'>").append(Layout.escape(s.getSubjectName())).append("</span>");
        }
        body.append("</div></div></div>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("Qualifications", body.toString(), tutor, msg));
    }

    private void uploadQualification(HttpExchange exchange, Tutor tutor) throws IOException {
        HttpUtil.MultipartForm form = HttpUtil.parseMultipart(exchange);
        String title = form.get("title");
        HttpUtil.UploadedFile document = form.files.get("document");
        if (title == null || title.isBlank() || document == null || document.isEmpty()) {
            qualificationsForm(exchange, tutor, "Please provide a title and choose a document to upload.");
            return;
        }
        String documentURL = FileStorage.save(document, "qualifications");
        Qualification q = tutor.uploadQualification(title, documentURL);
        SqlPersistence.saveQualification(tutor.getUserId(), q);
        HttpUtil.redirect(exchange, "/tutor/qualifications");
    }

    private void addSubject(HttpExchange exchange, Tutor tutor) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Subject s = DataStore.get().findSubject(Integer.parseInt(form.get("subjectId")));
        tutor.addSubjectToTeach(s);
        if (s != null) SqlPersistence.saveTutorSubject(tutor.getUserId(), s.getSubjectId());
        HttpUtil.redirect(exchange, "/tutor/qualifications");
    }

    private void materialsPage(HttpExchange exchange, Tutor tutor) throws IOException {
        materialsPage(exchange, tutor, null);
    }

    private void materialsPage(HttpExchange exchange, Tutor tutor, String msg) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Study Materials</h1>");

        if (tutor.getSessionOptions().isEmpty()) {
            body.append("<div class='card'><p class='muted'>Add a session option first - study materials must be linked to one of your session offerings.</p>")
                .append("<a class='btn-link' href='/tutor/options'>Add a session option &rarr;</a></div>");
        } else {
            body.append("<div class='card'><h2>Upload Material</h2>")
                .append("<form method='POST' action='/tutor/materials' enctype='multipart/form-data'>")
                .append("<label>Title</label><input name='title' required>")
                .append("<label>Session</label><select name='optionId' required>");
            for (SessionOption o : tutor.getSessionOptions()) {
                body.append("<option value='").append(o.getOptionId()).append("'>")
                    .append(Layout.escape(o.getTitle())).append(" &middot; ")
                    .append(Layout.escape(o.getSubject().getSubjectName())).append("</option>");
            }
            body.append("</select>")
                .append("<label>File</label><input type='file' name='file' required>")
                .append("<button type='submit'>Upload</button></form></div>");
        }

        body.append("<div class='card'><table><tr><th>Title</th><th>Session</th><th>Uploaded</th></tr>");
        for (StudyMaterial m : tutor.getStudyMaterials()) {
            body.append("<tr><td>").append(Layout.escape(m.getTitle())).append("</td><td>")
                .append(m.getOption() == null ? "-" : Layout.escape(m.getOption().getTitle())).append("</td><td>")
                .append(m.getUploadDate()).append("</td></tr>");
        }
        if (tutor.getStudyMaterials().isEmpty()) {
            body.append("<tr><td colspan='3' class='muted'>No materials uploaded yet.</td></tr>");
        }
        body.append("</table></div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Materials", body.toString(), tutor, msg));
    }

    private void uploadMaterial(HttpExchange exchange, Tutor tutor) throws IOException {
        HttpUtil.MultipartForm form = HttpUtil.parseMultipart(exchange);
        String title = form.get("title");
        String optionIdStr = form.get("optionId");
        HttpUtil.UploadedFile file = form.files.get("file");
        SessionOption option = optionIdStr == null ? null : tutor.findSessionOption(Integer.parseInt(optionIdStr));
        if (title == null || title.isBlank() || option == null || file == null || file.isEmpty()) {
            materialsPage(exchange, tutor, "Please provide a title, choose one of your sessions, and select a file to upload.");
            return;
        }
        String fileURL = FileStorage.save(file, "materials");
        StudyMaterial m = tutor.uploadMaterial(title, fileURL, option);
        SqlPersistence.saveStudyMaterial(tutor.getUserId(), m);
        HttpUtil.redirect(exchange, "/tutor/materials");
    }

    // Tutors define their own priced session offerings here (e.g. "1-Hour Algebra - Rs. 2500"). */
    private void optionsPage(HttpExchange exchange, Tutor tutor, String msg) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Session Options &amp; Pricing</h1>");
        body.append("<p class='muted'>Add as many priced session offerings as you like. Students choose one of these when they book you.</p>");

        body.append("<div class='card'><h2>Add a Session Option</h2>")
            .append("<form method='POST' action='/tutor/options' enctype='multipart/form-data'>")
            .append("<label>Title</label><input name='title' placeholder='e.g. 1-Hour Algebra Tutoring' required>")
            .append("<label>Subject</label><select name='subjectId'>");
        for (Subject s : tutor.getSubjects()) {
            body.append("<option value='").append(s.getSubjectId()).append("'>").append(Layout.escape(s.getSubjectName())).append("</option>");
        }
        if (tutor.getSubjects().isEmpty()) {
            body.append("<option value='' disabled selected>Add a subject first under Manage qualifications &amp; subjects</option>");
        }
        body.append("</select>")
            .append("<label>Duration (minutes)</label><input type='number' name='durationMinutes' min='15' step='15' value='60' required>")
            .append("<label>Price (Rs.)</label><input type='number' name='price' min='0' step='0.01' required>")
            .append("<label>Max students</label><input type='number' name='maxStudents' min='1' step='1' value='1' required>")
            .append("<label>Study material (optional)</label><input type='file' name='material'>")
            .append("<label>Material title</label><input name='materialTitle' placeholder='Only needed if you attached a file'>")
            .append("<button type='submit'>Add Option</button></form></div>");

        body.append("<div class='card'><table><tr><th>Title</th><th>Subject</th><th>Duration</th><th>Price</th><th>Capacity</th><th>Booked Slots</th><th>Materials</th><th></th></tr>");
        for (SessionOption o : tutor.getSessionOptions()) {
            body.append("<tr><td>").append(Layout.escape(o.getTitle())).append("</td><td>")
                .append(Layout.escape(o.getSubject().getSubjectName())).append("</td><td>")
                .append(o.getDurationMinutes()).append(" min</td><td>Rs. ").append(String.format("%.2f", o.getPrice()))
                .append("</td><td>").append(o.getMaxStudents()).append(" per session</td><td>")
                .append(o.existingSlots().size()).append(" slot(s) requested so far</td><td>")
                .append(tutor.materialsFor(o).size()).append(" material(s)</td><td>")
                .append("<form method='POST' action='/tutor/options/remove'>")
                .append("<input type='hidden' name='optionId' value='").append(o.getOptionId()).append("'>")
                .append("<button type='submit' class='btn-secondary'>Remove</button></form></td></tr>");
        }
        if (tutor.getSessionOptions().isEmpty()) {
            body.append("<tr><td colspan='8' class='muted'>No session options yet.</td></tr>");
        }
        body.append("</table></div>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("Session Options", body.toString(), tutor, msg));
    }

    private void addSessionOption(HttpExchange exchange, Tutor tutor) throws IOException {
        HttpUtil.MultipartForm form = HttpUtil.parseMultipart(exchange);
        Subject subject = DataStore.get().findSubject(Integer.parseInt(form.get("subjectId")));
        if (subject == null || !tutor.getSubjects().contains(subject)) {
            optionsPage(exchange, tutor, "Please choose a subject you're registered to teach.");
            return;
        }
        String title = form.get("title");
        int duration = Integer.parseInt(form.get("durationMinutes"));
        double price = Double.parseDouble(form.get("price"));
        int maxStudents = Integer.parseInt(form.get("maxStudents"));

        try {
            SessionOption option = tutor.addSessionOption(subject, title, duration, price, maxStudents);
            SqlPersistence.saveSessionOption(option);

            HttpUtil.UploadedFile material = form.files.get("material");
            if (material != null && !material.isEmpty()) {
                String materialTitle = form.get("materialTitle");
                if (materialTitle == null || materialTitle.isBlank()) materialTitle = title;
                String fileURL = FileStorage.save(material, "materials");
                StudyMaterial m = tutor.uploadMaterial(materialTitle, fileURL, option);
                SqlPersistence.saveStudyMaterial(tutor.getUserId(), m);
            }
        } catch (IllegalArgumentException e) {
            optionsPage(exchange, tutor, e.getMessage());
            return;
        }
        HttpUtil.redirect(exchange, "/tutor/options");
    }

    private void removeSessionOption(HttpExchange exchange, Tutor tutor) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        int optionId = Integer.parseInt(form.get("optionId"));
        tutor.removeSessionOption(optionId);
        SqlPersistence.deleteSessionOption(optionId);
        HttpUtil.redirect(exchange, "/tutor/options");
    }

    private void requestsPage(HttpExchange exchange, Tutor tutor) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Session Requests</h1><div class='card'><table><tr><th></th><th>Student</th><th>Option</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th><th></th></tr>");
        for (SessionRequest r : tutor.allRequests()) {
            body.append("<tr><td>").append(Layout.avatarHtml(r.getStudent(), "avatar")).append("</td><td>")
                .append(Layout.escape(r.getStudent().getName())).append("</td><td>")
                .append(Layout.escape(r.getOption().getTitle())).append("</td><td>")
                .append(Layout.escape(r.getSubject().getSubjectName())).append("</td><td>")
                .append(r.getRequestDate()).append("</td><td>").append(r.getRequestTime()).append("</td><td>")
                .append("Rs. ").append(String.format("%.2f", r.getPrice())).append("</td><td>")
                .append(badge(r.getStatus().name())).append("</td><td>");
            if (r.getStatus() == RequestStatus.PENDING) {
                body.append("<form method='POST' action='/tutor/requests/accept' style='display:inline-flex;flex-direction:row;gap:6px;max-width:none'>")
                    .append("<input type='hidden' name='requestId' value='").append(r.getRequestId()).append("'>")
                    .append("<button type='submit'>Accept</button></form> ")
                    .append("<form method='POST' action='/tutor/requests/reject' style='display:inline-flex;flex-direction:row;gap:6px;max-width:none'>")
                    .append("<input type='hidden' name='requestId' value='").append(r.getRequestId()).append("'>")
                    .append("<button type='submit' class='btn-secondary'>Reject</button></form>");
            }
            body.append("</td></tr>");
        }
        body.append("</table></div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Requests", body.toString(), tutor, null));
    }

    private void acceptRequest(HttpExchange exchange, Tutor tutor) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        SessionRequest r = DataStore.get().findRequest(Integer.parseInt(form.get("requestId")));
        if (r != null && r.getTutor() == tutor && r.getStatus() == RequestStatus.PENDING) {
            tutor.acceptRequest(r); // creates the Session, which DataStore.addSession() already persists
            SqlPersistence.updateSessionRequestStatus(r);
        }
        HttpUtil.redirect(exchange, "/tutor/requests");
    }

    private void rejectRequest(HttpExchange exchange, Tutor tutor) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        SessionRequest r = DataStore.get().findRequest(Integer.parseInt(form.get("requestId")));
        if (r != null && r.getTutor() == tutor) {
            tutor.rejectRequest(r);
            SqlPersistence.updateSessionRequestStatus(r);
        }
        HttpUtil.redirect(exchange, "/tutor/requests");
    }

    private void sessionsPage(HttpExchange exchange, Tutor tutor) throws IOException {
        StringBuilder body = new StringBuilder("<h1>My Sessions</h1><div class='card'><table><tr><th>Student</th><th>Subject</th><th>Date</th><th>Time</th><th>Price</th><th>Status</th><th>Payment</th><th></th></tr>");
        for (Session s : tutor.allSessions()) {
            body.append("<tr><td>").append(Layout.escape(s.getStudent().getName())).append("</td><td>")
                .append(Layout.escape(s.getSubject().getSubjectName())).append("</td><td>")
                .append(s.getScheduledDate()).append("</td><td>").append(s.getStartTime()).append("-").append(s.getEndTime())
                .append("</td><td>Rs. ").append(String.format("%.2f", s.getPrice())).append("</td><td>")
                .append(badge(s.getStatus().name())).append("</td><td>")
                .append(s.getPayment() == null ? badge("PENDING") : badge(s.getPayment().getStatus().name()))
                .append("</td><td>");
            if (s.getStatus() == SessionStatus.ACTIVE) {
                body.append("<form method='POST' action='/tutor/sessions/complete'>")
                    .append("<input type='hidden' name='sessionId' value='").append(s.getSessionId()).append("'>")
                    .append("<button type='submit'>Mark Complete</button></form>");
            }
            body.append("</td></tr>");
        }
        body.append("</table></div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("My Sessions", body.toString(), tutor, null));
    }

    private void completeSession(HttpExchange exchange, Tutor tutor) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Session s = DataStore.get().findSession(Integer.parseInt(form.get("sessionId")));
        if (s != null) {
            tutor.completeSession(s);
            SqlPersistence.updateSessionStatus(s);
        }
        HttpUtil.redirect(exchange, "/tutor/sessions");
    }
}
