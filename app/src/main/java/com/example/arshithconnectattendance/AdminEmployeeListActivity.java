package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AdminEmployeeListActivity extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private Button btnBack;
    private Button btnRefresh;

    private ProgressBar progressBar;
    private TextView tvNoEmployees;
    private LinearLayout employeesContainer;

    private LinearLayout headerLayout;
    private View contentRoot;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =====================================================
        // SYSTEM BAR
        // =====================================================

        WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        Window window = getWindow();

        window.setStatusBarColor(
                Color.TRANSPARENT
        );

        window.setNavigationBarColor(
                Color.parseColor("#F5F3FA")
        );

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        // =====================================================
        // LAYOUT
        // =====================================================

        setContentView(
                R.layout.activity_admin_employees
        );

        initializeViews();

        setupSystemBars();

        setupButtons();

        // =====================================================
        // FIREBASE
        // =====================================================

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            goToAdminLogin();

            return;
        }

        verifyAdminAndLoad();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        btnBack =
                findViewById(R.id.btnBack);

        btnRefresh =
                findViewById(R.id.btnRefresh);

        progressBar =
                findViewById(R.id.progressBar);

        tvNoEmployees =
                findViewById(R.id.tvNoEmployees);

        employeesContainer =
                findViewById(R.id.employeesContainer);

        headerLayout =
                findViewById(R.id.headerLayout);

        contentRoot =
                findViewById(R.id.employeeListContent);
    }

    // =========================================================
    // SYSTEM BAR FIX
    // =========================================================

    private void setupSystemBars() {

        ViewCompat.setOnApplyWindowInsetsListener(
                headerLayout,
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    int statusBarHeight =
                            systemBars.top;

                    int headerHeight =
                            dpToPx(70)
                                    + statusBarHeight;

                    LinearLayout.LayoutParams params =
                            (LinearLayout.LayoutParams)
                                    view.getLayoutParams();

                    params.height =
                            headerHeight;

                    view.setLayoutParams(params);

                    view.setPadding(
                            dpToPx(16),
                            statusBarHeight,
                            dpToPx(16),
                            0
                    );

                    if (contentRoot != null) {

                        Insets bars =
                                insets.getInsets(
                                        WindowInsetsCompat.Type.systemBars()
                                );

                        contentRoot.setPadding(
                                0,
                                0,
                                0,
                                bars.bottom
                        );
                    }

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(
                headerLayout
        );
    }

    // =========================================================
    // DP TO PX
    // =========================================================

    private int dpToPx(int dp) {

        return Math.round(
                dp *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                v -> finish()
        );

        btnRefresh.setOnClickListener(
                v -> verifyAdminAndLoad()
        );
    }

    // =========================================================
    // ADMIN VERIFICATION
    // =========================================================

    private void verifyAdminAndLoad() {

        showLoading(true);

        if (currentUser == null) {

            goToAdminLogin();

            return;
        }

        firestore.collection("admins")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {

                        Toast.makeText(
                                this,
                                "Admin access required",
                                Toast.LENGTH_LONG
                        ).show();

                        goToAdminLogin();

                        return;
                    }

                    String role =
                            document.getString("role");

                    if (role == null ||
                            !role.equalsIgnoreCase("admin")) {

                        Toast.makeText(
                                this,
                                "Admin access required",
                                Toast.LENGTH_LONG
                        ).show();

                        goToAdminLogin();

                        return;
                    }

                    loadEmployees();
                })
                .addOnFailureListener(e -> {

                    showLoading(false);

                    Toast.makeText(
                            this,
                            "Unable to verify admin: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // LOAD EMPLOYEES
    // =========================================================

    private void loadEmployees() {

        showLoading(true);

        employeesContainer.removeAllViews();

        tvNoEmployees.setVisibility(
                View.GONE
        );

        firestore.collection("employees")
                .orderBy(
                        "name",
                        Query.Direction.ASCENDING
                )
                .get()
                .addOnSuccessListener(employeeSnapshot -> {

                    if (employeeSnapshot.isEmpty()) {

                        showLoading(false);

                        tvNoEmployees.setVisibility(
                                View.VISIBLE
                        );

                        tvNoEmployees.setText(
                                "No employees found."
                        );

                        return;
                    }

                    loadHolidaysThenAttendance(
                            employeeSnapshot
                    );
                })
                .addOnFailureListener(e -> {

                    showLoading(false);

                    Toast.makeText(
                            this,
                            "Unable to load employees: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // LOAD HOLIDAYS
    // =========================================================

    private void loadHolidaysThenAttendance(
            com.google.firebase.firestore.QuerySnapshot
                    employeeSnapshot) {

        firestore.collection("holidays")
                .get()
                .addOnSuccessListener(holidaySnapshot -> {

                    Set<String> holidayDates =
                            new HashSet<>();

                    for (DocumentSnapshot document :
                            holidaySnapshot) {

                        String holidayDate =
                                document.getString("date");

                        if (holidayDate != null &&
                                !holidayDate.trim().isEmpty()) {

                            holidayDates.add(
                                    holidayDate.trim()
                            );
                        }
                    }

                    loadAttendanceAndBuildEmployees(
                            employeeSnapshot,
                            holidayDates
                    );
                })
                .addOnFailureListener(e -> {

                    showLoading(false);

                    Toast.makeText(
                            this,
                            "Unable to load company holidays: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // LOAD ATTENDANCE
    // =========================================================

    private void loadAttendanceAndBuildEmployees(
            com.google.firebase.firestore.QuerySnapshot
                    employeeSnapshot,
            Set<String> holidayDates) {

        firestore.collectionGroup("records")
                .get()
                .addOnSuccessListener(attendanceSnapshot -> {

                    HashMap<String, Set<String>>
                            attendanceByEmail =
                            new HashMap<>();

                    HashMap<String, Set<String>>
                            attendanceByEmployeeId =
                            new HashMap<>();

                    // =================================================
                    // BUILD ATTENDANCE MAP
                    // =================================================

                    for (DocumentSnapshot record :
                            attendanceSnapshot) {

                        String date =
                                record.getString("date");

                        if (date == null ||
                                date.trim().isEmpty()) {

                            continue;
                        }

                        date = date.trim();

                        String email =
                                record.getString("email");

                        String employeeId =
                                record.getString("employeeId");

                        if (isSunday(date)) {

                            continue;
                        }

                        if (holidayDates.contains(date)) {

                            continue;
                        }

                        // =================================================
                        // EMAIL
                        // =================================================

                        if (email != null &&
                                !email.trim().isEmpty()) {

                            String normalizedEmail =
                                    email.trim()
                                            .toLowerCase(
                                                    Locale.getDefault()
                                            );

                            if (!attendanceByEmail
                                    .containsKey(
                                            normalizedEmail
                                    )) {

                                attendanceByEmail.put(
                                        normalizedEmail,
                                        new HashSet<>()
                                );
                            }

                            attendanceByEmail
                                    .get(normalizedEmail)
                                    .add(date);
                        }

                        // =================================================
                        // EMPLOYEE ID
                        // =================================================

                        if (employeeId != null &&
                                !employeeId.trim().isEmpty()) {

                            employeeId =
                                    employeeId.trim();

                            if (!attendanceByEmployeeId
                                    .containsKey(
                                            employeeId
                                    )) {

                                attendanceByEmployeeId.put(
                                        employeeId,
                                        new HashSet<>()
                                );
                            }

                            attendanceByEmployeeId
                                    .get(employeeId)
                                    .add(date);
                        }
                    }

                    employeesContainer.removeAllViews();

                    int employeeCount = 0;

                    // =================================================
                    // BUILD EMPLOYEE CARDS
                    // =================================================

                    for (DocumentSnapshot employee :
                            employeeSnapshot) {

                        employeeCount++;

                        // =================================================
                        // FIRESTORE DOCUMENT ID
                        // =================================================

                        String employeeDocumentId =
                                employee.getId();

                        // =================================================
                        // EMPLOYEE ID
                        // =================================================

                        String employeeId =
                                employee.getString(
                                        "employeeId"
                                );

                        if (employeeId == null ||
                                employeeId.trim().isEmpty()) {

                            employeeId =
                                    employeeDocumentId;
                        }

                        // =================================================
                        // NAME
                        // =================================================

                        String name =
                                employee.getString(
                                        "name"
                                );

                        if (name == null ||
                                name.trim().isEmpty()) {

                            name = "Employee";
                        }

                        // =================================================
                        // EMAIL
                        // =================================================

                        String email =
                                employee.getString(
                                        "email"
                                );

                        if (email == null) {

                            email = "";
                        }

                        // =================================================
                        // PROFILE PHOTO URL
                        // =================================================

                        String photoUrl =
                                employee.getString(
                                        "photoUrl"
                                );

                        if (photoUrl == null) {

                            photoUrl = "";
                        }

                        // =================================================
                        // ATTENDANCE
                        // =================================================

                        double attendancePercentage =
                                calculateAttendancePercentage(
                                        employee,
                                        employeeId,
                                        email,
                                        holidayDates,
                                        attendanceByEmail,
                                        attendanceByEmployeeId
                                );

                        // =================================================
                        // CREATE CARD
                        // =================================================

                        addEmployeeCard(
                                employeeDocumentId,
                                employeeId,
                                name,
                                email,
                                photoUrl,
                                attendancePercentage
                        );
                    }

                    showLoading(false);

                    if (employeeCount == 0) {

                        tvNoEmployees.setVisibility(
                                View.VISIBLE
                        );

                        tvNoEmployees.setText(
                                "No employees found."
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    showLoading(false);

                    Toast.makeText(
                            this,
                            "Unable to load attendance: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // ATTENDANCE CALCULATION
    // =========================================================

    private double calculateAttendancePercentage(
            DocumentSnapshot employee,
            String employeeId,
            String email,
            Set<String> holidayDates,
            HashMap<String, Set<String>>
                    attendanceByEmail,
            HashMap<String, Set<String>>
                    attendanceByEmployeeId) {

        Calendar startCalendar =
                getEmployeeStartDate(employee);

        Calendar today =
                Calendar.getInstance();

        clearTime(today);

        if (startCalendar.after(today)) {

            return 0.0;
        }

        Set<String> attendanceDates =
                new HashSet<>();

        // =====================================================
        // EMAIL ATTENDANCE
        // =====================================================

        if (email != null &&
                !email.trim().isEmpty()) {

            String normalizedEmail =
                    email.trim()
                            .toLowerCase(
                                    Locale.getDefault()
                            );

            Set<String> emailDates =
                    attendanceByEmail.get(
                            normalizedEmail
                    );

            if (emailDates != null) {

                attendanceDates.addAll(
                        emailDates
                );
            }
        }

        // =====================================================
        // EMPLOYEE ID ATTENDANCE
        // =====================================================

        if (employeeId != null &&
                !employeeId.trim().isEmpty()) {

            Set<String> employeeIdDates =
                    attendanceByEmployeeId.get(
                            employeeId
                    );

            if (employeeIdDates != null) {

                attendanceDates.addAll(
                        employeeIdDates
                );
            }
        }

        int totalWorkingDays = 0;

        int presentDays = 0;

        Calendar current =
                (Calendar) startCalendar.clone();

        while (!current.after(today)) {

            int dayOfWeek =
                    current.get(
                            Calendar.DAY_OF_WEEK
                    );

            String currentDate =
                    dateFormat.format(
                            current.getTime()
                    );

            boolean sunday =
                    dayOfWeek ==
                            Calendar.SUNDAY;

            boolean companyHoliday =
                    holidayDates.contains(
                            currentDate
                    );

            if (!sunday &&
                    !companyHoliday) {

                totalWorkingDays++;

                if (attendanceDates.contains(
                        currentDate
                )) {

                    presentDays++;
                }
            }

            current.add(
                    Calendar.DAY_OF_MONTH,
                    1
            );
        }

        if (totalWorkingDays == 0) {

            return 0.0;
        }

        double percentage =
                ((double) presentDays /
                        (double) totalWorkingDays)
                        * 100.0;

        return Math.min(
                100.0,
                Math.max(
                        0.0,
                        percentage
                )
        );
    }

    // =========================================================
    // EMPLOYEE START DATE
    // =========================================================

    private Calendar getEmployeeStartDate(
            DocumentSnapshot employee) {

        Calendar calendar =
                Calendar.getInstance();

        Object createdAtObject =
                employee.get("createdAt");

        if (createdAtObject instanceof Timestamp) {

            Timestamp timestamp =
                    (Timestamp) createdAtObject;

            calendar.setTime(
                    timestamp.toDate()
            );

        } else if (createdAtObject instanceof Date) {

            calendar.setTime(
                    (Date) createdAtObject
            );

        } else {

            calendar.setTime(
                    new Date()
            );
        }

        clearTime(calendar);

        return calendar;
    }

    // =========================================================
    // SUNDAY CHECK
    // =========================================================

    private boolean isSunday(
            String dateString) {

        try {

            Date date =
                    dateFormat.parse(
                            dateString
                    );

            if (date == null) {

                return false;
            }

            Calendar calendar =
                    Calendar.getInstance();

            calendar.setTime(date);

            return calendar.get(
                    Calendar.DAY_OF_WEEK
            ) == Calendar.SUNDAY;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // CLEAR TIME
    // =========================================================

    private void clearTime(
            Calendar calendar) {

        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    // =========================================================
    // EMPLOYEE CARD
    // =========================================================

    private void addEmployeeCard(
            String employeeDocumentId,
            String employeeId,
            String name,
            String email,
            String photoUrl,
            double attendancePercentage) {

        // =====================================================
        // MAIN CARD
        // =====================================================

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dpToPx(20),
                dpToPx(20),
                dpToPx(20),
                dpToPx(20)
        );

        card.setBackgroundColor(
                Color.WHITE
        );

        card.setElevation(
                dpToPx(3)
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
                dpToPx(18)
        );

        card.setLayoutParams(
                cardParams
        );

        // =====================================================
        // TOP ROW
        //
        // PROFILE PHOTO | NAME
        //               | EMAIL
        // =====================================================

        LinearLayout topRow =
                new LinearLayout(this);

        topRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        topRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        topRow.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // =====================================================
        // PROFILE IMAGE
        // =====================================================

        ImageView profileImage =
                new ImageView(this);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        dpToPx(64),
                        dpToPx(64)
                );

        imageParams.setMargins(
                0,
                0,
                dpToPx(16),
                0
        );

        profileImage.setLayoutParams(
                imageParams
        );

        profileImage.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        profileImage.setContentDescription(
                "Employee profile photo"
        );

        // =====================================================
        // PROFILE CIRCLE
        // =====================================================

        GradientDrawable imageBackground =
                new GradientDrawable();

        imageBackground.setShape(
                GradientDrawable.OVAL
        );

        imageBackground.setColor(
                Color.WHITE
        );

        imageBackground.setStroke(
                dpToPx(2),
                Color.parseColor("#7C3AED")
        );

        profileImage.setBackground(
                imageBackground
        );

        // =====================================================
        // LOAD PROFILE PHOTO
        // =====================================================

        if (photoUrl != null &&
                !photoUrl.trim().isEmpty()) {

            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(
                            R.drawable.ic_profile_placeholder
                    )
                    .error(
                            R.drawable.ic_profile_placeholder
                    )
                    .circleCrop()
                    .into(profileImage);

        } else {

            Glide.with(this)
                    .load(
                            R.drawable.ic_profile_placeholder
                    )
                    .circleCrop()
                    .into(profileImage);
        }

        topRow.addView(
                profileImage
        );

        // =====================================================
        // EMPLOYEE INFORMATION
        // =====================================================

        LinearLayout employeeInfo =
                new LinearLayout(this);

        employeeInfo.setOrientation(
                LinearLayout.VERTICAL
        );

        employeeInfo.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        employeeInfo.setLayoutParams(
                infoParams
        );

        // =====================================================
        // NAME
        // =====================================================

        TextView nameView =
                createTextView(
                        name,
                        19,
                        "#251E32",
                        true
                );

        nameView.setMaxLines(2);

        employeeInfo.addView(
                nameView
        );

        // =====================================================
        // EMAIL
        // =====================================================

        TextView emailView =
                createTextView(
                        email,
                        14,
                        "#666666",
                        false
                );

        emailView.setMaxLines(2);

        LinearLayout.LayoutParams emailParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        emailParams.setMargins(
                0,
                dpToPx(5),
                0,
                0
        );

        emailView.setLayoutParams(
                emailParams
        );

        employeeInfo.addView(
                emailView
        );

        topRow.addView(
                employeeInfo
        );

        // =====================================================
        // ADD TOP ROW
        // =====================================================

        card.addView(
                topRow
        );

        // =====================================================
        // DIVIDER
        // =====================================================

        View divider =
                new View(this);

        divider.setBackgroundColor(
                Color.parseColor("#EEEEF2")
        );

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );

        dividerParams.setMargins(
                0,
                dpToPx(16),
                0,
                dpToPx(14)
        );

        divider.setLayoutParams(
                dividerParams
        );

        card.addView(
                divider
        );

        // =====================================================
        // ATTENDANCE
        // =====================================================

        TextView attendanceView =
                createTextView(
                        "Attendance: "
                                + String.format(
                                Locale.getDefault(),
                                "%.1f%%",
                                attendancePercentage
                        ),
                        16,
                        "#7651E8",
                        true
                );

        card.addView(
                attendanceView
        );

        // =====================================================
        // PROFILE CLICK ACTION
        //
        // Both the employee card AND the profile image
        // open the SAME existing EmployeeDetailsActivity.
        // =====================================================

        View.OnClickListener openEmployeeProfile =
                v -> {

                    Intent intent =
                            new Intent(
                                    AdminEmployeeListActivity.this,
                                    EmployeeDetailsActivity.class
                            );

                    // =================================================
                    // SEND EMPLOYEE INFORMATION
                    // =================================================

                    intent.putExtra(
                            "employeeId",
                            employeeDocumentId
                    );

                    intent.putExtra(
                            "name",
                            name
                    );

                    intent.putExtra(
                            "email",
                            email
                    );

                    intent.putExtra(
                            "attendancePercentage",
                            attendancePercentage
                    );

                    // Send photo URL too.
                    // This allows EmployeeDetailsActivity
                    // to display the same profile photo.
                    intent.putExtra(
                            "photoUrl",
                            photoUrl
                    );

                    startActivity(
                            intent
                    );
                };

        // =====================================================
        // CLICK ENTIRE CARD
        // =====================================================

        card.setOnClickListener(
                openEmployeeProfile
        );

        // =====================================================
        // CLICK PROFILE PHOTO
        // =====================================================

        profileImage.setOnClickListener(
                openEmployeeProfile
        );

        // =====================================================
        // ADD CARD TO CONTAINER
        // =====================================================

        employeesContainer.addView(
                card
        );
    }

    // =========================================================
    // CREATE TEXT VIEW
    // =========================================================

    private TextView createTextView(
            String text,
            int size,
            String color,
            boolean bold) {

        TextView textView =
                new TextView(this);

        textView.setText(
                text
        );

        textView.setTextSize(
                size
        );

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
    // LOADING
    // =========================================================

    private void showLoading(
            boolean loading) {

        if (loading) {

            progressBar.setVisibility(
                    View.VISIBLE
            );

            btnRefresh.setEnabled(
                    false
            );

        } else {

            progressBar.setVisibility(
                    View.GONE
            );

            btnRefresh.setEnabled(
                    true
            );
        }
    }

    // =========================================================
    // ADMIN LOGIN
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