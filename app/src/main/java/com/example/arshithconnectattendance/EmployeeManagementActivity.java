package com.example.arshithconnectattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class EmployeeManagementActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;

    private LinearLayout employeeContainer;

    private TextView tvNoEmployees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_employee_management);

        firestore =
                FirebaseFirestore.getInstance();

        employeeContainer =
                findViewById(
                        R.id.employeeContainer
                );

        tvNoEmployees =
                findViewById(
                        R.id.tvNoEmployees
                );

        loadEmployees();
    }

    private void loadEmployees() {

        firestore
                .collection("employees")
                .orderBy(
                        "name",
                        Query.Direction.ASCENDING
                )
                .get()
                .addOnSuccessListener(
                        snapshot -> {

                            employeeContainer
                                    .removeAllViews();

                            if (snapshot.isEmpty()) {

                                tvNoEmployees.setVisibility(
                                        View.VISIBLE
                                );

                                return;
                            }

                            tvNoEmployees.setVisibility(
                                    View.GONE
                            );

                            for (DocumentSnapshot document :
                                    snapshot.getDocuments()) {

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

    private void addEmployeeCard(
            DocumentSnapshot document
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setBackgroundColor(
                0xFFFFFFFF
        );

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

        card.setLayoutParams(
                cardParams
        );

        card.setElevation(
                dp(3)
        );

        String employeeId =
                document.getString(
                        "employeeId"
                );

        String name =
                document.getString(
                        "name"
                );

        String email =
                document.getString(
                        "email"
                );

        if (name == null ||
                name.trim().isEmpty()) {

            name = "Employee";
        }

        if (email == null) {
            email = "";
        }

        if (employeeId == null) {
            employeeId = "";
        }

        TextView tvName =
                new TextView(this);

        tvName.setText(
                name
        );

        tvName.setTextColor(
                0xFF241E31
        );

        tvName.setTextSize(
                19
        );

        tvName.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        card.addView(
                tvName
        );

        TextView tvEmail =
                new TextView(this);

        tvEmail.setText(
                email
        );

        tvEmail.setTextColor(
                0xFF5E576A
        );

        tvEmail.setTextSize(
                14
        );

        LinearLayout.LayoutParams emailParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        emailParams.setMargins(
                0,
                dp(6),
                0,
                0
        );

        tvEmail.setLayoutParams(
                emailParams
        );

        card.addView(
                tvEmail
        );

        TextView tvEmployeeId =
                new TextView(this);

        tvEmployeeId.setText(
                "Employee ID: " +
                        employeeId
        );

        tvEmployeeId.setTextColor(
                0xFF918A9B
        );

        tvEmployeeId.setTextSize(
                13
        );

        LinearLayout.LayoutParams idParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        idParams.setMargins(
                0,
                dp(5),
                0,
                0
        );

        tvEmployeeId.setLayoutParams(
                idParams
        );

        card.addView(
                tvEmployeeId
        );

        Button btnAssign =
                new Button(this);

        btnAssign.setText(
                "Create Work Assignment"
        );

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.setMargins(
                0,
                dp(14),
                0,
                0
        );

        btnAssign.setLayoutParams(
                buttonParams
        );

        card.addView(
                btnAssign
        );

        String finalName = name;
        String finalEmployeeId = employeeId;

        btnAssign.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            EmployeeManagementActivity.this,
                            AdminWorkAssignmentActivity.class
                    );

            intent.putExtra(
                    "assignmentType",
                    "INDIVIDUAL"
            );

            intent.putExtra(
                    "employeeId",
                    document.getId()
            );

            intent.putExtra(
                    "employeeName",
                    finalName
            );

            intent.putExtra(
                    "employeeCode",
                    finalEmployeeId
            );

            startActivity(
                    intent
            );
        });

        employeeContainer.addView(
                card
        );
    }

    private int dp(
            int value
    ) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    @Override
    protected void onResume() {

        super.onResume();

        loadEmployees();
    }
}
