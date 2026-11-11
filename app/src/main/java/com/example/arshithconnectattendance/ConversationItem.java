package com.example.arshithconnectattendance;

public class ConversationItem {

    public String conversationId;
    public String type;
    public String targetId;
    public String title;
    public String subtitle;
    public String lastMessage;
    public int unreadCount;
    public long updatedAt;

    public ConversationItem(
            String conversationId,
            String type,
            String targetId,
            String title,
            String subtitle,
            String lastMessage,
            int unreadCount
    ) {
        this(
                conversationId,
                type,
                targetId,
                title,
                subtitle,
                lastMessage,
                unreadCount,
                0L
        );
    }

    public ConversationItem(
            String conversationId,
            String type,
            String targetId,
            String title,
            String subtitle,
            String lastMessage,
            int unreadCount,
            long updatedAt
    ) {
        this.conversationId = conversationId == null ? "" : conversationId;
        this.type = type == null ? "direct" : type;
        this.targetId = targetId == null ? "" : targetId;
        this.title = title == null ? "Chat" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.lastMessage = lastMessage == null ? "" : lastMessage;
        this.unreadCount = unreadCount;
        this.updatedAt = updatedAt;
    }
}