package com.tutorapp.model;

import java.util.UUID;

public class Qualification {
    private final String qualificationId;
    private String title;
    private String documentURL;
    private QualificationStatus status;

    public Qualification(String title, String documentURL) {
        this.qualificationId = UUID.randomUUID().toString();
        this.title = title;
        this.documentURL = documentURL;
        this.status = QualificationStatus.PENDING;
    }

    private Qualification(String qualificationId, String title, String documentURL, QualificationStatus status) {
        this.qualificationId = qualificationId;
        this.title = title;
        this.documentURL = documentURL;
        this.status = status;
    }

    // Rebuilds a Qualification that already exists in the database, preserving its original id and status.
    public static Qualification restore(String qualificationId, String title, String documentURL, QualificationStatus status) {
        return new Qualification(qualificationId, title, documentURL, status);
    }

    // Marks the qualification as submitted / (re)uploaded.
    public void upload(String title, String documentURL) {
        this.title = title;
        this.documentURL = documentURL;
        this.status = QualificationStatus.PENDING;
    }

    public String getQualificationId() { return qualificationId; }
    public String getTitle() { return title; }
    public String getDocumentURL() { return documentURL; }
    public QualificationStatus getStatus() { return status; }
    public void setStatus(QualificationStatus status) { this.status = status; }
}
