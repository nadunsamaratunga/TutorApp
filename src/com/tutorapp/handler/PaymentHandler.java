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


public class PaymentHandler implements HttpHandler {

    // Hardcoded bank details shown to students paying by bank transfer.
    private static final String BANK_NAME = "Commercial Bank of Ceylon PLC";
    private static final String BANK_ACCOUNT_NAME = "TutorApp (Pvt) Ltd";
    private static final String BANK_ACCOUNT_NUMBER = "8001 2345 6789";
    private static final String BANK_BRANCH = "Colombo 03";

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

        if (path.equals("/payment/pay") && method.equals("GET")) showMethodChoice(exchange, student);
        else if (path.equals("/payment/card") && method.equals("GET")) showCardForm(exchange, student, null);
        else if (path.equals("/payment/card") && method.equals("POST")) processCard(exchange, student);
        else if (path.equals("/payment/bank") && method.equals("GET")) showBankForm(exchange, student, null);
        else if (path.equals("/payment/bank") && method.equals("POST")) processBank(exchange, student);
        else if (path.equals("/payment/success")) showSuccess(exchange, student);
        else HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", current, null));
    }

    private Session requireOwnedPendingSession(HttpExchange exchange, Student student) {
        Map<String, String> q = HttpUtil.queryParams(exchange);
        String idStr = q.get("sessionId");
        if (idStr == null) return null;
        Session s = DataStore.get().findSession(Integer.parseInt(idStr));
        if (s == null || s.getStudent() != student) return null;
        return s;
    }

    // True if this session already has a payment attached that's either awaiting review or already succeeded. 
    private boolean alreadyHasActivePayment(Session session) {
        Payment p = session.getPayment();
        return p != null && p.getStatus() != PaymentStatus.FAILED;
    }

    private void showMethodChoice(HttpExchange exchange, Student student) throws IOException {
        Session session = requireOwnedPendingSession(exchange, student);
        if (session == null) { HttpUtil.redirect(exchange, "/student/sessions"); return; }
        if (alreadyHasActivePayment(session)) {
            HttpUtil.redirect(exchange, "/student/sessions?msg=" + HttpUtil.encode("That session already has a payment on file."));
            return;
        }

        String body = "<h1>Pay for your session</h1>"
            + "<div class='card'>"
            + "<p><b>Tutor:</b> " + Layout.escape(session.getTutor().getName()) + "<br>"
            + "<b>Subject:</b> " + Layout.escape(session.getSubject().getSubjectName()) + "<br>"
            + "<b>Date:</b> " + session.getScheduledDate() + " (" + session.getStartTime() + "-" + session.getEndTime() + ")<br>"
            + "<b>Amount due:</b> Rs. " + String.format("%.2f", session.getPrice()) + "</p>"
            + "<div style='display:flex;gap:14px'>"
            + "<a href='/payment/card?sessionId=" + session.getSessionId() + "'><button type='button'>Pay with Card</button></a>"
            + "<a href='/payment/bank?sessionId=" + session.getSessionId() + "'><button type='button' class='btn-secondary'>Pay via Bank Transfer</button></a>"
            + "</div></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Pay", body, student, null));
    }

    private void showCardForm(HttpExchange exchange, Student student, String error) throws IOException {
        Session session = requireOwnedPendingSession(exchange, student);
        if (session == null) { HttpUtil.redirect(exchange, "/student/sessions"); return; }
        if (alreadyHasActivePayment(session)) {
            HttpUtil.redirect(exchange, "/student/sessions?msg=" + HttpUtil.encode("That session already has a payment on file."));
            return;
        }

        String body = "<h1>Card Payment</h1><div class='card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<p class='muted'>Enter your card details below. This is a demo checkout - no real charge is made.</p>"
            + "<form method='POST' action='/payment/card'>"
            + "<input type='hidden' name='sessionId' value='" + session.getSessionId() + "'>"
            + "<label>Card Number</label><input name='cardNumber' maxlength='19' placeholder='4242 4242 4242 4242' required>"
            + "<label>Cardholder Name</label><input name='holderName' required>"
            + "<label>Expiry Date</label><input name='expiryDate' placeholder='MM/YY' required>"
            + "<button type='submit'>Pay Rs. " + String.format("%.2f", session.getPrice()) + "</button>"
            + "</form></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Card Payment", body, student, null));
    }

    private void processCard(HttpExchange exchange, Student student) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(exchange);
        Session session = DataStore.get().findSession(Integer.parseInt(form.get("sessionId")));
        if (session == null || session.getStudent() != student || alreadyHasActivePayment(session)) {
            HttpUtil.redirect(exchange, "/student/sessions"); return;
        }

        student.payWithCard(session, form.get("cardNumber"), form.get("holderName"), form.get("expiryDate"));
        SqlPersistence.updateSessionPayment(session);
        HttpUtil.redirect(exchange, "/payment/success?sessionId=" + session.getSessionId());
    }

    private void showBankForm(HttpExchange exchange, Student student, String error) throws IOException {
        Session session = requireOwnedPendingSession(exchange, student);
        if (session == null) { HttpUtil.redirect(exchange, "/student/sessions"); return; }
        if (alreadyHasActivePayment(session)) {
            HttpUtil.redirect(exchange, "/student/sessions?msg=" + HttpUtil.encode("That session already has a payment on file."));
            return;
        }

        String body = "<h1>Bank Transfer</h1><div class='card'>"
            + (error != null ? "<p style='color:#c62828'>" + Layout.escape(error) + "</p>" : "")
            + "<div class='card' style='background:#f4f7fb;box-shadow:none'>"
            + "<p class='muted' style='margin-bottom:6px'>Transfer Rs. " + String.format("%.2f", session.getPrice()) + " to:</p>"
            + "<p style='margin:2px 0'><b>Bank:</b> " + Layout.escape(BANK_NAME) + "</p>"
            + "<p style='margin:2px 0'><b>Account Name:</b> " + Layout.escape(BANK_ACCOUNT_NAME) + "</p>"
            + "<p style='margin:2px 0'><b>Account Number:</b> " + Layout.escape(BANK_ACCOUNT_NUMBER) + "</p>"
            + "<p style='margin:2px 0'><b>Branch:</b> " + Layout.escape(BANK_BRANCH) + "</p>"
            + "</div>"
            + "<p class='muted' style='margin-top:16px'>Once you've made the transfer, submit your bank account and proof of "
            + "payment below. An admin will review it and confirm your session shortly after.</p>"
            + "<form method='POST' action='/payment/bank' enctype='multipart/form-data'>"
            + "<input type='hidden' name='sessionId' value='" + session.getSessionId() + "'>"
            + "<label>Your Bank Account Number</label><input name='accountNumber' required>"
            + "<label>Proof of Payment (upload a photo or PDF of your receipt)</label><input type='file' name='receipt' accept='.pdf,.png,.jpg,.jpeg' required>"
            + "<button type='submit'>Submit Payment Proof</button>"
            + "</form></div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Bank Transfer", body, student, null));
    }

    private void processBank(HttpExchange exchange, Student student) throws IOException {
        HttpUtil.MultipartForm form = HttpUtil.parseMultipart(exchange);
        Session session = DataStore.get().findSession(Integer.parseInt(form.get("sessionId")));
        if (session == null || session.getStudent() != student || alreadyHasActivePayment(session)) {
            HttpUtil.redirect(exchange, "/student/sessions"); return;
        }

        HttpUtil.UploadedFile receipt = form.files.get("receipt");
        if (receipt == null || receipt.isEmpty()) {
            showBankForm(exchange, student, "Please attach proof of payment.");
            return;
        }
        String receiptURL = FileStorage.save(receipt, "receipts");

        student.payWithBank(session, form.get("accountNumber"), receiptURL);
        SqlPersistence.updateSessionPayment(session);
        HttpUtil.redirect(exchange, "/student/sessions?msg=" + HttpUtil.encode(
                "Payment proof submitted! An admin will review it and confirm your session shortly."));
    }

    private void showSuccess(HttpExchange exchange, Student student) throws IOException {
        Session session = requireOwnedPendingSession(exchange, student);
        if (session == null) { HttpUtil.redirect(exchange, "/student/sessions"); return; }
        Payment payment = session.getPayment();

        String body = "<div class='card' style='text-align:center;padding:50px 30px'>"
            + "<h1 style='color:#1f8a4c'>Payment Successful</h1>"
            + "<p>Your session with <b>" + Layout.escape(session.getTutor().getName()) + "</b> is now confirmed.</p>"
            + "<p class='muted'>Method: " + (payment != null ? payment.getMethodName() : "-") + " &middot; Amount: Rs. "
            + (payment != null ? String.format("%.2f", payment.getAmount()) : "0.00") + " &middot; Reference #" + (payment != null ? payment.getPaymentId() : "-") + "</p>"
            + "<a class='btn-link' href='/student/sessions'>Go to My Sessions &rarr;</a>"
            + "</div>";
        HttpUtil.sendHtml(exchange, 200, Layout.page("Payment Success", body, student, null));
    }
}
