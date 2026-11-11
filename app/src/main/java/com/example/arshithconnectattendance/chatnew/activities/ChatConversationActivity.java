
package com.example.arshithconnectattendance.chatnew.activities;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.view.MotionEvent;

import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.adapters.ChatMessageAdapter;
import com.example.arshithconnectattendance.chatnew.model.ChatMessage;
import com.example.arshithconnectattendance.chatnew.repository.ChatMessageRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatReadRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatPresenceRepository;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatConversationActivity extends AppCompatActivity
        implements ChatMessageAdapter.MessageActionListener {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    public static final String EXTRA_CONTACT_UID = "contact_uid";
    public static final String EXTRA_FORWARD_MESSAGE_ID = "forward_message_id";

    private static final String TAG = "CHAT_UPLOAD_DEBUG";
    private static final String STORAGE_FOLDER = "chatAttachments";

    private TextView tvChatContactName;
    private TextView tvChatContactStatus;

    private LinearLayout groupInfoHeader;
    private EditText etChatMessage;

    private ImageButton btnSendChatMessage;
    private ImageButton btnChatBack;
    private ImageButton btnChatAttachment;
    private ImageButton btnChatOptions;
    private ImageView imgChatAvatar;

    private ImageButton btnRecordVoice;

    private MediaRecorder mediaRecorder;
    private File audioFile;

    private boolean isRecordingAudio = false;
    private long recordingStartTime = 0L;

    private ActivityResultLauncher<String> audioPermissionLauncher;

    private RecyclerView recyclerChatMessages;

    private ChatMessageAdapter messageAdapter;

    private ChatMessageRepository messageRepository;
    private ChatReadRepository readRepository;
    private ChatPresenceRepository presenceRepository;

    // Reply preview views
    private LinearLayout replyPreviewContainer;
    private TextView tvReplyPreviewTitle;
    private TextView tvReplyPreviewText;
    private ImageButton btnCancelReply;

    private ChatMessage replyingToMessage;

    private ListenerRegistration messagesListener;
    private ListenerRegistration presenceListener;
    private ListenerRegistration typingListener;

    private final Handler typingHandler =
            new Handler(Looper.getMainLooper());

    private boolean contactIsOnline = false;
    private long contactLastSeen = 0;
    private boolean contactIsTyping = false;

    private boolean typingUpdateSent = false;

    private Runnable stopTypingRunnable;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> documentLauncher;

    private String conversationId;
    private String currentUid;
    private String contactUid;
    private boolean isGroupChat = false;
    private String groupName = "";
    private final Map<String, String> senderNames = new HashMap<>();

    private Uri cameraImageUri;

    // Read-status update control
    private boolean readUpdateInProgress = false;
    private boolean readUpdatePending = false;

    private boolean imageUploadInProgress = false;
    private boolean documentUploadInProgress = false;
    private boolean clearingHistory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            Log.d("GROUP_DEBUG", "Current Firebase UID: " + currentUser.getUid());
        } else {
            Log.d("GROUP_DEBUG", "No user is signed in");
        }

        setContentView(R.layout.activity_chat_conversation);

        initializeViews();

        conversationId = getIntent().getStringExtra(
                EXTRA_CONVERSATION_ID
        );

        String contactName = getIntent().getStringExtra(
                EXTRA_CONTACT_NAME
        );

        contactUid = getIntent().getStringExtra(
                EXTRA_CONTACT_UID
        );

        currentUid = FirebaseAuth.getInstance().getUid();

        if (conversationId == null
                || conversationId.trim().isEmpty()
                || currentUid == null) {

            Toast.makeText(
                    this,
                    "Unable to open conversation.",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        tvChatContactName.setText(
                contactName == null || contactName.trim().isEmpty()
                        ? "Chat"
                        : contactName
        );

        tvChatContactStatus.setText("Offline");

// Load the contact's profile photo in the chat header.
        messageRepository = new ChatMessageRepository();
        readRepository = new ChatReadRepository();
        presenceRepository = new ChatPresenceRepository();

        setupRecyclerView();
        setupImagePickers();
        setupVoiceRecording();

// Determine whether this is a group or direct conversation
// before starting presence, typing and message listeners.
        loadConversationDetailsAndStartChat();

        logFirebaseConfiguration();
    }

    // --------------------------------------------------
    // INITIALIZATION
    // --------------------------------------------------

    private void initializeViews() {

        tvChatContactName = findViewById(R.id.tvChatContactName);
        tvChatContactStatus = findViewById(R.id.tvChatContactStatus);

        groupInfoHeader = findViewById(R.id.groupInfoHeader);

        etChatMessage = findViewById(R.id.etChatMessage);

        replyPreviewContainer =
                findViewById(R.id.replyPreviewContainer);

        tvReplyPreviewTitle =
                findViewById(R.id.tvReplyPreviewTitle);

        tvReplyPreviewText =
                findViewById(R.id.tvReplyPreviewText);

        btnCancelReply =
                findViewById(R.id.btnCancelReply);

        btnSendChatMessage =
                findViewById(R.id.btnSendChatMessage);
        imgChatAvatar = findViewById(R.id.imgChatAvatar);

        btnChatBack =
                findViewById(R.id.btnChatBack);

        btnChatAttachment =
                findViewById(R.id.btnChatAttachment);
        btnRecordVoice = findViewById(R.id.btnRecordVoice);

        btnChatOptions =
                findViewById(R.id.btnChatOptions);

        recyclerChatMessages =
                findViewById(R.id.recyclerChatMessages);
    }

    private void setupRecyclerView() {

        messageAdapter = new ChatMessageAdapter(currentUid, this);

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this);

        layoutManager.setStackFromEnd(true);

        recyclerChatMessages.setLayoutManager(layoutManager);
        recyclerChatMessages.setAdapter(messageAdapter);
    }

    // --------------------------------------------------
    // REPLY FEATURE
    // --------------------------------------------------

    @Override
    public void onReply(ChatMessage message) {

        if (message == null || message.isDeleted()) {
            return;
        }

        if (message.getMessageId() == null
                || message.getMessageId().trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Invalid message.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        replyingToMessage = message;

        tvReplyPreviewTitle.setText("Replying to message");

        String preview = message.getText();

        if ("image".equals(message.getType())) {

            preview = "📷 Photo";

        } else if ("document".equals(message.getType())) {

            preview = "📄 " + (
                    preview == null || preview.trim().isEmpty()
                            ? "Document"
                            : preview
            );

        } else if (preview == null || preview.trim().isEmpty()) {

            preview = "Message";
        }

        tvReplyPreviewText.setText(preview);

        replyPreviewContainer.setVisibility(View.VISIBLE);

        etChatMessage.setHint("Type your reply");
        etChatMessage.requestFocus();
    }

    private void clearReplyPreview() {

        replyingToMessage = null;

        if (replyPreviewContainer != null) {
            replyPreviewContainer.setVisibility(View.GONE);
        }

        if (etChatMessage != null) {
            etChatMessage.setHint("Type a message");
        }
    }

    private void clearReplyIfSame(ChatMessage target) {

        if (target != null && replyingToMessage == target) {
            clearReplyPreview();
        }
    }

    // --------------------------------------------------
    // EDIT MESSAGE
    // --------------------------------------------------

    @Override
    public void onEdit(ChatMessage message) {

        if (message == null || message.isDeleted()) {
            return;
        }

        if (!"text".equals(message.getType())) {

            Toast.makeText(
                    this,
                    "Only text messages can be edited.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        EditText editText = new EditText(this);

        editText.setText(message.getText());
        editText.setSelection(editText.getText().length());
        editText.setMinLines(2);
        editText.setMaxLines(6);

        new AlertDialog.Builder(this)
                .setTitle("Edit message")
                .setView(editText)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {

                    String updatedText =
                            editText.getText().toString().trim();

                    if (updatedText.isEmpty()) {

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Message cannot be empty.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    messageRepository.editTextMessage(
                            message.getMessageId(),
                            updatedText,
                            new ChatMessageRepository.MessageCallback() {

                                @Override
                                public void onSuccess() {

                                    Toast.makeText(
                                            ChatConversationActivity.this,
                                            "Message edited.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }

                                @Override
                                public void onError(String error) {

                                    Toast.makeText(
                                            ChatConversationActivity.this,
                                            error,
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    );
                })
                .show();
    }

    // --------------------------------------------------
    // DELETE MESSAGE
    // --------------------------------------------------

    @Override
    public void onDelete(ChatMessage message) {

        if (message == null) {
            return;
        }

        messageRepository.deleteMessage(
                message.getMessageId(),
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Message deleted.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String error) {

                        Toast.makeText(
                                ChatConversationActivity.this,
                                error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // --------------------------------------------------
    // FORWARD MESSAGE
    // --------------------------------------------------

    @Override
    public void onForward(ChatMessage message) {

        if (message == null) {

            Toast.makeText(
                    this,
                    "Invalid message.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (message.isDeleted()) {

            Toast.makeText(
                    this,
                    "Deleted messages cannot be forwarded.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String messageId = message.getMessageId();

        if (messageId == null || messageId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Message ID is missing.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent = new Intent(
                ChatConversationActivity.this,
                ChatContactsActivity.class
        );

        intent.putExtra(
                EXTRA_FORWARD_MESSAGE_ID,
                messageId
        );

        startActivity(intent);
    }

    private void loadContactAvatar() {
        if (contactUid == null || contactUid.trim().isEmpty()
                || imgChatAvatar == null) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("employees")
                .document(contactUid)
                .get()
                .addOnSuccessListener(document -> {
                    String photoUrl = null;

                    if (document.exists() && document.get("photoUrl") != null) {
                        photoUrl = String.valueOf(document.get("photoUrl"));
                    }

                    if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(android.R.drawable.ic_menu_myplaces)
                                .error(android.R.drawable.ic_menu_myplaces)
                                .circleCrop()
                                .into(imgChatAvatar);
                    } else {
                        loadContactAvatarFromChatUsers();
                    }
                })
                .addOnFailureListener(error -> loadContactAvatarFromChatUsers());
    }

    private void loadContactAvatarFromChatUsers() {
        FirebaseFirestore.getInstance()
                .collection("chat_users")
                .document(contactUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.get("photoUrl") != null) {
                        String photoUrl = String.valueOf(document.get("photoUrl"));

                        if (!photoUrl.trim().isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(android.R.drawable.ic_menu_myplaces)
                                    .error(android.R.drawable.ic_menu_myplaces)
                                    .circleCrop()
                                    .into(imgChatAvatar);
                            return;
                        }
                    }

                    imgChatAvatar.setImageResource(
                            android.R.drawable.ic_menu_myplaces
                    );
                })
                .addOnFailureListener(error ->
                        imgChatAvatar.setImageResource(
                                android.R.drawable.ic_menu_myplaces
                        )
                );
    }
    // --------------------------------------------------
    // BUTTONS
    // --------------------------------------------------

    private void setupButtons() {

        btnChatBack.setOnClickListener(v -> finish());

        btnSendChatMessage.setOnClickListener(v -> sendMessage());

        // Open profile when avatar or contact details are clicked.
        View.OnClickListener profileClickListener =
                v -> openGroupInfo();

        groupInfoHeader.setOnClickListener(profileClickListener);
        imgChatAvatar.setOnClickListener(profileClickListener);

        btnChatAttachment.setOnClickListener(
                v -> showAttachmentOptions()
        );

        btnChatOptions.setOnClickListener(
                v -> showChatOptions()
        );

        btnCancelReply.setOnClickListener(
                v -> clearReplyPreview()
        );
    }
    // --------------------------------------------------
    // CHAT OPTIONS MENU
    // --------------------------------------------------

    private void showChatOptions() {

        PopupMenu popupMenu = new PopupMenu(
                ChatConversationActivity.this,
                btnChatOptions
        );

        popupMenu.getMenu().add(
                0,
                1,
                0,
                "Clear chat history"
        );

        popupMenu.setOnMenuItemClickListener(
                item -> {

                    if (item.getItemId() == 1) {
                        confirmClearChatHistory();
                        return true;
                    }

                    return false;
                }
        );

        popupMenu.show();
    }

    // --------------------------------------------------
    // CONFIRM CLEAR CHAT HISTORY
    // --------------------------------------------------

    private void confirmClearChatHistory() {

        new AlertDialog.Builder(this)
                .setTitle("Clear chat history?")
                .setMessage(
                        "This will hide all existing messages from your view " +
                                "in this conversation.\n\n" +
                                "Other participants will still be able to see their messages. " +
                                "New messages will appear normally."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) ->
                        performClearChatHistory()
                )
                .show();
    }
    // --------------------------------------------------
// LOAD CONVERSATION DETAILS
// --------------------------------------------------

    private void loadConversationDetailsAndStartChat() {

        FirebaseFirestore.getInstance()
                .collection("chat_conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(document -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!document.exists()) {
                        Toast.makeText(
                                this,
                                "Conversation not found.",
                                Toast.LENGTH_SHORT
                        ).show();
                        finish();
                        return;
                    }

                    String type = document.getString("type");

                    isGroupChat = "group".equalsIgnoreCase(type);

                    if (isGroupChat) {

                        groupName = document.getString("name");

                        if (groupName == null || groupName.trim().isEmpty()) {
                            groupName = "Group Chat";
                        }

                        tvChatContactName.setText(groupName);
                        tvChatContactStatus.setText("");

                        // Do not treat the notification sender as a direct contact.
                        contactUid = null;

                        // Enable sender names in received group messages.
                        messageAdapter.setGroupChat(true);

                        loadGroupSenderNames(document.get("memberIds"));

                        loadGroupAvatar(document.getString("groupPhotoUrl"));

                        // No one-to-one presence or typing listeners for groups.
                        listenForMessages();

                    } else {

                        // Keep existing direct-chat behaviour.
                        messageAdapter.setGroupChat(false);

                        loadContactAvatar();

                        listenForContactPresence();
                        setupTypingDetection();

                        listenForMessages();
                    }
                })
                .addOnFailureListener(error -> {

                    Log.e(
                            "CHAT_CONVERSATION",
                            "Unable to load conversation details.",
                            error
                    );

                    Toast.makeText(
                            this,
                            "Unable to load conversation: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }


// --------------------------------------------------
// LOAD GROUP MEMBER DISPLAY NAMES
// --------------------------------------------------

    private void loadGroupSenderNames(Object membersObject) {

        if (!(membersObject instanceof List<?>)) {
            return;
        }

        List<?> members = (List<?>) membersObject;

        senderNames.clear();

        if (members.isEmpty()) {
            messageAdapter.setSenderNames(senderNames);
            return;
        }

        final int[] completed = {0};
        final int total = members.size();

        for (Object member : members) {

            if (!(member instanceof String)) {
                completed[0]++;
                continue;
            }

            String uid = (String) member;

            if (uid.trim().isEmpty()) {
                completed[0]++;
                continue;
            }

            loadMemberDisplayName(uid, name -> {

                if (name != null && !name.trim().isEmpty()) {
                    senderNames.put(uid, name);
                }

                completed[0]++;

                if (completed[0] >= total) {

                    if (!isFinishing() && !isDestroyed()) {
                        messageAdapter.setSenderNames(senderNames);
                    }
                }
            });
        }
    }


// --------------------------------------------------
// LOAD ONE MEMBER NAME
// --------------------------------------------------

    private interface MemberNameCallback {
        void onLoaded(String name);
    }

    private void loadMemberDisplayName(
            String uid,
            MemberNameCallback callback
    ) {

        FirebaseFirestore.getInstance()
                .collection("employees")
                .document(uid)
                .get()
                .addOnSuccessListener(employee -> {

                    String name = getDisplayNameFromDocument(employee);

                    if (name != null) {
                        callback.onLoaded(name);
                        return;
                    }

                    loadMemberNameFromChatUsers(uid, callback);
                })
                .addOnFailureListener(error ->
                        loadMemberNameFromChatUsers(uid, callback)
                );
    }


    private void loadMemberNameFromChatUsers(
            String uid,
            MemberNameCallback callback
    ) {

        FirebaseFirestore.getInstance()
                .collection("chat_users")
                .document(uid)
                .get()
                .addOnSuccessListener(document ->
                        callback.onLoaded(getDisplayNameFromDocument(document))
                )
                .addOnFailureListener(error -> callback.onLoaded(null));
    }


// --------------------------------------------------
// EXTRACT DISPLAY NAME
// --------------------------------------------------

    private String getDisplayNameFromDocument(
            com.google.firebase.firestore.DocumentSnapshot document
    ) {

        if (document == null || !document.exists()) {
            return null;
        }

        String[] fields = {
                "name",
                "fullName",
                "employeeName",
                "displayName",
                "firstName"
        };

        for (String field : fields) {

            String value = document.getString(field);

            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return null;
    }


// --------------------------------------------------
// LOAD GROUP PHOTO
// --------------------------------------------------

    private void loadGroupAvatar(String photoUrl) {

        if (imgChatAvatar == null) {
            return;
        }

        if (photoUrl != null && !photoUrl.trim().isEmpty()) {

            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .circleCrop()
                    .into(imgChatAvatar);

        } else {

            imgChatAvatar.setImageResource(
                    android.R.drawable.ic_menu_myplaces
            );
        }
    }

    // --------------------------------------------------
    // CLEAR CHAT HISTORY
    // --------------------------------------------------

    private void performClearChatHistory() {

        if (clearingHistory) {
            return;
        }

        clearingHistory = true;
        btnChatOptions.setEnabled(false);

        messageRepository.clearChatHistory(
                conversationId,
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Chat history cleared.",
                                Toast.LENGTH_SHORT
                        ).show();

                        clearReplyPreview();

                        refreshMessagesAfterClear();

                        clearingHistory = false;
                        btnChatOptions.setEnabled(true);
                    }

                    @Override
                    public void onError(String message) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        clearingHistory = false;
                        btnChatOptions.setEnabled(true);

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Unable to clear chat: " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // --------------------------------------------------
    // REFRESH MESSAGES AFTER CLEARING
    // --------------------------------------------------

    private void refreshMessagesAfterClear() {

        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        messageRepository.getChatHistoryClearTime(
                conversationId,
                new ChatMessageRepository.ClearTimeCallback() {

                    @Override
                    public void onSuccess(Timestamp clearedAt) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        attachMessageListener(clearedAt);
                    }

                    @Override
                    public void onError(String message) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Unable to refresh chat history: " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // --------------------------------------------------
    // FIREBASE DIAGNOSTIC INFORMATION
    // --------------------------------------------------

    private void logFirebaseConfiguration() {

        FirebaseAuth auth = FirebaseAuth.getInstance();

        FirebaseStorage storage = FirebaseStorage.getInstance();

        Log.d(TAG, "========== FIREBASE CONFIGURATION ==========");

        Log.d(
                TAG,
                "Signed-in UID: " +
                        (auth.getUid() == null ? "NULL" : auth.getUid())
        );

        Log.d(TAG, "Conversation ID: " + conversationId);

        Log.d(
                TAG,
                "Storage root reference: " +
                        storage.getReference().toString()
        );

        Log.d(
                TAG,
                "Firestore project ID: " +
                        FirebaseFirestore.getInstance()
                                .getApp()
                                .getOptions()
                                .getProjectId()
        );

        Log.d(
                TAG,
                "Firebase Storage bucket: " +
                        storage.getReference().toString()
        );

        Log.d(TAG, "============================================");
    }

    // --------------------------------------------------
    // OPEN GROUP INFORMATION / EMPLOYEE PROFILE
    // --------------------------------------------------

    private void openGroupInfo() {

        FirebaseFirestore.getInstance()
                .collection("chat_conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Conversation not found.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    String type = document.getString("type");

                    if ("group".equalsIgnoreCase(type)) {

                        Intent intent = new Intent(
                                ChatConversationActivity.this,
                                GroupInfoActivity.class
                        );

                        intent.putExtra(
                                GroupInfoActivity.EXTRA_CONVERSATION_ID,
                                conversationId
                        );

                        startActivity(intent);
                        return;
                    }

                    String employeeUid = contactUid;

                    if (employeeUid == null
                            || employeeUid.trim().isEmpty()
                            || employeeUid.equals(currentUid)) {

                        Object membersObject =
                                document.get("memberIds");

                        if (membersObject instanceof List<?>) {

                            List<?> members = (List<?>) membersObject;

                            for (Object member : members) {

                                if (member instanceof String) {

                                    String uid = (String) member;

                                    if (!uid.trim().isEmpty()
                                            && !uid.equals(currentUid)) {

                                        employeeUid = uid;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (employeeUid == null
                            || employeeUid.trim().isEmpty()
                            || employeeUid.equals(currentUid)) {

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Employee profile could not be found.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    Intent intent = new Intent(
                            ChatConversationActivity.this,
                            ViewEmployeeProfileActivity.class
                    );

                    intent.putExtra(
                            ViewEmployeeProfileActivity.EXTRA_EMPLOYEE_UID,
                            employeeUid
                    );

                    startActivity(intent);

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            ChatConversationActivity.this,
                            "Unable to open information: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    Log.e(
                            "CHAT_PROFILE",
                            "Unable to load conversation information.",
                            e
                    );
                });
    }

    // --------------------------------------------------
    // ATTACHMENT PICKERS
    // --------------------------------------------------

    private void setupImagePickers() {

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {

                    if (uri != null) {
                        uploadImage(uri);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {

                    if (success && cameraImageUri != null) {

                        uploadImage(cameraImageUri);

                    } else {

                        cameraImageUri = null;

                        Toast.makeText(
                                this,
                                "Photo capture cancelled.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {

                    if (uri != null) {
                        uploadDocument(uri);
                    }
                }
        );
    }

    // --------------------------------------------------
    // ATTACHMENT MENU
    // --------------------------------------------------

    private void showAttachmentOptions() {

        String[] options = {
                "Gallery",
                "Camera",
                "Documents"
        };

        new AlertDialog.Builder(this)
                .setTitle("Attach")
                .setItems(options, (dialog, which) -> {

                    switch (which) {

                        case 0:
                            galleryLauncher.launch("image/*");
                            break;

                        case 1:
                            openCamera();
                            break;

                        case 2:
                            openDocumentPicker();
                            break;
                    }
                })
                .show();
    }

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------

    private void openCamera() {

        try {

            File picturesDirectory =
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES);

            if (picturesDirectory == null) {

                Toast.makeText(
                        this,
                        "Camera storage is unavailable.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!picturesDirectory.exists()
                    && !picturesDirectory.mkdirs()) {

                Toast.makeText(
                        this,
                        "Could not create photo folder.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            File photoFile = new File(
                    picturesDirectory,
                    "CHAT_" + System.currentTimeMillis() + ".jpg"
            );

            cameraImageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );

            cameraLauncher.launch(cameraImageUri);

        } catch (Exception e) {

            Log.e(TAG, "Camera opening failed.", e);

            Toast.makeText(
                    this,
                    "Could not open camera: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // --------------------------------------------------
    // DOCUMENT PICKER
    // --------------------------------------------------

    private void openDocumentPicker() {

        String[] mimeTypes = {
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        };

        documentLauncher.launch(mimeTypes);
    }

    // --------------------------------------------------
    // STORAGE REFERENCE AND DIAGNOSTICS
    // --------------------------------------------------

    private StorageReference createAttachmentReference(
            String fileName,
            String mimeType,
            long fileSize
    ) {

        if (currentUid == null || currentUid.trim().isEmpty()) {

            Log.e(TAG, "Upload stopped: Firebase UID is NULL.");
            return null;
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {

            Log.e(TAG, "Upload stopped: Conversation ID is empty.");
            return null;
        }

        String storagePath =
                STORAGE_FOLDER
                        + "/"
                        + conversationId
                        + "/"
                        + currentUid
                        + "/"
                        + fileName;

        StorageReference reference =
                FirebaseStorage.getInstance()
                        .getReference()
                        .child(storagePath);

        Log.d(TAG, "========== STORAGE UPLOAD CHECK ==========");
        Log.d(TAG, "Signed-in UID: " + currentUid);
        Log.d(TAG, "Conversation ID: " + conversationId);
        Log.d(TAG, "Storage path: " + reference.getPath());
        Log.d(TAG, "Storage bucket: " + reference);
        Log.d(TAG, "File name: " + fileName);
        Log.d(TAG, "MIME type: " + mimeType);
        Log.d(TAG, "File size: " + fileSize);
        Log.d(TAG, "==========================================");

        return reference;
    }

    private void logStorageError(String operation, Exception error) {

        Log.e(TAG, operation + " failed.", error);

        if (error instanceof StorageException) {

            StorageException storageError =
                    (StorageException) error;

            Log.e(
                    TAG,
                    "Storage error code: " +
                            storageError.getErrorCode()
            );

            Log.e(
                    TAG,
                    "HTTP result code: " +
                            storageError.getHttpResultCode()
            );

            Log.e(
                    TAG,
                    "Storage error message: " +
                            storageError.getMessage()
            );
        }
    }

    // --------------------------------------------------
    // DOCUMENT UPLOAD
    // --------------------------------------------------

    private void uploadDocument(Uri documentUri) {

        if (documentUploadInProgress) {
            return;
        }

        if (documentUri == null || currentUid == null) {

            Toast.makeText(
                    this,
                    "Invalid document.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final ChatMessage replyTarget = replyingToMessage;

        String mimeType = getContentResolver().getType(documentUri);

        if (mimeType == null || !isAllowedDocumentType(mimeType)) {

            Toast.makeText(
                    this,
                    "Unsupported document format: " + mimeType,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String originalFileName = getDocumentFileName(documentUri);

        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            originalFileName = "Document";
        }

        String safeFileName = originalFileName.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_"
        );

        if (safeFileName.length() > 150) {
            safeFileName = safeFileName.substring(0, 150);
        }

        String storageFileName =
                UUID.randomUUID() + "_" + safeFileName;

        long fileSize = getFileSize(documentUri);

        StorageReference documentReference =
                createAttachmentReference(
                        storageFileName,
                        mimeType,
                        fileSize
                );

        if (documentReference == null) {
            return;
        }

        documentUploadInProgress = true;
        btnChatAttachment.setEnabled(false);

        StorageMetadata metadata =
                new StorageMetadata.Builder()
                        .setContentType(mimeType)
                        .build();

        String finalOriginalFileName = originalFileName;

        documentReference.putFile(documentUri, metadata)

                .addOnSuccessListener(taskSnapshot -> {

                    Log.d(TAG, "Document upload successful.");

                    documentReference.getDownloadUrl()

                            .addOnSuccessListener(downloadUri -> {

                                Log.d(TAG, "Document download URL obtained.");

                                sendDocumentMessage(
                                        finalOriginalFileName,
                                        downloadUri.toString(),
                                        replyTarget
                                );
                            })

                            .addOnFailureListener(error -> {

                                logStorageError(
                                        "Document download URL",
                                        error
                                );

                                finishDocumentUpload(
                                        "Could not get document URL: "
                                                + error.getMessage()
                                );
                            });
                })

                .addOnFailureListener(error -> {

                    logStorageError("Document upload", error);

                    finishDocumentUpload(
                            "Document upload failed: "
                                    + error.getMessage()
                    );
                });
    }

    private boolean isAllowedDocumentType(String mimeType) {

        return mimeType.equals("application/pdf")
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
        );
    }

    private String getDocumentFileName(Uri uri) {

        String fileName = null;

        try (Cursor cursor = getContentResolver().query(
                uri,
                null,
                null,
                null,
                null
        )) {

            if (cursor != null && cursor.moveToFirst()) {

                int nameIndex =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "Document filename lookup failed.", e);
        }

        return fileName;
    }

    private long getFileSize(Uri uri) {

        long size = -1;

        try (Cursor cursor = getContentResolver().query(
                uri,
                null,
                null,
                null,
                null
        )) {

            if (cursor != null && cursor.moveToFirst()) {

                int sizeIndex =
                        cursor.getColumnIndex(OpenableColumns.SIZE);

                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "File size lookup failed.", e);
        }

        return size;
    }

    // --------------------------------------------------
    // SEND DOCUMENT MESSAGE
    // --------------------------------------------------

    private void sendDocumentMessage(
            String fileName,
            String documentUrl,
            ChatMessage replyTarget
    ) {

        ChatMessageRepository.MessageCallback callback =
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        Log.d(TAG, "Document message saved in Firestore.");

                        clearReplyIfSame(replyTarget);

                        finishDocumentUpload(null);

                        markConversationAsRead();
                    }

                    @Override
                    public void onError(String message) {

                        Log.e(
                                TAG,
                                "Document Firestore save failed: " + message
                        );

                        finishDocumentUpload(
                                "Document message failed: " + message
                        );
                    }
                };

        if (replyTarget != null) {

            messageRepository.sendReplyMessage(
                    conversationId,
                    "document",
                    fileName,
                    documentUrl,
                    replyTarget,
                    callback
            );

        } else {

            messageRepository.sendDocumentMessage(
                    conversationId,
                    fileName,
                    documentUrl,
                    callback
            );
        }
    }

    private void finishDocumentUpload(String error) {

        documentUploadInProgress = false;

        if (isFinishing() || isDestroyed()) {
            return;
        }

        btnChatAttachment.setEnabled(true);

        if (error != null) {

            Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_LONG
            ).show();

        } else {

            Toast.makeText(
                    this,
                    "Document sent.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // --------------------------------------------------
    // IMAGE UPLOAD
    // --------------------------------------------------

    private void uploadImage(Uri imageUri) {

        if (imageUploadInProgress) {
            return;
        }

        if (imageUri == null || currentUid == null) {

            Toast.makeText(
                    this,
                    "Invalid image.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final ChatMessage replyTarget = replyingToMessage;

        String mimeType = getContentResolver().getType(imageUri);

        if (mimeType == null) {
            mimeType = "image/jpeg";
        }

        if (!mimeType.matches("image/(jpeg|png|gif|webp)")) {

            Toast.makeText(
                    this,
                    "Unsupported image format: " + mimeType,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String extension;

        if ("image/png".equals(mimeType)) {
            extension = ".png";
        } else if ("image/gif".equals(mimeType)) {
            extension = ".gif";
        } else if ("image/webp".equals(mimeType)) {
            extension = ".webp";
        } else {
            extension = ".jpg";
        }

        String fileName = "IMG_" + UUID.randomUUID() + extension;

        long fileSize = getFileSize(imageUri);

        StorageReference imageReference =
                createAttachmentReference(
                        fileName,
                        mimeType,
                        fileSize
                );

        if (imageReference == null) {
            return;
        }

        imageUploadInProgress = true;
        btnChatAttachment.setEnabled(false);

        StorageMetadata metadata =
                new StorageMetadata.Builder()
                        .setContentType(mimeType)
                        .build();

        Log.d(TAG, "Image URI: " + imageUri);

        imageReference.putFile(imageUri, metadata)

                .addOnSuccessListener(taskSnapshot -> {

                    Log.d(TAG, "Image upload successful.");

                    imageReference.getDownloadUrl()

                            .addOnSuccessListener(downloadUri -> {

                                Log.d(TAG, "Image download URL obtained.");

                                sendImageMessage(
                                        downloadUri.toString(),
                                        replyTarget
                                );
                            })

                            .addOnFailureListener(error -> {

                                logStorageError(
                                        "Image download URL",
                                        error
                                );

                                finishImageUpload(
                                        "Could not get image URL: "
                                                + error.getMessage()
                                );
                            });
                })

                .addOnFailureListener(error -> {

                    logStorageError("Image upload", error);

                    finishImageUpload(
                            "Image upload failed: "
                                    + error.getMessage()
                    );
                });
    }

    // --------------------------------------------------
    // SEND IMAGE MESSAGE
    // --------------------------------------------------

    private void sendImageMessage(
            String imageUrl,
            ChatMessage replyTarget
    ) {

        ChatMessageRepository.MessageCallback callback =
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        Log.d(TAG, "Image message saved in Firestore.");

                        clearReplyIfSame(replyTarget);

                        finishImageUpload(null);

                        markConversationAsRead();
                    }

                    @Override
                    public void onError(String message) {

                        Log.e(
                                TAG,
                                "Firestore image message failed: " + message
                        );

                        finishImageUpload(
                                "Image message failed: " + message
                        );
                    }
                };

        if (replyTarget != null) {

            messageRepository.sendReplyMessage(
                    conversationId,
                    "image",
                    "Photo",
                    imageUrl,
                    replyTarget,
                    callback
            );

        } else {

            messageRepository.sendImageMessage(
                    conversationId,
                    imageUrl,
                    callback
            );
        }
    }

    private void finishImageUpload(String error) {

        imageUploadInProgress = false;

        if (isFinishing() || isDestroyed()) {
            return;
        }

        btnChatAttachment.setEnabled(true);

        if (error != null) {

            Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_LONG
            ).show();

        } else {

            Toast.makeText(
                    this,
                    "Image sent.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // --------------------------------------------------
    // CONTACT PRESENCE
    // --------------------------------------------------

    // --------------------------------------------------
// CONTACT PRESENCE AND TYPING INDICATOR
// --------------------------------------------------

    private void listenForContactPresence() {

        if (contactUid == null || contactUid.trim().isEmpty()) {
            tvChatContactStatus.setText("");
            return;
        }

        presenceListener = presenceRepository.listenToPresence(
                contactUid,
                new ChatPresenceRepository.PresenceCallback() {

                    @Override
                    public void onPresenceChanged(
                            boolean isOnline,
                            long lastSeen
                    ) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        contactIsOnline = isOnline;
                        updateContactStatus(lastSeen);
                    }

                    @Override
                    public void onError(String message) {

                        if (!isFinishing() && !isDestroyed()) {
                            contactIsOnline = false;
                            contactIsTyping = false;
                            tvChatContactStatus.setText("Offline");
                        }
                    }
                }
        );

        typingListener = presenceRepository.listenToTyping(
                contactUid,
                conversationId,
                new ChatPresenceRepository.TypingCallback() {

                    @Override
                    public void onTypingChanged(boolean isTyping) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        contactIsTyping = isTyping;

                        if (contactIsTyping) {
                            tvChatContactStatus.setText("Typing…");
                        } else {
                            refreshContactStatus();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Log.e("CHAT_TYPING", "Typing listener error: " + message);
                    }
                }
        );
    }

    private void updateContactStatus(long lastSeen) {
        contactLastSeen = lastSeen;
        refreshContactStatus();
    }

    private void refreshContactStatus() {

        if (contactIsTyping) {
            tvChatContactStatus.setText("Typing…");

        } else if (contactIsOnline) {
            tvChatContactStatus.setText("Online");

        } else if (contactLastSeen > 0) {

            SimpleDateFormat formatter = new SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
            );

            String formattedTime = formatter.format(
                    new Date(contactLastSeen)
            );

            tvChatContactStatus.setText("Last seen " + formattedTime);

        } else {
            tvChatContactStatus.setText("Offline");
        }
    }

// --------------------------------------------------
// CURRENT USER TYPING DETECTION
// --------------------------------------------------

    private void setupTypingDetection() {

        Log.d("CHAT_TYPING", "setupTypingDetection() called");
        Log.d("CHAT_TYPING", "contactUid = " + contactUid);
        Log.d("CHAT_TYPING", "conversationId = " + conversationId);

        if (contactUid == null || contactUid.trim().isEmpty()) {
            Log.e("CHAT_TYPING",
                    "Typing detection stopped: contactUid is empty.");
            return;
        }

        if (etChatMessage == null) {
            Log.e("CHAT_TYPING",
                    "Typing detection stopped: etChatMessage is NULL.");
            return;
        }

        if (presenceRepository == null) {
            Log.e("CHAT_TYPING",
                    "Typing detection stopped: presenceRepository is NULL.");
            return;
        }

        stopTypingRunnable = () -> {

            Log.d("CHAT_TYPING", "Stop typing timer executed.");

            if (typingUpdateSent && presenceRepository != null) {

                presenceRepository.setTyping(
                        conversationId,
                        false,
                        new ChatPresenceRepository.UpdateCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d("CHAT_TYPING",
                                        "Typing status disabled successfully.");
                            }

                            @Override
                            public void onError(String message) {
                                Log.e("CHAT_TYPING",
                                        "Failed to stop typing: " + message);
                            }
                        }
                );

                typingUpdateSent = false;
            }
        };

        etChatMessage.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {

                Log.d("CHAT_TYPING",
                        "Text changed: " + s);

                if (isFinishing() || isDestroyed()) {
                    Log.d("CHAT_TYPING",
                            "Activity is closing. Ignoring text change.");
                    return;
                }

                if (stopTypingRunnable != null) {
                    typingHandler.removeCallbacks(stopTypingRunnable);
                }

                if (s != null && s.toString().trim().length() > 0) {

                    Log.d("CHAT_TYPING",
                            "User is typing a message.");

                    if (!typingUpdateSent) {

                        Log.d("CHAT_TYPING",
                                "Sending typing=true to Firestore.");

                        presenceRepository.setTyping(
                                conversationId,
                                true,
                                new ChatPresenceRepository.UpdateCallback() {

                                    @Override
                                    public void onSuccess() {
                                        Log.d("CHAT_TYPING",
                                                "Typing status enabled successfully.");
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Log.e("CHAT_TYPING",
                                                "Failed to enable typing: " + message);
                                    }
                                }
                        );

                        typingUpdateSent = true;
                    }

                    // Stop typing after 2 seconds without new input.
                    typingHandler.postDelayed(
                            stopTypingRunnable,
                            2000
                    );

                } else {

                    Log.d("CHAT_TYPING",
                            "Message input is empty. Stopping typing.");

                    stopTyping();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Log.d("CHAT_TYPING",
                "Typing detection initialized successfully.");
    }
    private void stopTyping() {

        typingHandler.removeCallbacks(stopTypingRunnable);

        if (typingUpdateSent && presenceRepository != null) {

            presenceRepository.setTyping(
                    conversationId,
                    false,
                    null
            );

            typingUpdateSent = false;
        }
    }

    // --------------------------------------------------
    // INDIVIDUAL MESSAGE READ RECEIPTS
    // --------------------------------------------------

    private void markIndividualMessagesAsRead() {

        if (messageRepository == null
                || conversationId == null
                || conversationId.trim().isEmpty()) {
            return;
        }

        messageRepository.markMessagesAsRead(
                conversationId,
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {
                        // Incoming messages marked as read.
                    }

                    @Override
                    public void onError(String message) {

                        Log.e(
                                "ChatReadReceipt",
                                "Could not mark messages as read: " + message
                        );
                    }
                }
        );
    }

    // --------------------------------------------------
    // REAL-TIME MESSAGE LISTENER
    // --------------------------------------------------

    private void listenForMessages() {

        messageRepository.getChatHistoryClearTime(
                conversationId,
                new ChatMessageRepository.ClearTimeCallback() {

                    @Override
                    public void onSuccess(Timestamp clearedAt) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        attachMessageListener(clearedAt);
                    }

                    @Override
                    public void onError(String message) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        Toast.makeText(
                                ChatConversationActivity.this,
                                "Unable to load chat history: " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // --------------------------------------------------
    // ATTACH FILTERED MESSAGE LISTENER
    // --------------------------------------------------

    private void attachMessageListener(Timestamp clearedAt) {

        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        messagesListener = messageRepository.listenToMessages(
                conversationId,
                clearedAt,
                new ChatMessageRepository.MessagesListener() {

                    @Override
                    public void onMessagesChanged(
                            List<ChatMessage> messages
                    ) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        messageAdapter.setMessages(messages);

                        if (!messages.isEmpty()) {

                            recyclerChatMessages.scrollToPosition(
                                    messages.size() - 1
                            );
                        }

                        // Mark incoming message documents as read.
                        markIndividualMessagesAsDelivered();
                        markIndividualMessagesAsRead();
                        markConversationAsRead();
                    }

                    @Override
                    public void onError(String message) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        Toast.makeText(
                                ChatConversationActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    // --------------------------------------------------
    // CONVERSATION READ STATUS
    // --------------------------------------------------

    private void markConversationAsRead() {

        if (conversationId == null
                || conversationId.trim().isEmpty()
                || readRepository == null
                || isFinishing()
                || isDestroyed()) {
            return;
        }

        // If an update is already running, remember that
        // another update must run after it finishes.
        if (readUpdateInProgress) {
            readUpdatePending = true;
            return;
        }

        readUpdateInProgress = true;

        readRepository.markConversationAsRead(
                conversationId,
                new ChatReadRepository.ReadCallback() {

                    @Override
                    public void onSuccess() {
                        finishReadUpdate();
                    }

                    @Override
                    public void onError(String message) {

                        Log.e(
                                TAG,
                                "Conversation read update failed: " + message
                        );

                        finishReadUpdate();
                    }
                }
        );
    }

    // --------------------------------------------------
    // FINISH READ UPDATE AND PROCESS PENDING UPDATE
    // --------------------------------------------------

    private void finishReadUpdate() {

        readUpdateInProgress = false;

        if (readUpdatePending) {

            readUpdatePending = false;

            if (!isFinishing() && !isDestroyed()) {
                markConversationAsRead();
            }
        }
    }

    // --------------------------------------------------
    // SEND TEXT MESSAGE
    // --------------------------------------------------

    private void sendMessage() {

        String text = etChatMessage.getText().toString().trim();

        if (text.isEmpty()) {
            return;
        }

        final ChatMessage replyTarget = replyingToMessage;

        btnSendChatMessage.setEnabled(false);

        ChatMessageRepository.MessageCallback callback =
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        etChatMessage.setText("");
                        btnSendChatMessage.setEnabled(true);

                        clearReplyIfSame(replyTarget);

                        markConversationAsRead();
                    }

                    @Override
                    public void onError(String message) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        btnSendChatMessage.setEnabled(true);

                        Toast.makeText(
                                ChatConversationActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                };

        if (replyTarget != null) {

            messageRepository.sendReplyMessage(
                    conversationId,
                    "text",
                    text,
                    "",
                    replyTarget,
                    callback
            );

        } else {

            messageRepository.sendTextMessage(
                    conversationId,
                    text,
                    callback
            );
        }
    }
    private void markIndividualMessagesAsDelivered() {
        if (messageRepository == null
                || conversationId == null
                || conversationId.trim().isEmpty()) {
            return;
        }

        messageRepository.markMessagesAsDelivered(
                conversationId,
                new ChatMessageRepository.MessageCallback() {
                    @Override
                    public void onSuccess() {
                        // Delivery receipts updated successfully.
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.e(
                                "CHAT_DELIVERY",
                                "Unable to mark messages as delivered: " + error
                        );
                    }
                }
        );
    }
    // --------------------------------------------------
// VOICE MESSAGE RECORDING
// --------------------------------------------------

    private void setupVoiceRecording() {

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    Log.d("CHAT_AUDIO", "RECORD_AUDIO granted = " + granted);

                    if (granted) {
                        Toast.makeText(this,
                                "Microphone permission granted. Press and hold again.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Microphone permission denied. Check app settings.",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        btnRecordVoice.setOnTouchListener((view, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED) {

                        audioPermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                        );

                        return true;
                    }

                    startVoiceRecording();
                    return true;

                case MotionEvent.ACTION_UP:

                case MotionEvent.ACTION_CANCEL:

                    if (isRecordingAudio) {
                        stopVoiceRecording(true);
                    }

                    return true;
            }

            return true;
        });
    }

    private void startVoiceRecording() {

        if (isRecordingAudio) {
            return;
        }

        if (imageUploadInProgress || documentUploadInProgress) {
            Toast.makeText(
                    this,
                    "Please wait for the current upload to finish.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {

            File audioDirectory = getExternalFilesDir(
                    Environment.DIRECTORY_MUSIC
            );

            if (audioDirectory == null) {
                throw new Exception("Audio storage is unavailable.");
            }

            if (!audioDirectory.exists() && !audioDirectory.mkdirs()) {
                throw new Exception("Could not create audio folder.");
            }

            audioFile = new File(
                    audioDirectory,
                    "VOICE_" + System.currentTimeMillis() + ".m4a"
            );

            mediaRecorder = new MediaRecorder();

            mediaRecorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
            );

            mediaRecorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
            );

            mediaRecorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
            );

            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);

            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());

            mediaRecorder.prepare();
            mediaRecorder.start();

            recordingStartTime = System.currentTimeMillis();
            isRecordingAudio = true;

            btnRecordVoice.setImageResource(
                    android.R.drawable.ic_media_pause
            );

            btnRecordVoice.setContentDescription(
                    "Release to send voice message"
            );

            etChatMessage.setEnabled(false);
            btnChatAttachment.setEnabled(false);
            btnSendChatMessage.setEnabled(false);

            Toast.makeText(
                    this,
                    "Recording... Release to send.",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Log.e("CHAT_AUDIO", "Recording could not start.", e);

            releaseMediaRecorder();

            Toast.makeText(
                    this,
                    "Could not start recording: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    private void stopVoiceRecording(boolean sendAudio) {

        if (!isRecordingAudio) {
            return;
        }

        long duration = System.currentTimeMillis() - recordingStartTime;
        File recordedFile = audioFile;

        // Prevent another touch event from stopping the recorder again.
        isRecordingAudio = false;

        boolean recordingStopped = false;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                recordingStopped = true;
            }

        } catch (RuntimeException e) {
            Log.e("CHAT_AUDIO", "MediaRecorder failed to stop.", e);

        } finally {
            releaseMediaRecorder();
            resetVoiceRecordingControls();
        }

        if (!sendAudio) {
            if (recordedFile != null && recordedFile.exists()) {
                recordedFile.delete();
            }
            audioFile = null;
            return;
        }

        if (!recordingStopped) {
            Log.e("CHAT_AUDIO", "Recording was not finalized. Upload cancelled.");

            if (recordedFile != null && recordedFile.exists()) {
                recordedFile.delete();
            }

            audioFile = null;

            Toast.makeText(
                    this,
                    "Recording failed. Please hold the button and try again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (duration < 700) {
            if (recordedFile != null && recordedFile.exists()) {
                recordedFile.delete();
            }

            audioFile = null;

            Toast.makeText(
                    this,
                    "Please record for at least one second.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (recordedFile == null
                || !recordedFile.exists()
                || recordedFile.length() <= 0) {

            Log.e("CHAT_AUDIO", "Audio file is missing or empty.");

            if (recordedFile != null && recordedFile.exists()) {
                recordedFile.delete();
            }

            audioFile = null;

            Toast.makeText(
                    this,
                    "No audio captured. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Log.d("CHAT_AUDIO",
                "Recording completed. Duration: " + duration
                        + " ms, File size: " + recordedFile.length());

        uploadVoiceMessage(recordedFile, duration);
    }


    private void releaseMediaRecorder() {

        if (mediaRecorder != null) {

            try {
                mediaRecorder.reset();
            } catch (Exception ignored) {
            }

            try {
                mediaRecorder.release();
            } catch (Exception ignored) {
            }

            mediaRecorder = null;
        }
    }

    private void resetVoiceRecordingControls() {

        if (btnRecordVoice != null) {

            btnRecordVoice.setImageResource(
                    android.R.drawable.ic_btn_speak_now
            );

            btnRecordVoice.setContentDescription(
                    "Hold to record voice message"
            );
        }

        if (etChatMessage != null) {
            etChatMessage.setEnabled(true);
        }

        if (btnChatAttachment != null) {
            btnChatAttachment.setEnabled(true);
        }

        if (btnSendChatMessage != null) {
            btnSendChatMessage.setEnabled(true);
        }
    }

// --------------------------------------------------
// UPLOAD VOICE MESSAGE
// --------------------------------------------------

    private void uploadVoiceMessage(File file, long duration) {

        if (file == null || !file.exists()) {
            Toast.makeText(
                    this,
                    "Voice recording file not found.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String fileName = "VOICE_" + UUID.randomUUID() + ".m4a";

        StorageReference audioReference = createAttachmentReference(
                fileName,
                "audio/mp4",
                file.length()
        );

        if (audioReference == null) {
            return;
        }

        btnRecordVoice.setEnabled(false);

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("audio/mp4")
                .build();

        audioReference.putFile(Uri.fromFile(file), metadata)

                .addOnSuccessListener(taskSnapshot ->

                        audioReference.getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {

                                    sendVoiceMessageToFirestore(
                                            downloadUri.toString(),
                                            duration,
                                            file
                                    );

                                })
                                .addOnFailureListener(error -> {

                                    logStorageError(
                                            "Voice download URL",
                                            error
                                    );

                                    finishVoiceUpload(
                                            file,
                                            "Could not get voice URL: "
                                                    + error.getMessage()
                                    );
                                })

                )
                .addOnFailureListener(error -> {

                    logStorageError("Voice upload", error);

                    finishVoiceUpload(
                            file,
                            "Voice upload failed: "
                                    + error.getMessage()
                    );
                });
    }

    private void sendVoiceMessageToFirestore(
            String audioUrl,
            long duration,
            File localFile
    ) {

        final ChatMessage replyTarget = replyingToMessage;

        ChatMessageRepository.MessageCallback callback =
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        clearReplyIfSame(replyTarget);

                        finishVoiceUpload(
                                localFile,
                                null
                        );

                        markConversationAsRead();
                    }

                    @Override
                    public void onError(String error) {

                        finishVoiceUpload(
                                localFile,
                                "Voice message could not be sent: " + error
                        );
                    }
                };

        if (replyTarget != null) {

            messageRepository.sendReplyMessage(
                    conversationId,
                    "audio",
                    "Voice message",
                    audioUrl,
                    replyTarget,
                    callback
            );

        } else {

            messageRepository.sendAudioMessage(
                    conversationId,
                    audioUrl,
                    duration,
                    callback
            );
        }
    }

    private void finishVoiceUpload(File file, String error) {

        if (file != null && file.exists()) {
            file.delete();
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        btnRecordVoice.setEnabled(true);

        if (error != null) {

            Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_LONG
            ).show();

        } else {

            Toast.makeText(
                    this,
                    "Voice message sent.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // --------------------------------------------------
    // ACTIVITY LIFECYCLE
    // --------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();

        if (presenceRepository != null) {
            presenceRepository.setOnline(null);
        }

        markIndividualMessagesAsDelivered();
        markIndividualMessagesAsRead();
        markConversationAsRead();
    }

    @Override
    protected void onPause() {
        stopTyping();
        if (isRecordingAudio) {
            stopVoiceRecording(false);
        }

        if (presenceRepository != null) {
            presenceRepository.setOffline(null);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isRecordingAudio) {
            stopVoiceRecording(false);
        }

        releaseMediaRecorder();
        super.onDestroy();

        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        if (presenceListener != null) {
            presenceListener.remove();
            presenceListener = null;
        }
        if (typingListener != null) {
            typingListener.remove();
            typingListener = null;
        }

        typingHandler.removeCallbacksAndMessages(null);
    }
}
