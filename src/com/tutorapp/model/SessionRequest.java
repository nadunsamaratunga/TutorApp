package com.tutorapp.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionRequest {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int requestId;
    private final Student student;
    private final Tutor tutor;
    private final Subject subject;
    private final SessionOption option;   // the priced offering the student picked
    private final double price;           // snapshot of option.getPrice() at request time
    private final LocalDate requestDate;
    private final String requestTime;     // e.g. "14:00-15:00"
    private RequestStatus status;

    // Preferred constructor: student is booking one of the tutor's priced SessionOptions.
    public SessionRequest(Student student, Tutor tutor, SessionOption option,
                           LocalDate requestDate, LocalTime startTime) {
        this.requestId = COUNTER.getAndIncrement();
        this.student = student;
        this.tutor = tutor;
        this.option = option;
        this.subject = option.getSubject();
        this.price = option.getPrice();
        this.requestDate = requestDate;
        LocalTime endTime = startTime.plusMinutes(option.getDurationMinutes());
        this.requestTime = startTime + "-" + endTime;
        this.status = RequestStatus.PENDING;
    }

    private SessionRequest(int requestId, Student student, Tutor tutor, Subject subject, SessionOption option,
                            double price, LocalDate requestDate, String requestTime, RequestStatus status) {
        this.requestId = requestId;
        this.student = student;
        this.tutor = tutor;
        this.subject = subject;
        this.option = option;
        this.price = price;
        this.requestDate = requestDate;
        this.requestTime = requestTime;
        this.status = status;
    }

    // Rebuilds a SessionRequest that already exists in the database, preserving its original id and status. 
    public static SessionRequest restore(int requestId, Student student, Tutor tutor, Subject subject,
                                          SessionOption option, double price, LocalDate requestDate,
                                          String requestTime, RequestStatus status) {
        return new SessionRequest(requestId, student, tutor, subject, option, price, requestDate, requestTime, status);
    }

    // Ensures the next auto-assigned id won't collide with one already loaded from the database.
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    public void approve() { this.status = RequestStatus.ACCEPTED; }
    public void deny() { this.status = RequestStatus.REJECTED; }

    public int getRequestId() { return requestId; }
    public Student getStudent() { return student; }
    public Tutor getTutor() { return tutor; }
    public Subject getSubject() { return subject; }
    public SessionOption getOption() { return option; }
    public double getPrice() { return price; }
    public LocalDate getRequestDate() { return requestDate; }
    public String getRequestTime() { return requestTime; }
    public RequestStatus getStatus() { return status; }

    // The slot's start time, parsed from "HH:mm-HH:mm". Used to group requests into the same timeslot.
    public LocalTime getStartTime() { return LocalTime.parse(requestTime.split("-")[0]); }

    // The slot's end time, parsed from "HH:mm-HH:mm". 
    public LocalTime getEndTime() { return LocalTime.parse(requestTime.split("-")[1]); }
}
