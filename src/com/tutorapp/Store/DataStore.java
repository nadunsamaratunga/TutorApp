package com.tutorapp.store;

import com.tutorapp.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// In-memory singleton data store. Everything lives in memory only (no DB,no external dependencies) and is reset whenever the server restarts.

public class DataStore {
    private static final DataStore INSTANCE = new DataStore();
    public static DataStore get() { return INSTANCE; }

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final List<SessionRequest> sessionRequests = new ArrayList<>();
    private final List<Session> sessions = new ArrayList<>();

    private DataStore() {
     
        boolean loadedFromDb = Database.isAvailable() && DatabaseLoader.loadInto(this);

        if (!loadedFromDb) {
            // seed a couple of subjects so the app isn't empty on first run
            subjects.add(new Subject("Mathematics", "Algebra, calculus, geometry and more"));
            subjects.add(new Subject("English", "Grammar, essay writing and literature"));
            subjects.add(new Subject("Science", "Physics, chemistry and biology"));
            subjects.forEach(SqlPersistence::saveSubject);

            // seed one admin account: admin@tutorapp.com / admin123
            Admin defaultAdmin = new Admin("Site Admin", "admin@tutorapp.com", "admin123", "0000000000");
            usersByEmail.put(defaultAdmin.getEmail().toLowerCase(), defaultAdmin);
            usersById.put(defaultAdmin.getUserId(), defaultAdmin);
            SqlPersistence.saveUser(defaultAdmin);
        }
    }

    //Load-only mutators used by DatabaseLoader while rebuilding state from the database. 

    void loadUser(User user) {
        usersByEmail.put(user.getEmail().toLowerCase(), user);
        usersById.put(user.getUserId(), user);
    }

    void loadSubject(Subject subject) {
        subjects.add(subject);
    }

    void loadSessionRequest(SessionRequest request) {
        sessionRequests.add(request);
    }

    void loadSession(Session session) {
        sessions.add(session);
    }

    public boolean emailExists(String email) {
        return usersByEmail.containsKey(email.toLowerCase());
    }

    public void addUser(User user) {
        usersByEmail.put(user.getEmail().toLowerCase(), user);
        usersById.put(user.getUserId(), user);
        SqlPersistence.saveUser(user);
    }

    public User findByEmail(String email) {
        return usersByEmail.get(email.toLowerCase());
    }

    public User findById(String id) {
        return usersById.get(id);
    }

    public List<Tutor> allTutors() {
        List<Tutor> result = new ArrayList<>();
        for (User u : usersById.values()) {
            if (u instanceof Tutor) result.add((Tutor) u);
        }
        return result;
    }

    public List<Tutor> verifiedTutors() {
        List<Tutor> result = new ArrayList<>();
        for (Tutor t : allTutors()) {
            if (t.isVerified()) result.add(t);
        }
        return result;
    }

    public List<Subject> allSubjects() { return subjects; }

    public Subject findSubject(int id) {
        for (Subject s : subjects) if (s.getSubjectId() == id) return s;
        return null;
    }

    public void addSubject(Subject subject) {
        subjects.add(subject);
        SqlPersistence.saveSubject(subject);
    }

    public void removeSubject(int id) {
        subjects.removeIf(s -> s.getSubjectId() == id);
        SqlPersistence.deleteSubject(id);
    }

    public void addSessionRequest(SessionRequest r) {
        sessionRequests.add(r);
        SqlPersistence.saveSessionRequest(r);
    }
    public List<SessionRequest> allSessionRequests() { return sessionRequests; }
    public SessionRequest findRequest(int id) {
        for (SessionRequest r : sessionRequests) if (r.getRequestId() == id) return r;
        return null;
    }

    public void addSession(Session s) {
        sessions.add(s);
        SqlPersistence.saveSession(s);
    }
    public List<Session> allSessions() { return sessions; }
    public Session findSession(int id) {
        for (Session s : sessions) if (s.getSessionId() == id) return s;
        return null;
    }
}
