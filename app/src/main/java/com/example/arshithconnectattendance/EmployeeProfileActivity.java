package com.example.arshithconnectattendance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class EmployeeProfileActivity extends AppCompatActivity {

    // UI
    private ImageButton btnBack;
    private ImageView imgProfile;
    private Button btnChoosePhoto;
    private Button btnSaveProfile;
    private MaterialButton btnLogout;
    private EditText etProfileName;
    private TextView tvProfileEmail;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;

    // Image
    private Uri selectedImageUri;
    private Uri croppedImageUri;

    // Gallery launcher
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        selectedImageUri = uri;
                        startImageCrop(uri);
                    }
            );

    // Image crop launcher
    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {

                            Uri resultUri =
                                    UCrop.getOutput(result.getData());

                            if (resultUri != null) {
                                croppedImageUri = resultUri;
                                imgProfile.setImageURI(croppedImageUri);

                                Toast.makeText(
                                        EmployeeProfileActivity.this,
                                        "Photo cropped successfully",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                        } else if (result.getResultCode()
                                == UCrop.RESULT_ERROR) {

                            if (result.getData() != null) {
                                Throwable cropError =
                                        UCrop.getError(result.getData());

                                String message = "Unable to crop photo";

                                if (cropError != null
                                        && cropError.getMessage() != null) {
                                    message = cropError.getMessage();
                                }

                                Toast.makeText(
                                        EmployeeProfileActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    }
            );

    // ON CREATE
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_employee_profile);

        initializeFirebase();
        initializeViews();
        setupClicks();
        loadProfile();
    }

    // INITIALIZE FIREBASE
    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = auth.getCurrentUser();
    }

    // INITIALIZE VIEWS
    private void initializeViews() {

        btnBack = findViewById(R.id.btnBack);
        imgProfile = findViewById(R.id.imgProfile);
        btnChoosePhoto = findViewById(R.id.btnChoosePhoto);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);
        etProfileName = findViewById(R.id.etProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
    }

    // CLICK EVENTS
    private void setupClicks() {

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Select profile photo
        btnChoosePhoto.setOnClickListener(v ->
                galleryLauncher.launch("image/*")
        );

        // Save profile
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Logout
        btnLogout.setOnClickListener(v -> logoutEmployee());
    }

    // LOGOUT EMPLOYEE
    private void logoutEmployee() {

        // Sign out the currently authenticated Firebase user.
        auth.signOut();

        // Clear the activity stack and open the login screen.
        Intent intent = new Intent(
                EmployeeProfileActivity.this,
                MainActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    // START IMAGE CROP
    private void startImageCrop(Uri sourceUri) {

        String fileName =
                "profile_crop_" + System.currentTimeMillis() + ".jpg";

        Uri destinationUri = Uri.fromFile(
                new File(getCacheDir(), fileName)
        );

        UCrop.Options options = new UCrop.Options();

        options.setToolbarTitle("Crop Profile Photo");

        options.setToolbarColor(
                android.graphics.Color.rgb(11, 143, 104)
        );

        options.setActiveControlsWidgetColor(
                android.graphics.Color.rgb(11, 143, 104)
        );

        // Circular crop overlay
        options.setCircleDimmedLayer(true);

        // Crop guidelines
        options.setShowCropGrid(true);
        options.setHideBottomControls(false);

        // JPEG output
        options.setCompressionFormat(
                android.graphics.Bitmap.CompressFormat.JPEG
        );

        options.setCompressionQuality(90);

        // Start cropping
        UCrop crop = UCrop.of(sourceUri, destinationUri);

        crop.withAspectRatio(1, 1);
        crop.withMaxResultSize(1000, 1000);
        crop.withOptions(options);

        crop.start(this, cropLauncher);
    }

    // LOAD PROFILE
    private void loadProfile() {

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "User is not logged in",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        String uid = currentUser.getUid();
        String email = currentUser.getEmail();

        // Display email
        if (email != null) {
            tvProfileEmail.setText(email);
        } else {
            tvProfileEmail.setText("");
        }

        // Load Firebase Authentication display name
        if (currentUser.getDisplayName() != null
                && !currentUser.getDisplayName().trim().isEmpty()) {

            etProfileName.setText(currentUser.getDisplayName());
        }

        // Load employee profile from Firestore
        db.collection("employees")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    String name = documentSnapshot.getString("name");
                    String photoUrl =
                            documentSnapshot.getString("photoUrl");

                    if (name != null && !name.trim().isEmpty()) {
                        etProfileName.setText(name);
                    }

                    if (photoUrl != null
                            && !photoUrl.trim().isEmpty()) {

                        loadProfileImage(photoUrl);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Unable to load profile",
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // LOAD PROFILE IMAGE FROM FIREBASE
    private void loadProfileImage(String photoUrl) {

        try {
            com.bumptech.glide.Glide
                    .with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .centerCrop()
                    .into(imgProfile);

        } catch (Exception e) {
            imgProfile.setImageResource(
                    R.drawable.ic_profile_placeholder
            );
        }
    }

    // SAVE PROFILE
    private void saveProfile() {

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "User is not logged in",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String name = etProfileName
                .getText()
                .toString()
                .trim();

        if (TextUtils.isEmpty(name)) {

            etProfileName.setError("Enter your name");
            etProfileName.requestFocus();

            return;
        }

        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("SAVING...");

        String uid = currentUser.getUid();
        String email = currentUser.getEmail();

        // Upload a new photo if one was selected and cropped.
        if (croppedImageUri != null) {

            uploadProfilePhoto(
                    uid,
                    name,
                    email,
                    croppedImageUri
            );

        } else {

            // Save profile data without changing the existing photo.
            saveProfileData(
                    uid,
                    name,
                    email,
                    null
            );
        }
    }

    // UPLOAD PROFILE PHOTO
    private void uploadProfilePhoto(
            String uid,
            String name,
            String email,
            Uri imageUri
    ) {

        StorageReference profileReference = storage
                .getReference()
                .child("profilePhotos")
                .child(uid)
                .child("profile.jpg");

        profileReference
                .putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->

                        profileReference
                                .getDownloadUrl()
                                .addOnSuccessListener(downloadUri ->

                                        saveProfileData(
                                                uid,
                                                name,
                                                email,
                                                downloadUri.toString()
                                        )
                                )
                                .addOnFailureListener(e -> {

                                    resetSaveButton();

                                    Toast.makeText(
                                            this,
                                            "Unable to get photo URL",
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                )
                .addOnFailureListener(e -> {

                    resetSaveButton();

                    Toast.makeText(
                            this,
                            "Photo upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // SAVE PROFILE TO FIRESTORE
    private void saveProfileData(
            String uid,
            String name,
            String email,
            String photoUrl
    ) {

        Map<String, Object> profile = new HashMap<>();

        profile.put("uid", uid);
        profile.put("name", name);
        profile.put("email", email);
        profile.put("role", "employee");

        profile.put(
                "updatedAt",
                com.google.firebase.firestore.FieldValue.serverTimestamp()
        );

        // Only update photoUrl when a new photo was uploaded.
        if (photoUrl != null) {
            profile.put("photoUrl", photoUrl);
        }

        db.collection("employees")
                .document(uid)
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        updateAuthDisplayName(name)
                )
                .addOnFailureListener(e -> {

                    resetSaveButton();

                    Toast.makeText(
                            this,
                            "Profile save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // UPDATE FIREBASE AUTH DISPLAY NAME
    private void updateAuthDisplayName(String name) {

        if (currentUser == null) {
            resetSaveButton();
            return;
        }

        currentUser.updateProfile(
                new com.google.firebase.auth.UserProfileChangeRequest
                        .Builder()
                        .setDisplayName(name)
                        .build()
        ).addOnCompleteListener(task -> {

            resetSaveButton();

            // The profile data has already been saved to Firestore.
            if (task.isSuccessful()) {

                Toast.makeText(
                        this,
                        "Profile saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                // Clear temporary crop reference after successful save.
                croppedImageUri = null;
                selectedImageUri = null;

            } else {

                Toast.makeText(
                        this,
                        "Profile saved, but display name could not be updated",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    // RESET SAVE BUTTON
    private void resetSaveButton() {

        btnSaveProfile.setEnabled(true);
        btnSaveProfile.setText("SAVE PROFILE");
    }
}