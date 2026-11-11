package com.example.arshithconnectattendance;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.bumptech.glide.Glide;

public class AdminEmployeeProfileActivity
        extends ComponentActivity {

    // =========================================================
    // UI
    // =========================================================

    private ImageButton btnBack;

    private ShapeableImageView imgProfile;

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvRole;


    // =========================================================
    // FIREBASE
    // =========================================================

    private FirebaseFirestore firestore;

    private FirebaseStorage storage;


    // =========================================================
    // EMPLOYEE UID
    // =========================================================

    private String employeeUid;


    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_employee_profile
        );


        // ---------------------------------------------------------
        // Firebase
        // ---------------------------------------------------------

        firestore =
                FirebaseFirestore.getInstance();

        storage =
                FirebaseStorage.getInstance();


        // ---------------------------------------------------------
        // Views
        // ---------------------------------------------------------

        initializeViews();


        // ---------------------------------------------------------
        // Back
        // ---------------------------------------------------------

        btnBack.setOnClickListener(
                v -> finish()
        );


        // ---------------------------------------------------------
        // Get employee UID
        // ---------------------------------------------------------

        employeeUid =
                getIntent().getStringExtra(
                        "employeeUid"
                );


        if (employeeUid == null
                || employeeUid.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Employee information not found",
                    Toast.LENGTH_LONG
            ).show();

            finish();

            return;
        }


        // ---------------------------------------------------------
        // Load employee profile
        // ---------------------------------------------------------

        loadEmployeeProfile();
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

        tvRole =
                findViewById(
                        R.id.tvRole
                );
    }


    // =========================================================
    // LOAD EMPLOYEE PROFILE
    // =========================================================

    private void loadEmployeeProfile() {

        firestore
                .collection("employees")
                .document(employeeUid)
                .get()
                .addOnSuccessListener(
                        this::displayEmployeeProfile
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load employee profile: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }


    // =========================================================
    // DISPLAY PROFILE
    // =========================================================

    private void displayEmployeeProfile(
            DocumentSnapshot document) {

        if (!document.exists()) {

            Toast.makeText(
                    this,
                    "Employee profile not found",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        // ---------------------------------------------------------
        // Name
        // ---------------------------------------------------------

        String name =
                document.getString(
                        "name"
                );

        if (name == null
                || name.trim().isEmpty()) {

            name = "Employee";
        }

        tvName.setText(name);


        // ---------------------------------------------------------
        // Email
        // ---------------------------------------------------------

        String email =
                document.getString(
                        "email"
                );

        if (email == null
                || email.trim().isEmpty()) {

            email = "Email not available";
        }

        tvEmail.setText(email);


        // ---------------------------------------------------------
        // Role
        // ---------------------------------------------------------

        String role =
                document.getString(
                        "role"
                );

        if (role == null
                || role.trim().isEmpty()) {

            role = "Employee";
        }

        tvRole.setText(role);


        // ---------------------------------------------------------
        // Profile photo
        // ---------------------------------------------------------

        String photoUrl =
                document.getString(
                        "photoUrl"
                );


        if (photoUrl != null
                && !photoUrl.trim().isEmpty()) {

            loadPhotoFromUrl(photoUrl);

        } else {

            loadPhotoFromStorage();
        }
    }


    // =========================================================
    // LOAD PHOTO FROM FIRESTORE URL
    // =========================================================

    private void loadPhotoFromUrl(
            String photoUrl) {

        Glide.with(this)
                .load(photoUrl)
                .placeholder(
                        R.drawable.ic_profile_placeholder
                )
                .error(
                        R.drawable.ic_profile_placeholder
                )
                .centerCrop()
                .into(imgProfile);
    }


    // =========================================================
    // FALLBACK:
    // LOAD PHOTO DIRECTLY FROM STORAGE
    // =========================================================

    private void loadPhotoFromStorage() {

        StorageReference photoReference =
                storage
                        .getReference()
                        .child("profilePhotos")
                        .child(employeeUid)
                        .child("profile.jpg");


        photoReference
                .getDownloadUrl()
                .addOnSuccessListener(uri -> {

                    Glide.with(this)
                            .load(uri)
                            .placeholder(
                                    R.drawable.ic_profile_placeholder
                            )
                            .error(
                                    R.drawable.ic_profile_placeholder
                            )
                            .centerCrop()
                            .into(imgProfile);

                })
                .addOnFailureListener(e -> {

                    imgProfile.setImageResource(
                            R.drawable.ic_profile_placeholder
                    );
                });
    }
}