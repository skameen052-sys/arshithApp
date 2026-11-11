package com.example.arshithconnectattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class AdminLoginActivity extends ComponentActivity {

    // =========================================================
    // VIEWS
    // =========================================================

    private EditText etAdminEmail;
    private EditText etAdminPassword;

    private Button btnAdminLogin;

    private ProgressBar progressBar;

    // =========================================================
    // FIREBASE
    // =========================================================

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    // =========================================================
    // SHARED PREFERENCES
    // =========================================================

    private static final String PREFS_NAME =
            "ArshithConnectPrefs";

    private static final String KEY_LOGIN_TYPE =
            "loginType";

    // =========================================================
    // LOG
    // =========================================================

    private static final String TAG =
            "ArshithAdminFCM";

    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    private final ActivityResultLauncher<String>
            notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {

                        if (isGranted) {

                            Log.d(
                                    TAG,
                                    "Admin notification permission granted."
                            );

                            saveAdminFcmToken();

                        } else {

                            Log.w(
                                    TAG,
                                    "Admin notification permission denied."
                            );
                        }
                    }
            );

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_login
        );

        // =====================================================
        // FIREBASE
        // =====================================================

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        // =====================================================
        // VIEWS
        // =====================================================

        etAdminEmail =
                findViewById(
                        R.id.etAdminEmail
                );

        etAdminPassword =
                findViewById(
                        R.id.etAdminPassword
                );

        btnAdminLogin =
                findViewById(
                        R.id.btnAdminLogin
                );

        progressBar =
                findViewById(
                        R.id.progressBar
                );

        // =====================================================
        // LOGIN BUTTON
        // =====================================================

        btnAdminLogin.setOnClickListener(
                v -> loginAdmin()
        );
    }

    // =========================================================
    // ADMIN LOGIN
    // =========================================================

    private void loginAdmin() {

        String email =
                etAdminEmail
                        .getText()
                        .toString()
                        .trim();

        String password =
                etAdminPassword
                        .getText()
                        .toString()
                        .trim();

        // =====================================================
        // VALIDATION
        // =====================================================

        if (TextUtils.isEmpty(email)) {

            etAdminEmail.setError(
                    "Enter admin email"
            );

            etAdminEmail.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(password)) {

            etAdminPassword.setError(
                    "Enter admin password"
            );

            etAdminPassword.requestFocus();

            return;
        }

        // =====================================================
        // SHOW LOADING
        // =====================================================

        progressBar.setVisibility(
                View.VISIBLE
        );

        btnAdminLogin.setEnabled(
                false
        );

        // =====================================================
        // FIREBASE AUTHENTICATION
        // =====================================================

        firebaseAuth
                .signInWithEmailAndPassword(
                        email,
                        password
                )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                progressBar.setVisibility(
                                        View.GONE
                                );

                                btnAdminLogin.setEnabled(
                                        true
                                );

                                String error =
                                        "Admin login failed";

                                if (task.getException()
                                        != null) {

                                    error =
                                            task.getException()
                                                    .getMessage();
                                }

                                Toast.makeText(
                                        AdminLoginActivity.this,
                                        error,
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            FirebaseUser user =
                                    firebaseAuth
                                            .getCurrentUser();

                            if (user == null) {

                                progressBar.setVisibility(
                                        View.GONE
                                );

                                btnAdminLogin.setEnabled(
                                        true
                                );

                                Toast.makeText(
                                        AdminLoginActivity.this,
                                        "Admin account not found.",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            // =================================================
                            // CHECK ADMIN ROLE
                            // =================================================

                            checkAdminRole(user);
                        }
                );
    }

    // =========================================================
    // CHECK ADMIN ROLE
    // =========================================================

    private void checkAdminRole(
            FirebaseUser user
    ) {

        String uid =
                user.getUid();

        firestore
                .collection("admins")
                .document(uid)
                .get()
                .addOnSuccessListener(
                        documentSnapshot -> {

                            progressBar.setVisibility(
                                    View.GONE
                            );

                            btnAdminLogin.setEnabled(
                                    true
                            );

                            // =================================================
                            // ADMIN DOCUMENT NOT FOUND
                            // =================================================

                            if (!documentSnapshot.exists()) {

                                firebaseAuth.signOut();

                                Toast.makeText(
                                        AdminLoginActivity.this,
                                        "This account is not registered as an admin.",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            // =================================================
                            // CHECK ROLE
                            // =================================================

                            String role =
                                    documentSnapshot.getString(
                                            "role"
                                    );

                            if (!"admin".equalsIgnoreCase(
                                    role
                            )) {

                                firebaseAuth.signOut();

                                Toast.makeText(
                                        AdminLoginActivity.this,
                                        "Admin access denied.",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            // =================================================
                            // ADMIN VERIFIED
                            // =================================================

                            getSharedPreferences(
                                    PREFS_NAME,
                                    MODE_PRIVATE
                            )
                                    .edit()
                                    .putString(
                                            KEY_LOGIN_TYPE,
                                            "ADMIN"
                                    )
                                    .apply();

                            // =================================================
                            // REQUEST NOTIFICATION PERMISSION
                            // =================================================

                            askAdminNotificationPermission();

                            // =================================================
                            // SAVE ADMIN FCM TOKEN
                            // =================================================

                            saveAdminFcmToken();

                            // =================================================
                            // SUCCESS
                            // =================================================

                            Toast.makeText(
                                    AdminLoginActivity.this,
                                    "Admin login successful",
                                    Toast.LENGTH_SHORT
                            ).show();

                            // =================================================
                            // OPEN ADMIN DASHBOARD
                            // =================================================

                            Intent intent =
                                    new Intent(
                                            AdminLoginActivity.this,
                                            AdminDashboardActivity.class
                                    );

                            startActivity(intent);

                            finish();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            progressBar.setVisibility(
                                    View.GONE
                            );

                            btnAdminLogin.setEnabled(
                                    true
                            );

                            Toast.makeText(
                                    AdminLoginActivity.this,
                                    "Unable to verify admin account.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // ASK ADMIN NOTIFICATION PERMISSION
    // =========================================================

    private void askAdminNotificationPermission() {

        // Android 13+
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                    )
                            != PackageManager.PERMISSION_GRANTED
            ) {

                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                );

            } else {

                saveAdminFcmToken();
            }

        } else {

            // Android 12 and below
            saveAdminFcmToken();
        }
    }

    // =========================================================
    // SAVE ADMIN FCM TOKEN
    // =========================================================

    private void saveAdminFcmToken() {

        FirebaseUser user =
                firebaseAuth.getCurrentUser();

        if (user == null) {

            Log.w(
                    TAG,
                    "Cannot save admin FCM token. User is null."
            );

            return;
        }

        String uid =
                user.getUid();

        Log.d(
                TAG,
                "Getting admin FCM token for UID: "
                        + uid
        );

        FirebaseMessaging
                .getInstance()
                .getToken()
                .addOnSuccessListener(
                        token -> {

                            if (
                                    token == null
                                            || token.trim().isEmpty()
                            ) {

                                Log.e(
                                        TAG,
                                        "Admin FCM token is empty."
                                );

                                return;
                            }

                            Log.d(
                                    TAG,
                                    "Admin FCM token received."
                            );

                            // =================================================
                            // SAVE TO ADMINS/{UID}
                            // =================================================

                            AdminNotificationTokenManager
                                    .saveToken(token);
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Log.e(
                                    TAG,
                                    "Failed to get admin FCM token: "
                                            + e.getMessage()
                            );
                        }
                );
    }
}