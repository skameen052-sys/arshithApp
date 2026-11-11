package com.example.arshithconnectattendance.chatnew.repository;

import com.example.arshithconnectattendance.chatnew.model.ChatConversation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatConversationRepository {

    public interface ConversationCallback {

        void onSuccess(ChatConversation conversation);

        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public ChatConversationRepository() {

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public void getOrCreateDirectConversation(
            String otherUserId,
            ConversationCallback callback
    ) {

        if (callback == null) {
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (otherUserId == null || otherUserId.trim().isEmpty()) {
            callback.onError("Invalid recipient user ID.");
            return;
        }

        String myUid = currentUser.getUid();
        String recipientUid = otherUserId.trim();

        if (myUid.equals(recipientUid)) {
            callback.onError(
                    "You cannot start a conversation with yourself."
            );
            return;
        }

        // Both users must generate the same conversation ID.
        List<String> memberIds = new ArrayList<>(
                Arrays.asList(myUid, recipientUid)
        );

        Collections.sort(memberIds);

        String conversationId =
                "direct_" + memberIds.get(0) + "_" + memberIds.get(1);

        DocumentReference conversationRef = firestore
                .collection("chat_conversations")
                .document(conversationId);

        DocumentReference myProfileRef = firestore
                .collection("chat_users")
                .document(myUid);

        DocumentReference recipientEmployeeRef = firestore
                .collection("employees")
                .document(recipientUid);

        firestore.runTransaction(transaction -> {

            DocumentSnapshot conversationSnapshot =
                    transaction.get(conversationRef);

            DocumentSnapshot myProfileSnapshot =
                    transaction.get(myProfileRef);

            DocumentSnapshot recipientEmployeeSnapshot =
                    transaction.get(recipientEmployeeRef);

            // The signed-in user must have a chat profile.
            if (!myProfileSnapshot.exists()) {

                throw new FirebaseFirestoreException(
                        "Your chat profile was not found. Please reopen Chat.",
                        FirebaseFirestoreException.Code.NOT_FOUND
                );
            }

            String myStatus =
                    myProfileSnapshot.getString("accountStatus");

            if (!"active".equalsIgnoreCase(myStatus)) {

                throw new FirebaseFirestoreException(
                        "Your chat account is not active.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                );
            }

            // Recipient must exist in the employee directory.
            if (!recipientEmployeeSnapshot.exists()) {

                throw new FirebaseFirestoreException(
                        "Recipient employee was not found.",
                        FirebaseFirestoreException.Code.NOT_FOUND
                );
            }

            String recipientStatus =
                    recipientEmployeeSnapshot.getString("status");

            if (!"approved".equalsIgnoreCase(recipientStatus)) {

                throw new FirebaseFirestoreException(
                        "This employee is not approved for chat.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                );
            }

            // Check existing conversation membership.
            if (conversationSnapshot.exists()) {

                List<String> existingMembers =
                        (List<String>) conversationSnapshot.get("memberIds");

                if (existingMembers == null
                        || !existingMembers.contains(myUid)
                        || !existingMembers.contains(recipientUid)) {

                    throw new FirebaseFirestoreException(
                            "Conversation membership is invalid.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                    );
                }

                return conversationId;
            }

            // Create a new direct conversation.
            Map<String, Object> conversation =
                    new HashMap<>();

            conversation.put("conversationId", conversationId);
            conversation.put("type", "direct");
            conversation.put("memberIds", memberIds);
            conversation.put("createdBy", myUid);
            conversation.put("lastMessage", "");
            conversation.put("lastMessageType", "text");
            conversation.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );
            conversation.put(
                    "lastMessageAt",
                    FieldValue.serverTimestamp()
            );

            transaction.set(conversationRef, conversation);

            return conversationId;

        }).addOnSuccessListener(createdConversationId -> {

            conversationRef.get()
                    .addOnSuccessListener(snapshot -> {

                        ChatConversation conversation =
                                snapshot.toObject(ChatConversation.class);

                        if (conversation != null) {

                            callback.onSuccess(conversation);

                        } else {

                            callback.onError(
                                    "Conversation was created but could not be loaded."
                            );
                        }
                    })
                    .addOnFailureListener(e ->
                            callback.onError(
                                    "Unable to load conversation: "
                                            + e.getMessage()
                            )
                    );

        }).addOnFailureListener(e -> {

            callback.onError(
                    "Unable to create conversation: "
                            + e.getMessage()
            );
        });
    }

    public void createGroupConversation(
            String groupName,
            List<String> selectedMemberIds,
            String groupImage,
            ConversationCallback callback
    ) {

        if (callback == null) {
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (groupName == null || groupName.trim().isEmpty()) {
            callback.onError("Please enter a group name.");
            return;
        }

        String myUid = currentUser.getUid();

        // Include the creator automatically.
        List<String> memberIds = new ArrayList<>();

        memberIds.add(myUid);

        if (selectedMemberIds != null) {
            for (String uid : selectedMemberIds) {

                if (uid != null
                        && !uid.trim().isEmpty()
                        && !memberIds.contains(uid.trim())) {

                    memberIds.add(uid.trim());
                }
            }
        }

        if (memberIds.size() < 2) {
            callback.onError(
                    "Select at least one other employee to create a group."
            );
            return;
        }

        String conversationId = firestore
                .collection("chat_conversations")
                .document()
                .getId();

        DocumentReference conversationRef = firestore
                .collection("chat_conversations")
                .document(conversationId);

        DocumentReference myProfileRef = firestore
                .collection("chat_users")
                .document(myUid);

        // Prepare employee references for validation.
        List<DocumentReference> employeeRefs = new ArrayList<>();

        for (String memberId : memberIds) {
            if (!memberId.equals(myUid)) {
                employeeRefs.add(
                        firestore.collection("employees")
                                .document(memberId)
                );
            }
        }

        firestore.runTransaction(transaction -> {

            // All reads must happen before writes.
            DocumentSnapshot myProfile =
                    transaction.get(myProfileRef);

            if (!myProfile.exists()) {
                throw new FirebaseFirestoreException(
                        "Your chat profile was not found.",
                        FirebaseFirestoreException.Code.NOT_FOUND
                );
            }

            String accountStatus =
                    myProfile.getString("accountStatus");

            if (!"active".equalsIgnoreCase(accountStatus)) {
                throw new FirebaseFirestoreException(
                        "Your chat account is not active.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                );
            }

            // Validate every selected employee.
            for (DocumentReference employeeRef : employeeRefs) {

                DocumentSnapshot employee =
                        transaction.get(employeeRef);

                if (!employee.exists()) {
                    throw new FirebaseFirestoreException(
                            "One of the selected employees was not found.",
                            FirebaseFirestoreException.Code.NOT_FOUND
                    );
                }

                String status = employee.getString("status");

                if (!"approved".equalsIgnoreCase(status)) {
                    throw new FirebaseFirestoreException(
                            "All group members must be approved employees.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                    );
                }
            }

            // Initialize unread counts for every member.
            Map<String, Object> unreadCounts = new HashMap<>();

            for (String memberId : memberIds) {
                unreadCounts.put(memberId, 0);
            }

            Map<String, Object> groupData = new HashMap<>();

            groupData.put("conversationId", conversationId);
            groupData.put("type", "group");
            groupData.put("groupName", groupName.trim());
            groupData.put(
                    "groupImage",
                    groupImage == null ? "" : groupImage.trim()
            );
            groupData.put("memberIds", memberIds);

            // Creator is the initial group admin.
            groupData.put(
                    "adminIds",
                    Collections.singletonList(myUid)
            );

            groupData.put("createdBy", myUid);
            groupData.put("lastMessage", "");
            groupData.put("lastMessageType", "text");
            groupData.put("lastSenderId", "");
            groupData.put("unreadCounts", unreadCounts);

            groupData.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );

            groupData.put(
                    "lastMessageAt",
                    FieldValue.serverTimestamp()
            );

            transaction.set(conversationRef, groupData);

            return conversationId;

        }).addOnSuccessListener(createdId -> {

            conversationRef.get()
                    .addOnSuccessListener(snapshot -> {

                        ChatConversation conversation =
                                snapshot.toObject(ChatConversation.class);

                        if (conversation != null) {
                            callback.onSuccess(conversation);
                        } else {
                            callback.onError(
                                    "Group created but could not be loaded."
                            );
                        }
                    })
                    .addOnFailureListener(error ->
                            callback.onError(
                                    "Unable to load group: "
                                            + error.getMessage()
                            )
                    );

        }).addOnFailureListener(error -> {

            callback.onError(
                    "Unable to create group: "
                            + error.getMessage()
            );
        });
    }

}