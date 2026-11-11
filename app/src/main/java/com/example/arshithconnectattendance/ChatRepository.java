
package com.example.arshithconnectattendance;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ChatRepository {

    private static final String TAG = "CHAT_DEBUG";
    private static final String CONVERSATIONS = "conversations";
    private static final String MESSAGES = "messages";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public interface ResultCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    public interface ConversationsCallback {
        void onUpdate(List<ConversationItem> conversations);
        void onError(String error);
    }

    public interface MessagesCallback {
        void onUpdate(List<Message> messages);
        void onError(String error);
    }

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private String uid() {
        return auth.getCurrentUser() == null
                ? ""
                : auth.getCurrentUser().getUid();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // =====================================================
    // CREATE OR GET DIRECT CONVERSATION
    // =====================================================

    public void createOrGetDirectConversation(
            Contact contact,
            String currentName,
            String currentBusinessId,
            ResultCallback<String> callback
    ) {

        String currentUid = uid();

        if (currentUid.isEmpty()) {
            callback.onFailure("Please sign in again.");
            return;
        }

        if (contact == null || safe(contact.getAuthUid()).isEmpty()) {
            callback.onFailure("Invalid recipient account.");
            return;
        }

        String otherUid = safe(contact.getAuthUid());

        if (currentUid.equals(otherUid)) {
            callback.onFailure("You cannot chat with yourself.");
            return;
        }

        List<String> members = new ArrayList<>();
        members.add(currentUid);
        members.add(otherUid);
        Collections.sort(members);

        String conversationId =
                "direct_" + members.get(0) + "_" + members.get(1);

        DocumentReference conversationRef =
                db.collection(CONVERSATIONS).document(conversationId);

        Map<String, Object> memberNames = new HashMap<>();
        memberNames.put(currentUid, safe(currentName));
        memberNames.put(otherUid, safe(contact.getName()));

        Map<String, Object> businessIds = new HashMap<>();
        businessIds.put(currentUid, safe(currentBusinessId));
        businessIds.put(otherUid, safe(contact.getUid()));

        Map<String, Object> unreadCounts = new HashMap<>();
        unreadCounts.put(currentUid, 0);
        unreadCounts.put(otherUid, 0);

        db.runTransaction(transaction -> {

            DocumentSnapshot existing =
                    transaction.get(conversationRef);

            if (!existing.exists()) {

                Map<String, Object> data = new HashMap<>();

                data.put("type", "direct");
                data.put("memberIds", members);
                data.put("memberNames", memberNames);
                data.put("memberBusinessIds", businessIds);
                data.put("createdBy", currentUid);
                data.put("createdAt", Timestamp.now());
                data.put("lastMessage", "");
                data.put("lastMessageTime", Timestamp.now());
                data.put("lastSenderId", "");
                data.put("unreadCounts", unreadCounts);

                transaction.set(conversationRef, data);
            }

            return conversationId;

        }).addOnSuccessListener(id -> {
            callback.onSuccess(id);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Create direct conversation failed", e);
            callback.onFailure(e.getMessage());
        });
    }

    // =====================================================
    // LISTEN TO CONVERSATIONS
    // =====================================================

    public ListenerRegistration listenToConversations(
            ConversationsCallback callback
    ) {

        String currentUid = uid();

        if (currentUid.isEmpty()) {
            callback.onError("User is not logged in.");
            return null;
        }

        Log.d(TAG, "======================================");
        Log.d(TAG, "Starting conversation listener");
        Log.d(TAG, "Logged-in UID: " + currentUid);
        Log.d(TAG, "Firebase Project: "
                + db.getApp().getOptions().getProjectId());
        Log.d(TAG, "Query: conversations where memberIds contains current UID");
        Log.d(TAG, "======================================");

        return db.collection(CONVERSATIONS)
                .whereArrayContains("memberIds", currentUid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        Log.e(TAG, "Conversation listener error", error);
                        callback.onError(error.getMessage());
                        return;
                    }

                    if (snapshot == null) {
                        Log.w(TAG, "Conversation snapshot is null");
                        return;
                    }

                    Log.d(TAG, "Conversation snapshot count: "
                            + snapshot.size());

                    List<ConversationItem> result = new ArrayList<>();

                    // First loop: DEBUG ONLY.
                    // This logs every document returned by Firestore.
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Log.d(TAG,
                                "Found conversation: " + doc.getId()
                                        + " | Type: " + doc.getString("type")
                                        + " | Members: " + doc.get("memberIds"));
                    }

                    // Second loop: BUILD THE CONVERSATION LIST.
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        List<String> memberIds =
                                stringList(doc.get("memberIds"));

                        // Only include conversations belonging to this user.
                        if (!memberIds.contains(currentUid)) {
                            Log.w(TAG, "Skipping conversation without current UID: "
                                    + doc.getId());
                            continue;
                        }

                        Map<String, Object> names =
                                objectMap(doc.get("memberNames"));

                        Map<String, Object> businessIds =
                                objectMap(doc.get("memberBusinessIds"));

                        String type = safe(doc.getString("type"))
                                .toLowerCase(Locale.ROOT);

                        if (type.equals("team") || type.equals("teams")) {
                            type = "group";
                        }

                        if (!type.equals("direct") && !type.equals("group")) {
                            Log.w(TAG, "Skipping unsupported conversation type: "
                                    + doc.getId() + " | " + type);
                            continue;
                        }

                        String title = safe(doc.getString("name"));
                        String subtitle = "";
                        String targetId = doc.getId();

                        if ("direct".equals(type)) {

                            String otherUid = "";

                            for (String memberId : memberIds) {
                                if (!currentUid.equals(memberId)) {
                                    otherUid = memberId;
                                    break;
                                }
                            }

                            Object otherName = names.get(otherUid);

                            title = otherName == null
                                    ? ""
                                    : safe(String.valueOf(otherName));

                            Object otherBusinessId = businessIds.get(otherUid);

                            targetId = otherBusinessId == null
                                    ? ""
                                    : safe(String.valueOf(otherBusinessId));

                            if (targetId.isEmpty()) {
                                targetId = otherUid;
                            }

                            if (title.isEmpty()) {
                                title = "Direct Message";
                            }

                            subtitle = "Direct message";

                        } else {

                            if (title.isEmpty()) {
                                title = "Group";
                            }

                            subtitle = memberIds.size() + " members";
                        }

                        String lastMessage =
                                safe(doc.getString("lastMessage"));

                        if (lastMessage.isEmpty()) {
                            lastMessage = "No messages yet";
                        }

                        Timestamp timestamp =
                                doc.getTimestamp("lastMessageTime");

                        long updatedAt = timestamp != null
                                ? timestamp.toDate().getTime()
                                : 0L;

                        Long unreadValue = 0L;

                        Map<String, Object> unreadCounts =
                                objectMap(doc.get("unreadCounts"));

                        Object unreadObject = unreadCounts.get(currentUid);

                        if (unreadObject instanceof Number) {
                            unreadValue =
                                    ((Number) unreadObject).longValue();
                        }

                        ConversationItem item = new ConversationItem(
                                doc.getId(),
                                type,
                                targetId,
                                title,
                                subtitle,
                                lastMessage,
                                unreadValue.intValue(),
                                updatedAt
                        );

                        item.conversationId = doc.getId();
                        item.targetId = targetId;
                        item.type = type;
                        item.title = title;
                        item.subtitle = subtitle;
                        item.lastMessage = lastMessage;
                        item.unreadCount = unreadValue.intValue();
                        item.updatedAt = updatedAt;

                        result.add(item);

                        Log.d(TAG,
                                "Added conversation: " + item.conversationId
                                        + " | Type: " + item.type
                                        + " | Title: " + item.title
                                        + " | Target: " + item.targetId
                                        + " | Unread: " + item.unreadCount);
                    }

                    result.sort((a, b) ->
                            Long.compare(b.updatedAt, a.updatedAt));

                    Log.d(TAG, "Final conversation count: " + result.size());
                    Log.d(TAG, "Conversation listener update completed");

                    callback.onUpdate(result);
                });
    }

    // =====================================================
    // LISTEN TO MESSAGES
    // =====================================================

    public ListenerRegistration listenToMessages(
            String conversationId,
            MessagesCallback callback
    ) {

        if (uid().isEmpty()) {
            callback.onError("Please sign in again.");
            return null;
        }

        if (safe(conversationId).isEmpty()) {
            callback.onError("Invalid conversation.");
            return null;
        }

        return db.collection(CONVERSATIONS)
                .document(conversationId)
                .collection(MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    List<Message> messages = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        Message message = new Message();

                        message.setId(doc.getId());
                        message.setConversationId(conversationId);

                        message.setSenderId(
                                safe(doc.getString("senderId"))
                        );

                        message.setSenderAuthUid(
                                safe(doc.getString("senderAuthUid"))
                        );

                        message.setSenderName(
                                safe(doc.getString("senderName"))
                        );

                        message.setSenderRole(
                                safe(doc.getString("senderRole"))
                        );

                        message.setContent(
                                safe(doc.getString("content"))
                        );

                        message.setChatType(
                                safe(doc.getString("chatType"))
                        );

                        message.setMessageType(
                                safe(doc.getString("messageType"))
                        );

                        Timestamp timestamp = doc.getTimestamp(
                                "timestamp",
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        );

                        if (timestamp != null) {
                            message.setCreatedAtMillis(
                                    timestamp.toDate().getTime()
                            );
                        }

                        message.setReadBy(
                                stringList(doc.get("readBy"))
                        );

                        message.setDeliveredTo(
                                stringList(doc.get("deliveredTo"))
                        );

                        message.setParticipantIds(
                                stringList(doc.get("participantIds"))
                        );

                        message.setTeamId(
                                safe(doc.getString("teamId"))
                        );

                        message.setTeamName(
                                safe(doc.getString("teamName"))
                        );

                        message.setPinned(
                                Boolean.TRUE.equals(doc.getBoolean("pinned"))
                        );

                        message.setEdited(
                                Boolean.TRUE.equals(doc.getBoolean("edited"))
                        );

                        message.setDeleted(
                                Boolean.TRUE.equals(doc.getBoolean("deleted"))
                        );

                        message.setReplyToMessageId(
                                safe(doc.getString("replyToMessageId"))
                        );

                        message.setReplyToSender(
                                safe(doc.getString("replyToSender"))
                        );

                        message.setReplyToContent(
                                safe(doc.getString("replyToContent"))
                        );

                        messages.add(message);
                    }

                    callback.onUpdate(messages);
                });
    }

    // =====================================================
    // SEND TEXT MESSAGE
    // =====================================================

    public void sendTextMessage(
            String conversationId,
            String content,
            String senderName,
            String senderRole,
            String chatType,
            List<String> memberIds,
            String replyMessageId,
            String replySender,
            String replyContent,
            ResultCallback<String> callback
    ) {

        String currentUid = uid();
        String text = safe(content);

        if (currentUid.isEmpty()) {
            callback.onFailure("Please sign in again.");
            return;
        }

        if (text.isEmpty()) {
            callback.onFailure("Message cannot be empty.");
            return;
        }

        DocumentReference conversationRef =
                db.collection(CONVERSATIONS)
                        .document(conversationId);

        DocumentReference messageRef =
                conversationRef.collection(MESSAGES).document();

        db.runTransaction(transaction -> {

            DocumentSnapshot conversation =
                    transaction.get(conversationRef);

            if (!conversation.exists()) {
                throw new RuntimeException("Conversation not found.");
            }

            List<String> members =
                    stringList(conversation.get("memberIds"));

            if (!members.contains(currentUid)) {
                throw new RuntimeException(
                        "You are not a member of this conversation."
                );
            }

            Map<String, Object> unread =
                    objectMap(conversation.get("unreadCounts"));

            Map<String, Object> updatedUnread =
                    new HashMap<>(unread);

            for (String member : members) {
                if (!member.equals(currentUid)) {
                    Object old = updatedUnread.get(member);

                    int count = old instanceof Number
                            ? ((Number) old).intValue()
                            : 0;

                    updatedUnread.put(member, count + 1);
                } else {
                    updatedUnread.put(member, 0);
                }
            }

            Map<String, Object> message = new HashMap<>();

            message.put("senderId", currentUid);
            message.put("senderAuthUid", currentUid);
            message.put("senderName", safe(senderName));
            message.put("senderRole", safe(senderRole));
            message.put("content", text);
            message.put("chatType", safe(chatType));
            message.put("messageType", "text");
            message.put("timestamp", Timestamp.now());
            message.put("readBy",
                    Collections.singletonList(currentUid));
            message.put("deliveredTo", new ArrayList<String>());
            message.put("participantIds", members);

            if (!safe(replyMessageId).isEmpty()) {
                message.put("replyToMessageId", replyMessageId);
                message.put("replyToSender", safe(replySender));
                message.put("replyToContent", safe(replyContent));
            }

            transaction.set(messageRef, message);

            Map<String, Object> conversationUpdate = new HashMap<>();

            conversationUpdate.put("lastMessage", text);
            conversationUpdate.put("lastMessageTime", Timestamp.now());
            conversationUpdate.put("lastSenderId", currentUid);
            conversationUpdate.put("unreadCounts", updatedUnread);

            transaction.update(conversationRef, conversationUpdate);

            return messageRef.getId();

        }).addOnSuccessListener(id -> {
            callback.onSuccess(id);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Send text message failed", e);
            callback.onFailure(e.getMessage());
        });
    }

    // =====================================================
    // MARK CONVERSATION AS READ
    // =====================================================

    public void markConversationAsRead(String conversationId) {

        String currentUid = uid();

        if (currentUid.isEmpty()
                || safe(conversationId).isEmpty()) {
            return;
        }

        DocumentReference conversationRef =
                db.collection(CONVERSATIONS)
                        .document(conversationId);

        db.collection(CONVERSATIONS)
                .document(conversationId)
                .collection(MESSAGES)
                .whereNotEqualTo("senderId", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    com.google.firebase.firestore.WriteBatch batch =
                            db.batch();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        List<String> readBy =
                                stringList(doc.get("readBy"));

                        if (!readBy.contains(currentUid)) {

                            batch.update(
                                    doc.getReference(),
                                    "readBy",
                                    FieldValue.arrayUnion(currentUid)
                            );
                        }
                    }

                    batch.update(
                            conversationRef,
                            "unreadCounts." + currentUid,
                            0
                    );

                    batch.commit().addOnFailureListener(e ->
                            Log.e(TAG, "Mark conversation as read failed", e));
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Load messages for read status failed", e));
    }
    public void debugDirectConversation() {

        String conversationId =
                "direct_Rv0fCllzm8MFn9D5C5p6mnasawK2_lQmFmzADTfRVjW49HkswaZyyuot1";

        String currentUid = uid();

        Log.d(TAG, "Checking direct conversation");
        Log.d(TAG, "Current UID: " + currentUid);
        Log.d(TAG, "Conversation ID: " + conversationId);

        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(document -> {

                    Log.d(TAG, "Direct document exists: "
                            + document.exists());

                    if (document.exists()) {
                        Log.d(TAG, "Direct document data: "
                                + document.getData());

                        Log.d(TAG, "Current UID in memberIds: "
                                + stringList(document.get("memberIds"))
                                .contains(currentUid));
                    }
                })
                .addOnFailureListener(error ->
                        Log.e(TAG, "Direct document read failed", error));
    }

    // =====================================================
    // CREATE GROUP CONVERSATION
    // =====================================================

    public void createGroupConversation(
            String groupName,
            String description,
            String teamId,
            List<String> memberIds,
            Map<String, String> memberNames,
            Map<String, String> memberBusinessIds,
            ResultCallback<String> callback
    ) {

        String currentUid = uid();

        if (auth.getCurrentUser() == null || currentUid.isEmpty()) {
            callback.onFailure("Please sign in again.");
            return;
        }

        String cleanedGroupName = safe(groupName);

        if (cleanedGroupName.isEmpty()) {
            callback.onFailure("Enter a group name.");
            return;
        }

        if (memberIds == null || memberIds.isEmpty()) {
            callback.onFailure("Select at least one group member.");
            return;
        }

        Set<String> uniqueMemberSet = new TreeSet<>();

        for (String memberId : memberIds) {
            if (memberId != null && !memberId.trim().isEmpty()) {
                uniqueMemberSet.add(memberId.trim());
            }
        }

        // The creator is always a member.
        uniqueMemberSet.add(currentUid);

        if (uniqueMemberSet.size() < 2) {
            callback.onFailure(
                    "Select at least one other member to create a group."
            );
            return;
        }

        if (uniqueMemberSet.size() > 100) {
            callback.onFailure("A group can have at most 100 members.");
            return;
        }

        List<String> uniqueMembers = new ArrayList<>(uniqueMemberSet);

        Map<String, String> safeMemberNames =
                memberNames == null
                        ? new HashMap<>()
                        : new HashMap<>(memberNames);

        Map<String, String> safeMemberBusinessIds =
                memberBusinessIds == null
                        ? new HashMap<>()
                        : new HashMap<>(memberBusinessIds);

        String conversationId =
                "group_" + java.util.UUID.randomUUID();

        String cleanedTeamId = safe(teamId).isEmpty()
                ? "TEA-" + System.currentTimeMillis()
                : safe(teamId);

        Map<String, Object> unreadCounts = new HashMap<>();

        for (String member : uniqueMembers) {
            unreadCounts.put(member, 0);
        }

        // Conversation document.
        Map<String, Object> data = new HashMap<>();

        data.put("type", "group");
        data.put("name", cleanedGroupName);
        data.put("memberIds", uniqueMembers);
        data.put("memberNames", safeMemberNames);
        data.put("memberBusinessIds", safeMemberBusinessIds);
        data.put("createdBy", currentUid);
        data.put("createdAt", Timestamp.now());
        data.put("lastMessage", "");
        data.put("lastMessageTime", Timestamp.now());
        data.put("lastSenderId", "");
        data.put("unreadCounts", unreadCounts);
        data.put("teamId", cleanedTeamId);
        data.put("teamName", cleanedGroupName);

        // Team document.
        Map<String, Object> teamData = new HashMap<>();

        teamData.put("team_id", cleanedTeamId);
        teamData.put("name", cleanedGroupName);
        teamData.put("description", safe(description));
        teamData.put("memberIds", uniqueMembers);

        teamData.put(
                "memberBusinessIds",
                new ArrayList<>(safeMemberBusinessIds.values())
        );

        teamData.put(
                "memberNames",
                new ArrayList<>(safeMemberNames.values())
        );

        teamData.put("createdAt", Timestamp.now());
        teamData.put("createdBy", currentUid);
        teamData.put("active", true);
        teamData.put("conversation_id", conversationId);

        // Save both documents atomically.
        com.google.firebase.firestore.WriteBatch batch = db.batch();

        batch.set(
                db.collection(CONVERSATIONS).document(conversationId),
                data
        );

        batch.set(
                db.collection("teams").document(cleanedTeamId),
                teamData
        );

        batch.commit()
                .addOnSuccessListener(unused ->
                        callback.onSuccess(conversationId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Create group conversation failed", e);

                    String error = e.getMessage();

                    callback.onFailure(
                            error != null
                                    ? error
                                    : "Unable to create group. Please try again."
                    );
                });
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private List<String> stringList(Object value) {

        List<String> result = new ArrayList<>();

        if (value instanceof List<?>) {

            for (Object item : (List<?>) value) {

                if (item instanceof String) {
                    result.add((String) item);
                }
            }
        }

        return result;
    }

    private Map<String, Object> objectMap(Object value) {

        if (value instanceof Map<?, ?>) {

            Map<String, Object> result = new HashMap<>();

            for (Map.Entry<?, ?> entry :
                    ((Map<?, ?>) value).entrySet()) {

                if (entry.getKey() instanceof String) {
                    result.put(
                            (String) entry.getKey(),
                            entry.getValue()
                    );
                }
            }

            return result;
        }

        return new HashMap<>();
    }
}
