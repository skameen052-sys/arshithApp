package com.example.arshithconnectattendance;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class EmployeeDetailsActivity extends ComponentActivity {

    private FirebaseFirestore firestore;

    private ImageButton btnBack;

    private ImageView imgProfile;

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvAttendance;
    private TextView tvEmployeeId;

    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private String photoUrl;

    private double attendancePercentage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =====================================================
        // SYSTEM BARS
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
                Color.parseColor("#F7F5FC")
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
                R.layout.activity_employee_details
        );

        // =====================================================
        // FIREBASE
        // =====================================================

        firestore =
                FirebaseFirestore.getInstance();

        // =====================================================
        // GET INTENT DATA
        // =====================================================

        employeeId =
                getIntent().getStringExtra(
                        "employeeId"
                );

        employeeName =
                getIntent().getStringExtra(
                        "name"
                );

        employeeEmail =
                getIntent().getStringExtra(
                        "email"
                );

        photoUrl =
                getIntent().getStringExtra(
                        "photoUrl"
                );

        attendancePercentage =
                getIntent().getDoubleExtra(
                        "attendancePercentage",
                        0.0
                );

        // =====================================================
        // INITIALIZE
        // =====================================================

        initializeViews();

        setupSystemBars();

        setupBackButton();

        displayEmployeeData();

        /*
         * Load the latest profile information from Firestore.
         * This makes sure that if the employee changes
         * their profile photo/name, admin sees the latest data.
         */
        loadLatestEmployeeProfile();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        btnBack =
                findViewById(
                        R.id.btnBack
                );

        imgProfile =
                findViewById(
                        R.id.imgProfile
                );

        tvName =
                findViewById(
                        R.id.tvName
                );

        tvEmail =
                findViewById(
                        R.id.tvEmail
                );

        tvAttendance =
                findViewById(
                        R.id.tvAttendance
                );

        tvEmployeeId =
                findViewById(
                        R.id.tvEmployeeId
                );
    }

    // =========================================================
    // SYSTEM BAR
    // =========================================================

    private void setupSystemBars() {

        View root =
                findViewById(
                        R.id.employeeDetailsRoot
                );

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    view.setPadding(
                            view.getPaddingLeft(),
                            systemBars.top,
                            view.getPaddingRight(),
                            systemBars.bottom
                    );

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(root);
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    private void setupBackButton() {

        btnBack.setOnClickListener(
                v -> finish()
        );
    }

    // =========================================================
    // DISPLAY EMPLOYEE DATA
    // =========================================================

    private void displayEmployeeData() {

        // =====================================================
        // NAME
        // =====================================================

        if (employeeName == null ||
                employeeName.trim().isEmpty()) {

            tvName.setText(
                    "Employee"
            );

        } else {

            tvName.setText(
                    employeeName
            );
        }

        // =====================================================
        // EMAIL
        // =====================================================

        if (employeeEmail == null ||
                employeeEmail.trim().isEmpty()) {

            tvEmail.setText(
                    "Email not available"
            );

        } else {

            tvEmail.setText(
                    employeeEmail
            );
        }

        // =====================================================
        // EMPLOYEE ID
        // =====================================================

        if (employeeId == null ||
                employeeId.trim().isEmpty()) {

            tvEmployeeId.setText(
                    "Employee ID not available"
            );

        } else {

            tvEmployeeId.setText(
                    "Employee ID: " + employeeId
            );
        }

        // =====================================================
        // ATTENDANCE
        // =====================================================

        tvAttendance.setText(
                String.format(
                        Locale.getDefault(),
                        "%.1f%%",
                        attendancePercentage
                )
        );

        // =====================================================
        // PROFILE PHOTO
        // =====================================================

        loadProfilePhoto(photoUrl);
    }

    // =========================================================
    // LOAD PROFILE PHOTO
    // =========================================================

    private void loadProfilePhoto(
            String url) {

        if (url != null &&
                !url.trim().isEmpty()) {

            Glide.with(this)
                    .load(url)
                    .placeholder(
                            R.drawable.ic_profile_placeholder
                    )
                    .error(
                            R.drawable.ic_profile_placeholder
                    )
                    .circleCrop()
                    .into(imgProfile);

        } else {

            Glide.with(this)
                    .load(
                            R.drawable.ic_profile_placeholder
                    )
                    .circleCrop()
                    .into(imgProfile);
        }
    }

    // =========================================================
    // LOAD LATEST EMPLOYEE PROFILE
    // =========================================================

    private void loadLatestEmployeeProfile() {

        if (employeeId == null ||
                employeeId.trim().isEmpty()) {

            return;
        }

        firestore.collection("employees")
                .document(employeeId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {

                        return;
                    }

                    // =================================================
                    // LATEST NAME
                    // =================================================

                    String latestName =
                            document.getString(
                                    "name"
                            );

                    if (latestName != null &&
                            !latestName.trim().isEmpty()) {

                        employeeName =
                                latestName;

                        tvName.setText(
                                latestName
                        );
                    }

                    // =================================================
                    // LATEST EMAIL
                    // =================================================

                    String latestEmail =
                            document.getString(
                                    "email"
                            );

                    if (latestEmail != null &&
                            !latestEmail.trim().isEmpty()) {

                        employeeEmail =
                                latestEmail;

                        tvEmail.setText(
                                latestEmail
                        );
                    }

                    // =================================================
                    // LATEST PHOTO
                    // =================================================

                    String latestPhoto =
                            document.getString(
                                    "photoUrl"
                            );

                    if (latestPhoto != null &&
                            !latestPhoto.trim().isEmpty()) {

                        photoUrl =
                                latestPhoto;

                        loadProfilePhoto(
                                latestPhoto
                        );
                    }
                });
    }
}