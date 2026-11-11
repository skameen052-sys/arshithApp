
package com.example.arshithconnectattendance.chatnew.repository;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChatReadRepository {

    private static final String TAG = "CHAT_READ_DEBUG";

    public interface ReadCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public ChatReadRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void markConversationAsRead(
            String conversationId,
            ReadCallback callback
    ) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            reportError(callback, "User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            reportError(callback, "Invalid conversation ID.");
            return;
        }

        String uid = currentUser.getUid();

        DocumentReference conversationRef = db
                .collection("chat_conversations")
                .document(conversationId);

        Log.d(TAG, "==================================");
        Log.d(TAG, "Marking conversation as read");
        Log.d(TAG, "Conversation ID: " + conversationId);
        Log.d(TAG, "Current UID: " + uid);
        Log.d(TAG, "Collection: chat_conversations");
        Log.d(TAG, "Document path: chat_conversations/" + conversationId);
        Log.d(TAG, "Unread field: unreadCounts." + uid);
        Log.d(TAG, "==================================");

        // First verify that the conversation exists and this user is a member.
        conversationRef.get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        reportError(callback,
                                "Conversation document does not exist.");
                        return;
                    }

                    if (!document.contains("memberIds")) {
                        reportError(callback,
                                "Conversation has no memberIds field.");
                        return;
                    }

                    Object membersObject = document.get("memberIds");

                    if (!(membersObject instanceof java.util.List)) {
                        reportError(callback,
                                "Conversation memberIds field is invalid.");
                        return;
                    }

                    java.util.List<?> members =
                            (java.util.List<?>) membersObject;

                    if (!members.contains(uid)) {
                        reportError(callback,
                                "Current user is not a member of this conversation.");
                        Log.e(TAG, "UID is not present in memberIds: " + uid);
                        return;
                    }

                    Object unreadObject =
                            document.get("unreadCounts." + uid);

                    Log.d(TAG, "Document exists: true");
                    Log.d(TAG, "User is a member: true");
                    Log.d(TAG, "Unread count before update: "
                            + unreadObject);

                    // Clear this user's unread count and update lastReadAt.
                    conversationRef.update(
                            FieldPath.of("unreadCounts", uid),
                            0,
                            FieldPath.of("lastReadAt", uid),
                            FieldValue.serverTimestamp()
                    ).addOnSuccessListener(unused -> {

                        Log.d(TAG,
                                "SUCCESS: Conversation read update completed.");
                        Log.d(TAG,
                                "Updated UID: " + uid);

                        // Read the document again to verify the saved value.
                        conversationRef.get()
                                .addOnSuccessListener(updatedDocument -> {

                                    Object newUnreadCount =
                                            updatedDocument.get(
                                                    "unreadCounts." + uid
                                            );

                                    Object lastReadAt =
                                            updatedDocument.get(
                                                    "lastReadAt." + uid
                                            );

                                    Log.d(TAG,
                                            "Unread count after update: "
                                                    + newUnreadCount);

                                    Log.d(TAG,
                                            "Last read timestamp: "
                                                    + lastReadAt);

                                    if (newUnreadCount instanceof Number
                                            && ((Number) newUnreadCount)
                                            .intValue() == 0) {

                                        Log.d(TAG,
                                                "VERIFIED: Receiver unread count is zero.");

                                        if (callback != null) {
                                            callback.onSuccess();
                                        }

                                    } else {
                                        Log.e(TAG,
                                                "WARNING: Unread count is not zero after update.");

                                        if (callback != null) {
                                            callback.onError(
                                                    "Read update completed, but unread count is not zero."
                                            );
                                        }
                                    }

                                })
                                .addOnFailureListener(error -> {
                                    Log.e(TAG,
                                            "Unable to verify updated unread count.",
                                            error);

                                    if (callback != null) {
                                        callback.onError(
                                                error.getMessage() == null
                                                        ? "Unable to verify read status."
                                                        : error.getMessage()
                                        );
                                    }
                                });

                    }).addOnFailureListener(error -> {

                        Log.e(TAG,
                                "FAILED: Could not update conversation read status.",
                                error);

                        if (callback != null) {
                            callback.onError(
                                    error.getMessage() == null
                                            ? "Unable to update read status."
                                            : error.getMessage()
                            );
                        }
                    });

                })
                .addOnFailureListener(error -> {

                    Log.e(TAG,
                            "FAILED: Could not read conversation document.",
                            error);

                    if (callback != null) {
                        callback.onError(
                                error.getMessage() == null
                                        ? "Unable to check conversation."
                                        : error.getMessage()
                        );
                    }
                });
    }

    private void reportError(
            ReadCallback callback,
            String message
    ) {

        Log.e(TAG, message);

        if (callback != null) {
            callback.onError(message);
        }
    }
}
