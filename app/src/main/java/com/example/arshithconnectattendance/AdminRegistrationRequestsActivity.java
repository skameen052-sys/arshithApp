package com.example.arshithconnectattendance;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminRegistrationRequestsActivity extends ComponentActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private LinearLayout requestsContainer;
    private ProgressBar progressRequests;
    private TextView tvNoRequests;
    private TextView tabPending;
    private TextView tabProcessed;
    private TextView tabAll;
    private Button btnRefreshRequests;

    private final List<QueryDocumentSnapshot> allRequests = new ArrayList<>();

    private String currentFilter = "pending";

    private int pendingCount;
    private int processedCount;
    private int allCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_registration_requests);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        requestsContainer = findViewById(R.id.requestsContainer);
        progressRequests = findViewById(R.id.progressRequests);
        tvNoRequests = findViewById(R.id.tvNoRequests);
        tabPending = findViewById(R.id.tabPending);
        tabProcessed = findViewById(R.id.tabProcessed);
        tabAll = findViewById(R.id.tabAll);
        btnRefreshRequests = findViewById(R.id.btnRefreshRequests);

        btnRefreshRequests.setOnClickListener(v -> verifyAdminAndLoad());

        tabPending.setOnClickListener(v -> selectFilter("pending"));
        tabProcessed.setOnClickListener(v -> selectFilter("processed"));
        tabAll.setOnClickListener(v -> selectFilter("all"));

        if (auth.getCurrentUser() == null) {
            goToAdminLogin();
            return;
        }

        updateTabAppearance();
        verifyAdminAndLoad();
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        updateTabAppearance();
        displayRequests();
    }

    // ------------------------------------------------------------
    // ADMIN VERIFICATION
    // ------------------------------------------------------------

    private void verifyAdminAndLoad() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            goToAdminLogin();
            return;
        }

        setLoading(true);

        firestore.collection("admins")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(adminDocument -> {
                    String role = adminDocument.getString("role");

                    if (!adminDocument.exists()
                            || role == null
                            || !"admin".equalsIgnoreCase(role.trim())) {

                        setLoading(false);

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        auth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    loadAllRequests();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);

                    Toast.makeText(
                            this,
                            "Could not verify admin: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ------------------------------------------------------------
    // LOADING
    // ------------------------------------------------------------

    private void setLoading(boolean loading) {
        progressRequests.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );

        btnRefreshRequests.setEnabled(!loading);
    }

    private void loadAllRequests() {
        setLoading(true);
        requestsContainer.removeAllViews();
        tvNoRequests.setVisibility(View.GONE);

        firestore.collection("employees")
                .get()
                .addOnSuccessListener(snapshot -> {
                    allRequests.clear();

                    for (QueryDocumentSnapshot document : snapshot) {
                        allRequests.add(document);
                    }

                    calculateCounts();
                    updateTabTexts();
                    updateTabAppearance();
                    displayRequests();
                    setLoading(false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);

                    Toast.makeText(
                            this,
                            "Unable to load requests: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void calculateCounts() {
        pendingCount = 0;
        processedCount = 0;
        allCount = allRequests.size();

        for (QueryDocumentSnapshot document : allRequests) {
            String status = document.getString("status");

            if (status != null
                    && "pending".equalsIgnoreCase(status.trim())) {
                pendingCount++;
            } else {
                processedCount++;
            }
        }
    }

    private void updateTabTexts() {
        tabPending.setText("Pending Approval (" + pendingCount + ")");
        tabProcessed.setText("Processed (" + processedCount + ")");
        tabAll.setText("All Requests (" + allCount + ")");
    }

    // ------------------------------------------------------------
    // TAB STYLING
    // ------------------------------------------------------------

    private void updateTabAppearance() {
        resetTab(tabPending);
        resetTab(tabProcessed);
        resetTab(tabAll);

        TextView selected;

        if ("processed".equals(currentFilter)) {
            selected = tabProcessed;
        } else if ("all".equals(currentFilter)) {
            selected = tabAll;
        } else {
            selected = tabPending;
        }

        selected.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(8, 72, 54))
        );
        selected.setBackgroundColor(Color.rgb(8, 72, 54));
        selected.setTextColor(Color.WHITE);
        selected.setTypeface(null, Typeface.BOLD);
    }

    private void resetTab(TextView tab) {
        tab.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(247, 249, 252))
        );
        tab.setBackgroundColor(Color.rgb(247, 249, 252));
        tab.setTextColor(Color.rgb(100, 116, 139));
        tab.setTypeface(null, Typeface.NORMAL);
    }

    // ------------------------------------------------------------
    // DISPLAY REQUESTS
    // ------------------------------------------------------------

    private void displayRequests() {
        requestsContainer.removeAllViews();
        tvNoRequests.setVisibility(View.GONE);

        List<QueryDocumentSnapshot> filtered = new ArrayList<>();

        for (QueryDocumentSnapshot document : allRequests) {
            String status = document.getString("status");

            if (status == null) {
                status = "";
            }

            status = status.trim();

            if ("pending".equals(currentFilter)) {
                if ("pending".equalsIgnoreCase(status)) {
                    filtered.add(document);
                }
            } else if ("processed".equals(currentFilter)) {
                if (!"pending".equalsIgnoreCase(status)) {
                    filtered.add(document);
                }
            } else {
                filtered.add(document);
            }
        }

        if (filtered.isEmpty()) {
            tvNoRequests.setVisibility(View.VISIBLE);
            tvNoRequests.setText(
                    "all".equals(currentFilter)
                            ? "No registration requests found."
                            : "processed".equals(currentFilter)
                              ? "No processed requests."
                              : "No pending registration requests."
            );
            return;
        }

        HorizontalScrollView horizontalScrollView =
                new HorizontalScrollView(this);

        horizontalScrollView.setFillViewport(false);
        horizontalScrollView.setHorizontalScrollBarEnabled(true);

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);

        horizontalScrollView.addView(
                table,
                new HorizontalScrollView.LayoutParams(
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        requestsContainer.addView(
                horizontalScrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        addTableHeader(table);

        for (QueryDocumentSnapshot document : filtered) {
            addRequestRow(table, document);
        }
    }

    // ------------------------------------------------------------
    // TABLE HEADER
    // ------------------------------------------------------------

    private void addTableHeader(LinearLayout table) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackgroundColor(Color.rgb(248, 249, 252));

        addCell(row, "APPLICANT", 260, true);
        addCell(row, "EMPLOYEE CODE", 190, true);
        addCell(row, "PHONE", 145, true);
        addCell(row, "REQUEST DATE", 160, true);
        addCell(row, "STATUS", 120, true);
        addCell(row, "DECISION / AUDIT", 190, true);
        addCell(row, "ACTIONS", 240, true);

        table.addView(row);
    }

    // ------------------------------------------------------------
    // TABLE ROW
    // ------------------------------------------------------------

    private void addRequestRow(
            LinearLayout table,
            QueryDocumentSnapshot document
    ) {
        String uid = document.getId();
        String name = getValue(document, "name", "Employee");
        String employeeId = getValue(document, "employeeId", "--");
        String phone = getValue(document, "phone", "--");
        String email = getValue(document, "email", "");
        String status = getValue(document, "status", "Pending");
        String requestDate = getRequestDate(document);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        row.setBackgroundColor(Color.WHITE);

        // Applicant name and email
        LinearLayout applicant = new LinearLayout(this);
        applicant.setOrientation(LinearLayout.VERTICAL);

        applicant.addView(createCellText(
                name, 15, Color.rgb(25, 78, 65), true
        ));

        if (!email.trim().isEmpty()) {
            TextView emailText = createCellText(
                    email, 12, Color.rgb(100, 116, 139), false
            );

            LinearLayout.LayoutParams emailParams =
                    new LinearLayout.LayoutParams(
                            dp(230),
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            emailParams.topMargin = dp(4);
            applicant.addView(emailText, emailParams);
        }

        row.addView(applicant, fixedWidth(260));

        addCell(row, employeeId, 190, false);
        addCell(row, phone, 145, false);
        addCell(row, requestDate, 160, false);

        row.addView(
                createStatusText(status),
                fixedWidth(120)
        );

        String decision = "pending".equalsIgnoreCase(status)
                ? "Awaiting Review"
                : !getValue(document, "reviewedBy", "").isEmpty()
                  ? "Reviewed by Admin"
                  : "Processed";

        row.addView(
                createCellText(
                        decision,
                        14,
                        "pending".equalsIgnoreCase(status)
                                ? Color.rgb(190, 120, 35)
                                : Color.rgb(71, 85, 105),
                        false
                ),
                fixedWidth(190)
        );

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        if ("pending".equalsIgnoreCase(status)) {
            Button approveButton = createApproveButton();
            Button rejectButton = createRejectButton();

            actions.addView(approveButton);

            LinearLayout.LayoutParams rejectParams =
                    fixedWidth(105);
            rejectParams.leftMargin = dp(8);
            actions.addView(rejectButton, rejectParams);

            approveButton.setOnClickListener(v -> {
                updateRequestStatus(
                        uid, "approved", approveButton, rejectButton
                );
            });

            rejectButton.setOnClickListener(v -> {
                updateRequestStatus(
                        uid, "rejected", approveButton, rejectButton
                );
            });
        } else {
            actions.addView(createCellText(
                    "Completed",
                    13,
                    Color.rgb(100, 116, 139),
                    false
            ));
        }

        row.addView(actions, fixedWidth(240));
        table.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(230, 234, 240));

        table.addView(
                divider,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );
    }

    // ------------------------------------------------------------
    // CELL HELPERS
    // ------------------------------------------------------------

    private void addCell(
            LinearLayout row,
            String value,
            int widthDp,
            boolean header
    ) {
        row.addView(
                createCellText(
                        value,
                        header ? 12 : 14,
                        header
                                ? Color.rgb(71, 85, 105)
                                : Color.rgb(51, 65, 85),
                        header
                ),
                fixedWidth(widthDp)
        );
    }

    private LinearLayout.LayoutParams fixedWidth(int widthDp) {
        return new LinearLayout.LayoutParams(
                dp(widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private TextView createCellText(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(true);

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    // ------------------------------------------------------------
    // STATUS BADGE
    // ------------------------------------------------------------

    private TextView createStatusText(String status) {
        TextView text = new TextView(this);

        if ("approved".equalsIgnoreCase(status)) {
            text.setText("Approved");
            text.setTextColor(Color.rgb(22, 128, 83));
            text.setBackgroundColor(Color.rgb(232, 248, 240));
        } else if ("rejected".equalsIgnoreCase(status)) {
            text.setText("Rejected");
            text.setTextColor(Color.rgb(185, 28, 28));
            text.setBackgroundColor(Color.rgb(254, 242, 242));
        } else {
            text.setText("Pending");
            text.setTextColor(Color.rgb(194, 93, 25));
            text.setBackgroundColor(Color.rgb(255, 247, 237));
        }

        text.setTextSize(12);
        text.setTypeface(null, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(12), dp(7), dp(12), dp(7));

        return text;
    }

    private Button createApproveButton() {
        Button button = new Button(this);
        button.setText("✓ Approve");
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(22, 128, 83));
        button.setTypeface(null, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(235, 250, 243))
        );

        button.setLayoutParams(
                new LinearLayout.LayoutParams(dp(110), dp(44))
        );

        return button;
    }

    private Button createRejectButton() {
        Button button = new Button(this);
        button.setText("✕ Reject");
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(220, 38, 38));
        button.setTypeface(null, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(254, 242, 242))
        );

        button.setLayoutParams(
                new LinearLayout.LayoutParams(dp(105), dp(44))
        );

        return button;
    }

    // ------------------------------------------------------------
    // APPROVE / REJECT
    // ------------------------------------------------------------

    private void updateRequestStatus(
            String employeeUid,
            String newStatus,
            Button approveButton,
            Button rejectButton
    ) {
        FirebaseUser currentAdmin = auth.getCurrentUser();

        if (currentAdmin == null) {
            goToAdminLogin();
            return;
        }

        approveButton.setEnabled(false);
        rejectButton.setEnabled(false);

        Toast.makeText(
                this,
                "approved".equals(newStatus)
                        ? "Approving employee..."
                        : "Rejecting employee...",
                Toast.LENGTH_SHORT
        ).show();

        // Revalidate admin access before changing data.
        firestore.collection("admins")
                .document(currentAdmin.getUid())
                .get()
                .addOnSuccessListener(adminDocument -> {
                    String role = adminDocument.getString("role");

                    if (!adminDocument.exists()
                            || role == null
                            || !"admin".equalsIgnoreCase(role.trim())) {

                        restoreButtons(approveButton, rejectButton);

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        auth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    firestore.collection("employees")
                            .document(employeeUid)
                            .get()
                            .addOnSuccessListener(employeeDocument -> {

                                if (!employeeDocument.exists()) {
                                    restoreButtons(approveButton, rejectButton);

                                    Toast.makeText(
                                            this,
                                            "Employee request not found.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    loadAllRequests();
                                    return;
                                }

                                String currentStatus =
                                        employeeDocument.getString("status");

                                if (currentStatus == null
                                        || !"pending".equalsIgnoreCase(
                                        currentStatus.trim())) {

                                    restoreButtons(approveButton, rejectButton);

                                    Toast.makeText(
                                            this,
                                            "This request is no longer pending.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    loadAllRequests();
                                    return;
                                }

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", newStatus);
                                updates.put("reviewedAt", FieldValue.serverTimestamp());
                                updates.put("reviewedBy", currentAdmin.getUid());

                                firestore.collection("employees")
                                        .document(employeeUid)
                                        .update(updates)
                                        .addOnSuccessListener(unused -> {
                                            Toast.makeText(
                                                    this,
                                                    "approved".equals(newStatus)
                                                            ? "Employee approved successfully."
                                                            : "Employee request rejected.",
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            loadAllRequests();
                                        })
                                        .addOnFailureListener(e -> {
                                            restoreButtons(
                                                    approveButton,
                                                    rejectButton
                                            );

                                            Toast.makeText(
                                                    this,
                                                    "Could not update request: "
                                                            + e.getMessage(),
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                restoreButtons(approveButton, rejectButton);

                                Toast.makeText(
                                        this,
                                        "Could not check employee: "
                                                + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                })
                .addOnFailureListener(e -> {
                    restoreButtons(approveButton, rejectButton);

                    Toast.makeText(
                            this,
                            "Could not verify admin: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void restoreButtons(Button approve, Button reject) {
        approve.setEnabled(true);
        reject.setEnabled(true);
    }

    // ------------------------------------------------------------
    // FIRESTORE VALUE / DATE HELPERS
    // ------------------------------------------------------------

    private String getValue(
            DocumentSnapshot document,
            String field,
            String fallback
    ) {
        Object value = document.get(field);

        if (value == null) {
            return fallback;
        }

        String result = String.valueOf(value).trim();
        return result.isEmpty() ? fallback : result;
    }

    private String getRequestDate(DocumentSnapshot document) {
        Object value = document.get("createdAt");

        if (value == null) {
            value = document.get("requestedAt");
        }

        if (value == null) {
            value = document.get("registeredAt");
        }

        if (value instanceof Timestamp) {
            return formatDate(((Timestamp) value).toDate());
        }

        if (value instanceof Date) {
            return formatDate((Date) value);
        }

        if (value instanceof String) {
            String date = ((String) value).trim();
            return date.isEmpty() ? "--" : date;
        }

        if (value instanceof Number) {
            long millis = ((Number) value).longValue();

            if (millis < 10000000000L) {
                millis *= 1000L;
            }

            return formatDate(new Date(millis));
        }

        return "--";
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
        ).format(date);
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void goToAdminLogin() {
        Intent intent = new Intent(
                this,
                AdminLoginActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}