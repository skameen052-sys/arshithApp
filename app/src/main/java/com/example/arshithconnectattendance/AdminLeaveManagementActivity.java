package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class AdminLeaveManagementActivity
        extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private TextView tvPendingCount;
    private TextView tvApprovedCount;
    private TextView tvNoRequests;

    private Button btnBack;
    private Button btnRefresh;

    private LinearLayout requestsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_leave_management
        );

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        initializeViews();
        setupButtons();

        verifyAdminAndLoadRequests();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        tvPendingCount =
                findViewById(R.id.tvPendingCount);

        tvApprovedCount =
                findViewById(R.id.tvApprovedCount);

        tvNoRequests =
                findViewById(R.id.tvNoRequests);

        btnBack =
                findViewById(R.id.btnBack);

        btnRefresh =
                findViewById(R.id.btnRefresh);

        requestsContainer =
                findViewById(R.id.requestsContainer);
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                v -> finish()
        );

        btnRefresh.setOnClickListener(
                v -> loadRequests()
        );
    }

    // =========================================================
    // VERIFY ADMIN
    // =========================================================

    private void verifyAdminAndLoadRequests() {

        firestore.collection("admins")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {

                        firebaseAuth.signOut();

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        goToAdminLogin();

                        return;
                    }

                    String role =
                            documentSnapshot.getString("role");

                    if (!"admin".equalsIgnoreCase(role)) {

                        firebaseAuth.signOut();

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        goToAdminLogin();

                        return;
                    }

                    loadRequests();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to verify admin.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // LOAD LEAVE REQUESTS
    // =========================================================

    private void loadRequests() {

        requestsContainer.removeAllViews();

        tvNoRequests.setVisibility(
                View.GONE
        );

        tvPendingCount.setText("0");
        tvApprovedCount.setText("0");

        firestore.collectionGroup("requests")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    List<DocumentSnapshot> documents =
                            new ArrayList<>();

                    int pending = 0;
                    int approved = 0;

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        documents.add(document);

                        String status =
                                document.getString("status");

                        if ("Pending".equalsIgnoreCase(status)) {
                            pending++;
                        }

                        if ("Approved".equalsIgnoreCase(status)) {
                            approved++;
                        }
                    }

                    tvPendingCount.setText(
                            String.valueOf(pending)
                    );

                    tvApprovedCount.setText(
                            String.valueOf(approved)
                    );

                    // Sort newest requests first.
                    // Read appliedAt safely because older Firestore documents
                    // may store it as a Timestamp, Date, number, or string.
                    Collections.sort(documents, (first, second) -> {
                        long firstTime = getAppliedAtMillis(first);
                        long secondTime = getAppliedAtMillis(second);

                        return Long.compare(secondTime, firstTime);
                    });

                    // Add every leave request
                    for (DocumentSnapshot document :
                            documents) {

                        addLeaveCard(document);
                    }

                    if (documents.isEmpty()) {

                        tvNoRequests.setVisibility(
                                View.VISIBLE
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    tvNoRequests.setVisibility(
                            View.VISIBLE
                    );

                    tvNoRequests.setText(
                            "Unable to load leave requests."
                    );

                    Toast.makeText(
                            this,
                            "Leave loading failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // SAFE APPLIED-AT DATE READER
    // =========================================================

    /**
     * Returns appliedAt as epoch milliseconds without calling
     * DocumentSnapshot.getTimestamp() on a value of the wrong type.
     * Missing or unrecognized values are treated as oldest (0).
     */
    private long getAppliedAtMillis(DocumentSnapshot document) {
        Object value = document.get("appliedAt");

        if (value == null) {
            return 0L;
        }

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }

        if (value instanceof Date) {
            return ((Date) value).getTime();
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            String dateValue = ((String) value).trim();

            if (dateValue.isEmpty()) {
                return 0L;
            }

            // Accept numeric strings containing epoch milliseconds.
            try {
                return Long.parseLong(dateValue);
            } catch (NumberFormatException ignored) {
                // Continue and try supported date formats.
            }

            String[] formats = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
            };

            for (String format : formats) {
                try {
                    java.text.SimpleDateFormat dateFormat =
                            new java.text.SimpleDateFormat(
                                    format,
                                    java.util.Locale.US
                            );

                    dateFormat.setLenient(false);
                    dateFormat.setTimeZone(
                            java.util.TimeZone.getTimeZone("UTC")
                    );

                    Date parsedDate = dateFormat.parse(dateValue);

                    if (parsedDate != null) {
                        return parsedDate.getTime();
                    }
                } catch (java.text.ParseException ignored) {
                    // Try the next supported date format.
                }
            }
        }

        // Keep malformed legacy values from crashing the admin screen.
        return 0L;
    }

    // =========================================================
    // CREATE LEAVE CARD
    // =========================================================

    private void addLeaveCard(
            DocumentSnapshot document
    ) {

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

        card.setElevation(
                dp(3)
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
                dp(16)
        );

        card.setLayoutParams(
                cardParams
        );

        // =====================================================
        // EMPLOYEE NAME
        // =====================================================

        String employeeName =
                document.getString("employeeName");

        if (employeeName == null
                || employeeName.trim().isEmpty()) {

            employeeName =
                    document.getString("name");
        }

        if (employeeName == null
                || employeeName.trim().isEmpty()) {

            employeeName = "Loading employee name...";
        }

        TextView employeeNameView =
                createTextView(
                        employeeName,
                        18,
                        "#251E32",
                        true
                );

        card.addView(
                employeeNameView
        );

        // =====================================================
        // EMPLOYEE EMAIL
        // =====================================================

        String email =
                document.getString("email");

        if (email == null
                || email.trim().isEmpty()) {

            email = "Unknown employee";
        }

        TextView employeeView =
                createTextView(
                        email,
                        15,
                        "#5F5869",
                        false
                );

        setTopMargin(
                employeeView,
                4
        );

        card.addView(
                employeeView
        );

        // =====================================================
        // LOAD EMPLOYEE NAME FROM EMPLOYEES COLLECTION
        // =====================================================

        loadEmployeeName(
                document,
                employeeNameView
        );

        // =====================================================
        // LEAVE TYPE
        // =====================================================

        String leaveType =
                document.getString("leaveType");

        if (leaveType == null) {
            leaveType = "Not specified";
        }

        TextView typeView =
                createTextView(
                        "Leave Type: " + leaveType,
                        14,
                        "#7651E8",
                        true
                );

        setTopMargin(
                typeView,
                8
        );

        card.addView(
                typeView
        );

        // =====================================================
        // DATES
        // =====================================================

        String fromDate =
                document.getString("fromDate");

        String toDate =
                document.getString("toDate");

        if (fromDate == null) {
            fromDate = "-";
        }

        if (toDate == null) {
            toDate = "-";
        }

        TextView datesView =
                createTextView(
                        "Dates: "
                                + fromDate
                                + " to "
                                + toDate,
                        14,
                        "#5F5869",
                        false
                );

        card.addView(
                datesView
        );

        // =====================================================
        // REASON
        // =====================================================

        String reason =
                document.getString("reason");

        if (reason == null
                || reason.trim().isEmpty()) {

            reason = "No reason provided";
        }

        TextView reasonView =
                createTextView(
                        "Reason: " + reason,
                        14,
                        "#5F5869",
                        false
                );

        setTopMargin(
                reasonView,
                6
        );

        card.addView(
                reasonView
        );

        // =====================================================
        // STATUS
        // =====================================================

        String status =
                document.getString("status");

        if (status == null) {
            status = "Pending";
        }

        TextView statusView =
                createTextView(
                        "Status: " + status,
                        14,
                        getStatusColor(status),
                        true
                );

        setTopMargin(
                statusView,
                10
        );

        card.addView(
                statusView
        );

        // =====================================================
        // APPROVE / REJECT
        // =====================================================

        if ("Pending".equalsIgnoreCase(status)) {

            LinearLayout buttonsRow =
                    new LinearLayout(this);

            buttonsRow.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            buttonsRow.setGravity(
                    Gravity.CENTER
            );

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(52)
                    );

            rowParams.setMargins(
                    0,
                    dp(14),
                    0,
                    0
            );

            buttonsRow.setLayoutParams(
                    rowParams
            );

            // =================================================
            // APPROVE BUTTON
            // =================================================

            Button approveButton =
                    new Button(this);

            approveButton.setText(
                    "Approve"
            );

            approveButton.setTextSize(
                    14
            );

            approveButton.setTextColor(
                    Color.WHITE
            );

            approveButton.setTypeface(
                    null,
                    Typeface.BOLD
            );

            approveButton.setGravity(
                    Gravity.CENTER
            );

            approveButton.setAllCaps(
                    false
            );

            approveButton.setMinHeight(0);
            approveButton.setMinWidth(0);

            approveButton.setPadding(
                    dp(10),
                    0,
                    dp(10),
                    0
            );

            approveButton.setBackground(
                    createButtonBackground(
                            "#159447"
                    )
            );

            LinearLayout.LayoutParams approveParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(52),
                            1
                    );

            approveParams.setMargins(
                    0,
                    0,
                    dp(6),
                    0
            );

            approveButton.setLayoutParams(
                    approveParams
            );

            // =================================================
            // REJECT BUTTON
            // =================================================

            Button rejectButton =
                    new Button(this);

            rejectButton.setText(
                    "Reject"
            );

            rejectButton.setTextSize(
                    14
            );

            rejectButton.setTextColor(
                    Color.WHITE
            );

            rejectButton.setTypeface(
                    null,
                    Typeface.BOLD
            );

            rejectButton.setGravity(
                    Gravity.CENTER
            );

            rejectButton.setAllCaps(
                    false
            );

            rejectButton.setMinHeight(0);
            rejectButton.setMinWidth(0);

            rejectButton.setPadding(
                    dp(10),
                    0,
                    dp(10),
                    0
            );

            rejectButton.setBackground(
                    createButtonBackground(
                            "#D24646"
                    )
            );

            LinearLayout.LayoutParams rejectParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(52),
                            1
                    );

            rejectParams.setMargins(
                    dp(6),
                    0,
                    0,
                    0
            );

            rejectButton.setLayoutParams(
                    rejectParams
            );

            // =================================================
            // APPROVE CLICK
            // =================================================

            approveButton.setOnClickListener(
                    v -> updateLeaveStatus(
                            document,
                            "Approved"
                    )
            );

            // =================================================
            // REJECT CLICK
            // =================================================

            rejectButton.setOnClickListener(
                    v -> updateLeaveStatus(
                            document,
                            "Rejected"
                    )
            );

            // Add buttons
            buttonsRow.addView(
                    approveButton
            );

            buttonsRow.addView(
                    rejectButton
            );

            card.addView(
                    buttonsRow
            );
        }

        // Add card to screen
        requestsContainer.addView(
                card
        );
    }

    // =========================================================
    // LOAD EMPLOYEE NAME
    // =========================================================

    private void loadEmployeeName(
            DocumentSnapshot leaveDocument,
            TextView employeeNameView
    ) {

        String employeeId =
                leaveDocument.getString("employeeId");

        if (employeeId == null
                || employeeId.trim().isEmpty()) {

            employeeNameView.setText(
                    "Employee"
            );

            return;
        }

        firestore.collection("employees")
                .document(employeeId)
                .get()
                .addOnSuccessListener(employeeDocument -> {

                    if (employeeDocument.exists()) {

                        String name =
                                employeeDocument.getString("name");

                        if (name != null
                                && !name.trim().isEmpty()) {

                            employeeNameView.setText(
                                    name
                            );

                            return;
                        }
                    }

                    // If name is not found
                    employeeNameView.setText(
                            "Employee"
                    );
                })
                .addOnFailureListener(e -> {

                    employeeNameView.setText(
                            "Employee"
                    );
                });
    }

    // =========================================================
    // BUTTON BACKGROUND
    // =========================================================

    private GradientDrawable createButtonBackground(
            String color
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(color)
        );

        drawable.setCornerRadius(
                dp(8)
        );

        return drawable;
    }

    // =========================================================
    // UPDATE LEAVE STATUS
    // =========================================================

    private void updateLeaveStatus(
            DocumentSnapshot document,
            String newStatus
    ) {

        String requestId =
                document.getId();

        String employeeId =
                document.getString("employeeId");

        if (employeeId == null
                || employeeId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Employee ID not found.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        firestore.collection("leaves")
                .document(employeeId)
                .collection("requests")
                .document(requestId)
                .update(
                        "status",
                        newStatus,
                        "reviewedAt",
                        new Timestamp(new Date()),
                        "reviewedBy",
                        currentUser.getUid()
                )
                .addOnSuccessListener(unused -> {

                    Toast.makeText(
                            this,
                            "Leave " + newStatus,
                            Toast.LENGTH_SHORT
                    ).show();

                    loadRequests();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to update leave: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // STATUS COLOR
    // =========================================================

    private int getStatusColor(
            String status
    ) {

        if ("Approved".equalsIgnoreCase(status)) {

            return Color.rgb(
                    21,
                    148,
                    71
            );
        }

        if ("Rejected".equalsIgnoreCase(status)) {

            return Color.rgb(
                    210,
                    70,
                    70
            );
        }

        return Color.rgb(
                216,
                131,
                40
        );
    }

    // =========================================================
    // CREATE TEXT VIEW
    // =========================================================

    private TextView createTextView(
            String text,
            int size,
            int color,
            boolean bold
    ) {

        TextView textView =
                new TextView(this);

        textView.setText(
                text
        );

        textView.setTextSize(
                size
        );

        textView.setTextColor(
                color
        );

        if (bold) {

            textView.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    // =========================================================
    // CREATE TEXT VIEW - STRING COLOR
    // =========================================================

    private TextView createTextView(
            String text,
            int size,
            String color,
            boolean bold
    ) {

        return createTextView(
                text,
                size,
                Color.parseColor(color),
                bold
        );
    }

    // =========================================================
    // TOP MARGIN
    // =========================================================

    private void setTopMargin(
            View view,
            int margin
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dp(margin),
                0,
                0
        );

        view.setLayoutParams(
                params
        );
    }

    // =========================================================
    // DP TO PIXELS
    // =========================================================

    private int dp(
            int value
    ) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }

    // =========================================================
    // GO TO ADMIN LOGIN
    // =========================================================

    private void goToAdminLogin() {

        Intent intent =
                new Intent(
                        this,
                        AdminLoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(
                intent
        );

        finish();
    }
}
