
package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LeaveManagementActivity extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private Spinner spinnerLeaveType;
    private Spinner spinnerLeaveDuration;

    private Button btnStartTime;
    private Button btnEndTime;
    private Button btnFromDate;
    private Button btnToDate;
    private Button btnSubmitLeave;
    private Button btnShowApplyLeave;

    private EditText etLeaveReason;

    private LinearLayout requestsContainer;
    private LinearLayout applyLeaveForm;

    private TextView tvNoRequests;
    private TextView btnBack;
    private TextView btnFormBack;
    private TextView labelToDate;
    private TextView labelStartTime;
    private TextView labelEndTime;

    private ScrollView mainScrollView;
    private ScrollView applyLeaveScrollView;

    private String fromDate = "";
    private String toDate = "";
    private String startTime = "";
    private String endTime = "";

    private static final int PURPLE = Color.rgb(121, 80, 239);
    private static final int DARK_TEXT = Color.rgb(36, 30, 49);
    private static final int MUTED_TEXT = Color.rgb(129, 122, 139);
    private static final int PAGE_BACKGROUND = Color.rgb(245, 243, 250);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leave_management);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initializeViews();
        setupSpinners();
        setupClickListeners();

        showRequestsScreen();
        loadLeaveRequests();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {
        mainScrollView = findViewById(R.id.mainScrollView);
        applyLeaveScrollView = findViewById(R.id.applyLeaveScrollView);

        btnShowApplyLeave = findViewById(R.id.btnShowApplyLeave);
        btnBack = findViewById(R.id.btnBack);
        btnFormBack = findViewById(R.id.btnFormBack);

        spinnerLeaveType = findViewById(R.id.spinnerLeaveType);
        spinnerLeaveDuration = findViewById(R.id.spinnerLeaveDuration);

        btnFromDate = findViewById(R.id.btnFromDate);
        btnToDate = findViewById(R.id.btnToDate);
        btnStartTime = findViewById(R.id.btnStartTime);
        btnEndTime = findViewById(R.id.btnEndTime);
        btnSubmitLeave = findViewById(R.id.btnSubmitLeave);

        etLeaveReason = findViewById(R.id.etLeaveReason);

        requestsContainer = findViewById(R.id.requestsContainer);
        tvNoRequests = findViewById(R.id.tvNoRequests);

        applyLeaveForm = findViewById(R.id.applyLeaveForm);

        labelToDate = findViewById(R.id.labelToDate);
        labelStartTime = findViewById(R.id.labelStartTime);
        labelEndTime = findViewById(R.id.labelEndTime);
    }

    // =========================================================
    // SPINNERS
    // =========================================================

    private void setupSpinners() {
        String[] leaveTypes = {
                "Casual Leave",
                "Sick Leave",
                "Earned Leave",
                "Emergency Leave",
                "Other"
        };

        ArrayAdapter<String> leaveTypeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                leaveTypes
        );

        leaveTypeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerLeaveType.setAdapter(leaveTypeAdapter);

        String[] durationOptions = {
                "Full-Day Leave",
                "Hourly Leave"
        };

        ArrayAdapter<String> durationAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                durationOptions
        );

        durationAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerLeaveDuration.setAdapter(durationAdapter);

        spinnerLeaveDuration.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        updateDurationFields();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );
    }

    private void updateDurationFields() {
        boolean hourly = isHourlyLeave();

        labelToDate.setVisibility(hourly ? View.GONE : View.VISIBLE);
        btnToDate.setVisibility(hourly ? View.GONE : View.VISIBLE);

        labelStartTime.setVisibility(hourly ? View.VISIBLE : View.GONE);
        btnStartTime.setVisibility(hourly ? View.VISIBLE : View.GONE);

        labelEndTime.setVisibility(hourly ? View.VISIBLE : View.GONE);
        btnEndTime.setVisibility(hourly ? View.VISIBLE : View.GONE);

        if (hourly) {
            btnFromDate.setText(fromDate.isEmpty()
                    ? "SELECT LEAVE DATE"
                    : "LEAVE DATE: " + fromDate);

            if (!fromDate.isEmpty()) {
                toDate = fromDate;
            }
        } else {
            btnFromDate.setText(fromDate.isEmpty()
                    ? "SELECT START DATE"
                    : fromDate);

            btnToDate.setText(toDate.isEmpty()
                    ? "SELECT END DATE"
                    : toDate);
        }
    }

    private boolean isHourlyLeave() {
        return spinnerLeaveDuration.getSelectedItemPosition() == 1;
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private void setupClickListeners() {
        btnShowApplyLeave.setOnClickListener(v -> {
            resetForm();
            showApplyScreen();
        });

        btnFormBack.setOnClickListener(v -> showRequestsScreen());

        btnBack.setOnClickListener(v -> finish());

        btnFromDate.setOnClickListener(v -> {
            openDatePicker(true);
        });

        btnToDate.setOnClickListener(v -> {
            openDatePicker(false);
        });

        btnStartTime.setOnClickListener(v -> openTimePicker(true));
        btnEndTime.setOnClickListener(v -> openTimePicker(false));

        btnSubmitLeave.setOnClickListener(v -> submitLeaveRequest());
    }

    private void showRequestsScreen() {
        applyLeaveScrollView.setVisibility(View.GONE);
        mainScrollView.setVisibility(View.VISIBLE);
    }

    private void showApplyScreen() {
        mainScrollView.setVisibility(View.GONE);
        applyLeaveScrollView.setVisibility(View.VISIBLE);

        applyLeaveScrollView.post(() ->
                applyLeaveScrollView.scrollTo(0, 0)
        );
    }

    // =========================================================
    // DATE PICKER
    // =========================================================

    private void openDatePicker(boolean selectingFromDate) {
        Calendar calendar = Calendar.getInstance();

        String existingDate = selectingFromDate ? fromDate : toDate;

        if (!existingDate.isEmpty()) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                );
                format.setLenient(false);

                Date date = format.parse(existingDate);

                if (date != null) {
                    calendar.setTime(date);
                }
            } catch (Exception ignored) {
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            dayOfMonth
                    );

                    if (selectingFromDate) {
                        fromDate = selectedDate;
                        btnFromDate.setText(isHourlyLeave()
                                ? "LEAVE DATE: " + selectedDate
                                : selectedDate);

                        if (isHourlyLeave()) {
                            toDate = selectedDate;
                        } else if (!toDate.isEmpty()
                                && toDate.compareTo(fromDate) < 0) {
                            toDate = "";
                            btnToDate.setText("SELECT END DATE");
                        }
                    } else {
                        if (!fromDate.isEmpty()
                                && selectedDate.compareTo(fromDate) < 0) {
                            Toast.makeText(
                                    this,
                                    "End date cannot be before start date",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        toDate = selectedDate;
                        btnToDate.setText(selectedDate);
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    // =========================================================
    // TIME PICKER
    // =========================================================

    private void openTimePicker(boolean selectingStart) {
        Calendar calendar = Calendar.getInstance();

        String existingTime = selectingStart ? startTime : endTime;

        if (!existingTime.isEmpty()) {
            try {
                String[] parts = existingTime.split(":");
                calendar.set(
                        Calendar.HOUR_OF_DAY,
                        Integer.parseInt(parts[0])
                );
                calendar.set(
                        Calendar.MINUTE,
                        Integer.parseInt(parts[1])
                );
            } catch (Exception ignored) {
            }
        }

        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String selectedTime = String.format(
                            Locale.US,
                            "%02d:%02d",
                            hourOfDay,
                            minute
                    );

                    if (selectingStart) {
                        startTime = selectedTime;
                        btnStartTime.setText("START TIME: " + selectedTime);
                    } else {
                        endTime = selectedTime;
                        btnEndTime.setText("END TIME: " + selectedTime);
                    }
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        ).show();
    }

    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60
                + Integer.parseInt(parts[1]);
    }

    // =========================================================
    // SUBMIT LEAVE REQUEST
    // =========================================================

    private void submitLeaveRequest() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(
                    this,
                    "Please login again",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        boolean hourlyLeave = isHourlyLeave();

        if (fromDate.isEmpty()) {
            Toast.makeText(
                    this,
                    hourlyLeave
                            ? "Please select the leave date"
                            : "Please select the start date",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (hourlyLeave) {
            toDate = fromDate;

            if (startTime.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(
                        this,
                        "Please select start and end times",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (timeToMinutes(endTime) <= timeToMinutes(startTime)) {
                Toast.makeText(
                        this,
                        "End time must be after start time",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
        } else {
            if (toDate.isEmpty()) {
                Toast.makeText(
                        this,
                        "Please select the end date",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (toDate.compareTo(fromDate) < 0) {
                Toast.makeText(
                        this,
                        "End date cannot be before start date",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
        }

        String reason = etLeaveReason.getText().toString().trim();

        if (reason.isEmpty()) {
            etLeaveReason.setError("Please enter the reason");
            etLeaveReason.requestFocus();
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail();

        String leaveType = spinnerLeaveType.getSelectedItem().toString();

        Map<String, Object> leaveData = new HashMap<>();

        // Preserve the existing Firestore fields.
        leaveData.put("employeeId", uid);
        leaveData.put("email", email);
        leaveData.put("leaveType", leaveType);
        leaveData.put("fromDate", fromDate);
        leaveData.put("toDate", toDate);
        leaveData.put(
                "durationType",
                hourlyLeave ? "Hourly" : "Full-Day"
        );

        if (hourlyLeave) {
            leaveData.put("startTime", startTime);
            leaveData.put("endTime", endTime);

            double hours = (
                    timeToMinutes(endTime) - timeToMinutes(startTime)
            ) / 60.0;

            leaveData.put("hours", hours);
        }

        leaveData.put("reason", reason);
        leaveData.put("status", "Pending");
        leaveData.put("appliedAt", new Date());

        btnSubmitLeave.setEnabled(false);
        btnSubmitLeave.setText("SUBMITTING...");

        DocumentReference newRequest = firestore
                .collection("leaves")
                .document(uid)
                .collection("requests")
                .document();

        newRequest.set(leaveData)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(
                            LeaveManagementActivity.this,
                            "Leave request submitted successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    resetForm();
                    showRequestsScreen();
                    loadLeaveRequests();
                })
                .addOnFailureListener(e -> {
                    btnSubmitLeave.setEnabled(true);
                    btnSubmitLeave.setText("SUBMIT LEAVE REQUEST");

                    Toast.makeText(
                            LeaveManagementActivity.this,
                            "Failed to submit leave: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // RESET FORM
    // =========================================================

    private void resetForm() {
        fromDate = "";
        toDate = "";
        startTime = "";
        endTime = "";

        btnFromDate.setText("SELECT START DATE");
        btnToDate.setText("SELECT END DATE");
        btnStartTime.setText("SELECT START TIME");
        btnEndTime.setText("SELECT END TIME");

        etLeaveReason.setText("");

        spinnerLeaveType.setSelection(0);
        spinnerLeaveDuration.setSelection(0);

        btnSubmitLeave.setEnabled(true);
        btnSubmitLeave.setText("SUBMIT LEAVE REQUEST");

        updateDurationFields();
    }

    // =========================================================
    // LOAD LEAVE REQUESTS
    // =========================================================

    private void loadLeaveRequests() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            return;
        }

        String uid = user.getUid();

        firestore.collection("leaves")
                .document(uid)
                .collection("requests")
                .orderBy("appliedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    requestsContainer.removeAllViews();

                    int activeRequests = 0;

                    for (DocumentSnapshot document
                            : querySnapshot.getDocuments()) {

                        String endDate = document.getString("toDate");

                        // Preserve the existing expired-leave filtering.
                        if (isLeaveExpired(endDate)) {
                            continue;
                        }

                        activeRequests++;
                        addRequestCard(document);
                    }

                    if (activeRequests == 0) {
                        tvNoRequests.setVisibility(View.VISIBLE);
                        tvNoRequests.setText("No active leave requests");
                    } else {
                        tvNoRequests.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(
                            LeaveManagementActivity.this,
                            "Could not load leave requests: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // CHECK EXPIRED LEAVE
    // =========================================================

    private boolean isLeaveExpired(String endDate) {
        if (endDate == null || endDate.trim().isEmpty()) {
            return false;
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
            );
            format.setLenient(false);

            Date leaveEndDate = format.parse(endDate);
            Date today = format.parse(format.format(new Date()));

            if (leaveEndDate == null || today == null) {
                return false;
            }

            // Requests ending today remain visible.
            return leaveEndDate.before(today);

        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    // CREATE LEAVE REQUEST CARD
    // =========================================================

    private void addRequestCard(DocumentSnapshot document) {
        String leaveType = document.getString("leaveType");
        String startDate = document.getString("fromDate");
        String endDate = document.getString("toDate");
        String durationType = document.getString("durationType");
        String savedStartTime = document.getString("startTime");
        String savedEndTime = document.getString("endTime");
        String reason = document.getString("reason");
        String status = document.getString("status");

        if (leaveType == null) leaveType = "Leave";
        if (startDate == null) startDate = "-";
        if (endDate == null) endDate = "-";
        if (reason == null) reason = "-";
        if (status == null) status = "Pending";

        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(createRoundedBackground(Color.WHITE, 14, 0));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        // Title and status row
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView typeText = new TextView(this);
        typeText.setText(leaveType);
        typeText.setTextColor(DARK_TEXT);
        typeText.setTextSize(16);
        typeText.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        typeText.setLayoutParams(typeParams);

        TextView statusText = new TextView(this);
        statusText.setText(status);
        statusText.setTextSize(12);
        statusText.setTypeface(null, Typeface.BOLD);
        statusText.setPadding(dp(12), dp(7), dp(12), dp(7));

        int statusTextColor;
        int statusBackgroundColor;

        if ("Approved".equalsIgnoreCase(status)) {
            statusTextColor = Color.rgb(30, 130, 75);
            statusBackgroundColor = Color.rgb(225, 245, 234);
        } else if ("Rejected".equalsIgnoreCase(status)) {
            statusTextColor = Color.rgb(190, 50, 50);
            statusBackgroundColor = Color.rgb(255, 232, 232);
        } else {
            statusTextColor = Color.rgb(190, 120, 25);
            statusBackgroundColor = Color.rgb(255, 244, 220);
        }

        statusText.setTextColor(statusTextColor);
        statusText.setBackground(
                createRoundedBackground(statusBackgroundColor, 20, 0)
        );

        topRow.addView(typeText);
        topRow.addView(statusText);
        card.addView(topRow);

        // Date or hourly time range
        TextView datesText = new TextView(this);
        datesText.setTextSize(13);
        datesText.setTextColor(MUTED_TEXT);

        if ("Hourly".equalsIgnoreCase(durationType)) {
            Object hours = document.get("hours");

            String hoursText = hours == null ? "" : " (" + hours + " hours)";

            datesText.setText(
                    startDate + "  •  "
                            + (savedStartTime == null ? "" : savedStartTime)
                            + " – "
                            + (savedEndTime == null ? "" : savedEndTime)
                            + hoursText
            );
        } else {
            datesText.setText(startDate + "  →  " + endDate);
        }

        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dateParams.setMargins(0, dp(12), 0, 0);
        datesText.setLayoutParams(dateParams);
        card.addView(datesText);

        // Reason
        TextView reasonText = new TextView(this);
        reasonText.setText("Reason: " + reason);
        reasonText.setTextColor(Color.rgb(80, 72, 90));
        reasonText.setTextSize(14);
        reasonText.setLineSpacing(dp(2), 1.0f);

        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        reasonParams.setMargins(0, dp(10), 0, 0);
        reasonText.setLayoutParams(reasonParams);
        card.addView(reasonText);

        requestsContainer.addView(card);
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private GradientDrawable createRoundedBackground(
            int color,
            int radiusDp,
            int strokeColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));

        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }

        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
