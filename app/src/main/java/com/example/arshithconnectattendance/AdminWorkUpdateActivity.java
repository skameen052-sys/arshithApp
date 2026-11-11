package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminWorkUpdateActivity extends ComponentActivity {

    private FirebaseFirestore firestore;
    private LinearLayout employeesContainer;
    private TextView tvStatus;
    private TextView tvEmployeeCount;

    private final Map<String, EmployeeData> employees = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_work_updates);

        firestore = FirebaseFirestore.getInstance();

        employeesContainer = findViewById(R.id.employeesContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvEmployeeCount = findViewById(R.id.tvEmployeeCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadEmployees();
    }

    private void loadEmployees() {
        employees.clear();
        employeesContainer.removeAllViews();
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Loading employee work updates...");
        tvEmployeeCount.setText("0 Employees");

        firestore.collectionGroup("dailyUpdates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    employees.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String key = getEmployeeKey(doc);
                        if (key == null || key.trim().isEmpty()) continue;

                        EmployeeData data = employees.get(key);
                        if (data == null) {
                            data = new EmployeeData();
                            data.employeeKey = key;
                            employees.put(key, data);
                        }

                        String email = doc.getString("employeeEmail");
                        if (data.email == null && email != null) data.email = email;

                        data.totalUpdates++;

                        String date = getDate(doc);
                        if (date != null && !date.isEmpty()
                                && (data.latestDate == null || date.compareTo(data.latestDate) > 0)) {
                            data.latestDate = date;
                        }
                    }

                    if (employees.isEmpty()) {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("No employee work updates yet.");
                        tvEmployeeCount.setText("0 Employees");
                        return;
                    }

                    List<EmployeeData> list = new ArrayList<>(employees.values());
                    Collections.sort(list, Comparator.comparing(
                            item -> item.displayName == null ? "" : item.displayName.toLowerCase()
                    ));

                    tvEmployeeCount.setText(list.size() + (list.size() == 1 ? " Employee" : " Employees"));
                    tvStatus.setVisibility(View.GONE);

                    // Load employee profile names, then display cards.
                    for (EmployeeData item : list) loadEmployeeName(item);
                })
                .addOnFailureListener(e -> {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Unable to load updates.\n" + e.getMessage());
                    Toast.makeText(this, "Firestore error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String getEmployeeKey(DocumentSnapshot doc) {
        String employeeId = doc.getString("employeeId");
        if (employeeId != null && !employeeId.trim().isEmpty()) return employeeId.trim();

        // Existing structure: workUpdates/{employeeUID}/dailyUpdates/{updateID}
        if (doc.getReference().getParent() != null
                && doc.getReference().getParent().getParent() != null) {
            return doc.getReference().getParent().getParent().getId();
        }
        return null;
    }

    private String getDate(DocumentSnapshot doc) {
        String date = doc.getString("date");
        if (date != null && !date.trim().isEmpty()) return date;

        Object submittedAt = doc.get("submittedAt");
        if (submittedAt instanceof com.google.firebase.Timestamp) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(((com.google.firebase.Timestamp) submittedAt).toDate());
        }
        return null;
    }

    private void loadEmployeeName(EmployeeData data) {
        firestore.collection("employees")
                .document(data.employeeKey)
                .get()
                .addOnSuccessListener(profile -> {
                    String name = profile.getString("name");
                    if (isBlank(name)) name = profile.getString("employeeName");
                    if (isBlank(name)) name = data.email;
                    if (isBlank(name)) name = data.employeeKey;
                    data.displayName = name;
                    addEmployeeCard(data);
                })
                .addOnFailureListener(error -> {
                    data.displayName = isBlank(data.email) ? data.employeeKey : data.email;
                    addEmployeeCard(data);
                });
    }

    private void addEmployeeCard(EmployeeData data) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(getDrawable(R.drawable.bg_work_card));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        employeesContainer.addView(card, cardParams);

        TextView name = makeText(data.displayName, 19, true, 0xFF063F3B);
        card.addView(name);

        TextView email = makeText(safe(data.email), 14, false, 0xFF687875);
        LinearLayout.LayoutParams emailParams = new LinearLayout.LayoutParams(-1, -2);
        emailParams.topMargin = dp(4);
        card.addView(email, emailParams);

        card.addView(makeText("Total Submitted Updates: " + data.totalUpdates,
                14, false, 0xFF52635C));

        card.addView(makeText("Latest Update: " + safe(data.latestDate),
                14, false, 0xFF52635C));

        Button viewButton = new Button(this);
        viewButton.setText("View Work History");
        viewButton.setAllCaps(false);
        viewButton.setTextColor(0xFFFFFFFF);
        viewButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF078477));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonParams.topMargin = dp(12);
        card.addView(viewButton, buttonParams);

        viewButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmployeeWorkHistoryActivity.class);
            intent.putExtra("employeeKey", data.employeeKey);
            intent.putExtra("employeeName", data.displayName);
            intent.putExtra("employeeEmail", safe(data.email));
            startActivity(intent);
        });
    }

    private TextView makeText(String value, float size, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return isBlank(value) ? "-" : value;
    }

    private static class EmployeeData {
        String employeeKey;
        String displayName;
        String email;
        String latestDate;
        int totalUpdates;
    }
}