package com.tutorapp.store;

import com.tutorapp.model.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

final class DatabaseLoader {

    private DatabaseLoader() {}

    // Loads everything from the database into {@code store}. Returns true if any users were found. 
    static boolean loadInto(DataStore store) {
        try (Connection conn = Database.connect()) {
            Map<Integer, Subject> subjectsById = loadSubjects(conn, store);
            Map<String, Boolean> tutorVerified = loadTutorVerifiedFlags(conn);
            Map<String, User> usersById = loadUsers(conn, store, tutorVerified);

            if (usersById.isEmpty()) {
                // Fresh/empty database - nothing to rebuild. Let DataStore seed as usual.
                return false;
            }

            loadTutorSubjects(conn, usersById, subjectsById);
            loadQualifications(conn, usersById);
            Map<Integer, SessionOption> optionsById = loadSessionOptions(conn, usersById, subjectsById);
            loadStudyMaterials(conn, usersById, optionsById);
            Map<Integer, Payment> paymentsById = loadPayments(conn);
            loadSessionRequests(conn, store, usersById, subjectsById, optionsById);
            loadSessions(conn, store, usersById, subjectsById, optionsById, paymentsById);

            return true;
        } catch (SQLException e) {
            System.out.println("[TutorApp/Database] Failed to load existing data on startup: " + e.getMessage()
                    + " - falling back to default seed data.");
            return false;
        }
    }

