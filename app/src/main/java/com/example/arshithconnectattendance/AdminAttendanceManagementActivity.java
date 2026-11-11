package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AdminAttendanceManagementActivity
        extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private TextView tvSelectedDate;
    private TextView tvTotalAttendance;
    private TextView tvWfoCount;
    private TextView tvWfhCount;
    private TextView tvLateCount;
    private TextView tvNoRecords;

    private Button btnBack;
    private Button btnSelectDate;
    private Button btnRefresh;

    private LinearLayout recordsContainer;

    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_attendance_management
        );

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        initializeViews();

        /*
         * IMPORTANT:
         *
         * Start with today's date.
         * You can use CHANGE to select the date
         * on which the employee attendance was recorded.
         */
        selectedDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
        ).format(new Date());

        tvSelectedDate.setText(selectedDate);

        setupButtons();

        verifyAdminAndLoadAttendance();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        tvSelectedDate = findViewById(
                R.id.tvSelectedDate
        );

        tvTotalAttendance = findViewById(
                R.id.tvTotalAttendance
        );

        tvWfoCount = findViewById(
                R.id.tvWfoCount
        );

        tvWfhCount = findViewById(
                R.id.tvWfhCount
        );

        tvLateCount = findViewById(
                R.id.tvLateCount
        );

        tvNoRecords = findViewById(
                R.id.tvNoRecords
        );

        recordsContainer = findViewById(
                R.id.recordsContainer
        );

        btnBack = findViewById(
                R.id.btnBack
        );

        btnSelectDate = findViewById(
                R.id.btnSelectDate
        );

        btnRefresh = findViewById(
                R.id.btnRefresh
        );
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                v -> finish()
        );

        btnSelectDate.setOnClickListener(
                v -> showDatePicker()
        );

        btnRefresh.setOnClickListener(
                v -> loadAttendance()
        );
    }

    // =========================================================
    // VERIFY ADMIN
    // =========================================================

    private void verifyAdminAndLoadAttendance() {

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        firestore
                .collection("admins")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        firebaseAuth.signOut();

                        goToAdminLogin();

                        return;
                    }

                    String role =
                            documentSnapshot.getString("role");

                    if (!"admin".equalsIgnoreCase(role)) {

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        firebaseAuth.signOut();

                        goToAdminLogin();

                        return;
                    }

                    loadAttendance();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to verify admin: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // DATE PICKER
    // =========================================================

    private void showDatePicker() {

        Calendar calendar = Calendar.getInstance();

        try {

            Date date = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
            ).parse(selectedDate);

            if (date != null) {
                calendar.setTime(date);
            }

        } catch (Exception ignored) {
        }

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            Calendar selected =
                                    Calendar.getInstance();

                            selected.set(
                                    year,
                                    month,
                                    dayOfMonth
                            );

                            selectedDate =
                                    new SimpleDateFormat(
                                            "yyyy-MM-dd",
                                            Locale.getDefault()
                                    ).format(
                                            selected.getTime()
                                    );

                            tvSelectedDate.setText(
                                    selectedDate
                            );

                            loadAttendance();
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );

        dialog.show();
    }

    // =========================================================
    // LOAD ATTENDANCE
    // =========================================================

    private void loadAttendance() {

        recordsContainer.removeAllViews();

        tvNoRecords.setVisibility(
                View.GONE
        );

        tvTotalAttendance.setText("0");
        tvWfoCount.setText("0");
        tvWfhCount.setText("0");
        tvLateCount.setText("0");

        firestore
                .collectionGroup("records")
                .whereEqualTo(
                        "date",
                        selectedDate
                )
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int total = 0;
                    int wfo = 0;
                    int wfh = 0;
                    int late = 0;

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        total++;

                        String workMode =
                                document.getString(
                                        "workMode"
                                );

                        if ("WFO".equalsIgnoreCase(
                                workMode
                        )) {

                            wfo++;

                        } else if (
                                "WFH".equalsIgnoreCase(
                                        workMode
                                )
                        ) {

                            wfh++;
                        }

                        Boolean lateLogin =
                                document.getBoolean(
                                        "lateLogin"
                                );

                        if (Boolean.TRUE.equals(
                                lateLogin
                        )) {

                            late++;
                        }

                        addAttendanceCard(
                                document
                        );
                    }

                    tvTotalAttendance.setText(
                            String.valueOf(total)
                    );

                    tvWfoCount.setText(
                            String.valueOf(wfo)
                    );

                    tvWfhCount.setText(
                            String.valueOf(wfh)
                    );

                    tvLateCount.setText(
                            String.valueOf(late)
                    );

                    if (total == 0) {

                        tvNoRecords.setVisibility(
                                View.VISIBLE
                        );

                        tvNoRecords.setText(
                                "No attendance records found for "
                                        + selectedDate
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    tvNoRecords.setVisibility(
                            View.VISIBLE
                    );

                    tvNoRecords.setText(
                            "Unable to load attendance records."
                    );

                    Toast.makeText(
                            this,
                            "Firestore error: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // ADD ATTENDANCE CARD
    // =========================================================

    private void addAttendanceCard(
            DocumentSnapshot document
    ) {

        LinearLayout card =
                new LinearLayout(this);

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
                Color.WHITE
        );

        card.setElevation(3f);

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
        // EMPLOYEE EMAIL
        // =====================================================

        String email =
                document.getString("email");

        if (email == null ||
                email.trim().isEmpty()) {

            email =
                    document.getString("employeeId");
        }

        if (email == null ||
                email.trim().isEmpty()) {

            email = "Unknown employee";
        }

        TextView employee =
                createTextView(
                        email,
                        17,
                        "#251E32",
                        true
                );

        card.addView(employee);

        // =====================================================
        // DATE
        // =====================================================

        String date =
                document.getString("date");

        if (date == null) {
            date = selectedDate;
        }

        TextView dateView =
                createTextView(
                        "Date: " + date,
                        13,
                        "#777080",
                        false
                );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                5,
                0,
                0
        );

        dateView.setLayoutParams(
                dateParams
        );

        card.addView(dateView);

        // =====================================================
        // WORK MODE
        // =====================================================

        String workMode =
                document.getString("workMode");

        if (workMode == null) {
            workMode = "Not specified";
        }

        TextView mode =
                createTextView(
                        "Work Mode: " + workMode,
                        14,
                        "#7651E8",
                        true
                );

        LinearLayout.LayoutParams modeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        modeParams.setMargins(
                0,
                8,
                0,
                0
        );

        mode.setLayoutParams(
                modeParams
        );

        card.addView(mode);

        // =====================================================
        // CHECK IN
        // =====================================================

        String checkIn =
                formatTimestamp(
                        document.get("checkIn")
                );

        TextView checkInView =
                createTextView(
                        "Check In: " + checkIn,
                        13,
                        "#5F5869",
                        false
                );

        card.addView(checkInView);

        // =====================================================
        // CHECK OUT
        // =====================================================

        String checkOut =
                formatTimestamp(
                        document.get("checkOut")
                );

        TextView checkOutView =
                createTextView(
                        "Check Out: " + checkOut,
                        13,
                        "#5F5869",
                        false
                );

        card.addView(checkOutView);

        // =====================================================
        // CHECK-IN PHOTO
        // =====================================================

        String checkInPhotoUrl =
                document.getString(
                        "checkInPhotoUrl"
                );

        if (checkInPhotoUrl != null
                && !checkInPhotoUrl.trim().isEmpty()) {

            Button btnViewPhoto =
                    new Button(this);

            btnViewPhoto.setText(
                    "VIEW CHECK-IN PHOTO"
            );

            btnViewPhoto.setTextSize(12);

            btnViewPhoto.setTextColor(
                    Color.WHITE
            );

            btnViewPhoto.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#7651E8")
                    )
            );

            LinearLayout.LayoutParams photoButtonParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            photoButtonParams.setMargins(
                    0,
                    12,
                    0,
                    0
            );

            btnViewPhoto.setLayoutParams(
                    photoButtonParams
            );

            btnViewPhoto.setOnClickListener(
                    v -> openCheckInPhoto(
                            checkInPhotoUrl
                    )
            );

            card.addView(
                    btnViewPhoto
            );
        }

        // =====================================================
        // LATE LOGIN
        // =====================================================

        Boolean lateLogin =
                document.getBoolean(
                        "lateLogin"
                );

        if (Boolean.TRUE.equals(
                lateLogin
        )) {

            TextView lateView =
                    createTextView(
                            "LATE LOGIN",
                            13,
                            "#D88328",
                            true
                    );

            LinearLayout.LayoutParams lateParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            lateParams.setMargins(
                    0,
                    8,
                    0,
                    0
            );

            lateView.setLayoutParams(
                    lateParams
            );

            card.addView(lateView);

            String lateReason =
                    document.getString(
                            "lateReason"
                    );

            if (lateReason != null
                    && !lateReason.trim().isEmpty()) {

                TextView reasonView =
                        createTextView(
                                "Reason: " + lateReason,
                                13,
                                "#777080",
                                false
                        );

                card.addView(
                        reasonView
                );
            }
        }

        recordsContainer.addView(card);
    }

    // =========================================================
    // OPEN CHECK-IN PHOTO
    // =========================================================

    private void openCheckInPhoto(
            String photoUrl
    ) {

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(photoUrl)
                    );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to open check-in photo.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // CREATE TEXT VIEW
    // =========================================================

    private TextView createTextView(
            String text,
            int size,
            String color,
            boolean bold
    ) {

        TextView textView =
                new TextView(this);

        textView.setText(text);

        textView.setTextSize(size);

        textView.setTextColor(
                Color.parseColor(color)
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
    // FORMAT TIMESTAMP
    // =========================================================

    private String formatTimestamp(
            Object value
    ) {

        if (value == null) {
            return "Not checked";
        }

        Date date = null;

        if (value instanceof Timestamp) {

            date =
                    ((Timestamp) value).toDate();

        } else if (value instanceof Date) {

            date =
                    (Date) value;

        } else if (value instanceof String) {

            /*
             * Also support String values in case
             * your attendance records store
             * checkIn/checkOut as text.
             */
            return (String) value;
        }

        if (date == null) {
            return "Not checked";
        }

        return new SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
        ).format(date);
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
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }
}