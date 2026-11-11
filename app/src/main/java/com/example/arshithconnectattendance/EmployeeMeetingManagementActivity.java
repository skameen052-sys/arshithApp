package com.example.arshithconnectattendance;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EmployeeMeetingManagementActivity extends AppCompatActivity {

    // ============================================================
    // MEETING ACCESS SETTINGS
    // ============================================================

    private static final long ACCESS_DURATION_MS = 5 * 60 * 1000L;
    private static final long REFRESH_INTERVAL_MS = 1000L;

    // ============================================================
    // COLORS
    // ============================================================

    private static final int STATUS_BAR_COLOR =
            Color.rgb(7, 91, 92);

    private static final int NAVIGATION_BAR_COLOR =
            Color.rgb(242, 245, 252);

    private static final int EMPTY_TEXT_COLOR =
            Color.rgb(100, 116, 139);

    // ============================================================
    // FIREBASE
    // ============================================================

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private ListenerRegistration meetingsListener;

    // ============================================================
    // UI VIEWS
    // ============================================================

    private LinearLayout meetingsContainer;
    private TextView tvMeetingCount;
    private TextView btnBack;

    // ============================================================
    // HANDLER
    // ============================================================

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean listenerStarted = false;

    // ============================================================
    // CACHED MEETINGS
    // ============================================================

    private final List<DocumentSnapshot> cachedMeetings =
            new ArrayList<>();

    // ============================================================
    // AUTOMATIC TIMER REFRESH
    // Refreshes the countdown without repeatedly querying Firestore.
    // ============================================================

    private final Runnable refreshRunnable = new Runnable() {

        @Override
        public void run() {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (meetingsContainer != null) {
                loadMeetingsViewFromCache();
            }

            handler.postDelayed(
                    this,
                    REFRESH_INTERVAL_MS
            );
        }
    };

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.employee_meeting_management);

        setupSystemBars();

        initializeViews();

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Please log in to view meetings.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        loadMeetings();
    }

    // ============================================================
    // SYSTEM BAR COLORS
    // ============================================================

    private void setupSystemBars() {

        Window window = getWindow();

        // Dark teal status bar with white icons.
        window.setStatusBarColor(STATUS_BAR_COLOR);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            int flags = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }

            window.getDecorView().setSystemUiVisibility(flags);
        }

        // Light navigation bar to match the page background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            window.setNavigationBarColor(
                    NAVIGATION_BAR_COLOR
            );
        }
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private void initializeViews() {

        meetingsContainer =
                findViewById(R.id.meetingsContainer);

        tvMeetingCount =
                findViewById(R.id.tvMeetingCount);

        btnBack =
                findViewById(R.id.btnBack);

        if (tvMeetingCount != null) {
            tvMeetingCount.setText("0 Meetings");
        }

        // Back button in the hero section.
        if (btnBack != null) {

            btnBack.setOnClickListener(v -> finish());
        }
    }

    // ============================================================
    // LOAD MEETINGS FROM FIRESTORE
    // ============================================================

    private void loadMeetings() {

        if (currentUser == null || listenerStarted) {
            return;
        }

        listenerStarted = true;

        final String employeeUid = currentUser.getUid();

        meetingsListener = firestore
                .collection("meetings")
                .addSnapshotListener((snapshots, error) -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (error != null) {

                        Toast.makeText(
                                EmployeeMeetingManagementActivity.this,
                                "Unable to load meetings: "
                                        + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();

                        if (cachedMeetings.isEmpty()) {

                            showEmptyMessage(
                                    "Unable to load meetings. Please try again."
                            );
                        }

                        return;
                    }

                    if (snapshots == null) {
                        return;
                    }

                    cachedMeetings.clear();

                    for (DocumentSnapshot document :
                            snapshots.getDocuments()) {

                        String status =
                                getString(document, "status");

                        if (status.isEmpty()) {
                            status = "Scheduled";
                        }

                        // Display supported meeting statuses only.
                        if (!isSupportedStatus(status)) {
                            continue;
                        }

                        // Only display meetings this employee can see.
                        if (employeeCanSeeMeeting(
                                document,
                                employeeUid
                        )) {

                            cachedMeetings.add(document);
                        }
                    }

                    sortMeetings(cachedMeetings);

                    loadMeetingsViewFromCache();
                });

        // Start automatic countdown refresh.
        handler.removeCallbacks(refreshRunnable);

        handler.postDelayed(
                refreshRunnable,
                REFRESH_INTERVAL_MS
        );
    }

    // ============================================================
    // DISPLAY MEETINGS
    // ============================================================

    private void loadMeetingsViewFromCache() {

        if (meetingsContainer == null) {
            return;
        }

        meetingsContainer.removeAllViews();

        int meetingCount = cachedMeetings.size();

        updateMeetingCount(meetingCount);

        if (meetingCount == 0) {

            showEmptyMessage(
                    "No meetings available right now."
            );

            return;
        }

        meetingsContainer.setVisibility(View.VISIBLE);

        for (DocumentSnapshot document : cachedMeetings) {
            addMeetingCard(document);
        }
    }

    // ============================================================
    // UPDATE MEETING COUNT
    // ============================================================

    private void updateMeetingCount(int count) {

        if (tvMeetingCount == null) {
            return;
        }

        if (count == 1) {

            tvMeetingCount.setText("1 Meeting");

        } else {

            tvMeetingCount.setText(
                    count + " Meetings"
            );
        }
    }

    // ============================================================
    // EMPTY STATE
    // ============================================================

    private void showEmptyMessage(String message) {

        if (meetingsContainer == null) {
            return;
        }

        meetingsContainer.removeAllViews();

        meetingsContainer.setVisibility(View.VISIBLE);

        TextView emptyText = new TextView(this);

        emptyText.setText(message);
        emptyText.setTextSize(15);
        emptyText.setTextColor(EMPTY_TEXT_COLOR);

        emptyText.setGravity(Gravity.CENTER);

        emptyText.setPadding(
                dp(24),
                dp(36),
                dp(24),
                dp(36)
        );

        meetingsContainer.addView(emptyText);
    }

    // ============================================================
    // SORT MEETINGS BY DATE AND TIME
    // ============================================================

    private void sortMeetings(List<DocumentSnapshot> meetings) {

        Collections.sort(
                meetings,
                new Comparator<DocumentSnapshot>() {

                    @Override
                    public int compare(
                            DocumentSnapshot first,
                            DocumentSnapshot second
                    ) {

                        String firstDate =
                                getString(first, "date");

                        String secondDate =
                                getString(second, "date");

                        int dateCompare =
                                firstDate.compareTo(secondDate);

                        if (dateCompare != 0) {
                            return dateCompare;
                        }

                        return getString(first, "time")
                                .compareTo(
                                        getString(second, "time")
                                );
                    }
                }
        );
    }

    // ============================================================
    // CHECK SUPPORTED STATUS
    // ============================================================

    private boolean isSupportedStatus(String status) {

        return "Scheduled".equalsIgnoreCase(status)
                || "Active".equalsIgnoreCase(status)
                || "Ended".equalsIgnoreCase(status);
    }

    // ============================================================
    // SAFELY READ FIRESTORE STRING
    // ============================================================

    private String getString(
            DocumentSnapshot document,
            String field
    ) {

        String value = document.getString(field);

        return value == null ? "" : value.trim();
    }

    // ============================================================
    // EMPLOYEE MEETING VISIBILITY
    // ============================================================

    private boolean employeeCanSeeMeeting(
            DocumentSnapshot document,
            String employeeUid
    ) {

        String audienceType =
                getString(document, "audienceType");

        // Support older meeting documents.
        if (audienceType.isEmpty()) {
            return true;
        }

        if ("ALL".equalsIgnoreCase(audienceType)) {
            return true;
        }

        if ("SELECTED".equalsIgnoreCase(audienceType)) {

            Object rawIds =
                    document.get("selectedEmployeeIds");

            if (!(rawIds instanceof List<?>)) {
                return false;
            }

            for (Object id : (List<?>) rawIds) {

                if (id instanceof String
                        && employeeUid.equals(id)) {

                    return true;
                }
            }

            return false;
        }

        return false;
    }

    // ============================================================
    // ADD MEETING CARD
    // ============================================================

    private void addMeetingCard(DocumentSnapshot document) {

        if (meetingsContainer == null) {
            return;
        }

        View card = LayoutInflater.from(this).inflate(
                R.layout.item_employee_meeting,
                meetingsContainer,
                false
        );

        TextView tvTitle =
                card.findViewById(R.id.tvMeetingTitle);

        TextView tvDate =
                card.findViewById(R.id.tvMeetingDate);

        TextView tvTime =
                card.findViewById(R.id.tvMeetingTime);

        TextView tvDescription =
                card.findViewById(R.id.tvMeetingDescription);

        TextView tvStatus =
                card.findViewById(R.id.tvMeetingStatus);

        Button btnRequest =
                card.findViewById(R.id.btnRequestJoin);

        String title =
                getString(document, "title");

        String date =
                getString(document, "date");

        String time =
                getString(document, "time");

        String description =
                getString(document, "description");

        String status =
                getString(document, "status");

        if (title.isEmpty()) {
            title = "Meeting";
        }

        if (date.isEmpty()) {
            date = "--";
        }

        if (time.isEmpty()) {
            time = "--";
        }

        if (description.isEmpty()) {
            description = "No description";
        }

        if (status.isEmpty()) {
            status = "Scheduled";
        }

        tvTitle.setText(title);
        tvDate.setText("Date: " + date);
        tvTime.setText("Time: " + time);

        tvDescription.setText(
                "Description: " + description
        );

        // --------------------------------------------------------
        // SCHEDULED MEETING
        // --------------------------------------------------------

        if ("Scheduled".equalsIgnoreCase(status)) {

            tvStatus.setText("Status: Scheduled");

            btnRequest.setText("WAITING FOR MEETING");

            btnRequest.setEnabled(false);

            btnRequest.setOnClickListener(null);
        }

        // --------------------------------------------------------
        // ACTIVE MEETING
        // --------------------------------------------------------

        else if ("Active".equalsIgnoreCase(status)) {

            long startedAt =
                    getStartedAtMillis(document);

            long now =
                    System.currentTimeMillis();

            if (startedAt <= 0) {

                tvStatus.setText(
                        "Status: Start time unavailable"
                );

                btnRequest.setText("ACCESS UNAVAILABLE");

                btnRequest.setEnabled(false);

                btnRequest.setOnClickListener(null);

            } else if (
                    now >= startedAt + ACCESS_DURATION_MS
            ) {

                tvStatus.setText("Status: Access expired");

                btnRequest.setText("ACCESS EXPIRED");

                btnRequest.setEnabled(false);

                btnRequest.setOnClickListener(null);

            } else {

                long remainingMs =
                        startedAt + ACCESS_DURATION_MS - now;

                long remainingSeconds =
                        (remainingMs + 999L) / 1000L;

                tvStatus.setText(
                        "Status: Active - "
                                + remainingSeconds
                                + " seconds left"
                );

                btnRequest.setText("OPEN GOOGLE MEET");

                btnRequest.setEnabled(true);

                final String meetingId =
                        document.getId();

                final long finalStartedAt =
                        startedAt;

                btnRequest.setOnClickListener(v ->
                        verifyAndJoinMeeting(
                                meetingId,
                                finalStartedAt
                        )
                );
            }
        }

        // --------------------------------------------------------
        // ENDED MEETING
        // --------------------------------------------------------

        else {

            tvStatus.setText("Status: Ended");

            btnRequest.setText("MEETING ENDED");

            btnRequest.setEnabled(false);

            btnRequest.setOnClickListener(null);
        }

        meetingsContainer.addView(card);
    }

    // ============================================================
    // READ MEETING START TIME
    // ============================================================

    private long getStartedAtMillis(
            DocumentSnapshot document
    ) {

        Object value =
                document.get("startedAt");

        if (value instanceof Timestamp) {

            return ((Timestamp) value)
                    .toDate()
                    .getTime();
        }

        if (value instanceof Number) {

            long timestamp =
                    ((Number) value).longValue();

            // Support epoch seconds and milliseconds.
            if (timestamp > 0
                    && timestamp < 10000000000L) {

                timestamp *= 1000L;
            }

            return timestamp;
        }

        return 0L;
    }

    // ============================================================
    // VERIFY MEETING ACCESS BEFORE JOINING
    // ============================================================

    private void verifyAndJoinMeeting(
            String meetingId,
            long expectedStartedAt
    ) {

        if (meetingId == null
                || meetingId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Meeting information is unavailable.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Please log in to join the meeting.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Verify the latest Firestore document before joining.
        firestore.collection("meetings")
                .document(meetingId)
                .get()
                .addOnSuccessListener(document -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!document.exists()) {

                        Toast.makeText(
                                this,
                                "This meeting is no longer available.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    // Verify employee visibility again.
                    if (!employeeCanSeeMeeting(
                            document,
                            currentUser.getUid()
                    )) {

                        Toast.makeText(
                                this,
                                "You no longer have access to this meeting.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    String latestStatus =
                            getString(document, "status");

                    long latestStartedAt =
                            getStartedAtMillis(document);

                    long now =
                            System.currentTimeMillis();

                    if (!"Active".equalsIgnoreCase(latestStatus)) {

                        Toast.makeText(
                                this,
                                "This meeting is no longer active.",
                                Toast.LENGTH_LONG
                        ).show();

                        loadMeetingsViewFromCache();

                        return;
                    }

                    if (latestStartedAt <= 0
                            || latestStartedAt != expectedStartedAt
                            || now >= latestStartedAt
                            + ACCESS_DURATION_MS) {

                        Toast.makeText(
                                this,
                                "The 5-minute meeting access period has expired.",
                                Toast.LENGTH_LONG
                        ).show();

                        loadMeetingsViewFromCache();

                        return;
                    }

                    String latestMeetingLink =
                            getString(document, "meetingLink");

                    joinMeeting(latestMeetingLink);
                })
                .addOnFailureListener(error -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            "Unable to verify meeting access. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ============================================================
    // OPEN GOOGLE MEET
    // ============================================================

    private void joinMeeting(String meetingLink) {

        if (meetingLink == null
                || meetingLink.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Google Meet link is not available.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String cleanLink = meetingLink
                .replace("\n", "")
                .replace("\r", "")
                .trim();

        if (cleanLink.startsWith("meet.google.com/")) {

            cleanLink = "https://" + cleanLink;

        } else if (
                cleanLink.startsWith("www.meet.google.com/")
        ) {

            cleanLink = "https://" + cleanLink;
        }

        Uri uri = Uri.parse(cleanLink);

        String host = uri.getHost();

        boolean validHost =
                "meet.google.com".equalsIgnoreCase(host)
                        || "www.meet.google.com"
                        .equalsIgnoreCase(host);

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !validHost
                || uri.getUserInfo() != null) {

            Toast.makeText(
                    this,
                    "Invalid Google Meet link.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {

            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            startActivity(intent);

        } catch (ActivityNotFoundException e) {

            Toast.makeText(
                    this,
                    "No app is available to open the meeting.",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to open Google Meet.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // DP CONVERTER
    // ============================================================

    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ============================================================
    // ON DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        // Stop the countdown refresh.
        handler.removeCallbacks(refreshRunnable);

        // Remove the Firestore listener.
        if (meetingsListener != null) {

            meetingsListener.remove();

            meetingsListener = null;
        }

        listenerStarted = false;

        super.onDestroy();
    }
}