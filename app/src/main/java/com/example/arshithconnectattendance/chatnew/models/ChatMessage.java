package com.example.arshithconnectattendance.chatnew.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessage {

    private String messageId;
    private String conversationId;
    private String senderId;

    private String replyToMessageId;
    private String replyToSenderId;
    private String replyToText;
    private String replyToType;
    private String replyToMediaUrl;
    private long duration;

    private String type;
    private String text;
    private String mediaUrl;

    private List<String> deliveredTo;
    private List<String> readBy;
    private Map<String, String> reactions;

    @ServerTimestamp
    private Date createdAt;

    private boolean edited;
    private boolean deleted;

    private boolean forwarded;
    private String forwardedFromSenderId;
    private String forwardedFromMessageId;

    // Local-only attachment upload information.
    // These fields are not written to Firestore.
    private transient String localUploadId;
    private transient String localUri;
    private transient int uploadProgress;
    private transient boolean uploading;
    private transient boolean pendingUpload;

    public ChatMessage() {
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }
    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public List<String> getDeliveredTo() {
        if (deliveredTo == null) {
            deliveredTo = new ArrayList<>();
        }
        return deliveredTo;
    }

    public void setDeliveredTo(List<String> deliveredTo) {
        this.deliveredTo = deliveredTo;
    }

    public List<String> getReadBy() {
        if (readBy == null) {
            readBy = new ArrayList<>();
        }
        return readBy;
    }

    public void setReadBy(List<String> readBy) {
        this.readBy = readBy;
    }

    public Map<String, String> getReactions() {
        if (reactions == null) {
            reactions = new HashMap<>();
        }
        return reactions;
    }

    public void setReactions(Map<String, String> reactions) {
        this.reactions = reactions;
    }

    public void addReaction(String userId, String emoji) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        if (emoji == null || emoji.trim().isEmpty()) {
            getReactions().remove(userId);
        } else {
            getReactions().put(userId, emoji);
        }
    }

    public void removeReaction(String userId) {
        if (userId != null && reactions != null) {
            reactions.remove(userId);
        }
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getReplyToSenderId() {
        return replyToSenderId;
    }

    public void setReplyToSenderId(String replyToSenderId) {
        this.replyToSenderId = replyToSenderId;
    }

    public String getReplyToText() {
        return replyToText;
    }

    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }

    public String getReplyToType() {
        return replyToType;
    }

    public void setReplyToType(String replyToType) {
        this.replyToType = replyToType;
    }

    public String getReplyToMediaUrl() {
        return replyToMediaUrl;
    }

    public void setReplyToMediaUrl(String replyToMediaUrl) {
        this.replyToMediaUrl = replyToMediaUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isForwarded() {
        return forwarded;
    }

    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
    }

    public String getForwardedFromSenderId() {
        return forwardedFromSenderId;
    }

    public void setForwardedFromSenderId(String forwardedFromSenderId) {
        this.forwardedFromSenderId = forwardedFromSenderId;
    }

    public String getForwardedFromMessageId() {
        return forwardedFromMessageId;
    }

    public void setForwardedFromMessageId(String forwardedFromMessageId) {
        this.forwardedFromMessageId = forwardedFromMessageId;
    }

    // Local upload state.

    public String getLocalUploadId() {
        return localUploadId;
    }

    public void setLocalUploadId(String localUploadId) {
        this.localUploadId = localUploadId;
    }

    public String getLocalUri() {
        return localUri;
    }

    public void setLocalUri(String localUri) {
        this.localUri = localUri;
    }

    public int getUploadProgress() {
        return uploadProgress;
    }

    public void setUploadProgress(int uploadProgress) {
        this.uploadProgress = Math.max(0, Math.min(100, uploadProgress));
    }

    public boolean isUploading() {
        return uploading;
    }

    public void setUploading(boolean uploading) {
        this.uploading = uploading;
    }

    public boolean isPendingUpload() {
        return pendingUpload;
    }

    public void setPendingUpload(boolean pendingUpload) {
        this.pendingUpload = pendingUpload;
    }
}