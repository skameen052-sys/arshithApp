package com.example.arshithconnectattendance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class EmployeeWorkUpdateDateActivity extends ComponentActivity {

    private FirebaseFirestore firestore;

    private String employeeKey;
    private String employeeName;
    private String employeeEmail;
    private String selectedDate;

    private LinearLayout sectionsContainer;
    private TextView tvStatus;
    private TextView tvSectionCount;

    private final List<DocumentSnapshot> selectedDayUpdates = new ArrayList<>();

    private ActivityResultLauncher<String> createPdfLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_work_update_date);

        firestore = FirebaseFirestore.getInstance();

        employeeKey = getIntent().getStringExtra("employeeKey");
        employeeName = getIntent().getStringExtra("employeeName");
        employeeEmail = getIntent().getStringExtra("employeeEmail");
        selectedDate = getIntent().getStringExtra("selectedDate");

        if (employeeName == null) employeeName = "Employee";
        if (employeeEmail == null) employeeEmail = "";
        if (selectedDate == null) selectedDate = "";

        sectionsContainer = findViewById(R.id.sectionsContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvSectionCount = findViewById(R.id.tvSectionCount);

        ((TextView) findViewById(R.id.tvEmployeeName)).setText(employeeName);
        ((TextView) findViewById(R.id.tvEmployeeEmail)).setText(employeeEmail);
        ((TextView) findViewById(R.id.tvSelectedDate)).setText(formatDate(selectedDate));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDownloadPdf).setOnClickListener(v -> requestPdfSave());

        // Android opens a Save As screen. The PDF is written only after a location is selected.
        createPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> {
                    if (uri != null) {
                        writePdfToUri(uri);
                    } else {
                        Toast.makeText(this, "PDF save cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        loadSelectedDateUpdates();
    }

    private void loadSelectedDateUpdates() {
        tvStatus.setText("Loading work updates...");
        tvStatus.setVisibility(TextView.VISIBLE);
        sectionsContainer.removeAllViews();

        if (employeeKey == null || employeeKey.trim().isEmpty()
                || selectedDate.trim().isEmpty()) {
            tvStatus.setText("Employee or date information is missing.");
            return;
        }

        firestore.collectionGroup("dailyUpdates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    selectedDayUpdates.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String key = getEmployeeKey(doc);
                        String date = getDate(doc);

                        if (employeeKey.equals(key) && selectedDate.equals(date)) {
                            selectedDayUpdates.add(doc);
                        }
                    }

                    Collections.sort(selectedDayUpdates, (a, b) -> {
                        long sectionA = getSectionNumber(a);
                        long sectionB = getSectionNumber(b);
                        if (sectionA != sectionB) return Long.compare(sectionA, sectionB);
                        return Long.compare(getSubmittedMillis(a), getSubmittedMillis(b));
                    });

                    if (selectedDayUpdates.isEmpty()) {
                        tvStatus.setText("No work updates found for this date.");
                        tvSectionCount.setText("0 Sections");
                        findViewById(R.id.btnDownloadPdf).setEnabled(false);
                        return;
                    }

                    tvSectionCount.setText(selectedDayUpdates.size() +
                            (selectedDayUpdates.size() == 1 ? " Section Submitted" : " Sections Submitted"));

                    tvStatus.setVisibility(TextView.GONE);

                    for (DocumentSnapshot doc : selectedDayUpdates) {
                        addSectionCard(doc);
                    }
                })
                .addOnFailureListener(e -> {
                    tvStatus.setText("Unable to load daily updates:\n" + e.getMessage());
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

    private long getSectionNumber(DocumentSnapshot doc) {
        Object value = doc.get("section");
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.MAX_VALUE;
    }

    private long getSubmittedMillis(DocumentSnapshot doc) {
        Object value = doc.get("submittedAt");
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        return 0L;
    }

    private void addSectionCard(DocumentSnapshot doc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(getDrawable(R.drawable.bg_work_card));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        sectionsContainer.addView(card, params);

        long section = getSectionNumber(doc);
        card.addView(makeText("Section: " + (section == Long.MAX_VALUE ? "-" : section),
                17, true, 0xFF073F3A));

        card.addView(makeText("Work Schedule: " + safe(doc.getString("workSchedule")),
                15, false, 0xFF43534F));

        card.addView(makeText("Submission Window: " + safe(doc.getString("submissionWindow")),
                15, false, 0xFF43534F));

        TextView label = makeText("Work Update", 16, true, 0xFF073F3A);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(12);
        card.addView(label, labelParams);

        TextView update = makeText(safe(doc.getString("update")),
                15, false, 0xFF263A35);
        card.addView(update);

        TextView submitted = makeText("Submitted: " + getSubmittedTime(doc),
                13, false, 0xFF77857F);
        LinearLayout.LayoutParams submittedParams = new LinearLayout.LayoutParams(-1, -2);
        submittedParams.topMargin = dp(8);
        card.addView(submitted, submittedParams);
    }

    private void requestPdfSave() {
        if (selectedDayUpdates.isEmpty()) {
            Toast.makeText(this, "No work updates to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        String employeePart = employeeName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String datePart = selectedDate.replaceAll("[^0-9-]", "");
        String filename = "Work_Update_" + employeePart + "_" + datePart + ".pdf";

        try {
            createPdfLauncher.launch(filename);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open PDF save screen: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void writePdfToUri(Uri uri) {
        PdfDocument pdf = new PdfDocument();
        PdfWriter writer = new PdfWriter(pdf);

        try {
            writer.startPage();
            writer.title("EMPLOYEE DAILY WORK REPORT");
            writer.bold("Employee Name: " + employeeName);
            writer.line("Employee Email: " + employeeEmail);
            writer.line("Employee ID: " + employeeKey);
            writer.line("Date: " + selectedDate);
            writer.line("Total Sections Submitted: " + selectedDayUpdates.size());
            writer.line("------------------------------------------------------------");

            for (DocumentSnapshot doc : selectedDayUpdates) {
                long section = getSectionNumber(doc);

                writer.blank();
                writer.bold("SECTION: " + (section == Long.MAX_VALUE ? "-" : section));
                writer.line("Work Schedule: " + safe(doc.getString("workSchedule")));
                writer.line("Submission Window: " + safe(doc.getString("submissionWindow")));
                writer.bold("Work Update:");
                writer.wrapped(safe(doc.getString("update")));
                writer.line("Submitted: " + getSubmittedTime(doc));
                writer.line("------------------------------------------------------------");
            }

            writer.finish();

            OutputStream output = getContentResolver().openOutputStream(uri, "w");
            if (output == null) throw new Exception("Could not open the selected PDF file.");

            pdf.writeTo(output);
            output.flush();
            output.close();

            Toast.makeText(this, "PDF saved successfully.", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "PDF download failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            pdf.close();
        }
    }

    private String getSubmittedTime(DocumentSnapshot doc) {
        Object value = doc.get("submittedAt");
        if (value instanceof Timestamp) {
            return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    .format(((Timestamp) value).toDate());
        }
        return safe(doc.getString("submittedTime"));
    }

    private String formatDate(String date) {
        try {
            return new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
        } catch (Exception e) {
            return date;
        }
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

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    // PDF helper: wraps long work descriptions and automatically creates more pages.
    private static class PdfWriter {
        private final PdfDocument pdf;
        private PdfDocument.Page page;
        private Canvas canvas;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int pageWidth = 595;
        private final int pageHeight = 842;
        private final int left = 42;
        private final int maxTextWidth = 510;
        private int y;

        PdfWriter(PdfDocument pdf) {
            this.pdf = pdf;
        }

        void startPage() {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    pageWidth, pageHeight, pdf.getPages().size() + 1).create();
            page = pdf.startPage(info);
            canvas = page.getCanvas();
            y = 48;
        }

        private void ensureSpace(int height) {
            if (y + height > 790) {
                pdf.finishPage(page);
                startPage();
            }
        }

        void title(String value) {
            ensureSpace(32);
            paint.setTextSize(17);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(value, left, y, paint);
            y += 30;
            paint.setTypeface(Typeface.DEFAULT);
        }

        void bold(String value) {
            paint.setTextSize(11);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            drawWrapped(value);
            paint.setTypeface(Typeface.DEFAULT);
        }

        void line(String value) {
            paint.setTextSize(10.5f);
            paint.setTypeface(Typeface.DEFAULT);
            drawWrapped(value);
        }

        void wrapped(String value) {
            paint.setTextSize(10.5f);
            paint.setTypeface(Typeface.DEFAULT);
            drawWrapped(value);
        }

        void blank() {
            ensureSpace(12);
            y += 8;
        }

        private void drawWrapped(String value) {
            String[] words = value.split("\\s+");
            String line = "";

            for (String word : words) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (paint.measureText(candidate) > maxTextWidth && !line.isEmpty()) {
                    ensureSpace(17);
                    canvas.drawText(line, left, y, paint);
                    y += 15;
                    line = word;
                } else {
                    line = candidate;
                }
            }

            if (!line.isEmpty()) {
                ensureSpace(17);
                canvas.drawText(line, left, y, paint);
                y += 15;
            }
        }

        void finish() {
            if (page != null) {
                pdf.finishPage(page);
                page = null;
            }
        }
    }
}