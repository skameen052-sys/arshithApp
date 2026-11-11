
package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminOfficeTimingsActivity extends ComponentActivity {

    private TextView tvStartTime;
    private TextView tvEndTime;
    private TextView tvSelectedDate;
    private EditText etGracePeriod;
    private Button btnSaveTimings;
    private Button btnSelectDate;
    private RadioGroup rgTimingType;
    private LinearLayout layoutSpecialDate;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private String startTime = "09:00";
    private String endTime = "18:00";
    private String selectedDate = null;

    private boolean isSpecialMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_office_timings);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        etGracePeriod = findViewById(R.id.etGracePeriod);
        btnSaveTimings = findViewById(R.id.btnSaveTimings);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        rgTimingType = findViewById(R.id.rgTimingType);
        layoutSpecialDate = findViewById(R.id.layoutSpecialDate);

        tvStartTime.setText(startTime);
        tvEndTime.setText(endTime);
        etGracePeriod.setText("15");

        tvStartTime.setOnClickListener(v -> showTimePicker(true));
        tvEndTime.setOnClickListener(v -> showTimePicker(false));

        btnSelectDate.setOnClickListener(v -> showDatePicker());

        rgTimingType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbRegularTimings) {
                isSpecialMode = false;
                layoutSpecialDate.setVisibility(View.GONE);
                loadRegularTimings();
            } else if (checkedId == R.id.rbSpecialTimings) {
                isSpecialMode = true;
                layoutSpecialDate.setVisibility(View.VISIBLE);

                if (selectedDate != null) {
                    loadSpecialTimings(selectedDate);
                } else {
                    tvSelectedDate.setText("No date selected");
                    loadRegularTimings();
                }
            }
        });

        loadRegularTimings();

        btnSaveTimings.setOnClickListener(v -> saveOfficeTimings());
    }

    private void showTimePicker(boolean isStartTime) {
        String currentTime = isStartTime ? startTime : endTime;
        String[] parts = currentTime.split(":");

        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    String time = String.format(
                            Locale.US,
                            "%02d:%02d",
                            selectedHour,
                            selectedMinute
                    );

                    if (isStartTime) {
                        startTime = time;
                        tvStartTime.setText(time);
                    } else {
                        endTime = time;
                        tvEndTime.setText(time);
                    }
                },
                hour,
                minute,
                false
        ).show();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();

        if (selectedDate != null) {
            try {
                SimpleDateFormat formatter =
                        new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                formatter.setLenient(false);

                calendar.setTime(formatter.parse(selectedDate));
            } catch (Exception ignored) {
                calendar = Calendar.getInstance();
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (datePicker, year, month, dayOfMonth) -> {
                    Calendar chosenDate = Calendar.getInstance();
                    chosenDate.set(year, month, dayOfMonth, 0, 0, 0);
                    chosenDate.set(Calendar.MILLISECOND, 0);

                    SimpleDateFormat formatter =
                            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

                    selectedDate = formatter.format(chosenDate.getTime());

                    tvSelectedDate.setText("Selected date: " + selectedDate);

                    if (isSpecialMode) {
                        loadSpecialTimings(selectedDate);
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.getDatePicker().setMinDate(
                Calendar.getInstance().getTimeInMillis()
        );

        dialog.show();
    }

    private void loadRegularTimings() {
        firestore.collection("settings")
                .document("officeTimings")
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        startTime = "09:00";
                        endTime = "18:00";
                        etGracePeriod.setText("15");
                        updateTimeViews();
                        return;
                    }

                    String savedStart =
                            document.getString("officeStartTime");
                    String savedEnd =
                            document.getString("officeEndTime");
                    Long grace =
                            document.getLong("gracePeriodMinutes");

                    startTime = savedStart != null ? savedStart : "09:00";
                    endTime = savedEnd != null ? savedEnd : "18:00";

                    if (grace != null) {
                        etGracePeriod.setText(String.valueOf(grace));
                    } else {
                        etGracePeriod.setText("15");
                    }

                    updateTimeViews();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Unable to load regular timings: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void loadSpecialTimings(String date) {
        if (date == null || date.isEmpty()) {
            return;
        }

        firestore.collection("specialOfficeTimings")
                .document(date)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isSpecialMode || !date.equals(selectedDate)) {
                        return;
                    }

                    if (document.exists()) {
                        String savedStart =
                                document.getString("officeStartTime");
                        String savedEnd =
                                document.getString("officeEndTime");
                        Long grace =
                                document.getLong("gracePeriodMinutes");

                        startTime = savedStart != null
                                ? savedStart : "09:00";
                        endTime = savedEnd != null
                                ? savedEnd : "18:00";

                        etGracePeriod.setText(
                                String.valueOf(grace != null ? grace : 15)
                        );

                        updateTimeViews();

                        Toast.makeText(
                                this,
                                "Special timings loaded for " + date,
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        loadRegularTimings();

                        Toast.makeText(
                                this,
                                "No special timings saved for this date. "
                                        + "Default timings are loaded.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Unable to load special timings: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void updateTimeViews() {
        tvStartTime.setText(startTime);
        tvEndTime.setText(endTime);
    }

    private void saveOfficeTimings() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (isSpecialMode &&
                (selectedDate == null || selectedDate.isEmpty())) {
            Toast.makeText(
                    this,
                    "Please select a date for special timings.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String graceText = etGracePeriod.getText().toString().trim();

        if (graceText.isEmpty()) {
            etGracePeriod.setError("Enter grace period");
            return;
        }

        int grace;

        try {
            grace = Integer.parseInt(graceText);
        } catch (NumberFormatException e) {
            etGracePeriod.setError("Enter a valid number");
            return;
        }

        if (grace < 0 || grace > 180) {
            etGracePeriod.setError("Enter a value between 0 and 180");
            return;
        }

        if (startTime.compareTo(endTime) >= 0) {
            Toast.makeText(
                    this,
                    "Office end time must be later than start time.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        btnSaveTimings.setEnabled(false);

        firestore.collection("admins")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(adminDocument -> {
                    String role = adminDocument.getString("role");

                    if (!adminDocument.exists()
                            || role == null
                            || !"admin".equalsIgnoreCase(role)) {

                        btnSaveTimings.setEnabled(true);

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("officeStartTime", startTime);
                    data.put("officeEndTime", endTime);
                    data.put("gracePeriodMinutes", grace);
                    data.put("updatedBy", user.getUid());
                    data.put("updatedAt", FieldValue.serverTimestamp());

                    if (isSpecialMode) {
                        data.put("specialDate", selectedDate);

                        firestore.collection("specialOfficeTimings")
                                .document(selectedDate)
                                .set(data, SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    btnSaveTimings.setEnabled(true);

                                    Toast.makeText(
                                            this,
                                            "Special timings saved for "
                                                    + selectedDate,
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                                .addOnFailureListener(e -> {
                                    btnSaveTimings.setEnabled(true);

                                    Toast.makeText(
                                            this,
                                            "Failed to save special timings: "
                                                    + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });

                    } else {
                        firestore.collection("settings")
                                .document("officeTimings")
                                .set(data, SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    btnSaveTimings.setEnabled(true);

                                    Toast.makeText(
                                            this,
                                            "Regular office timings saved.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                                .addOnFailureListener(e -> {
                                    btnSaveTimings.setEnabled(true);

                                    Toast.makeText(
                                            this,
                                            "Failed to save regular timings: "
                                                    + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    btnSaveTimings.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Unable to verify admin: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}
