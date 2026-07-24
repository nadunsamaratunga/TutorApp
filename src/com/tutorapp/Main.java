package com.tutorapp;

import com.sun.net.httpserver.HttpServer;
import com.tutorapp.handler.*;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new HomeHandler());
        server.createContext("/register", new AuthHandler());
        server.createContext("/login", new AuthHandler());
        server.createContext("/admin/login", new AuthHandler());
        server.createContext("/logout", new AuthHandler());

        server.createContext("/static/", new StaticFileHandler());
        server.createContext("/uploads/", new UploadHandler());
        server.createContext("/profile", new ProfileHandler());
        server.createContext("/student/", new StudentHandler());
        server.createContext("/tutor/", new TutorHandler());
        server.createContext("/admin/", new AdminHandler());
        server.createContext("/payment/", new PaymentHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("TutorApp running at http://localhost:" + port);
        System.out.println("Admin login available at /admin/login (see DataStore.java for seeded credentials)");
    }
}
