package com.tutorapp.model;

import com.tutorapp.store.DataStore;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Tutor extends User {
    private final List<Qualification> qualifications = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final List<StudyMaterial> studyMaterials = new ArrayList<>();
    private final List<SessionOption> sessionOptions = new ArrayList<>();
    private boolean verified = false;

    public Tutor(String name, String email, String password, String phone) {
        super(name, email, password, phone);
    }

    private Tutor(String userId, String name, String email, String password, String phone, boolean verified) {
        super(userId, name, email, password, phone);
        this.verified = verified;
    }

    // Rebuilds a Tutor that already exists in the database, preserving its original id and verified flag. 
    
    public static Tutor restore(String userId, String name, String email, String password, String phone, boolean verified) {
        return new Tutor(userId, name, email, password, phone, verified);
    }

    @Override
    public String getRole() { return "TUTOR"; }

    // Validates and creates a new Tutor account, then registers it in the data store. Returns null if the email is already taken.
     
    public static Tutor register(String name, String email, String password, String phone) {
        if (DataStore.get().emailExists(email)) return null;
        Tutor tutor = new Tutor(name, email, password, phone);
        DataStore.get().addUser(tutor);
        return tutor;
    }

    public Qualification uploadQualification(String title, String documentURL) {
        Qualification q = new Qualification(title, documentURL);
        qualifications.add(q);
        return q;
    }

    public StudyMaterial uploadMaterial(String title, String fileURL, SessionOption option) {
        StudyMaterial m = new StudyMaterial(title, fileURL, option);
        studyMaterials.add(m);
        return m;
    }

    // All study materials this tutor has shared for one particular session option.
    public List<StudyMaterial> materialsFor(SessionOption option) {
        return studyMaterials.stream().filter(m -> m.getOption() == option).toList();
    }

    // Adds a subject to teach, ignoring duplicates. 
     
    public void addSubjectToTeach(Subject subject) {
        if (subject != null && !subjects.contains(subject)) subjects.add(subject);
    }

    // Defines a new priced session offering (e.g. "1-Hour Algebra - Rs. 2500"), capped at maxStudents students. 

    public SessionOption addSessionOption(Subject subject, String title, int durationMinutes, double price, int maxStudents) {
        if (subject == null || title == null || title.isBlank() || durationMinutes <= 0 || price < 0 || maxStudents < 1) {
            throw new IllegalArgumentException("Please fill in every field with a valid value.");
        }
        SessionOption option = new SessionOption(this, subject, title, durationMinutes, price, maxStudents);
        sessionOptions.add(option);
        return option;
    }

    public void removeSessionOption(int optionId) {
        sessionOptions.removeIf(o -> o.getOptionId() == optionId);
    }

    public SessionOption findSessionOption(int optionId) {
        for (SessionOption o : sessionOptions) if (o.getOptionId() == optionId) return o;
        return null;
    }

    public void acceptSession(Session session) {
        session.setStatus(SessionStatus.ACTIVE);
    }

    // Accepts a pending request: approves it, creates the Session, and links it to both parties. 
     
    public Session acceptRequest(SessionRequest request) {
        request.approve();
        String[] times = request.getRequestTime().split("-");
        Session session = new Session(request.getStudent(), this, request.getSubject(), request.getOption(),
                request.getPrice(), request.getRequestDate(), LocalTime.parse(times[0]), LocalTime.parse(times[1]));
        DataStore.get().addSession(session);
        request.getStudent().getSessions().add(session);
        return session;
    }

    public void rejectRequest(SessionRequest request) { request.deny(); }

    // Marks one of this tutor's own active sessions as complete. 
    public void completeSession(Session session) {
        if (session.getTutor() == this && session.getStatus() == SessionStatus.ACTIVE) {
            session.completeSession();
        }
    }

    public List<SessionRequest> pendingRequests() {
        return DataStore.get().allSessionRequests().stream()
                .filter(r -> r.getTutor() == this && r.getStatus() == RequestStatus.PENDING)
                .toList();
    }

    public List<SessionRequest> allRequests() {
        return DataStore.get().allSessionRequests().stream()
                .filter(r -> r.getTutor() == this)
                .toList();
    }

    public List<Session> allSessions() {
        return DataStore.get().allSessions().stream()
                .filter(s -> s.getTutor() == this)
                .toList();
    }

    public List<Qualification> getQualifications() { return qualifications; }
    public List<Subject> getSubjects() { return subjects; }
    public List<StudyMaterial> getStudyMaterials() { return studyMaterials; }
    public List<SessionOption> getSessionOptions() { return sessionOptions; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
