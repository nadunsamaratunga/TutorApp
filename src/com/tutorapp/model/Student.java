package com.tutorapp.model;

import com.tutorapp.store.DataStore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Student extends User {
    private final List<Session> sessions = new ArrayList<>();

    public Student(String name, String email, String password, String phone) {
        super(name, email, password, phone);
    }

    private Student(String userId, String name, String email, String password, String phone) {
        super(userId, name, email, password, phone);
    }

    //Rebuilds a Student that already exists in the database, preserving its original id. 
    
    public static Student restore(String userId, String name, String email, String password, String phone) {
        return new Student(userId, name, email, password, phone);
    }

    @Override
    public String getRole() { return "STUDENT"; }

    // Validates and creates a new Student account, then registers it in the data store. Returns null if the email is already taken.
     
    public static Student register(String name, String email, String password, String phone) {
        if (DataStore.get().emailExists(email)) return null;
        Student student = new Student(name, email, password, phone);
        DataStore.get().addUser(student);
        return student;
    }

    // All tutors, optionally filtered to ones offering the given subject and/or matching the given name (case-insensitive substring match). Verification is shown as a trust badge, not a listing filter.

    public List<Tutor> searchTutor(Subject subjectFilter, String nameFilter) {
        List<Tutor> tutors = DataStore.get().allTutors();
        List<Tutor> matching = new ArrayList<>();
        String needle = (nameFilter == null) ? "" : nameFilter.trim().toLowerCase();
        for (Tutor t : tutors) {
            if (t.getSessionOptions().isEmpty()) continue; // hide tutors with no bookable sessions
            boolean offersSubject = subjectFilter == null || t.getSessionOptions().stream()
                    .anyMatch(o -> o.getSubject().getSubjectId() == subjectFilter.getSubjectId());
            boolean matchesName = needle.isEmpty() || t.getName().toLowerCase().contains(needle);
            if (offersSubject && matchesName) matching.add(t);
        }
        return matching;
    }

    //Requests to book one of a tutor's priced SessionOptions at a specific date/time.
    // Returns null if this student has already booked this exact session option, or if that particular date+time slot has reached its max student capacity - a different date/time for the same option may still have room.
     
    public SessionRequest hireTutor(Tutor tutor, SessionOption option, LocalDate date, LocalTime startTime) {
        if (hasRequested(option) || option.isFull(date, startTime)) return null;
        SessionRequest request = new SessionRequest(this, tutor, option, date, startTime);
        DataStore.get().addSessionRequest(request);
        return request;
    }

    // True if this student already has a pending/accepted request for this exact session option. 

    public boolean hasRequested(SessionOption option) {
        return DataStore.get().allSessionRequests().stream()
                .anyMatch(r -> r.getStudent() == this && r.getOption() == option && r.getStatus() != RequestStatus.REJECTED);
    }

    // Pays for a session with a card, activating it once payment succeeds. 

    public CardPayment payWithCard(Session session, String cardNumber, String holderName, String expiryDate) {
        requireOwnSession(session);
        CardPayment payment = new CardPayment(session.getPrice(), cardNumber, holderName, expiryDate);
        makePayment(session, payment);
        return payment;
    }

    // Submits proof of a bank transfer for a session. Unlike card payments, this does NOT immediately activate the session - the payment stays PENDING until an admin reviews the submitted proof and verifies it (see Admin.verifyPayment). The session itself also stays PENDING until that happens.
     
    public BankPayment payWithBank(Session session, String accountNumber, String proofOfPayment) {
        requireOwnSession(session);
        BankPayment payment = new BankPayment(session.getPrice(), accountNumber, proofOfPayment);
        session.setPayment(payment);
        return payment;
    }

    private void makePayment(Session session, Payment payment) {
        payment.processPayment();
        session.setPayment(payment);
        session.getTutor().acceptSession(session); // PENDING -> ACTIVE
    }

    private void requireOwnSession(Session session) {
        if (session.getStudent() != this) {
            throw new IllegalStateException("That session does not belong to this student.");
        }
    }

    // Sessions the student has paid for and that the tutor has activated - study materials for a
    // session only become visible once it reaches this state.
    public List<Session> sessionsWithAccessibleMaterials() {
        return sessions.stream().filter(s -> s.getStatus() == SessionStatus.ACTIVE).toList();
    }

    // Distinct tutors this student has booked at least one session with (used to group materials in the UI). 

    public List<Tutor> tutorsBooked() {
        return new ArrayList<>(new LinkedHashSet<>(sessions.stream().map(Session::getTutor).toList()));
    }

    public List<SessionRequest> myRequests() {
        return DataStore.get().allSessionRequests().stream()
                .filter(r -> r.getStudent() == this)
                .toList();
    }

    public List<Session> getSessions() { return sessions; }
}
