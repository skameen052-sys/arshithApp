
package com.example.arshithconnectattendance.chatnew.repository;

import com.example.arshithconnectattendance.chatnew.model.ChatUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ChatUserRepository {

    public interface ProfileCallback {
        void onSuccess(ChatUser user);
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public ChatUserRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public void loadOrCreateProfile(ProfileCallback callback) {

        if (callback == null) return;

        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            callback.onError("No signed-in Firebase user found.");
            return;
        }

        String uid = firebaseUser.getUid();

        DocumentReference chatProfileRef = firestore
                .collection("chat_users")
                .document(uid);

        chatProfileRef.get()
                .addOnSuccessListener(chatSnapshot -> {

                    if (chatSnapshot.exists()) {
                        ChatUser user = chatSnapshot.toObject(ChatUser.class);

                        if (user == null) {
                            callback.onError("Unable to read chat profile.");
                            return;
                        }

                        // Refresh the photo from the existing employee profile.
                        loadExistingEmployeePhoto(
                                firebaseUser,
                                chatProfileRef,
                                user,
                                callback
                        );

                    } else {
                        createProfileFromEmployeeData(
                                firebaseUser,
                                chatProfileRef,
                                callback
                        );
                    }
                })
                .addOnFailureListener(e ->
                        callback.onError(
                                "Chat profile loading failed: " + e.getMessage()
                        )
                );
    }

    private void createProfileFromEmployeeData(
            FirebaseUser firebaseUser,
            DocumentReference chatProfileRef,
            ProfileCallback callback
    ) {

        String uid = firebaseUser.getUid();

        firestore.collection("employees")
                .document(uid)
                .get()
                .addOnSuccessListener(employeeSnapshot -> {

                    String name = employeeSnapshot.getString("name");

                    if (name == null || name.trim().isEmpty()) {
                        name = firebaseUser.getDisplayName();
                    }

                    if (name == null || name.trim().isEmpty()) {
                        String email = firebaseUser.getEmail();
                        name = email != null && email.contains("@")
                                ? email.substring(0, email.indexOf("@"))
                                : "User";
                    }

                    String email = firebaseUser.getEmail();
                    String photoUrl = employeeSnapshot.getString("photoUrl");

                    if (photoUrl == null || photoUrl.trim().isEmpty()) {
                        photoUrl = firebaseUser.getPhotoUrl() != null
                                ? firebaseUser.getPhotoUrl().toString()
                                : "";
                    }

                    Map<String, Object> profile = new HashMap<>();
                    profile.put("uid", uid);
                    profile.put("name", name);
                    profile.put("email", email != null ? email : "");
                    profile.put("photoUrl", photoUrl);
                    profile.put("about", "Available");
                    profile.put("accountStatus", "active");
                    profile.put("createdAt", FieldValue.serverTimestamp());
                    profile.put("updatedAt", FieldValue.serverTimestamp());

                    chatProfileRef.set(profile)
                            .addOnSuccessListener(unused ->
                                    chatProfileRef.get()
                                            .addOnSuccessListener(snapshot -> {
                                                ChatUser user =
                                                        snapshot.toObject(ChatUser.class);

                                                if (user != null) {
                                                    callback.onSuccess(user);
                                                } else {
                                                    callback.onError(
                                                            "Chat profile created but could not be loaded."
                                                    );
                                                }
                                            })
                                            .addOnFailureListener(e ->
                                                    callback.onError(
                                                            "Profile reload failed: " + e.getMessage()
                                                    )
                                            )
                            )
                            .addOnFailureListener(e ->
                                    callback.onError(
                                            "Chat profile creation failed: " + e.getMessage()
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(
                                "Employee profile loading failed: " + e.getMessage()
                        )
                );
    }

    private void loadExistingEmployeePhoto(
            FirebaseUser firebaseUser,
            DocumentReference chatProfileRef,
            ChatUser user,
            ProfileCallback callback
    ) {

        firestore.collection("employees")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(employeeSnapshot -> {

                    String photoUrl = employeeSnapshot.getString("photoUrl");

                    if (photoUrl == null || photoUrl.trim().isEmpty()) {
                        callback.onSuccess(user);
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("photoUrl", photoUrl);
                    updates.put("updatedAt", FieldValue.serverTimestamp());

                    chatProfileRef.update(updates)
                            .addOnSuccessListener(unused -> {
                                user.setPhotoUrl(photoUrl);
                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError(
                                            "Unable to update chat profile photo: "
                                                    + e.getMessage()
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(
                                "Unable to read employee photo: " + e.getMessage()
                        )
                );
    }
}
