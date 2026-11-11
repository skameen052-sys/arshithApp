
package com.example.arshithconnectattendance;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmployeeWorkAssignmentsActivity extends AppCompatActivity {

    private static final String TAG = "WORK_ASSIGNMENTS_DEBUG";

    // Replace this with the actual Firestore document ID.
    private static final String TEST_ASSIGNMENT_ID = "8hd2rCdrZrYqRdrR4jyA";

    // FIREBASE
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    // UI VIEWS
    private LinearLayout assignmentsContainer;

    private TextView tvEmpty;
    private TextView tvAllCaughtUp;

    private TextView tvAssignedCount;
    private TextView tvInProgressCount;
    private TextView tvCompletedCount;

    private TextView filterAll;
    private TextView filterPending;
    private TextView filterInProgress;
    private TextView filterCompleted;

    private TextView btnBack;

    // ASSIGNMENTS
    private final List<DocumentSnapshot> allAssignments =
            new ArrayList<>();

    private String currentFilter = "All";
    private boolean isLoadingAssignments = false;

    // THEME COLORS
    private static final int PRIMARY = Color.rgb(7, 63, 74);
    private static final int PRIMARY_LIGHT = Color.rgb(221, 241, 237);
    private static final int BACKGROUND = Color.rgb(245, 248, 248);
    private static final int CARD_WHITE = Color.WHITE;
    private static final int TEXT_COLOR = Color.rgb(24, 52, 66);
    private static final int MUTED_COLOR = Color.rgb(105, 124, 129);
    private static final int GREEN = Color.rgb(33, 102, 61);
    private static final int BLUE = Color.rgb(30, 94, 210);
    private static final int ORANGE = Color.rgb(133, 85, 22);
    private static final int RED = Color.rgb(190, 45, 60);
    private static final int LIGHT_GREEN = Color.rgb(225, 244, 231);
    private static final int LIGHT_BLUE = Color.rgb(225, 235, 255);
    private static final int LIGHT_ORANGE = Color.rgb(255, 240, 217);
    private static final int LIGHT_RED = Color.rgb(255, 225, 229);

    // ON CREATE
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_employee_work_assignments);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupBackButton();
        setupFilters();
        loadAssignments();
    }

    // INITIALIZE VIEWS
    private void initializeViews() {

        assignmentsContainer =
                findViewById(R.id.assignmentsContainer);

        tvEmpty = findViewById(R.id.tvEmpty);
        tvAllCaughtUp = findViewById(R.id.tvAllCaughtUp);

        tvAssignedCount = findViewById(R.id.tvAssignedCount);
        tvInProgressCount = findViewById(R.id.tvInProgressCount);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);

        filterAll = findViewById(R.id.filterAll);
        filterPending = findViewById(R.id.filterPending);
        filterInProgress = findViewById(R.id.filterInProgress);
        filterCompleted = findViewById(R.id.filterCompleted);

        btnBack = findViewById(R.id.btnBack);

        tvAssignedCount.setText("0");
        tvInProgressCount.setText("0");
        tvCompletedCount.setText("0");

        tvEmpty.setVisibility(View.GONE);
        tvAllCaughtUp.setVisibility(View.GONE);

        View root = findViewById(android.R.id.content);

        if (root != null) {
            root.setBackgroundColor(BACKGROUND);
        }
    }

    // BACK BUTTON
    private void setupBackButton() {

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }
    }

    // FILTER SETUP
    private void setupFilters() {

        filterAll.setOnClickListener(v -> changeFilter("All"));
        filterPending.setOnClickListener(v -> changeFilter("Pending"));
        filterInProgress.setOnClickListener(v -> changeFilter("In Progress"));
        filterCompleted.setOnClickListener(v -> changeFilter("Completed"));

        updateFilterAppearance();
    }

    private void changeFilter(String filter) {
        currentFilter = filter;
        updateFilterAppearance();
        displayAssignments();
    }

    // FILTER APPEARANCE
    private void updateFilterAppearance() {

        setFilterAppearance(filterAll, currentFilter.equals("All"));
        setFilterAppearance(filterPending, currentFilter.equals("Pending"));
        setFilterAppearance(filterInProgress, currentFilter.equals("In Progress"));
        setFilterAppearance(filterCompleted, currentFilter.equals("Completed"));
    }

    private void setFilterAppearance(TextView view, boolean selected) {

        if (view == null) return;

        view.setBackground(makeBackground(
                selected ? PRIMARY : CARD_WHITE,
                24,
                selected ? PRIMARY : Color.rgb(224, 233, 233),
                1
        ));

        view.setTextColor(selected ? Color.WHITE : PRIMARY);

        view.setTypeface(
                null,
                selected ? Typeface.BOLD : Typeface.NORMAL
        );

        view.setClickable(true);
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
    }

    // =========================================================
    // DIAGNOSTIC: READ ONE ASSIGNMENT DOCUMENT
    // =========================================================

    private void testSingleAssignmentRead(String uid) {

        if (TEST_ASSIGNMENT_ID.equals("YOUR_ASSIGNMENT_DOCUMENT_ID")) {

            Log.w(TAG,
                    "Please replace TEST_ASSIGNMENT_ID with the actual Firestore document ID.");

            return;
        }

        Log.d(TAG, "Starting single assignment document test.");
        Log.d(TAG, "Current Firebase UID: " + uid);
        Log.d(TAG, "Testing assignment document: " + TEST_ASSIGNMENT_ID);

        firestore.collection("workAssignments")
                .document(TEST_ASSIGNMENT_ID)
                .get()
                .addOnSuccessListener(document -> {

                    if (isFinishing() || isDestroyed()) return;

                    if (document.exists()) {

                        Log.d(TAG,
                                "SINGLE DOCUMENT READ SUCCESS");

                        Log.d(TAG,
                                "Document ID: " + document.getId());

                        Log.d(TAG,
                                "Title: " + document.getString("title"));

                        Log.d(TAG,
                                "Employee IDs: "
                                        + document.get("employeeIds"));

                        Log.d(TAG,
                                "Status: " + document.getString("status"));

                        Object employeeIds = document.get("employeeIds");

                        if (employeeIds instanceof List) {

                            List<?> ids = (List<?>) employeeIds;

                            Log.d(TAG,
                                    "Logged-in UID included: "
                                            + ids.contains(uid));
                        }

                    } else {

                        Log.w(TAG,
                                "SINGLE DOCUMENT READ SUCCESS, BUT DOCUMENT DOES NOT EXIST.");
                    }
                })
                .addOnFailureListener(error -> {

                    Log.e(TAG,
                            "SINGLE DOCUMENT READ FAILED", error);

                    Log.e(TAG,
                            "UID used: " + uid);

                    Log.e(TAG,
                            "Assignment ID tested: " + TEST_ASSIGNMENT_ID);
                });
    }

    // =========================================================
    // LOAD ASSIGNMENTS FROM FIRESTORE
    // =========================================================


    private void loadAssignments() {

        if (auth.getCurrentUser() == null) {
            Toast.makeText(
                    this,
                    "Please sign in to view your assignments.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (isLoadingAssignments) return;

        isLoadingAssignments = true;

        String uid = auth.getCurrentUser().getUid();

        Log.d(TAG, "================================");
        Log.d(TAG, "Loading employee assignments");
        Log.d(TAG, "Firebase UID: " + uid);
        Log.d(TAG, "Firebase project should be arshith-connect-attendance");
        Log.d(TAG, "Starting LIMIT 1 assignment query");
        Log.d(TAG, "================================");

        // Test reading one known assignment document.
        testSingleAssignmentRead(uid);

        assignmentsContainer.removeAllViews();
        tvEmpty.setVisibility(View.GONE);
        tvAllCaughtUp.setVisibility(View.GONE);

        // DIAGNOSTIC QUERY: only retrieve one matching assignment.
        firestore.collection("workAssignments")
                .whereArrayContains("employeeIds", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (isFinishing() || isDestroyed()) {
                        isLoadingAssignments = false;
                        return;
                    }

                    Log.d(TAG, "LIMIT 1 QUERY SUCCESS");
                    Log.d(TAG, "Documents found: " + snapshot.size());

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Log.d(TAG, "Document ID: " + doc.getId());
                        Log.d(TAG, "Title: " + doc.getString("title"));
                        Log.d(TAG, "Employee IDs: " + doc.get("employeeIds"));
                    }

                    allAssignments.clear();
                    allAssignments.addAll(snapshot.getDocuments());

                    Collections.sort(
                            allAssignments,
                            (first, second) -> {

                                Timestamp firstTime =
                                        first.getTimestamp("assignedAt");

                                Timestamp secondTime =
                                        second.getTimestamp("assignedAt");

                                if (firstTime == null && secondTime == null) {
                                    return 0;
                                }

                                if (firstTime == null) return 1;
                                if (secondTime == null) return -1;

                                return secondTime.compareTo(firstTime);
                            }
                    );

                    isLoadingAssignments = false;

                    updateSummaryCounts();
                    displayAssignments();
                })
                .addOnFailureListener(error -> {

                    isLoadingAssignments = false;

                    Log.e(TAG, "LIMIT 1 QUERY FAILED", error);
                    Log.e(TAG, "Firebase UID used: " + uid);
                    Log.e(TAG, "Test assignment ID: " + TEST_ASSIGNMENT_ID);

                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Unable to load assignments: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    tvEmpty.setText(
                            "Unable to load assignments. Please try again."
                    );

                    tvEmpty.setVisibility(View.VISIBLE);
                    tvAllCaughtUp.setVisibility(View.GONE);
                });
    }


    // UPDATE SUMMARY COUNTS
    private void updateSummaryCounts() {

        int assigned = 0;
        int inProgress = 0;
        int completed = 0;

        for (DocumentSnapshot document : allAssignments) {

            String status = getStatus(document);

            if (status.equals("Completed")) {
                completed++;
            } else if (status.equals("In Progress")) {
                inProgress++;
            } else {
                assigned++;
            }
        }

        tvAssignedCount.setText(String.valueOf(assigned));
        tvInProgressCount.setText(String.valueOf(inProgress));
        tvCompletedCount.setText(String.valueOf(completed));
    }

    // DISPLAY FILTERED ASSIGNMENTS
    private void displayAssignments() {

        assignmentsContainer.removeAllViews();

        List<DocumentSnapshot> filtered = new ArrayList<>();

        for (DocumentSnapshot document : allAssignments) {

            String status = getStatus(document);
            boolean include;

            switch (currentFilter) {

                case "Pending":
                    include = !status.equals("Completed")
                            && !status.equals("In Progress");
                    break;

                case "In Progress":
                    include = status.equals("In Progress");
                    break;

                case "Completed":
                    include = status.equals("Completed");
                    break;

                default:
                    include = true;
                    break;
            }

            if (include) filtered.add(document);
        }

        if (filtered.isEmpty()) {

            tvEmpty.setVisibility(View.VISIBLE);
            tvAllCaughtUp.setVisibility(View.GONE);

            if (currentFilter.equals("Completed")) {
                tvEmpty.setText("No completed assignments yet");
            } else if (currentFilter.equals("Pending")) {
                tvEmpty.setText("No pending assignments");
            } else if (currentFilter.equals("In Progress")) {
                tvEmpty.setText("No assignments in progress");
            } else {
                tvEmpty.setText("No work assignments yet");
            }

            return;
        }

        tvEmpty.setVisibility(View.GONE);

        boolean hasActiveAssignments = false;

        for (DocumentSnapshot document : filtered) {

            addAssignmentCard(document);

            if (!getStatus(document).equals("Completed")) {
                hasActiveAssignments = true;
            }
        }

        tvAllCaughtUp.setVisibility(
                currentFilter.equals("All") && !hasActiveAssignments
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    // CREATE ASSIGNMENT CARD
    private void addAssignmentCard(DocumentSnapshot document) {

        String title = document.getString("title");
        String description = document.getString("description");
        String priority = document.getString("priority");
        String dueDate = document.getString("dueDate");
        String type = document.getString("assignmentType");

        String status = getStatus(document);

        if (title == null || title.trim().isEmpty()) {
            title = "Untitled Work";
        }

        if (description == null || description.trim().isEmpty()) {
            description = "No description provided.";
        }

        if (priority == null || priority.trim().isEmpty()) {
            priority = "Medium";
        }

        if (dueDate == null || dueDate.trim().isEmpty()) {
            dueDate = "Not set";
        }

        String assignmentType =
                "TEAM".equalsIgnoreCase(type)
                        ? "Team assignment"
                        : "Individual assignment";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        card.setBackground(makeBackground(
                CARD_WHITE,
                20,
                Color.rgb(225, 235, 234),
                1
        ));

        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(cardParams);

        // TITLE AND PRIORITY
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(TEXT_COLOR);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setMaxLines(3);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        tvTitle.setLayoutParams(titleParams);

        TextView tvPriority = createBadge(
                priority,
                getPriorityBackground(priority),
                getPriorityColor(priority)
        );

        LinearLayout.LayoutParams priorityParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        priorityParams.setMargins(dp(8), 0, 0, 0);
        tvPriority.setLayoutParams(priorityParams);

        titleRow.addView(tvTitle);
        titleRow.addView(tvPriority);
        card.addView(titleRow);

        // ASSIGNMENT TYPE
        TextView tvType = createInfo("◉  " + assignmentType);
        tvType.setTextColor(MUTED_COLOR);
        tvType.setPadding(0, dp(12), 0, dp(5));
        card.addView(tvType);

        // DUE DATE
        TextView tvDue = createInfo("Due date: " + dueDate);
        tvDue.setTextColor(TEXT_COLOR);
        tvDue.setTypeface(null, Typeface.BOLD);
        tvDue.setPadding(0, dp(5), 0, dp(10));
        card.addView(tvDue);

        // DESCRIPTION
        TextView tvDescription = createInfo(description);
        tvDescription.setTextColor(MUTED_COLOR);
        tvDescription.setPadding(0, 0, 0, dp(14));
        card.addView(tvDescription);

        // STATUS BADGE
        TextView tvStatus = createBadge(
                getDisplayStatus(status),
                getStatusBackground(status),
                getStatusColor(status)
        );

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.END);
        statusRow.addView(tvStatus);
        card.addView(statusRow);

        // START WORK BUTTON
        if (status.equals("Assigned") || status.equals("Pending")) {

            Button btnStartWork = createActionButton(
                    "START WORK",
                    PRIMARY
            );

            btnStartWork.setOnClickListener(v ->
                    startWork(document.getId(), btnStartWork)
            );

            addActionButton(card, btnStartWork);

        } else if (status.equals("In Progress")) {

            Button btnComplete = createActionButton(
                    "MARK COMPLETED",
                    GREEN
            );

            btnComplete.setOnClickListener(v ->
                    confirmCompleteWork(document.getId(), btnComplete)
            );

            addActionButton(card, btnComplete);
        }

        assignmentsContainer.addView(card);
    }

    // START WORK
    private void startWork(String assignmentId, Button button) {

        if (auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        button.setEnabled(false);

        firestore.collection("workAssignments")
                .document(assignmentId)
                .update(
                        "status", "In Progress",
                        "startedAt", Timestamp.now()
                )
                .addOnSuccessListener(unused -> {

                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Work started",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadAssignments();
                })
                .addOnFailureListener(error -> {

                    if (isFinishing() || isDestroyed()) return;

                    button.setEnabled(true);

                    Log.e(TAG, "START WORK FAILED", error);

                    Toast.makeText(
                            this,
                            "Unable to start work: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // CONFIRM COMPLETION
    private void confirmCompleteWork(
            String assignmentId,
            Button button
    ) {

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Complete assignment?")
                .setMessage(
                        "Are you sure you want to mark this assignment as completed?"
                )
                .setPositiveButton(
                        "Confirm",
                        (dialog, which) ->
                                completeWork(assignmentId, button)
                )
                .setNegativeButton(
                        "Cancel",
                        (dialog, which) -> dialog.dismiss()
                )
                .show();
    }

    // COMPLETE WORK
    private void completeWork(String assignmentId, Button button) {

        if (auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        button.setEnabled(false);

        firestore.collection("workAssignments")
                .document(assignmentId)
                .update(
                        "status", "Completed",
                        "completedAt", Timestamp.now()
                )
                .addOnSuccessListener(unused -> {

                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Work completed",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadAssignments();
                })
                .addOnFailureListener(error -> {

                    if (isFinishing() || isDestroyed()) return;

                    button.setEnabled(true);

                    Log.e(TAG, "COMPLETE WORK FAILED", error);

                    Toast.makeText(
                            this,
                            "Unable to complete work: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // READ ASSIGNMENT STATUS
    private String getStatus(DocumentSnapshot document) {

        String status = document.getString("status");

        if (status == null || status.trim().isEmpty()) {
            return "Assigned";
        }

        if (status.equalsIgnoreCase("Pending")) return "Pending";
        if (status.equalsIgnoreCase("Assigned")) return "Assigned";
        if (status.equalsIgnoreCase("In Progress")) return "In Progress";
        if (status.equalsIgnoreCase("Completed")) return "Completed";

        return status;
    }

    private String getDisplayStatus(String status) {

        if (status.equalsIgnoreCase("Assigned")) {
            return "Pending";
        }

        return status;
    }

    // PRIORITY COLORS
    private int getPriorityBackground(String priority) {

        if (priority.equalsIgnoreCase("High")) return LIGHT_RED;
        if (priority.equalsIgnoreCase("Low")) return LIGHT_GREEN;

        return LIGHT_ORANGE;
    }

    private int getPriorityColor(String priority) {

        if (priority.equalsIgnoreCase("High")) return RED;
        if (priority.equalsIgnoreCase("Low")) return GREEN;

        return ORANGE;
    }

    // STATUS COLORS
    private int getStatusBackground(String status) {

        if (status.equalsIgnoreCase("Completed")) return LIGHT_GREEN;
        if (status.equalsIgnoreCase("In Progress")) return LIGHT_BLUE;

        return PRIMARY_LIGHT;
    }

    private int getStatusColor(String status) {

        if (status.equalsIgnoreCase("Completed")) return GREEN;
        if (status.equalsIgnoreCase("In Progress")) return BLUE;

        return PRIMARY;
    }

    // CREATE BADGE
    private TextView createBadge(
            String text,
            int backgroundColor,
            int textColor
    ) {

        TextView badge = new TextView(this);

        badge.setText(text);
        badge.setTextSize(12);
        badge.setTextColor(textColor);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));

        badge.setBackground(makeBackground(
                backgroundColor,
                30,
                Color.TRANSPARENT,
                0
        ));

        return badge;
    }

    // CREATE INFORMATION TEXT
    private TextView createInfo(String text) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(MUTED_COLOR);
        view.setLineSpacing(dp(3), 1f);

        return view;
    }

    // CREATE ACTION BUTTON
    private Button createActionButton(String text, int color) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(color));

        return button;
    }

    private void addActionButton(LinearLayout card, Button button) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        params.setMargins(0, dp(14), 0, 0);

        button.setLayoutParams(params);
        card.addView(button);
    }

    // CREATE ROUNDED BACKGROUND
    private GradientDrawable makeBackground(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeWidthDp
    ) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));

        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }

        return drawable;
    }

    // DP CONVERTER
    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ON RESUME
    @Override
    protected void onResume() {

        super.onResume();

        if (assignmentsContainer != null
                && auth != null
                && auth.getCurrentUser() != null) {

            loadAssignments();
        }
    }
}
