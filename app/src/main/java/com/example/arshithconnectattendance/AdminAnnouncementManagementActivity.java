package com.example.arshithconnectattendance;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AdminAnnouncementManagementActivity extends ComponentActivity {

    // =========================================================
    // FIREBASE
    // =========================================================

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    // =========================================================
    // MAIN PAGE
    // =========================================================

    private LinearLayout announcementHomeLayout;
    private LinearLayout createAnnouncementLayout;

    // =========================================================
    // BUTTONS
    // =========================================================

    private Button btnAddAnnouncement;
    private Button btnPublishAnnouncement;
    private Button btnCancelAnnouncement;
    private Button btnSelectEmployees;

    // =========================================================
    // INPUTS
    // =========================================================

    private EditText etAnnouncementTitle;
    private EditText etAnnouncementMessage;

    private Spinner spinnerAnnouncementType;
    private Spinner spinnerSendTo;

    // =========================================================
    // OTHER
    // =========================================================

    private LinearLayout announcementsContainer;
    private TextView tvSelectedMembers;

    private ArrayList<String> selectedEmployeeIds =
            new ArrayList<>();

    private ArrayList<String> selectedEmployeeNames =
            new ArrayList<>();

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_announcement_management
        );

        // =====================================================
        // FIREBASE
        // =====================================================

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        // =====================================================
        // INITIALIZE
        // =====================================================

        initializeViews();

        // =====================================================
        // SETUP SPINNERS
        // =====================================================

        setupAnnouncementType();

        setupSendToSpinner();

        // =====================================================
        // INITIAL PAGE
        // =====================================================

        showAnnouncementHome();

        // =====================================================
        // ADD ANNOUNCEMENT
        // =====================================================

        btnAddAnnouncement.setOnClickListener(
                v -> showCreateAnnouncement()
        );

        // =====================================================
        // CANCEL
        // =====================================================

        btnCancelAnnouncement.setOnClickListener(
                v -> showAnnouncementHome()
        );

        // =====================================================
        // SEND TO
        // =====================================================

        spinnerSendTo.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        if (position == 1) {

                            btnSelectEmployees
                                    .setVisibility(View.VISIBLE);

                            tvSelectedMembers
                                    .setVisibility(View.VISIBLE);

                        } else {

                            btnSelectEmployees
                                    .setVisibility(View.GONE);

                            tvSelectedMembers
                                    .setVisibility(View.GONE);

                            selectedEmployeeIds.clear();

                            selectedEmployeeNames.clear();

                            tvSelectedMembers.setText(
                                    "No members selected"
                            );
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                    }
                }
        );

        // =====================================================
        // SELECT MEMBERS
        // =====================================================

        btnSelectEmployees.setOnClickListener(
                v -> showEmployeeSelection()
        );

        // =====================================================
        // PUBLISH
        // =====================================================

        btnPublishAnnouncement.setOnClickListener(
                v -> publishAnnouncement()
        );

        // =====================================================
        // LOAD EXISTING
        // =====================================================

        loadAnnouncements();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        announcementHomeLayout =
                findViewById(
                        R.id.announcementHomeLayout
                );

        createAnnouncementLayout =
                findViewById(
                        R.id.createAnnouncementLayout
                );

        btnAddAnnouncement =
                findViewById(
                        R.id.btnAddAnnouncement
                );

        btnPublishAnnouncement =
                findViewById(
                        R.id.btnPublishAnnouncement
                );

        btnCancelAnnouncement =
                findViewById(
                        R.id.btnCancelAnnouncement
                );

        btnSelectEmployees =
                findViewById(
                        R.id.btnSelectEmployees
                );

        etAnnouncementTitle =
                findViewById(
                        R.id.etAnnouncementTitle
                );

        etAnnouncementMessage =
                findViewById(
                        R.id.etAnnouncementMessage
                );

        spinnerAnnouncementType =
                findViewById(
                        R.id.spinnerAnnouncementType
                );

        spinnerSendTo =
                findViewById(
                        R.id.spinnerSendTo
                );

        tvSelectedMembers =
                findViewById(
                        R.id.tvSelectedMembers
                );

        announcementsContainer =
                findViewById(
                        R.id.announcementsContainer
                );
    }

    // =========================================================
    // SHOW ANNOUNCEMENT HOME
    // =========================================================

    private void showAnnouncementHome() {

        announcementHomeLayout
                .setVisibility(View.VISIBLE);

        createAnnouncementLayout
                .setVisibility(View.GONE);

        // Clear form

        etAnnouncementTitle.setText("");

        etAnnouncementMessage.setText("");

        spinnerAnnouncementType.setSelection(0);

        spinnerSendTo.setSelection(0);

        selectedEmployeeIds.clear();

        selectedEmployeeNames.clear();

        tvSelectedMembers.setText(
                "No members selected"
        );

        btnSelectEmployees
                .setVisibility(View.GONE);

        tvSelectedMembers
                .setVisibility(View.GONE);

        btnPublishAnnouncement
                .setEnabled(true);

        loadAnnouncements();
    }

    // =========================================================
    // SHOW CREATE ANNOUNCEMENT
    // =========================================================

    private void showCreateAnnouncement() {

        announcementHomeLayout
                .setVisibility(View.GONE);

        createAnnouncementLayout
                .setVisibility(View.VISIBLE);

        etAnnouncementTitle
                .requestFocus();

        findViewById(
                R.id.createAnnouncementLayout
        ).post(
                () -> {
                }
        );
    }

    // =========================================================
    // ANNOUNCEMENT TYPE
    // =========================================================

    private void setupAnnouncementType() {

        String[] types = {

                "General",
                "Important",
                "Holiday",
                "Meeting"

        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        types
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAnnouncementType
                .setAdapter(adapter);
    }

    // =========================================================
    // SEND TO SPINNER
    // =========================================================

    private void setupSendToSpinner() {

        String[] sendOptions = {

                "All Employees",
                "Selected Employees"

        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        sendOptions
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerSendTo.setAdapter(adapter);
    }

    // =========================================================
    // SHOW EMPLOYEE SELECTION
    // =========================================================

    private void showEmployeeSelection() {

        /*
         * IMPORTANT:
         *
         * Employees are stored in:
         *
         * employees/{uid}
         *
         * So we MUST read the employees collection.
         *
         * Previously this was:
         *
         * firestore.collection("users")
         *
         * which caused "No employees found".
         */

        firestore
                .collection("employees")
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            ArrayList<String> employeeNames =
                                    new ArrayList<>();

                            ArrayList<String> employeeIds =
                                    new ArrayList<>();

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                // -----------------------------------------
                                // GET EMPLOYEE NAME
                                // -----------------------------------------

                                String name =
                                        document.getString("name");

                                // -----------------------------------------
                                // GET EMPLOYEE EMAIL
                                // -----------------------------------------

                                String email =
                                        document.getString("email");

                                /*
                                 * If name doesn't exist,
                                 * use email instead.
                                 */

                                if (name == null ||
                                        name.trim().isEmpty()) {

                                    name =
                                            email != null &&
                                                    !email.trim().isEmpty()
                                                    ? email
                                                    : "Employee";
                                }

                                /*
                                 * Display both name and email when
                                 * possible so admin can identify
                                 * the employee.
                                 */

                                String displayName;

                                if (email != null &&
                                        !email.trim().isEmpty()) {

                                    displayName =
                                            name + "\n" + email;

                                } else {

                                    displayName =
                                            name;
                                }

                                employeeNames.add(
                                        displayName
                                );

                                /*
                                 * VERY IMPORTANT:
                                 *
                                 * employees document ID is the
                                 * Firebase Authentication UID.
                                 *
                                 * This is exactly what the Cloud
                                 * Function expects.
                                 */

                                employeeIds.add(
                                        document.getId()
                                );
                            }

                            // =================================================
                            // NO EMPLOYEES
                            // =================================================

                            if (employeeNames.isEmpty()) {

                                Toast.makeText(
                                        this,
                                        "No employees found.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            // =================================================
                            // CHECKED ITEMS
                            // =================================================

                            boolean[] checked =
                                    new boolean[
                                            employeeNames.size()
                                            ];

                            // Restore previous selection

                            for (
                                    int i = 0;
                                    i < employeeIds.size();
                                    i++
                            ) {

                                if (
                                        selectedEmployeeIds
                                                .contains(
                                                        employeeIds.get(i)
                                                )
                                ) {

                                    checked[i] = true;
                                }
                            }

                            // =================================================
                            // EMPLOYEE SELECTION DIALOG
                            // =================================================

                            AlertDialog dialog =
                                    new AlertDialog.Builder(
                                            this
                                    )
                                            .setTitle(
                                                    "Select Employees"
                                            )
                                            .setMultiChoiceItems(
                                                    employeeNames.toArray(
                                                            new String[0]
                                                    ),
                                                    checked,
                                                    (
                                                            dialogInterface,
                                                            which,
                                                            isChecked
                                                    ) -> {

                                                        String employeeId =
                                                                employeeIds.get(
                                                                        which
                                                                );

                                                        /*
                                                         * employeeNames
                                                         * contains:
                                                         *
                                                         * Name
                                                         * Email
                                                         */

                                                        String employeeName =
                                                                employeeNames.get(
                                                                        which
                                                                );

                                                        if (isChecked) {

                                                            if (
                                                                    !selectedEmployeeIds
                                                                            .contains(
                                                                                    employeeId
                                                                            )
                                                            ) {

                                                                selectedEmployeeIds
                                                                        .add(
                                                                                employeeId
                                                                        );

                                                                selectedEmployeeNames
                                                                        .add(
                                                                                employeeName
                                                                        );
                                                            }

                                                        } else {

                                                            selectedEmployeeIds
                                                                    .remove(
                                                                            employeeId
                                                                    );

                                                            selectedEmployeeNames
                                                                    .remove(
                                                                            employeeName
                                                                    );
                                                        }
                                                    }
                                            )
                                            .setPositiveButton(
                                                    "DONE",
                                                    (
                                                            dialogInterface,
                                                            which
                                                    ) -> {

                                                        updateSelectedMembersText();
                                                    }
                                            )
                                            .setNegativeButton(
                                                    "CANCEL",
                                                    null
                                            )
                                            .create();

                            dialog.show();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load employees: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // UPDATE SELECTED MEMBERS TEXT
    // =========================================================

    private void updateSelectedMembersText() {

        if (selectedEmployeeNames.isEmpty()) {

            tvSelectedMembers.setText(
                    "No members selected"
            );

            return;
        }

        StringBuilder names =
                new StringBuilder();

        names.append("Selected: ");

        for (
                int i = 0;
                i < selectedEmployeeNames.size();
                i++
        ) {

            if (i > 0) {
                names.append(", ");
            }

            names.append(
                    selectedEmployeeNames.get(i)
            );
        }

        tvSelectedMembers.setText(
                names.toString()
        );
    }

    // =========================================================
    // PUBLISH ANNOUNCEMENT
    // =========================================================

    private void publishAnnouncement() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Admin login required.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String title =
                etAnnouncementTitle
                        .getText()
                        .toString()
                        .trim();

        String message =
                etAnnouncementMessage
                        .getText()
                        .toString()
                        .trim();

        String type =
                spinnerAnnouncementType
                        .getSelectedItem()
                        .toString();

        String sendTo =
                spinnerSendTo
                        .getSelectedItem()
                        .toString();

        // =====================================================
        // VALIDATION
        // =====================================================

        if (TextUtils.isEmpty(title)) {

            etAnnouncementTitle.setError(
                    "Enter announcement title"
            );

            etAnnouncementTitle.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(message)) {

            etAnnouncementMessage.setError(
                    "Enter announcement message"
            );

            etAnnouncementMessage.requestFocus();

            return;
        }

        if (
                sendTo.equals("Selected Employees")
                        &&
                        selectedEmployeeIds.isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Please select at least one employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // =====================================================
        // DISABLE BUTTON
        // =====================================================

        btnPublishAnnouncement
                .setEnabled(false);

        // =====================================================
        // ANNOUNCEMENT DATA
        // =====================================================

        Map<String, Object> announcement =
                new HashMap<>();

        announcement.put(
                "title",
                title
        );

        announcement.put(
                "message",
                message
        );

        announcement.put(
                "type",
                type
        );

        announcement.put(
                "sendTo",
                sendTo
        );

        announcement.put(
                "createdAt",
                FieldValue.serverTimestamp()
        );

        announcement.put(
                "createdBy",
                currentUser.getUid()
        );

        if (currentUser.getEmail() != null) {

            announcement.put(
                    "createdByEmail",
                    currentUser.getEmail()
            );
        }

        // =====================================================
        // SELECTED EMPLOYEES
        // =====================================================

        if (
                sendTo.equals(
                        "Selected Employees"
                )
        ) {

            announcement.put(
                    "selectedEmployeeIds",
                    new ArrayList<>(
                            selectedEmployeeIds
                    )
            );

            announcement.put(
                    "selectedEmployeeNames",
                    new ArrayList<>(
                            selectedEmployeeNames
                    )
            );
        }

        // =====================================================
        // SAVE FIRESTORE
        // =====================================================

        firestore
                .collection("announcements")
                .add(announcement)
                .addOnSuccessListener(
                        documentReference -> {

                            Toast.makeText(
                                    AdminAnnouncementManagementActivity.this,
                                    "Announcement published.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            showAnnouncementHome();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            btnPublishAnnouncement
                                    .setEnabled(true);

                            Toast.makeText(
                                    AdminAnnouncementManagementActivity.this,
                                    "Unable to publish announcement: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // LOAD ANNOUNCEMENTS
    // =========================================================

    private void loadAnnouncements() {

        announcementsContainer
                .removeAllViews();

        firestore
                .collection("announcements")
                .orderBy(
                        "createdAt",
                        Query.Direction.DESCENDING
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            if (
                                    querySnapshot.isEmpty()
                            ) {

                                TextView emptyText =
                                        new TextView(
                                                this
                                        );

                                emptyText.setText(
                                        "No announcements yet."
                                );

                                emptyText.setTextSize(
                                        15
                                );

                                emptyText.setTextColor(
                                        0xFF777080
                                );

                                emptyText.setPadding(
                                        8,
                                        20,
                                        8,
                                        20
                                );

                                announcementsContainer
                                        .addView(
                                                emptyText
                                        );

                                return;
                            }

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                addAnnouncementCard(
                                        document
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        e -> {

                            TextView errorText =
                                    new TextView(
                                            this
                                    );

                            errorText.setText(
                                    "Unable to load announcements."
                            );

                            errorText.setTextSize(
                                    15
                            );

                            errorText.setTextColor(
                                    0xFFD04444
                            );

                            errorText.setPadding(
                                    8,
                                    20,
                                    8,
                                    20
                            );

                            announcementsContainer
                                    .addView(
                                            errorText
                                    );
                        }
                );
    }

    // =========================================================
    // ADD ANNOUNCEMENT CARD
    // =========================================================

    private void addAnnouncementCard(
            DocumentSnapshot document
    ) {

        String title =
                document.getString(
                        "title"
                );

        String message =
                document.getString(
                        "message"
                );

        String type =
                document.getString(
                        "type"
                );

        String sendTo =
                document.getString(
                        "sendTo"
                );

        if (title == null) {
            title = "Announcement";
        }

        if (message == null) {
            message = "";
        }

        if (type == null) {
            type = "General";
        }

        if (sendTo == null) {
            sendTo = "All Employees";
        }

        // =====================================================
        // CARD
        // =====================================================

        LinearLayout card =
                new LinearLayout(
                        this
                );

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                18,
                18,
                18,
                18
        );

        card.setBackgroundColor(
                0xFFFFFFFF
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                14
        );

        card.setLayoutParams(
                cardParams
        );

        // =====================================================
        // TYPE
        // =====================================================

        TextView typeText =
                new TextView(
                        this
                );

        typeText.setText(
                type.toUpperCase()
        );

        typeText.setTextSize(
                11
        );

        typeText.setTextColor(
                0xFF7651E8
        );

        typeText.setTypeface(
                null,
                Typeface.BOLD
        );

        // =====================================================
        // TITLE
        // =====================================================

        TextView titleText =
                new TextView(
                        this
                );

        titleText.setText(
                title
        );

        titleText.setTextSize(
                18
        );

        titleText.setTextColor(
                0xFF251E32
        );

        titleText.setTypeface(
                null,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(
                0,
                6,
                0,
                0
        );

        titleText.setLayoutParams(
                titleParams
        );

        // =====================================================
        // MESSAGE
        // =====================================================

        TextView messageText =
                new TextView(
                        this
                );

        messageText.setText(
                message
        );

        messageText.setTextSize(
                14
        );

        messageText.setTextColor(
                0xFF5F5869
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(
                0,
                8,
                0,
                0
        );

        messageText.setLayoutParams(
                messageParams
        );

        // =====================================================
        // SEND TO TEXT
        // =====================================================

        TextView sendToText =
                new TextView(
                        this
                );

        sendToText.setText(
                "Sent to: " + sendTo
        );

        sendToText.setTextSize(
                14
        );

        sendToText.setTextColor(
                0xFF064E3B
        );

        sendToText.setTypeface(
                null,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams sendToParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        sendToParams.setMargins(
                0,
                12,
                0,
                0
        );

        sendToText.setLayoutParams(
                sendToParams
        );

        // =====================================================
        // DELETE BUTTON
        // =====================================================

        Button deleteButton =
                new Button(
                        this
                );

        deleteButton.setText(
                "DELETE"
        );

        deleteButton.setTextSize(
                12
        );

        deleteButton.setTextColor(
                0xFFFFFFFF
        );

        deleteButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        0xFFD04444
                )
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        deleteParams.setMargins(
                0,
                10,
                0,
                0
        );

        deleteButton.setLayoutParams(
                deleteParams
        );

        String documentId =
                document.getId();

        deleteButton.setOnClickListener(
                v -> deleteAnnouncement(
                        documentId
                )
        );

        // =====================================================
        // ADD TO CARD
        // =====================================================

        card.addView(
                typeText
        );

        card.addView(
                titleText
        );

        card.addView(
                messageText
        );

        card.addView(
                sendToText
        );

        card.addView(
                deleteButton
        );

        // =====================================================
        // ADD CARD
        // =====================================================

        announcementsContainer
                .addView(
                        card
                );
    }

    // =========================================================
    // DELETE
    // =========================================================

    private void deleteAnnouncement(
            String documentId
    ) {

        firestore
                .collection("announcements")
                .document(documentId)
                .delete()
                .addOnSuccessListener(
                        unused -> {

                            Toast.makeText(
                                    AdminAnnouncementManagementActivity.this,
                                    "Announcement deleted.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadAnnouncements();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    AdminAnnouncementManagementActivity.this,
                                    "Unable to delete announcement.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }
}