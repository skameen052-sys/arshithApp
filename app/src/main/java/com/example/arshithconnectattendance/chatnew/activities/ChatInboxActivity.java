
package com.example.arshithconnectattendance.chatnew.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.adapters.ChatInboxAdapter;
import com.example.arshithconnectattendance.chatnew.model.ChatInboxItem;
import com.example.arshithconnectattendance.chatnew.repository.ChatInboxRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatPresenceRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChatInboxActivity extends AppCompatActivity
        implements ChatInboxAdapter.OnInboxItemClickListener {

    // Main views
    private EditText etSearchInbox;
    private RecyclerView recyclerChatInbox;
    private TextView tvInboxStatus;
    private TextView tvInboxEmpty;
    private TextView tvInboxTitle;
    private FloatingActionButton fabNewChat;

    // Active / Archived tabs
    private TextView btnActiveChats;
    private TextView btnArchivedChats;

    // Inbox filters
    private TextView btnFilterAll;
    private TextView btnFilterUnread;
    private TextView btnFilterFavorites;
    private TextView btnFilterGroups;

    // Selection toolbar
    private View layoutSelectionToolbar;
    private TextView tvSelectionCount;
    private TextView btnSelectionPin;
    private TextView btnSelectionDelete;
    private TextView btnSelectionArchive;
    private TextView btnSelectionMore;

    // Repositories
    private ChatInboxAdapter inboxAdapter;
    private ChatInboxRepository inboxRepository;
    private ChatPresenceRepository presenceRepository;

    private ListenerRegistration inboxListener;

    private final List<ChatInboxItem> inboxItems = new ArrayList<>();

    private boolean isLoading = false;
    private boolean isUpdatingBulkAction = false;

    private String currentFilter = "all";

    // =========================================================
    // ACTIVITY CREATE
    // =========================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat_inbox);

        initializeViews();

        if (!validateViews()) {
            Toast.makeText(
                    this,
                    "Unable to initialize chat inbox. Check activity_chat_inbox.xml.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        inboxRepository = new ChatInboxRepository();
        presenceRepository = new ChatPresenceRepository();

        setupRecyclerView();
        setupSearch();
        setupActiveArchivedTabs();
        setupFilters();
        setupSelectionToolbar();
        setupNewChatButton();

        updateSelectionToolbar(0);
        showLoading();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        tvInboxTitle = findViewById(R.id.tvInboxTitle);
        etSearchInbox = findViewById(R.id.etSearchInbox);

        btnActiveChats = findViewById(R.id.btnActiveChats);
        btnArchivedChats = findViewById(R.id.btnArchivedChats);

        tvInboxStatus = findViewById(R.id.tvInboxStatus);
        recyclerChatInbox = findViewById(R.id.recyclerChatInbox);
        tvInboxEmpty = findViewById(R.id.tvInboxEmpty);

        fabNewChat = findViewById(R.id.fabNewChat);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);
        btnFilterFavorites = findViewById(R.id.btnFilterFavorites);
        btnFilterGroups = findViewById(R.id.btnFilterGroups);

        layoutSelectionToolbar =
                findViewById(R.id.layoutSelectionToolbar);

        tvSelectionCount =
                findViewById(R.id.tvSelectionCount);

        btnSelectionPin =
                findViewById(R.id.btnSelectionPin);

        btnSelectionDelete =
                findViewById(R.id.btnSelectionDelete);

        btnSelectionArchive =
                findViewById(R.id.btnSelectionArchive);

        btnSelectionMore =
                findViewById(R.id.btnSelectionMore);
    }

    private boolean validateViews() {

        return etSearchInbox != null
                && recyclerChatInbox != null
                && tvInboxStatus != null
                && tvInboxEmpty != null
                && btnActiveChats != null
                && btnArchivedChats != null
                && btnFilterAll != null
                && btnFilterUnread != null
                && btnFilterFavorites != null
                && btnFilterGroups != null
                && layoutSelectionToolbar != null
                && tvSelectionCount != null
                && btnSelectionPin != null
                && btnSelectionDelete != null
                && btnSelectionArchive != null
                && btnSelectionMore != null;
    }

    // =========================================================
    // RECYCLER VIEW
    // =========================================================

    private void setupRecyclerView() {

        recyclerChatInbox.setLayoutManager(
                new LinearLayoutManager(this)
        );

        inboxAdapter = new ChatInboxAdapter(
                inboxItems,
                this
        );

        recyclerChatInbox.setAdapter(inboxAdapter);

        inboxAdapter.setShowArchived(false);
        inboxAdapter.setInboxFilter(currentFilter);
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

        etSearchInbox.addTextChangedListener(new TextWatcher() {

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

                if (inboxAdapter != null) {
                    inboxAdapter.filter(s.toString());
                }

                updateInboxDisplay();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // =========================================================
    // ACTIVE / ARCHIVED TABS
    // =========================================================

    private void setupActiveArchivedTabs() {

        btnActiveChats.setOnClickListener(v -> {

            if (inboxAdapter != null) {
                inboxAdapter.setShowArchived(false);
            }

            updateActiveArchivedButtons();
            updateInboxDisplay();
        });

        btnArchivedChats.setOnClickListener(v -> {

            if (inboxAdapter != null) {
                inboxAdapter.setShowArchived(true);
            }

            updateActiveArchivedButtons();
            updateInboxDisplay();
        });

        updateActiveArchivedButtons();
    }


    private void updateActiveArchivedButtons() {

        if (inboxAdapter == null) {
            return;
        }

        boolean archived = inboxAdapter.isShowingArchived();

        // Update selected state
        btnActiveChats.setSelected(!archived);
        btnArchivedChats.setSelected(archived);

        // Move the white rounded selection card
        if (archived) {

            // Archived Chats selected
            btnArchivedChats.setBackgroundResource(
                    R.drawable.bg_segment_thumb
            );

            // Active Chats becomes normal
            btnActiveChats.setBackgroundResource(0);

            btnArchivedChats.setTextColor(
                    Color.rgb(194, 70, 20)
            );

            btnActiveChats.setTextColor(
                    Color.rgb(128, 112, 101)
            );

            btnArchivedChats.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

            btnActiveChats.setTypeface(
                    null,
                    android.graphics.Typeface.NORMAL
            );

        } else {

            // Active Chats selected
            btnActiveChats.setBackgroundResource(
                    R.drawable.bg_segment_thumb
            );

            // Archived Chats becomes normal
            btnArchivedChats.setBackgroundResource(0);

            btnActiveChats.setTextColor(
                    Color.rgb(194, 70, 20)
            );

            btnArchivedChats.setTextColor(
                    Color.rgb(128, 112, 101)
            );

            btnActiveChats.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

            btnArchivedChats.setTypeface(
                    null,
                    android.graphics.Typeface.NORMAL
            );
        }

        btnActiveChats.setAlpha(1f);
        btnArchivedChats.setAlpha(1f);
    }


    // =========================================================
    // ALL / UNREAD / FAVORITES / GROUPS FILTERS
    // =========================================================

    private void setupFilters() {

        btnFilterAll.setOnClickListener(v ->
                changeInboxFilter("all")
        );

        btnFilterUnread.setOnClickListener(v ->
                changeInboxFilter("unread")
        );

        btnFilterFavorites.setOnClickListener(v ->
                changeInboxFilter("favorites")
        );

        btnFilterGroups.setOnClickListener(v ->
                changeInboxFilter("groups")
        );

        updateFilterButtons();
    }

    private void changeInboxFilter(String filter) {

        currentFilter = filter;

        if (inboxAdapter != null) {
            inboxAdapter.setInboxFilter(filter);
        }

        updateFilterButtons();
        updateInboxDisplay();
    }


    private void updateFilterButtons() {

        int unreadCount = inboxAdapter == null
                ? 0
                : inboxAdapter.getTotalUnreadCount();

        btnFilterUnread.setText(
                unreadCount > 0
                        ? "Unread " + (unreadCount > 99 ? "99+" : unreadCount)
                        : "Unread"
        );

        setFilterButtonState(btnFilterAll, "all");
        setFilterButtonState(btnFilterUnread, "unread");
        setFilterButtonState(btnFilterFavorites, "favorites");
        setFilterButtonState(btnFilterGroups, "groups");
    }

    private void setFilterButtonState(
            TextView button,
            String filter
    ) {
        if (button == null) return;

        boolean selected = currentFilter.equals(filter);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(
                getResources().getDisplayMetrics().density * 30
        );

        if (selected) {
            background.setColor(Color.rgb(255, 91, 0));
            button.setTextColor(Color.WHITE);
            button.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            background.setColor(Color.WHITE);
            background.setStroke(
                    (int) (getResources().getDisplayMetrics().density),
                    Color.rgb(240, 225, 214)
            );
            button.setTextColor(Color.rgb(128, 112, 101));
            button.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        button.setBackground(background);
        button.setAlpha(1f);
        button.setSelected(selected);
    }



    // =========================================================
    // SELECTION TOOLBAR
    // =========================================================

    private void setupSelectionToolbar() {

        btnSelectionPin.setOnClickListener(v -> {

            if (inboxAdapter != null) {
                inboxAdapter.performBulkAction("pin");
            }
        });

        btnSelectionDelete.setOnClickListener(v -> {

            if (inboxAdapter != null) {
                inboxAdapter.performBulkAction("delete");
            }
        });

        btnSelectionArchive.setOnClickListener(v -> {

            if (inboxAdapter != null) {
                inboxAdapter.performBulkAction("archive");
            }
        });

        btnSelectionMore.setOnClickListener(this::showBulkMoreMenu);
    }

    private void updateSelectionToolbar(int selectedCount) {

        boolean selectionActive = selectedCount > 0;

        layoutSelectionToolbar.setVisibility(
                selectionActive ? View.VISIBLE : View.GONE
        );

        if (selectionActive) {

            tvSelectionCount.setText(
                    selectedCount + " selected"
            );

            boolean archived = inboxAdapter != null
                    && inboxAdapter.isShowingArchived();

            btnSelectionArchive.setText(
                    archived ? "Unarchive" : "Archive"
            );
        }

        if (fabNewChat != null) {

            fabNewChat.setVisibility(
                    selectionActive ? View.GONE : View.VISIBLE
            );
        }
    }

    @Override
    public void onSelectionChanged(Set<String> selectedConversationIds) {

        int count = selectedConversationIds == null
                ? 0
                : selectedConversationIds.size();

        updateSelectionToolbar(count);
    }

    // =========================================================
    // BULK MORE MENU
    // =========================================================


    private void showBulkMoreMenu(View anchor) {

        if (inboxAdapter == null
                || inboxAdapter.getSelectedCount() == 0) {
            return;
        }

        List<ChatInboxItem> selectedItems =
                inboxAdapter.getSelectedItems();

        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }

        boolean allMuted = true;

        for (ChatInboxItem item : selectedItems) {
            if (item == null || !item.isMuted()) {
                allMuted = false;
                break;
            }
        }

        PopupMenu popup = new PopupMenu(this, anchor);

        popup.getMenu().add(
                0, 1, 0,
                allMuted ? "Unmute" : "Mute"
        );

        popup.getMenu().add(0, 2, 1, "Favorites");

        popup.setOnMenuItemClickListener(menuItem -> {

            if (menuItem.getItemId() == 1) {
                inboxAdapter.performBulkAction("mute");
                return true;
            }

            if (menuItem.getItemId() == 2) {
                inboxAdapter.performBulkAction("favorite");
                return true;
            }

            return false;
        });

        popup.show();
    }

    // =========================================================
    // INDIVIDUAL CHAT CLICK
    // =========================================================

    @Override
    public void onInboxItemClick(ChatInboxItem item) {

        if (item == null) {
            return;
        }

        openConversation(item);
    }

    private void openConversation(ChatInboxItem item) {

        String conversationId = item.getConversationId();

        if (conversationId == null
                || conversationId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Invalid conversation.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String contactName;

        if (item.isGroupChat()) {

            contactName = item.getGroupName();

            if (contactName == null
                    || contactName.trim().isEmpty()) {
                contactName = "Group";
            }

        } else {

            contactName = item.getContactName();

            if (contactName == null
                    || contactName.trim().isEmpty()) {
                contactName = "Employee";
            }
        }

        Intent intent = new Intent(
                ChatInboxActivity.this,
                ChatConversationActivity.class
        );

        intent.putExtra(
                ChatConversationActivity.EXTRA_CONVERSATION_ID,
                conversationId
        );

        intent.putExtra(
                ChatConversationActivity.EXTRA_CONTACT_NAME,
                contactName
        );

        intent.putExtra(
                ChatConversationActivity.EXTRA_CONTACT_UID,
                item.getContactUid() == null
                        ? ""
                        : item.getContactUid()
        );

        startActivity(intent);
    }
    private void openGroupInfo(ChatInboxItem item) {

        if (item == null || !item.isGroupChat()) {
            return;
        }

        Intent intent = new Intent(
                ChatInboxActivity.this,
                GroupInfoActivity.class
        );

        intent.putExtra(
                GroupInfoActivity.EXTRA_CONVERSATION_ID,
                item.getConversationId()
        );

        startActivity(intent);
    }

    // =========================================================
    // INDIVIDUAL CHAT ACTIONS
    // =========================================================

    @Override
    public void onInboxItemAction(
            ChatInboxItem item,
            String action
    ) {

        if (item == null || action == null) {
            return;
        }

        switch (action) {

            case "pin":
                updateSingleSetting(item, "pin");
                break;

            case "archive":
                updateSingleSetting(item, "archive");
                break;

            case "mute":
                updateSingleSetting(item, "mute");
                break;

            case "favorite":
                updateSingleFavorite(item);
                break;

            case "delete":
                confirmDeleteConversation(item);
                break;
        }
    }

    private void updateSingleSetting(
            ChatInboxItem item,
            String action
    ) {

        boolean pinned = item.isPinned();
        boolean archived = item.isArchived();
        boolean muted = item.isMuted();

        switch (action) {

            case "pin":
                pinned = !pinned;
                break;

            case "archive":
                archived = !archived;
                break;

            case "mute":
                muted = !muted;
                break;
        }

        final boolean newPinned = pinned;
        final boolean newArchived = archived;
        final boolean newMuted = muted;

        inboxRepository.setInboxSettings(
                item.getConversationId(),
                newPinned,
                newArchived,
                newMuted,
                task -> {

                    if (task.isSuccessful()) {

                        item.setPinned(newPinned);
                        item.setArchived(newArchived);
                        item.setMuted(newMuted);

                        refreshInbox();

                        Toast.makeText(
                                this,
                                "Chat settings updated.",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        showRepositoryError(
                                "Unable to update chat settings.",
                                task.getException()
                        );
                    }
                }
        );
    }

    private void updateSingleFavorite(ChatInboxItem item) {

        boolean newFavorite = !item.isFavorite();

        inboxRepository.setFavorite(
                item.getConversationId(),
                newFavorite,
                task -> {

                    if (task.isSuccessful()) {

                        item.setFavorite(newFavorite);

                        refreshInbox();

                        Toast.makeText(
                                this,
                                newFavorite
                                        ? "Added to Favorites."
                                        : "Removed from Favorites.",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        showRepositoryError(
                                "Unable to update favorite.",
                                task.getException()
                        );
                    }
                }
        );
    }

    // =========================================================
    // DELETE ONE CONVERSATION
    // =========================================================

    private void confirmDeleteConversation(ChatInboxItem item) {

        if (item == null) {
            return;
        }

        // Groups require a fresh Firestore membership check.
        if (item.isGroupChat()) {
            checkGroupMembershipBeforeDelete(item);
            return;
        }

        // Keep existing direct-chat deletion behavior unchanged.
        String name = item.getContactName();

        if (name == null || name.trim().isEmpty()) {
            name = "this conversation";
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete conversation?")
                .setMessage(
                        "Delete \"" + name
                                + "\" from your inbox? "
                                + "This only deletes it for you."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        deleteConversationForCurrentUser(item))
                .show();
    }

    private void deleteConversation(ChatInboxItem item) {

        // Safety check: group conversations must never be hidden
        // through the ordinary inbox deletion method.
        if (item == null || item.isGroupChat()) {
            return;
        }

        inboxRepository.deleteConversationForMe(
                item.getConversationId(),
                task -> {

                    if (task.isSuccessful()) {

                        inboxItems.remove(item);
                        refreshInbox();

                        Toast.makeText(
                                this,
                                "Conversation deleted for you.",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        showRepositoryError(
                                "Unable to delete conversation.",
                                task.getException()
                        );
                    }
                }
        );
    }
    // =========================================================
    // BULK ACTION CALLBACK
    // =========================================================

    @Override
    public void onInboxBulkAction(
            List<ChatInboxItem> selectedItems,
            String action
    ) {

        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }

        if (isUpdatingBulkAction) {
            return;
        }

        List<ChatInboxItem> items =
                new ArrayList<>(selectedItems);

        if ("delete".equals(action)) {

            confirmBulkDelete(items);
            return;
        }

        boolean targetValue = getBulkTargetValue(items, action);

        isUpdatingBulkAction = true;

        updateBulkItem(
                items,
                0,
                action,
                targetValue,
                0,
                0
        );
    }

    // =========================================================
    // DETERMINE BULK ACTION VALUE
    // =========================================================

    private boolean getBulkTargetValue(
            List<ChatInboxItem> items,
            String action
    ) {

        boolean allEnabled = true;

        for (ChatInboxItem item : items) {

            boolean enabled;

            switch (action) {

                case "pin":
                    enabled = item.isPinned();
                    break;

                case "archive":
                    enabled = item.isArchived();
                    break;

                case "mute":
                    enabled = item.isMuted();
                    break;

                case "favorite":
                    enabled = item.isFavorite();
                    break;

                default:
                    enabled = false;
                    break;
            }

            if (!enabled) {
                allEnabled = false;
                break;
            }
        }

        return !allEnabled;
    }

    // =========================================================
    // EXECUTE BULK ACTIONS
    // =========================================================

    private void updateBulkItem(
            List<ChatInboxItem> items,
            int index,
            String action,
            boolean targetValue,
            int successCount,
            int failureCount
    ) {

        if (index >= items.size()) {

            isUpdatingBulkAction = false;

            if (inboxAdapter != null) {
                inboxAdapter.clearSelection();
            }

            refreshInbox();

            Toast.makeText(
                    this,
                    "Updated " + successCount
                            + " chat(s). Failed: " + failureCount,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        ChatInboxItem item = items.get(index);

        if (item == null || item.getConversationId() == null) {

            updateBulkItem(
                    items,
                    index + 1,
                    action,
                    targetValue,
                    successCount,
                    failureCount + 1
            );

            return;
        }

        if ("favorite".equals(action)) {

            inboxRepository.setFavorite(
                    item.getConversationId(),
                    targetValue,
                    task -> {

                        if (task.isSuccessful()) {

                            item.setFavorite(targetValue);

                            updateBulkItem(
                                    items,
                                    index + 1,
                                    action,
                                    targetValue,
                                    successCount + 1,
                                    failureCount
                            );

                        } else {

                            updateBulkItem(
                                    items,
                                    index + 1,
                                    action,
                                    targetValue,
                                    successCount,
                                    failureCount + 1
                            );
                        }
                    }
            );

            return;
        }

        boolean pinned = item.isPinned();
        boolean archived = item.isArchived();
        boolean muted = item.isMuted();

        switch (action) {

            case "pin":
                pinned = targetValue;
                break;

            case "archive":
                archived = targetValue;
                break;

            case "mute":
                muted = targetValue;
                break;
        }

        final boolean newPinned = pinned;
        final boolean newArchived = archived;
        final boolean newMuted = muted;

        inboxRepository.setInboxSettings(
                item.getConversationId(),
                newPinned,
                newArchived,
                newMuted,
                task -> {

                    if (task.isSuccessful()) {

                        item.setPinned(newPinned);
                        item.setArchived(newArchived);
                        item.setMuted(newMuted);

                        updateBulkItem(
                                items,
                                index + 1,
                                action,
                                targetValue,
                                successCount + 1,
                                failureCount
                        );

                    } else {

                        updateBulkItem(
                                items,
                                index + 1,
                                action,
                                targetValue,
                                successCount,
                                failureCount + 1
                        );
                    }
                }
        );
    }

    // =========================================================
    // BULK DELETE CONFIRMATION
    // =========================================================

    // =========================================================
// BULK DELETE CONFIRMATION
// =========================================================

    private void confirmBulkDelete(List<ChatInboxItem> items) {

        if (items == null || items.isEmpty()) {
            return;
        }

        int groupCount = 0;
        int directCount = 0;

        for (ChatInboxItem item : items) {
            if (item == null) continue;

            if (item.isGroupChat()) {
                groupCount++;
            } else {
                directCount++;
            }
        }

        String message = "Delete the selected conversations from your inbox?";

        if (directCount > 0) {
            message += "\n\nDirect conversations: " + directCount;
        }

        if (groupCount > 0) {
            message += "\nGroups: " + groupCount
                    + "\n\nGroups where you are still a member will be skipped. "
                    + "Groups you have left or been removed from can be deleted.";
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete selected conversations?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    if (isUpdatingBulkAction) {
                        return;
                    }

                    isUpdatingBulkAction = true;

                    deleteBulkItem(
                            new ArrayList<>(items),
                            0,
                            0,
                            0,
                            0
                    );
                })
                .show();
    }

// =========================================================
// EXECUTE BULK DELETE
// =========================================================

    private void deleteBulkItem(
            List<ChatInboxItem> items,
            int index,
            int successCount,
            int failureCount,
            int protectedGroupCount
    ) {

        if (index >= items.size()) {

            isUpdatingBulkAction = false;

            if (inboxAdapter != null) {
                inboxAdapter.clearSelection();
            }

            refreshInbox();

            String result = "Deleted " + successCount
                    + " conversation(s).";

            if (protectedGroupCount > 0) {
                result += "\nSkipped " + protectedGroupCount
                        + " group(s) because you are still a member.";
            }

            if (failureCount > 0) {
                result += "\nFailed: " + failureCount;
            }

            Toast.makeText(
                    this,
                    result,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        ChatInboxItem item = items.get(index);

        if (item == null || item.getConversationId() == null) {

            deleteBulkItem(
                    items,
                    index + 1,
                    successCount,
                    failureCount + 1,
                    protectedGroupCount
            );

            return;
        }

        // Direct chats retain their existing delete-for-me behavior.
        if (!item.isGroupChat()) {

            inboxRepository.deleteConversationForMe(
                    item.getConversationId(),
                    task -> {

                        if (task.isSuccessful()) {
                            inboxItems.remove(item);

                            deleteBulkItem(
                                    items,
                                    index + 1,
                                    successCount + 1,
                                    failureCount,
                                    protectedGroupCount
                            );

                        } else {

                            deleteBulkItem(
                                    items,
                                    index + 1,
                                    successCount,
                                    failureCount + 1,
                                    protectedGroupCount
                            );
                        }
                    }
            );

            return;
        }

        // GROUP: use the secure Cloud Function.
// It checks membership and deletes only this user's inbox entry.
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("action", "deleteForMe");
        data.put("conversationId", item.getConversationId());

        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("manageGroupMembership")
                .call(data)
                .addOnSuccessListener(result -> {

                    inboxItems.remove(item);

                    deleteBulkItem(
                            items,
                            index + 1,
                            successCount + 1,
                            failureCount,
                            protectedGroupCount
                    );
                })
                .addOnFailureListener(error -> {

                    boolean stillMember =
                            error instanceof FirebaseFunctionsException
                                    && ((FirebaseFunctionsException) error).getCode()
                                    == FirebaseFunctionsException.Code.FAILED_PRECONDITION;

                    deleteBulkItem(
                            items,
                            index + 1,
                            successCount,
                            failureCount + (stillMember ? 0 : 1),
                            protectedGroupCount + (stillMember ? 1 : 0)
                    );
                });
    }
    // =========================================================
    // LOAD INBOX FROM FIRESTORE
    // =========================================================

    private void loadInbox() {

        if (inboxRepository == null) {
            return;
        }

        isLoading = true;
        showLoading();

        if (inboxListener != null) {
            inboxListener.remove();
            inboxListener = null;
        }

        inboxListener = inboxRepository.listenToInbox(
                new ChatInboxRepository.InboxListener() {

                    @Override
                    public void onInboxChanged(List<ChatInboxItem> items) {

                        isLoading = false;

                        inboxItems.clear();

                        if (items != null) {
                            inboxItems.addAll(items);
                        }

                        if (inboxAdapter != null) {
                            inboxAdapter.updateList(inboxItems);
                            inboxAdapter.setInboxFilter(currentFilter);
                        }

                        updateActiveArchivedButtons();
                        updateFilterButtons();
                        updateInboxDisplay();
                    }

                    @Override
                    public void onError(String message) {

                        isLoading = false;

                        tvInboxStatus.setVisibility(View.VISIBLE);
                        tvInboxStatus.setText(
                                message == null
                                        ? "Unable to load conversations."
                                        : message
                        );

                        recyclerChatInbox.setVisibility(View.GONE);
                        tvInboxEmpty.setVisibility(View.GONE);
                    }
                }
        );
    }

    // =========================================================
    // REFRESH DISPLAY
    // =========================================================

    private void refreshInbox() {

        if (inboxAdapter != null) {
            inboxAdapter.updateList(inboxItems);
            inboxAdapter.setInboxFilter(currentFilter);
        }

        updateFilterButtons();
        updateInboxDisplay();
    }

    private void updateInboxDisplay() {

        if (inboxAdapter == null) {
            return;
        }

        if (isLoading) {
            showLoading();
            return;
        }

        int visibleCount = inboxAdapter.getVisibleItemCount();

        if (visibleCount > 0) {

            recyclerChatInbox.setVisibility(View.VISIBLE);
            tvInboxEmpty.setVisibility(View.GONE);
            tvInboxStatus.setVisibility(View.GONE);

            return;
        }

        recyclerChatInbox.setVisibility(View.GONE);
        tvInboxStatus.setVisibility(View.GONE);
        tvInboxEmpty.setVisibility(View.VISIBLE);

        String emptyMessage;

        if (inboxAdapter.isShowingArchived()) {

            emptyMessage = "No archived conversations.";

        } else if ("unread".equals(currentFilter)) {

            emptyMessage = "No unread conversations.";

        } else if ("favorites".equals(currentFilter)) {

            emptyMessage = "No favorite conversations.";

        } else if ("groups".equals(currentFilter)) {

            emptyMessage = "No group conversations.";

        } else if (etSearchInbox.getText().length() > 0) {

            emptyMessage = "No conversations found.";

        } else {

            emptyMessage = "No conversations yet.";
        }

        tvInboxEmpty.setText(emptyMessage);
    }

    private void showLoading() {

        if (tvInboxStatus != null) {
            tvInboxStatus.setVisibility(View.VISIBLE);
            tvInboxStatus.setText("Loading conversations...");
        }

        if (recyclerChatInbox != null) {
            recyclerChatInbox.setVisibility(View.GONE);
        }

        if (tvInboxEmpty != null) {
            tvInboxEmpty.setVisibility(View.GONE);
        }
    }

    // =========================================================
    // NEW CHAT BUTTON
    // =========================================================

    private void setupNewChatButton() {

        if (fabNewChat == null) {
            return;
        }

        fabNewChat.setOnClickListener(v -> {

            Intent intent = new Intent(
                    ChatInboxActivity.this,
                    ChatContactsActivity.class
            );

            startActivity(intent);
        });
    }

    // =========================================================
    // ERROR HANDLING
    // =========================================================

    private void showRepositoryError(
            String defaultMessage,
            Exception error
    ) {

        String message = defaultMessage;

        if (error != null && error.getMessage() != null) {
            message += "\n" + error.getMessage();
        }

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    @Override
    public void onBackPressed() {

        if (inboxAdapter != null
                && inboxAdapter.isInSelectionMode()) {

            inboxAdapter.clearSelection();
            updateSelectionToolbar(0);
            return;
        }

        super.onBackPressed();
    }

    // =========================================================
    // PRESENCE LIFECYCLE
    // =========================================================

    @Override
    protected void onResume() {
        super.onResume();

        if (presenceRepository != null) {
            presenceRepository.setOnline(null);
        }

        if (inboxRepository != null) {
            loadInbox();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (presenceRepository != null) {
            presenceRepository.setOffline(null);
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (inboxListener != null) {
            inboxListener.remove();
            inboxListener = null;
        }
    }
    private void checkGroupMembershipBeforeDelete(ChatInboxItem item) {

        if (item == null || item.getConversationId() == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete group from inbox?")
                .setMessage(
                        "You can delete this group from your inbox only "
                                + "after leaving or being removed from it. "
                                + "If you are still a member, you will be asked "
                                + "to open Group Info."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        deleteConversationForCurrentUser(item))
                .show();
    }
    private void deleteConversationForCurrentUser(ChatInboxItem item) {

        if (item == null || item.getConversationId() == null) {
            return;
        }

        // GROUP: use the secure Cloud Function.
        if (item.isGroupChat()) {

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("action", "deleteForMe");
            data.put("conversationId", item.getConversationId());

            FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable("manageGroupMembership")
                    .call(data)
                    .addOnSuccessListener(result -> {

                        inboxItems.remove(item);

                        if (inboxAdapter != null) {
                            inboxAdapter.updateList(inboxItems);
                            inboxAdapter.clearSelection();
                        }

                        refreshInbox();

                        Toast.makeText(
                                this,
                                "Group deleted from your inbox.",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .addOnFailureListener(error -> {

                        if (error instanceof FirebaseFunctionsException
                                && ((FirebaseFunctionsException) error).getCode()
                                == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {

                            new AlertDialog.Builder(this)
                                    .setTitle("Leave Group First")
                                    .setMessage(
                                            "You are still a member of this group. "
                                                    + "Leave the group before deleting it "
                                                    + "from your inbox."
                                    )
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton(
                                            "Open Group Info",
                                            (dialog, which) -> openGroupInfo(item)
                                    )
                                    .show();

                        } else {

                            showRepositoryError(
                                    "Unable to delete group from your inbox.",
                                    error
                            );
                        }
                    });

            return;
        }

        // DIRECT CHAT: preserve the existing behavior.
        inboxRepository.deleteConversationForMe(
                item.getConversationId(),
                task -> {

                    if (task.isSuccessful()) {

                        inboxItems.remove(item);

                        if (inboxAdapter != null) {
                            inboxAdapter.updateList(inboxItems);
                            inboxAdapter.clearSelection();
                        }

                        refreshInbox();

                        Toast.makeText(
                                this,
                                "Conversation deleted from your inbox.",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        showRepositoryError(
                                "Unable to delete conversation.",
                                task.getException()
                        );
                    }
                }
        );
    }


}
