package com.example.arshithconnectattendance.chatnew.repository;

import com.example.arshithconnectattendance.chatnew.model.ChatMessage;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageRepository {

    public interface MessageCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface MessagesListener {
        void onMessagesChanged(List<ChatMessage> messages);
        void onError(String message);
    }

    public interface ClearTimeCallback {
        void onSuccess(Timestamp clearedAt);
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public ChatMessageRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // --------------------------------------------------
    // FIRESTORE REFERENCES
    // --------------------------------------------------

    private DocumentReference getConversationReference(
            String conversationId
    ) {
        return db.collection("chat_conversations")
                .document(conversationId);
    }

    private CollectionReference getMessagesCollection(
            String conversationId
    ) {
        return getConversationReference(conversationId)
                .collection("messages");
    }

    private void reportError(
            MessageCallback callback,
            Exception error,
            String fallback
    ) {
        callback.onError(
                error.getMessage() == null
                        ? fallback
                        : error.getMessage()
        );
    }

    // --------------------------------------------------
    // CREATE FIRESTORE MESSAGE DATA
    // --------------------------------------------------

    private Map<String, Object> buildMessageData(
            String conversationId,
            String senderUid,
            List<String> memberIds,
            String messageType,
            String messageText,
            String mediaUrl,
            ChatMessage replyTo,
            boolean forwarded,
            String forwardedFromSenderId,
            String forwardedFromMessageId,
            long duration
    ) {
        Map<String, Object> data = new HashMap<>();

        List<String> readBy = new ArrayList<>();
        readBy.add(senderUid);

        data.put("conversationId", conversationId);
        data.put("senderId", senderUid);
        data.put("senderAuthUid", senderUid);
        data.put("senderRole", "employee");
        data.put("chatType", "direct");

        data.put("content", messageText);
        data.put("messageType", messageType);
        data.put("timestamp", FieldValue.serverTimestamp());

        data.put("readBy", readBy);
        data.put("deliveredTo", new ArrayList<String>());
        data.put("participantIds", new ArrayList<>(memberIds));

        data.put("edited", false);
        data.put("deleted", false);

        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            data.put("mediaUrl", mediaUrl);
        }

        if ("audio".equals(messageType)) {
            data.put("duration", duration);
        }

        if (replyTo != null) {
            data.put("replyToMessageId", replyTo.getMessageId());
            data.put("replyToSenderId", replyTo.getSenderId());
            data.put("replyToText", replyTo.getText());
            data.put("replyToType", replyTo.getType());
            data.put("replyToMediaUrl", replyTo.getMediaUrl());
        }

        if (forwarded) {
            data.put("forwarded", true);
            data.put("forwardedFromSenderId", forwardedFromSenderId);
            data.put("forwardedFromMessageId", forwardedFromMessageId);
        } else {
            data.put("forwarded", false);
        }

        return data;
    }

    // --------------------------------------------------
    // UPDATE CONVERSATION AFTER SENDING
    // --------------------------------------------------

    private Map<String, Object> buildConversationUpdates(
            List<String> memberIds,
            String senderUid,
            String messageText,
            String messageType
    ) {
        Map<String, Object> updates = new HashMap<>();

        updates.put("lastMessage", messageText);
        updates.put("lastMessageType", messageType);
        updates.put("lastMessageAt", FieldValue.serverTimestamp());
        updates.put("lastMessageTime", FieldValue.serverTimestamp());
        updates.put("lastSenderId", senderUid);

        updates.putAll(
                buildUnreadCountUpdates(memberIds, senderUid)
        );

        return updates;
    }

    // --------------------------------------------------
    // SEND TEXT MESSAGE
    // --------------------------------------------------

    public void sendTextMessage(
            String conversationId,
            String text,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            callback.onError("Message cannot be empty.");
            return;
        }

        String value = text.trim();

        if (value.length() > 5000) {
            callback.onError("Message cannot exceed 5000 characters.");
            return;
        }

        sendMessage(
                conversationId,
                user.getUid(),
                "text",
                value,
                "",
                null,
                callback
        );
    }

    // --------------------------------------------------
    // SEND IMAGE MESSAGE
    // --------------------------------------------------

    public void sendImageMessage(
            String conversationId,
            String imageUrl,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            callback.onError("Invalid image URL.");
            return;
        }

        sendMessage(
                conversationId,
                user.getUid(),
                "image",
                "Photo",
                imageUrl.trim(),
                null,
                callback
        );
    }

    // --------------------------------------------------
    // SEND DOCUMENT MESSAGE
    // --------------------------------------------------

    public void sendDocumentMessage(
            String conversationId,
            String fileName,
            String downloadUrl,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (fileName == null || fileName.trim().isEmpty()
                || downloadUrl == null || downloadUrl.trim().isEmpty()) {
            callback.onError("Invalid document information.");
            return;
        }

        sendMessage(
                conversationId,
                user.getUid(),
                "document",
                fileName.trim(),
                downloadUrl.trim(),
                null,
                callback
        );
    }

    // --------------------------------------------------
    // SEND AUDIO MESSAGE
    // --------------------------------------------------

    public void sendAudioMessage(
            String conversationId,
            String audioUrl,
            long duration,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            callback.onError("Invalid audio URL.");
            return;
        }

        if (duration <= 0) {
            callback.onError("Invalid audio duration.");
            return;
        }

        sendAudioMessageToConversation(
                conversationId,
                user.getUid(),
                audioUrl.trim(),
                duration,
                callback
        );
    }

    private void sendAudioMessageToConversation(
            String conversationId,
            String senderUid,
            String audioUrl,
            long duration,
            MessageCallback callback
    ) {
        DocumentReference conversationRef =
                getConversationReference(conversationId);

        conversationRef.get()
                .addOnSuccessListener(conversation -> {

                    if (!conversation.exists()) {
                        callback.onError("Conversation does not exist.");
                        return;
                    }

                    List<String> members =
                            (List<String>) conversation.get("memberIds");

                    if (members == null || !members.contains(senderUid)) {
                        callback.onError(
                                "You are not a member of this conversation."
                        );
                        return;
                    }

                    if (members.size() < 2) {
                        callback.onError("Conversation members not found.");
                        return;
                    }

                    saveMessage(
                            conversationId,
                            senderUid,
                            members,
                            "audio",
                            "Voice message",
                            audioUrl,
                            null,
                            false,
                            null,
                            null,
                            conversationRef,
                            callback,
                            duration
                    );
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to verify conversation."
                        )
                );
    }

    // --------------------------------------------------
    // SEND REPLY MESSAGE
    // --------------------------------------------------

    public void sendReplyMessage(
            String conversationId,
            String messageType,
            String messageText,
            String mediaUrl,
            ChatMessage replyTo,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (replyTo == null || replyTo.getMessageId() == null) {
            callback.onError("Original message is invalid.");
            return;
        }

        if (conversationId == null
                || !conversationId.equals(replyTo.getConversationId())) {
            callback.onError("Reply must belong to this conversation.");
            return;
        }

        if (!"text".equals(messageType)
                && !"image".equals(messageType)
                && !"document".equals(messageType)
                && !"audio".equals(messageType)) {
            callback.onError("Invalid message type.");
            return;
        }

        if (messageText == null || messageText.trim().isEmpty()) {
            callback.onError("Message content is missing.");
            return;
        }

        if (!"text".equals(messageType)
                && (mediaUrl == null || mediaUrl.trim().isEmpty())) {
            callback.onError("Attachment URL is missing.");
            return;
        }

        sendMessage(
                conversationId,
                user.getUid(),
                messageType,
                messageText.trim(),
                mediaUrl == null ? "" : mediaUrl.trim(),
                replyTo,
                callback
        );
    }

    // --------------------------------------------------
    // COMMON SEND MESSAGE
    // --------------------------------------------------

    private void sendMessage(
            String conversationId,
            String senderUid,
            String messageType,
            String messageText,
            String mediaUrl,
            ChatMessage replyTo,
            MessageCallback callback
    ) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        DocumentReference conversationRef =
                getConversationReference(conversationId);

        conversationRef.get()
                .addOnSuccessListener(conversation -> {

                    if (!conversation.exists()) {
                        callback.onError("Conversation does not exist.");
                        return;
                    }

                    List<String> members =
                            (List<String>) conversation.get("memberIds");

                    if (members == null || !members.contains(senderUid)) {
                        callback.onError(
                                "You are not a member of this conversation."
                        );
                        return;
                    }

                    if (members.size() < 2) {
                        callback.onError("Conversation members not found.");
                        return;
                    }

                    saveMessage(
                            conversationId,
                            senderUid,
                            members,
                            messageType,
                            messageText,
                            mediaUrl,
                            replyTo,
                            false,
                            null,
                            null,
                            conversationRef,
                            callback,
                            0L
                    );
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to verify conversation."
                        )
                );
    }

    // --------------------------------------------------
    // SAVE MESSAGE
    // --------------------------------------------------

    private void saveMessage(
            String conversationId,
            String senderUid,
            List<String> members,
            String messageType,
            String messageText,
            String mediaUrl,
            ChatMessage replyTo,
            boolean forwarded,
            String forwardedFromSenderId,
            String forwardedFromMessageId,
            DocumentReference conversationRef,
            MessageCallback callback,
            long duration
    ) {
        DocumentReference messageRef =
                getMessagesCollection(conversationId).document();

        Map<String, Object> messageData = buildMessageData(
                conversationId,
                senderUid,
                members,
                messageType,
                messageText,
                mediaUrl,
                replyTo,
                forwarded,
                forwardedFromSenderId,
                forwardedFromMessageId,
                duration
        );

        Map<String, Object> conversationUpdates =
                buildConversationUpdates(
                        members,
                        senderUid,
                        messageText,
                        messageType
                );

        db.runBatch(batch -> {
            batch.set(messageRef, messageData);
            batch.update(conversationRef, conversationUpdates);
        }).addOnSuccessListener(unused ->
                callback.onSuccess()
        ).addOnFailureListener(error ->
                reportError(callback, error, "Unable to send message.")
        );
    }

    // --------------------------------------------------
    // FIND MESSAGE BY ID
    // --------------------------------------------------

    private void findMessageById(
            String messageId,
            MessageCallback callback,
            MessageFoundListener listener
    ) {
        db.collectionGroup("messages")
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (messageId.equals(document.getId())) {
                            listener.onFound(
                                    document.getReference(),
                                    document
                            );
                            return;
                        }
                    }

                    callback.onError("Message not found.");
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to find message."
                        )
                );
    }

    private interface MessageFoundListener {
        void onFound(
                DocumentReference reference,
                DocumentSnapshot snapshot
        );
    }

    // --------------------------------------------------
    // FORWARD MESSAGE
    // --------------------------------------------------

    public void forwardMessage(
            String sourceMessageId,
            String destinationConversationId,
            String copiedMediaUrl,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (sourceMessageId == null || sourceMessageId.trim().isEmpty()
                || destinationConversationId == null
                || destinationConversationId.trim().isEmpty()) {
            callback.onError("Invalid message or destination.");
            return;
        }

        findMessageById(
                sourceMessageId,
                callback,
                (sourceRef, source) -> {

                    String senderUid = user.getUid();

                    if (Boolean.TRUE.equals(source.getBoolean("deleted"))) {
                        callback.onError(
                                "Deleted messages cannot be forwarded."
                        );
                        return;
                    }

                    String sourceConversationId =
                            source.getString("conversationId");

                    String messageType =
                            source.getString("messageType");

                    String messageText =
                            source.getString("content");

                    String originalSenderId =
                            source.getString("senderId");

                    if (sourceConversationId == null
                            || messageType == null
                            || originalSenderId == null) {
                        callback.onError(
                                "Original message data is incomplete."
                        );
                        return;
                    }

                    if (!"text".equals(messageType)
                            && (copiedMediaUrl == null
                            || copiedMediaUrl.trim().isEmpty())) {
                        callback.onError(
                                "A copied attachment URL is required."
                        );
                        return;
                    }

                    DocumentReference sourceConversation =
                            getConversationReference(sourceConversationId);

                    DocumentReference destinationConversation =
                            getConversationReference(destinationConversationId);

                    sourceConversation.get()
                            .addOnSuccessListener(sourceParent -> {

                                List<String> sourceMembers =
                                        (List<String>) sourceParent.get("memberIds");

                                if (!sourceParent.exists()
                                        || sourceMembers == null
                                        || !sourceMembers.contains(senderUid)) {
                                    callback.onError(
                                            "You are not a member of the original conversation."
                                    );
                                    return;
                                }

                                destinationConversation.get()
                                        .addOnSuccessListener(destinationParent -> {

                                            if (!destinationParent.exists()) {
                                                callback.onError(
                                                        "Destination conversation does not exist."
                                                );
                                                return;
                                            }

                                            List<String> destinationMembers =
                                                    (List<String>) destinationParent.get("memberIds");

                                            if (destinationMembers == null
                                                    || !destinationMembers.contains(senderUid)
                                                    || destinationMembers.size() < 2) {
                                                callback.onError(
                                                        "You are not a member of the destination conversation."
                                                );
                                                return;
                                            }

                                            boolean alreadyForwarded =
                                                    Boolean.TRUE.equals(
                                                            source.getBoolean("forwarded")
                                                    );

                                            String fromSender =
                                                    source.getString("forwardedFromSenderId");

                                            String fromMessage =
                                                    source.getString("forwardedFromMessageId");

                                            if (!alreadyForwarded || fromSender == null) {
                                                fromSender = originalSenderId;
                                            }

                                            if (!alreadyForwarded || fromMessage == null) {
                                                fromMessage = sourceMessageId;
                                            }

                                            String finalMedia =
                                                    "text".equals(messageType)
                                                            ? ""
                                                            : copiedMediaUrl.trim();

                                            Long sourceDuration =
                                                    source.getLong("duration");

                                            saveMessage(
                                                    destinationConversationId,
                                                    senderUid,
                                                    destinationMembers,
                                                    messageType,
                                                    messageText == null ? "" : messageText,
                                                    finalMedia,
                                                    null,
                                                    true,
                                                    fromSender,
                                                    fromMessage,
                                                    destinationConversation,
                                                    callback,
                                                    sourceDuration == null
                                                            ? 0L
                                                            : sourceDuration
                                            );
                                        })
                                        .addOnFailureListener(error ->
                                                reportError(
                                                        callback,
                                                        error,
                                                        "Unable to verify destination conversation."
                                                )
                                        );
                            })
                            .addOnFailureListener(error ->
                                    reportError(
                                            callback,
                                            error,
                                            "Unable to verify original conversation."
                                    )
                            );
                }
        );
    }

    // --------------------------------------------------
    // UPDATE UNREAD COUNTS
    // --------------------------------------------------

    private Map<String, Object> buildUnreadCountUpdates(
            List<String> memberIds,
            String senderUid
    ) {
        Map<String, Object> updates = new HashMap<>();

        if (memberIds == null) {
            return updates;
        }

        for (String memberId : memberIds) {
            if (memberId == null || memberId.trim().isEmpty()) {
                continue;
            }

            if (memberId.equals(senderUid)) {
                updates.put("unreadCounts." + memberId, 0);
            } else {
                updates.put(
                        "unreadCounts." + memberId,
                        FieldValue.increment(1)
                );
            }
        }

        return updates;
    }

    // --------------------------------------------------
    // EDIT OWN TEXT MESSAGE
    // --------------------------------------------------

    public void editTextMessage(
            String messageId,
            String newText,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (newText == null || newText.trim().isEmpty()) {
            callback.onError("Message cannot be empty.");
            return;
        }

        String editedText = newText.trim();

        if (editedText.length() > 5000) {
            callback.onError("Message cannot exceed 5000 characters.");
            return;
        }

        findMessageById(
                messageId,
                callback,
                (reference, snapshot) -> {

                    String senderId = snapshot.getString("senderId");
                    String type = snapshot.getString("messageType");

                    if (!user.getUid().equals(senderId)) {
                        callback.onError(
                                "You can edit only your own messages."
                        );
                        return;
                    }

                    if (!"text".equals(type)) {
                        callback.onError(
                                "Only text messages can be edited."
                        );
                        return;
                    }

                    if (Boolean.TRUE.equals(snapshot.getBoolean("deleted"))) {
                        callback.onError(
                                "Deleted messages cannot be edited."
                        );
                        return;
                    }

                    reference.update(
                            "content", editedText,
                            "edited", true
                    ).addOnSuccessListener(unused ->
                            callback.onSuccess()
                    ).addOnFailureListener(error ->
                            reportError(
                                    callback,
                                    error,
                                    "Unable to edit message."
                            )
                    );
                }
        );
    }

    // --------------------------------------------------
    // DELETE OWN MESSAGE
    // --------------------------------------------------

    public void deleteMessage(
            String messageId,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        findMessageById(
                messageId,
                callback,
                (reference, snapshot) -> {

                    String senderId = snapshot.getString("senderId");

                    if (!user.getUid().equals(senderId)) {
                        callback.onError(
                                "You can delete only your own messages."
                        );
                        return;
                    }

                    if (Boolean.TRUE.equals(snapshot.getBoolean("deleted"))) {
                        callback.onError("Message is already deleted.");
                        return;
                    }

                    reference.update("deleted", true)
                            .addOnSuccessListener(unused ->
                                    callback.onSuccess()
                            )
                            .addOnFailureListener(error ->
                                    reportError(
                                            callback,
                                            error,
                                            "Unable to delete message."
                                    )
                            );
                }
        );
    }

    // --------------------------------------------------
    // MARK RECEIVED MESSAGES AS DELIVERED
    // --------------------------------------------------

    public void markMessagesAsDelivered(
            String conversationId,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        String currentUid = user.getUid();

        getMessagesCollection(conversationId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<DocumentSnapshot> undelivered =
                            new ArrayList<>();

                    for (DocumentSnapshot document : snapshot.getDocuments()) {

                        String senderId =
                                document.getString("senderId");

                        if (senderId == null || senderId.equals(currentUid)) {
                            continue;
                        }

                        List<String> deliveredTo =
                                (List<String>) document.get("deliveredTo");

                        if (deliveredTo == null
                                || !deliveredTo.contains(currentUid)) {
                            undelivered.add(document);
                        }
                    }

                    if (undelivered.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }

                    updateDeliveryReceiptsInBatches(
                            undelivered,
                            currentUid,
                            0,
                            callback
                    );
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to load messages for delivery."
                        )
                );
    }

    // --------------------------------------------------
    // UPDATE DELIVERY RECEIPTS IN BATCHES
    // --------------------------------------------------

    private void updateDeliveryReceiptsInBatches(
            List<DocumentSnapshot> messages,
            String currentUid,
            int startIndex,
            MessageCallback callback
    ) {
        int endIndex = Math.min(startIndex + 450, messages.size());

        db.runBatch(batch -> {
            for (int i = startIndex; i < endIndex; i++) {
                batch.update(
                        messages.get(i).getReference(),
                        "deliveredTo",
                        FieldValue.arrayUnion(currentUid)
                );
            }
        }).addOnSuccessListener(unused -> {

            if (endIndex < messages.size()) {
                updateDeliveryReceiptsInBatches(
                        messages,
                        currentUid,
                        endIndex,
                        callback
                );
            } else {
                callback.onSuccess();
            }

        }).addOnFailureListener(error ->
                reportError(
                        callback,
                        error,
                        "Unable to update delivery receipts."
                )
        );
    }

    // --------------------------------------------------
    // MARK RECEIVED MESSAGES AS READ
    // --------------------------------------------------

    public void markMessagesAsRead(
            String conversationId,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        String currentUid = user.getUid();

        getMessagesCollection(conversationId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<DocumentSnapshot> unread = new ArrayList<>();

                    for (DocumentSnapshot document : snapshot.getDocuments()) {

                        String senderId = document.getString("senderId");

                        if (senderId == null || senderId.equals(currentUid)) {
                            continue;
                        }

                        List<String> readBy =
                                (List<String>) document.get("readBy");

                        if (readBy == null || !readBy.contains(currentUid)) {
                            unread.add(document);
                        }
                    }

                    if (unread.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }

                    updateReadReceiptsInBatches(
                            unread,
                            currentUid,
                            0,
                            callback
                    );
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to load messages."
                        )
                );
    }

    // --------------------------------------------------
    // UPDATE READ RECEIPTS IN BATCHES
    // --------------------------------------------------

    private void updateReadReceiptsInBatches(
            List<DocumentSnapshot> messages,
            String currentUid,
            int startIndex,
            MessageCallback callback
    ) {
        int endIndex = Math.min(startIndex + 450, messages.size());

        db.runBatch(batch -> {
            for (int i = startIndex; i < endIndex; i++) {
                batch.update(
                        messages.get(i).getReference(),
                        "readBy",
                        FieldValue.arrayUnion(currentUid)
                );
            }
        }).addOnSuccessListener(unused -> {

            if (endIndex < messages.size()) {
                updateReadReceiptsInBatches(
                        messages,
                        currentUid,
                        endIndex,
                        callback
                );
            } else {
                callback.onSuccess();
            }

        }).addOnFailureListener(error ->
                reportError(
                        callback,
                        error,
                        "Unable to update read receipts."
                )
        );
    }

    // --------------------------------------------------
    // MAP FIRESTORE DOCUMENT TO CHAT MESSAGE
    // --------------------------------------------------

    private ChatMessage mapMessage(DocumentSnapshot document) {

        ChatMessage message = new ChatMessage();

        message.setMessageId(document.getId());

        message.setConversationId(
                document.getString("conversationId")
        );

        message.setSenderId(
                document.getString("senderId")
        );

        message.setText(
                document.getString("content")
        );

        message.setType(
                document.getString("messageType")
        );

        message.setMediaUrl(
                document.getString("mediaUrl")
        );

        Long duration = document.getLong("duration");

        message.setDuration(
                duration == null ? 0L : duration
        );

        Timestamp timestamp = document.getTimestamp("timestamp");

        if (timestamp != null) {
            message.setCreatedAt(timestamp.toDate());
        }

        List<String> deliveredTo =
                (List<String>) document.get("deliveredTo");

        message.setDeliveredTo(
                deliveredTo == null
                        ? new ArrayList<>()
                        : new ArrayList<>(deliveredTo)
        );

        List<String> readBy =
                (List<String>) document.get("readBy");

        message.setReadBy(
                readBy == null
                        ? new ArrayList<>()
                        : new ArrayList<>(readBy)
        );

        message.setEdited(
                Boolean.TRUE.equals(document.getBoolean("edited"))
        );

        message.setDeleted(
                Boolean.TRUE.equals(document.getBoolean("deleted"))
        );

        message.setForwarded(
                Boolean.TRUE.equals(document.getBoolean("forwarded"))
        );

        message.setForwardedFromSenderId(
                document.getString("forwardedFromSenderId")
        );

        message.setForwardedFromMessageId(
                document.getString("forwardedFromMessageId")
        );

        message.setReplyToMessageId(
                document.getString("replyToMessageId")
        );

        message.setReplyToSenderId(
                document.getString("replyToSenderId")
        );

        message.setReplyToText(
                document.getString("replyToText")
        );

        message.setReplyToType(
                document.getString("replyToType")
        );

        message.setReplyToMediaUrl(
                document.getString("replyToMediaUrl")
        );

        return message;
    }

    // --------------------------------------------------
    // REAL-TIME MESSAGE LISTENER
    // --------------------------------------------------

    public ListenerRegistration listenToMessages(
            String conversationId,
            MessagesListener listener
    ) {
        return listenToMessages(conversationId, null, listener);
    }

    public ListenerRegistration listenToMessages(
            String conversationId,
            Timestamp clearedAt,
            MessagesListener listener
    ) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            listener.onError("Invalid conversation.");
            return null;
        }

        return getMessagesCollection(conversationId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {

                    if (error != null) {
                        listener.onError(
                                error.getMessage() == null
                                        ? "Unable to listen for messages."
                                        : error.getMessage()
                        );
                        return;
                    }

                    if (snapshots == null) {
                        listener.onMessagesChanged(new ArrayList<>());
                        return;
                    }

                    List<ChatMessage> messages = new ArrayList<>();

                    for (DocumentSnapshot document : snapshots.getDocuments()) {

                        Timestamp timestamp =
                                document.getTimestamp("timestamp");

                        if (clearedAt != null
                                && (timestamp == null
                                || timestamp.compareTo(clearedAt) <= 0)) {
                            continue;
                        }

                        messages.add(mapMessage(document));
                    }

                    listener.onMessagesChanged(messages);
                });
    }

    // --------------------------------------------------
    // CLEAR CHAT HISTORY FOR CURRENT USER
    // --------------------------------------------------

    public void clearChatHistory(
            String conversationId,
            MessageCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        String uid = user.getUid();

        DocumentReference conversationRef =
                getConversationReference(conversationId);

        conversationRef.get()
                .addOnSuccessListener(conversation -> {

                    if (!conversation.exists()) {
                        callback.onError("Conversation does not exist.");
                        return;
                    }

                    List<String> members =
                            (List<String>) conversation.get("memberIds");

                    if (members == null || !members.contains(uid)) {
                        callback.onError(
                                "You are not a member of this conversation."
                        );
                        return;
                    }

                    DocumentReference clearRef =
                            db.collection("chat_user_cleared")
                                    .document(uid)
                                    .collection("conversations")
                                    .document(conversationId);

                    Map<String, Object> data = new HashMap<>();
                    data.put("clearedAt", FieldValue.serverTimestamp());

                    clearRef.set(data)
                            .addOnSuccessListener(unused ->
                                    callback.onSuccess()
                            )
                            .addOnFailureListener(error ->
                                    reportError(
                                            callback,
                                            error,
                                            "Unable to clear chat history."
                                    )
                            );
                })
                .addOnFailureListener(error ->
                        reportError(
                                callback,
                                error,
                                "Unable to verify conversation."
                        )
                );
    }

    // --------------------------------------------------
    // GET CURRENT USER'S CLEAR TIME
    // --------------------------------------------------

    public void getChatHistoryClearTime(
            String conversationId,
            ClearTimeCallback callback
    ) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("User is not signed in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.onError("Invalid conversation.");
            return;
        }

        db.collection("chat_user_cleared")
                .document(user.getUid())
                .collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(document -> {

                    Timestamp clearedAt = null;

                    if (document.exists()) {
                        clearedAt = document.getTimestamp("clearedAt");
                    }

                    callback.onSuccess(clearedAt);
                })
                .addOnFailureListener(error -> {

                    String errorMessage = error.getMessage();

                    callback.onError(
                            errorMessage == null
                                    ? "Unable to load chat history settings."
                                    : errorMessage
                    );
                });
    }
}
