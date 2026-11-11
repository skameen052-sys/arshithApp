package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.Timestamp;

public class AdminMeetingManagementActivity extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private EditText etMeetingTitle;
    private EditText etMeetingDate;
    private EditText etMeetingTime;
    private EditText etMeetingDescription;
    private EditText etMeetingLink;

    private Spinner spinnerMeetingAudience;

    private TextView tvSelectedMembersTitle;

    private LinearLayout selectedMembersContainer;
    private LinearLayout meetingsContainer;

    private Button btnScheduleMeeting;

    private Calendar selectedDate =
            Calendar.getInstance();

    private final ArrayList<EmployeeInfo> employeeList =
            new ArrayList<>();

    private final LinkedHashMap<String, Boolean> selectedEmployees =
            new LinkedHashMap<>();

    // =========================================================
    // EMPLOYEE MODEL
    // =========================================================

    private static class EmployeeInfo {

        String uid;
        String name;
        String email;

        EmployeeInfo(
                String uid,
                String name,
                String email
        ) {
            this.uid = uid;
            this.name = name;
            this.email = email;
        }
    }

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_meeting_management
        );

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        initializeViews();

        setupDatePicker();

        setupTimePicker();

        setupAudienceSpinner();

        setupScheduleButton();

        loadEmployees();

        loadMeetings();
    }

    // =========================================================
    // INITIALIZE
    // =========================================================

    private void initializeViews() {

        etMeetingTitle =
                findViewById(
                        R.id.etMeetingTitle
                );

        etMeetingDate =
                findViewById(
                        R.id.etMeetingDate
                );

        etMeetingTime =
                findViewById(
                        R.id.etMeetingTime
                );

        etMeetingDescription =
                findViewById(
                        R.id.etMeetingDescription
                );

        etMeetingLink =
                findViewById(
                        R.id.etMeetingLink
                );

        spinnerMeetingAudience =
                findViewById(
                        R.id.spinnerMeetingAudience
                );

        tvSelectedMembersTitle =
                findViewById(
                        R.id.tvSelectedMembersTitle
                );

        selectedMembersContainer =
                findViewById(
                        R.id.selectedMembersContainer
                );

        btnScheduleMeeting =
                findViewById(
                        R.id.btnScheduleMeeting
                );

        meetingsContainer =
                findViewById(
                        R.id.meetingsContainer
                );
    }

    // =========================================================
    // DATE PICKER
    // =========================================================

    private void setupDatePicker() {

        etMeetingDate.setOnClickListener(v -> {

            Calendar calendar =
                    Calendar.getInstance();

            DatePickerDialog dialog =
                    new DatePickerDialog(
                            this,

                            (view, year, month, dayOfMonth) -> {

                                selectedDate.set(
                                        Calendar.YEAR,
                                        year
                                );

                                selectedDate.set(
                                        Calendar.MONTH,
                                        month
                                );

                                selectedDate.set(
                                        Calendar.DAY_OF_MONTH,
                                        dayOfMonth
                                );

                                String date =
                                        String.format(
                                                Locale.getDefault(),
                                                "%04d-%02d-%02d",
                                                year,
                                                month + 1,
                                                dayOfMonth
                                        );

                                etMeetingDate.setText(
                                        date
                                );
                            },

                            calendar.get(
                                    Calendar.YEAR
                            ),

                            calendar.get(
                                    Calendar.MONTH
                            ),

                            calendar.get(
                                    Calendar.DAY_OF_MONTH
                            )
                    );

            dialog.show();
        });
    }

    // =========================================================
    // TIME PICKER
    // =========================================================

    private void setupTimePicker() {

        etMeetingTime.setOnClickListener(v -> {

            Calendar calendar =
                    Calendar.getInstance();

            TimePickerDialog dialog =
                    new TimePickerDialog(
                            this,

                            (view, hourOfDay, minute) -> {

                                Calendar time =
                                        Calendar.getInstance();

                                time.set(
                                        Calendar.HOUR_OF_DAY,
                                        hourOfDay
                                );

                                time.set(
                                        Calendar.MINUTE,
                                        minute
                                );

                                String formattedTime =
                                        new SimpleDateFormat(
                                                "hh:mm a",
                                                Locale.getDefault()
                                        ).format(
                                                time.getTime()
                                        );

                                etMeetingTime.setText(
                                        formattedTime
                                );
                            },

                            calendar.get(
                                    Calendar.HOUR_OF_DAY
                            ),

                            calendar.get(
                                    Calendar.MINUTE
                            ),

                            false
                    );

            dialog.show();
        });
    }

    // =========================================================
    // AUDIENCE DROPDOWN
    // =========================================================

    private void setupAudienceSpinner() {

        String[] options = {
                "All Employees",
                "Selected Members"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        options
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerMeetingAudience.setAdapter(
                adapter
        );

        spinnerMeetingAudience.setSelection(
                0
        );

        spinnerMeetingAudience.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        if (position == 1) {

                            tvSelectedMembersTitle.setVisibility(
                                    View.VISIBLE
                            );

                            selectedMembersContainer.setVisibility(
                                    View.VISIBLE
                            );

                            displayEmployeeCheckboxes();

                        } else {

                            tvSelectedMembersTitle.setVisibility(
                                    View.GONE
                            );

                            selectedMembersContainer.setVisibility(
                                    View.GONE
                            );
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                    }
                }
        );
    }

    // =========================================================
    // LOAD EMPLOYEES
    // =========================================================

    private void loadEmployees() {

        employeeList.clear();

        // First try the employees collection.
        firestore
                .collection("employees")
                .get()
                .addOnSuccessListener(
                        snapshot -> {

                            for (
                                    DocumentSnapshot document :
                                    snapshot.getDocuments()
                            ) {

                                addEmployeeFromDocument(
                                        document
                                );
                            }

                            // If employees collection has
                            // no records, also try users.
                            if (employeeList.isEmpty()) {

                                loadEmployeesFromUsers();

                            } else {

                                displayEmployeeCheckboxes();
                            }
                        }
                )
                .addOnFailureListener(
                        e -> loadEmployeesFromUsers()
                );
    }

    // =========================================================
    // LOAD FROM USERS
    // =========================================================

    private void loadEmployeesFromUsers() {

        firestore
                .collection("users")
                .get()
                .addOnSuccessListener(
                        snapshot -> {

                            for (
                                    DocumentSnapshot document :
                                    snapshot.getDocuments()
                            ) {

                                if (
                                        firebaseAuth.getCurrentUser() != null
                                                &&
                                                document.getId().equals(
                                                        firebaseAuth
                                                                .getCurrentUser()
                                                                .getUid()
                                                )
                                ) {
                                    continue;
                                }

                                addEmployeeFromDocument(
                                        document
                                );
                            }

                            displayEmployeeCheckboxes();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load employees: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // ADD EMPLOYEE
    // =========================================================

    private void addEmployeeFromDocument(
            DocumentSnapshot document
    ) {

        String uid =
                document.getId();

        if (
                firebaseAuth.getCurrentUser() != null
                        &&
                        uid.equals(
                                firebaseAuth
                                        .getCurrentUser()
                                        .getUid()
                        )
        ) {
            return;
        }

        String name =
                document.getString("name");

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {

            name =
                    document.getString(
                            "employeeName"
                    );
        }

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {

            name =
                    document.getString(
                            "fullName"
                    );
        }

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {

            name =
                    document.getString(
                            "displayName"
                    );
        }

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {

            name = "Employee";
        }

        String email =
                document.getString(
                        "email"
                );

        if (
                email == null ||
                        email.trim().isEmpty()
        ) {

            email = "--";
        }

        // Prevent duplicates if the same UID
        // exists in both collections.

        for (
                EmployeeInfo existing :
                employeeList
        ) {

            if (
                    existing.uid.equals(uid)
            ) {
                return;
            }
        }

        employeeList.add(
                new EmployeeInfo(
                        uid,
                        name,
                        email
                )
        );

        if (
                !selectedEmployees.containsKey(uid)
        ) {

            selectedEmployees.put(
                    uid,
                    false
            );
        }
    }

    // =========================================================
    // DISPLAY CHECKBOXES
    // =========================================================

    private void displayEmployeeCheckboxes() {

        if (
                selectedMembersContainer == null
        ) {
            return;
        }

        selectedMembersContainer.removeAllViews();

        if (employeeList.isEmpty()) {

            TextView empty =
                    new TextView(this);

            empty.setText(
                    "No employees found."
            );

            empty.setTextSize(14);

            empty.setTextColor(
                    0xFF777777
            );

            empty.setPadding(
                    12,
                    15,
                    12,
                    15
            );

            selectedMembersContainer.addView(
                    empty
            );

            return;
        }

        for (
                EmployeeInfo employee :
                employeeList
        ) {

            CheckBox checkBox =
                    new CheckBox(this);

            checkBox.setText(
                    employee.name
                            + "\n"
                            + employee.email
            );

            checkBox.setTextSize(
                    15
            );

            checkBox.setTextColor(
                    0xFF251E32
            );

            Boolean checked =
                    selectedEmployees.get(
                            employee.uid
                    );

            checkBox.setChecked(
                    checked != null &&
                            checked
            );

            checkBox.setPadding(
                    8,
                    10,
                    8,
                    10
            );

            checkBox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {

                        selectedEmployees.put(
                                employee.uid,
                                isChecked
                        );
                    }
            );

            selectedMembersContainer.addView(
                    checkBox
            );
        }
    }

    // =========================================================
    // SCHEDULE BUTTON
    // =========================================================

    private void setupScheduleButton() {

        btnScheduleMeeting.setOnClickListener(
                v -> scheduleMeeting()
        );
    }

    // =========================================================
    // SCHEDULE MEETING
    // =========================================================

    private void scheduleMeeting() {

        String title =
                etMeetingTitle
                        .getText()
                        .toString()
                        .trim();

        String date =
                etMeetingDate
                        .getText()
                        .toString()
                        .trim();

        String time =
                etMeetingTime
                        .getText()
                        .toString()
                        .trim();

        String description =
                etMeetingDescription
                        .getText()
                        .toString()
                        .trim();

        String meetingLink =
                etMeetingLink
                        .getText()
                        .toString()
                        .trim();

        meetingLink =
                meetingLink
                        .replace("\n", "")
                        .replace("\r", "")
                        .trim();

        // =====================================================
        // VALIDATION
        // =====================================================

        if (TextUtils.isEmpty(title)) {

            etMeetingTitle.setError(
                    "Enter meeting title"
            );

            etMeetingTitle.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(date)) {

            etMeetingDate.setError(
                    "Select meeting date"
            );

            etMeetingDate.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(time)) {

            etMeetingTime.setError(
                    "Select meeting time"
            );

            etMeetingTime.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(meetingLink)) {

            etMeetingLink.setError(
                    "Paste Google Meet link"
            );

            etMeetingLink.requestFocus();

            return;
        }

        if (
                meetingLink.startsWith(
                        "meet.google.com/"
                )
        ) {

            meetingLink =
                    "https://" + meetingLink;

        } else if (
                meetingLink.startsWith(
                        "www.meet.google.com/"
                )
        ) {

            meetingLink =
                    "https://" + meetingLink;
        }

        if (
                !meetingLink.startsWith(
                        "https://meet.google.com/"
                )
        ) {

            etMeetingLink.setError(
                    "Enter a valid Google Meet link"
            );

            etMeetingLink.requestFocus();

            return;
        }

        if (
                firebaseAuth.getCurrentUser() == null
        ) {

            Toast.makeText(
                    this,
                    "Admin session expired.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // =====================================================
        // AUDIENCE
        // =====================================================

        boolean allEmployees =
                spinnerMeetingAudience
                        .getSelectedItemPosition()
                        == 0;

        ArrayList<String> selectedIds =
                new ArrayList<>();

        if (!allEmployees) {

            for (
                    EmployeeInfo employee :
                    employeeList
            ) {

                Boolean checked =
                        selectedEmployees.get(
                                employee.uid
                        );

                if (
                        checked != null &&
                                checked
                ) {

                    selectedIds.add(
                            employee.uid
                    );
                }
            }

            if (selectedIds.isEmpty()) {

                Toast.makeText(
                        this,
                        "Select at least one employee.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }
        }

        // =====================================================
        // ADMIN UID
        // =====================================================

        String adminUid =
                firebaseAuth
                        .getCurrentUser()
                        .getUid();

        // =====================================================
        // MEETING DATA
        // =====================================================

        Map<String, Object> meeting =
                new HashMap<>();

        meeting.put(
                "title",
                title
        );

        meeting.put(
                "date",
                date
        );

        meeting.put(
                "time",
                time
        );

        meeting.put(
                "description",
                description
        );

        meeting.put(
                "meetingLink",
                meetingLink
        );

        meeting.put(
                "createdBy",
                adminUid
        );

        meeting.put(
                "createdAt",
                FieldValue.serverTimestamp()
        );

        meeting.put(
                "status",
                "Scheduled"
        );

        // =====================================================
        // NEW AUDIENCE FIELDS
        // =====================================================

        if (allEmployees) {

            meeting.put(
                    "audienceType",
                    "ALL"
            );

            meeting.put(
                    "selectedEmployeeIds",
                    new ArrayList<String>()
            );

        } else {

            meeting.put(
                    "audienceType",
                    "SELECTED"
            );

            meeting.put(
                    "selectedEmployeeIds",
                    selectedIds
            );
        }

        // =====================================================
        // SAVE
        // =====================================================

        btnScheduleMeeting.setEnabled(
                false
        );

        firestore
                .collection("meetings")
                .add(meeting)

                .addOnSuccessListener(
                        documentReference -> {

                            Toast.makeText(
                                    this,
                                    "Meeting scheduled successfully.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            clearMeetingFields();

                            btnScheduleMeeting.setEnabled(
                                    true
                            );

                            loadMeetings();
                        }
                )

                .addOnFailureListener(
                        e -> {

                            btnScheduleMeeting.setEnabled(
                                    true
                            );

                            Toast.makeText(
                                    this,
                                    "Failed to schedule meeting: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // CLEAR FORM
    // =========================================================

    private void clearMeetingFields() {

        etMeetingTitle.setText("");

        etMeetingDate.setText("");

        etMeetingTime.setText("");

        etMeetingDescription.setText("");

        etMeetingLink.setText("");

        spinnerMeetingAudience.setSelection(
                0
        );

        for (
                String uid :
                selectedEmployees.keySet()
        ) {

            selectedEmployees.put(
                    uid,
                    false
            );
        }

        displayEmployeeCheckboxes();
    }

    // =========================================================
    // LOAD MEETINGS
    // =========================================================

    private void loadMeetings() {

        meetingsContainer.removeAllViews();

        firestore
                .collection("meetings")
                .orderBy(
                        "date",
                        Query.Direction.ASCENDING
                )
                .get()

                .addOnSuccessListener(
                        querySnapshot -> {

                            if (
                                    querySnapshot.isEmpty()
                            ) {

                                TextView empty =
                                        new TextView(this);

                                empty.setText(
                                        "No meetings scheduled."
                                );

                                empty.setTextSize(
                                        16
                                );

                                empty.setTextColor(
                                        0xFF777080
                                );

                                empty.setPadding(
                                        10,
                                        20,
                                        10,
                                        20
                                );

                                meetingsContainer.addView(
                                        empty
                                );

                                return;
                            }

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                addMeetingCard(
                                        document
                                );
                            }
                        }
                )

                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load meetings: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // ADD ADMIN MEETING CARD
    // =========================================================

    private void addMeetingCard(
            DocumentSnapshot document
    ) {

        View view =
                getLayoutInflater().inflate(
                        R.layout.item_admin_meeting,
                        meetingsContainer,
                        false
                );

        TextView tvTitle =
                view.findViewById(
                        R.id.tvMeetingTitle
                );

        TextView tvDate =
                view.findViewById(
                        R.id.tvMeetingDate
                );

        TextView tvTime =
                view.findViewById(
                        R.id.tvMeetingTime
                );

        TextView tvDescription =
                view.findViewById(
                        R.id.tvMeetingDescription
                );

        TextView tvStatus =
                view.findViewById(
                        R.id.tvMeetingStatus
                );

        Button btnStartMeeting =
                view.findViewById(
                        R.id.btnStartMeeting
                );

        Button btnEndMeeting =
                view.findViewById(
                        R.id.btnEndMeeting
                );

        Button btnDeleteMeeting =
                view.findViewById(
                        R.id.btnDeleteMeeting
                );

        String title =
                document.getString("title");

        String date =
                document.getString("date");

        String time =
                document.getString("time");

        String description =
                document.getString("description");

        String meetingLink =
                document.getString("meetingLink");

        String status =
                document.getString("status");

        if (
                status == null ||
                        status.trim().isEmpty()
        ) {
            status = "Scheduled";
        }

        tvTitle.setText(
                title != null
                        ? title
                        : "Meeting"
        );

        tvDate.setText(
                "Date: " +
                        (
                                date != null
                                        ? date
                                        : "-"
                        )
        );

        tvTime.setText(
                "Time: " +
                        (
                                time != null
                                        ? time
                                        : "-"
                        )
        );

        tvDescription.setText(
                description == null ||
                        description.trim().isEmpty()
                        ? "No description"
                        : description
        );

        tvStatus.setText(
                "Status: " + status
        );

        final String finalLink =
                meetingLink;

        final String meetingId =
                document.getId();

        if (
                "Scheduled".equalsIgnoreCase(
                        status
                )
        ) {

            btnStartMeeting.setText(
                    "START MEETING"
            );

            btnStartMeeting.setEnabled(
                    true
            );

            btnEndMeeting.setText(
                    "END MEETING"
            );

            btnEndMeeting.setEnabled(
                    false
            );

            btnStartMeeting.setOnClickListener(
                    v -> startAdminMeeting(
                            meetingId,
                            finalLink,
                            btnStartMeeting,
                            btnEndMeeting,
                            tvStatus
                    )
            );

        } else if (
                "Active".equalsIgnoreCase(
                        status
                )
        ) {

            btnStartMeeting.setText(
                    "OPEN GOOGLE MEET"
            );

            btnStartMeeting.setEnabled(
                    true
            );

            btnEndMeeting.setText(
                    "END MEETING"
            );

            btnEndMeeting.setEnabled(
                    true
            );

            btnStartMeeting.setOnClickListener(
                    v -> openGoogleMeet(
                            finalLink
                    )
            );

            btnEndMeeting.setOnClickListener(
                    v -> endMeeting(
                            meetingId,
                            btnStartMeeting,
                            btnEndMeeting,
                            tvStatus
                    )
            );

        } else {

            btnStartMeeting.setText(
                    "MEETING ENDED"
            );

            btnStartMeeting.setEnabled(
                    false
            );

            btnEndMeeting.setText(
                    "MEETING ENDED"
            );

            btnEndMeeting.setEnabled(
                    false
            );
        }

        btnDeleteMeeting.setOnClickListener(
                v -> deleteMeeting(
                        meetingId
                )
        );

        meetingsContainer.addView(
                view
        );
    }

// =========================================================
// START MEETING
// =========================================================

    private void startAdminMeeting(
            String meetingId,
            String meetingLink,
            Button btnStartMeeting,
            Button btnEndMeeting,
            TextView tvStatus
    ) {

        if (
                meetingLink == null ||
                        meetingLink.trim().isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Google Meet link not found.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (firebaseAuth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Admin session expired.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        btnStartMeeting.setEnabled(false);

        String adminUid =
                firebaseAuth.getCurrentUser().getUid();

        // Five-minute deadline from the Start button press.
        long deadlineMillis =
                System.currentTimeMillis() + (5L * 60L * 1000L);

        Map<String, Object> updates = new HashMap<>();

        updates.put("status", "Active");

        updates.put(
                "startedAt",
                FieldValue.serverTimestamp()
        );

        updates.put(
                "startedBy",
                adminUid
        );

        // Added fields; existing meeting fields remain unchanged.
        updates.put(
                "joinRequestsDeadline",
                new Timestamp(deadlineMillis / 1000, 0)
        );

        updates.put(
                "joinRequestWindowMinutes",
                5
        );

        firestore
                .collection("meetings")
                .document(meetingId)
                .update(updates)

                .addOnSuccessListener(unused -> {

                    tvStatus.setText("Status: Active");

                    btnStartMeeting.setText(
                            "OPEN GOOGLE MEET"
                    );

                    btnStartMeeting.setEnabled(true);

                    btnEndMeeting.setText(
                            "END MEETING"
                    );

                    btnEndMeeting.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Meeting started. Five-minute deadline recorded.",
                            Toast.LENGTH_LONG
                    ).show();

                    // Keep the existing Google Meet opening behavior.
                    openGoogleMeet(meetingLink);
                })

                .addOnFailureListener(e -> {

                    btnStartMeeting.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Unable to start meeting: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }


    // =========================================================
    // END MEETING
    // =========================================================

    private void endMeeting(
            String meetingId,
            Button btnStartMeeting,
            Button btnEndMeeting,
            TextView tvStatus
    ) {

        if (
                firebaseAuth.getCurrentUser() == null
        ) {
            return;
        }

        btnStartMeeting.setEnabled(
                false
        );

        btnEndMeeting.setEnabled(
                false
        );

        String adminUid =
                firebaseAuth
                        .getCurrentUser()
                        .getUid();

        Map<String, Object> update =
                new HashMap<>();

        update.put(
                "status",
                "Ended"
        );

        update.put(
                "endedAt",
                FieldValue.serverTimestamp()
        );

        update.put(
                "endedBy",
                adminUid
        );

        firestore
                .collection("meetings")
                .document(meetingId)
                .update(update)

                .addOnSuccessListener(
                        unused -> {

                            tvStatus.setText(
                                    "Status: Ended"
                            );

                            btnStartMeeting.setText(
                                    "MEETING ENDED"
                            );

                            btnEndMeeting.setText(
                                    "MEETING ENDED"
                            );

                            Toast.makeText(
                                    this,
                                    "Meeting ended successfully.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )

                .addOnFailureListener(
                        e -> {

                            btnStartMeeting.setEnabled(
                                    true
                            );

                            btnEndMeeting.setEnabled(
                                    true
                            );

                            Toast.makeText(
                                    this,
                                    "Unable to end meeting: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // OPEN GOOGLE MEET
    // =========================================================

    private void openGoogleMeet(
            String meetingLink
    ) {

        if (
                meetingLink == null ||
                        meetingLink.trim().isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Google Meet link is not available.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String cleanLink =
                meetingLink
                        .replace("\n", "")
                        .replace("\r", "")
                        .trim();

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(cleanLink)
                    );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to open Google Meet.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // DELETE
    // =========================================================

    private void deleteMeeting(
            String meetingId
    ) {

        firestore
                .collection("meetings")
                .document(meetingId)
                .delete()

                .addOnSuccessListener(
                        unused -> {

                            Toast.makeText(
                                    this,
                                    "Meeting deleted.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadMeetings();
                        }
                )

                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to delete meeting: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }
}