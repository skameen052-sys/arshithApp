package com.example.arshithconnectattendance;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.widget.ImageButton;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TeamsActivity extends AppCompatActivity {

    private RecyclerView recyclerConversations, recyclerMessages;
    private EditText edtSearch, edtMessage;
    private TextView txtChatTitle, txtChatSubtitle, txtEmptyMessages;
    private View btnSend, btnAttachFile, layoutReplyComposer;
    private Button btnNewChat, btnFilterAll, btnFilterDirect,
            btnFilterTeams, btnFilterUnread;
    private TextView txtReplyComposerSender, txtReplyComposerContent, btnCancelReply;
    private View layoutChatList, layoutConversation;

    private ActivityResultLauncher<String[]> attachmentPicker;
    private ChatAttachmentManager attachmentManager;
    private Message pendingReply;

    private View layoutRecording;
    private TextView txtRecordingDuration;
    private View btnRecordAudio;
    private View btnCancelRecording;
    private View btnStopRecording;
    private ActivityResultLauncher<String> microphonePermissionLauncher;

    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private Handler recordingHandler = new Handler(Looper.getMainLooper());

    private long recordingStartTime = 0L;
    private boolean isRecording = false;
    private boolean isUploadingRecording = false;

    private static final long MAX_RECORDING_SIZE = 20L * 1024L * 1024L;

    private final Runnable recordingTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording || txtRecordingDuration == null) return;

            long elapsed = SystemClock.elapsedRealtime() - recordingStartTime;
            long seconds = elapsed / 1000L;

            txtRecordingDuration.setText(String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    seconds / 60,
                    seconds % 60
            ));

            recordingHandler.postDelayed(this, 500);
        }
    };

    private TeamAdapter teamAdapter;
    private MessageAdapter messageAdapter;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser firebaseUser;

    private ChatRepository chatRepository;

    private ListenerRegistration conversationsChatListener;
    private ListenerRegistration messagesChatListener;

    private String currentAuthUid = "";
    private String currentUserId = "";
    private String currentUserName = "Employee";
    private String currentUserRole = "employee";

    private String activeFilter = "all";
    private ConversationItem activeConversation;

    private final List<ConversationItem> allConversations = new ArrayList<>();
    private final List<Contact> allContacts = new ArrayList<>();

    private final Map<String, Contact> contactsByBusinessId = new HashMap<>();
    private final Map<String, Contact> contactsByAuthUid = new HashMap<>();

    // Firestore listeners are retained only for profile/contact/team metadata.
    private ListenerRegistration employeesListener;
    private ListenerRegistration adminsListener;
    private ListenerRegistration teamsListener;

    private final Map<String, Message> messageCache = new HashMap<>();
    private final Map<String, ConversationItem> teamConversationItems = new HashMap<>();
    private final Map<String, String> teamIdToName = new HashMap<>();
    private final Map<String, List<String>> teamIdToBusinessMembers = new HashMap<>();
    private final Map<String, List<String>> teamIdToAuthMembers = new HashMap<>();

    // FastAPI polling.
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private boolean isPollingMessages = false;
    private boolean isLoadingMessages = false;

    private static final long MESSAGE_POLL_INTERVAL = 3000;

    private AlertDialog newChatDialog;
    private ContactAdapter contactAdapter;

    private final Set<String> selectedMemberBusinessIds = new HashSet<>();

    private EditText dialogSearch;
    private EditText groupNameInput, groupDescriptionInput;
    private RecyclerView contactsRecycler;
    private TextView selectedCount;
    private View groupFields;

    private Button directTab, groupTab, dialogAction;
    private boolean groupMode = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        attachmentPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        sendAttachment(uri);
                    }
                }
        );

        attachmentManager = new ChatAttachmentManager(this);
        microphonePermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                startAudioRecording();
                            } else {
                                toast("Microphone permission is required.");
                            }
                        }
                );

        setContentView(R.layout.activity_teams);

        db = FirebaseFirestore.getInstance();
        chatRepository = new ChatRepository();
        auth = FirebaseAuth.getInstance();
        firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentAuthUid = firebaseUser.getUid();

        initializeViews();
        setupConversationList();
        setupFilters();
        setupSearch();
        setupButtons();
        setupQuickReplies();
        setupBackNavigation();
        showChatList();

        loadCurrentUserProfile();
    }

    private void initializeViews() {
        layoutChatList = findViewById(R.id.layoutChatList);
        layoutConversation = findViewById(R.id.layoutConversation);

        recyclerConversations = findViewById(R.id.recyclerConversations);
        recyclerMessages = findViewById(R.id.recyclerMessages);

        edtSearch = findViewById(R.id.edtSearchTeams);
        edtMessage = findViewById(R.id.edtMessage);

        txtChatTitle = findViewById(R.id.txtChatTitle);
        txtChatSubtitle = findViewById(R.id.txtChatSubtitle);
        txtEmptyMessages = findViewById(R.id.txtEmptyMessages);

        btnSend = findViewById(R.id.btnSendMessage);
        btnNewChat = findViewById(R.id.btnNewGroup);
        btnAttachFile = findViewById(R.id.btnAttachFile);

        layoutReplyComposer = findViewById(R.id.layoutReplyComposer);

        txtReplyComposerSender = findViewById(R.id.txtReplyComposerSender);
        txtReplyComposerContent = findViewById(R.id.txtReplyComposerContent);
        btnCancelReply = findViewById(R.id.btnCancelReply);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterDirect = findViewById(R.id.btnFilterDirect);
        btnFilterTeams = findViewById(R.id.btnFilterTeams);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);
        layoutRecording = findViewById(R.id.layoutRecording);
        txtRecordingDuration = findViewById(R.id.txtRecordingDuration);
        btnRecordAudio = findViewById(R.id.btnRecordAudio);
        btnCancelRecording = findViewById(R.id.btnCancelRecording);
        btnStopRecording = findViewById(R.id.btnStopRecording);
    }

    private void loadCurrentUserProfile() {
        db.collection("employees").document(currentAuthUid)
                .get()
                .addOnSuccessListener(employeeDoc -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (employeeDoc.exists()) {
                        currentUserId = value(employeeDoc, "employeeId", "");

                        currentUserName = firstNonEmpty(
                                value(employeeDoc, "name", ""),
                                value(employeeDoc, "fullName", ""),
                                firebaseUser.getDisplayName(),
                                "Employee"
                        );

                        currentUserRole = "employee";

                        if (currentUserId.isEmpty()) {
                            profileLoadFailed(
                                    "Employee document is missing employeeId."
                            );
                            return;
                        }

                        startChatListeners();

                    } else {
                        loadEmployeeProfileByUidField();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && !isDestroyed()) {
                        profileLoadFailed(
                                "Unable to load employee profile: " + e.getMessage()
                        );
                    }
                });
    }

    // Older employee documents are not keyed by the Firebase UID,
    // so look them up using the stored "uid" field.
    private void loadEmployeeProfileByUidField() {
        db.collection("employees")
                .whereEqualTo("uid", currentAuthUid)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (snapshot.isEmpty()) {
                        loadAdminProfile();
                        return;
                    }

                    DocumentSnapshot employeeDoc =
                            snapshot.getDocuments().get(0);

                    currentUserId = value(employeeDoc, "employeeId", "");

                    currentUserName = firstNonEmpty(
                            value(employeeDoc, "name", ""),
                            value(employeeDoc, "fullName", ""),
                            firebaseUser.getDisplayName(),
                            "Employee"
                    );

                    currentUserRole = "employee";

                    if (currentUserId.isEmpty()) {
                        profileLoadFailed(
                                "Employee document is missing employeeId."
                        );
                        return;
                    }

                    startChatListeners();
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && !isDestroyed()) {
                        loadAdminProfile();
                    }
                });
    }

    private void loadAdminProfile() {
        db.collection("admins").document(currentAuthUid)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!adminDoc.exists()) {
                        profileLoadFailed(
                                "Your employee/admin profile was not found."
                        );
                        return;
                    }

                    currentUserId = value(adminDoc, "adminId", "");

                    currentUserName = firstNonEmpty(
                            value(adminDoc, "name", ""),
                            value(adminDoc, "displayName", ""),
                            value(adminDoc, "fullName", ""),
                            "Administrator"
                    );

                    currentUserRole = "admin";

                    if (currentUserId.isEmpty()) {
                        profileLoadFailed(
                                "Admin document is missing adminId."
                        );
                        return;
                    }

                    startChatListeners();
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && !isDestroyed()) {
                        profileLoadFailed(
                                "Unable to load admin profile: " + e.getMessage()
                        );
                    }
                });
    }

    private void profileLoadFailed(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        txtEmptyMessages.setText(message);
        txtEmptyMessages.setVisibility(View.VISIBLE);
    }

    private void startChatListeners() {
        setupMessageList();

        listenForContacts();
        listenForTeams();

        loadConversationsFromApi();
    }

    private void setupConversationList() {
        teamAdapter = new TeamAdapter(this::openConversation);

        recyclerConversations.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerConversations.setAdapter(teamAdapter);
    }

    private void setupMessageList() {
        messageAdapter = new MessageAdapter(
                currentAuthUid,
                this::showReplyComposer
        );

        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);

        recyclerMessages.setLayoutManager(manager);
        recyclerMessages.setAdapter(messageAdapter);
    }

    private void setupFilters() {
        btnFilterAll.setOnClickListener(v -> applyFilter("all"));
        btnFilterDirect.setOnClickListener(v -> applyFilter("direct"));
        btnFilterTeams.setOnClickListener(v -> applyFilter("group"));
        btnFilterUnread.setOnClickListener(v -> applyFilter("unread"));
    }
    private void applyFilter(String filter) {
        activeFilter = filter;

        String query = edtSearch.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        List<ConversationItem> filtered = new ArrayList<>();

        for (ConversationItem item : allConversations) {
            if (item == null) {
                continue;
            }

            boolean typeMatch = "all".equals(filter)
                    || ("direct".equals(filter)
                    && "direct".equalsIgnoreCase(item.type))
                    || ("group".equals(filter)
                    && "group".equalsIgnoreCase(item.type))
                    || ("unread".equals(filter)
                    && item.unreadCount > 0);

            boolean searchMatch = query.isEmpty()
                    || safe(item.title).toLowerCase(Locale.ROOT).contains(query)
                    || safe(item.lastMessage).toLowerCase(Locale.ROOT).contains(query);

            if (typeMatch && searchMatch) {
                filtered.add(item);
            }
        }

        if (teamAdapter != null) {
            teamAdapter.setItems(filtered);
        }

        updateFilterButtons();
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                applyFilter(activeFilter);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupButtons() {
        btnNewChat.setOnClickListener(v -> showNewChatDialog());

        btnSend.setOnClickListener(v -> sendMessage());

        if (btnAttachFile != null) {
            btnAttachFile.setOnClickListener(v -> {
                if (activeConversation == null) {
                    toast("Open a chat before attaching a file.");
                    return;
                }

                attachmentPicker.launch(new String[]{"*/*"});
            });
        }

        if (btnCancelReply != null) {
            btnCancelReply.setOnClickListener(v -> clearReply());
        }

        if (btnRecordAudio != null) {
            btnRecordAudio.setOnClickListener(v -> {
                if (activeConversation == null) {
                    toast("Open a chat before recording audio.");
                    return;
                }

                requestAudioPermissionAndStart();
            });
        }

        if (btnCancelRecording != null) {
            btnCancelRecording.setOnClickListener(v -> cancelAudioRecording());
        }

        if (btnStopRecording != null) {
            btnStopRecording.setOnClickListener(v -> stopAudioRecordingAndSend());
        }
    }

    private void setupQuickReplies() {
        TextView one = findViewById(R.id.txtQuickReply1);
        TextView two = findViewById(R.id.txtQuickReply2);
        TextView three = findViewById(R.id.txtQuickReply3);

        one.setOnClickListener(v -> edtMessage.setText(one.getText()));
        two.setOnClickListener(v -> edtMessage.setText(two.getText()));
        three.setOnClickListener(v -> edtMessage.setText(three.getText()));
    }

    private void setupBackNavigation() {
        findViewById(R.id.btnBackToChats)
                .setOnClickListener(v -> showChatList());

        getOnBackPressedDispatcher().addCallback(
                this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (layoutConversation.getVisibility() == View.VISIBLE) {
                            showChatList();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // CONTACTS - FIRESTORE METADATA ONLY
    // ---------------------------------------------------------

    private void listenForContacts() {
        employeesListener = db.collection("employees")
                .whereEqualTo("status", "approved")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        toast("Unable to load employees: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    removeContactsByRole("employee");

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        // Prefer the actual Firebase Auth UID stored in the document.
                        // Fall back to the document ID only when no uid field exists.
                        String docAuthUid = firstNonEmpty(
                                value(doc, "uid", ""),
                                value(doc, "authUid", ""),
                                doc.getId()
                        );

                        if (docAuthUid.equals(currentAuthUid)) {
                            continue;
                        }

                        String status = value(doc, "status", "");

                        if (!"approved".equalsIgnoreCase(status)) {
                            continue;
                        }

                        String businessId = value(doc, "employeeId", "");

                        if (businessId.isEmpty() || docAuthUid.isEmpty()) {
                            continue;
                        }

                        Contact contact = new Contact();

                        contact.setUid(businessId);
                        contact.setAuthUid(docAuthUid);
                        contact.setEmployee_id(businessId);

                        contact.setName(firstNonEmpty(
                                value(doc, "name", ""),
                                value(doc, "fullName", ""),
                                "Employee"
                        ));

                        contact.setEmail(value(doc, "email", ""));
                        contact.setDesignation(value(doc, "designation", ""));
                        contact.setDepartment(value(doc, "department", ""));
                        contact.setRole(value(doc, "role", "Employee"));
                        contact.setProfile_photo(value(doc, "photoUrl", ""));

                        putContact(contact);
                    }

                    refreshContactList();
                    rebuildConversationList();
                });

        adminsListener = db.collection("admins")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        toast("Unable to load administrators: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    removeContactsByRole("admin");

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String docAuthUid = firstNonEmpty(
                                value(doc, "uid", ""),
                                value(doc, "authUid", ""),
                                doc.getId()
                        );

                        if (docAuthUid.equals(currentAuthUid)) {
                            continue;
                        }

                        String adminId = value(doc, "adminId", "");

                        if (adminId.isEmpty() || docAuthUid.isEmpty()) {
                            continue;
                        }

                        Contact contact = new Contact();

                        contact.setUid(adminId);
                        contact.setAuthUid(docAuthUid);
                        contact.setEmployee_id(adminId);

                        contact.setName(firstNonEmpty(
                                value(doc, "name", ""),
                                value(doc, "displayName", ""),
                                value(doc, "fullName", ""),
                                "Administrator"
                        ));

                        contact.setEmail(value(doc, "email", ""));
                        contact.setDesignation(
                                value(doc, "designation", "Administrator")
                        );
                        contact.setDepartment(value(doc, "department", ""));
                        contact.setRole("Admin");
                        contact.setProfile_photo(value(doc, "photoUrl", ""));

                        putContact(contact);
                    }
                    refreshContactList();
                    rebuildConversationList();
                });
    }

    private void removeContactsByRole(String role) {
        List<String> remove = new ArrayList<>();

        for (Map.Entry<String, Contact> entry :
                contactsByBusinessId.entrySet()) {

            if (role.equalsIgnoreCase(entry.getValue().getRole())) {
                remove.add(entry.getKey());
            }
        }

        for (String id : remove) {
            Contact old = contactsByBusinessId.remove(id);

            if (old != null) {
                contactsByAuthUid.remove(old.getAuthUid());
            }
        }
    }

    private void putContact(Contact contact) {
        contactsByBusinessId.put(contact.getUid(), contact);
        contactsByAuthUid.put(contact.getAuthUid(), contact);
    }

    private void refreshContactList() {
        allContacts.clear();
        allContacts.addAll(contactsByBusinessId.values());

        Collections.sort(
                allContacts,
                (a, b) -> a.getName().compareToIgnoreCase(b.getName())
        );

        if (contactAdapter != null) {
            String query = dialogSearch == null
                    ? ""
                    : dialogSearch.getText().toString();

            contactAdapter.setContacts(filterContacts(query));
            contactAdapter.setSelectedIds(selectedMemberBusinessIds);
        }

        if (contactsRecycler != null && newChatDialog != null) {
            TextView empty = newChatDialog.findViewById(R.id.txtNoContacts);

            if (empty != null) {
                boolean noContacts = filterContacts(
                        dialogSearch == null
                                ? ""
                                : dialogSearch.getText().toString()
                ).isEmpty();

                empty.setText(
                        allContacts.isEmpty()
                                ? "No approved employees or administrators found."
                                : "No contacts match your search."
                );

                empty.setVisibility(
                        noContacts ? View.VISIBLE : View.GONE
                );

                contactsRecycler.setVisibility(
                        noContacts ? View.GONE : View.VISIBLE
                );
            }
        }
    }

    // ---------------------------------------------------------
    // FASTAPI CONVERSATION LIST
    // ---------------------------------------------------------

    private void loadConversationsFromApi() {

        if (conversationsChatListener != null) {
            conversationsChatListener.remove();
        }

        conversationsChatListener =
                chatRepository.listenToConversations(
                        new ChatRepository.ConversationsCallback() {

                            @Override
                            public void onUpdate(
                                    List<ConversationItem> conversations
                            ) {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }

                                allConversations.clear();

                                if (conversations != null) {
                                    allConversations.addAll(conversations);
                                }

                                rebuildConversationList();

                                // Refresh the selected conversation if it still exists.
                                if (activeConversation != null) {
                                    for (ConversationItem item : allConversations) {
                                        if (item.conversationId.equals(
                                                activeConversation.conversationId
                                        )) {
                                            activeConversation = item;
                                            txtChatTitle.setText(item.title);
                                            txtChatSubtitle.setText(item.subtitle);
                                            break;
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onError(String error) {
                                LogWarning(
                                        "Firestore conversation loading failed: "
                                                + error
                                );
                            }
                        }
                );
    }

    private void updateConversationItem(ConversationItem item) {
        if (item == null || item.conversationId == null) {
            return;
        }

        for (int i = 0; i < allConversations.size(); i++) {
            ConversationItem existing = allConversations.get(i);

            if (existing != null
                    && item.conversationId.equals(existing.conversationId)) {

                allConversations.set(i, item);
                rebuildConversationList();
                return;
            }
        }

        allConversations.add(item);
        rebuildConversationList();
    }
    // ---------------------------------------------------------
    // TEAMS - FIRESTORE METADATA ONLY
    // ---------------------------------------------------------

    private void listenForTeams() {
        teamsListener = db.collection("teams")
                .whereArrayContains("memberIds", currentAuthUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        toast("Unable to load your teams: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    Set<String> activeTeamIds = new HashSet<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Boolean active = doc.getBoolean("active");

                        if (active != null && !active) continue;

                        String teamId = firstNonEmpty(
                                value(doc, "team_id", ""),
                                doc.getId()
                        );

                        String teamName = firstNonEmpty(
                                value(doc, "name", ""),
                                value(doc, "team_name", ""),
                                "Team"
                        );

                        activeTeamIds.add(teamId);
                        teamIdToName.put(teamId, teamName);

                        List<String> businessMembers =
                                readStringList(doc.get("memberBusinessIds"));

                        if (businessMembers.isEmpty()) {
                            List<String> authMembers =
                                    readStringList(doc.get("memberIds"));

                            for (String uid : authMembers) {
                                Contact contact = contactsByAuthUid.get(uid);

                                if (contact != null) {
                                    businessMembers.add(contact.getUid());
                                }
                            }
                        }

                        if (!businessMembers.contains(currentUserId)) {
                            businessMembers.add(currentUserId);
                        }

                        teamIdToBusinessMembers.put(
                                teamId,
                                businessMembers
                        );

                        teamIdToAuthMembers.put(
                                teamId,
                                readStringList(doc.get("memberIds"))
                        );

                        String conversationId = firstNonEmpty(
                                value(doc, "conversation_id", ""),
                                "team_" + teamId
                        );

                        ConversationItem item = new ConversationItem(
                                conversationId,
                                "group",
                                teamId,
                                teamName,
                                businessMembers.size() + " members",
                                "No messages yet",
                                0
                        );

                        teamConversationItems.put(conversationId, item);
                    }

                    // Remove teams no longer in the current user's membership.
                    List<String> staleIds = new ArrayList<>();

                    for (ConversationItem item :
                            teamConversationItems.values()) {

                        if ("group".equalsIgnoreCase(item.type)
                                && !activeTeamIds.contains(item.targetId)) {
                            staleIds.add(item.conversationId);
                        }
                    }

                    for (String stale : staleIds) {
                        teamConversationItems.remove(stale);
                    }

                    rebuildConversationList();
                });
    }

    // ---------------------------------------------------------
    // CONVERSATION LIST
    // ---------------------------------------------------------

    private void rebuildConversationList() {

        Map<String, ConversationItem> rebuilt = new HashMap<>();

        // FIRST: Preserve all actual conversations received from Firestore.
        for (ConversationItem item : allConversations) {

            if (item == null || item.conversationId == null) {
                continue;
            }

            rebuilt.put(item.conversationId, item);
        }

        // SECOND: Add team placeholders only when Firestore has no
        // actual conversation for that ID.
        for (ConversationItem item : teamConversationItems.values()) {

            if (item == null || item.conversationId == null) {
                continue;
            }

            if (!rebuilt.containsKey(item.conversationId)) {
                rebuilt.put(item.conversationId, item);
            }
        }

        // THIRD: Rebuild the displayed conversation list.
        allConversations.clear();
        allConversations.addAll(rebuilt.values());

        // Sort newest conversations first.
        Collections.sort(
                allConversations,
                (a, b) -> Long.compare(b.updatedAt, a.updatedAt)
        );

        // Refresh All, Direct, Groups and Unread filters.
        applyFilter(activeFilter);
    }
    private String findOtherParticipant(List<String> ids) {
        if (ids == null) return "";

        // memberIds contains Firebase Auth UIDs.
        // Do not compare against currentUserId (employee/business ID).
        for (String id : ids) {
            if (id != null
                    && !id.trim().isEmpty()
                    && !id.equals(currentAuthUid)) {
                return id;
            }
        }

        return "";
    }

    private int countUnread(List<Message> messages) {
        int count = 0;

        if (messages == null) return 0;

        for (Message message : messages) {
            if (message == null) continue;
            if (currentAuthUid.equals(message.getSenderId())) continue;

            List<String> readBy = message.getReadBy();
            if (readBy == null || !readBy.contains(currentAuthUid)) {
                count++;
            }
        }

        return count;
    }

    // ---------------------------------------------------------
    // REPLY
    // ---------------------------------------------------------

    private void showReplyComposer(Message message) {
        if (message == null) return;

        pendingReply = message;

        txtReplyComposerSender.setText(
                currentAuthUid.equals(message.getSenderId())
                        ? "You"
                        : message.getSenderName()
        );

        String preview = message.isDeleted()
                ? "This message was deleted"
                : message.getContent();

        if (preview.trim().isEmpty()) {
            preview = message.getAttachmentName();
        }

        txtReplyComposerContent.setText(preview);
        layoutReplyComposer.setVisibility(View.VISIBLE);
        edtMessage.requestFocus();
    }

    private void clearReply() {
        pendingReply = null;

        if (layoutReplyComposer != null) {
            layoutReplyComposer.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------
    // OPEN CONVERSATION AND FASTAPI MESSAGE POLLING
    // ---------------------------------------------------------

    private void openConversation(ConversationItem item) {
        if (item == null) return;

        activeConversation = item;

        txtChatTitle.setText(item.title);
        txtChatSubtitle.setText(item.subtitle);

        showConversation();

        messageAdapter.setMessages(new ArrayList<>());
        messageCache.clear();

        txtEmptyMessages.setVisibility(View.VISIBLE);
        txtEmptyMessages.setText("Loading messages...");

        markConversationAsRead(item.conversationId);
        loadMessagesFromApi();
    }

    private void startMessagePolling() {
        // Kept temporarily for compatibility with existing method calls.
        loadMessagesFromApi();
    }

    private void stopMessagePolling() {
        if (messagesChatListener != null) {
            messagesChatListener.remove();
            messagesChatListener = null;
        }

        isLoadingMessages = false;
    }

    private void loadMessagesFromApi() {

        if (activeConversation == null || chatRepository == null) {
            return;
        }

        final String conversationId =
                activeConversation.conversationId;

        if (messagesChatListener != null) {
            messagesChatListener.remove();
            messagesChatListener = null;
        }

        isLoadingMessages = true;

        messagesChatListener = chatRepository.listenToMessages(
                conversationId,
                new ChatRepository.MessagesCallback() {

                    @Override
                    public void onUpdate(List<Message> messages) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (activeConversation == null
                                || !conversationId.equals(
                                activeConversation.conversationId
                        )) {
                            return;
                        }

                        isLoadingMessages = false;

                        List<Message> updatedMessages =
                                messages == null
                                        ? new ArrayList<>()
                                        : new ArrayList<>(messages);

                        messageCache.clear();

                        for (Message message : updatedMessages) {
                            if (message.getId() != null) {
                                messageCache.put(
                                        message.getId(),
                                        message
                                );
                            }
                        }

                        messageAdapter.setMessages(updatedMessages);

                        txtEmptyMessages.setVisibility(
                                updatedMessages.isEmpty()
                                        ? View.VISIBLE
                                        : View.GONE
                        );

                        if (updatedMessages.isEmpty()) {
                            txtEmptyMessages.setText(
                                    "No messages yet. Start the conversation."
                            );
                        } else {
                            recyclerMessages.scrollToPosition(
                                    updatedMessages.size() - 1
                            );
                        }

                        updateActiveConversationPreview(updatedMessages);

                        markConversationAsRead(conversationId);
                    }

                    @Override
                    public void onError(String error) {

                        isLoadingMessages = false;

                        LogWarning(
                                "Firestore message loading failed: " + error
                        );

                        if (messageAdapter.getItemCount() == 0) {
                            txtEmptyMessages.setVisibility(View.VISIBLE);
                            txtEmptyMessages.setText(
                                    "Unable to load messages. Check your connection."
                            );
                        }
                    }
                }
        );
    }

    private void updateActiveConversationPreview(List<Message> messages) {
        if (activeConversation == null || messages.isEmpty()) return;

        Message latest = messages.get(messages.size() - 1);

        activeConversation.lastMessage = latest.getContent();
        activeConversation.updatedAt = latest.getCreatedAtMillis();
        activeConversation.unreadCount = countUnread(messages);
    }

    // ---------------------------------------------------------
    // SEND TEXT MESSAGE (FIRESTORE)
    // ---------------------------------------------------------


    private void sendMessage() {
        if (activeConversation == null) {
            toast("Select a conversation first.");
            return;
        }

        String content = edtMessage.getText().toString().trim();

        if (content.isEmpty()) {
            return;
        }

        if (currentAuthUid == null || currentAuthUid.trim().isEmpty()) {
            toast("User authentication is missing.");
            return;
        }

        String conversationId = activeConversation.conversationId;

        if (conversationId == null || conversationId.trim().isEmpty()) {
            toast("Invalid conversation.");
            return;
        }

        btnSend.setEnabled(false);

        com.google.firebase.firestore.DocumentReference conversationRef =
                db.collection("conversations").document(conversationId);

        com.google.firebase.firestore.DocumentReference messageRef =
                conversationRef.collection("messages").document();

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot conversation =
                    transaction.get(conversationRef);

            if (!conversation.exists()) {
                throw new IllegalStateException("Conversation does not exist.");
            }

            List<String> memberIds =
                    (List<String>) conversation.get("memberIds");

            if (memberIds == null || !memberIds.contains(currentAuthUid)) {
                throw new IllegalStateException(
                        "You are not a member of this conversation."
                );
            }

            Map<String, Object> unreadCounts =
                    (Map<String, Object>) conversation.get("unreadCounts");

            if (unreadCounts == null) {
                unreadCounts = new HashMap<>();
            } else {
                unreadCounts = new HashMap<>(unreadCounts);
            }

            for (String memberId : memberIds) {
                if (memberId == null) continue;

                if (memberId.equals(currentAuthUid)) {
                    unreadCounts.put(memberId, 0);
                } else {
                    Object oldValue = unreadCounts.get(memberId);
                    long oldCount = oldValue instanceof Number
                            ? ((Number) oldValue).longValue()
                            : 0L;

                    unreadCounts.put(memberId, oldCount + 1);
                }
            }

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("conversationId", conversationId);
            messageData.put("chatType", activeConversation.type);
            messageData.put("senderId", currentAuthUid);
            messageData.put("senderAuthUid", currentAuthUid);
            messageData.put("senderName", currentUserName);
            messageData.put("senderRole", currentUserRole);
            messageData.put("content", content);
            messageData.put("messageType", "text");
            messageData.put(
                    "timestamp",
                    com.google.firebase.firestore.FieldValue.serverTimestamp()
            );
            messageData.put(
                    "readBy",
                    new ArrayList<>(java.util.Collections.singletonList(currentAuthUid))
            );
            messageData.put(
                    "deliveredTo",
                    new ArrayList<>(java.util.Collections.singletonList(currentAuthUid))
            );
            messageData.put("participantIds", new ArrayList<>(memberIds));

            if (pendingReply != null) {
                messageData.put("replyToMessageId", pendingReply.getId());
                messageData.put("replyToSender", pendingReply.getSenderName());
                messageData.put("replyToContent", pendingReply.getContent());
            }

            transaction.set(messageRef, messageData);

            Map<String, Object> conversationUpdate = new HashMap<>();
            conversationUpdate.put("lastMessage", content);
            conversationUpdate.put(
                    "lastMessageTime",
                    com.google.firebase.firestore.FieldValue.serverTimestamp()
            );
            conversationUpdate.put("lastSenderId", currentAuthUid);
            conversationUpdate.put("unreadCounts", unreadCounts);

            transaction.update(conversationRef, conversationUpdate);

            return null;

        }).addOnSuccessListener(unused -> {
            btnSend.setEnabled(true);
            edtMessage.setText("");
            clearReply();

        }).addOnFailureListener(error -> {
            btnSend.setEnabled(true);

            LogWarning("Firestore send failed: " + error.getMessage());
            toast("Message could not be sent: " + error.getMessage());
        });
    }


    // ---------------------------------------------------------
    // READ STATUS (FIRESTORE)
    // ---------------------------------------------------------


    private void markConversationAsRead(String conversationId) {

        if (chatRepository == null
                || conversationId == null
                || conversationId.trim().isEmpty()) {
            return;
        }

        chatRepository.markConversationAsRead(conversationId);
    }

    // ---------------------------------------------------------
    // AUDIO RECORDING
    // ---------------------------------------------------------

    private void requestAudioPermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        startAudioRecording();
    }

    private void startAudioRecording() {
        if (activeConversation == null) {
            toast("Open a chat before recording audio.");
            return;
        }

        if (isRecording || isUploadingRecording) {
            toast("An audio recording is already in progress.");
            return;
        }

        try {
            File recordingDirectory = new File(getCacheDir(), "chat_recordings");
            if (!recordingDirectory.exists() && !recordingDirectory.mkdirs()) {
                toast("Unable to create the audio recording folder.");
                return;
            }

            recordingFile = new File(
                    recordingDirectory,
                    "voice_" + System.currentTimeMillis() + ".m4a"
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());
            mediaRecorder.setMaxFileSize(MAX_RECORDING_SIZE);

            mediaRecorder.setOnInfoListener((recorder, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    toast("20 MB recording limit reached. Sending audio.");
                    stopAudioRecordingAndSend();
                }
            });

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = SystemClock.elapsedRealtime();

            if (layoutRecording != null) {
                layoutRecording.setVisibility(View.VISIBLE);
            }
            if (txtRecordingDuration != null) {
                txtRecordingDuration.setText("00:00");
            }
            if (btnRecordAudio != null) {
                btnRecordAudio.setEnabled(false);
            }

            recordingHandler.removeCallbacks(recordingTimerRunnable);
            recordingHandler.post(recordingTimerRunnable);

        } catch (Exception e) {
            LogWarning("Audio recording could not start: " + e.getMessage());
            releaseMediaRecorder();
            deleteRecordingFile();
            resetRecordingViews();
            toast("Unable to start audio recording: " + e.getMessage());
        }
    }

    private void stopAudioRecordingAndSend() {
        if (!isRecording || mediaRecorder == null) return;

        recordingHandler.removeCallbacks(recordingTimerRunnable);

        boolean stopped = true;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            stopped = false;
            LogWarning("Audio recording stop failed: " + e.getMessage());
        }

        releaseMediaRecorder();
        isRecording = false;

        if (!stopped || recordingFile == null || !recordingFile.exists()
                || recordingFile.length() <= 0L) {
            deleteRecordingFile();
            resetRecordingViews();
            toast("The audio recording is empty or could not be saved.");
            return;
        }

        if (recordingFile.length() > MAX_RECORDING_SIZE) {
            deleteRecordingFile();
            resetRecordingViews();
            toast("Audio file must not exceed 20 MB.");
            return;
        }

        isUploadingRecording = true;
        if (btnStopRecording != null) btnStopRecording.setEnabled(false);
        if (btnCancelRecording != null) btnCancelRecording.setEnabled(false);

        sendAttachment(Uri.fromFile(recordingFile));
    }

    private void cancelAudioRecording() {
        recordingHandler.removeCallbacks(recordingTimerRunnable);

        if (mediaRecorder != null) {
            try {
                if (isRecording) mediaRecorder.stop();
            } catch (RuntimeException ignored) {
                // A very short recording may not contain enough data to stop cleanly.
            }
        }

        releaseMediaRecorder();
        isRecording = false;
        isUploadingRecording = false;
        deleteRecordingFile();
        resetRecordingViews();
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

    private void deleteRecordingFile() {
        if (recordingFile != null) {
            try {
                if (recordingFile.exists()) recordingFile.delete();
            } catch (Exception ignored) {
            }
            recordingFile = null;
        }
    }

    private void resetRecordingViews() {
        if (layoutRecording != null) {
            layoutRecording.setVisibility(View.GONE);
        }
        if (txtRecordingDuration != null) {
            txtRecordingDuration.setText("00:00");
        }
        if (btnRecordAudio != null) btnRecordAudio.setEnabled(true);
        if (btnStopRecording != null) btnStopRecording.setEnabled(true);
        if (btnCancelRecording != null) btnCancelRecording.setEnabled(true);
    }

    private void cleanupRecordingOnDestroy() {
        recordingHandler.removeCallbacks(recordingTimerRunnable);
        if (mediaRecorder != null) {
            try {
                if (isRecording) mediaRecorder.stop();
            } catch (RuntimeException ignored) {
            }
        }
        releaseMediaRecorder();
        isRecording = false;
        isUploadingRecording = false;
        deleteRecordingFile();
    }

    // ---------------------------------------------------------
    // ATTACHMENTS
    // ---------------------------------------------------------

    private void sendAttachment(Uri fileUri) {
        if (activeConversation == null) {
            toast("Select a conversation first.");
            if (isUploadingRecording) {
                isUploadingRecording = false;
                deleteRecordingFile();
                resetRecordingViews();
            }
            return;
        }

        final boolean sendingRecording = isUploadingRecording;

        boolean isTeam = "group".equalsIgnoreCase(activeConversation.type);
        String chatType = isTeam ? "team" : "direct";
        String teamId = isTeam ? activeConversation.targetId : "";

        List<String> participantIds = new ArrayList<>();
        List<String> participantAuthUids = new ArrayList<>();

        if (isTeam) {
            participantIds.add(teamId);
            participantAuthUids.addAll(
                    teamIdToAuthMembers.getOrDefault(teamId, new ArrayList<>())
            );

            if (!participantAuthUids.contains(currentAuthUid)) {
                participantAuthUids.add(currentAuthUid);
            }
        } else {
            participantIds.add(currentUserId);
            participantIds.add(activeConversation.targetId);

            Contact receiver = contactsByBusinessId.get(activeConversation.targetId);

            if (receiver == null || receiver.getAuthUid() == null
                    || receiver.getAuthUid().trim().isEmpty()) {
                toast("Unable to identify the recipient account.");
                if (sendingRecording) {
                    isUploadingRecording = false;
                    deleteRecordingFile();
                    resetRecordingViews();
                }
                return;
            }

            participantAuthUids.add(currentAuthUid);
            participantAuthUids.add(receiver.getAuthUid());
        }

        if (btnAttachFile != null) btnAttachFile.setEnabled(false);
        if (btnRecordAudio != null) btnRecordAudio.setEnabled(false);

        attachmentManager.uploadAndSend(
                this,
                fileUri,
                activeConversation.conversationId,
                chatType,
                currentUserId,
                currentAuthUid,
                currentUserName,
                currentUserRole,
                participantIds,
                participantAuthUids,
                isTeam
                        ? teamIdToBusinessMembers.getOrDefault(teamId, new ArrayList<>())
                        : participantIds,
                teamId,
                isTeam ? activeConversation.title : "",
                pendingReply == null ? "" : pendingReply.getId(),
                pendingReply == null ? "" : pendingReply.getSenderName(),
                pendingReply == null ? "" : pendingReply.getContent(),
                new ChatAttachmentManager.UploadCallback() {
                    @Override
                    public void onSuccess(String downloadUrl, String fileName) {
                        if (btnAttachFile != null) btnAttachFile.setEnabled(true);
                        if (sendingRecording) {
                            isUploadingRecording = false;
                            deleteRecordingFile();
                            resetRecordingViews();
                        } else if (btnRecordAudio != null) {
                            btnRecordAudio.setEnabled(true);
                        }

                        clearReply();
                        toast(sendingRecording ? "Audio message sent." : "Attachment sent.");
                    }

                    @Override
                    public void onFailure(String error) {
                        if (btnAttachFile != null) btnAttachFile.setEnabled(true);
                        if (sendingRecording) {
                            isUploadingRecording = false;
                            deleteRecordingFile();
                            resetRecordingViews();
                        } else if (btnRecordAudio != null) {
                            btnRecordAudio.setEnabled(true);
                        }

                        toast("Attachment failed: " + error);
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // FILTERS
    // ---------------------------------------------------------

    private void updateFilterButtons() {
        setFilterStyle(btnFilterAll, "all");
        setFilterStyle(btnFilterDirect, "direct");
        setFilterStyle(btnFilterTeams, "group");
        setFilterStyle(btnFilterUnread, "unread");
    }

    private void setFilterStyle(Button button, String filter) {
        boolean selected = activeFilter.equals(filter);

        button.setBackgroundResource(
                selected
                        ? R.drawable.bg_filter_selected
                        : R.drawable.bg_filter_unselected
        );

        button.setTextColor(
                android.graphics.Color.parseColor(
                        selected ? "#FFFFFF" : "#8138E8"
                )
        );
    }

    // ---------------------------------------------------------
    // NEW CHAT DIALOG
    // ---------------------------------------------------------

    private void showNewChatDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_new_chat, null, false);

        directTab = view.findViewById(R.id.btnDirectTab);
        groupTab = view.findViewById(R.id.btnGroupTab);

        dialogSearch = view.findViewById(R.id.edtContactSearch);
        contactsRecycler = view.findViewById(R.id.recyclerContacts);

        selectedCount = view.findViewById(R.id.txtSelectedCount);
        groupFields = view.findViewById(R.id.layoutGroupFields);

        groupNameInput = view.findViewById(R.id.edtNewGroupName);
        groupDescriptionInput =
                view.findViewById(R.id.edtNewGroupDescription);

        dialogAction = view.findViewById(R.id.btnDialogAction);

        selectedMemberBusinessIds.clear();

        contactsRecycler.setLayoutManager(
                new LinearLayoutManager(this)
        );

        contactsRecycler.setHasFixedSize(false);

        contactAdapter = new ContactAdapter(contact -> {
            if (!groupMode) {
                startDirectConversation(contact);
            } else {
                if (selectedMemberBusinessIds.contains(contact.getUid())) {
                    selectedMemberBusinessIds.remove(contact.getUid());
                } else {
                    selectedMemberBusinessIds.add(contact.getUid());
                }

                contactAdapter.setSelectedIds(
                        selectedMemberBusinessIds
                );

                updateSelectedCount();
            }
        }, true);

        contactsRecycler.setAdapter(contactAdapter);
        contactAdapter.setContacts(filterContacts(""));

        directTab.setOnClickListener(v -> setDialogMode(false));
        groupTab.setOnClickListener(v -> setDialogMode(true));

        dialogSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                contactAdapter.setContacts(
                        filterContacts(s.toString())
                );
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialogAction.setOnClickListener(v -> createGroupFromDialog());

        newChatDialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        newChatDialog.setOnDismissListener(dialog -> {
            dialogSearch = null;
            contactsRecycler = null;
            contactAdapter = null;
        });

        view.findViewById(R.id.btnCloseNewChat)
                .setOnClickListener(v -> newChatDialog.dismiss());

        newChatDialog.show();

        if (newChatDialog.getWindow() != null) {
            newChatDialog.getWindow()
                    .setBackgroundDrawableResource(
                            android.R.color.transparent
                    );

            int width = (int) (
                    getResources().getDisplayMetrics().widthPixels * 0.92f
            );

            int height = (int) (
                    getResources().getDisplayMetrics().heightPixels * 0.78f
            );

            newChatDialog.getWindow().setLayout(width, height);
        }

        setDialogMode(false);
        refreshContactList();
    }

    private void setDialogMode(boolean group) {
        groupMode = group;

        groupFields.setVisibility(group ? View.VISIBLE : View.GONE);
        selectedCount.setVisibility(group ? View.VISIBLE : View.GONE);
        dialogAction.setVisibility(group ? View.VISIBLE : View.GONE);

        directTab.setBackgroundResource(
                group
                        ? R.drawable.bg_filter_unselected
                        : R.drawable.bg_filter_selected
        );

        groupTab.setBackgroundResource(
                group
                        ? R.drawable.bg_filter_selected
                        : R.drawable.bg_filter_unselected
        );

        directTab.setTextColor(getColor(
                group
                        ? R.color.teams_secondary_text
                        : android.R.color.white
        ));

        groupTab.setTextColor(getColor(
                group
                        ? android.R.color.white
                        : R.color.teams_secondary_text
        ));

        contactAdapter.setGroupMode(group);
        contactAdapter.setSelectedIds(selectedMemberBusinessIds);

        updateSelectedCount();
    }

    private void updateSelectedCount() {
        selectedCount.setText(
                "Add Members (" + selectedMemberBusinessIds.size()
                        + " selected)"
        );
    }

    private List<Contact> filterContacts(String query) {
        String q = safe(query).trim().toLowerCase(Locale.ROOT);

        List<Contact> result = new ArrayList<>();

        for (Contact contact : allContacts) {
            String text = (
                    safe(contact.getName()) + " "
                            + safe(contact.getEmployee_id()) + " "
                            + safe(contact.getEmail()) + " "
                            + safe(contact.getRole()) + " "
                            + safe(contact.getDesignation())
            ).toLowerCase(Locale.ROOT);

            if (text.contains(q)) {
                result.add(contact);
            }
        }

        return result;
    }

    // ---------------------------------------------------------
    // START DIRECT CHAT
    // ---------------------------------------------------------

    private void startDirectConversation(Contact contact) {
        if (contact == null
                || contact.getUid() == null
                || contact.getUid().trim().isEmpty()
                || contact.getAuthUid() == null
                || contact.getAuthUid().trim().isEmpty()
                || currentAuthUid == null
                || currentAuthUid.trim().isEmpty()) {
            toast("Unable to identify this contact account.");
            return;
        }

        // The conversation document must exist in Firestore before any
        // message can be sent, so create (or fetch) it first.
        chatRepository.createOrGetDirectConversation(
                contact,
                currentUserName,
                currentUserId,
                new ChatRepository.ResultCallback<String>() {

                    @Override
                    public void onSuccess(String conversationId) {
                        if (isFinishing() || isDestroyed()) return;

                        if (newChatDialog != null) {
                            newChatDialog.dismiss();
                        }

                        ConversationItem item = new ConversationItem(
                                conversationId,
                                "direct",
                                contact.getUid(),
                                contact.getName(),
                                firstNonEmpty(
                                        contact.getDesignation(),
                                        contact.getRole()
                                ),
                                "No messages yet",
                                0
                        );

                        openConversation(item);
                    }

                    @Override
                    public void onFailure(String error) {
                        toast("Could not start chat: " + error);
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // CREATE GROUP - FIRESTORE TEAM METADATA
    // ---------------------------------------------------------


    private void createGroupFromDialog() {

        String name = groupNameInput.getText().toString().trim();

        String description =
                groupDescriptionInput.getText().toString().trim();

        if (name.isEmpty()) {
            groupNameInput.setError("Enter group name");
            return;
        }

        if (selectedMemberBusinessIds.isEmpty()) {
            toast("Select at least one employee or admin");
            return;
        }

        List<String> memberAuthUids = new ArrayList<>();
        List<String> memberBusinessIds = new ArrayList<>();

        Map<String, String> memberNameMap = new HashMap<>();
        Map<String, String> memberBusinessMap = new HashMap<>();

        memberAuthUids.add(currentAuthUid);
        memberBusinessIds.add(currentUserId);

        memberNameMap.put(currentAuthUid, currentUserName);
        memberBusinessMap.put(currentAuthUid, currentUserId);

        for (String businessId : selectedMemberBusinessIds) {

            Contact contact = contactsByBusinessId.get(businessId);

            if (contact == null) continue;

            String authUid = contact.getAuthUid();

            if (authUid == null || authUid.trim().isEmpty()) {
                continue;
            }

            if (!memberAuthUids.contains(authUid)) {

                memberAuthUids.add(authUid);
                memberBusinessIds.add(contact.getUid());

                memberNameMap.put(authUid, contact.getName());
                memberBusinessMap.put(authUid, contact.getUid());
            }
        }

        if (memberAuthUids.size() < 2) {
            toast("Select at least one other member");
            return;
        }

        if (memberAuthUids.size() > 100) {
            toast("A group can have at most 100 members");
            return;
        }

        String teamId = "TEA-" + System.currentTimeMillis();

        // Prevent double taps while the group is being saved.
        if (dialogAction != null) {
            dialogAction.setEnabled(false);
        }

        // Saves the chat conversation and the team details in ONE batch.
        chatRepository.createGroupConversation(
                name,
                description,
                teamId,
                memberAuthUids,
                memberNameMap,
                memberBusinessMap,
                new ChatRepository.ResultCallback<String>() {

                    @Override
                    public void onSuccess(String conversationId) {

                        if (isFinishing() || isDestroyed()) return;

                        if (dialogAction != null) {
                            dialogAction.setEnabled(true);
                        }

                        if (newChatDialog != null) {
                            newChatDialog.dismiss();
                        }

                        teamIdToName.put(teamId, name);

                        teamIdToBusinessMembers.put(
                                teamId,
                                memberBusinessIds
                        );

                        teamIdToAuthMembers.put(
                                teamId,
                                memberAuthUids
                        );

                        ConversationItem item =
                                new ConversationItem(
                                        conversationId,
                                        "group",
                                        teamId,
                                        name,
                                        memberBusinessIds.size()
                                                + " members",
                                        "No messages yet",
                                        0
                                );

                        teamConversationItems.put(
                                conversationId,
                                item
                        );

                        rebuildConversationList();
                        openConversation(item);

                        toast("Team chat created successfully");
                    }

                    @Override
                    public void onFailure(String error) {

                        if (dialogAction != null) {
                            dialogAction.setEnabled(true);
                        }

                        LogWarning("Group creation failed: " + error);
                        toast("Group creation failed: " + error);
                    }
                }
        );
    }


    // ---------------------------------------------------------
    // GENERAL HELPERS
    // ---------------------------------------------------------

    private Contact findContact(String businessId) {
        return contactsByBusinessId.get(businessId);
    }

    private String value(
            DocumentSnapshot doc,
            String key,
            String fallback
    ) {
        Object value = doc.get(key);

        return value == null
                ? fallback
                : String.valueOf(value);
    }

    private List<String> readStringList(Object raw) {
        List<String> result = new ArrayList<>();

        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }

        return result;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long parseApiTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return 0L;
        }

        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception ignored) {
            try {
                return java.time.OffsetDateTime.parse(
                        timestamp
                ).toInstant().toEpochMilli();
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private void showChatList() {
        stopMessagePolling();

        layoutChatList.setVisibility(View.VISIBLE);
        layoutConversation.setVisibility(View.GONE);

        activeConversation = null;

        loadConversationsFromApi();
    }

    private void showConversation() {
        layoutChatList.setVisibility(View.GONE);
        layoutConversation.setVisibility(View.VISIBLE);
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void LogWarning(String message) {
        android.util.Log.w("TeamsActivity", message);
    }

    @Override
    protected void onDestroy() {

        cleanupRecordingOnDestroy();

        if (conversationsChatListener != null) {
            conversationsChatListener.remove();
            conversationsChatListener = null;
        }
        if (messageAdapter != null) {
            messageAdapter.releaseAudio();
        }

        if (messagesChatListener != null) {
            messagesChatListener.remove();
            messagesChatListener = null;
        }

        if (employeesListener != null) {
            employeesListener.remove();
            employeesListener = null;
        }

        if (adminsListener != null) {
            adminsListener.remove();
            adminsListener = null;
        }

        if (teamsListener != null) {
            teamsListener.remove();
            teamsListener = null;
        }

        if (attachmentManager != null) {
            attachmentManager.shutdown();
        }

        super.onDestroy();
    }
}