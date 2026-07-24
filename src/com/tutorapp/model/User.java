package com.tutorapp.model;

import com.tutorapp.store.DataStore;

import java.util.UUID;

// Abstract base class for any person logs into the system

public abstract class User {
    private final String userId;
    private String name;
    private String email;
    private String password;
    private String phone;
    private String profilePictureUrl;

    protected User(String name, String email, String password, String phone) {
        this(UUID.randomUUID().toString(), name, email, password, phone);
    }

    // Used when reconstructing an account that already has an id  (loading from database etc.)
    protected User(String userId, String name, String email, String password, String phone) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
    }

   
    // Every concrete user type must say what role it plays in the system (student/tutor/admin)
    public abstract String getRole();

    public boolean login(String email, String password) {
        return this.email.equalsIgnoreCase(email) && this.password.equals(password);
    }

    public void logout() {
        // Session removal is handled by SessionManager in util layer
    }

    // Looks up the account by email and validates the password. Returns null if either is wrong
    public static User authenticate(String email, String password) {
        if (email == null || password == null) return null;
        User user = DataStore.get().findByEmail(email);
        if (user == null || !user.login(email, password)) return null;
        return user;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getPhone() { return phone; }
    public String getProfilePictureUrl() { return profilePictureUrl; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
}
