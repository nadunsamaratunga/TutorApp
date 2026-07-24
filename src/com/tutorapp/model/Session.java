package com.tutorapp.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Session {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int sessionId;
    private final Student student;
    private final Tutor tutor;
    private final Subject subject;
    private final SessionOption option;   // which priced offering this session was booked from
    private final double price;           // amount due, snapshotted from the request/option
    private Payment payment;
    private LocalDate scheduledDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String meetingLink;
    private SessionStatus status;

    public Session(Student student, Tutor tutor, Subject subject, SessionOption option, double price,
                   LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) {
        this.sessionId = COUNTER.getAndIncrement();
        this.student = student;
        this.tutor = tutor;
        this.subject = subject;
        this.option = option;
        this.price = price;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.meetingLink = "https://meet.tutorapp.local/" + this.sessionId;
        this.status = SessionStatus.PENDING;
    }

    private Session(int sessionId, Student student, Tutor tutor, Subject subject, SessionOption option,
                     double price, Payment payment, LocalDate scheduledDate, LocalTime startTime,
                     LocalTime endTime, String meetingLink, SessionStatus status) {
        this.sessionId = sessionId;
        this.student = student;
        this.tutor = tutor;
        this.subject = subject;
        this.option = option;
        this.price = price;
        this.payment = payment;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.meetingLink = meetingLink;
        this.status = status;
    }

    // Rebuilds a Session that already exists in the database, preserving its original id, payment link and status
    public static Session restore(int sessionId, Student student, Tutor tutor, Subject subject, SessionOption option,
                                   double price, Payment payment, LocalDate scheduledDate, LocalTime startTime,
                                   LocalTime endTime, String meetingLink, SessionStatus status) {
        return new Session(sessionId, student, tutor, subject, option, price, payment,
                scheduledDate, startTime, endTime, meetingLink, status);
    }

    // Ensures the next auto-assigned id won't collide with one already loaded from the database.
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    public void completeSession() {
        this.status = SessionStatus.COMPLETED;
    }

    // Checks whether a tutor is free in the given window (no overlapping ACTIVE/PENDING sessions).
    public static boolean checkAvailability(Tutor tutor, LocalDate date, LocalTime start, LocalTime end,
                                             List<Session> existingSessions) {
        for (Session s : existingSessions) {
            if (s.getTutor() == tutor
                    && s.getScheduledDate().equals(date)
                    && s.getStatus() != SessionStatus.CANCELLED
                    && start.isBefore(s.getEndTime())
                    && end.isAfter(s.getStartTime())) {
                return false;
            }
        }
        return true;
    }

    public int getSessionId() { return sessionId; }
    public Student getStudent() { return student; }
    public Tutor getTutor() { return tutor; }
    public Subject getSubject() { return subject; }
    public SessionOption getOption() { return option; }
    public double getPrice() { return price; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getMeetingLink() { return meetingLink; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
}
