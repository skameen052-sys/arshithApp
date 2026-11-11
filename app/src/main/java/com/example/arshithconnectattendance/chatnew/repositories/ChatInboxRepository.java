
package com.example.arshithconnectattendance.chatnew.repository;

import com.example.arshithconnectattendance.chatnew.model.ChatInboxItem;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Tasks;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ChatInboxRepository {

    public interface InboxListener {
        void onInboxChanged(List<ChatInboxItem> items);
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private final AtomicLong snapshotVersion = new AtomicLong(0);

    public ChatInboxRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // LISTEN TO USER CONVERSATIONS

    public ListenerRegistration listenToInbox(InboxListener listener) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null || listener == null) {
            if (listener != null) {
                listener.onError("User is not signed in.");
            }
            return null;
        }

        String currentUid = currentUser.getUid();

        return db.collection("chat_conversations")
                .whereArrayContains("memberIds", currentUid)
                .addSnapshotListener((snapshots, error) -> {

                    if (error != null) {
                        listener.onError(
                                error.getMessage() == null
                                        ? "Unable to load conversations."
                                        : error.getMessage()
                        );
                        return;
                    }

                    if (snapshots == null) {
                        listener.onInboxChanged(new ArrayList<>());
                        return;
                    }

                    long version = snapshotVersion.incrementAndGet();

                    loadInboxItems(
                            snapshots,
                            currentUid,
                            version,
                            listener
                    );
                });
    }

    // IDENTIFY GROUP / TEAM CONVERSATIONS

    private boolean isGroupConversation(DocumentSnapshot conversation) {

        String type = conversation.getString("type");

        if (type == null || type.trim().isEmpty()) {
            type = conversation.getString("chat_type");
        }

        if (type == null || type.trim().isEmpty()) {
            type = conversation.getString("chatType");
        }

        if (type == null) {
            return false;
        }

        type = type.trim().toLowerCase(Locale.ROOT);

        return type.equals("group")
                || type.equals("team")
                || type.equals("teams");
    }

    // LOAD CONVERSATIONS, PROFILES AND SETTINGS

    private void loadInboxItems(
            QuerySnapshot snapshots,
            String currentUid,
            long version,
            InboxListener listener
    ) {

        List<DocumentSnapshot> conversations = new ArrayList<>();

        List<com.google.android.gms.tasks.Task<?>> allTasks =
                new ArrayList<>();

        for (DocumentSnapshot conversation : snapshots.getDocuments()) {

            boolean isGroup = isGroupConversation(conversation);

            @SuppressWarnings("unchecked")
            List<String> memberIds =
                    (List<String>) conversation.get("memberIds");

            String contactUid = null;

            if (!isGroup) {

                contactUid = getOtherMemberId(memberIds, currentUid);

                if (contactUid == null) {
                    continue;
                }
            }

            conversations.add(conversation);

            // PROFILE TASK

            if (isGroup) {

                allTasks.add(Tasks.forResult(null));

            } else {

                com.google.android.gms.tasks.Task<DocumentSnapshot> profileTask =
                        db.collection("employees")
                                .document(contactUid)
                                .get()
                                .continueWith(task -> {

                                    if (task.isSuccessful()) {
                                        return task.getResult();
                                    }

                                    return null;
                                });

                allTasks.add(profileTask);
            }

            // PER-USER INBOX SETTINGS TASK

            com.google.android.gms.tasks.Task<DocumentSnapshot> settingsTask =
                    db.collection("chat_user_inbox")
                            .document(currentUid)
                            .collection("items")
                            .document(conversation.getId())
                            .get()
                            .continueWith(task -> {

                                if (task.isSuccessful()) {
                                    return task.getResult();
                                }

                                return null;
                            });

            allTasks.add(settingsTask);
        }

        if (conversations.isEmpty()) {

            if (version == snapshotVersion.get()) {
                listener.onInboxChanged(new ArrayList<>());
            }

            return;
        }

        Tasks.whenAllSuccess(allTasks)

                .addOnSuccessListener(results -> {

                    if (version != snapshotVersion.get()) {
                        return;
                    }

                    List<ChatInboxItem> inboxItems = new ArrayList<>();

                    for (int i = 0; i < conversations.size(); i++) {

                        DocumentSnapshot conversation = conversations.get(i);

                        boolean isGroup =
                                isGroupConversation(conversation);

                        @SuppressWarnings("unchecked")
                        List<String> memberIds =
                                (List<String>) conversation.get("memberIds");

                        DocumentSnapshot employee =
                                (DocumentSnapshot) results.get(i * 2);

                        DocumentSnapshot settings =
                                (DocumentSnapshot) results.get(i * 2 + 1);

                        // DELETE FOR ME FILTER

                        if (settings != null
                                && settings.exists()
                                && Boolean.TRUE.equals(
                                settings.getBoolean("deletedForMe"))) {

                            continue;
                        }

                        ChatInboxItem item = new ChatInboxItem();

                        item.setConversationId(conversation.getId());

                        String name;
                        String photoUrl = "";

                        // GROUP / TEAM DETAILS

                        if (isGroup) {

                            name = conversation.getString("groupName");

                            if (name == null || name.trim().isEmpty()) {
                                name = conversation.getString("teamName");
                            }

                            if (name == null || name.trim().isEmpty()) {
                                name = "Group";
                            }

                            String groupImage =
                                    conversation.getString("groupPhotoUrl");

                            if (groupImage == null
                                    || groupImage.trim().isEmpty()) {

                                groupImage =
                                        conversation.getString("groupImage");
                            }

                            if (groupImage == null
                                    || groupImage.trim().isEmpty()) {

                                groupImage =
                                        conversation.getString("teamPhotoUrl");
                            }

                            if (groupImage != null) {
                                photoUrl = groupImage;
                            }

                            item.setConversationType("group");
                            item.setGroupName(name);
                            item.setGroupImage(photoUrl);
                            item.setContactUid("");

                        } else {

                            // DIRECT CHAT DETAILS

                            String otherUid =
                                    getOtherMemberId(memberIds, currentUid);

                            if (otherUid == null) {
                                continue;
                            }

                            item.setConversationType("direct");
                            item.setContactUid(otherUid);

                            name = "Employee";

                            if (employee != null && employee.exists()) {

                                String employeeName =
                                        employee.getString("name");

                                if (employeeName != null
                                        && !employeeName.trim().isEmpty()) {

                                    name = employeeName;
                                }

                                String photo =
                                        employee.getString("photoUrl");

                                if (photo != null) {
                                    photoUrl = photo;
                                }
                            }
                        }

                        item.setContactName(name);
                        item.setContactPhotoUrl(photoUrl);

                        // LAST MESSAGE

                        String lastMessage =
                                conversation.getString("lastMessage");

                        String messageType =
                                conversation.getString("lastMessageType");

                        String lastSenderId =
                                conversation.getString("lastSenderId");

                        if (lastMessage == null
                                || lastMessage.trim().isEmpty()) {

                            lastMessage = "Start a conversation";
                        }

                        item.setLastMessage(lastMessage);

                        item.setLastMessageType(
                                messageType == null ? "text" : messageType
                        );

                        item.setLastSenderId(lastSenderId);

                        // LAST MESSAGE TIME

                        Date lastMessageAt =
                                conversation.getDate("lastMessageAt");

                        if (lastMessageAt == null) {
                            lastMessageAt =
                                    conversation.getDate("createdAt");
                        }

                        item.setLastMessageAt(lastMessageAt);

                        // UNREAD COUNT FOR CURRENT USER

                        @SuppressWarnings("unchecked")
                        Map<String, Object> unreadCounts =
                                (Map<String, Object>)
                                        conversation.get("unreadCounts");

                        int unreadCount = 0;

                        if (unreadCounts != null) {

                            Object count = unreadCounts.get(currentUid);

                            if (count instanceof Number) {
                                unreadCount =
                                        Math.max(0, ((Number) count).intValue());
                            }
                        }

                        item.setUnreadCount(unreadCount);

                        // USER INBOX SETTINGS

                        boolean pinned = false;
                        boolean archived = false;
                        boolean muted = false;
                        boolean favorite = false;

                        if (settings != null && settings.exists()) {

                            Boolean savedPinned =
                                    settings.getBoolean("pinned");

                            Boolean savedArchived =
                                    settings.getBoolean("archived");

                            Boolean savedMuted =
                                    settings.getBoolean("muted");

                            Boolean savedFavorite =
                                    settings.getBoolean("favorite");

                            pinned = Boolean.TRUE.equals(savedPinned);
                            archived = Boolean.TRUE.equals(savedArchived);
                            muted = Boolean.TRUE.equals(savedMuted);
                            favorite = Boolean.TRUE.equals(savedFavorite);
                        }

                        item.setPinned(pinned);
                        item.setArchived(archived);
                        item.setMuted(muted);
                        item.setFavorite(favorite);

                        inboxItems.add(item);
                    }

                    // PINNED FIRST, THEN NEWEST

                    Collections.sort(inboxItems, (first, second) -> {

                        if (first.isPinned() != second.isPinned()) {
                            return first.isPinned() ? -1 : 1;
                        }

                        Date firstDate = first.getLastMessageAt();
                        Date secondDate = second.getLastMessageAt();

                        if (firstDate == null && secondDate == null) {
                            return 0;
                        }

                        if (firstDate == null) {
                            return 1;
                        }

                        if (secondDate == null) {
                            return -1;
                        }

                        return secondDate.compareTo(firstDate);
                    });

                    if (version == snapshotVersion.get()) {
                        listener.onInboxChanged(inboxItems);
                    }
                })

                .addOnFailureListener(error -> {

                    if (version != snapshotVersion.get()) {
                        return;
                    }

                    listener.onError(
                            error.getMessage() == null
                                    ? "Unable to load inbox."
                                    : error.getMessage()
                    );
                });
    }

    // SAVE PIN / ARCHIVE / MUTE SETTINGS

    public void setInboxSettings(
            String conversationId,
            boolean pinned,
            boolean archived,
            boolean muted,
            OnCompleteListener<Void> listener
    ) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            completeWithError(listener, "User is not logged in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            completeWithError(listener, "Invalid conversation ID.");
            return;
        }

        Map<String, Object> settings = new HashMap<>();

        settings.put("pinned", pinned);
        settings.put("archived", archived);
        settings.put("muted", muted);
        settings.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chat_user_inbox")
                .document(currentUser.getUid())
                .collection("items")
                .document(conversationId)
                .set(settings, SetOptions.merge())
                .addOnCompleteListener(task -> {

                    if (listener != null) {
                        listener.onComplete(task);
                    }
                });
    }

    // SAVE OR REMOVE FAVORITE

    public void setFavorite(
            String conversationId,
            boolean favorite,
            OnCompleteListener<Void> listener
    ) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            completeWithError(listener, "User is not logged in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            completeWithError(listener, "Invalid conversation ID.");
            return;
        }

        Map<String, Object> settings = new HashMap<>();

        settings.put("favorite", favorite);
        settings.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chat_user_inbox")
                .document(currentUser.getUid())
                .collection("items")
                .document(conversationId)
                .set(settings, SetOptions.merge())
                .addOnCompleteListener(task -> {

                    if (listener != null) {
                        listener.onComplete(task);
                    }
                });
    }

    // DELETE CONVERSATION FOR CURRENT USER ONLY

    public void deleteConversationForMe(
            String conversationId,
            OnCompleteListener<Void> listener
    ) {

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            completeWithError(listener, "User is not logged in.");
            return;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            completeWithError(listener, "Invalid conversation ID.");
            return;
        }

        Map<String, Object> settings = new HashMap<>();

        settings.put("deletedForMe", true);
        settings.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chat_user_inbox")
                .document(currentUser.getUid())
                .collection("items")
                .document(conversationId)
                .set(settings, SetOptions.merge())
                .addOnCompleteListener(task -> {

                    if (listener != null) {
                        listener.onComplete(task);
                    }
                });
    }

    // COMMON ERROR CALLBACK

    private void completeWithError(
            OnCompleteListener<Void> listener,
            String message
    ) {

        if (listener != null) {
            listener.onComplete(
                    Tasks.forException(new Exception(message))
            );
        }
    }

    // GET OTHER MEMBER ID

    private String getOtherMemberId(
            List<String> memberIds,
            String currentUid
    ) {

        if (memberIds == null) {
            return null;
        }

        for (String memberId : memberIds) {

            if (memberId != null && !memberId.equals(currentUid)) {
                return memberId;
            }
        }

        return null;
    }
}
