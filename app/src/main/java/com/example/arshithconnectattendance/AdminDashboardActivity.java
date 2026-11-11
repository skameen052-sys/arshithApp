
package com.example.arshithconnectattendance;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.arshithconnectattendance.chatnew.activities.ChatInboxActivity;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AdminDashboardActivity extends ComponentActivity {

    // FIREBASE
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // ADMIN DRAWER
    private DrawerLayout adminDrawerLayout;
    private NavigationView adminNavigationView;
    private ImageView btnAdminMenu;

    // HEADER
    private TextView tvAdminName;
    private TextView tvAdminEmail;
    private Button btnRefreshDashboard;

    private boolean isRefreshing = false;
    private int refreshTasksRemaining = 0;

    // DASHBOARD STATISTICS
    private TextView tvTodayAttendance;
    private TextView tvWfoCount;
    private TextView tvWfhCount;
    private TextView tvLateCount;
    private TextView tvPendingLeaves;
    private TextView tvHolidayCount;

    // MANAGEMENT CARDS
    private LinearLayout cardAttendanceManagement;
    private LinearLayout cardLeaveManagement;
    private LinearLayout cardHolidayManagement;
    private LinearLayout cardReports;
    private LinearLayout cardMeetingManagement;
    private LinearLayout cardAnnouncementManagement;

    private LinearLayout cardOfficeTimings;
    private LinearLayout cardEmployees;
    private LinearLayout cardTeams;
    private LinearLayout cardWorkAssignments;
    private LinearLayout cardWorkUpdates;
    private LinearLayout cardEmployeeList;
    private LinearLayout cardRegistrationRequests;

    // LOGOUT
    private Button btnLogout;

    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_admin_dashboard);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        initializeViews();

        setupAdminDrawer();
        setupManagementCards();
        setupRefreshButton();
        setupLogout();
        setupBackButton();

        verifyAdminAndLoadDashboard();
    }

    // =====================================================
    // INITIALIZE VIEWS
    // =====================================================

    private void initializeViews() {

        adminDrawerLayout = findViewById(R.id.adminDrawerLayout);
        adminNavigationView = findViewById(R.id.adminNavigationView);
        btnAdminMenu = findViewById(R.id.btnAdminMenu);

        tvAdminName = findViewById(R.id.tvAdminName);
        tvAdminEmail = findViewById(R.id.tvAdminEmail);
        btnRefreshDashboard = findViewById(R.id.btnRefreshDashboard);

        tvTodayAttendance = findViewById(R.id.tvTodayAttendance);
        tvWfoCount = findViewById(R.id.tvWfoCount);
        tvWfhCount = findViewById(R.id.tvWfhCount);
        tvLateCount = findViewById(R.id.tvLateCount);
        tvPendingLeaves = findViewById(R.id.tvPendingLeaves);
        tvHolidayCount = findViewById(R.id.tvHolidayCount);

        cardAttendanceManagement = findViewById(R.id.cardAttendanceManagement);
        cardLeaveManagement = findViewById(R.id.cardLeaveManagement);
        cardHolidayManagement = findViewById(R.id.cardHolidayManagement);
        cardReports = findViewById(R.id.cardReports);
        cardMeetingManagement = findViewById(R.id.cardMeetingManagement);
        cardAnnouncementManagement = findViewById(R.id.cardAnnouncementManagement);

        cardOfficeTimings = findViewById(R.id.cardOfficeTimings);
        cardEmployeeList = findViewById(R.id.cardEmployeeList);
        cardRegistrationRequests = findViewById(R.id.cardRegistrationRequests);
        cardEmployees = findViewById(R.id.cardEmployees);
        cardTeams = findViewById(R.id.cardTeams);
        cardWorkAssignments = findViewById(R.id.cardWorkAssignments);
        cardWorkUpdates = findViewById(R.id.cardWorkUpdates);

        btnLogout = findViewById(R.id.btnLogout);
    }

    // =====================================================
    // ADMIN DRAWER
    // =====================================================

    private void setupAdminDrawer() {

        if (adminDrawerLayout == null) {
            return;
        }

        adminDrawerLayout.setDrawerLockMode(
                DrawerLayout.LOCK_MODE_UNLOCKED
        );

        if (adminNavigationView != null) {
            adminNavigationView.bringToFront();
        }

        if (btnAdminMenu != null) {
            btnAdminMenu.setOnClickListener(v -> {

                if (adminDrawerLayout.isDrawerOpen(
                        GravityCompat.START)) {

                    adminDrawerLayout.closeDrawer(
                            GravityCompat.START
                    );

                } else {

                    adminDrawerLayout.openDrawer(
                            GravityCompat.START
                    );
                }
            });
        }

        if (adminNavigationView != null) {

            adminNavigationView.setNavigationItemSelectedListener(item -> {

                int id = item.getItemId();

                // DASHBOARD
                if (id == R.id.admin_nav_dashboard) {

                    adminDrawerLayout.closeDrawer(
                            GravityCompat.START
                    );
                }

                // EMPLOYEES
                else if (id == R.id.admin_nav_employees) {

                    openActivity(
                            AdminEmployeeManagementActivity.class
                    );
                }

                // EMPLOYEE LIST
                else if (id == R.id.admin_nav_employee_list) {

                    openActivity(
                            AdminEmployeeListActivity.class
                    );
                }

                // REGISTRATION REQUESTS
                else if (id == R.id.admin_nav_registration_requests) {

                    openActivity(
                            AdminRegistrationRequestsActivity.class
                    );
                }

                // TEAMS
                else if (id == R.id.admin_nav_teams) {

                    openActivity(
                            AdminTeamManagementActivity.class
                    );
                }

                // ATTENDANCE
                else if (id == R.id.admin_nav_attendance) {

                    openActivity(
                            AdminAttendanceManagementActivity.class
                    );
                }

                // LEAVE
                else if (id == R.id.admin_nav_leave) {

                    openActivity(
                            AdminLeaveManagementActivity.class
                    );
                }

                // HOLIDAYS
                else if (id == R.id.admin_nav_holidays) {

                    openActivity(
                            AdminHolidayManagementActivity.class
                    );
                }

                // MEETINGS
                else if (id == R.id.admin_nav_meetings) {

                    openActivity(
                            AdminMeetingManagementActivity.class
                    );
                }

                // ANNOUNCEMENTS
                else if (id == R.id.admin_nav_announcements) {

                    openActivity(
                            AdminAnnouncementManagementActivity.class
                    );
                }

                // WORK ASSIGNMENTS
                else if (id == R.id.admin_nav_work_assignments) {

                    openActivity(
                            AdminWorkAssignmentActivity.class
                    );
                }

                // WORK UPDATES
                else if (id == R.id.admin_nav_work_updates) {

                    openActivity(
                            AdminWorkUpdateActivity.class
                    );
                }

                // CHAT
                else if (id == R.id.admin_nav_chat) {

                    openActivity(
                            ChatInboxActivity.class
                    );
                }

                adminDrawerLayout.closeDrawer(
                        GravityCompat.START
                );

                return true;
            });
        }
    }

    // =====================================================
    // OPEN ACTIVITY
    // =====================================================

    private void openActivity(Class<?> activityClass) {

        try {

            Intent intent = new Intent(
                    AdminDashboardActivity.this,
                    activityClass
            );

            startActivity(intent);

            if (adminDrawerLayout != null &&
                    adminDrawerLayout.isDrawerOpen(
                            GravityCompat.START)) {

                adminDrawerLayout.closeDrawer(
                        GravityCompat.START
                );
            }

        } catch (Exception e) {

            Log.e(
                    "AdminDashboard",
                    "Unable to open screen",
                    e
            );

            Toast.makeText(
                    this,
                    "Unable to open screen: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =====================================================
    // MANAGEMENT CARDS
    // =====================================================

    private void setupManagementCards() {

        if (cardAttendanceManagement != null) {
            cardAttendanceManagement.setOnClickListener(v ->
                    openActivity(AdminAttendanceManagementActivity.class)
            );
        }

        if (cardLeaveManagement != null) {
            cardLeaveManagement.setOnClickListener(v ->
                    openActivity(AdminLeaveManagementActivity.class)
            );
        }

        if (cardHolidayManagement != null) {
            cardHolidayManagement.setOnClickListener(v ->
                    openActivity(AdminHolidayManagementActivity.class)
            );
        }

        if (cardReports != null) {
            cardReports.setOnClickListener(v ->
                    openActivity(AdminAttendanceReportsActivity.class)
            );
        }

        if (cardMeetingManagement != null) {
            cardMeetingManagement.setOnClickListener(v ->
                    openActivity(AdminMeetingManagementActivity.class)
            );
        }

        if (cardAnnouncementManagement != null) {
            cardAnnouncementManagement.setOnClickListener(v ->
                    openActivity(AdminAnnouncementManagementActivity.class)
            );
        }

        if (cardOfficeTimings != null) {
            cardOfficeTimings.setOnClickListener(v ->
                    openActivity(AdminOfficeTimingsActivity.class)
            );
        }

        if (cardEmployeeList != null) {
            cardEmployeeList.setOnClickListener(v ->
                    openActivity(AdminEmployeeListActivity.class)
            );
        }

        if (cardEmployees != null) {
            cardEmployees.setOnClickListener(v ->
                    openActivity(AdminEmployeeManagementActivity.class)
            );
        }

        if (cardTeams != null) {
            cardTeams.setOnClickListener(v ->
                    openActivity(AdminTeamManagementActivity.class)
            );
        }

        if (cardRegistrationRequests != null) {
            cardRegistrationRequests.setOnClickListener(v ->
                    openActivity(AdminRegistrationRequestsActivity.class)
            );
        }

        if (cardWorkAssignments != null) {
            cardWorkAssignments.setOnClickListener(v ->
                    openActivity(AdminWorkAssignmentActivity.class)
            );
        }

        if (cardWorkUpdates != null) {
            cardWorkUpdates.setOnClickListener(v ->
                    openActivity(AdminWorkUpdateActivity.class)
            );
        }
    }

    // =====================================================
    // REFRESH BUTTON
    // =====================================================

    private void setupRefreshButton() {

        if (btnRefreshDashboard != null) {
            btnRefreshDashboard.setOnClickListener(
                    v -> refreshDashboard()
            );
        }
    }

    private void refreshDashboard() {

        if (isRefreshing) {
            return;
        }

        isRefreshing = true;
        refreshTasksRemaining = 3;

        if (btnRefreshDashboard != null) {
            btnRefreshDashboard.setEnabled(false);
            btnRefreshDashboard.setText("Refreshing...");
        }

        loadDashboardData(true);
    }

    private void finishRefreshTask() {

        if (!isRefreshing) {
            return;
        }

        refreshTasksRemaining--;

        if (refreshTasksRemaining <= 0) {

            refreshTasksRemaining = 0;
            isRefreshing = false;

            if (btnRefreshDashboard != null) {
                btnRefreshDashboard.setEnabled(true);
                btnRefreshDashboard.setText("↻ Refresh Dashboard");
            }

            Toast.makeText(
                    this,
                    "Dashboard refresh completed",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =====================================================
    // VERIFY ADMIN
    // =====================================================

    private void verifyAdminAndLoadDashboard() {

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        String uid = currentUser.getUid();

        firestore.collection("admins")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        firebaseAuth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    String role = documentSnapshot.getString("role");

                    if (role == null ||
                            !"admin".equalsIgnoreCase(role)) {

                        Toast.makeText(
                                this,
                                "Admin access denied.",
                                Toast.LENGTH_LONG
                        ).show();

                        firebaseAuth.signOut();
                        goToAdminLogin();
                        return;
                    }

                    String name = documentSnapshot.getString("name");

                    if (name == null || name.trim().isEmpty()) {
                        name = "Administrator";
                    }

                    if (tvAdminName != null) {
                        tvAdminName.setText(name);
                    }

                    if (currentUser.getEmail() != null &&
                            tvAdminEmail != null) {

                        tvAdminEmail.setText(currentUser.getEmail());
                    }

                    loadDashboardData(false);
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "AdminDashboard",
                            "Unable to verify admin",
                            e
                    );

                    Toast.makeText(
                            this,
                            "Unable to verify admin: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // =====================================================
    // LOAD DASHBOARD
    // =====================================================

    private void loadDashboardData() {
        loadDashboardData(false);
    }

    private void loadDashboardData(boolean isRefresh) {

        loadTodayAttendance(isRefresh);
        loadPendingLeaves(isRefresh);
        loadHolidayCount(isRefresh);
    }

    // =====================================================
    // TODAY ATTENDANCE
    // =====================================================

    private void loadTodayAttendance() {
        loadTodayAttendance(false);
    }

    private void loadTodayAttendance(boolean isRefreshTask) {

        String today = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
        ).format(new Date());

        firestore.collectionGroup("records")
                .whereEqualTo("date", today)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int total = 0;
                    int wfo = 0;
                    int wfh = 0;
                    int late = 0;

                    for (DocumentSnapshot document : querySnapshot) {

                        total++;

                        String workMode = document.getString("workMode");

                        if ("WFO".equalsIgnoreCase(workMode)) {
                            wfo++;
                        }

                        if ("WFH".equalsIgnoreCase(workMode)) {
                            wfh++;
                        }

                        Boolean lateLogin = document.getBoolean("lateLogin");

                        if (Boolean.TRUE.equals(lateLogin)) {
                            late++;
                        }
                    }

                    if (tvTodayAttendance != null) {
                        tvTodayAttendance.setText(String.valueOf(total));
                    }

                    if (tvWfoCount != null) {
                        tvWfoCount.setText(String.valueOf(wfo));
                    }

                    if (tvWfhCount != null) {
                        tvWfhCount.setText(String.valueOf(wfh));
                    }

                    if (tvLateCount != null) {
                        tvLateCount.setText(String.valueOf(late));
                    }

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "AdminDashboard",
                            "Unable to load attendance",
                            e
                    );

                    Toast.makeText(
                            AdminDashboardActivity.this,
                            "Unable to refresh attendance: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                });
    }

    // =====================================================
    // PENDING LEAVES
    // =====================================================

    private void loadPendingLeaves() {
        loadPendingLeaves(false);
    }

    private void loadPendingLeaves(boolean isRefreshTask) {

        firestore.collectionGroup("requests")
                .whereEqualTo("status", "Pending")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int pendingCount = querySnapshot.size();

                    if (tvPendingLeaves != null) {
                        tvPendingLeaves.setText(
                                String.valueOf(pendingCount)
                        );
                    }

                    Log.d(
                            "PendingLeaves",
                            "Successfully loaded pending leave requests. Count: "
                                    + pendingCount
                    );

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "PendingLeaves",
                            "Unable to refresh pending leaves",
                            e
                    );

                    Toast.makeText(
                            AdminDashboardActivity.this,
                            "Unable to refresh pending leaves: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                });
    }

    // =====================================================
    // HOLIDAY COUNT
    // =====================================================

    private void loadHolidayCount() {
        loadHolidayCount(false);
    }

    private void loadHolidayCount(boolean isRefreshTask) {

        Calendar calendar = Calendar.getInstance();

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);

        firestore.collection("holidays")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int count = 0;

                    for (DocumentSnapshot document : querySnapshot) {

                        String date = document.getString("date");

                        if (date == null || date.trim().isEmpty()) {
                            date = document.getId();
                        }

                        try {

                            SimpleDateFormat format = new SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.getDefault()
                            );

                            format.setLenient(false);

                            Date holidayDate = format.parse(date);

                            if (holidayDate == null) {
                                continue;
                            }

                            Calendar holidayCalendar = Calendar.getInstance();
                            holidayCalendar.setTime(holidayDate);

                            if (holidayCalendar.get(Calendar.YEAR) == year
                                    && holidayCalendar.get(Calendar.MONTH) == month) {

                                count++;
                            }

                        } catch (Exception e) {

                            Log.w(
                                    "AdminDashboard",
                                    "Invalid holiday date: " + date,
                                    e
                            );
                        }
                    }

                    if (tvHolidayCount != null) {
                        tvHolidayCount.setText(String.valueOf(count));
                    }

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "AdminDashboard",
                            "Unable to load holidays",
                            e
                    );

                    Toast.makeText(
                            AdminDashboardActivity.this,
                            "Unable to refresh holidays: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    if (isRefreshTask) {
                        finishRefreshTask();
                    }
                });
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    private void setupLogout() {

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logoutAdmin());
        }
    }

    private void logoutAdmin() {

        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }

        getSharedPreferences(
                "ArshithConnectPrefs",
                MODE_PRIVATE
        )
                .edit()
                .remove("loginType")
                .apply();

        Intent intent = new Intent(
                AdminDashboardActivity.this,
                MainActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    // =====================================================
    // ADMIN LOGIN
    // =====================================================

    private void goToAdminLogin() {

        Intent intent = new Intent(
                AdminDashboardActivity.this,
                AdminLoginActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    // =====================================================
    // BACK BUTTON
    // =====================================================

    private void setupBackButton() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (adminDrawerLayout != null &&
                                adminDrawerLayout.isDrawerOpen(
                                        GravityCompat.START)) {

                            adminDrawerLayout.closeDrawer(
                                    GravityCompat.START
                            );

                        } else {

                            setEnabled(false);

                            getOnBackPressedDispatcher()
                                    .onBackPressed();
                        }
                    }
                }
        );
    }
}
