
package com.example.arshithconnectattendance.chatnew.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.Collections;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.arshithconnectattendance.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupInfoActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    private FirebaseFirestore db;

    private String conversationId;
    private String createdBy = "";
    private String currentUid = "";
    private String currentGroupPhotoUrl = "";

    private TextView tvGroupName;
    private TextView tvMemberCount;
    private TextView tvGroupCreator;

    private ImageView imgGroupPhoto;

    private Button btnAddGroupMembers;
    private Button btnEditGroupDetails;

    private LinearLayout layoutGroupMembers;
    private Button btnTransferOwnership;
    private Button btnLeaveGroup;

    private FirebaseFunctions functions;

    private List<String> memberIds = new ArrayList<>();

    // Internal Java variable. Firestore field is groupAdmins.
    private List<String> adminIds = new ArrayList<>();

    private int currentMemberIndex = 0;

    private ActivityResultLauncher<Intent> addMembersLauncher;
    private ActivityResultLauncher<String> groupPhotoPicker;

    private boolean isUploadingGroupPhoto = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_group_info);

        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance("us-central1");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance()
                    .getCurrentUser()
                    .getUid();
        }

        conversationId = getIntent().getStringExtra(
                EXTRA_CONVERSATION_ID
        );

        tvGroupName = findViewById(R.id.tvGroupInfoName);
        tvMemberCount = findViewById(R.id.tvGroupMemberCount);
        tvGroupCreator = findViewById(R.id.tvGroupCreator);

        imgGroupPhoto = findViewById(R.id.imgGroupPhoto);

        layoutGroupMembers = findViewById(
                R.id.layoutGroupMembers
        );

        btnAddGroupMembers = findViewById(
                R.id.btnAddGroupMembers
        );

        btnEditGroupDetails = findViewById(
                R.id.btnEditGroupDetails
        );
        btnTransferOwnership = findViewById(R.id.btnTransferOwnership);
        btnLeaveGroup = findViewById(R.id.btnLeaveGroup);

        btnTransferOwnership.setOnClickListener(v -> showTransferOwnershipDialog());
        btnLeaveGroup.setOnClickListener(v -> confirmLeaveGroup());

        groupPhotoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadGroupPhoto(uri);
                    }
                }
        );

        addMembersLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {

                    if (result.getResultCode() != RESULT_OK
                            || result.getData() == null) {
                        return;
                    }

                    ArrayList<String> selectedIds =
                            result.getData().getStringArrayListExtra(
                                    ChatContactsActivity.RESULT_SELECTED_MEMBER_IDS
                            );

                    if (selectedIds == null || selectedIds.isEmpty()) {
                        Toast.makeText(
                                this,
                                "No employees selected.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    addMembersToGroup(selectedIds);
                }
        );

        findViewById(R.id.btnGroupInfoBack)
                .setOnClickListener(v -> finish());

        if (btnAddGroupMembers != null) {
            btnAddGroupMembers.setOnClickListener(
                    v -> openAddMembersScreen()
            );
        }

        if (btnEditGroupDetails != null) {
            btnEditGroupDetails.setOnClickListener(
                    v -> showEditGroupDetailsDialog()
            );
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Invalid group",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        loadGroupInformation();
    }

    // ==================================================
    // LOAD GROUP INFORMATION
    // ==================================================

    private void loadGroupInformation() {

        db.collection("chat_conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()
                            || !"group".equals(document.getString("type"))) {

                        Toast.makeText(
                                this,
                                "Group not found",
                                Toast.LENGTH_SHORT
                        ).show();

                        finish();
                        return;
                    }

                    // Correct Firestore field: name
                    String groupName = document.getString("name");

                    if (groupName == null || groupName.trim().isEmpty()) {
                        groupName = "Group";
                    }

                    tvGroupName.setText(groupName);

                    currentGroupPhotoUrl =
                            document.getString("groupPhotoUrl");

                    if (currentGroupPhotoUrl == null) {
                        currentGroupPhotoUrl = "";
                    }

                    displayGroupPhoto(currentGroupPhotoUrl);

                    createdBy = document.getString("createdBy");

                    if (createdBy == null) {
                        createdBy = "";
                    }

                    memberIds = getStringList(
                            document.get("memberIds")
                    );

                    // Correct Firestore field: groupAdmins
                    adminIds = getStringList(
                            document.get("groupAdmins")
                    );

                    tvMemberCount.setText(
                            memberIds.size() + " members"
                    );

                    updateAddMembersButton();
                    updateEditGroupDetailsButton();
                    updateOwnershipButton();

                    loadCreatorName();

                    layoutGroupMembers.removeAllViews();

                    currentMemberIndex = 0;

                    loadNextMember();

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load group information: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ==================================================
    // DISPLAY GROUP PHOTO
    // ==================================================

    private void displayGroupPhoto(String photoUrl) {

        if (imgGroupPhoto == null) {
            return;
        }

        if (photoUrl == null || photoUrl.trim().isEmpty()) {

            imgGroupPhoto.setImageResource(
                    android.R.drawable.ic_menu_myplaces
            );

            return;
        }

        Glide.with(this)
                .load(photoUrl)
                .placeholder(android.R.drawable.ic_menu_myplaces)
                .error(android.R.drawable.ic_menu_myplaces)
                .centerCrop()
                .into(imgGroupPhoto);
    }

    // ==================================================
    // ADMIN PERMISSIONS
    // ==================================================

    private boolean isCurrentUserAdmin() {

        return !currentUid.isEmpty()
                && (currentUid.equals(createdBy)
                || adminIds.contains(currentUid));
    }

    private boolean isCurrentUserCreator() {

        return !currentUid.isEmpty()
                && currentUid.equals(createdBy);
    }

    private void updateEditGroupDetailsButton() {

        if (btnEditGroupDetails == null) {
            return;
        }

        btnEditGroupDetails.setVisibility(
                isCurrentUserAdmin()
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void updateAddMembersButton() {

        if (btnAddGroupMembers == null) {
            return;
        }

        btnAddGroupMembers.setVisibility(
                isCurrentUserAdmin()
                        ? View.VISIBLE
                        : View.GONE
        );
    }
    private void updateOwnershipButton() {
        if (btnTransferOwnership != null) {
            btnTransferOwnership.setVisibility(
                    isCurrentUserCreator() ? View.VISIBLE : View.GONE
            );
        }
    }

    // ==================================================
    // EDIT GROUP DETAILS
    // ==================================================

    private void showEditGroupDetailsDialog() {

        if (!isCurrentUserAdmin()) {

            Toast.makeText(
                    this,
                    "Only group admins can edit group details.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setHint("Enter group name");
        input.setText(tvGroupName.getText().toString().trim());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Edit Group Details")
                .setView(input)
                .setNegativeButton("Cancel", (dialog, which) ->
                        dialog.dismiss()
                )
                .setNeutralButton("Change Photo", (dialog, which) -> {

                    if (isUploadingGroupPhoto) {
                        Toast.makeText(
                                this,
                                "Please wait for the current upload.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    groupPhotoPicker.launch("image/*");
                })
                .setPositiveButton("Save Name", (dialog, which) -> {

                    String newName = input.getText()
                            .toString()
                            .trim();

                    if (newName.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Group name cannot be empty.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (newName.length() > 60) {

                        Toast.makeText(
                                this,
                                "Group name cannot exceed 60 characters.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (!newName.equals(
                            tvGroupName.getText().toString().trim()
                    )) {
                        updateGroupName(newName);
                    }
                })
                .show();
    }

    // ==================================================
    // UPDATE GROUP NAME
    // ==================================================

    private void updateGroupName(String newName) {

        if (!isCurrentUserAdmin()) {
            return;
        }

        db.collection("chat_conversations")
                .document(conversationId)
                .update(
                        "name", newName,
                        "updatedAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {

                    tvGroupName.setText(newName);

                    Toast.makeText(
                            this,
                            "Group name updated successfully.",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .addOnFailureListener(error -> {

                    Toast.makeText(
                            this,
                            "Unable to update group name: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ==================================================
    // UPLOAD GROUP PHOTO
    // ==================================================

    private void uploadGroupPhoto(Uri imageUri) {

        if (!isCurrentUserAdmin()) {

            Toast.makeText(
                    this,
                    "Only group admins can change the group photo.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (imageUri == null || isUploadingGroupPhoto) {
            return;
        }

        isUploadingGroupPhoto = true;

        btnEditGroupDetails.setEnabled(false);

        Toast.makeText(
                this,
                "Uploading group photo...",
                Toast.LENGTH_SHORT
        ).show();

        StorageReference photoReference = FirebaseStorage
                .getInstance()
                .getReference()
                .child("groupPhotos")
                .child(conversationId)
                .child("group.jpg");

        photoReference.putFile(imageUri)
                .continueWithTask(task -> {

                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    return photoReference.getDownloadUrl();

                })
                .addOnSuccessListener(downloadUri -> {

                    saveGroupPhotoUrl(downloadUri);

                })
                .addOnFailureListener(error -> {

                    isUploadingGroupPhoto = false;
                    btnEditGroupDetails.setEnabled(true);

                    displayGroupPhoto(currentGroupPhotoUrl);

                    Toast.makeText(
                            this,
                            "Photo upload failed: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    private void saveGroupPhotoUrl(Uri downloadUri) {

        String photoUrl = downloadUri.toString();

        db.collection("chat_conversations")
                .document(conversationId)
                .update(
                        "groupPhotoUrl", photoUrl,
                        "updatedAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {

                    currentGroupPhotoUrl = photoUrl;

                    displayGroupPhoto(photoUrl);

                    isUploadingGroupPhoto = false;
                    btnEditGroupDetails.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Group photo updated successfully.",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .addOnFailureListener(error -> {

                    isUploadingGroupPhoto = false;
                    btnEditGroupDetails.setEnabled(true);

                    displayGroupPhoto(currentGroupPhotoUrl);

                    Toast.makeText(
                            this,
                            "Unable to save group photo: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ==================================================
    // FIRESTORE LIST HELPERS
    // ==================================================

    private List<String> getStringList(Object value) {

        List<String> result = new ArrayList<>();

        if (!(value instanceof List)) {
            return result;
        }

        List<?> values = (List<?>) value;

        for (Object item : values) {

            if (item instanceof String) {

                String uid = ((String) item).trim();

                if (!uid.isEmpty() && !result.contains(uid)) {
                    result.add(uid);
                }
            }
        }

        return result;
    }

    // ==================================================
    // OPEN ADD MEMBERS SCREEN
    // ==================================================

    private void openAddMembersScreen() {

        if (!isCurrentUserAdmin()) {

            Toast.makeText(
                    this,
                    "Only group admins can add members.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent = new Intent(
                GroupInfoActivity.this,
                ChatContactsActivity.class
        );

        intent.putExtra(
                ChatContactsActivity.EXTRA_ADD_TO_GROUP,
                true
        );

        intent.putExtra(
                ChatContactsActivity.EXTRA_GROUP_CONVERSATION_ID,
                conversationId
        );

        addMembersLauncher.launch(intent);
    }

    // ==================================================
    // ADD SELECTED MEMBERS
    // ==================================================

    private void addMembersToGroup(List<String> selectedIds) {

        if (!isCurrentUserAdmin()) {
            return;
        }

        setAddButtonLoading(true);

        db.runTransaction(transaction -> {

            DocumentReference groupRef = db.collection(
                    "chat_conversations"
            ).document(conversationId);

            DocumentSnapshot snapshot = transaction.get(groupRef);

            if (!snapshot.exists()
                    || !"group".equals(snapshot.getString("type"))) {

                throw new IllegalStateException("Group not found.");
            }

            String creatorId = snapshot.getString("createdBy");

            List<String> existingMembers = getStringList(
                    snapshot.get("memberIds")
            );

            // Correct Firestore field: groupAdmins
            List<String> existingAdmins = getStringList(
                    snapshot.get("groupAdmins")
            );

            if (!currentUid.equals(creatorId)
                    && !existingAdmins.contains(currentUid)) {

                throw new IllegalStateException(
                        "Only group admins can add members."
                );
            }

            List<String> updatedMembers =
                    new ArrayList<>(existingMembers);

            int addedCount = 0;

            for (String uid : selectedIds) {

                if (uid == null) {
                    continue;
                }

                uid = uid.trim();

                if (uid.isEmpty()
                        || updatedMembers.contains(uid)) {
                    continue;
                }

                if (updatedMembers.size() >= 100) {
                    throw new IllegalStateException(
                            "Maximum group size is 100 members."
                    );
                }

                updatedMembers.add(uid);
                addedCount++;
            }

            if (addedCount == 0) {
                throw new IllegalStateException(
                        "All selected employees are already members."
                );
            }

            Map<String, Object> unreadCounts =
                    getUpdatedUnreadCounts(
                            snapshot.get("unreadCounts"),
                            updatedMembers
                    );

            Map<String, Object> updates = new HashMap<>();

            updates.put("memberIds", updatedMembers);
            updates.put("unreadCounts", unreadCounts);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(groupRef, updates);

            return addedCount;

        }).addOnSuccessListener(addedCount -> {

            setAddButtonLoading(false);

            Toast.makeText(
                    this,
                    addedCount + " member(s) added successfully.",
                    Toast.LENGTH_LONG
            ).show();

            loadGroupInformation();

        }).addOnFailureListener(error -> {

            setAddButtonLoading(false);

            Toast.makeText(
                    this,
                    "Unable to add members: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        });
    }

    private void setAddButtonLoading(boolean loading) {

        if (btnAddGroupMembers == null) {
            return;
        }

        btnAddGroupMembers.setEnabled(!loading);

        btnAddGroupMembers.setText(
                loading ? "Please wait..." : "Add Members"
        );
    }

    // ==================================================
    // REMOVE MEMBER
    // ==================================================

    private void confirmRemoveMember(String targetUid, String name) {

        if (!isCurrentUserAdmin()) {

            Toast.makeText(
                    this,
                    "Only group admins can remove members.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (targetUid.equals(createdBy) || adminIds.contains(targetUid)) {

            Toast.makeText(
                    this,
                    "The group creator and admins cannot be removed here.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove Group Member")
                .setMessage(
                        "Are you sure you want to remove "
                                + name + " from this group?"
                )
                .setNegativeButton("Cancel", (dialog, which) ->
                        dialog.dismiss()
                )
                .setPositiveButton("Remove", (dialog, which) ->
                        removeMemberFromGroup(targetUid)
                )
                .show();
    }

    private void removeMemberFromGroup(String targetUid) {

        if (!isCurrentUserAdmin()) {
            return;
        }

        db.runTransaction(transaction -> {

            DocumentReference groupRef = db.collection(
                    "chat_conversations"
            ).document(conversationId);

            DocumentSnapshot snapshot = transaction.get(groupRef);

            if (!snapshot.exists()
                    || !"group".equals(snapshot.getString("type"))) {

                throw new IllegalStateException("Group not found.");
            }

            String creatorId = snapshot.getString("createdBy");

            List<String> existingMembers = getStringList(
                    snapshot.get("memberIds")
            );

            // Correct Firestore field: groupAdmins
            List<String> existingAdmins = getStringList(
                    snapshot.get("groupAdmins")
            );

            if (!currentUid.equals(creatorId)
                    && !existingAdmins.contains(currentUid)) {

                throw new IllegalStateException(
                        "Only group admins can remove members."
                );
            }

            if (targetUid.equals(creatorId)
                    || existingAdmins.contains(targetUid)) {

                throw new IllegalStateException(
                        "The creator or an admin cannot be removed here."
                );
            }

            if (!existingMembers.contains(targetUid)) {
                throw new IllegalStateException(
                        "This employee is not a group member."
                );
            }

            if (existingMembers.size() <= 2) {
                throw new IllegalStateException(
                        "A group must have at least two members."
                );
            }

            List<String> updatedMembers =
                    new ArrayList<>(existingMembers);

            updatedMembers.remove(targetUid);

            Map<String, Object> updatedUnreadCounts =
                    getUpdatedUnreadCounts(
                            snapshot.get("unreadCounts"),
                            updatedMembers
                    );

            Map<String, Object> updates = new HashMap<>();

            updates.put("memberIds", updatedMembers);
            updates.put("unreadCounts", updatedUnreadCounts);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(groupRef, updates);

            return true;

        }).addOnSuccessListener(success -> {

            Toast.makeText(
                    this,
                    "Member removed successfully.",
                    Toast.LENGTH_SHORT
            ).show();

            loadGroupInformation();

        }).addOnFailureListener(error -> {

            Toast.makeText(
                    this,
                    "Unable to remove member: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        });
    }

    // ==================================================
    // UNREAD COUNTS
    // ==================================================

    private Map<String, Object> getUpdatedUnreadCounts(
            Object unreadObject,
            List<String> updatedMembers
    ) {

        Map<String, Object> updatedUnreadCounts =
                new HashMap<>();

        if (unreadObject instanceof Map) {

            Map<?, ?> existingUnreadCounts =
                    (Map<?, ?>) unreadObject;

            for (Map.Entry<?, ?> entry :
                    existingUnreadCounts.entrySet()) {

                if (entry.getKey() instanceof String) {

                    String uid = (String) entry.getKey();

                    if (updatedMembers.contains(uid)) {

                        updatedUnreadCounts.put(
                                uid,
                                entry.getValue()
                        );
                    }
                }
            }
        }

        for (String uid : updatedMembers) {

            if (!updatedUnreadCounts.containsKey(uid)) {
                updatedUnreadCounts.put(uid, 0L);
            }
        }

        return updatedUnreadCounts;
    }

    // ==================================================
    // LOAD CREATOR NAME
    // ==================================================

    private void loadCreatorName() {

        if (createdBy == null || createdBy.isEmpty()) {

            tvGroupCreator.setText("Creator: Unknown");
            return;
        }

        loadUserName(createdBy, name ->
                tvGroupCreator.setText("Created by: " + name)
        );
    }

    // ==================================================
    // ADMIN ROLE CHANGE
    // ==================================================

    private void confirmAdminRoleChange(
            String targetUid,
            String name
    ) {

        if (!isCurrentUserCreator()) {

            Toast.makeText(
                    this,
                    "Only the group creator can manage admins.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (targetUid == null || targetUid.equals(createdBy)) {
            return;
        }

        boolean currentlyAdmin = adminIds.contains(targetUid);

        String title = currentlyAdmin
                ? "Remove Admin Role"
                : "Make Group Admin";

        String message = currentlyAdmin
                ? "Remove admin privileges from " + name + "?"
                : "Make " + name + " an admin of this group?";

        String buttonText = currentlyAdmin
                ? "Remove Admin"
                : "Make Admin";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", (dialog, which) ->
                        dialog.dismiss()
                )
                .setPositiveButton(buttonText, (dialog, which) ->
                        updateAdminRole(targetUid, currentlyAdmin)
                )
                .show();
    }

    private void updateAdminRole(
            String targetUid,
            boolean currentlyAdmin
    ) {

        if (!isCurrentUserCreator()) {

            Toast.makeText(
                    this,
                    "Only the group creator can manage admins.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        db.runTransaction(transaction -> {

            DocumentReference groupRef = db.collection(
                    "chat_conversations"
            ).document(conversationId);

            DocumentSnapshot snapshot = transaction.get(groupRef);

            if (!snapshot.exists()
                    || !"group".equals(snapshot.getString("type"))) {

                throw new IllegalStateException("Group not found.");
            }

            String creatorId = snapshot.getString("createdBy");

            if (!currentUid.equals(creatorId)) {
                throw new IllegalStateException(
                        "Only the group creator can manage admins."
                );
            }

            List<String> existingMembers = getStringList(
                    snapshot.get("memberIds")
            );

            // Correct Firestore field: groupAdmins
            List<String> existingAdmins = getStringList(
                    snapshot.get("groupAdmins")
            );

            if (!existingMembers.contains(targetUid)) {
                throw new IllegalStateException(
                        "This employee is not a group member."
                );
            }

            if (targetUid.equals(creatorId)) {
                throw new IllegalStateException(
                        "The group creator cannot be demoted."
                );
            }

            List<String> updatedAdmins =
                    new ArrayList<>(existingAdmins);

            if (currentlyAdmin) {

                if (!updatedAdmins.remove(targetUid)) {
                    throw new IllegalStateException(
                            "This member is not an admin."
                    );
                }

            } else {

                if (updatedAdmins.contains(targetUid)) {
                    throw new IllegalStateException(
                            "This member is already an admin."
                    );
                }

                updatedAdmins.add(targetUid);
            }

            if (!updatedAdmins.contains(creatorId)) {
                throw new IllegalStateException(
                        "The group creator must remain an admin."
                );
            }

            Map<String, Object> updates = new HashMap<>();

            // Correct Firestore update field: groupAdmins
            updates.put("groupAdmins", updatedAdmins);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(groupRef, updates);

            return true;

        }).addOnSuccessListener(success -> {

            Toast.makeText(
                    this,
                    currentlyAdmin
                            ? "Admin role removed successfully."
                            : "Member promoted to admin successfully.",
                    Toast.LENGTH_LONG
            ).show();

            loadGroupInformation();

        }).addOnFailureListener(error -> {

            Toast.makeText(
                    this,
                    "Unable to update admin role: "
                            + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        });
    }
    // ==================================================
// TRANSFER GROUP OWNERSHIP
// ==================================================

    private void showTransferOwnershipDialog() {

        if (!isCurrentUserCreator()) {
            Toast.makeText(this,
                    "Only the group creator can transfer ownership.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> eligibleMembers = new ArrayList<>();

        for (String uid : memberIds) {
            if (!uid.equals(currentUid)) {
                eligibleMembers.add(uid);
            }
        }

        if (eligibleMembers.isEmpty()) {
            Toast.makeText(this,
                    "There are no other members to transfer ownership to.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[eligibleMembers.size()];

        loadTransferMemberNames(eligibleMembers, names, 0);
    }

    private void loadTransferMemberNames(
            List<String> eligibleMembers,
            String[] names,
            int index
    ) {
        if (index >= eligibleMembers.size()) {

            new AlertDialog.Builder(this)
                    .setTitle("Transfer Group Ownership")
                    .setItems(names, (dialog, which) -> {

                        String targetUid = eligibleMembers.get(which);
                        String targetName = names[which];

                        new AlertDialog.Builder(this)
                                .setTitle("Confirm Ownership Transfer")
                                .setMessage(
                                        "Transfer ownership of this group to "
                                                + targetName
                                                + "?\n\nYou will no longer be the group creator."
                                )
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Transfer", (d, w) ->
                                        transferGroupOwnership(targetUid)
                                )
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            return;
        }

        String uid = eligibleMembers.get(index);

        loadUserName(uid, name -> {
            names[index] = name;
            loadTransferMemberNames(eligibleMembers, names, index + 1);
        });
    }

    private void transferGroupOwnership(String targetUid) {

        if (!isCurrentUserCreator()) {
            Toast.makeText(this,
                    "Only the current creator can transfer ownership.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnTransferOwnership.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("action", "transferOwnership");
        data.put("targetUid", targetUid);

        functions.getHttpsCallable("manageGroupMembership")
                .call(data)
                .addOnSuccessListener(result -> {

                    btnTransferOwnership.setEnabled(true);

                    Toast.makeText(this,
                            "Group ownership transferred successfully.",
                            Toast.LENGTH_LONG).show();

                    loadGroupInformation();
                })
                .addOnFailureListener(error -> {

                    btnTransferOwnership.setEnabled(true);

                    Toast.makeText(this,
                            "Unable to transfer ownership: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

// ==================================================
// LEAVE GROUP
// ==================================================

    private void confirmLeaveGroup() {

        if (isCurrentUserCreator()) {
            new AlertDialog.Builder(this)
                    .setTitle("Transfer Ownership First")
                    .setMessage(
                            "You are the group creator. Transfer ownership "
                                    + "to another member before leaving this group."
                    )
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Leave Group")
                .setMessage(
                        "Are you sure you want to leave this group? "
                                + "You will no longer receive its messages."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Leave", (dialog, which) ->
                        leaveGroup()
                )
                .show();
    }

    private void leaveGroup() {

        if (currentUid.isEmpty() || !memberIds.contains(currentUid)) {
            Toast.makeText(this,
                    "You are not a member of this group.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnLeaveGroup.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("action", "leave");

        functions.getHttpsCallable("manageGroupMembership")
                .call(data)
                .addOnSuccessListener(result -> {

                    Toast.makeText(this,
                            "You left the group successfully.",
                            Toast.LENGTH_LONG).show();

                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(error -> {

                    btnLeaveGroup.setEnabled(true);

                    Toast.makeText(this,
                            "Unable to leave group: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ==================================================
    // LOAD MEMBERS
    // ==================================================

    private void loadNextMember() {

        if (currentMemberIndex >= memberIds.size()) {
            return;
        }

        String uid = memberIds.get(currentMemberIndex);

        currentMemberIndex++;

        loadUserName(uid, name -> {

            boolean isAdmin = adminIds.contains(uid);
            boolean isCreator = uid.equals(createdBy);

            LinearLayout memberRow = new LinearLayout(this);

            memberRow.setOrientation(LinearLayout.HORIZONTAL);
            memberRow.setGravity(Gravity.CENTER_VERTICAL);
            memberRow.setPadding(14, 12, 14, 12);

            memberRow.setBackgroundResource(
                    android.R.drawable.list_selector_background
            );

            memberRow.setOnClickListener(v -> {

                Intent intent = new Intent(
                        GroupInfoActivity.this,
                        ViewEmployeeProfileActivity.class
                );

                intent.putExtra(
                        ViewEmployeeProfileActivity.EXTRA_EMPLOYEE_UID,
                        uid
                );

                startActivity(intent);
            });

            LinearLayout detailsLayout = new LinearLayout(this);

            detailsLayout.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams detailsParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    );

            TextView nameView = new TextView(this);

            nameView.setText(name);
            nameView.setTextSize(15);
            nameView.setTextColor(0xFF202124);
            nameView.setGravity(Gravity.CENTER_VERTICAL);

            detailsLayout.addView(nameView);

            String role = "";

            if (isCreator) {
                role = "Group creator";
            } else if (isAdmin) {
                role = "Group admin";
            }

            if (!role.isEmpty()) {

                TextView roleView = new TextView(this);

                roleView.setText(role);
                roleView.setTextSize(12);
                roleView.setTextColor(0xFF075E54);

                detailsLayout.addView(roleView);
            }

            memberRow.addView(detailsLayout, detailsParams);

            if (isCurrentUserCreator() && !isCreator) {

                Button adminButton = new Button(this);

                adminButton.setText(
                        isAdmin ? "Remove Admin" : "Make Admin"
                );

                adminButton.setAllCaps(false);
                adminButton.setTextSize(11);
                adminButton.setMinWidth(0);
                adminButton.setMinimumWidth(0);
                adminButton.setPadding(8, 4, 8, 4);

                adminButton.setOnClickListener(v ->
                        confirmAdminRoleChange(uid, name)
                );

                memberRow.addView(adminButton);
            }

            if (isCurrentUserAdmin()
                    && !isCreator
                    && !isAdmin) {

                Button removeButton = new Button(this);

                removeButton.setText("Remove");
                removeButton.setAllCaps(false);
                removeButton.setTextSize(12);
                removeButton.setTextColor(0xFFB3261E);

                removeButton.setMinWidth(0);
                removeButton.setMinimumWidth(0);
                removeButton.setPadding(10, 4, 10, 4);

                removeButton.setOnClickListener(v ->
                        confirmRemoveMember(uid, name)
                );

                memberRow.addView(removeButton);
            }

            layoutGroupMembers.addView(memberRow);

            ViewDivider divider = new ViewDivider(this);

            layoutGroupMembers.addView(divider);

            loadNextMember();
        });
    }

    // ==================================================
    // LOAD USER NAME
    // ==================================================

    private void loadUserName(
            String uid,
            NameCallback callback
    ) {

        db.collection("employees")
                .document(uid)
                .get()
                .addOnSuccessListener(employeeDoc -> {

                    String name = getNameFromDocument(employeeDoc);

                    if (name != null) {
                        callback.onName(name);
                    } else {
                        loadChatUserName(uid, callback);
                    }

                })
                .addOnFailureListener(e ->
                        loadChatUserName(uid, callback)
                );
    }

    private void loadChatUserName(
            String uid,
            NameCallback callback
    ) {

        db.collection("chat_users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String name = getNameFromDocument(userDoc);

                    if (name == null) {
                        name = uid;
                    }

                    callback.onName(name);

                })
                .addOnFailureListener(e ->
                        callback.onName(uid)
                );
    }

    private String getNameFromDocument(
            DocumentSnapshot document
    ) {

        if (document == null || !document.exists()) {
            return null;
        }

        String[] possibleFields = {
                "name",
                "fullName",
                "employeeName",
                "displayName",
                "firstName"
        };

        for (String field : possibleFields) {

            String value = document.getString(field);

            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return null;
    }

    // ==================================================
    // CALLBACKS
    // ==================================================

    private interface NameCallback {
        void onName(String name);
    }

    // ==================================================
    // MEMBER DIVIDER
    // ==================================================

    private class ViewDivider extends View {

        public ViewDivider(android.content.Context context) {

            super(context);

            setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                    )
            );

            setBackgroundColor(0xFFE5E7EB);
        }
    }
}
