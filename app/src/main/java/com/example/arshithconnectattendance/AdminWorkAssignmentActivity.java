package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Locale;

public class AdminWorkAssignmentActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private Button btnIndividual;
    private Button btnTeam;

    private TextView tvSelectionTitle;
    private TextView tvSelectedTarget;

    private LinearLayout selectionContainer;
    private LinearLayout formContainer;
    private LinearLayout assignmentsContainer;

    private EditText etTitle;
    private EditText etDescription;
    private EditText etDueDate;

    private Spinner spinnerPriority;

    private Button btnSaveAssignment;

    private String selectedMode = "";

    private String selectedEmployeeId = "";
    private String selectedEmployeeName = "";

    private String selectedTeamId = "";
    private String selectedTeamName = "";

    private ArrayList<String> selectedTeamMemberIds =
            new ArrayList<>();

    // Prevents the same assignment from being created twice
    private boolean isSavingAssignment = false;

    // =========================================================
    // REAL-TIME ASSIGNMENT LISTENER
    // =========================================================

    private ListenerRegistration assignmentsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_admin_work_assignment);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnIndividual = findViewById(R.id.btnIndividual);
        btnTeam = findViewById(R.id.btnTeam);

        tvSelectionTitle = findViewById(R.id.tvSelectionTitle);
        tvSelectedTarget = findViewById(R.id.tvSelectedTarget);

        selectionContainer = findViewById(R.id.selectionContainer);
        formContainer = findViewById(R.id.formContainer);
        assignmentsContainer = findViewById(R.id.assignmentsContainer);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etDueDate = findViewById(R.id.etDueDate);

        // Completion date is selected using the calendar, not typed manually.
        etDueDate.setHint("Completion date (YYYY-MM-DD)");
        etDueDate.setFocusable(false);
        etDueDate.setFocusableInTouchMode(false);
        etDueDate.setClickable(true);
        etDueDate.setLongClickable(false);
        etDueDate.setInputType(0);

        etDueDate.setOnClickListener(v -> showCompletionDatePicker());

        spinnerPriority = findViewById(R.id.spinnerPriority);

        btnSaveAssignment = findViewById(R.id.btnSaveAssignment);

        setupPrioritySpinner();

        btnIndividual.setOnClickListener(v -> {

            if (!isSavingAssignment) {
                loadIndividualSelection();
            }

        });

        btnTeam.setOnClickListener(v -> {

            if (!isSavingAssignment) {
                loadTeamSelection();
            }

        });

        btnSaveAssignment.setOnClickListener(v -> {

            // IMPORTANT:
            // Prevent multiple clicks from creating duplicate documents.
            if (isSavingAssignment) {
                return;
            }

            saveAssignment();

        });

        handleIncomingSelection();

        // =====================================================
        // START REAL-TIME ASSIGNMENT LISTENER
        // =====================================================

        loadAssignments();
    }

    // =========================================================
    // COMPLETION DATE PICKER
    // =========================================================

    private void showCompletionDatePicker() {
        Calendar calendar = Calendar.getInstance();

        // If a date was already selected, reopen the picker on that date.
        String selectedDate = etDueDate.getText().toString().trim();

        if (!selectedDate.isEmpty()) {
            try {
                String[] parts = selectedDate.split("-");
                if (parts.length == 3) {
                    calendar.set(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2])
                    );
                }
            } catch (NumberFormatException ignored) {
                // Keep today's date if the existing value cannot be parsed.
            }
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                AdminWorkAssignmentActivity.this,
                (view, year, month, dayOfMonth) -> {
                    String formattedDate = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            dayOfMonth
                    );
                    etDueDate.setText(formattedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    // =========================================================
    // PRIORITY SPINNER
    // =========================================================

    private void setupPrioritySpinner() {

        String[] priorities = {
                "Low",
                "Medium",
                "High"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        priorities
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerPriority.setAdapter(adapter);
    }

    // =========================================================
    // HANDLE INCOMING SELECTION
    // =========================================================

    private void handleIncomingSelection() {

        String mode =
                getIntent().getStringExtra("mode");

        if (mode == null) {
            return;
        }

        if (mode.equals("INDIVIDUAL")) {

            selectedMode = "INDIVIDUAL";

            selectedEmployeeId =
                    getIntent().getStringExtra(
                            "employeeId"
                    );

            selectedEmployeeName =
                    getIntent().getStringExtra(
                            "employeeName"
                    );

            if (selectedEmployeeId != null) {

                if (selectedEmployeeName == null) {
                    selectedEmployeeName = "Employee";
                }

                btnIndividual.setEnabled(false);
                btnTeam.setEnabled(true);

                tvSelectionTitle.setText(
                        "Individual Assignment"
                );

                tvSelectedTarget.setText(
                        "Employee: "
                                + selectedEmployeeName
                );

                selectionContainer.setVisibility(
                        View.GONE
                );

                formContainer.setVisibility(
                        View.VISIBLE
                );
            }

        } else if (mode.equals("TEAM")) {

            selectedMode = "TEAM";

            selectedTeamId =
                    getIntent().getStringExtra(
                            "teamId"
                    );

            selectedTeamName =
                    getIntent().getStringExtra(
                            "teamName"
                    );

            ArrayList<String> members =
                    getIntent().getStringArrayListExtra(
                            "teamMemberIds"
                    );

            if (members != null) {
                selectedTeamMemberIds =
                        new ArrayList<>(members);
            }

            if (selectedTeamName == null) {
                selectedTeamName = "Team";
            }

            btnTeam.setEnabled(false);
            btnIndividual.setEnabled(true);

            tvSelectionTitle.setText(
                    "Team Assignment"
            );

            tvSelectedTarget.setText(
                    "Team: "
                            + selectedTeamName
            );

            selectionContainer.setVisibility(
                    View.GONE
            );

            formContainer.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // =========================================================
    // LOAD INDIVIDUAL EMPLOYEES
    // =========================================================

    private void loadIndividualSelection() {

        selectedMode = "INDIVIDUAL";

        selectedEmployeeId = "";
        selectedEmployeeName = "";

        btnIndividual.setEnabled(false);
        btnTeam.setEnabled(true);

        tvSelectionTitle.setText(
                "Select Employee"
        );

        tvSelectedTarget.setText(
                "No employee selected"
        );

        formContainer.setVisibility(
                View.GONE
        );

        selectionContainer.setVisibility(
                View.VISIBLE
        );

        selectionContainer.removeAllViews();

        firestore.collection("employees")
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<DocumentSnapshot> employees =
                            new ArrayList<>(
                                    snapshot.getDocuments()
                            );

                    Collections.sort(
                            employees,
                            (a, b) -> {

                                String nameA =
                                        a.getString("name");

                                String nameB =
                                        b.getString("name");

                                if (nameA == null) {
                                    nameA = "";
                                }

                                if (nameB == null) {
                                    nameB = "";
                                }

                                return nameA.compareToIgnoreCase(
                                        nameB
                                );
                            }
                    );

                    for (DocumentSnapshot document :
                            employees) {

                        addEmployeeSelectionCard(
                                document
                        );
                    }

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Failed to load employees: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // =========================================================
    // EMPLOYEE SELECTION CARD
    // =========================================================

    private void addEmployeeSelectionCard(
            DocumentSnapshot document
    ) {

        String uid =
                document.getId();

        String name =
                document.getString("name");

        String email =
                document.getString("email");

        if (name == null) {
            name = "Unnamed Employee";
        }

        if (email == null) {
            email = "";
        }

        LinearLayout card =
                createSelectionCard();

        TextView text =
                createCardTitle(
                        name
                                + "\n"
                                + email
                );

        Button button =
                new Button(this);

        button.setText(
                "Select Employee"
        );

        button.setAllCaps(false);

        String finalName = name;

        button.setOnClickListener(v -> {

            selectedEmployeeId = uid;
            selectedEmployeeName = finalName;

            tvSelectedTarget.setText(
                    "Employee: "
                            + selectedEmployeeName
            );

            selectionContainer.setVisibility(
                    View.GONE
            );

            formContainer.setVisibility(
                    View.VISIBLE
            );
        });

        card.addView(text);
        card.addView(button);

        selectionContainer.addView(card);
    }

    // =========================================================
    // LOAD TEAMS
    // =========================================================

    private void loadTeamSelection() {

        selectedMode = "TEAM";

        selectedTeamId = "";
        selectedTeamName = "";
        selectedTeamMemberIds.clear();

        btnIndividual.setEnabled(true);
        btnTeam.setEnabled(false);

        tvSelectionTitle.setText(
                "Select Team"
        );

        tvSelectedTarget.setText(
                "No team selected"
        );

        formContainer.setVisibility(
                View.GONE
        );

        selectionContainer.setVisibility(
                View.VISIBLE
        );

        selectionContainer.removeAllViews();

        firestore.collection("teams")
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (DocumentSnapshot document :
                            snapshot.getDocuments()) {

                        addTeamSelectionCard(
                                document
                        );
                    }

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Failed to load teams: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // =========================================================
    // TEAM SELECTION CARD
    // =========================================================

    private void addTeamSelectionCard(
            DocumentSnapshot document
    ) {

        String teamId =
                document.getId();

        String teamName =
                document.getString("name");

        if (teamName == null) {
            teamName = "Unnamed Team";
        }

        List<String> memberIds =
                (List<String>)
                        document.get("memberIds");

        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }

        LinearLayout card =
                createSelectionCard();

        TextView text =
                createCardTitle(
                        teamName
                                + "\nMembers: "
                                + memberIds.size()
                );

        Button button =
                new Button(this);

        button.setText(
                "Select Team"
        );

        button.setAllCaps(false);

        String finalTeamName =
                teamName;

        ArrayList<String> members =
                new ArrayList<>(memberIds);

        button.setOnClickListener(v -> {

            selectedTeamId = teamId;

            selectedTeamName =
                    finalTeamName;

            selectedTeamMemberIds =
                    new ArrayList<>(members);

            tvSelectedTarget.setText(
                    "Team: "
                            + selectedTeamName
            );

            selectionContainer.setVisibility(
                    View.GONE
            );

            formContainer.setVisibility(
                    View.VISIBLE
            );
        });

        card.addView(text);
        card.addView(button);

        selectionContainer.addView(card);
    }

    // =========================================================
    // SAVE ASSIGNMENT
    // =========================================================

    private void saveAssignment() {

        if (isSavingAssignment) {
            return;
        }

        if (auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "You are not logged in",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String title =
                etTitle.getText()
                        .toString()
                        .trim();

        String description =
                etDescription.getText()
                        .toString()
                        .trim();

        String dueDate =
                etDueDate.getText()
                        .toString()
                        .trim();

        String priority =
                spinnerPriority
                        .getSelectedItem()
                        .toString();

        // -----------------------------------------------------
        // VALIDATION
        // -----------------------------------------------------

        if (selectedMode.isEmpty()) {

            Toast.makeText(
                    this,
                    "Select Individual or Team",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (title.isEmpty()) {

            etTitle.setError(
                    "Enter work title"
            );

            etTitle.requestFocus();

            return;
        }

        if (selectedMode.equals("INDIVIDUAL")
                && selectedEmployeeId.isEmpty()) {

            Toast.makeText(
                    this,
                    "Select an employee",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (selectedMode.equals("TEAM")
                && (
                selectedTeamId.isEmpty()
                        || selectedTeamMemberIds.isEmpty()
        )) {

            Toast.makeText(
                    this,
                    "Select a team",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -----------------------------------------------------
        // LOCK SAVE BUTTON IMMEDIATELY
        // -----------------------------------------------------

        isSavingAssignment = true;

        btnSaveAssignment.setEnabled(false);

        btnIndividual.setEnabled(false);
        btnTeam.setEnabled(false);

        // -----------------------------------------------------
        // CREATE EMPLOYEE IDS
        // -----------------------------------------------------

        ArrayList<String> employeeIds =
                new ArrayList<>();

        Map<String, Object> assignment =
                new HashMap<>();

        assignment.put(
                "title",
                title
        );

        assignment.put(
                "description",
                description
        );

        assignment.put(
                "priority",
                priority
        );

        // New field name for the completion date.
        assignment.put(
                "completionDate",
                dueDate
        );

        // Keep the legacy field so existing screens that still read dueDate
        // continue to work until they are updated to completionDate.
        assignment.put(
                "dueDate",
                dueDate
        );

        assignment.put(
                "status",
                "Assigned"
        );

        assignment.put(
                "assignedAt",
                Timestamp.now()
        );

        assignment.put(
                "assignedBy",
                auth.getCurrentUser().getUid()
        );

        // -----------------------------------------------------
        // INDIVIDUAL
        // -----------------------------------------------------

        if (selectedMode.equals("INDIVIDUAL")) {

            employeeIds.add(
                    selectedEmployeeId
            );

            assignment.put(
                    "assignmentType",
                    "INDIVIDUAL"
            );

            assignment.put(
                    "employeeId",
                    selectedEmployeeId
            );

            assignment.put(
                    "employeeName",
                    selectedEmployeeName
            );
        }

        // -----------------------------------------------------
        // TEAM
        // -----------------------------------------------------

        else {

            employeeIds.addAll(
                    selectedTeamMemberIds
            );

            assignment.put(
                    "assignmentType",
                    "TEAM"
            );

            assignment.put(
                    "teamId",
                    selectedTeamId
            );

            assignment.put(
                    "teamName",
                    selectedTeamName
            );
        }

        assignment.put(
                "employeeIds",
                employeeIds
        );

        // -----------------------------------------------------
        // SAVE ONLY ONCE
        // -----------------------------------------------------

        firestore.collection(
                        "workAssignments"
                )
                .add(assignment)
                .addOnSuccessListener(
                        documentReference -> {

                            Toast.makeText(
                                    this,
                                    "Work assigned successfully",
                                    Toast.LENGTH_SHORT
                            ).show();

                            // Clear form
                            clearAssignmentForm();

                            // Unlock buttons
                            isSavingAssignment = false;

                            btnSaveAssignment
                                    .setEnabled(true);

                            btnIndividual
                                    .setEnabled(true);

                            btnTeam
                                    .setEnabled(true);

                            /*
                             * No need to manually call
                             * loadAssignments() here.
                             *
                             * The real-time listener will
                             * automatically receive the new
                             * assignment.
                             */
                        }
                )
                .addOnFailureListener(e -> {

                    isSavingAssignment = false;

                    btnSaveAssignment
                            .setEnabled(true);

                    btnIndividual
                            .setEnabled(true);

                    btnTeam
                            .setEnabled(true);

                    Toast.makeText(
                            this,
                            "Failed to assign work: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // CLEAR FORM AFTER SUCCESS
    // =========================================================

    private void clearAssignmentForm() {

        etTitle.setText("");
        etDescription.setText("");
        etDueDate.setText("");

        spinnerPriority.setSelection(0);

        selectedMode = "";

        selectedEmployeeId = "";
        selectedEmployeeName = "";

        selectedTeamId = "";
        selectedTeamName = "";

        selectedTeamMemberIds.clear();

        tvSelectionTitle.setText(
                "Select Individual or Team"
        );

        tvSelectedTarget.setText(
                "No selection"
        );

        formContainer.setVisibility(
                View.GONE
        );

        selectionContainer.setVisibility(
                View.GONE
        );
    }

    // =========================================================
    // LOAD ASSIGNMENTS - REAL TIME
    // =========================================================

    private void loadAssignments() {

        // Remove previous listener if one exists
        if (assignmentsListener != null) {
            assignmentsListener.remove();
            assignmentsListener = null;
        }

        // Clear current cards
        assignmentsContainer.removeAllViews();

        /*
         * REAL-TIME LISTENER
         *
         * Whenever an employee changes an assignment,
         * this listener receives the updated Firestore data.
         *
         * Therefore, when status changes to Completed,
         * the card disappears automatically.
         */

        assignmentsListener =
                firestore.collection(
                                "workAssignments"
                        )
                        .addSnapshotListener(
                                (snapshot, error) -> {

                                    if (error != null) {

                                        Toast.makeText(
                                                this,
                                                "Failed to load assignments: "
                                                        + error.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show();

                                        return;
                                    }

                                    if (snapshot == null) {
                                        return;
                                    }

                                    // Clear old cards
                                    assignmentsContainer
                                            .removeAllViews();

                                    List<DocumentSnapshot> assignments =
                                            new ArrayList<>(
                                                    snapshot.getDocuments()
                                            );

                                    // -------------------------------------------------
                                    // SORT NEWEST FIRST
                                    // -------------------------------------------------

                                    Collections.sort(
                                            assignments,
                                            (a, b) -> {

                                                Timestamp ta =
                                                        a.getTimestamp(
                                                                "assignedAt"
                                                        );

                                                Timestamp tb =
                                                        b.getTimestamp(
                                                                "assignedAt"
                                                        );

                                                if (ta == null
                                                        && tb == null) {
                                                    return 0;
                                                }

                                                if (ta == null) {
                                                    return 1;
                                                }

                                                if (tb == null) {
                                                    return -1;
                                                }

                                                return tb.compareTo(ta);
                                            }
                                    );

                                    int activeAssignmentCount = 0;

                                    // -------------------------------------------------
                                    // DISPLAY ONLY ACTIVE ASSIGNMENTS
                                    // -------------------------------------------------

                                    for (DocumentSnapshot document :
                                            assignments) {

                                        String status =
                                                document.getString(
                                                        "status"
                                                );

                                        /*
                                         * IMPORTANT:
                                         *
                                         * Completed assignments are NOT
                                         * displayed in Admin Work Assignments.
                                         *
                                         * We do NOT delete the Firestore document.
                                         */

                                        if (status != null
                                                && status.trim()
                                                .equalsIgnoreCase(
                                                        "Completed"
                                                )) {

                                            continue;
                                        }

                                        addAssignmentCard(
                                                document
                                        );

                                        activeAssignmentCount++;
                                    }

                                    // -------------------------------------------------
                                    // NO ACTIVE ASSIGNMENTS
                                    // -------------------------------------------------

                                    if (activeAssignmentCount == 0) {

                                        TextView emptyText =
                                                new TextView(
                                                        this
                                                );

                                        emptyText.setText(
                                                "No active work assignments."
                                        );

                                        emptyText.setTextSize(
                                                15
                                        );

                                        emptyText.setTextColor(
                                                Color.DKGRAY
                                        );

                                        emptyText.setPadding(
                                                dp(18),
                                                dp(20),
                                                dp(18),
                                                dp(20)
                                        );

                                        assignmentsContainer
                                                .addView(
                                                        emptyText
                                                );
                                    }
                                }
                        );
    }

    // =========================================================
    // ASSIGNMENT CARD
    // =========================================================

    private void addAssignmentCard(
            DocumentSnapshot document
    ) {

        String title =
                document.getString("title");

        String description =
                document.getString("description");

        String priority =
                document.getString("priority");

        String dueDate =
                document.getString("completionDate");

        // Backward compatibility for assignments created before this change.
        if (dueDate == null) {
            dueDate = document.getString("dueDate");
        }

        String status =
                document.getString("status");

        String type =
                document.getString(
                        "assignmentType"
                );

        if (title == null) {
            title = "Untitled Work";
        }

        if (description == null) {
            description = "";
        }

        if (priority == null) {
            priority = "Medium";
        }

        if (dueDate == null) {
            dueDate = "";
        }

        if (status == null) {
            status = "Assigned";
        }

        String target;

        if ("TEAM".equals(type)) {

            String teamName =
                    document.getString(
                            "teamName"
                    );

            if (teamName == null) {
                teamName = "Unknown Team";
            }

            target =
                    "Team: "
                            + teamName;

        } else {

            String employeeName =
                    document.getString(
                            "employeeName"
                    );

            if (employeeName == null) {
                employeeName = "Unknown Employee";
            }

            target =
                    "Employee: "
                            + employeeName;
        }

        // -----------------------------------------------------
        // CARD
        // -----------------------------------------------------

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );

        card.setBackgroundColor(
                Color.WHITE
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        card.setLayoutParams(params);

        // -----------------------------------------------------
        // TITLE
        // -----------------------------------------------------

        TextView tvTitle =
                createCardTitle(
                        title
                );

        // -----------------------------------------------------
        // TARGET
        // -----------------------------------------------------

        TextView tvTarget =
                createInfo(
                        target
                );

        // -----------------------------------------------------
        // DESCRIPTION
        // -----------------------------------------------------

        TextView tvDescription =
                createInfo(
                        "Description: "
                                + description
                );

        // -----------------------------------------------------
        // PRIORITY
        // -----------------------------------------------------

        TextView tvPriority =
                createInfo(
                        "Priority: "
                                + priority
                );

        // -----------------------------------------------------
        // COMPLETION DATE
        // -----------------------------------------------------

        TextView tvDue =
                createInfo(
                        "Completion Date: "
                                + (
                                dueDate.isEmpty()
                                        ? "Not set"
                                        : dueDate
                        )
                );

        // -----------------------------------------------------
        // STATUS
        // -----------------------------------------------------

        TextView tvStatus =
                createInfo(
                        "Status: "
                                + status
                );

        card.addView(tvTitle);
        card.addView(tvTarget);
        card.addView(tvDescription);
        card.addView(tvPriority);
        card.addView(tvDue);
        card.addView(tvStatus);

        assignmentsContainer.addView(card);
    }

    // =========================================================
    // SELECTION CARD
    // =========================================================

    private LinearLayout createSelectionCard() {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
        );

        card.setBackgroundColor(
                Color.WHITE
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );

        card.setLayoutParams(params);

        return card;
    }

    // =========================================================
    // CARD TITLE
    // =========================================================

    private TextView createCardTitle(
            String text
    ) {

        TextView tv =
                new TextView(this);

        tv.setText(text);

        tv.setTextSize(18);

        tv.setTextColor(
                Color.rgb(
                        30,
                        20,
                        60
                )
        );

        tv.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        tv.setPadding(
                0,
                0,
                0,
                dp(8)
        );

        return tv;
    }

    // =========================================================
    // INFO TEXT
    // =========================================================

    private TextView createInfo(
            String text
    ) {

        TextView tv =
                new TextView(this);

        tv.setText(text);

        tv.setTextSize(14);

        tv.setTextColor(
                Color.DKGRAY
        );

        tv.setPadding(
                0,
                dp(4),
                0,
                dp(4)
        );

        return tv;
    }

    // =========================================================
    // DP
    // =========================================================

    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // =========================================================
    // CLEAN UP REAL-TIME LISTENER
    // =========================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (assignmentsListener != null) {
            assignmentsListener.remove();
            assignmentsListener = null;
        }
    }
}