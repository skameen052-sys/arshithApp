package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AdminEmployeeManagementActivity extends ComponentActivity {

    private FirebaseFirestore firestore;

    private LinearLayout employeesContainer;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_employee_management
        );

        firestore =
                FirebaseFirestore.getInstance();

        employeesContainer =
                findViewById(
                        R.id.employeesContainer
                );

        tvEmpty =
                findViewById(
                        R.id.tvEmpty
                );

        loadEmployees();
    }

    // =========================================================
    // LOAD EMPLOYEES
    // =========================================================

    private void loadEmployees() {

        employeesContainer.removeAllViews();

        tvEmpty.setVisibility(
                TextView.GONE
        );

        firestore
                .collection("employees")
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            List<DocumentSnapshot> employees =
                                    new ArrayList<>(
                                            querySnapshot.getDocuments()
                                    );

                            Collections.sort(
                                    employees,
                                    Comparator.comparing(
                                            document -> {

                                                String name =
                                                        document.getString(
                                                                "name"
                                                        );

                                                if (name == null) {
                                                    return "";
                                                }

                                                return name.toLowerCase();
                                            }
                                    )
                            );

                            if (employees.isEmpty()) {

                                tvEmpty.setVisibility(
                                        TextView.VISIBLE
                                );

                                return;
                            }

                            for (DocumentSnapshot document :
                                    employees) {

                                addEmployeeCard(
                                        document
                                );
                            }
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
    // EMPLOYEE CARD
    // =========================================================

    private void addEmployeeCard(
            DocumentSnapshot document
    ) {

        String uid =
                document.getId();

        String employeeId =
                document.getString(
                        "employeeId"
                );

        String employeeName = document.getString("name");

        String email =
                document.getString(
                        "email"
                );

        String role =
                document.getString(
                        "role"
                );

        if (employeeName == null || employeeName.trim().isEmpty()) {
            employeeName = "Unnamed Employee";
        }

        final String finalEmployeeName = employeeName;

        if (employeeId == null) {
            employeeId = "-";
        }

        if (email == null) {
            email = "-";
        }

        if (role == null) {
            role = "Employee";
        }

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );

        card.setBackgroundColor(
                Color.WHITE
        );

        card.setElevation(
                dp(3)
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
                dp(14)
        );

        card.setLayoutParams(
                cardParams
        );

        TextView tvName =
                createText(
                        finalEmployeeName,
                        20,
                        Color.rgb(
                                37,
                                30,
                                50
                        )
                );

        tvName.setGravity(
                Gravity.START
        );

        card.addView(
                tvName
        );

        TextView tvDetails =
                createText(
                        "Employee ID: "
                                + employeeId
                                + "\nEmail: "
                                + email
                                + "\nRole: "
                                + role,
                        14,
                        Color.rgb(
                                117,
                                112,
                                128
                        )
                );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        detailsParams.setMargins(
                0,
                dp(8),
                0,
                dp(10)
        );

        tvDetails.setLayoutParams(
                detailsParams
        );

        card.addView(
                tvDetails
        );

        Button btnAssignment =
                new Button(this);

        btnAssignment.setText(
                "CREATE INDIVIDUAL ASSIGNMENT"
        );

        btnAssignment.setTextColor(
                Color.WHITE
        );

        btnAssignment.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(
                                118,
                                81,
                                232
                        )
                )
        );

        btnAssignment.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    AdminWorkAssignmentActivity.class
                            );

                    intent.putExtra(
                            "mode",
                            "INDIVIDUAL"
                    );

                    intent.putExtra(
                            "employeeId",
                            uid
                    );

                    intent.putExtra(
                            "employeeName",
                            finalEmployeeName
                    );

                    startActivity(
                            intent
                    );
                }
        );

        card.addView(
                btnAssignment
        );

        card.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    AdminWorkAssignmentActivity.class
                            );

                    intent.putExtra(
                            "mode",
                            "INDIVIDUAL"
                    );

                    intent.putExtra(
                            "employeeId",
                            uid
                    );

                    intent.putExtra(
                            "employeeName",
                            finalEmployeeName
                    );

                    startActivity(
                            intent
                    );
                }
        );

        employeesContainer.addView(
                card
        );
    }

    // =========================================================
    // TEXT HELPER
    // =========================================================

    private TextView createText(
            String text,
            int size,
            int color
    ) {

        TextView textView =
                new TextView(this);

        textView.setText(text);

        textView.setTextSize(
                size
        );

        textView.setTextColor(
                color
        );

        return textView;
    }

    // =========================================================
    // DP
    // =========================================================

    private int dp(int value) {

        return (int)
                (
                        value
                                * getResources()
                                .getDisplayMetrics()
                                .density
                );
    }
}