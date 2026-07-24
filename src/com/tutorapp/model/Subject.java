package com.tutorapp.model;

import java.util.concurrent.atomic.AtomicInteger;

public class Subject {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int subjectId;
    private String subjectName;
    private String description;

    public Subject(String subjectName, String description) {
        this.subjectId = COUNTER.getAndIncrement();
        this.subjectName = subjectName;
        this.description = description;
    }

    private Subject(int subjectId, String subjectName, String description) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.description = description;
    }

    // Rebuilds a Subject that already exists in the database, preserving its original id. 

    public static Subject restore(int subjectId, String subjectName, String description) {
        return new Subject(subjectId, subjectName, description);
    }

    //Ensures the next auto-assigned id won't collide with one already loaded from the database. 
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    public int getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public String getDescription() { return description; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public void setDescription(String description) { this.description = description; }
}
