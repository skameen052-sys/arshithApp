
package com.example.arshithconnectattendance;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmployeeWorkUpdateActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ImageButton btnBack;

    private TextView tvDate;
    private TextView tvProgressCount;
    private TextView tvProgressPercent;
    private TextView tvNextWindow;

    private FrameLayout progressRingContainer;

    private final View[] progressSteps = new View[5];

    private final LinearLayout[] sections = new LinearLayout[5];
    private final TextView[] statusViews = new TextView[5];
    private final Button[] sectionButtons = new Button[5];
    private final TextView[] timelineViews = new TextView[5];

    private final boolean[] submitted = new boolean[5];
    private final boolean[] saving = new boolean[5];

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable timeRunnable;

    private ProgressRingView progressRingView;

    private static final int SECTION_COUNT = 4;

    private static final int COLOR_DARK_GREEN =
            Color.rgb(6, 78, 59);

    private static final int COLOR_SUCCESS =
            Color.rgb(4, 120, 87);

    private static final int COLOR_MUTED =
            Color.rgb(100, 117, 109);

    private static final int COLOR_WHITE =
            Color.WHITE;

    private static final int COLOR_BORDER =
            Color.rgb(228, 235, 231);

    private static final int COLOR_DARK_TEXT =
            Color.rgb(15, 31, 25);

    private static final int COLOR_AMBER =
            Color.rgb(180, 83, 9);

    private static final int[] START_HOUR = {
            0, 10, 12, 15, 18
    };

    private static final int[] START_MINUTE = {
            0, 30, 30, 30, 0
    };

    private static final int[] END_HOUR = {
            0, 11, 13, 16, 18
    };

    private static final int[] END_MINUTE = {
            0, 0, 0, 0, 30
    };

    private static final String[] TITLES = {
            "",
            "Work Update 01",
            "Work Update 02",
            "Work Update 03",
            "Work Update 04"
    };

    private static final String[] WINDOWS = {
            "",
            "10:30 AM - 11:00 AM",
            "12:30 PM - 1:00 PM",
            "3:30 PM - 4:00 PM",
            "6:00 PM - 6:30 PM"
    };

    private static final String[] SCHEDULES = {
            "",
            "9:30 AM - 11:00 AM",
            "11:15 AM - 1:00 PM",
            "2:00 PM - 4:00 PM",
            "4:15 PM - 6:00 PM"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.employee_work_update);

        auth = FirebaseAuth.getInstance();

        db = FirebaseFirestore.getInstance();

        currentUser = auth.getCurrentUser();

        initializeViews();

        setupClickListeners();

        updateDate();

        createProgressRing();

        updateProgressCard();

        updateSectionStates();

        loadTodaySubmissions();

        startTimeChecker();
    }

    private void initializeViews() {

        btnBack = findViewById(R.id.btnBack);

        tvDate = findViewById(R.id.tvDate);

        tvProgressCount =
                findViewById(R.id.tvProgressCount);

        tvProgressPercent =
                findViewById(R.id.tvProgressPercent);

        tvNextWindow =
                findViewById(R.id.tvNextWindow);

        progressRingContainer =
                findViewById(R.id.progressRingContainer);

        progressSteps[1] =
                findViewById(R.id.progressStep1);

        progressSteps[2] =
                findViewById(R.id.progressStep2);

        progressSteps[3] =
                findViewById(R.id.progressStep3);

        progressSteps[4] =
                findViewById(R.id.progressStep4);

        sections[1] =
                findViewById(R.id.section1);

        sections[2] =
                findViewById(R.id.section2);

        sections[3] =
                findViewById(R.id.section3);

        sections[4] =
                findViewById(R.id.section4);

        statusViews[1] =
                findViewById(R.id.tvSection1Status);

        statusViews[2] =
                findViewById(R.id.tvSection2Status);

        statusViews[3] =
                findViewById(R.id.tvSection3Status);

        statusViews[4] =
                findViewById(R.id.tvSection4Status);

        sectionButtons[1] =
                findViewById(R.id.btnSection1);

        sectionButtons[2] =
                findViewById(R.id.btnSection2);

        sectionButtons[3] =
                findViewById(R.id.btnSection3);

        sectionButtons[4] =
                findViewById(R.id.btnSection4);

        timelineViews[1] =
                findViewById(R.id.tvTimeline1);

        timelineViews[2] =
                findViewById(R.id.tvTimeline2);

        timelineViews[3] =
                findViewById(R.id.tvTimeline3);

        timelineViews[4] =
                findViewById(R.id.tvTimeline4);
    }

    private void setupClickListeners() {

        if (btnBack != null) {

            btnBack.setOnClickListener(v -> finish());
        }

        for (int i = 1; i <= SECTION_COUNT; i++) {

            final int sectionNumber = i;

            if (sectionButtons[i] != null) {

                sectionButtons[i].setOnClickListener(
                        v -> handleSectionClick(sectionNumber)
                );
            }
        }
    }

    private void createProgressRing() {

        if (progressRingContainer == null) return;

        progressRingView = new ProgressRingView(this);

        progressRingContainer.addView(
                progressRingView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        progressRingView.setProgress(0);
    }

    private void updateDate() {

        if (tvDate != null) {

            tvDate.setText(
                    new SimpleDateFormat(
                            "EEE, dd MMM",
                            Locale.getDefault()
                    ).format(Calendar.getInstance().getTime())
            );
        }
    }

    private int getSubmittedCount() {

        int count = 0;

        for (int i = 1; i <= SECTION_COUNT; i++) {

            if (submitted[i]) {
                count++;
            }
        }

        return count;
    }

    private void updateProgressCard() {

        int count = getSubmittedCount();

        int percentage =
                (count * 100) / SECTION_COUNT;

        if (tvProgressCount != null) {

            tvProgressCount.setText(
                    count + " of " + SECTION_COUNT + " submitted"
            );
        }

        if (tvProgressPercent != null) {

            tvProgressPercent.setText(
                    percentage + "%"
            );
        }

        if (progressRingView != null) {

            progressRingView.setProgress(percentage);
        }

        for (int i = 1; i <= SECTION_COUNT; i++) {

            if (progressSteps[i] != null) {

                progressSteps[i].setBackground(
                        ContextCompat.getDrawable(
                                this,
                                submitted[i]
                                        ? R.drawable.bg_progress_step_active
                                        : R.drawable.bg_progress_step_inactive
                        )
                );
            }
        }

        updateNextWindowText();
    }

    private void updateNextWindowText() {

        if (tvNextWindow == null) return;

        int now = getCurrentMinutes();

        for (int i = 1; i <= SECTION_COUNT; i++) {

            if (submitted[i]) continue;

            int start =
                    START_HOUR[i] * 60
                            + START_MINUTE[i];

            int end =
                    END_HOUR[i] * 60
                            + END_MINUTE[i];

            if (now >= start && now < end) {

                tvNextWindow.setText(
                        "Current window closes at "
                                + formatTime(
                                END_HOUR[i],
                                END_MINUTE[i]
                        )
                );

                return;
            }

            if (now < start) {

                tvNextWindow.setText(
                        "Next window opens at "
                                + formatTime(
                                START_HOUR[i],
                                START_MINUTE[i]
                        )
                );

                return;
            }
        }

        if (getSubmittedCount() == SECTION_COUNT) {

            tvNextWindow.setText(
                    "All 4 work updates completed"
            );

        } else {

            tvNextWindow.setText(
                    "Today's submission windows are closed"
            );
        }
    }

    private String formatTime(int hour, int minute) {

        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hour);

        c.set(Calendar.MINUTE, minute);

        return new SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
        ).format(c.getTime());
    }

    private void handleSectionClick(int sectionNumber) {

        currentUser = auth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Your login session has expired. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (submitted[sectionNumber]) {

            openSubmittedUpdate(sectionNumber);

            return;
        }

        if (saving[sectionNumber]) {

            Toast.makeText(
                    this,
                    "Your work update is being submitted. Please wait.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!isSectionOpen(sectionNumber)) {

            Toast.makeText(
                    this,
                    "This section is available only from "
                            + WINDOWS[sectionNumber] + ".",
                    Toast.LENGTH_LONG
            ).show();

            updateSectionState(sectionNumber);

            return;
        }

        openWorkUpdateForm(sectionNumber);
    }

    private void loadTodaySubmissions() {

        currentUser = auth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Employee login not found. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String uid = currentUser.getUid();

        String today = getToday();

        for (int i = 1; i <= SECTION_COUNT; i++) {

            checkSubmission(uid, today, i);
        }
    }

    private void checkSubmission(
            String uid,
            String date,
            int sectionNumber
    ) {

        String documentId =
                date + "_section_" + sectionNumber;

        db.collection("workUpdates")
                .document(uid)
                .collection("dailyUpdates")
                .document(documentId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (isFinishing() || isDestroyed()) return;

                    submitted[sectionNumber] =
                            snapshot.exists();

                    updateSectionState(sectionNumber);

                    updateProgressCard();
                })
                .addOnFailureListener(e -> {

                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Could not check Work Update "
                                    + sectionNumber + ": "
                                    + getErrorMessage(e),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void startTimeChecker() {

        if (timeRunnable != null) {

            handler.removeCallbacks(timeRunnable);
        }

        timeRunnable = new Runnable() {

            @Override
            public void run() {

                if (isFinishing() || isDestroyed()) return;

                updateSectionStates();

                updateProgressCard();

                handler.postDelayed(
                        this,
                        30_000L
                );
            }
        };

        handler.post(timeRunnable);
    }

    private void updateSectionStates() {

        for (int i = 1; i <= SECTION_COUNT; i++) {

            updateSectionState(i);
        }

        updateProgressCard();
    }

    /*
     * Removes button tint and unwanted green background.
     * The original card background remains unchanged.
     */
    private void setPlainButtonBackground(Button button) {

        if (button == null) return;

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(Color.TRANSPARENT);

        background.setCornerRadius(
                dpToPx(10)
        );

        button.setBackgroundTintList(null);

        button.setBackground(background);

        button.setBackgroundTintList(null);
    }

    private void updateSectionState(int sectionNumber) {

        Button button =
                sectionButtons[sectionNumber];

        TextView status =
                statusViews[sectionNumber];

        TextView timeline =
                timelineViews[sectionNumber];

        if (button == null || status == null) return;

        /*
         * SUBMITTED
         */
        if (submitted[sectionNumber]) {

            button.setEnabled(true);

            button.setText("View  ›");

            button.setTextColor(
                    COLOR_DARK_GREEN
            );

            button.setAlpha(1f);

            // No green button background.
            setPlainButtonBackground(button);

            setStatus(
                    status,
                    "✓  Submitted",
                    COLOR_SUCCESS,
                    0xFFE3F4EB
            );

            // Green only on the submitted timeline circle.
            if (timeline != null) {

                timeline.setText("✓");

                timeline.setTextColor(
                        COLOR_WHITE
                );

                timeline.setBackground(
                        ContextCompat.getDrawable(
                                this,
                                R.drawable.bg_timeline_done
                        )
                );
            }

            return;
        }

        /*
         * SAVING
         */
        if (saving[sectionNumber]) {

            button.setEnabled(false);

            button.setText("Submitting...");

            button.setTextColor(
                    COLOR_MUTED
            );

            button.setAlpha(1f);

            setPlainButtonBackground(button);

            setStatus(
                    status,
                    "Saving...",
                    COLOR_MUTED,
                    0xFFEEF2F0
            );

            return;
        }

        /*
         * AVAILABLE
         */
        if (isSectionOpen(sectionNumber)) {

            button.setEnabled(true);

            button.setText("Submit  ›");

            button.setTextColor(
                    COLOR_DARK_GREEN
            );

            button.setAlpha(1f);

            // Transparent button background.
            setPlainButtonBackground(button);

            setStatus(
                    status,
                    "Available now",
                    COLOR_SUCCESS,
                    0xFFE3F4EB
            );

            // Neutral timeline circle until submitted.
            if (timeline != null) {

                timeline.setText("○");

                timeline.setTextColor(
                        COLOR_MUTED
                );

                timeline.setBackground(
                        ContextCompat.getDrawable(
                                this,
                                R.drawable.bg_timeline_pending
                        )
                );
            }

        } else {

            /*
             * CLOSED
             */
            button.setEnabled(false);

            button.setText("🔒 Submit");

            button.setTextColor(
                    0xFF8A9992
            );

            button.setAlpha(1f);

            GradientDrawable disabledButton =
                    new GradientDrawable();

            disabledButton.setColor(
                    0xFFEEF2F0
            );

            disabledButton.setCornerRadius(
                    dpToPx(10)
            );

            button.setBackgroundTintList(null);

            button.setBackground(
                    disabledButton
            );

            button.setBackgroundTintList(null);

            int now = getCurrentMinutes();

            int start =
                    START_HOUR[sectionNumber] * 60
                            + START_MINUTE[sectionNumber];

            if (now < start) {

                setStatus(
                        status,
                        "◷  Opens "
                                + formatStartTime(sectionNumber),
                        COLOR_AMBER,
                        0xFFFEF3C7
                );

            } else {

                setStatus(
                        status,
                        "Window closed",
                        COLOR_MUTED,
                        0xFFEEF2F0
                );
            }

            if (timeline != null) {

                timeline.setText("○");

                timeline.setTextColor(
                        COLOR_MUTED
                );

                timeline.setBackground(
                        ContextCompat.getDrawable(
                                this,
                                R.drawable.bg_timeline_pending
                        )
                );
            }
        }
    }

    private void setStatus(
            TextView view,
            String text,
            int textColor,
            int backgroundColor
    ) {

        view.setText(text);

        view.setTextColor(
                textColor
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                backgroundColor
        );

        bg.setCornerRadius(
                dpToPx(18)
        );

        view.setBackground(bg);
    }

    private boolean isSectionOpen(int sectionNumber) {

        return isTimeBetween(
                START_HOUR[sectionNumber],
                START_MINUTE[sectionNumber],
                END_HOUR[sectionNumber],
                END_MINUTE[sectionNumber]
        );
    }

    private boolean isTimeBetween(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {

        int currentMinutes =
                getCurrentMinutes();

        int startMinutes =
                startHour * 60 + startMinute;

        int endMinutes =
                endHour * 60 + endMinute;

        return currentMinutes >= startMinutes
                && currentMinutes < endMinutes;
    }

    private int getCurrentMinutes() {

        Calendar calendar =
                Calendar.getInstance();

        return calendar.get(Calendar.HOUR_OF_DAY) * 60
                + calendar.get(Calendar.MINUTE);
    }

    private void openWorkUpdateForm(int sectionNumber) {

        showUpdateDialog(
                sectionNumber,
                TITLES[sectionNumber],
                SCHEDULES[sectionNumber],
                WINDOWS[sectionNumber]
        );
    }

    private void showUpdateDialog(
            int sectionNumber,
            String title,
            String schedule,
            String window
    ) {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        int padding =
                dpToPx(24);

        layout.setPadding(
                padding,
                dpToPx(10),
                padding,
                0
        );

        TextView info =
                new TextView(this);

        info.setText(
                "Work schedule: " + schedule
                        + "\nUpdate window: " + window
        );

        info.setTextSize(14);

        info.setTextColor(
                COLOR_DARK_GREEN
        );

        info.setLineSpacing(
                dpToPx(4),
                1f
        );

        layout.addView(info);

        EditText input =
                new EditText(this);

        input.setHint(
                "Enter your work update"
        );

        input.setGravity(
                Gravity.TOP | Gravity.START
        );

        input.setMinLines(5);

        input.setPadding(
                dpToPx(14),
                dpToPx(14),
                dpToPx(14),
                dpToPx(14)
        );

        input.setBackground(
                createRoundedBackground(
                        Color.WHITE,
                        COLOR_BORDER,
                        12,
                        1
                )
        );

        input.setTextColor(
                COLOR_DARK_GREEN
        );

        input.setHintTextColor(
                COLOR_MUTED
        );

        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(180)
                );

        inputParams.topMargin =
                dpToPx(14);

        layout.addView(
                input,
                inputParams
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setView(layout)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Submit", null)
                        .create();

        dialog.setOnShowListener(unused -> {

            Button submitButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            Button cancelButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_NEGATIVE
                    );

            submitButton.setTextColor(
                    COLOR_SUCCESS
            );

            cancelButton.setTextColor(
                    COLOR_MUTED
            );

            submitButton.setOnClickListener(v -> {

                String update =
                        input.getText().toString().trim();

                if (update.isEmpty()) {

                    input.setError(
                            "Enter your work update"
                    );

                    input.requestFocus();

                    return;
                }

                currentUser =
                        auth.getCurrentUser();

                if (currentUser == null) {

                    Toast.makeText(
                            this,
                            "Employee login not found. Please log in again.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                if (submitted[sectionNumber]) {

                    Toast.makeText(
                            this,
                            "This work update has already been submitted today.",
                            Toast.LENGTH_LONG
                    ).show();

                    dialog.dismiss();

                    updateSectionState(
                            sectionNumber
                    );

                    return;
                }

                if (saving[sectionNumber]) return;

                if (!isSectionOpen(sectionNumber)) {

                    Toast.makeText(
                            this,
                            "The update window has closed.",
                            Toast.LENGTH_LONG
                    ).show();

                    dialog.dismiss();

                    updateSectionState(
                            sectionNumber
                    );

                    return;
                }

                saving[sectionNumber] = true;

                submitButton.setEnabled(false);

                submitButton.setText(
                        "Submitting..."
                );

                updateSectionState(
                        sectionNumber
                );

                saveWorkUpdate(
                        sectionNumber,
                        update,
                        dialog,
                        submitButton
                );
            });
        });

        dialog.show();
    }

    private void saveWorkUpdate(
            int sectionNumber,
            String update,
            AlertDialog dialog,
            Button submitButton
    ) {

        currentUser =
                auth.getCurrentUser();

        if (currentUser == null) {

            saving[sectionNumber] = false;

            updateSectionState(
                    sectionNumber
            );

            Toast.makeText(
                    this,
                    "Employee login not found. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        final String uid =
                currentUser.getUid();

        final String employeeEmail =
                currentUser.getEmail();

        final String today =
                getToday();

        final String documentId =
                today + "_section_" + sectionNumber;

        db.collection("workUpdates")
                .document(uid)
                .collection("dailyUpdates")
                .document(documentId)
                .get()
                .addOnSuccessListener(existing -> {

                    if (isFinishing() || isDestroyed()) {

                        saving[sectionNumber] = false;

                        return;
                    }

                    if (existing.exists()) {

                        saving[sectionNumber] = false;

                        submitted[sectionNumber] = true;

                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }

                        updateSectionState(
                                sectionNumber
                        );

                        updateProgressCard();

                        Toast.makeText(
                                this,
                                "This work update has already been submitted today.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    if (!isSectionOpen(sectionNumber)) {

                        saving[sectionNumber] = false;

                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }

                        updateSectionState(
                                sectionNumber
                        );

                        Toast.makeText(
                                this,
                                "The update window has closed.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    Map<String, Object> data =
                            new HashMap<>();

                    data.put(
                            "employeeId",
                            uid
                    );

                    data.put(
                            "employeeEmail",
                            employeeEmail
                    );

                    data.put(
                            "date",
                            today
                    );

                    data.put(
                            "section",
                            sectionNumber
                    );

                    data.put(
                            "sectionTitle",
                            TITLES[sectionNumber]
                    );

                    data.put(
                            "schedule",
                            SCHEDULES[sectionNumber]
                    );

                    data.put(
                            "updateWindow",
                            WINDOWS[sectionNumber]
                    );

                    data.put(
                            "update",
                            update
                    );

                    data.put(
                            "submittedAt",
                            Timestamp.now()
                    );

                    data.put(
                            "submittedTime",
                            new SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                            ).format(
                                    Calendar.getInstance().getTime()
                            )
                    );

                    db.collection("workUpdates")
                            .document(uid)
                            .collection("dailyUpdates")
                            .document(documentId)
                            .set(data)
                            .addOnSuccessListener(unused -> {

                                if (isFinishing() || isDestroyed()) {

                                    saving[sectionNumber] = false;

                                    return;
                                }

                                saving[sectionNumber] = false;

                                submitted[sectionNumber] = true;

                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }

                                updateSectionState(
                                        sectionNumber
                                );

                                updateProgressCard();

                                Toast.makeText(
                                        this,
                                        "Work update submitted successfully.",
                                        Toast.LENGTH_LONG
                                ).show();
                            })
                            .addOnFailureListener(e -> {

                                saving[sectionNumber] = false;

                                if (isFinishing() || isDestroyed()) return;

                                if (dialog.isShowing()) {

                                    submitButton.setEnabled(true);

                                    submitButton.setText(
                                            "Submit"
                                    );
                                }

                                updateSectionState(
                                        sectionNumber
                                );

                                Toast.makeText(
                                        this,
                                        "Failed to submit work update: "
                                                + getErrorMessage(e),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                })
                .addOnFailureListener(e -> {

                    saving[sectionNumber] = false;

                    if (isFinishing() || isDestroyed()) return;

                    if (dialog.isShowing()) {

                        submitButton.setEnabled(true);

                        submitButton.setText(
                                "Submit"
                        );
                    }

                    updateSectionState(
                            sectionNumber
                    );

                    Toast.makeText(
                            this,
                            "Could not verify the existing submission: "
                                    + getErrorMessage(e),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void openSubmittedUpdate(int sectionNumber) {

        currentUser =
                auth.getCurrentUser();

        if (currentUser == null) return;

        String documentId =
                getToday() + "_section_" + sectionNumber;

        db.collection("workUpdates")
                .document(currentUser.getUid())
                .collection("dailyUpdates")
                .document(documentId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (isFinishing() || isDestroyed()) return;

                    if (!snapshot.exists()) {

                        submitted[sectionNumber] = false;

                        updateSectionState(
                                sectionNumber
                        );

                        updateProgressCard();

                        Toast.makeText(
                                this,
                                "Submitted update could not be found.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    String update =
                            snapshot.getString("update");

                    String submittedTime =
                            snapshot.getString("submittedTime");

                    TextView content =
                            new TextView(this);

                    content.setText(
                            (update == null ? "" : update)
                                    + (submittedTime == null
                                    ? ""
                                    : "\n\nSubmitted at: "
                                    + submittedTime)
                    );

                    content.setTextColor(
                            COLOR_DARK_TEXT
                    );

                    content.setTextSize(15);

                    content.setPadding(
                            dpToPx(20),
                            dpToPx(12),
                            dpToPx(20),
                            dpToPx(12)
                    );

                    new AlertDialog.Builder(this)
                            .setTitle(
                                    TITLES[sectionNumber]
                            )
                            .setView(content)
                            .setPositiveButton(
                                    "Close",
                                    null
                            )
                            .show();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load submitted update: "
                                    + getErrorMessage(e),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private GradientDrawable createRoundedBackground(
            int fillColor,
            int strokeColor,
            int radiusDp,
            int strokeWidthDp
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setShape(
                GradientDrawable.RECTANGLE
        );

        drawable.setColor(
                fillColor
        );

        drawable.setCornerRadius(
                dpToPx(radiusDp)
        );

        if (strokeWidthDp > 0) {

            drawable.setStroke(
                    dpToPx(strokeWidthDp),
                    strokeColor
            );
        }

        return drawable;
    }

    private int dpToPx(int dp) {

        return (int) (
                dp * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    private String formatStartTime(int sectionNumber) {

        return formatTime(
                START_HOUR[sectionNumber],
                START_MINUTE[sectionNumber]
        );
    }

    private String getToday() {

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
        ).format(
                Calendar.getInstance().getTime()
        );
    }

    private String getErrorMessage(Exception e) {

        if (e == null || e.getMessage() == null) {

            return "Unknown error. Check your internet connection and Firebase configuration.";
        }

        return e.getMessage();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (auth == null) return;

        currentUser =
                auth.getCurrentUser();

        updateDate();

        updateSectionStates();

        loadTodaySubmissions();

        if (timeRunnable != null) {

            handler.removeCallbacks(
                    timeRunnable
            );

            handler.post(
                    timeRunnable
            );
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (timeRunnable != null) {

            handler.removeCallbacks(
                    timeRunnable
            );
        }
    }

    /**
     * Draws the percentage ring shown on the progress card.
     */
    public static class ProgressRingView extends View {

        private final Paint trackPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint progressPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF oval =
                new RectF();

        private int progress = 0;

        public ProgressRingView(Context context) {

            super(context);

            init();
        }

        public ProgressRingView(
                Context context,
                AttributeSet attrs
        ) {

            super(context, attrs);

            init();
        }

        private void init() {

            trackPaint.setStyle(
                    Paint.Style.STROKE
            );

            trackPaint.setStrokeWidth(
                    7f
            );

            trackPaint.setColor(
                    0x35FFFFFF
            );

            trackPaint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            progressPaint.setStyle(
                    Paint.Style.STROKE
            );

            progressPaint.setStrokeWidth(
                    7f
            );

            progressPaint.setColor(
                    Color.rgb(52, 211, 153)
            );

            progressPaint.setStrokeCap(
                    Paint.Cap.ROUND
            );
        }

        public void setProgress(int value) {

            progress = Math.max(
                    0,
                    Math.min(100, value)
            );

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            float density =
                    getResources()
                            .getDisplayMetrics()
                            .density;

            float stroke =
                    7f * density;

            float inset =
                    stroke / 2f + 2f * density;

            oval.set(
                    inset,
                    inset,
                    getWidth() - inset,
                    getHeight() - inset
            );

            trackPaint.setStrokeWidth(
                    stroke
            );

            progressPaint.setStrokeWidth(
                    stroke
            );

            canvas.drawArc(
                    oval,
                    -90,
                    360,
                    false,
                    trackPaint
            );

            if (progress > 0) {

                canvas.drawArc(
                        oval,
                        -90,
                        360f * progress / 100f,
                        false,
                        progressPaint
                );
            }
        }
    }
}
