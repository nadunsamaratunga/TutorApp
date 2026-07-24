package com.tutorapp.store;

import com.tutorapp.util.ProjectPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Thin JDBC connection helper for MySQL/MariaDB. Configure connection details in db.properties at the project root (same folder as 'src' and 'web'). Defaults match a stock XAMPP MySQL install (root user, no password, localhost:3306).
 
public final class Database {
    private static final String CONFIG_FILE = "db.properties";

    private static String url;
    private static String user;
    private static String password;
    private static boolean enabled;

    private static volatile Boolean available; // null = not checked yet
    private static volatile boolean warnedOnce = false;

    private Database() {}

    static {
        loadConfig();
        registerDriver();
    }

    private static void loadConfig() {
        Properties props = new Properties();
        try {
            Path configPath = ProjectPaths.findProjectRoot().resolve(CONFIG_FILE);
            if (Files.isRegularFile(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
            }
        } catch (Exception e) {
            // No project root / no config file found - fall through to defaults below.
        }

        url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/tutorapp");
        user = props.getProperty("db.user", "root");
        password = props.getProperty("db.password", "");
        enabled = Boolean.parseBoolean(props.getProperty("db.enabled", "true"));
    }

    private static void registerDriver() {
        try {
            // MariaDB Connector/J understands both jdbc:mariadb: and jdbc:mysql: URLs,
            // so it works against either a MySQL or MariaDB/XAMPP server.
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            enabled = false;
            warnOnce("MariaDB JDBC driver not found on the classpath - database sync is DISABLED. "
                    + "TutorApp will keep running normally using in-memory storage only. "
                    + "To enable database sync, add lib/mariadb-java-client.jar to your classpath "
                    + "(in IntelliJ: right-click the jar -> Add as Library; from a terminal: "
                    + "java -cp \"out:lib/mariadb-java-client.jar\" com.tutorapp.Main).");
        }
    }

    public static boolean isAvailable() {
        if (!enabled) return false;
        Boolean cached = available;
        if (cached != null) return cached;

        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            available = true;
            System.out.println("[TutorApp/Database] Connected to " + url
                    + " - database sync is ENABLED. Every register/hire/pay/etc. will now also be written here.");
        } catch (SQLException e) {
            available = false;
            warnOnce("Could not connect to MySQL/MariaDB at " + url + " (" + e.getMessage() + "). "
                    + "TutorApp will keep running normally using in-memory storage only. "
                    + "To enable database sync, start MySQL/MariaDB, import database/schema.sql, "
                    + "and check the settings in db.properties.");
        }
        return available;
    }

    // Opens a new connection. Caller is responsible for closing it. 
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void warnOnce(String message) {
        if (!warnedOnce) {
            warnedOnce = true;
            System.out.println("[TutorApp/Database] " + message);
        }
    }
}
