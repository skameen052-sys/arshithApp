package com.example.arshithconnectattendance;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends ComponentActivity {

    private static final String PREFS_NAME = "ArshithConnectPrefs";
    private static final String KEY_LOGIN_TYPE = "loginType";
    private static final String TAG = "ArshithFCM";

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private View tvAdminEntry;
    private ImageButton btnTogglePassword;
    private ProgressBar progressBar;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private boolean passwordVisible = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            saveFcmToken();
                        } else {
                            Log.w(TAG, "Notification permission denied");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        checkExistingLogin();
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvAdminEntry = findViewById(R.id.tvAdminEntry);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {

        btnLogin.setOnClickListener(v -> loginUser());

        // Admin button at the top-right
        tvAdminEntry.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    AdminLoginActivity.class
            );
            startActivity(intent);
        });

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RegisterActivity.class
            );
            startActivity(intent);
        });

        btnTogglePassword.setOnClickListener(v ->
                togglePasswordVisibility());
    }

    private void checkExistingLogin() {

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String loginType =
                preferences.getString(KEY_LOGIN_TYPE, "");

        if (currentUser == null) return;

        if ("EMPLOYEE".equals(loginType)) {
            checkEmployeeApproval(currentUser, false);

        } else if ("ADMIN".equals(loginType)) {
            startActivity(new Intent(
                    MainActivity.this,
                    AdminDashboardActivity.class
            ));
            finish();
        }
    }

    private void togglePasswordVisibility() {

        int cursorPosition = etPassword.getSelectionStart();

        if (passwordVisible) {

            etPassword.setTransformationMethod(
                    PasswordTransformationMethod.getInstance());

            btnTogglePassword.setImageResource(
                    R.drawable.ic_visibility_off);

            passwordVisible = false;

        } else {

            etPassword.setTransformationMethod(
                    HideReturnsTransformationMethod.getInstance());

            btnTogglePassword.setImageResource(
                    R.drawable.ic_visibility);

            passwordVisible = true;
        }

        if (cursorPosition >= 0) {
            etPassword.setSelection(
                    Math.min(cursorPosition, etPassword.length()));
        }
    }

    private void loginUser() {

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return;
        }

        showLoading(true);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        showLoading(false);

                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed. Please try again.";

                        Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser user =
                            firebaseAuth.getCurrentUser();

                    if (user == null) {
                        showLoading(false);
                        Toast.makeText(
                                this,
                                "Unable to get user account",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    checkEmployeeApproval(user, true);
                });
    }

    // Verify employee approval in Firestore

    private void checkEmployeeApproval(
            FirebaseUser user,
            boolean showLoginSuccess) {

        if (user == null) {
            showLoading(false);
            return;
        }

        showLoading(true);

        firestore.collection("employees")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {

                    if (isFinishing() || isDestroyed()) return;

                    if (document.exists()) {
                        handleEmployeeDocument(
                                document, user, showLoginSuccess);
                    } else {
                        findEmployeeByEmail(user, showLoginSuccess);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Employee lookup failed", e);

                    if (!isFinishing() && !isDestroyed()) {
                        showLoading(false);
                        Toast.makeText(
                                this,
                                "Could not verify your account. Check your connection.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void findEmployeeByEmail(
            FirebaseUser user,
            boolean showLoginSuccess) {

        String email = user.getEmail();

        if (TextUtils.isEmpty(email)) {
            denyEmployeeAccess("Employee registration was not found.");
            return;
        }

        firestore.collection("employees")
                .whereEqualTo("email", email)
                .limit(2)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (isFinishing() || isDestroyed()) return;

                    if (snapshot.isEmpty()) {
                        denyEmployeeAccess(
                                "Employee registration was not found. Contact admin.");
                        return;
                    }

                    if (snapshot.size() > 1) {
                        showLoading(false);
                        Toast.makeText(
                                this,
                                "Multiple employee records found. Contact admin.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    DocumentSnapshot document =
                            snapshot.getDocuments().get(0);

                    handleEmployeeDocument(
                            document, user, showLoginSuccess);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Email lookup failed", e);

                    if (!isFinishing() && !isDestroyed()) {
                        showLoading(false);
                        Toast.makeText(
                                this,
                                "Unable to verify employee record.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void handleEmployeeDocument(
            DocumentSnapshot document,
            FirebaseUser user,
            boolean showLoginSuccess) {

        String storedEmail = document.getString("email");
        String authEmail = user.getEmail();

        if (!TextUtils.isEmpty(storedEmail)
                && !TextUtils.isEmpty(authEmail)
                && !storedEmail.trim().equalsIgnoreCase(authEmail.trim())) {

            denyEmployeeAccess(
                    "Employee record does not match this login.");
            return;
        }

        String status = document.getString("status");

        if (status == null) {
            denyEmployeeAccess("Your account has not been approved.");
            return;
        }

        status = status.trim();

        if ("pending".equalsIgnoreCase(status)) {
            denyEmployeeAccess(
                    "Your registration is awaiting admin approval.");
            return;
        }

        if ("rejected".equalsIgnoreCase(status)) {
            denyEmployeeAccess(
                    "Your registration was rejected. Contact admin.");
            return;
        }

        if (!"approved".equalsIgnoreCase(status)) {
            denyEmployeeAccess(
                    "Your account is not approved. Contact admin.");
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_LOGIN_TYPE, "EMPLOYEE")
                .apply();

        showLoading(false);

        if (showLoginSuccess) {
            Toast.makeText(
                    this,
                    "Login successful",
                    Toast.LENGTH_SHORT
            ).show();
        }

        askNotificationPermission();

        startActivity(new Intent(
                MainActivity.this,
                EmployeeDashboardActivity.class
        ));

        finish();
    }

    private void denyEmployeeAccess(String message) {

        showLoading(false);
        firebaseAuth.signOut();

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_LOGIN_TYPE)
                .apply();

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showLoading(boolean loading) {

        progressBar.setVisibility(
                loading ? View.VISIBLE : View.GONE);

        btnLogin.setEnabled(!loading);
        btnRegister.setEnabled(!loading);
        tvAdminEntry.setEnabled(!loading);
        btnTogglePassword.setEnabled(!loading);
    }

    private void askNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS);

            } else {
                saveFcmToken();
            }

        } else {
            saveFcmToken();
        }
    }

    private void saveFcmToken() {

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) return;

        FirebaseMessaging.getInstance()
                .getToken()
                .addOnSuccessListener(token -> {

                    if (TextUtils.isEmpty(token)) return;

                    NotificationTokenManager.saveToken(token);

                    Log.d(TAG, "FCM token saved");
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "FCM token error", e));
    }
}