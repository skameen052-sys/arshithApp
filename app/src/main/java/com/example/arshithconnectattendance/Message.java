package com.example.arshithconnectattendance;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class Message {

    private String id;

    @SerializedName("conversation_id")
    private String conversationId;

    @SerializedName("chat_type")
    private String chatType;

    @SerializedName("sender_id")
    private String senderId;

    @SerializedName("sender_auth_uid")
    private String senderAuthUid;

    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("sender_role")
    private String senderRole;

    private String content;

    @SerializedName("message_type")
    private String messageType;

    @SerializedName("attachment_url")
    private String attachmentUrl;

    @SerializedName("attachment_name")
    private String attachmentName;

    @SerializedName("reply_to_message_id")
    private String replyToMessageId;

    @SerializedName("reply_to_sender")
    private String replyToSender;

    @SerializedName("reply_to_content")
    private String replyToContent;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("created_at_millis")
    private long createdAtMillis;

    @SerializedName("team_id")
    private String teamId;

    @SerializedName("team_name")
    private String teamName;

    @SerializedName("is_pinned")
    private boolean pinned;

    @SerializedName("is_edited")
    private boolean edited;

    @SerializedName("is_deleted")
    private boolean deleted;

    @SerializedName("participant_ids")
    private List<String> participantIds = new ArrayList<>();

    @SerializedName("member_business_ids")
    private List<String> memberBusinessIds = new ArrayList<>();

    @SerializedName("delivered_to")
    private List<String> deliveredTo = new ArrayList<>();

    @SerializedName("read_by")
    private List<String> readBy = new ArrayList<>();

    @SerializedName("attachments")
    private List<AttachmentData> attachments = new ArrayList<>();

    @SerializedName("reply_to")
    private ReplyData replyTo;

    public Message() {
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // =====================================================
    // ATTACHMENT DATA
    // =====================================================

    public static class AttachmentData {

        private String url;

        private String filename;

        @SerializedName("content_type")
        private String contentType;

        @SerializedName("file_size")
        private long fileSize;

        @SerializedName("is_image")
        private boolean image;

        @SerializedName("is_audio")
        private boolean audio;

        public String getUrl() {
            return url == null ? "" : url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFilename() {
            return filename == null ? "" : filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getContentType() {
            return contentType == null ? "" : contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public boolean isImage() {
            return image;
        }

        public void setImage(boolean image) {
            this.image = image;
        }

        public boolean isAudio() {
            return audio;
        }

        public void setAudio(boolean audio) {
            this.audio = audio;
        }
    }

    // =====================================================
    // REPLY DATA
    // =====================================================

    public static class ReplyData {

        @SerializedName("message_id")
        private String messageId;

        private String sender;

        private String content;

        public String getMessageId() {
            return messageId == null ? "" : messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getSender() {
            return sender == null ? "" : sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getContent() {
            return content == null ? "" : content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    // =====================================================
    // BASIC FIELDS
    // =====================================================

    public String getId() {
        return safe(id);
    }

    public void setId(String value) {
        id = value;
    }

    public String getConversationId() {
        return safe(conversationId);
    }

    public void setConversationId(String value) {
        conversationId = value;
    }

    public String getChatType() {
        return safe(chatType);
    }

    public void setChatType(String value) {
        chatType = value;
    }

    public String getSenderId() {
        return safe(senderId);
    }

    public void setSenderId(String value) {
        senderId = value;
    }

    public String getSenderAuthUid() {
        return safe(senderAuthUid);
    }

    public void setSenderAuthUid(String value) {
        senderAuthUid = value;
    }

    public String getSenderName() {
        return senderName == null ? "Employee" : senderName;
    }

    public void setSenderName(String value) {
        senderName = value;
    }

    public String getSenderRole() {
        return senderRole == null ? "employee" : senderRole;
    }

    public void setSenderRole(String value) {
        senderRole = value;
    }

    public String getContent() {
        return safe(content);
    }

    public void setContent(String value) {
        content = value;
    }

    public String getMessageType() {

        if (messageType != null && !messageType.isEmpty()) {
            return messageType;
        }

        if (attachments != null && !attachments.isEmpty()) {

            AttachmentData attachment = attachments.get(0);

            if (attachment.isImage()) {
                return "image";
            }

            if (attachment.isAudio()) {
                return "audio";
            }

            return "file";
        }

        return "text";
    }

    public void setMessageType(String value) {
        messageType = value;
    }

    // =====================================================
    // ATTACHMENTS
    // =====================================================

    public String getAttachmentUrl() {

        if (attachmentUrl != null && !attachmentUrl.isEmpty()) {
            return attachmentUrl;
        }

        if (attachments != null && !attachments.isEmpty()) {
            return attachments.get(0).getUrl();
        }

        return "";
    }

    public void setAttachmentUrl(String value) {
        attachmentUrl = value;
    }

    public String getAttachmentName() {

        if (attachmentName != null && !attachmentName.isEmpty()) {
            return attachmentName;
        }

        if (attachments != null && !attachments.isEmpty()) {
            return attachments.get(0).getFilename();
        }

        return "";
    }

    public void setAttachmentName(String value) {
        attachmentName = value;
    }

    public List<AttachmentData> getAttachments() {

        return attachments == null
                ? new ArrayList<>()
                : new ArrayList<>(attachments);
    }

    public void setAttachments(List<AttachmentData> value) {

        attachments = value == null
                ? new ArrayList<>()
                : new ArrayList<>(value);
    }

    // =====================================================
    // REPLY
    // =====================================================

    public String getReplyToMessageId() {

        if (replyToMessageId != null && !replyToMessageId.isEmpty()) {
            return replyToMessageId;
        }

        return replyTo == null ? "" : replyTo.getMessageId();
    }

    public void setReplyToMessageId(String value) {
        replyToMessageId = value;
    }

    public String getReplyToSender() {

        if (replyToSender != null && !replyToSender.isEmpty()) {
            return replyToSender;
        }

        return replyTo == null ? "" : replyTo.getSender();
    }

    public void setReplyToSender(String value) {
        replyToSender = value;
    }

    public String getReplyToContent() {

        if (replyToContent != null && !replyToContent.isEmpty()) {
            return replyToContent;
        }

        return replyTo == null ? "" : replyTo.getContent();
    }

    public void setReplyToContent(String value) {
        replyToContent = value;
    }

    public ReplyData getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(ReplyData value) {
        replyTo = value;
    }

    // =====================================================
    // TIMESTAMP
    // =====================================================

    public String getCreatedAt() {
        return safe(createdAt);
    }

    public void setCreatedAt(String value) {

        createdAt = value;

        if (value == null || value.trim().isEmpty()) {
            createdAtMillis = 0L;
        } else {
            createdAtMillis = parseTimestamp(value);
        }
    }

    public long getCreatedAtMillis() {

        if (createdAtMillis <= 0
                && createdAt != null
                && !createdAt.trim().isEmpty()) {

            createdAtMillis = parseTimestamp(createdAt);
        }

        return createdAtMillis;
    }

    /**
     * Use this method when reading Firebase Timestamp values.
     * Pass timestamp.toDate().getTime().
     */
    public void setCreatedAtMillis(long value) {

        createdAtMillis = value;

        if (value > 0) {

            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US
            );

            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            createdAt = sdf.format(new Date(value));
        } else {
            createdAt = "";
        }
    }

    private long parseTimestamp(String value) {

        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }

        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String format : formats) {

            try {

                SimpleDateFormat sdf =
                        new SimpleDateFormat(format, Locale.US);

                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date = sdf.parse(value);

                if (date != null) {
                    return date.getTime();
                }

            } catch (ParseException ignored) {
                // Try the next supported format.
            }
        }

        return 0L;
    }

    // =====================================================
    // TEAM
    // =====================================================

    public String getTeamId() {
        return safe(teamId);
    }

    public void setTeamId(String value) {
        teamId = value;
    }

    public String getTeamName() {
        return safe(teamName);
    }

    public void setTeamName(String value) {
        teamName = value;
    }

    // =====================================================
    // MESSAGE STATUS
    // =====================================================

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean value) {
        pinned = value;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean value) {
        edited = value;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean value) {
        deleted = value;
    }

    // =====================================================
    // PARTICIPANTS
    // =====================================================

    public List<String> getParticipantIds() {

        return participantIds == null
                ? new ArrayList<>()
                : new ArrayList<>(participantIds);
    }

    public void setParticipantIds(List<String> value) {

        participantIds = value == null
                ? new ArrayList<>()
                : new ArrayList<>(value);
    }

    public List<String> getMemberBusinessIds() {

        return memberBusinessIds == null
                ? new ArrayList<>()
                : new ArrayList<>(memberBusinessIds);
    }

    public void setMemberBusinessIds(List<String> value) {

        memberBusinessIds = value == null
                ? new ArrayList<>()
                : new ArrayList<>(value);
    }

    // =====================================================
    // DELIVERY RECEIPTS
    // =====================================================

    public List<String> getDeliveredTo() {

        return deliveredTo == null
                ? new ArrayList<>()
                : new ArrayList<>(deliveredTo);
    }

    public void setDeliveredTo(List<String> value) {

        deliveredTo = value == null
                ? new ArrayList<>()
                : new ArrayList<>(value);
    }

    // =====================================================
    // READ RECEIPTS
    // =====================================================

    public List<String> getReadBy() {

        return readBy == null
                ? new ArrayList<>()
                : new ArrayList<>(readBy);
    }

    public void setReadBy(List<String> value) {

        readBy = value == null
                ? new ArrayList<>()
                : new ArrayList<>(value);
    }
}