package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EmployeeWorkHistoryActivity extends ComponentActivity {

    private FirebaseFirestore firestore;
    private String employeeKey;
    private String employeeName;
    private String employeeEmail;

    private LinearLayout datesContainer;
    private TextView tvStatus;
    private TextView tvDateCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_work_history);

        firestore = FirebaseFirestore.getInstance();

        employeeKey = getIntent().getStringExtra("employeeKey");
        employeeName = getIntent().getStringExtra("employeeName");
        employeeEmail = getIntent().getStringExtra("employeeEmail");

        if (employeeName == null) employeeName = "Employee";
        if (employeeEmail == null) employeeEmail = "";

        datesContainer = findViewById(R.id.datesContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvDateCount = findViewById(R.id.tvDateCount);

        ((TextView) findViewById(R.id.tvEmployeeName)).setText(employeeName);
        ((TextView) findViewById(R.id.tvEmployeeEmail)).setText(employeeEmail);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadDates();
    }

    private void loadDates() {
        datesContainer.removeAllViews();
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Loading daily work dates...");

        if (employeeKey == null || employeeKey.trim().isEmpty()) {
            tvStatus.setText("Employee ID is missing.");
            return;
        }

        firestore.collectionGroup("dailyUpdates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, DateSummary> byDate = new LinkedHashMap<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!employeeKey.equals(getEmployeeKey(doc))) continue;

                        String date = getDate(doc);
                        if (date == null || date.isEmpty()) continue;

                        DateSummary summary = byDate.get(date);
                        if (summary == null) {
                            summary = new DateSummary();
                            summary.date = date;
                            byDate.put(date, summary);
                        }
                        summary.documents.add(doc);
                    }

                    if (byDate.isEmpty()) {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("No daily work updates found.");
                        tvDateCount.setText("0 Days");
                        return;
                    }

                    List<DateSummary> dates = new ArrayList<>(byDate.values());
                    Collections.sort(dates,
                            (a, b) -> b.date.compareTo(a.date));

                    tvDateCount.setText(dates.size() +
                            (dates.size() == 1 ? " Work Day" : " Work Days"));
                    tvStatus.setVisibility(View.GONE);

                    for (DateSummary summary : dates) addDateCard(summary);
                })
                .addOnFailureListener(e -> {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Unable to load dates:\n" + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String getEmployeeKey(DocumentSnapshot doc) {
        String id = doc.getString("employeeId");
        if (id != null && !id.trim().isEmpty()) return id.trim();

        if (doc.getReference().getParent() != null
                && doc.getReference().getParent().getParent() != null) {
            return doc.getReference().getParent().getParent().getId();
        }
        return null;
    }

    private String getDate(DocumentSnapshot doc) {
        String date = doc.getString("date");
        if (date != null && !date.trim().isEmpty()) return date;

        Object value = doc.get("submittedAt");
        if (value instanceof Timestamp) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(((Timestamp) value).toDate());
        }
        return null;
    }

    private void addDateCard(DateSummary summary) {
        // Sort the sections before opening the selected day.
        Collections.sort(summary.documents, (a, b) -> {
            long sectionA = getSectionNumber(a);
            long sectionB = getSectionNumber(b);
            if (sectionA != sectionB) return Long.compare(sectionA, sectionB);
            return getSubmittedMillis(a).compareTo(getSubmittedMillis(b));
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(getDrawable(R.drawable.bg_work_card));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(13);
        datesContainer.addView(card, params);

        TextView date = makeText(formatDate(summary.date), 19, true, 0xFF087F70);
        card.addView(date);

        int sectionCount = summary.documents.size();
        TextView sections = makeText(sectionCount +
                        (sectionCount == 1 ? " submitted section" : " submitted sections"),
                14, false, 0xFF63736F);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(5);
        card.addView(sections, sp);

        TextView hint = makeText("Tap to view this day's work updates  ›",
                14, true, 0xFF075C55);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(12);
        card.addView(hint, hp);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmployeeWorkUpdateDateActivity.class);
            intent.putExtra("employeeKey", employeeKey);
            intent.putExtra("employeeName", employeeName);
            intent.putExtra("employeeEmail", employeeEmail);
            intent.putExtra("selectedDate", summary.date);
            startActivity(intent);
        });
    }

    private String formatDate(String date) {
        try {
            return new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
        } catch (Exception e) {
            return date;
        }
    }

    private long getSectionNumber(DocumentSnapshot doc) {
        Number number = (Number) doc.get("section");
        return number == null ? Long.MAX_VALUE : number.longValue();
    }

    private Long getSubmittedMillis(DocumentSnapshot doc) {
        Object value = doc.get("submittedAt");
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        return 0L;
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

    private static class DateSummary {
        String date;
        List<DocumentSnapshot> documents = new ArrayList<>();
    }
}