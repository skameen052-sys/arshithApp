
package com.example.arshithconnectattendance.chatnew.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class ChatPresenceRepository {

    public interface PresenceCallback {
        void onPresenceChanged(boolean isOnline, long lastSeen);
        void onError(String message);
    }

    public interface TypingCallback {
        void onTypingChanged(boolean isTyping);
        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public ChatPresenceRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void setOnline(UpdateCallback callback) {
        updatePresence(true, callback);
    }

    public void setOffline(UpdateCallback callback) {
        updatePresence(false, callback);
    }

    private void updatePresence(boolean online, UpdateCallback callback) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            if (callback != null) {
                callback.onError("User is not signed in.");
            }
            return;
        }

        String uid = currentUser.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("online", online);
        data.put("updatedAt", FieldValue.serverTimestamp());

        if (!online) {
            data.put("lastSeen", FieldValue.serverTimestamp());
            data.put("typing", false);
            data.put("typingConversationId", "");
        }

        db.collection("chat_presence")
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        callback.onError(error.getMessage() == null
                                ? "Unable to update presence."
                                : error.getMessage());
                    }
                });
    }

    public void setTyping(
            String conversationId,
            boolean typing,
            UpdateCallback callback
    ) {

        FirebaseUser currentUser = auth.getCurrentUser();

        Log.d("CHAT_TYPING", "setTyping() called");
        Log.d("CHAT_TYPING", "Current user = " +
                (currentUser != null ? currentUser.getUid() : "NULL"));
        Log.d("CHAT_TYPING", "Conversation ID = " + conversationId);
        Log.d("CHAT_TYPING", "Typing value = " + typing);

        if (currentUser == null) {
            Log.e("CHAT_TYPING", "User is not signed in.");

            if (callback != null) {
                callback.onError("User is not signed in.");
            }
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            Log.e("CHAT_TYPING", "Invalid conversation ID.");

            if (callback != null) {
                callback.onError("Invalid conversation ID.");
            }
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("uid", currentUser.getUid());
        data.put("typing", typing);
        data.put("typingConversationId", typing ? conversationId : "");
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chat_presence")
                .document(currentUser.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    Log.d("CHAT_TYPING",
                            "SUCCESS: Typing status updated to " + typing);

                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(error -> {

                    Log.e("CHAT_TYPING",
                            "FAILED to update typing status", error);

                    if (callback != null) {
                        callback.onError(error.getMessage() == null
                                ? "Unable to update typing status."
                                : error.getMessage());
                    }
                });
    }

    public ListenerRegistration listenToTyping(
            String userId,
            String conversationId,
            TypingCallback callback
    ) {

        if (userId == null || userId.trim().isEmpty()
                || conversationId == null || conversationId.trim().isEmpty()) {

            if (callback != null) {
                callback.onError("Invalid user or conversation ID.");
            }
            return null;
        }

        return db.collection("chat_presence")
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onTypingChanged(false);
                        return;
                    }

                    Boolean typingValue = snapshot.getBoolean("typing");
                    String typingChat =
                            snapshot.getString("typingConversationId");

                    boolean isTyping =
                            Boolean.TRUE.equals(typingValue)
                                    && conversationId.equals(typingChat);

                    callback.onTypingChanged(isTyping);
                });
    }

    public ListenerRegistration listenToPresence(
            String userId,
            PresenceCallback callback
    ) {

        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("Invalid employee ID.");
            return null;
        }

        return db.collection("chat_presence")
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onPresenceChanged(false, 0);
                        return;
                    }

                    Boolean onlineValue = snapshot.getBoolean("online");
                    boolean online =
                            onlineValue != null && onlineValue;

                    long lastSeen = 0;

                    if (snapshot.contains("lastSeen")) {
                        Object value = snapshot.get("lastSeen");

                        if (value instanceof com.google.firebase.Timestamp) {
                            lastSeen = ((com.google.firebase.Timestamp) value)
                                    .toDate()
                                    .getTime();
                        }
                    }

                    callback.onPresenceChanged(online, lastSeen);
                });
    }
}
