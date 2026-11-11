
package com.example.arshithconnectattendance;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.arshithconnectattendance.chatnew.activities.ChatConversationActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String GENERAL_CHANNEL_ID =
            "arshith_connect_notifications";

    private static final String CHAT_CHANNEL_ID =
            "chat_messages";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        NotificationTokenManager.saveToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();

        String notificationType = data.get("notificationType");

        if ("chat_message".equals(notificationType)) {
            showChatNotification(data);
        } else {
            showGeneralNotification(remoteMessage, data);
        }
    }

    private void showGeneralNotification(
            RemoteMessage remoteMessage,
            Map<String, String> data) {

        String title = "Arshith Connect";
        String body = "You have a new notification.";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }

            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (data.get("title") != null) {
            title = data.get("title");
        }

        if (data.get("body") != null) {
            body = data.get("body");
        }

        createNotificationChannel(
                GENERAL_CHANNEL_ID,
                "Arshith Connect Notifications",
                "Notifications from Arshith Connect"
        );

        Intent intent = new Intent(
                this,
                EmployeeDashboardActivity.class
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        showNotification(
                GENERAL_CHANNEL_ID,
                title,
                body,
                intent,
                (int) System.currentTimeMillis()
        );
    }

    private void showChatNotification(Map<String, String> data) {

        String conversationId = data.get("conversationId");
        String senderId = data.get("senderId");
        String senderName = data.get("senderName");
        String title = data.get("title");
        String body = data.get("body");

        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }

        if (senderId == null || senderId.trim().isEmpty()) {
            return;
        }

        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = "New message";
        }

        if (title == null || title.trim().isEmpty()) {
            title = senderName;
        }

        if (body == null || body.trim().isEmpty()) {
            body = "You received a new message.";
        }

        createNotificationChannel(
                CHAT_CHANNEL_ID,
                "Chat Messages",
                "New chat message notifications"
        );

        Intent intent = new Intent(
                this,
                ChatConversationActivity.class
        );

        intent.putExtra(
                "conversation_id",
                conversationId
        );

        intent.putExtra(
                "contact_uid",
                senderId
        );

        intent.putExtra(
                "contact_name",
                senderName
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int notificationId =
                (conversationId + "_" +
                        data.get("messageId")).hashCode();

        showNotification(
                CHAT_CHANNEL_ID,
                title,
                body,
                intent,
                notificationId
        );
    }

    private void showNotification(
            String channelId,
            String title,
            String body,
            Intent intent,
            int notificationId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                pendingFlags
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(body)
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify(
                notificationId,
                builder.build()
        );
    }

    private void createNotificationChannel(
            String channelId,
            String channelName,
            String description) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            channelName,
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(description);
            channel.enableVibration(true);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
