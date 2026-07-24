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
import java.util.Map;

import static com.tutorapp.handler.StudentHandler.badge;

public class AdminHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        User current = SessionManager.getCurrentUser(exchange);
        if (!(current instanceof Admin)) {
            HttpUtil.redirect(exchange, "/login");
            return;
        }
        Admin admin = (Admin) current;
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/admin/dashboard")) dashboard(exchange, admin);
        else if (path.equals("/admin/tutors")) tutorsPage(exchange, admin);
        else if (path.equals("/admin/tutors/qualifications") && method.equals("GET")) qualificationsPage(exchange, admin);
        else if (path.equals("/admin/tutors/qualifications/verify") && method.equals("POST")) reviewQualification(exchange, admin, QualificationStatus.VERIFIED);
        else if (path.equals("/admin/tutors/qualifications/reject") && method.equals("POST")) reviewQualification(exchange, admin, QualificationStatus.REJECTED);
        else if (path.equals("/admin/payments") && method.equals("GET")) paymentsPage(exchange, admin);
        else if (path.equals("/admin/payments/verify") && method.equals("POST")) reviewPayment(exchange, admin, true);
        else if (path.equals("/admin/payments/reject") && method.equals("POST")) reviewPayment(exchange, admin, false);
        else if (path.equals("/admin/subjects") && method.equals("GET")) subjectsPage(exchange, admin, null);
        else if (path.equals("/admin/subjects/add") && method.equals("POST")) addSubject(exchange, admin);
        else if (path.equals("/admin/subjects/remove") && method.equals("POST")) removeSubject(exchange, admin);
        else if (path.equals("/admin/reports")) reportsPage(exchange, admin);
        else HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", current, null));
    }

    private void dashboard(HttpExchange exchange, Admin admin) throws IOException {
        String body = "<h1>Admin Dashboard</h1><div class='grid'>"
            + "<div class='card'><h3>" + admin.allTutors().size() + "</h3><p class='muted'>Total tutors</p></div>"
            + "<div class='card'><h3>" + admin.unverifiedTutorCount() + "</h3><p class='muted'>Pending verification</p></div>"
            + "<div class='card'><h3>" + admin.allSessions().size() + "</h3><p class='muted'>Total sessions</p></div>"
            + "<div class='card'><h3>" + admin.pendingBankPayments().size() + "</h3><p class='muted'>Bank payments awaiting review</p></div>"
            + "<div class='card'><h3>Rs. " + String.format("%.2f", admin.totalRevenue()) + "</h3><p class='muted'>Revenue collected</p></div>"
            + "</div><div class='card'>"
            + "<a class='btn-link' href='/admin/tutors'>Manage &amp; verify tutors &rarr;</a><br>"
            + "<a class='btn-link' href='/admin/payments'>Review bank payment proofs &rarr;</a><br>"
            + "<a class='btn-link' href='/admin/subjects'>Manage subjects &rarr;</a><br>"
            + "<a class='btn-link' href='/admin/reports'>View full report &rarr;</a></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Admin Dashboard", body, admin, null));
    }

    private void tutorsPage(HttpExchange exchange, Admin admin) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Tutors</h1><p class='muted'>A tutor becomes verified automatically once you approve at least one of their uploaded qualifications.</p>")
            .append("<table><tr><th>Name</th><th>Email</th><th>Qualifications</th><th>Status</th></tr>");
        for (Tutor t : admin.allTutors()) {
            body.append("<tr><td>").append(Layout.escape(t.getName())).append(t.isVerified() ? " &#9989;" : "").append("</td><td>")
                .append(Layout.escape(t.getEmail())).append("</td><td>")
                .append("<a href='/admin/tutors/qualifications?tutorId=").append(t.getUserId()).append("'>")
                .append(t.getQualifications().size()).append(" &rarr;</a>")
                .append("</td><td>").append(t.isVerified() ? badge("VERIFIED") : badge("PENDING")).append("</td></tr>");
        }
        body.append("</table>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Tutors", body.toString(), admin, null));
    }

    private void qualificationsPage(HttpExchange exchange, Admin admin) throws IOException {
        Map<String, String> q = HttpUtil.queryParams(exchange);
        Tutor t = q.get("tutorId") == null ? null : (Tutor) DataStore.get().findById(q.get("tutorId"));
        if (t == null) {
            HttpUtil.redirect(exchange, "/admin/tutors");
            return;
        }

        StringBuilder body = new StringBuilder("<h1>Qualifications &mdash; ").append(Layout.escape(t.getName())).append("</h1>")
            .append("<p class='muted'>").append(Layout.escape(t.getEmail())).append(" &middot; ")
            .append(t.isVerified() ? badge("VERIFIED") : badge("PENDING")).append("</p>")
            .append("<p class='muted'>Approving any one qualification below verifies this tutor.</p>");

        if (t.getQualifications().isEmpty()) {
            body.append("<div class='card'><p class='muted'>This tutor hasn't uploaded any qualifications yet.</p></div>");
        } else {
            body.append("<table><tr><th>Title</th><th>Document</th><th>Status</th><th></th></tr>");
            for (Qualification qual : t.getQualifications()) {
                body.append("<tr><td>").append(Layout.escape(qual.getTitle())).append("</td><td>")
                    .append("<a href='").append(Layout.escape(qual.getDocumentURL())).append("' target='_blank'>view document</a></td><td>")
                    .append(badge(qual.getStatus().name())).append("</td><td>");
                if (qual.getStatus() != QualificationStatus.VERIFIED) {
                    body.append("<form style='display:inline' method='POST' action='/admin/tutors/qualifications/verify'>")
                        .append("<input type='hidden' name='tutorId' value='").append(t.getUserId()).append("'>")
                        .append("<input type='hidden' name='qualificationId' value='").append(qual.getQualificationId()).append("'>")
                        .append("<button type='submit'>Verify</button></form> ");
                }
                if (qual.getStatus() != QualificationStatus.REJECTED) {
                    body.append("<form style='display:inline' method='POST' action='/admin/tutors/qualifications/reject'>")
                        .append("<input type='hidden' name='tutorId' value='").append(t.getUserId()).append("'>")
                        .append("<input type='hidden' name='qualificationId' value='").append(qual.getQualificationId()).append("'>")
                        .append("<button type='submit' class='btn-secondary'>Reject</button></form>");
                }
                body.append("</td></tr>");
            }
            body.append("</table>");
        }

        body.append("<a class='btn-link' href='/admin/tutors'>&larr; Back to tutors</a>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("Qualifications", body.toString(), admin, null));
    }

    private void reviewQualification(HttpExchange exchange, Admin admin, QualificationStatus status) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Tutor t = (Tutor) DataStore.get().findById(form.get("tutorId"));
        Qualification qual = t == null ? null : t.getQualifications().stream()
                .filter(q -> q.getQualificationId().equals(form.get("qualificationId")))
                .findFirst().orElse(null);
        if (t != null && qual != null) {
            admin.reviewQualification(t, qual, status);
            SqlPersistence.updateQualificationStatus(qual);
            SqlPersistence.updateTutorVerified(t);
        }
        HttpUtil.redirect(exchange, "/admin/tutors/qualifications?tutorId=" + (t == null ? "" : t.getUserId()));
    }

    /** Lists every session whose bank transfer proof is still awaiting admin review. */
    private void paymentsPage(HttpExchange exchange, Admin admin) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Bank Payment Proofs</h1>")
            .append("<p class='muted'>Review the submitted proof, then verify to activate the session or reject to let the student resubmit.</p>");

        var pending = admin.pendingBankPayments();
        if (pending.isEmpty()) {
            body.append("<div class='card'><p class='muted'>No bank payments are awaiting review right now.</p></div>");
        } else {
            body.append("<table><tr><th>Student</th><th>Tutor</th><th>Subject</th><th>Date</th><th>Amount</th><th>Account #</th><th>Proof</th><th></th></tr>");
            for (Session s : pending) {
                BankPayment payment = (BankPayment) s.getPayment();
                body.append("<tr><td>").append(Layout.escape(s.getStudent().getName())).append("</td><td>")
                    .append(Layout.escape(s.getTutor().getName())).append("</td><td>")
                    .append(Layout.escape(s.getSubject().getSubjectName())).append("</td><td>")
                    .append(s.getScheduledDate()).append(" ").append(s.getStartTime()).append("</td><td>Rs. ")
                    .append(String.format("%.2f", s.getPrice())).append("</td><td>")
                    .append(Layout.escape(payment.getAccountNumber())).append("</td><td>")
                    .append("<a href='").append(Layout.escape(payment.getReceiptImage())).append("' target='_blank'>View proof</a></td><td>")
                    .append("<form style='display:inline' method='POST' action='/admin/payments/verify'>")
                    .append("<input type='hidden' name='sessionId' value='").append(s.getSessionId()).append("'>")
                    .append("<button type='submit'>Verify</button></form> ")
                    .append("<form style='display:inline' method='POST' action='/admin/payments/reject'>")
                    .append("<input type='hidden' name='sessionId' value='").append(s.getSessionId()).append("'>")
                    .append("<button type='submit' class='btn-secondary'>Reject</button></form>")
                    .append("</td></tr>");
            }
            body.append("</table>");
        }
        HttpUtil.sendHtml(exchange, 200, Layout.page("Payment Proofs", body.toString(), admin, null));
    }

    private void reviewPayment(HttpExchange exchange, Admin admin, boolean approve) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Session session = DataStore.get().findSession(Integer.parseInt(form.get("sessionId")));
        if (session != null) {
            Payment payment = session.getPayment();
            if (approve) {
                admin.verifyPayment(session);
                if (payment != null) SqlPersistence.updatePaymentStatus(payment);
                SqlPersistence.updateSessionStatus(session);
            } else {
                if (payment != null) {
                    admin.rejectPayment(session);
                    SqlPersistence.updatePaymentStatus(payment);
                    SqlPersistence.clearSessionPayment(session);
                }
            }
        }
        HttpUtil.redirect(exchange, "/admin/payments");
    }

    private void subjectsPage(HttpExchange exchange, Admin admin, String msg) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Subjects</h1>");
        body.append("<div class='card'><h2>Add Subject</h2>")
            .append("<form method='POST' action='/admin/subjects/add'>")
            .append("<label>Name</label><input name='subjectName' required>")
            .append("<label>Description</label><input name='description' required>")
            .append("<button type='submit'>Add</button></form></div>");

        body.append("<table><tr><th>Name</th><th>Description</th><th></th></tr>");
        for (Subject s : admin.allSubjects()) {
            body.append("<tr><td>").append(Layout.escape(s.getSubjectName())).append("</td><td>")
                .append(Layout.escape(s.getDescription())).append("</td><td>")
                .append("<form method='POST' action='/admin/subjects/remove'>")
                .append("<input type='hidden' name='subjectId' value='").append(s.getSubjectId()).append("'>")
                .append("<button type='submit' class='btn-secondary'>Remove</button></form></td></tr>");
        }
        body.append("</table>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Subjects", body.toString(), admin, msg));
    }

    private void addSubject(HttpExchange exchange, Admin admin) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        admin.addSubject(form.get("subjectName"), form.get("description"));
        HttpUtil.redirect(exchange, "/admin/subjects");
    }

    private void removeSubject(HttpExchange exchange, Admin admin) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        admin.removeSubject(Integer.parseInt(form.get("subjectId")));
        HttpUtil.redirect(exchange, "/admin/subjects");
    }

    private void reportsPage(HttpExchange exchange, Admin admin) throws IOException {
        StringBuilder body = new StringBuilder("<h1>Reports</h1>");
        body.append("<div class='card'><h2>All Sessions</h2><table><tr><th>#</th><th>Student</th><th>Tutor</th><th>Subject</th><th>Status</th><th>Payment</th></tr>");
        for (Session s : admin.allSessions()) {
            body.append("<tr><td>").append(s.getSessionId()).append("</td><td>")
                .append(Layout.escape(s.getStudent().getName())).append("</td><td>")
                .append(Layout.escape(s.getTutor().getName())).append("</td><td>")
                .append(Layout.escape(s.getSubject().getSubjectName())).append("</td><td>")
                .append(badge(s.getStatus().name())).append("</td><td>")
                .append(s.getPayment() == null ? badge("PENDING") : badge(s.getPayment().getStatus().name()))
                .append("</td></tr>");
        }
        body.append("</table></div>");
        HttpUtil.sendHtml(exchange, 200, Layout.page("Reports", body.toString(), admin, null));
    }
}
