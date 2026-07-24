package com.tutorapp.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tutorapp.model.User;
import com.tutorapp.store.SqlPersistence;
import com.tutorapp.util.FileStorage;
import com.tutorapp.util.HttpUtil;
import com.tutorapp.util.Layout;
import com.tutorapp.util.SessionManager;

import java.io.IOException;

// Lets any logged-in user (student, tutor, or admin) upload a profile picture for their own account. Shared across roles since the underlying behavior - and the User.profilePictureUrl field it sets - is identicalfor all of them.
public class ProfileHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        User current = SessionManager.getCurrentUser(exchange);
        if (current == null) {
            HttpUtil.redirect(exchange, "/login");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/profile") && method.equals("GET")) profilePage(exchange, current, null);
        else if (path.equals("/profile/picture") && method.equals("POST")) uploadPicture(exchange, current);
        else HttpUtil.sendHtml(exchange, 404, Layout.page("Not found", "<h1>404</h1>", current, null));
    }

    private void profilePage(HttpExchange exchange, User user, String msg) throws IOException {
        StringBuilder body = new StringBuilder("<h1>My Profile</h1>");
        body.append("<div class='card'>");
        body.append(Layout.avatarHtml(user, "avatar-lg")).append("<br><br>");
        body.append("<p><b>").append(Layout.escape(user.getName())).append("</b><br>")
            .append("<span class='muted'>").append(Layout.escape(user.getEmail())).append(" &middot; ")
            .append(Layout.escape(user.getRole())).append("</span></p>");

        body.append("<form method='POST' action='/profile/picture' enctype='multipart/form-data'>")
            .append("<label>Profile Picture</label><input type='file' name='picture' accept='.png,.jpg,.jpeg,.gif,.webp' required>")
            .append("<button type='submit'>Upload Picture</button></form>");
        body.append("</div>");

        HttpUtil.sendHtml(exchange, 200, Layout.page("My Profile", body.toString(), user, msg));
    }

    private void uploadPicture(HttpExchange exchange, User user) throws IOException {
        HttpUtil.MultipartForm form = HttpUtil.parseMultipart(exchange);
        HttpUtil.UploadedFile picture = form.files.get("picture");
        if (picture == null || picture.isEmpty()) {
            profilePage(exchange, user, "Please choose an image to upload.");
            return;
        }
        String url = FileStorage.save(picture, "profile-pictures");
        user.setProfilePictureUrl(url);
        SqlPersistence.updateProfilePicture(user);
        HttpUtil.redirect(exchange, "/profile");
    }
}
