
package com.example.arshithconnectattendance.chatnew.repository;

import com.example.arshithconnectattendance.chatnew.model.ChatUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatDirectoryRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public ChatDirectoryRepository() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface DirectoryCallback {
        void onSuccess(List<ChatUser> users);
        void onError(Exception error);
    }

    public void loadActiveUsers(DirectoryCallback callback) {

        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("User is not logged in."));
            return;
        }

        String currentUid = auth.getCurrentUser().getUid();

        firestore.collection("employees")
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<ChatUser> users = new ArrayList<>();

                    for (DocumentSnapshot document : snapshot.getDocuments()) {

                        String uid = document.getString("uid");

                        if (uid == null || uid.trim().isEmpty()) {
                            uid = document.getId();
                        }

                        if (uid.equals(currentUid)) {
                            continue;
                        }

                        ChatUser user = new ChatUser();
                        user.setUid(uid);

                        String name = document.getString("name");
                        user.setName(
                                name != null && !name.trim().isEmpty()
                                        ? name : "Employee"
                        );

                        String email = document.getString("email");
                        user.setEmail(email != null ? email : "");

                        String photoUrl = document.getString("photoUrl");
                        user.setPhotoUrl(photoUrl != null ? photoUrl : "");

                        user.setAbout("Available");
                        user.setAccountStatus("active");

                        users.add(user);
                    }

                    users.sort(Comparator.comparing(
                            user -> user.getName() == null
                                    ? ""
                                    : user.getName().toLowerCase()
                    ));

                    callback.onSuccess(users);

                })
                .addOnFailureListener(callback::onError);
    }
}
