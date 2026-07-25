package com.tutorapp.store;

import com.tutorapp.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class SqlPersistence {

    private SqlPersistence() {}

    public static void saveUser(User user) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO users (user_id, name, email, password_hash, phone, role, profile_picture_url) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword()); // NOTE: app stores/compares plaintext in-memory too; see README
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getRole());
            ps.setString(7, user.getProfilePictureUrl());
            ps.executeUpdate();

            if (user instanceof Tutor tutor) {
                String tutorSql = "INSERT INTO tutor_profiles (user_id, verified) VALUES (?,?)";
                try (PreparedStatement tps = conn.prepareStatement(tutorSql)) {
                    tps.setString(1, tutor.getUserId());
                    tps.setBoolean(2, tutor.isVerified());
                    tps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logFailure("saveUser", e);
        }
    }

    public static void updateProfilePicture(User user) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE users SET profile_picture_url = ? WHERE user_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getProfilePictureUrl());
            ps.setString(2, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updateProfilePicture", e);
        }
    }

    public static void updateTutorVerified(Tutor tutor) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE tutor_profiles SET verified = ? WHERE user_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, tutor.isVerified());
            ps.setString(2, tutor.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updateTutorVerified", e);
        }
    }

    public static void saveSubject(Subject subject) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO subjects (subject_id, subject_name, description) VALUES (?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subject.getSubjectId());
            ps.setString(2, subject.getSubjectName());
            ps.setString(3, subject.getDescription());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveSubject", e);
        }
    }

    public static void deleteSubject(int subjectId) {
        if (!Database.isAvailable()) return;
        String sql = "DELETE FROM subjects WHERE subject_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("deleteSubject", e);
        }
    }

    public static void saveTutorSubject(String tutorId, int subjectId) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT IGNORE INTO tutor_subjects (tutor_id, subject_id) VALUES (?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tutorId);
            ps.setInt(2, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveTutorSubject", e);
        }
    }

    public static void saveQualification(String tutorId, Qualification q) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO qualifications (qualification_id, tutor_id, title, document_url, status) VALUES (?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getQualificationId());
            ps.setString(2, tutorId);
            ps.setString(3, q.getTitle());
            ps.setString(4, q.getDocumentURL());
            ps.setString(5, q.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveQualification", e);
        }
    }

    public static void updateQualificationStatus(Qualification q) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE qualifications SET status = ? WHERE qualification_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getStatus().name());
            ps.setString(2, q.getQualificationId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updateQualificationStatus", e);
        }
    }

    public static void saveStudyMaterial(String tutorId, StudyMaterial m) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO study_materials (material_id, tutor_id, option_id, title, file_url, upload_date) VALUES (?,?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.getMaterialId());
            ps.setString(2, tutorId);
            if (m.getOption() != null) ps.setInt(3, m.getOption().getOptionId());
            else ps.setNull(3, java.sql.Types.INTEGER);
            ps.setString(4, m.getTitle());
            ps.setString(5, m.getFileURL());
            ps.setObject(6, m.getUploadDate());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveStudyMaterial", e);
        }
    }

    public static void saveSessionOption(SessionOption option) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO session_options (option_id, tutor_id, subject_id, title, duration_minutes, price, max_students) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, option.getOptionId());
            ps.setString(2, option.getTutor().getUserId());
            ps.setInt(3, option.getSubject().getSubjectId());
            ps.setString(4, option.getTitle());
            ps.setInt(5, option.getDurationMinutes());
            ps.setDouble(6, option.getPrice());
            ps.setInt(7, option.getMaxStudents());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveSessionOption", e);
        }
    }

    public static void deleteSessionOption(int optionId) {
        if (!Database.isAvailable()) return;
        String sql = "DELETE FROM session_options WHERE option_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, optionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("deleteSessionOption", e);
        }
    }

    public static void saveSessionRequest(SessionRequest r) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO session_requests "
                + "(request_id, student_id, tutor_id, subject_id, option_id, price, request_date, request_time, status) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getRequestId());
            ps.setString(2, r.getStudent().getUserId());
            ps.setString(3, r.getTutor().getUserId());
            ps.setInt(4, r.getSubject().getSubjectId());
            ps.setInt(5, r.getOption().getOptionId());
            ps.setDouble(6, r.getPrice());
            ps.setObject(7, r.getRequestDate());
            ps.setString(8, r.getRequestTime());
            ps.setString(9, r.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveSessionRequest", e);
        }
    }

    public static void updateSessionRequestStatus(SessionRequest r) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE session_requests SET status = ? WHERE request_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getStatus().name());
            ps.setInt(2, r.getRequestId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updateSessionRequestStatus", e);
        }
    }

    public static void saveSession(Session s) {
        if (!Database.isAvailable()) return;
        String sql = "INSERT INTO sessions "
                + "(session_id, student_id, tutor_id, subject_id, option_id, price, payment_id, "
                + "scheduled_date, start_time, end_time, meeting_link, status) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.getSessionId());
            ps.setString(2, s.getStudent().getUserId());
            ps.setString(3, s.getTutor().getUserId());
            ps.setInt(4, s.getSubject().getSubjectId());
            ps.setInt(5, s.getOption().getOptionId());
            ps.setDouble(6, s.getPrice());
            ps.setObject(7, null); // no payment yet
            ps.setObject(8, s.getScheduledDate());
            ps.setObject(9, s.getStartTime());
            ps.setObject(10, s.getEndTime());
            ps.setString(11, s.getMeetingLink());
            ps.setString(12, s.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("saveSession", e);
        }
    }

    public static void updateSessionStatus(Session s) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE sessions SET status = ? WHERE session_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getStatus().name());
            ps.setInt(2, s.getSessionId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updateSessionStatus", e);
        }
    }

    // Updates just a payment's status column - used when an admin verifies or rejects a bank transfer proof. 
    public static void updatePaymentStatus(Payment payment) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE payments SET status = ? WHERE payment_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payment.getStatus().name());
            ps.setInt(2, payment.getPaymentId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("updatePaymentStatus", e);
        }
    }

    // Clears a session's payment link (used when an admin rejects a submitted bank transfer proof). 
    public static void clearSessionPayment(Session session) {
        if (!Database.isAvailable()) return;
        String sql = "UPDATE sessions SET payment_id = NULL WHERE session_id = ?";
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.getSessionId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logFailure("clearSessionPayment", e);
        }
    }

    // Persists the session's Payment (base row + Card/Bank subtype row) and links it to the session. 
    public static void updateSessionPayment(Session session) {
        if (!Database.isAvailable()) return;
        Payment payment = session.getPayment();
        if (payment == null) return;

        String paymentSql = "INSERT INTO payments (payment_id, amount, status, payment_type) VALUES (?,?,?,?)";
        try (Connection conn = Database.connect()) {
            try (PreparedStatement ps = conn.prepareStatement(paymentSql)) {
                ps.setInt(1, payment.getPaymentId());
                ps.setDouble(2, payment.getAmount());
                ps.setString(3, payment.getStatus().name());
                ps.setString(4, payment instanceof CardPayment ? "CARD" : "BANK");
                ps.executeUpdate();
            }

            if (payment instanceof CardPayment card) {
                String sql = "INSERT INTO card_payments (payment_id, card_number, holder_name, expiry_date) VALUES (?,?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, card.getPaymentId());
                    ps.setString(2, card.getMaskedCardNumber()); // never persist a raw PAN
                    ps.setString(3, card.getHolderName());
                    ps.setString(4, card.getExpiryDate());
                    ps.executeUpdate();
                }
            } else if (payment instanceof BankPayment bank) {
                String sql = "INSERT INTO bank_payments (payment_id, account_number, receipt_image) VALUES (?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, bank.getPaymentId());
                    ps.setString(2, bank.getAccountNumber());
                    ps.setString(3, bank.getReceiptImage());
                    ps.executeUpdate();
                }
            }

            String linkSql = "UPDATE sessions SET payment_id = ?, status = ? WHERE session_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                ps.setInt(1, payment.getPaymentId());
                ps.setString(2, session.getStatus().name());
                ps.setInt(3, session.getSessionId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logFailure("updateSessionPayment", e);
        }
    }

    private static void logFailure(String operation, SQLException e) {
        System.out.println("[TutorApp/Database] " + operation + " failed: " + e.getMessage());
    }
}
