
package com.example.arshithconnectattendance;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatAttachmentManager {

    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024;

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;

    public interface UploadCallback {
        void onSuccess(String downloadUrl, String fileName);
        void onFailure(String error);
    }

    public ChatAttachmentManager(Context context) {

        this.context = context.getApplicationContext();

        this.executor = Executors.newSingleThreadExecutor();

        this.mainHandler = new Handler(Looper.getMainLooper());

        this.auth = FirebaseAuth.getInstance();

        this.db = FirebaseFirestore.getInstance();

        this.storage = FirebaseStorage.getInstance();
    }

    // =====================================================
    // UPLOAD AND SEND ATTACHMENT
    // =====================================================

    public void uploadAndSend(
            Context activityContext,
            Uri fileUri,
            String conversationId,
            String chatType,
            String senderId,
            String senderAuthUid,
            String senderName,
            String senderRole,
            List<String> participantIds,
            List<String> participantAuthUids,
            List<String> memberBusinessIds,
            String teamId,
            String teamName,
            String replyToMessageId,
            String replyToSender,
            String replyToContent,
            UploadCallback callback
    ) {

        executor.execute(() -> {

            try {

                if (auth.getCurrentUser() == null) {
                    throw new Exception("Please sign in again.");
                }

                String currentUid = auth.getCurrentUser().getUid();

                if (!currentUid.equals(senderAuthUid)) {
                    throw new Exception("Invalid sender account.");
                }

                if (conversationId == null
                        || conversationId.trim().isEmpty()) {
                    throw new Exception("Invalid conversation.");
                }

                if (fileUri == null) {
                    throw new Exception("No file selected.");
                }

                String fileName = getFileName(fileUri);

                String mimeType = getMimeType(fileUri);

                validateMimeType(mimeType);

                byte[] fileBytes = readFile(fileUri);

                if (fileBytes.length == 0) {
                    throw new Exception("Selected file is empty.");
                }

                if (fileBytes.length > MAX_FILE_SIZE) {
                    throw new Exception(
                            "File size must not exceed 20 MB."
                    );
                }

                String uniqueName =
                        UUID.randomUUID() + "_" + sanitizeFileName(fileName);

                // -------------------------------------------------
                // STEP 1: FIREBASE STORAGE UPLOAD
                // -------------------------------------------------

                String storagePath =
                        "chatAttachments/"
                                + conversationId
                                + "/"
                                + currentUid
                                + "/"
                                + uniqueName;

                StorageReference fileReference =
                        storage.getReference().child(storagePath);

                StorageMetadata metadata =
                        new StorageMetadata.Builder()
                                .setContentType(mimeType)
                                .setCustomMetadata("originalFileName", fileName)
                                .setCustomMetadata("senderAuthUid", currentUid)
                                .build();

                UploadTask uploadTask =
                        fileReference.putBytes(fileBytes, metadata);

                uploadTask
                        .addOnSuccessListener(taskSnapshot -> {

                            fileReference.getDownloadUrl()
                                    .addOnSuccessListener(downloadUri -> {

                                        String downloadUrl =
                                                downloadUri.toString();

                                        // STEP 2: CREATE FIRESTORE MESSAGE
                                        saveAttachmentMessage(
                                                conversationId,
                                                chatType,
                                                currentUid,
                                                senderName,
                                                senderRole,
                                                fileName,
                                                mimeType,
                                                fileBytes.length,
                                                downloadUrl,
                                                fileReference,
                                                teamId,
                                                teamName,
                                                replyToMessageId,
                                                replyToSender,
                                                replyToContent,
                                                callback
                                        );
                                    })
                                    .addOnFailureListener(error -> {

                                        deleteUploadedFile(fileReference);

                                        notifyFailure(
                                                callback,
                                                "Unable to get attachment URL: "
                                                        + error.getMessage()
                                        );
                                    });
                        })
                        .addOnFailureListener(error -> {

                            notifyFailure(
                                    callback,
                                    "Firebase upload failed: "
                                            + error.getMessage()
                            );
                        });

            } catch (Exception error) {

                notifyFailure(
                        callback,
                        error.getMessage() == null
                                ? "Attachment upload failed."
                                : error.getMessage()
                );
            }
        });
    }

    // =====================================================
    // SAVE ATTACHMENT MESSAGE IN FIRESTORE
    // =====================================================

    private void saveAttachmentMessage(
            String conversationId,
            String chatType,
            String senderUid,
            String senderName,
            String senderRole,
            String fileName,
            String mimeType,
            long fileSize,
            String downloadUrl,
            StorageReference fileReference,
            String teamId,
            String teamName,
            String replyToMessageId,
            String replyToSender,
            String replyToContent,
            UploadCallback callback
    ) {

        DocumentReference conversationRef =
                db.collection("conversations")
                        .document(conversationId);

        DocumentReference messageRef =
                conversationRef.collection("messages")
                        .document();

        db.runTransaction(transaction -> {

            DocumentSnapshot conversation =
                    transaction.get(conversationRef);

            if (!conversation.exists()) {
                throw new RuntimeException("Conversation not found.");
            }

            List<String> memberIds =
                    getStringList(conversation.get("memberIds"));

            if (!memberIds.contains(senderUid)) {
                throw new RuntimeException(
                        "You are not a member of this conversation."
                );
            }

            Map<String, Object> unreadCounts =
                    getMap(conversation.get("unreadCounts"));

            Map<String, Object> updatedUnread =
                    new HashMap<>(unreadCounts);

            for (String memberUid : memberIds) {

                if (memberUid.equals(senderUid)) {

                    updatedUnread.put(memberUid, 0);

                } else {

                    Object previous = updatedUnread.get(memberUid);

                    int count = previous instanceof Number
                            ? ((Number) previous).intValue()
                            : 0;

                    updatedUnread.put(memberUid, count + 1);
                }
            }

            boolean isImage = mimeType.startsWith("image/");

            boolean isAudio = mimeType.startsWith("audio/");

            Map<String, Object> attachment =
                    new HashMap<>();

            attachment.put("url", downloadUrl);
            attachment.put("filename", fileName);
            attachment.put("content_type", mimeType);
            attachment.put("file_size", fileSize);
            attachment.put("is_image", isImage);
            attachment.put("is_audio", isAudio);

            List<Map<String, Object>> attachments =
                    new ArrayList<>();

            attachments.add(attachment);

            Map<String, Object> message =
                    new HashMap<>();

            message.put("senderId", senderUid);
            message.put("senderAuthUid", senderUid);
            message.put("senderName", safe(senderName));
            message.put("senderRole", safe(senderRole));

            // Filename provides nonempty content for attachment messages.
            message.put("content", fileName);

            message.put(
                    "chatType",
                    safe(conversation.getString("type")).isEmpty()
                            ? safe(chatType)
                            : conversation.getString("type")
            );

            message.put(
                    "messageType",
                    isImage ? "image" : isAudio ? "audio" : "file"
            );

            message.put("attachmentUrl", downloadUrl);
            message.put("attachmentName", fileName);
            message.put("attachments", attachments);

            message.put("timestamp", Timestamp.now());

            message.put(
                    "readBy",
                    java.util.Collections.singletonList(senderUid)
            );

            message.put(
                    "deliveredTo",
                    new ArrayList<String>()
            );

            message.put("participantIds", memberIds);

            message.put("teamId", safe(teamId));
            message.put("teamName", safe(teamName));

            if (!safe(replyToMessageId).isEmpty()) {

                message.put("replyToMessageId", replyToMessageId);
                message.put("replyToSender", safe(replyToSender));
                message.put("replyToContent", safe(replyToContent));
            }

            transaction.set(messageRef, message);

            Map<String, Object> conversationUpdate =
                    new HashMap<>();

            conversationUpdate.put(
                    "lastMessage",
                    isImage ? "📷 Photo" :
                            isAudio ? "🎵 Audio" :
                            "📎 " + fileName
            );

            conversationUpdate.put(
                    "lastMessageTime",
                    Timestamp.now()
            );

            conversationUpdate.put(
                    "lastSenderId",
                    senderUid
            );

            conversationUpdate.put(
                    "unreadCounts",
                    updatedUnread
            );

            transaction.update(
                    conversationRef,
                    conversationUpdate
            );

            return messageRef.getId();

        }).addOnSuccessListener(messageId -> {

            notifySuccess(
                    callback,
                    downloadUrl,
                    fileName
            );

        }).addOnFailureListener(error -> {

            // Remove the uploaded file if its message could not be saved.
            deleteUploadedFile(fileReference);

            notifyFailure(
                    callback,
                    "Message could not be saved: "
                            + error.getMessage()
            );
        });
    }

    // =====================================================
    // FILE READING
    // =====================================================

    private byte[] readFile(Uri uri) throws Exception {

        ContentResolver resolver =
                context.getContentResolver();

        try (
                InputStream input = resolver.openInputStream(uri);
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            if (input == null) {
                throw new Exception(
                        "Unable to open selected file."
                );
            }

            byte[] buffer = new byte[8192];

            int count;

            while ((count = input.read(buffer)) != -1) {

                output.write(buffer, 0, count);

                if (output.size() > MAX_FILE_SIZE) {
                    throw new Exception(
                            "File size must not exceed 20 MB."
                    );
                }
            }

            return output.toByteArray();
        }
    }

    // =====================================================
    // FILE NAME
    // =====================================================

    private String getFileName(Uri uri) {

        String result = null;

        if ("content".equals(uri.getScheme())) {

            try (
                    Cursor cursor = context.getContentResolver().query(
                            uri,
                            null,
                            null,
                            null,
                            null
                    )
            ) {

                if (cursor != null && cursor.moveToFirst()) {

                    int index = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                    );

                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }

            } catch (Exception ignored) {
            }
        }

        if (result == null || result.trim().isEmpty()) {
            result = uri.getLastPathSegment();
        }

        if (result == null || result.trim().isEmpty()) {
            result = "attachment_" + System.currentTimeMillis();
        }

        return result;
    }

    private String sanitizeFileName(String fileName) {

        if (fileName == null || fileName.trim().isEmpty()) {
            return "attachment";
        }

        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // =====================================================
    // MIME TYPE
    // =====================================================

    private String getMimeType(Uri uri) {

        String mimeType =
                context.getContentResolver().getType(uri);

        if (mimeType != null && !mimeType.trim().isEmpty()) {
            return mimeType;
        }

        String extension =
                MimeTypeMap.getFileExtensionFromUrl(
                        uri.toString()
                );

        if (extension != null) {

            String guessed = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(
                            extension.toLowerCase()
                    );

            if (guessed != null) {
                return guessed;
            }
        }

        return "application/octet-stream";
    }

    private void validateMimeType(String mimeType) throws Exception {

        boolean allowed =
                mimeType.matches("image/(jpeg|png|gif|webp)")
                        || mimeType.equals("application/pdf")
                        || mimeType.equals("text/plain")
                        || mimeType.equals("application/msword")
                        || mimeType.equals(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
                        || mimeType.equals("application/vnd.ms-excel")
                        || mimeType.equals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                        || mimeType.equals("application/vnd.ms-powerpoint")
                        || mimeType.equals(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
                        || mimeType.matches(
                        "audio/(aac|mp4|mpeg|wav|3gpp|ogg|opus)"
                )
                        || mimeType.equals("audio/x-wav")
                        || mimeType.equals("audio/m4a");

        if (!allowed) {
            throw new Exception("This file format is not supported.");
        }
    }

    // =====================================================
    // FIRESTORE HELPERS
    // =====================================================

    private List<String> getStringList(Object value) {

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

    private Map<String, Object> getMap(Object value) {

        Map<String, Object> result = new HashMap<>();

        if (value instanceof Map<?, ?>) {

            for (Map.Entry<?, ?> entry :
                    ((Map<?, ?>) value).entrySet()) {

                if (entry.getKey() instanceof String) {

                    result.put(
                            (String) entry.getKey(),
                            entry.getValue()
                    );
                }
            }
        }

        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // =====================================================
    // CALLBACKS
    // =====================================================

    private void notifySuccess(
            UploadCallback callback,
            String url,
            String fileName
    ) {

        if (callback == null) return;

        mainHandler.post(() ->
                callback.onSuccess(url, fileName)
        );
    }

    private void notifyFailure(
            UploadCallback callback,
            String error
    ) {

        if (callback == null) return;

        mainHandler.post(() ->
                callback.onFailure(error)
        );
    }

    // =====================================================
    // DELETE UPLOAD IF MESSAGE SAVE FAILS
    // =====================================================

    private void deleteUploadedFile(StorageReference reference) {

        reference.delete()
                .addOnFailureListener(error -> {
                    // Keep the original upload/message error.
                });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
