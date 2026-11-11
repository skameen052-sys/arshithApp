
package com.example.arshithconnectattendance.chatnew.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.adapters.ChatContactsAdapter;
import com.example.arshithconnectattendance.chatnew.model.ChatConversation;
import com.example.arshithconnectattendance.chatnew.model.ChatUser;
import com.example.arshithconnectattendance.chatnew.repository.ChatConversationRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatDirectoryRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatMessageRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatContactsActivity extends AppCompatActivity
        implements ChatContactsAdapter.OnContactClickListener {

    public static final String EXTRA_ADD_TO_GROUP =
            "extra_add_to_group";

    public static final String EXTRA_GROUP_CONVERSATION_ID =
            "extra_group_conversation_id";

    public static final String RESULT_SELECTED_MEMBER_IDS =
            "result_selected_member_ids";

    private RecyclerView recyclerContacts;
    private EditText etSearchContacts;
    private TextView tvContactsStatus;
    private Button btnForwardSelected;

    private ChatContactsAdapter adapter;

    private ChatDirectoryRepository directoryRepository;
    private ChatConversationRepository conversationRepository;
    private ChatMessageRepository messageRepository;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String currentUid;

    private final List<ChatUser> contactList = new ArrayList<>();

    // Group selection state
    private boolean isGroupSelectionMode = false;
    private boolean isAddToGroupMode = false;

    // Forwarding state
    private String forwardMessageId;
    private String forwardMessageType;
    private String forwardMediaUrl;

    private List<ChatUser> selectedForwardUsers = new ArrayList<>();

    private int forwardIndex = 0;
    private int forwardSuccessCount = 0;
    private int forwardFailureCount = 0;

    private boolean isForwarding = false;
    private boolean isForwardingMode = false;

    private static final long MAX_ATTACHMENT_SIZE =
            20L * 1024L * 1024L;

    // ==================================================
    // RETURN SELECTED MEMBERS TO GROUP INFO
    // ==================================================

    private void returnSelectedGroupMembers() {

        if (!isAddToGroupMode || adapter == null) {
            return;
        }

        List<ChatUser> selectedUsers = adapter.getSelectedUsers();

        if (selectedUsers == null || selectedUsers.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select at least one employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        ArrayList<String> selectedIds = new ArrayList<>();

        for (ChatUser user : selectedUsers) {

            if (user == null || user.getUid() == null) {
                continue;
            }

            String uid = user.getUid().trim();

            if (!uid.isEmpty()
                    && !uid.equals(currentUid)
                    && !selectedIds.contains(uid)) {

                selectedIds.add(uid);
            }
        }

        if (selectedIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "No valid employees selected.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent result = new Intent();

        result.putStringArrayListExtra(
                RESULT_SELECTED_MEMBER_IDS,
                selectedIds
        );

        setResult(RESULT_OK, result);
        finish();
    }

    // ==================================================
    // ON CREATE
    // ==================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat_contacts);

        recyclerContacts = findViewById(R.id.recyclerChatContacts);
        etSearchContacts = findViewById(R.id.etSearchContacts);
        tvContactsStatus = findViewById(R.id.tvContactsStatus);
        btnForwardSelected = findViewById(R.id.btnForwardSelected);

        if (recyclerContacts == null
                || etSearchContacts == null
                || tvContactsStatus == null) {

            Toast.makeText(
                    this,
                    "Unable to find chat contact views.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        currentUid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        directoryRepository = new ChatDirectoryRepository();
        conversationRepository = new ChatConversationRepository();
        messageRepository = new ChatMessageRepository();

        adapter = new ChatContactsAdapter(contactList, this);

        recyclerContacts.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerContacts.setAdapter(adapter);

        // Check forwarding mode.
        forwardMessageId = getIntent().getStringExtra(
                ChatConversationActivity.EXTRA_FORWARD_MESSAGE_ID
        );

        isForwardingMode = forwardMessageId != null
                && !forwardMessageId.trim().isEmpty();

        // Check Add Members mode.
        isAddToGroupMode = getIntent().getBooleanExtra(
                EXTRA_ADD_TO_GROUP,
                false
        );

        if (isForwardingMode) {

            adapter.setSelectionMode(true);

            if (btnForwardSelected != null) {

                btnForwardSelected.setVisibility(View.VISIBLE);
                btnForwardSelected.setText("Forward Selected");

                btnForwardSelected.setOnClickListener(v ->
                        startForwarding()
                );
            }

            setTitle("Forward Message");

            tvContactsStatus.setVisibility(View.VISIBLE);
            tvContactsStatus.setText(
                    "Select employees to forward the message."
            );

        } else if (isAddToGroupMode) {

            adapter.clearSelection();
            adapter.setSelectionMode(true);

            if (btnForwardSelected != null) {

                btnForwardSelected.setVisibility(View.VISIBLE);
                btnForwardSelected.setEnabled(true);
                btnForwardSelected.setText("Add Selected");

                btnForwardSelected.setOnClickListener(v ->
                        returnSelectedGroupMembers()
                );
            }

            setTitle("Add Group Members");

            tvContactsStatus.setVisibility(View.VISIBLE);
            tvContactsStatus.setText(
                    "Select employees to add to this group."
            );

        } else {

            setTitle("New Chat");

            if (btnForwardSelected != null) {

                btnForwardSelected.setVisibility(View.VISIBLE);
                btnForwardSelected.setEnabled(true);
                btnForwardSelected.setText("Select Group Members");

                btnForwardSelected.setOnClickListener(
                        v -> handleGroupButtonClick()
                );
            }
        }

        setupSearch();
        loadContacts();
    }

    // ==================================================
    // SEARCH CONTACTS
    // ==================================================

    private void setupSearch() {

        etSearchContacts.addTextChangedListener(new TextWatcher() {

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
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // ==================================================
    // LOAD CONTACTS
    // ==================================================

    private void loadContacts() {

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText("Loading employees...");

        directoryRepository.loadActiveUsers(
                new ChatDirectoryRepository.DirectoryCallback() {

                    @Override
                    public void onSuccess(List<ChatUser> users) {

                        contactList.clear();

                        if (users != null) {
                            contactList.addAll(users);
                        }

                        adapter.updateList(contactList);

                        if (contactList.isEmpty()) {

                            tvContactsStatus.setText(
                                    "No approved employees found."
                            );

                            tvContactsStatus.setVisibility(View.VISIBLE);

                        } else if (isForwardingMode) {

                            tvContactsStatus.setText(
                                    "Select employees to forward the message."
                            );

                            tvContactsStatus.setVisibility(View.VISIBLE);

                        } else if (isAddToGroupMode) {

                            tvContactsStatus.setText(
                                    "Select employees to add to this group."
                            );

                            tvContactsStatus.setVisibility(View.VISIBLE);

                        } else if (isGroupSelectionMode) {

                            tvContactsStatus.setText(
                                    "Select employees for your group."
                            );

                            tvContactsStatus.setVisibility(View.VISIBLE);

                        } else {

                            tvContactsStatus.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(Exception error) {

                        tvContactsStatus.setVisibility(View.VISIBLE);
                        tvContactsStatus.setText(
                                "Unable to load employees."
                        );

                        Toast.makeText(
                                ChatContactsActivity.this,
                                error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // ==================================================
    // NORMAL CONTACT CLICK
    // ==================================================

    @Override
    public void onContactClick(ChatUser user) {

        if (adapter.isSelectionMode()) {
            return;
        }

        if (user == null
                || user.getUid() == null
                || user.getUid().trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Invalid employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText("Opening conversation...");

        conversationRepository.getOrCreateDirectConversation(
                user.getUid(),
                new ChatConversationRepository.ConversationCallback() {

                    @Override
                    public void onSuccess(ChatConversation conversation) {

                        tvContactsStatus.setVisibility(View.GONE);

                        Intent intent = new Intent(
                                ChatContactsActivity.this,
                                ChatConversationActivity.class
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONVERSATION_ID,
                                conversation.getConversationId()
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_NAME,
                                user.getName()
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_UID,
                                user.getUid()
                        );

                        startActivity(intent);
                    }

                    @Override
                    public void onError(String error) {

                        tvContactsStatus.setVisibility(View.GONE);

                        Toast.makeText(
                                ChatContactsActivity.this,
                                "Unable to open chat: " + error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // ==================================================
    // GROUP CREATION
    // ==================================================

    private void handleGroupButtonClick() {

        if (isForwardingMode || isForwarding) {
            return;
        }

        if (!isGroupSelectionMode) {

            isGroupSelectionMode = true;

            adapter.clearSelection();
            adapter.setSelectionMode(true);

            if (btnForwardSelected != null) {
                btnForwardSelected.setText("Create Group");
            }

            tvContactsStatus.setVisibility(View.VISIBLE);
            tvContactsStatus.setText(
                    "Select at least one employee for your group."
            );

            setTitle("Select Group Members");

            return;
        }

        List<ChatUser> selectedUsers = adapter.getSelectedUsers();

        if (selectedUsers == null || selectedUsers.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select at least one employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        showCreateGroupDialog(selectedUsers);
    }

    // ==================================================
    // ENTER GROUP NAME
    // ==================================================

    private void showCreateGroupDialog(List<ChatUser> selectedUsers) {

        EditText input = new EditText(this);

        input.setHint("Enter group name");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        int padding = (int) (20 * getResources()
                .getDisplayMetrics().density);

        input.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create New Group")
                .setMessage(
                        "Selected employees: " + selectedUsers.size()
                )
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String groupName = input.getText()
                                .toString()
                                .trim();

                        if (groupName.isEmpty()) {

                            input.setError("Enter a group name");
                            return;
                        }

                        createGroup(groupName, selectedUsers);

                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    // ==================================================
    // CREATE GROUP IN FIRESTORE
    // ==================================================

    private void createGroup(
            String groupName,
            List<ChatUser> selectedUsers
    ) {

        if (currentUid == null) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        List<String> selectedMemberIds = new ArrayList<>();

        for (ChatUser user : selectedUsers) {

            if (user == null || user.getUid() == null) {
                continue;
            }

            String uid = user.getUid().trim();

            if (!uid.isEmpty()
                    && !uid.equals(currentUid)
                    && !selectedMemberIds.contains(uid)) {

                selectedMemberIds.add(uid);
            }
        }

        if (selectedMemberIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "Select at least one other employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (btnForwardSelected != null) {

            btnForwardSelected.setEnabled(false);
            btnForwardSelected.setText("Creating Group...");
        }

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText("Creating your group...");

        conversationRepository.createGroupConversation(
                groupName,
                selectedMemberIds,
                "",
                new ChatConversationRepository.ConversationCallback() {

                    @Override
                    public void onSuccess(ChatConversation conversation) {

                        if (conversation == null
                                || conversation.getConversationId() == null) {

                            showGroupCreationError(
                                    "Group created, but conversation could not be opened."
                            );

                            return;
                        }

                        Toast.makeText(
                                ChatContactsActivity.this,
                                "Group created successfully.",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent = new Intent(
                                ChatContactsActivity.this,
                                ChatConversationActivity.class
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONVERSATION_ID,
                                conversation.getConversationId()
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_NAME,
                                groupName
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_UID,
                                ""
                        );

                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String error) {

                        showGroupCreationError(error);
                    }
                }
        );
    }

    // ==================================================
    // GROUP CREATION ERROR
    // ==================================================

    private void showGroupCreationError(String error) {

        if (btnForwardSelected != null) {

            btnForwardSelected.setEnabled(true);
            btnForwardSelected.setText("Create Group");
        }

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText(error);

        Toast.makeText(
                ChatContactsActivity.this,
                error,
                Toast.LENGTH_LONG
        ).show();
    }

    // ==================================================
    // START FORWARDING
    // ==================================================

    private void startForwarding() {

        if (isForwarding) {
            return;
        }

        if (currentUid == null) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        selectedForwardUsers = adapter.getSelectedUsers();

        if (selectedForwardUsers == null
                || selectedForwardUsers.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select at least one employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        List<ChatUser> validUsers = new ArrayList<>();

        for (ChatUser user : selectedForwardUsers) {

            if (user != null
                    && user.getUid() != null
                    && !user.getUid().equals(currentUid)) {

                validUsers.add(user);
            }
        }

        selectedForwardUsers = validUsers;

        if (selectedForwardUsers.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select another employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        isForwarding = true;
        forwardIndex = 0;
        forwardSuccessCount = 0;
        forwardFailureCount = 0;

        btnForwardSelected.setEnabled(false);
        btnForwardSelected.setText("Preparing...");

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText("Loading original message...");

        db.collection("chatnew_messages")
                .document(forwardMessageId)
                .get()
                .addOnSuccessListener(this::onOriginalMessageLoaded)
                .addOnFailureListener(error ->
                        finishForwardingWithError(
                                "Unable to load original message: "
                                        + error.getMessage()
                        )
                );
    }

    private void onOriginalMessageLoaded(DocumentSnapshot snapshot) {

        if (!snapshot.exists()) {

            finishForwardingWithError("Original message not found.");
            return;
        }

        Boolean deleted = snapshot.getBoolean("deleted");

        if (Boolean.TRUE.equals(deleted)) {

            finishForwardingWithError(
                    "Deleted messages cannot be forwarded."
            );

            return;
        }

        forwardMessageType = snapshot.getString("type");
        forwardMediaUrl = snapshot.getString("mediaUrl");

        if (forwardMessageType == null) {

            finishForwardingWithError(
                    "Original message type is missing."
            );

            return;
        }

        if (!"text".equals(forwardMessageType)
                && !"image".equals(forwardMessageType)
                && !"document".equals(forwardMessageType)) {

            finishForwardingWithError(
                    "This message type cannot be forwarded."
            );

            return;
        }

        if (!"text".equals(forwardMessageType)
                && (forwardMediaUrl == null
                || forwardMediaUrl.trim().isEmpty())) {

            finishForwardingWithError(
                    "Original attachment URL is missing."
            );

            return;
        }

        forwardToNextEmployee();
    }

    // ==================================================
    // FORWARD TO EACH SELECTED EMPLOYEE
    // ==================================================

    private void forwardToNextEmployee() {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (forwardIndex >= selectedForwardUsers.size()) {

            finishForwarding();
            return;
        }

        ChatUser user = selectedForwardUsers.get(forwardIndex);

        String employeeName = user.getName() == null
                ? "employee"
                : user.getName();

        tvContactsStatus.setVisibility(View.VISIBLE);

        tvContactsStatus.setText(
                "Forwarding to "
                        + employeeName
                        + " ("
                        + (forwardIndex + 1)
                        + "/"
                        + selectedForwardUsers.size()
                        + ")..."
        );

        if (btnForwardSelected != null) {
            btnForwardSelected.setText("Forwarding...");
        }

        conversationRepository.getOrCreateDirectConversation(
                user.getUid(),
                new ChatConversationRepository.ConversationCallback() {

                    @Override
                    public void onSuccess(ChatConversation conversation) {

                        if ("text".equals(forwardMessageType)) {

                            sendForwardedMessage(
                                    conversation.getConversationId(),
                                    ""
                            );

                        } else {

                            copyAttachmentToDestination(
                                    conversation.getConversationId(),
                                    new MediaCopyCallback() {

                                        @Override
                                        public void onSuccess(String newUrl) {

                                            sendForwardedMessage(
                                                    conversation.getConversationId(),
                                                    newUrl
                                            );
                                        }

                                        @Override
                                        public void onError(String error) {

                                            forwardFailureCount++;
                                            forwardIndex++;

                                            forwardToNextEmployee();
                                        }
                                    }
                            );
                        }
                    }

                    @Override
                    public void onError(String error) {

                        forwardFailureCount++;
                        forwardIndex++;

                        forwardToNextEmployee();
                    }
                }
        );
    }

    // ==================================================
    // SAVE FORWARDED MESSAGE
    // ==================================================

    private void sendForwardedMessage(
            String destinationConversationId,
            String copiedMediaUrl
    ) {

        messageRepository.forwardMessage(
                forwardMessageId,
                destinationConversationId,
                copiedMediaUrl,
                new ChatMessageRepository.MessageCallback() {

                    @Override
                    public void onSuccess() {

                        forwardSuccessCount++;
                        forwardIndex++;

                        forwardToNextEmployee();
                    }

                    @Override
                    public void onError(String error) {

                        forwardFailureCount++;
                        forwardIndex++;

                        forwardToNextEmployee();
                    }
                }
        );
    }

    // ==================================================
    // COPY IMAGE / DOCUMENT TO DESTINATION STORAGE
    // ==================================================

    private interface MediaCopyCallback {

        void onSuccess(String downloadUrl);

        void onError(String error);
    }

    private void copyAttachmentToDestination(
            String destinationConversationId,
            MediaCopyCallback callback
    ) {

        final StorageReference sourceRef;

        try {

            sourceRef = storage.getReferenceFromUrl(forwardMediaUrl);

        } catch (Exception error) {

            callback.onError("Invalid attachment URL.");
            return;
        }

        sourceRef.getMetadata()
                .addOnSuccessListener(metadata -> {

                    if (metadata.getSizeBytes() > MAX_ATTACHMENT_SIZE) {

                        callback.onError(
                                "Attachment exceeds the 20 MB limit."
                        );

                        return;
                    }

                    File tempFile;

                    try {

                        tempFile = File.createTempFile(
                                "forward_attachment_",
                                ".tmp",
                                getCacheDir()
                        );

                    } catch (Exception error) {

                        callback.onError(
                                "Unable to prepare attachment."
                        );

                        return;
                    }

                    sourceRef.getFile(tempFile)
                            .addOnSuccessListener(taskSnapshot -> {

                                String originalName = sourceRef.getName();

                                String newFileName =
                                        UUID.randomUUID()
                                                + "_"
                                                + originalName;

                                StorageReference destinationRef =
                                        storage.getReference()
                                                .child("chatAttachments")
                                                .child(destinationConversationId)
                                                .child(currentUid)
                                                .child(newFileName);

                                StorageMetadata.Builder metadataBuilder =
                                        new StorageMetadata.Builder();

                                if (metadata.getContentType() != null) {

                                    metadataBuilder.setContentType(
                                            metadata.getContentType()
                                    );
                                }

                                StorageMetadata newMetadata =
                                        metadataBuilder.build();

                                UploadTask uploadTask =
                                        destinationRef.putFile(
                                                Uri.fromFile(tempFile),
                                                newMetadata
                                        );

                                uploadTask
                                        .addOnSuccessListener(uploadResult -> {

                                            deleteTempFile(tempFile);

                                            destinationRef.getDownloadUrl()
                                                    .addOnSuccessListener(uri ->
                                                            callback.onSuccess(
                                                                    uri.toString()
                                                            )
                                                    )
                                                    .addOnFailureListener(error ->
                                                            callback.onError(
                                                                    "Unable to get copied attachment URL."
                                                            )
                                                    );
                                        })
                                        .addOnFailureListener(error -> {

                                            deleteTempFile(tempFile);

                                            callback.onError(
                                                    error.getMessage() == null
                                                            ? "Unable to upload copied attachment."
                                                            : error.getMessage()
                                            );
                                        });

                            })
                            .addOnFailureListener(error -> {

                                deleteTempFile(tempFile);

                                callback.onError(
                                        error.getMessage() == null
                                                ? "Unable to download original attachment."
                                                : error.getMessage()
                                );
                            });

                })
                .addOnFailureListener(error ->
                        callback.onError(
                                error.getMessage() == null
                                        ? "Unable to read original attachment."
                                        : error.getMessage()
                        )
                );
    }

    private void deleteTempFile(File file) {

        if (file != null && file.exists()) {

            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    // ==================================================
    // FINISH FORWARDING
    // ==================================================

    private void finishForwarding() {

        isForwarding = false;

        if (btnForwardSelected != null) {

            btnForwardSelected.setEnabled(true);
            btnForwardSelected.setText("Forward Selected");
        }

        tvContactsStatus.setVisibility(View.VISIBLE);

        tvContactsStatus.setText(
                "Forwarding completed. Successful: "
                        + forwardSuccessCount
                        + ", Failed: "
                        + forwardFailureCount
        );

        Toast.makeText(
                this,
                "Forwarded to "
                        + forwardSuccessCount
                        + " employee(s). Failed: "
                        + forwardFailureCount,
                Toast.LENGTH_LONG
        ).show();

        if (forwardSuccessCount > 0 && forwardFailureCount == 0) {

            finish();

        } else if (forwardSuccessCount > 0) {

            adapter.clearSelection();
        }
    }

    private void finishForwardingWithError(String error) {

        isForwarding = false;

        if (btnForwardSelected != null) {

            btnForwardSelected.setEnabled(true);
            btnForwardSelected.setText("Forward Selected");
        }

        tvContactsStatus.setVisibility(View.VISIBLE);
        tvContactsStatus.setText(error);

        Toast.makeText(
                this,
                error,
                Toast.LENGTH_LONG
        ).show();
    }
}
