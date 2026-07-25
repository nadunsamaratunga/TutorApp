package com.tutorapp.model;

import com.tutorapp.store.DataStore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// A priced session offering that a Tutor defines for themselves, e.g."1-Hour Algebra Tutoring - Rs. 2500" or "2-Hour Exam Crash Course - Rs. 4500".Tutors can list as many of these as they like, each with its own subject,duration, and price. maxStudents is a per-timeslot capacity - it caps how many students can share one particular date+time booking of this option, not how many times the option can ever be booked in total. Students can always propose a brand new date/time for the same option once every existing slot is full.
public class SessionOption {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int optionId;
    private final Tutor tutor;
    private Subject subject;
    private String title;
    private int durationMinutes;
    private double price;
    private int maxStudents;

    public SessionOption(Tutor tutor, Subject subject, String title, int durationMinutes, double price, int maxStudents) {
        this.optionId = COUNTER.getAndIncrement();
        this.tutor = tutor;
        this.subject = subject;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.maxStudents = maxStudents;
    }

    private SessionOption(int optionId, Tutor tutor, Subject subject, String title, int durationMinutes,
                           double price, int maxStudents) {
        this.optionId = optionId;
        this.tutor = tutor;
        this.subject = subject;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.maxStudents = maxStudents;
    }

    // Rebuilds a SessionOption that already exists in the database, preserving its original id.
    public static SessionOption restore(int optionId, Tutor tutor, Subject subject, String title,
                                         int durationMinutes, double price, int maxStudents) {
        return new SessionOption(optionId, tutor, subject, title, durationMinutes, price, maxStudents);
    }

    // Ensures the next auto-assigned id won't collide with one already loaded from the database.
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    // How many students currently hold a (non-rejected) request for this exact date+time slot.
    public int getBookedCount(LocalDate date, LocalTime startTime) {
        return (int) DataStore.get().allSessionRequests().stream()
                .filter(r -> r.getOption() == this && r.getStatus() != RequestStatus.REJECTED)
                .filter(r -> r.getRequestDate().equals(date) && r.getStartTime().equals(startTime))
                .count();
    }

    // True once bookings for this exact date+time slot have reached max student capacity.
    public boolean isFull(LocalDate date, LocalTime startTime) {
        return getBookedCount(date, startTime) >= maxStudents;
    }

    // One representative (non-rejected) request per distinct date+time slot that has already been requested for this option, in the order each slot was first requested. Lets students see and join sessions other students already booked, instead of always having to propose a brand new time. Slots the tutor has already marked complete are excluded, since that class has already taken place.
    public List<SessionRequest> existingSlots() {
        Map<String, SessionRequest> distinctSlots = new LinkedHashMap<>();
        for (SessionRequest r : DataStore.get().allSessionRequests()) {
            if (r.getOption() != this || r.getStatus() == RequestStatus.REJECTED) continue;
            if (isSlotCompleted(r.getRequestDate(), r.getStartTime())) continue;
            String slotKey = r.getRequestDate() + "@" + r.getStartTime();
            distinctSlots.putIfAbsent(slotKey, r);
        }
        return new ArrayList<>(distinctSlots.values());
    }

    // True once the tutor has marked any session for this option at this exact date+time as complete.
    private boolean isSlotCompleted(LocalDate date, LocalTime startTime) {
        return DataStore.get().allSessions().stream()
                .anyMatch(s -> s.getOption() == this
                        && s.getScheduledDate().equals(date)
                        && s.getStartTime().equals(startTime)
                        && s.getStatus() == SessionStatus.COMPLETED);
    }

    public int getOptionId() { return optionId; }
    public Tutor getTutor() { return tutor; }
    public Subject getSubject() { return subject; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public double getPrice() { return price; }
    public int getMaxStudents() { return maxStudents; }

    public void setSubject(Subject subject) { this.subject = subject; }
    public void setTitle(String title) { this.title = title; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setPrice(double price) { this.price = price; }
    public void setMaxStudents(int maxStudents) { this.maxStudents = maxStudents; }
}
