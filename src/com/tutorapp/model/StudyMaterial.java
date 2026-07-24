package com.tutorapp.model;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class StudyMaterial {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int materialId;
    private String title;
    private String fileURL;
    private final LocalDate uploadDate;

    public StudyMaterial(String title, String fileURL) {
        this.materialId = COUNTER.getAndIncrement();
        this.title = title;
        this.fileURL = fileURL;
        this.uploadDate = LocalDate.now();
    }

    private StudyMaterial(int materialId, String title, String fileURL, LocalDate uploadDate) {
        this.materialId = materialId;
        this.title = title;
        this.fileURL = fileURL;
        this.uploadDate = uploadDate;
    }

    // Rebuilds a StudyMaterial that already exists in the database, preserving its original id and upload date. 

    public static StudyMaterial restore(int materialId, String title, String fileURL, LocalDate uploadDate) {
        return new StudyMaterial(materialId, title, fileURL, uploadDate);
    }

    //Ensures the next auto-assigned id won't collide with one already loaded from the database. 
     
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    public void upload(String title, String fileURL) {
        this.title = title;
        this.fileURL = fileURL;
    }

    //Returns the location the file can be downloaded from. 
    
    public String download() {
        return fileURL;
    }

    public int getMaterialId() { return materialId; }
    public String getTitle() { return title; }
    public String getFileURL() { return fileURL; }
    public LocalDate getUploadDate() { return uploadDate; }
}
