package com.tutorapp.model;

import com.tutorapp.store.DataStore;

import java.util.List;

public class Admin extends User {

    public Admin(String name, String email, String password, String phone) {
        super(name, email, password, phone);
    }

    private Admin(String userId, String name, String email, String password, String phone) {
        super(userId, name, email, password, phone);
    }

    // Rebuilds an admin that already exists in the database, preserving his original id
    public static Admin restore(String userId, String name, String email, String password, String phone) {
        return new Admin(userId, name, email, password, phone);
    }

    @Override
    public String getRole() { return "ADMIN"; }

    /** Authenticates against the given credentials, but only succeeds if the account is actually an Admin. */
    public static Admin authenticateAdmin(String email, String password) {
        User user = User.authenticate(email, password);
        return (user instanceof Admin) ? (Admin) user : null;
    }
/
    // Reviews one of a tutor's uploaded qualifications. A tutor's overall verified status is derived from this: as soon as any qualification is VERIFIED, the tutor becomes verifed and stays that way even if that qualification is later re-reviewed, as long as atleast one other VERIFIED one remains
    public void reviewQualification(Tutor tutor, Qualification qualification, QualificationStatus status) {
        qualification.setStatus(status);
        boolean anyVerified = tutor.getQualifications().stream()
                .anyMatch(q -> q.getStatus() == QualificationStatus.VERIFIED);
        tutor.setVerified(anyVerified);
    }

    // Approves a student's submitted bank transfer proof for this session; marks the payment SUCCESS and activates the session, granting the student access to it.
    public void verifyPayment(Session session) {
        Payment payment = session.getPayment();
        if (payment == null) return;
        payment.processPayment();
        session.getTutor().acceptSession(session); // PENDING -> ACTIVE
    }

    // Rejects a student's submitted bank transfer proof- marks it FAILED and clears it from the session so the student can submit new proof or pay a different way instead
    public void rejectPayment(Session session) {
        Payment payment = session.getPayment();
        if (payment == null) return;
        payment.setStatus(PaymentStatus.FAILED);
        session.setPayment(null);
    }

    // All sessions with a bank transfer proof still awaiting admin review
    public List<Session> pendingBankPayments() {
        return allSessions().stream()
                .filter(s -> s.getPayment() instanceof BankPayment
                        && s.getPayment().getStatus() == PaymentStatus.PENDING)
                .toList();
    }

    public Subject addSubject(String name, String description) {
        Subject subject = new Subject(name, description);
        DataStore.get().addSubject(subject);
        return subject;
    }

    public void removeSubject(int subjectId) {
        DataStore.get().removeSubject(subjectId);
    }

    public List<Tutor> allTutors() { return DataStore.get().allTutors(); }
    public List<Subject> allSubjects() { return DataStore.get().allSubjects(); }
    public List<Session> allSessions() { return DataStore.get().allSessions(); }

    public long unverifiedTutorCount() {
        return allTutors().stream().filter(t -> !t.isVerified()).count();
    }

    public double totalRevenue() {
        return allSessions().stream()
                .filter(s -> s.getPayment() != null && s.getPayment().getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(s -> s.getPayment().getAmount())
                .sum();
    }
}
