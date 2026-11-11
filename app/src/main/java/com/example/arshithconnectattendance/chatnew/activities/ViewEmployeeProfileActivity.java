
package com.example.arshithconnectattendance.chatnew.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.model.ChatConversation;
import com.example.arshithconnectattendance.chatnew.repository.ChatConversationRepository;
import com.example.arshithconnectattendance.chatnew.repository.ChatPresenceRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ViewEmployeeProfileActivity extends AppCompatActivity {

    public static final String EXTRA_EMPLOYEE_UID = "employee_uid";

    private ImageView imgEmployeeProfile;
    private TextView tvEmployeeName;
    private TextView tvEmployeeId;
    private TextView tvEmployeeEmail;
    private TextView tvEmployeePhone;
    private TextView tvEmployeeRole;
    private TextView tvEmployeeStatus;
    private TextView tvEmployeeDepartment;
    private TextView tvEmployeeDesignation;
    private TextView tvEmployeePresence;
    private Button btnMessage;

    private FirebaseFirestore db;
    private ChatPresenceRepository presenceRepository;
    private ListenerRegistration presenceListener;

    private String employeeUid;
    private String employeeName = "";
    private String employeeEmail = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_employee_profile);

        db = FirebaseFirestore.getInstance();
        presenceRepository = new ChatPresenceRepository();

        imgEmployeeProfile = findViewById(R.id.imgEmployeeProfile);
        tvEmployeeName = findViewById(R.id.tvEmployeeName);
        tvEmployeeId = findViewById(R.id.tvEmployeeId);
        tvEmployeeEmail = findViewById(R.id.tvEmployeeEmail);
        tvEmployeePhone = findViewById(R.id.tvEmployeePhone);
        tvEmployeeRole = findViewById(R.id.tvEmployeeRole);
        tvEmployeeStatus = findViewById(R.id.tvEmployeeStatus);
        tvEmployeeDepartment = findViewById(R.id.tvEmployeeDepartment);
        tvEmployeeDesignation = findViewById(R.id.tvEmployeeDesignation);
        tvEmployeePresence = findViewById(R.id.tvEmployeePresence);
        btnMessage = findViewById(R.id.btnMessage);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        employeeUid = getIntent().getStringExtra(EXTRA_EMPLOYEE_UID);

        if (employeeUid == null || employeeUid.trim().isEmpty()) {
            Toast.makeText(this, "Employee information not found",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentUid = FirebaseAuth.getInstance().getUid();

        if (employeeUid.equals(currentUid)) {
            btnMessage.setVisibility(View.GONE);
        } else {
            btnMessage.setOnClickListener(v -> openDirectChat());
        }

        loadEmployeeProfile();
        listenToEmployeePresence();
    }

    private void loadEmployeeProfile() {
        db.collection("employees")
                .document(employeeUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        displayEmployeeData(document);
                    } else {
                        loadChatUserProfile();
                    }
                })
                .addOnFailureListener(error -> loadChatUserProfile());
    }

    private void loadChatUserProfile() {
        db.collection("chat_users")
                .document(employeeUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        displayEmployeeData(document);
                    } else {
                        Toast.makeText(this, "Employee profile not found",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(error ->
                        Toast.makeText(this, "Unable to load profile",
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void displayEmployeeData(DocumentSnapshot document) {

        employeeName = getFirstAvailable(
                document, "name", "fullName", "employeeName", "displayName"
        );

        employeeEmail = getFirstAvailable(document, "email");

        String employeeId = getFirstAvailable(
                document, "employeeId", "employee_id"
        );

        String phone = getFirstAvailable(
                document, "phone", "phoneNumber", "mobile"
        );

        String role = getFirstAvailable(document, "role");

        String status = getFirstAvailable(
                document, "status", "accountStatus"
        );

        String department = getFirstAvailable(
                document, "department", "departmentName"
        );

        String designation = getFirstAvailable(
                document, "designation", "designationName"
        );

        String photoUrl = getFirstAvailable(document, "photoUrl");

        employeeName = showOrFallback(employeeName, "Employee");

        tvEmployeeName.setText(employeeName);
        tvEmployeeId.setText(showOrFallback(employeeId, "Not available"));
        tvEmployeeEmail.setText(showOrFallback(employeeEmail, "Not available"));
        tvEmployeePhone.setText(showOrFallback(phone, "Not available"));
        tvEmployeeRole.setText(showOrFallback(role, "Not available"));
        tvEmployeeStatus.setText(showOrFallback(status, "Not available"));
        tvEmployeeDepartment.setText(
                showOrFallback(department, "Not available")
        );
        tvEmployeeDesignation.setText(
                showOrFallback(designation, "Not available")
        );

        if (!photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .into(imgEmployeeProfile);
        } else {
            imgEmployeeProfile.setImageResource(
                    android.R.drawable.ic_menu_myplaces
            );
        }
    }

    private void listenToEmployeePresence() {

        tvEmployeePresence.setText("Checking availability...");

        presenceListener = presenceRepository.listenToPresence(
                employeeUid,
                new ChatPresenceRepository.PresenceCallback() {

                    @Override
                    public void onPresenceChanged(boolean isOnline, long lastSeen) {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        runOnUiThread(() -> {
                            if (isOnline) {
                                tvEmployeePresence.setText("Online");
                            } else if (lastSeen > 0) {
                                tvEmployeePresence.setText(
                                        "Last seen " + formatLastSeen(lastSeen)
                                );
                            } else {
                                tvEmployeePresence.setText("Offline");
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        runOnUiThread(() ->
                                tvEmployeePresence.setText(
                                        "Availability unavailable"
                                )
                        );
                    }
                }
        );
    }

    private String formatLastSeen(long timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        );

        return formatter.format(new Date(timestamp));
    }

    private String getFirstAvailable(
            DocumentSnapshot document,
            String... fieldNames
    ) {
        for (String field : fieldNames) {
            Object value = document.get(field);

            if (value != null) {
                String text = String.valueOf(value).trim();

                if (!text.isEmpty()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String showOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback
                : value;
    }

    private void openDirectChat() {

        if (employeeUid == null ||
                employeeUid.equals(FirebaseAuth.getInstance().getUid())) {
            return;
        }

        btnMessage.setEnabled(false);

        ChatConversationRepository repository =
                new ChatConversationRepository();

        repository.getOrCreateDirectConversation(
                employeeUid,
                new ChatConversationRepository.ConversationCallback() {

                    @Override
                    public void onSuccess(ChatConversation conversation) {

                        btnMessage.setEnabled(true);

                        Intent intent = new Intent(
                                ViewEmployeeProfileActivity.this,
                                ChatConversationActivity.class
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONVERSATION_ID,
                                conversation.getConversationId()
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_NAME,
                                employeeName
                        );

                        intent.putExtra(
                                ChatConversationActivity.EXTRA_CONTACT_UID,
                                employeeUid
                        );

                        startActivity(intent);
                    }

                    @Override
                    public void onError(String error) {

                        btnMessage.setEnabled(true);

                        Toast.makeText(
                                ViewEmployeeProfileActivity.this,
                                error,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (presenceListener != null) {
            presenceListener.remove();
            presenceListener = null;
        }

        super.onDestroy();
    }
}
