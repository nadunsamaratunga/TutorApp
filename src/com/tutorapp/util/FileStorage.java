package com.tutorapp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class FileStorage {
    private FileStorage() {}

    // Saves the file under web/uploads/{subfolder}/ and returns its public "/uploads/..." URL. 
    public static String save(HttpUtil.UploadedFile file, String subfolder) throws IOException {
        Path dir = ProjectPaths.findProjectRoot().resolve("web").resolve("uploads").resolve(subfolder);
        Files.createDirectories(dir);

        String storedName = UUID.randomUUID() + "_" + sanitize(file.filename);
        Path dest = dir.resolve(storedName);
        Files.write(dest, file.data);

        return "/uploads/" + subfolder + "/" + storedName;
    }

    private static String sanitize(String originalFilename) {
        String name = originalFilename.replaceAll("[\\\\/]", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "file" : name;
    }
}
