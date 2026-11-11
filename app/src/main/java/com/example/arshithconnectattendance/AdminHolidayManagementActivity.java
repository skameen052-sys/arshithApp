package com.example.arshithconnectattendance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AdminHolidayManagementActivity
        extends ComponentActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private EditText etHolidayName;

    private TextView tvHolidayDate;
    private TextView tvHolidayCount;
    private TextView tvNoHolidays;

    private Button btnBack;
    private Button btnAddHoliday;
    private Button btnRefresh;

    private LinearLayout holidaysContainer;

    private String selectedHolidayDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_holiday_management
        );

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            goToAdminLogin();
            return;
        }

        initializeViews();
        setupButtons();

        loadHolidays();
    }

    private void initializeViews() {

        etHolidayName =
                findViewById(R.id.etHolidayName);

        tvHolidayDate =
                findViewById(R.id.tvHolidayDate);

        tvHolidayCount =
                findViewById(R.id.tvHolidayCount);

        tvNoHolidays =
                findViewById(R.id.tvNoHolidays);

        btnBack =
                findViewById(R.id.btnBack);

        btnAddHoliday =
                findViewById(R.id.btnAddHoliday);

        btnRefresh =
                findViewById(R.id.btnRefresh);

        holidaysContainer =
                findViewById(R.id.holidaysContainer);
    }

    private void setupButtons() {

        btnBack.setOnClickListener(v -> finish());

        tvHolidayDate.setOnClickListener(
                v -> showDatePicker()
        );

        btnAddHoliday.setOnClickListener(
                v -> addHoliday()
        );

        btnRefresh.setOnClickListener(
                v -> loadHolidays()
        );
    }

    private void showDatePicker() {

        Calendar calendar = Calendar.getInstance();

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            Calendar selected =
                                    Calendar.getInstance();

                            selected.set(
                                    year,
                                    month,
                                    dayOfMonth
                            );

                            selectedHolidayDate =
                                    new SimpleDateFormat(
                                            "yyyy-MM-dd",
                                            Locale.getDefault()
                                    ).format(
                                            selected.getTime()
                                    );

                            tvHolidayDate.setText(
                                    selectedHolidayDate
                            );
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );

        dialog.show();
    }

    private void addHoliday() {

        String holidayName =
                etHolidayName
                        .getText()
                        .toString()
                        .trim();

        if (holidayName.isEmpty()) {

            etHolidayName.setError(
                    "Enter holiday name"
            );

            etHolidayName.requestFocus();

            return;
        }

        if (selectedHolidayDate.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select holiday date",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnAddHoliday.setEnabled(false);

        java.util.HashMap<String, Object> holiday =
                new java.util.HashMap<>();

        holiday.put(
                "name",
                holidayName
        );

        holiday.put(
                "date",
                selectedHolidayDate
        );

        holiday.put(
                "createdAt",
                new Timestamp(new Date())
        );

        holiday.put(
                "createdBy",
                currentUser.getUid()
        );

        firestore.collection("holidays")
                .add(holiday)
                .addOnSuccessListener(documentReference -> {

                    Toast.makeText(
                            this,
                            "Holiday added successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    etHolidayName.setText("");

                    selectedHolidayDate = "";

                    tvHolidayDate.setText(
                            "Select holiday date"
                    );

                    btnAddHoliday.setEnabled(true);

                    loadHolidays();
                })
                .addOnFailureListener(e -> {

                    btnAddHoliday.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Unable to add holiday: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void loadHolidays() {

        holidaysContainer.removeAllViews();

        tvNoHolidays.setVisibility(
                View.GONE
        );

        tvHolidayCount.setText("0");

        firestore.collection("holidays")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    int count = 0;

                    for (DocumentSnapshot document :
                            querySnapshot) {

                        count++;

                        addHolidayCard(document);
                    }

                    tvHolidayCount.setText(
                            String.valueOf(count)
                    );

                    if (count == 0) {

                        tvNoHolidays.setVisibility(
                                View.VISIBLE
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    tvNoHolidays.setVisibility(
                            View.VISIBLE
                    );

                    tvNoHolidays.setText(
                            "Unable to load holidays."
                    );

                    Toast.makeText(
                            this,
                            "Holiday loading failed.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void addHolidayCard(
            DocumentSnapshot document
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
                14
        );

        card.setLayoutParams(
                cardParams
        );

        String name =
                document.getString("name");

        if (name == null) {
            name = "Holiday";
        }

        TextView nameView =
                createTextView(
                        name,
                        18,
                        "#251E32",
                        true
                );

        card.addView(nameView);

        String date =
                document.getString("date");

        if (date == null) {
            date = "Unknown date";
        }

        TextView dateView =
                createTextView(
                        "Date: " + date,
                        14,
                        "#7651E8",
                        true
                );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                8,
                0,
                8
        );

        dateView.setLayoutParams(
                dateParams
        );

        card.addView(dateView);

        Button deleteButton =
                new Button(this);

        deleteButton.setText(
                "DELETE"
        );

        deleteButton.setTextColor(
                Color.WHITE
        );

        deleteButton.setBackgroundColor(
                Color.rgb(
                        210,
                        70,
                        70
                )
        );

        deleteButton.setOnClickListener(
                v -> deleteHoliday(document)
        );

        card.addView(
                deleteButton
        );

        holidaysContainer.addView(
                card
        );
    }

    private void deleteHoliday(
            DocumentSnapshot document
    ) {

        firestore.collection("holidays")
                .document(document.getId())
                .delete()
                .addOnSuccessListener(unused -> {

                    Toast.makeText(
                            this,
                            "Holiday deleted",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadHolidays();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to delete holiday",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

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
}
