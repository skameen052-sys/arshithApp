package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Date;

import androidx.activity.ComponentActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdminAttendanceReportsActivity
        extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private TextView tvSelectedMonth;
    private TextView tvTotalRecords;
    private TextView tvWfoCount;
    private TextView tvWfhCount;
    private TextView tvLateCount;
    private TextView tvNoReports;

    private Button btnBack;
    private Button btnChangeMonth;
    private Button btnRefresh;

    private LinearLayout reportsContainer;

    private Calendar selectedMonth;

    // Holiday dates for selected month
    private Set<String> holidayDates =
            new HashSet<>();

    // Approved leave dates grouped by employee
    private Map<String, Set<String>> approvedLeaveDates =
            new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_attendance_reports
        );

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        initializeViews();

        selectedMonth =
                Calendar.getInstance();

        updateMonthText();

        setupButtons();

        verifyAdminAndLoadReport();
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {

        tvSelectedMonth =
                findViewById(R.id.tvSelectedMonth);

        tvTotalRecords =
                findViewById(R.id.tvTotalRecords);

        tvWfoCount =
                findViewById(R.id.tvWfoCount);

        tvWfhCount =
                findViewById(R.id.tvWfhCount);

        tvLateCount =
                findViewById(R.id.tvLateCount);

        tvNoReports =
                findViewById(R.id.tvNoReports);

        btnBack =
                findViewById(R.id.btnBack);

        btnChangeMonth =
                findViewById(R.id.btnChangeMonth);

        btnRefresh =
                findViewById(R.id.btnRefresh);

        reportsContainer =
                findViewById(R.id.reportsContainer);
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                v -> finish()
        );

        btnChangeMonth.setOnClickListener(
                v -> showMonthPicker()
        );

        btnRefresh.setOnClickListener(
                v -> loadReport()
        );
    }

    // =========================================================
    // VERIFY ADMIN
    // =========================================================

    private void verifyAdminAndLoadReport() {

        firestore.collection("admins")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {

                        firebaseAuth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    String role =
                            documentSnapshot.getString("role");

                    if (!"admin".equalsIgnoreCase(role)) {

                        firebaseAuth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    loadReport();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to verify admin.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // UPDATE MONTH TEXT
    // =========================================================

    private void updateMonthText() {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.getDefault()
                );

        tvSelectedMonth.setText(
                format.format(
                        selectedMonth.getTime()
                )
        );
    }

    // =========================================================
    // MONTH PICKER
    // =========================================================

    private void showMonthPicker() {

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            selectedMonth.set(
                                    Calendar.YEAR,
                                    year
                            );

                            selectedMonth.set(
                                    Calendar.MONTH,
                                    month
                            );

                            updateMonthText();

                            loadReport();
                        },
                        selectedMonth.get(
                                Calendar.YEAR
                        ),
                        selectedMonth.get(
                                Calendar.MONTH
                        ),
                        1
                );

        dialog.show();
    }

    // =========================================================
    // LOAD REPORT
    // =========================================================

    private void loadReport() {

        reportsContainer.removeAllViews();

        tvNoReports.setVisibility(
                View.GONE
        );

        tvTotalRecords.setText("0");
        tvWfoCount.setText("0");
        tvWfhCount.setText("0");
        tvLateCount.setText("0");

        holidayDates.clear();
        approvedLeaveDates.clear();

        /*
         * First load holidays.
         * Then approved leaves.
         * Then attendance.
         */
        loadHolidaysForReport();
    }

    // =========================================================
    // LOAD HOLIDAYS
    // =========================================================

    private void loadHolidaysForReport() {

        firestore.collection("holidays")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    holidayDates.clear();

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        String date =
                                document.getString("date");

                        /*
                         * If the holiday date field is missing,
                         * also support document ID as date.
                         */
                        if (date == null
                                || date.trim().isEmpty()) {

                            date =
                                    document.getId();
                        }

                        if (date == null
                                || date.trim().isEmpty()) {

                            continue;
                        }

                        if (isDateInSelectedMonth(date)) {

                            holidayDates.add(date);
                        }
                    }

                    loadApprovedLeavesForReport();

                })
                .addOnFailureListener(e -> {

                    holidayDates.clear();

                    /*
                     * Continue report generation even if
                     * holidays cannot be loaded.
                     */
                    loadApprovedLeavesForReport();
                });
    }

    // =========================================================
    // LOAD APPROVED LEAVES
    // =========================================================

    private void loadApprovedLeavesForReport() {

        firestore.collectionGroup("requests")
                .whereEqualTo(
                        "status",
                        "Approved"
                )
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    approvedLeaveDates.clear();

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        String employeeId =
                                document.getString(
                                        "employeeId"
                                );

                        String email =
                                document.getString(
                                        "email"
                                );

                        /*
                         * Employee ID is preferred because
                         * it uniquely identifies an employee.
                         *
                         * If it does not exist, email is used.
                         */
                        String employeeKey;

                        if (employeeId != null
                                && !employeeId.trim().isEmpty()) {

                            employeeKey =
                                    employeeId;

                        } else if (email != null
                                && !email.trim().isEmpty()) {

                            employeeKey =
                                    email;

                        } else {

                            continue;
                        }

                        String fromDate =
                                document.getString(
                                        "fromDate"
                                );

                        String toDate =
                                document.getString(
                                        "toDate"
                                );

                        if (fromDate == null
                                || toDate == null) {

                            continue;
                        }

                        addApprovedLeaveDates(
                                employeeKey,
                                fromDate,
                                toDate
                        );
                    }

                    loadAttendanceReport();

                })
                .addOnFailureListener(e -> {

                    approvedLeaveDates.clear();

                    /*
                     * Continue report generation even if
                     * leaves cannot be loaded.
                     */
                    loadAttendanceReport();
                });
    }

    // =========================================================
    // ADD APPROVED LEAVE DATES
    // =========================================================

    private void addApprovedLeaveDates(
            String employeeKey,
            String fromDateText,
            String toDateText
    ) {

        Calendar from =
                parseDate(fromDateText);

        Calendar to =
                parseDate(toDateText);

        if (from == null || to == null) {
            return;
        }

        Calendar monthStart =
                getSelectedMonthStart();

        Calendar monthEnd =
                getSelectedMonthEnd();

        /*
         * For the current month, do not count
         * future dates as leave.
         */
        if (isCurrentMonth()) {

            Calendar today =
                    Calendar.getInstance();

            today.set(
                    Calendar.HOUR_OF_DAY,
                    23
            );

            today.set(
                    Calendar.MINUTE,
                    59
            );

            today.set(
                    Calendar.SECOND,
                    59
            );

            if (monthEnd.after(today)) {
                monthEnd =
                        (Calendar) today.clone();
            }
        }

        /*
         * Restrict leave range to selected month.
         */
        if (from.before(monthStart)) {

            from =
                    (Calendar) monthStart.clone();
        }

        if (to.after(monthEnd)) {

            to =
                    (Calendar) monthEnd.clone();
        }

        Set<String> employeeLeaves =
                approvedLeaveDates.get(
                        employeeKey
                );

        if (employeeLeaves == null) {

            employeeLeaves =
                    new HashSet<>();

            approvedLeaveDates.put(
                    employeeKey,
                    employeeLeaves
            );
        }

        while (!from.after(to)) {

            String date =
                    formatDate(from);

            /*
             * Approved leave counts only when:
             *
             * Monday-Friday
             * AND not a holiday
             */
            if (isWeekday(from)
                    && !holidayDates.contains(date)) {

                employeeLeaves.add(date);
            }

            from.add(
                    Calendar.DAY_OF_MONTH,
                    1
            );
        }
    }

    // =========================================================
    // LOAD ATTENDANCE REPORT
    // =========================================================

    private void loadAttendanceReport() {

        int year =
                selectedMonth.get(
                        Calendar.YEAR
                );

        int month =
                selectedMonth.get(
                        Calendar.MONTH
                );

        Calendar firstDay =
                Calendar.getInstance();

        firstDay.set(
                year,
                month,
                1,
                0,
                0,
                0
        );

        Calendar lastDay =
                Calendar.getInstance();

        lastDay.set(
                year,
                month,
                selectedMonth.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                ),
                23,
                59,
                59
        );

        /*
         * For current month, future dates are not
         * considered working/absent days.
         */
        if (isCurrentMonth()) {

            Calendar today =
                    Calendar.getInstance();

            today.set(
                    Calendar.HOUR_OF_DAY,
                    23
            );

            today.set(
                    Calendar.MINUTE,
                    59
            );

            today.set(
                    Calendar.SECOND,
                    59
            );

            if (lastDay.after(today)) {

                lastDay =
                        (Calendar) today.clone();
            }
        }

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                );

        String startDate =
                dateFormat.format(
                        firstDay.getTime()
                );

        String endDate =
                dateFormat.format(
                        lastDay.getTime()
                );

        /*
         * Calculate total working days once.
         *
         * Holidays and weekends are excluded.
         */
        int totalWorkingDays =
                calculateWorkingDays(
                        firstDay,
                        lastDay
                );

        firestore.collectionGroup("records")
                .whereGreaterThanOrEqualTo(
                        "date",
                        startDate
                )
                .whereLessThanOrEqualTo(
                        "date",
                        endDate
                )
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int totalRecords = 0;
                    int wfo = 0;
                    int wfh = 0;
                    int late = 0;

                    Map<String, EmployeeReport> employees =
                            new HashMap<>();

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        String email =
                                document.getString(
                                        "email"
                                );

                        if (email == null
                                || email.trim().isEmpty()) {

                            email =
                                    "Unknown employee";
                        }

                        String employeeId =
                                document.getString(
                                        "employeeId"
                                );

                        if (employeeId == null
                                || employeeId.trim().isEmpty()) {

                            /*
                             * Use email as fallback key.
                             */
                            employeeId =
                                    email;
                        }

                        String date =
                                document.getString(
                                        "date"
                                );

                        if (date == null
                                || date.trim().isEmpty()) {

                            continue;
                        }

                        /*
                         * Attendance on a weekend or holiday
                         * does not count toward attendance
                         * percentage.
                         */
                        boolean validWorkingDate =
                                isValidWorkingAttendanceDate(
                                        date
                                );

                        String workMode =
                                document.getString(
                                        "workMode"
                                );

                        Boolean lateLogin =
                                document.getBoolean(
                                        "lateLogin"
                                );

                        EmployeeReport report =
                                employees.get(
                                        employeeId
                                );

                        if (report == null) {

                            report =
                                    new EmployeeReport(
                                            employeeId,
                                            email
                                    );

                            employees.put(
                                    employeeId,
                                    report
                            );
                        }

                        /*
                         * Count the attendance record
                         * in the overall report.
                         */
                        totalRecords++;

                        /*
                         * Present days for percentage
                         * only count valid working days.
                         */
                        if (validWorkingDate) {

                            report.presentDates.add(
                                    date
                            );
                        }

                        if ("WFO".equalsIgnoreCase(
                                workMode
                        )) {

                            wfo++;
                            report.wfoDays++;
                        }

                        if ("WFH".equalsIgnoreCase(
                                workMode
                        )) {

                            wfh++;
                            report.wfhDays++;
                        }

                        if (Boolean.TRUE.equals(
                                lateLogin
                        )) {

                            late++;

                            report.lateDays++;

                            String reason =
                                    document.getString(
                                            "lateReason"
                                    );

                            if (reason != null
                                    && !reason.trim().isEmpty()) {

                                report.lateReasons.add(
                                        date
                                                + " - "
                                                + reason
                                );
                            }
                        }
                    }

                    /*
                     * Add approved leave count and
                     * calculate percentage for every employee.
                     */
                    for (EmployeeReport report :
                            employees.values()) {

                        Set<String> leaves =
                                approvedLeaveDates.get(
                                        report.employeeId
                                );

                        /*
                         * Some older records may have used
                         * email instead of employeeId.
                         */
                        if (leaves == null) {

                            leaves =
                                    approvedLeaveDates.get(
                                            report.email
                                    );
                        }

                        if (leaves != null) {

                            report.approvedLeaveDays =
                                    leaves.size();
                        }

                        report.workingDays =
                                totalWorkingDays;

                        int eligibleDays =
                                report.workingDays
                                        - report.approvedLeaveDays;

                        /*
                         * Never allow denominator
                         * to become negative.
                         */
                        if (eligibleDays < 0) {

                            eligibleDays = 0;
                        }

                        /*
                         * Approved leave should not be
                         * counted as present.
                         */
                        report.presentDays = 0;

                        for (String presentDate :
                                report.presentDates) {

                            if (leaves == null
                                    || !leaves.contains(
                                    presentDate
                            )) {

                                report.presentDays++;
                            }
                        }

                        if (eligibleDays > 0) {

                            report.attendancePercentage =
                                    (
                                            report.presentDays
                                                    * 100.0
                                    )
                                            / eligibleDays;

                        } else {

                            report.attendancePercentage =
                                    0.0;
                        }
                    }

                    tvTotalRecords.setText(
                            String.valueOf(
                                    totalRecords
                            )
                    );

                    tvWfoCount.setText(
                            String.valueOf(wfo)
                    );

                    tvWfhCount.setText(
                            String.valueOf(wfh)
                    );

                    tvLateCount.setText(
                            String.valueOf(late)
                    );

                    for (EmployeeReport report :
                            employees.values()) {

                        addEmployeeReport(
                                report
                        );
                    }

                    if (employees.isEmpty()) {

                        tvNoReports.setVisibility(
                                View.VISIBLE
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    tvNoReports.setVisibility(
                            View.VISIBLE
                    );

                    tvNoReports.setText(
                            "Unable to load attendance report."
                    );

                    Toast.makeText(
                            this,
                            "Report loading failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =========================================================
    // CALCULATE WORKING DAYS
    // =========================================================

    private int calculateWorkingDays(
            Calendar startDate,
            Calendar endDate
    ) {

        int workingDays = 0;

        Calendar current =
                (Calendar) startDate.clone();

        while (!current.after(endDate)) {

            String date =
                    formatDate(current);

            /*
             * Monday-Friday
             * AND not a holiday
             */
            if (isWeekday(current)
                    && !holidayDates.contains(date)) {

                workingDays++;
            }

            current.add(
                    Calendar.DAY_OF_MONTH,
                    1
            );
        }

        return workingDays;
    }

    // =========================================================
    // ADD EMPLOYEE REPORT
    // =========================================================

    private void addEmployeeReport(
            EmployeeReport report
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                18,
                18,
                18,
                18
        );

        card.setBackgroundColor(
                Color.WHITE
        );

        card.setElevation(3f);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                16
        );

        card.setLayoutParams(
                cardParams
        );

        // -----------------------------------------------------
        // EMAIL
        // -----------------------------------------------------

        TextView emailView =
                createTextView(
                        report.email,
                        17,
                        "#251E32",
                        true
                );

        card.addView(
                emailView
        );

        // -----------------------------------------------------
        // PRESENT DAYS
        // -----------------------------------------------------

        TextView presentView =
                createTextView(
                        "Present Days: "
                                + report.presentDays,
                        14,
                        "#159447",
                        true
                );

        setTopMargin(
                presentView,
                8
        );

        card.addView(
                presentView
        );

        // -----------------------------------------------------
        // WORKING DAYS
        // -----------------------------------------------------

        TextView workingDaysView =
                createTextView(
                        "Working Days: "
                                + report.workingDays,
                        14,
                        "#5F5869",
                        false
                );

        card.addView(
                workingDaysView
        );

        // -----------------------------------------------------
        // APPROVED LEAVE
        // -----------------------------------------------------

        TextView leaveView =
                createTextView(
                        "Approved Leave: "
                                + report.approvedLeaveDays,
                        14,
                        "#7651E8",
                        false
                );

        card.addView(
                leaveView
        );

        // -----------------------------------------------------
        // ATTENDANCE PERCENTAGE
        // -----------------------------------------------------

        TextView percentageView =
                createTextView(
                        String.format(
                                Locale.getDefault(),
                                "Attendance Percentage: %.1f%%",
                                report.attendancePercentage
                        ),
                        16,
                        "#7651E8",
                        true
                );

        setTopMargin(
                percentageView,
                8
        );

        card.addView(
                percentageView
        );

        // -----------------------------------------------------
        // WFO
        // -----------------------------------------------------

        TextView wfoView =
                createTextView(
                        "WFO Days: "
                                + report.wfoDays,
                        14,
                        "#159447",
                        false
                );

        card.addView(
                wfoView
        );

        // -----------------------------------------------------
        // WFH
        // -----------------------------------------------------

        TextView wfhView =
                createTextView(
                        "WFH Days: "
                                + report.wfhDays,
                        14,
                        "#7651E8",
                        false
                );

        card.addView(
                wfhView
        );

        // -----------------------------------------------------
        // LATE
        // -----------------------------------------------------

        TextView lateView =
                createTextView(
                        "Late Days: "
                                + report.lateDays,
                        14,
                        "#D88328",
                        true
                );

        card.addView(
                lateView
        );

        // -----------------------------------------------------
        // LATE REASONS
        // -----------------------------------------------------

        if (!report.lateReasons.isEmpty()) {

            TextView reasonsTitle =
                    createTextView(
                            "Late Reasons:",
                            14,
                            "#5F5869",
                            true
                    );

            setTopMargin(
                    reasonsTitle,
                    8
            );

            card.addView(
                    reasonsTitle
            );

            for (String reason :
                    report.lateReasons) {

                TextView reasonView =
                        createTextView(
                                reason,
                                13,
                                "#777080",
                                false
                        );

                card.addView(
                        reasonView
                );
            }
        }

        reportsContainer.addView(
                card
        );
    }

    // =========================================================
    // VALID ATTENDANCE DATE
    // =========================================================

    private boolean isValidWorkingAttendanceDate(
            String date
    ) {

        Calendar calendar =
                parseDate(date);

        if (calendar == null) {
            return false;
        }

        if (!isWeekday(calendar)) {
            return false;
        }

        return !holidayDates.contains(date);
    }

    // =========================================================
    // CHECK DATE IN SELECTED MONTH
    // =========================================================

    private boolean isDateInSelectedMonth(
            String date
    ) {

        Calendar calendar =
                parseDate(date);

        if (calendar == null) {
            return false;
        }

        return calendar.get(
                Calendar.YEAR
        ) == selectedMonth.get(
                Calendar.YEAR
        )
                && calendar.get(
                Calendar.MONTH
        ) == selectedMonth.get(
                Calendar.MONTH
        );
    }

    // =========================================================
    // CURRENT MONTH
    // =========================================================

    private boolean isCurrentMonth() {

        Calendar now =
                Calendar.getInstance();

        return now.get(
                Calendar.YEAR
        ) == selectedMonth.get(
                Calendar.YEAR
        )
                && now.get(
                Calendar.MONTH
        ) == selectedMonth.get(
                Calendar.MONTH
        );
    }

    // =========================================================
    // SELECTED MONTH START
    // =========================================================

    private Calendar getSelectedMonthStart() {

        Calendar start =
                Calendar.getInstance();

        start.set(
                selectedMonth.get(
                        Calendar.YEAR
                ),
                selectedMonth.get(
                        Calendar.MONTH
                ),
                1,
                0,
                0,
                0
        );

        return start;
    }

    // =========================================================
    // SELECTED MONTH END
    // =========================================================

    private Calendar getSelectedMonthEnd() {

        Calendar end =
                Calendar.getInstance();

        end.set(
                selectedMonth.get(
                        Calendar.YEAR
                ),
                selectedMonth.get(
                        Calendar.MONTH
                ),
                selectedMonth.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                ),
                23,
                59,
                59
        );

        if (isCurrentMonth()) {

            Calendar today =
                    Calendar.getInstance();

            if (end.after(today)) {

                end =
                        (Calendar) today.clone();
            }
        }

        return end;
    }

    // =========================================================
    // WEEKDAY
    // =========================================================

    private boolean isWeekday(
            Calendar calendar
    ) {

        int day =
                calendar.get(
                        Calendar.DAY_OF_WEEK
                );

        return day != Calendar.SATURDAY
                && day != Calendar.SUNDAY;
    }

    // =========================================================
    // PARSE DATE
    // =========================================================

    private Calendar parseDate(
            String dateText
    ) {

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                    );

            format.setLenient(false);

            Date date =
                    format.parse(
                            dateText
                    );

            if (date == null) {
                return null;
            }

            Calendar calendar =
                    Calendar.getInstance();

            calendar.setTime(date);

            return calendar;

        } catch (Exception e) {

            return null;
        }
    }

    // =========================================================
    // FORMAT DATE
    // =========================================================

    private String formatDate(
            Calendar calendar
    ) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                );

        return format.format(
                calendar.getTime()
        );
    }

    // =========================================================
    // CREATE TEXT VIEW
    // =========================================================

    private TextView createTextView(
            String text,
            int size,
            String color,
            boolean bold
    ) {

        TextView textView =
                new TextView(this);

        textView.setText(text);

        textView.setTextSize(size);

        textView.setTextColor(
                Color.parseColor(color)
        );

        if (bold) {

            textView.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    // =========================================================
    // TOP MARGIN
    // =========================================================

    private void setTopMargin(
            View view,
            int margin
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                margin,
                0,
                0
        );

        view.setLayoutParams(
                params
        );
    }

    // =========================================================
    // GO TO ADMIN LOGIN
    // =========================================================

    private void goToAdminLogin() {

        Intent intent =
                new Intent(
                        this,
                        AdminLoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }

    // =========================================================
    // EMPLOYEE REPORT MODEL
    // =========================================================

    private static class EmployeeReport {

        String employeeId;

        String email;

        int presentDays = 0;

        int wfoDays = 0;

        int wfhDays = 0;

        int lateDays = 0;

        int workingDays = 0;

        int approvedLeaveDays = 0;

        double attendancePercentage = 0.0;

        /*
         * Set prevents duplicate attendance dates
         * from being counted twice.
         */
        Set<String> presentDates =
                new HashSet<>();

        List<String> lateReasons =
                new ArrayList<>();

        EmployeeReport(
                String employeeId,
                String email
        ) {

            this.employeeId =
                    employeeId;

            this.email =
                    email;
        }
    }
}