    private static Map<Integer, Subject> loadSubjects(Connection conn, DataStore store) throws SQLException {
        Map<Integer, Subject> result = new HashMap<>();
        int maxId = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT subject_id, subject_name, description FROM subjects")) {
            while (rs.next()) {
                int id = rs.getInt("subject_id");
                Subject subject = Subject.restore(id, rs.getString("subject_name"), rs.getString("description"));
                store.loadSubject(subject);
                result.put(id, subject);
                maxId = Math.max(maxId, id);
            }
        }
        Subject.bumpCounterPast(maxId);
        return result;
    }

    private static Map<String, Boolean> loadTutorVerifiedFlags(Connection conn) throws SQLException {
        Map<String, Boolean> result = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, verified FROM tutor_profiles")) {
            while (rs.next()) {
                result.put(rs.getString("user_id"), rs.getBoolean("verified"));
            }
        }
        return result;
    }

    private static Map<String, User> loadUsers(Connection conn, DataStore store, Map<String, Boolean> tutorVerified)
            throws SQLException {
        Map<String, User> result = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT user_id, name, email, password_hash, phone, role, profile_picture_url FROM users")) {
            while (rs.next()) {
                String id = rs.getString("user_id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                String password = rs.getString("password_hash"); // stored (and compared) as plaintext, see README
                String phone = rs.getString("phone");
                String role = rs.getString("role");
                String profilePictureUrl = rs.getString("profile_picture_url");

                User user = switch (role) {
                    case "TUTOR" -> Tutor.restore(id, name, email, password, phone,
                            tutorVerified.getOrDefault(id, false));
                    case "ADMIN" -> Admin.restore(id, name, email, password, phone);
                    default -> Student.restore(id, name, email, password, phone);
                };
                user.setProfilePictureUrl(profilePictureUrl);
                store.loadUser(user);
                result.put(id, user);
            }
        }
        return result;
    }

    private static void loadTutorSubjects(Connection conn, Map<String, User> usersById,
                                           Map<Integer, Subject> subjectsById) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT tutor_id, subject_id FROM tutor_subjects")) {
            while (rs.next()) {
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                Subject subject = subjectsById.get(rs.getInt("subject_id"));
                if (tutor != null && subject != null) {
                    tutor.addSubjectToTeach(subject);
                }
            }
        }
    }

    private static void loadQualifications(Connection conn, Map<String, User> usersById) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT qualification_id, tutor_id, title, document_url, status FROM qualifications")) {
            while (rs.next()) {
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                if (tutor == null) continue;
                Qualification q = Qualification.restore(
                        rs.getString("qualification_id"),
                        rs.getString("title"),
                        rs.getString("document_url"),
                        QualificationStatus.valueOf(rs.getString("status")));
                tutor.getQualifications().add(q);
            }
        }
    }

    private static void loadStudyMaterials(Connection conn, Map<String, User> usersById,
                                            Map<Integer, SessionOption> optionsById) throws SQLException {
        int maxId = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT material_id, tutor_id, option_id, title, file_url, upload_date FROM study_materials")) {
            while (rs.next()) {
                int id = rs.getInt("material_id");
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                maxId = Math.max(maxId, id);
                if (tutor == null) continue;
                int optionId = rs.getInt("option_id");
                SessionOption option = rs.wasNull() ? null : optionsById.get(optionId);
                StudyMaterial m = StudyMaterial.restore(
                        id, rs.getString("title"), rs.getString("file_url"),
                        rs.getObject("upload_date", LocalDate.class), option);
                tutor.getStudyMaterials().add(m);
            }
        }
        StudyMaterial.bumpCounterPast(maxId);
    }

    private static Map<Integer, SessionOption> loadSessionOptions(Connection conn, Map<String, User> usersById,
                                                                    Map<Integer, Subject> subjectsById) throws SQLException {
        Map<Integer, SessionOption> result = new HashMap<>();
        int maxId = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT option_id, tutor_id, subject_id, title, duration_minutes, price, max_students FROM session_options")) {
            while (rs.next()) {
                int id = rs.getInt("option_id");
                maxId = Math.max(maxId, id);
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                Subject subject = subjectsById.get(rs.getInt("subject_id"));
                if (tutor == null || subject == null) continue;
                SessionOption option = SessionOption.restore(id, tutor, subject,
                        rs.getString("title"), rs.getInt("duration_minutes"), rs.getDouble("price"),
                        rs.getInt("max_students"));
                tutor.getSessionOptions().add(option);
                result.put(id, option);
            }
        }
        SessionOption.bumpCounterPast(maxId);
        return result;
    }

    private static Map<Integer, Payment> loadPayments(Connection conn) throws SQLException {
        Map<Integer, Payment> result = new HashMap<>();
        int maxId = 0;
        String sql = "SELECT p.payment_id, p.amount, p.status, p.payment_type, "
                + "c.card_number, c.holder_name, c.expiry_date, "
                + "b.account_number, b.receipt_image "
                + "FROM payments p "
                + "LEFT JOIN card_payments c ON c.payment_id = p.payment_id "
                + "LEFT JOIN bank_payments b ON b.payment_id = p.payment_id";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("payment_id");
                maxId = Math.max(maxId, id);
                double amount = rs.getDouble("amount");
                PaymentStatus status = PaymentStatus.valueOf(rs.getString("status"));
                Payment payment;
                if ("CARD".equals(rs.getString("payment_type"))) {
                    payment = CardPayment.restore(id, amount, status,
                            rs.getString("card_number"), rs.getString("holder_name"), rs.getString("expiry_date"));
                } else {
                    payment = BankPayment.restore(id, amount, status,
                            rs.getString("account_number"), rs.getString("receipt_image"));
                }
                result.put(id, payment);
            }
        }
        Payment.bumpCounterPast(maxId);
        return result;
    }

    private static void loadSessionRequests(Connection conn, DataStore store, Map<String, User> usersById,
                                             Map<Integer, Subject> subjectsById,
                                             Map<Integer, SessionOption> optionsById) throws SQLException {
        int maxId = 0;
        String sql = "SELECT request_id, student_id, tutor_id, subject_id, option_id, price, "
                + "request_date, request_time, status FROM session_requests";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("request_id");
                maxId = Math.max(maxId, id);
                Student student = asStudent(usersById.get(rs.getString("student_id")));
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                Subject subject = subjectsById.get(rs.getInt("subject_id"));
                SessionOption option = optionsById.get(rs.getInt("option_id"));
                if (student == null || tutor == null || subject == null || option == null) continue;

                SessionRequest request = SessionRequest.restore(id, student, tutor, subject, option,
                        rs.getDouble("price"), rs.getObject("request_date", LocalDate.class),
                        rs.getString("request_time"), RequestStatus.valueOf(rs.getString("status")));
                store.loadSessionRequest(request);
            }
        }
        SessionRequest.bumpCounterPast(maxId);
    }

    private static void loadSessions(Connection conn, DataStore store, Map<String, User> usersById,
                                      Map<Integer, Subject> subjectsById, Map<Integer, SessionOption> optionsById,
                                      Map<Integer, Payment> paymentsById) throws SQLException {
        int maxId = 0;
        String sql = "SELECT session_id, student_id, tutor_id, subject_id, option_id, price, payment_id, "
                + "scheduled_date, start_time, end_time, meeting_link, status FROM sessions";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("session_id");
                maxId = Math.max(maxId, id);
                Student student = asStudent(usersById.get(rs.getString("student_id")));
                Tutor tutor = asTutor(usersById.get(rs.getString("tutor_id")));
                Subject subject = subjectsById.get(rs.getInt("subject_id"));
                SessionOption option = optionsById.get(rs.getInt("option_id"));
                if (student == null || tutor == null || subject == null || option == null) continue;

                int paymentId = rs.getInt("payment_id");
                Payment payment = rs.wasNull() ? null : paymentsById.get(paymentId);

                Session session = Session.restore(id, student, tutor, subject, option, rs.getDouble("price"),
                        payment, rs.getObject("scheduled_date", LocalDate.class),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class),
                        rs.getString("meeting_link"), SessionStatus.valueOf(rs.getString("status")));

                store.loadSession(session);
                student.getSessions().add(session);
            }
        }
        Session.bumpCounterPast(maxId);
    }

    private static Tutor asTutor(User user) {
        return (user instanceof Tutor tutor) ? tutor : null;
    }

    private static Student asStudent(User user) {
        return (user instanceof Student student) ? student : null;
    }
}
