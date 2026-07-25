-- =====================================================================
-- TutorApp - Database Schema
-- Mirrors the OOP class diagram: User (base) -> Student/Tutor/Admin,
-- Payment (base) -> CardPayment/BankPayment, plus Subject, Qualification,
-- StudyMaterial, SessionRequest, Session.
--
-- Written for MySQL/MariaDB (matches XAMPP's bundled MySQL). Adjust
-- AUTO_INCREMENT/ENUM syntax if targeting Postgres or SQL Server.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS tutorapp
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tutorapp;

-- ---------------------------------------------------------------------
-- USERS  (base table for the abstract User class)
-- Student / Tutor / Admin share these columns. `role` is the
-- discriminator; role-specific extra columns live in their own tables
-- below (class-table inheritance), keeping this table lean.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id             CHAR(36)     NOT NULL PRIMARY KEY,        -- UUID, matches User.userId
    name                VARCHAR(120) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,                    -- store a hash, never plaintext
    phone               VARCHAR(30)  NOT NULL,
    role                ENUM('STUDENT', 'TUTOR', 'ADMIN') NOT NULL,
    profile_picture_url VARCHAR(300),                             -- uploaded avatar, nullable
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tutor-only attributes (Student/Admin have no extra columns so they
-- don't need their own extension table).
CREATE TABLE tutor_profiles (
    user_id  CHAR(36) NOT NULL PRIMARY KEY,
    verified BOOLEAN  NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tutor_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- SUBJECT
-- ---------------------------------------------------------------------
CREATE TABLE subjects (
    subject_id   INT AUTO_INCREMENT PRIMARY KEY,
    subject_name VARCHAR(100) NOT NULL,
    description  VARCHAR(500)
);

-- Many-to-many: which subjects a tutor teaches.
CREATE TABLE tutor_subjects (
    tutor_id   CHAR(36) NOT NULL,
    subject_id INT      NOT NULL,
    PRIMARY KEY (tutor_id, subject_id),
    CONSTRAINT fk_tutor_subjects_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tutor_subjects_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- QUALIFICATION  (one tutor -> many qualifications)
-- ---------------------------------------------------------------------
CREATE TABLE qualifications (
    qualification_id CHAR(36) NOT NULL PRIMARY KEY,
    tutor_id         CHAR(36) NOT NULL,
    title            VARCHAR(150) NOT NULL,
    document_url     VARCHAR(300) NOT NULL,
    status           ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    uploaded_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qualifications_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- STUDY MATERIAL  (one tutor -> many materials)
-- ---------------------------------------------------------------------
-- NOTE: option_id references session_options, which is defined further down this file,
-- so that foreign key is added afterwards via ALTER TABLE (see below session_options).
CREATE TABLE study_materials (
    material_id  INT AUTO_INCREMENT PRIMARY KEY,
    tutor_id     CHAR(36) NOT NULL,
    option_id    INT NULL,                                        -- which of the tutor's session offerings this material belongs to
    title        VARCHAR(150) NOT NULL,
    file_url     VARCHAR(300) NOT NULL,
    upload_date  DATE NOT NULL,
    CONSTRAINT fk_study_materials_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- SESSION OPTION  (a tutor's own priced offering, e.g. "1-Hour Algebra - Rs. 2500")
-- Tutors list as many of these as they like, each with its own subject,
-- duration, and price. Students pick one of these when requesting a session.
-- ---------------------------------------------------------------------
CREATE TABLE session_options (
    option_id         INT AUTO_INCREMENT PRIMARY KEY,
    tutor_id          CHAR(36)     NOT NULL,
    subject_id        INT          NOT NULL,
    title             VARCHAR(150) NOT NULL,
    duration_minutes  INT          NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    max_students      INT          NOT NULL DEFAULT 1,   -- how many students can book this session
    CONSTRAINT fk_session_options_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_options_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(subject_id)
);

-- Deferred FK: study_materials.option_id -> session_options.option_id
-- (added here since session_options is defined after study_materials above)
ALTER TABLE study_materials
    ADD CONSTRAINT fk_study_materials_option
        FOREIGN KEY (option_id) REFERENCES session_options(option_id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- SESSION REQUEST  (student asks to hire a tutor for a subject/time slot)
-- ---------------------------------------------------------------------
CREATE TABLE session_requests (
    request_id    INT AUTO_INCREMENT PRIMARY KEY,
    student_id    CHAR(36) NOT NULL,
    tutor_id      CHAR(36) NOT NULL,
    subject_id    INT      NOT NULL,
    option_id     INT      NOT NULL,       -- which of the tutor's priced options was booked
    price         DECIMAL(10,2) NOT NULL,  -- snapshot of session_options.price at request time
    request_date  DATE     NOT NULL,
    request_time  VARCHAR(20) NOT NULL,   -- e.g. "10:00-11:00"
    status        ENUM('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_requests_student
        FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_requests_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_requests_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    CONSTRAINT fk_session_requests_option
        FOREIGN KEY (option_id) REFERENCES session_options(option_id)
);

-- ---------------------------------------------------------------------
-- PAYMENT  (abstract Payment -> CardPayment / BankPayment)
-- Class-table inheritance: shared columns in `payments`, method-specific
-- columns in their own child table keyed by payment_id.
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    payment_id   INT AUTO_INCREMENT PRIMARY KEY,
    amount       DECIMAL(10,2) NOT NULL,
    status       ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
    payment_type ENUM('CARD', 'BANK') NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE card_payments (
    payment_id  INT NOT NULL PRIMARY KEY,
    card_number VARCHAR(25) NOT NULL,     -- store masked/tokenized in production, never raw PAN
    holder_name VARCHAR(120) NOT NULL,
    expiry_date VARCHAR(10) NOT NULL,     -- "MM/YY"
    CONSTRAINT fk_card_payments_payment
        FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE CASCADE
);

CREATE TABLE bank_payments (
    payment_id     INT NOT NULL PRIMARY KEY,
    account_number VARCHAR(40) NOT NULL,
    receipt_image  VARCHAR(300),
    CONSTRAINT fk_bank_payments_payment
        FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- SESSION  (created once a tutor accepts a SessionRequest)
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    session_id     INT AUTO_INCREMENT PRIMARY KEY,
    student_id     CHAR(36) NOT NULL,
    tutor_id       CHAR(36) NOT NULL,
    subject_id     INT      NOT NULL,
    option_id      INT      NOT NULL,       -- which of the tutor's priced options this session was booked from
    price          DECIMAL(10,2) NOT NULL,  -- amount due, snapshotted from the request/option
    payment_id     INT      NULL,             -- NULL until the student pays
    scheduled_date DATE     NOT NULL,
    start_time     TIME     NOT NULL,
    end_time       TIME     NOT NULL,
    meeting_link   VARCHAR(300),
    status         ENUM('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_student
        FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_tutor
        FOREIGN KEY (tutor_id) REFERENCES tutor_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    CONSTRAINT fk_sessions_option
        FOREIGN KEY (option_id) REFERENCES session_options(option_id),
    CONSTRAINT fk_sessions_payment
        FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------------
-- Helpful indexes for common lookups (dashboards, availability checks)
-- ---------------------------------------------------------------------
CREATE INDEX idx_sessions_tutor_date       ON sessions(tutor_id, scheduled_date);
CREATE INDEX idx_sessions_student          ON sessions(student_id);
CREATE INDEX idx_session_requests_tutor    ON session_requests(tutor_id, status);
CREATE INDEX idx_session_requests_student  ON session_requests(student_id);
CREATE INDEX idx_qualifications_status     ON qualifications(status);
CREATE INDEX idx_session_options_tutor     ON session_options(tutor_id);

-- ---------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------
-- No manual INSERTs here on purpose: TutorApp itself seeds 3 subjects
-- (Mathematics, English, Science) and one admin account
-- (admin@tutorapp.com / admin123) every time it starts up, in
-- DataStore's constructor. If MySQL is reachable (see db.properties),
-- that same seeding is automatically mirrored into the tables below the
-- very first time the app connects - so this schema fills itself in
-- rather than needing its own separate seed block. If you want to seed
-- this database manually without running the app (e.g. to inspect it in
-- isolation), you can INSERT your own rows here; just don't leave both
-- this file's inserts AND a running app pointed at the same empty
-- database, since they'll then race to seed the same rows.

-- =====================================================================
-- Entity relationship summary
-- =====================================================================
-- users (1) ----------- (1) tutor_profiles
-- tutor_profiles (M) --- (M) subjects            via tutor_subjects
-- tutor_profiles (1) --- (M) qualifications
-- tutor_profiles (1) --- (M) study_materials
-- session_options (1) -- (M) study_materials     (which session offering a material belongs to)
-- tutor_profiles (1) --- (M) session_options      (tutor's own priced offerings)
-- users[student] (1) --- (M) session_requests --- (M..1) tutor_profiles
-- session_requests (M) - (1) session_options       (price snapshotted at request time)
-- session_requests --(accepted)--> sessions
-- sessions (M) --------- (1) subjects
-- sessions (M) --------- (1) session_options       (price snapshotted at booking time)
-- sessions (1) --------- (0..1) payments
-- payments (1) --------- (0..1) card_payments  OR  (0..1) bank_payments
-- =====================================================================
