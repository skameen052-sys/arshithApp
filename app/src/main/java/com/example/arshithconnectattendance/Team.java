package com.example.arshithconnectattendance;

import java.util.List;
import java.util.ArrayList;
import com.google.gson.annotations.SerializedName;


public class Team {

    @SerializedName("id")
    private String id;

    @SerializedName("team_id")
    private String teamId;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("department")
    private String department;

    @SerializedName("leader")
    private String leader;

    @SerializedName("member_count")
    private int memberCount;

    @SerializedName("members")
    private List<Contact> members;

    @SerializedName("last_message")
    private String lastMessage;

    @SerializedName("unread_count")
    private int unreadCount;

    public String getId() {
        return id != null ? id : teamId;
    }

    public String getName() {
        return name == null ? "Team" : name;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getDepartment() {
        return department == null ? "" : department;
    }

    public String getLeader() {
        return leader == null ? "" : leader;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public List<Contact> getMembers() {
        return members;
    }

    public String getLastMessage() {
        return lastMessage == null ? "" : lastMessage;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}