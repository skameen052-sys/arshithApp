package com.example.arshithconnectattendance;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class EmployeeAnnouncementsActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;

    private LinearLayout announcementsContainer;

    private TextView tvNoAnnouncements;
    private TextView tvEmptyTitle;
    private TextView tvAnnouncementCount;

    private View announcementEmptyState;
    private View btnAnnouncementBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_employee_announcements);

        firestore = FirebaseFirestore.getInstance();

        initializeViews();

        loadAnnouncements();
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private void initializeViews() {

        announcementsContainer =
                findViewById(R.id.announcementsContainer);

        tvNoAnnouncements =
                findViewById(R.id.tvNoAnnouncements);

        tvEmptyTitle =
                findViewById(R.id.tvEmptyTitle);

        tvAnnouncementCount =
                findViewById(R.id.tvAnnouncementCount);

        announcementEmptyState =
                findViewById(R.id.announcementEmptyState);

        btnAnnouncementBack =
                findViewById(R.id.btnAnnouncementBack);

        // Back button

        if (btnAnnouncementBack != null) {

            btnAnnouncementBack.setOnClickListener(v -> finish());

        }

        // Initial state: no hardcoded announcements are displayed.

        if (announcementsContainer != null) {

            announcementsContainer.removeAllViews();

            announcementsContainer.setVisibility(View.GONE);

        }

        if (announcementEmptyState != null) {

            announcementEmptyState.setVisibility(View.GONE);

        }

        if (tvAnnouncementCount != null) {

            tvAnnouncementCount.setText("0 Updates");

        }
    }

    // ============================================================
    // LOAD ANNOUNCEMENTS FROM FIREBASE
    // ============================================================

    private void loadAnnouncements() {

        firestore.collection("announcements")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (announcementsContainer == null) {
                        return;
                    }

                    // Remove any previously displayed cards.

                    announcementsContainer.removeAllViews();

                    List<DocumentSnapshot> documents =
                            new ArrayList<>(
                                    querySnapshot.getDocuments()
                            );

                    // Sort newest announcements first.

                    Collections.sort(
                            documents,
                            (first, second) -> Long.compare(
                                    getCreatedAtMillis(
                                            second.get("createdAt")
                                    ),
                                    getCreatedAtMillis(
                                            first.get("createdAt")
                                    )
                            )
                    );

                    int announcementCount = documents.size();

                    // Update announcement count.

                    if (tvAnnouncementCount != null) {

                        tvAnnouncementCount.setText(
                                announcementCount
                                        + (announcementCount == 1
                                        ? " Update"
                                        : " Updates")
                        );

                    }

                    // =================================================
                    // NO ANNOUNCEMENTS
                    // =================================================

                    if (documents.isEmpty()) {

                        announcementsContainer.setVisibility(
                                View.GONE
                        );

                        showEmptyState(
                                "No announcements yet",
                                "Company updates and notices will appear here."
                        );

                        return;
                    }

                    // =================================================
                    // ANNOUNCEMENTS EXIST
                    // =================================================

                    hideEmptyState();

                    announcementsContainer.setVisibility(
                            View.VISIBLE
                    );

                    // Display only records retrieved from Firebase.

                    for (DocumentSnapshot document : documents) {

                        addAnnouncementCard(document);

                    }

                })
                .addOnFailureListener(e -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (announcementsContainer != null) {

                        announcementsContainer.removeAllViews();

                        announcementsContainer.setVisibility(
                                View.GONE
                        );

                    }

                    if (tvAnnouncementCount != null) {

                        tvAnnouncementCount.setText("0 Updates");

                    }

                    // Do not show "No announcements yet" for a
                    // network or Firebase loading error.

                    showEmptyState(
                            "Unable to load announcements",
                            "Please check your internet connection and try again."
                    );

                    Toast.makeText(
                            EmployeeAnnouncementsActivity.this,
                            "Unable to load announcements. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ============================================================
    // SHOW EMPTY STATE
    // ============================================================

    private void showEmptyState(
            String title,
            String message
    ) {

        if (tvEmptyTitle != null) {

            tvEmptyTitle.setText(title);

        }

        if (tvNoAnnouncements != null) {

            tvNoAnnouncements.setText(message);

        }

        if (announcementEmptyState != null) {

            announcementEmptyState.setVisibility(
                    View.VISIBLE
            );

        }
    }

    // ============================================================
    // HIDE EMPTY STATE
    // ============================================================

    private void hideEmptyState() {

        if (announcementEmptyState != null) {

            announcementEmptyState.setVisibility(
                    View.GONE
            );

        }
    }

    // ============================================================
    // SAFELY READ CREATED-AT FOR SORTING
    // ============================================================

    private long getCreatedAtMillis(Object createdAtValue) {

        if (createdAtValue instanceof Timestamp) {

            return ((Timestamp) createdAtValue)
                    .toDate()
                    .getTime();

        }

        if (createdAtValue instanceof Date) {

            return ((Date) createdAtValue).getTime();

        }

        if (createdAtValue instanceof Number) {

            long timestamp =
                    ((Number) createdAtValue).longValue();

            // Convert seconds into milliseconds if necessary.

            if (timestamp > 0 && timestamp < 10000000000L) {

                timestamp *= 1000L;

            }

            return timestamp;
        }

        if (createdAtValue instanceof String) {

            String value =
                    ((String) createdAtValue).trim();

            if (value.isEmpty()) {
                return 0L;
            }

            // Check numeric timestamps first.

            try {

                long timestamp = Long.parseLong(value);

                if (timestamp > 0 && timestamp < 10000000000L) {

                    timestamp *= 1000L;

                }

                return timestamp;

            } catch (NumberFormatException ignored) {

                // Continue with date parsing.

            }

            String[] formats = {

                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd",
                    "dd MMM yyyy, hh:mm a",
                    "dd MMM yyyy"

            };

            for (String format : formats) {

                try {

                    SimpleDateFormat parser =
                            new SimpleDateFormat(
                                    format,
                                    Locale.getDefault()
                            );

                    parser.setLenient(false);

                    if (format.endsWith("'Z'")) {

                        parser.setTimeZone(
                                TimeZone.getTimeZone("UTC")
                        );

                    }

                    Date parsedDate = parser.parse(value);

                    if (parsedDate != null) {

                        return parsedDate.getTime();

                    }

                } catch (java.text.ParseException ignored) {

                    // Try the next supported date format.

                }
            }
        }

        // Missing or invalid dates are treated as oldest.

        return 0L;
    }

    // ============================================================
    // ADD ANNOUNCEMENT CARD
    // ============================================================

    private void addAnnouncementCard(
            DocumentSnapshot document
    ) {

        // ========================================================
        // CARD BACKGROUND
        // ========================================================

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable cardBackground =
                new GradientDrawable();

        cardBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        cardBackground.setColor(Color.WHITE);

        cardBackground.setCornerRadius(dp(18));

        cardBackground.setStroke(
                dp(1),
                0xFFE4EAF3
        );

        card.setBackground(cardBackground);

        card.setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
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
                dp(16)
        );

        card.setLayoutParams(cardParams);

        card.setElevation(dp(3));

        // ========================================================
        // ANNOUNCEMENT TYPE
        // ========================================================

        TextView tvType = new TextView(this);

        String type = document.getString("type");

        if (type == null || type.trim().isEmpty()) {

            type = "General";

        }

        tvType.setText(
                type.toUpperCase(Locale.getDefault())
        );

        tvType.setTextColor(0xFF7651E8);

        tvType.setTextSize(12);

        tvType.setTypeface(
                null,
                Typeface.BOLD
        );

        card.addView(tvType);

        // ========================================================
        // ANNOUNCEMENT TITLE
        // ========================================================

        TextView tvTitle = new TextView(this);

        String title = document.getString("title");

        if (title == null || title.trim().isEmpty()) {

            title = "Announcement";

        }

        tvTitle.setText(title);

        tvTitle.setTextColor(0xFF241E31);

        tvTitle.setTextSize(19);

        tvTitle.setTypeface(
                null,
                Typeface.BOLD
        );

        tvTitle.setSingleLine(false);

        tvTitle.setMaxLines(3);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(
                0,
                dp(7),
                0,
                0
        );

        tvTitle.setLayoutParams(titleParams);

        card.addView(tvTitle);

        // ========================================================
        // ANNOUNCEMENT MESSAGE
        // ========================================================

        TextView tvMessage = new TextView(this);

        String message = document.getString("message");

        if (message == null) {

            message = "";

        }

        tvMessage.setText(message);

        tvMessage.setTextColor(0xFF5E576A);

        tvMessage.setTextSize(14);

        tvMessage.setSingleLine(false);

        tvMessage.setMaxLines(10);

        tvMessage.setLineSpacing(
                dp(3),
                1.1f
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        tvMessage.setLayoutParams(messageParams);

        card.addView(tvMessage);

        // ========================================================
        // ANNOUNCEMENT DATE
        // ========================================================

        TextView tvDate = new TextView(this);

        Object createdAtValue =
                document.get("createdAt");

        if (createdAtValue instanceof Timestamp) {

            tvDate.setText(
                    formatDate(
                            ((Timestamp) createdAtValue).toDate()
                    )
            );

        } else if (createdAtValue instanceof Date) {

            tvDate.setText(
                    formatDate(
                            (Date) createdAtValue
                    )
            );

        } else if (createdAtValue instanceof String) {

            tvDate.setText(
                    (String) createdAtValue
            );

        } else if (createdAtValue instanceof Number) {

            long timestamp =
                    ((Number) createdAtValue).longValue();

            if (timestamp > 0 && timestamp < 10000000000L) {

                timestamp *= 1000L;

            }

            tvDate.setText(
                    formatDate(
                            new Date(timestamp)
                    )
            );

        } else {

            tvDate.setText("");

        }

        tvDate.setTextColor(0xFF918A9B);

        tvDate.setTextSize(12);

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        tvDate.setLayoutParams(dateParams);

        card.addView(tvDate);

        // ========================================================
        // ADD CARD TO FIREBASE CONTAINER
        // ========================================================

        if (announcementsContainer != null) {

            announcementsContainer.addView(card);

        }
    }

    // ============================================================
    // DP CONVERTER
    // ============================================================

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // ============================================================
    // FORMAT DATE
    // ============================================================

    private String formatDate(Date date) {

        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(date);
    }

    // ============================================================
    // REFRESH WHEN RETURNING TO THIS SCREEN
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();

        // Refresh the list whenever the employee opens or
        // returns to the announcements screen.

        loadAnnouncements();
    }
}