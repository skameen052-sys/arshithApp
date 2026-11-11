package com.example.arshithconnectattendance;

import com.google.gson.JsonObject;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TeamsApiService {

    @GET("api/chat/contacts")
    Call<List<Contact>> getContacts();

    @GET("api/chat/teams")
    Call<List<Team>> getTeams();

    @GET("api/chat/conversations")
    Call<List<JsonObject>> getConversations();

    @GET("api/chat/messages")
    Call<List<Message>> getMessages(
            @Query("chat_type") String chatType,
            @Query("target_id") String targetId,
            @Query("limit") int limit,
            @Query("conversation_id") String conversationId
    );

    @POST("api/chat/messages")
    Call<Message> sendMessage(@Body JsonObject payload);

    @POST("api/chat/messages/{messageId}/reactions")
    Call<JsonObject> toggleReaction(
            @Path("messageId") String messageId,
            @Body JsonObject payload
    );

    @POST("api/chat/messages/{messageId}/pin")
    Call<JsonObject> togglePin(
            @Path("messageId") String messageId
    );

    @DELETE("api/chat/messages/{messageId}")
    Call<JsonObject> deleteMessage(
            @Path("messageId") String messageId
    );

    @PATCH("api/chat/messages/{messageId}")
    Call<Message> editMessage(
            @Path("messageId") String messageId,
            @Body JsonObject payload
    );

    @Multipart
    @POST("api/chat/attachments/upload")
    Call<JsonObject> uploadAttachment(
            @Part MultipartBody.Part file
    );

    @POST("api/chat/conversations/group")
    Call<JsonObject> createGroup(
            @Body JsonObject payload
    );

    @POST("api/chat/conversations/{groupId}/members")
    Call<JsonObject> addGroupMember(
            @Path("groupId") String groupId,
            @Body JsonObject payload
    );

    @DELETE("api/chat/conversations/{groupId}/members/{userId}")
    Call<JsonObject> removeGroupMember(
            @Path("groupId") String groupId,
            @Path("userId") String userId
    );

    @POST("api/chat/read")
    Call<JsonObject> markRead(@Body JsonObject payload);

    @GET("api/chat/unread-count")
    Call<JsonObject> getUnreadCount();
}