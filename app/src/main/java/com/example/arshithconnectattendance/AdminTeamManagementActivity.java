package com.example.arshithconnectattendance;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminTeamManagementActivity extends ComponentActivity {

    private FirebaseFirestore firestore;

    private EditText etTeamName;
    private LinearLayout employeesSelectionContainer;
    private LinearLayout teamsContainer;

    private TextView tvEmptyEmployees;
    private TextView tvEmptyTeams;

    private Button btnCreateTeam;

    private final List<CheckBox> employeeCheckBoxes =
            new ArrayList<>();

    private final List<String> employeeIds =
            new ArrayList<>();

    private final List<String> employeeNames =
            new ArrayList<>();

    // AFI business IDs aligned with the Firebase UID list.
    private final List<String> employeeBusinessIds =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_team_management
        );

        firestore =
                FirebaseFirestore.getInstance();

        etTeamName =
                findViewById(
                        R.id.etTeamName
                );

        employeesSelectionContainer =
                findViewById(
                        R.id.employeesSelectionContainer
                );

        teamsContainer =
                findViewById(
                        R.id.teamsContainer
                );

        tvEmptyEmployees =
                findViewById(
                        R.id.tvEmptyEmployees
                );

        tvEmptyTeams =
                findViewById(
                        R.id.tvEmptyTeams
                );

        btnCreateTeam =
                findViewById(
                        R.id.btnCreateTeam
                );

        btnCreateTeam.setOnClickListener(
                v -> createTeam()
        );

        loadEmployees();
        loadTeams();
    }

    // =========================================================
    // LOAD EMPLOYEES
    // =========================================================

    private void loadEmployees() {

        employeesSelectionContainer.removeAllViews();

        employeeCheckBoxes.clear();
        employeeIds.clear();
        employeeNames.clear();
        employeeBusinessIds.clear();

        tvEmptyEmployees.setVisibility(
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

                                tvEmptyEmployees.setVisibility(
                                        TextView.VISIBLE
                                );

                                return;
                            }

                            for (DocumentSnapshot document :
                                    employees) {

                                String uid =
                                        document.getId();

                                String name =
                                        document.getString(
                                                "name"
                                        );

                                if (name == null
                                        || name.trim().isEmpty()) {

                                    name =
                                            "Unnamed Employee";
                                }

                                final String finalUid =
                                        uid;

                                final String finalName =
                                        name;

                                String employeeBusinessId =
                                        document.getString("employeeId");

                                final String finalBusinessId =
                                        employeeBusinessId == null || employeeBusinessId.trim().isEmpty()
                                                ? uid
                                                : employeeBusinessId.trim();

                                CheckBox checkBox =
                                        new CheckBox(this);

                                checkBox.setText(
                                        finalName
                                );

                                checkBox.setTextSize(
                                        16
                                );

                                checkBox.setTextColor(
                                        Color.rgb(
                                                37,
                                                30,
                                                50
                                        )
                                );

                                checkBox.setPadding(
                                        dp(8),
                                        dp(8),
                                        dp(8),
                                        dp(8)
                                );

                                LinearLayout.LayoutParams params =
                                        new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        );

                                params.setMargins(
                                        0,
                                        0,
                                        0,
                                        dp(4)
                                );

                                checkBox.setLayoutParams(
                                        params
                                );

                                checkBox.setTag(
                                        finalUid
                                );

                                employeeCheckBoxes.add(
                                        checkBox
                                );

                                employeeIds.add(
                                        finalUid
                                );

                                employeeNames.add(
                                        finalName
                                );

                                employeeBusinessIds.add(
                                        finalBusinessId
                                );

                                employeesSelectionContainer.addView(
                                        checkBox
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
    // CREATE TEAM
    // =========================================================

    private void createTeam() {

        String teamName =
                etTeamName
                        .getText()
                        .toString()
                        .trim();

        if (teamName.isEmpty()) {

            etTeamName.setError(
                    "Enter team name"
            );

            etTeamName.requestFocus();

            return;
        }

        ArrayList<String> selectedMemberIds =
                new ArrayList<>();

        ArrayList<String> selectedMemberNames =
                new ArrayList<>();

        ArrayList<String> selectedMemberBusinessIds =
                new ArrayList<>();

        for (int i = 0;
             i < employeeCheckBoxes.size();
             i++) {

            CheckBox checkBox =
                    employeeCheckBoxes.get(i);

            if (checkBox.isChecked()) {

                selectedMemberIds.add(
                        employeeIds.get(i)
                );

                selectedMemberNames.add(
                        employeeNames.get(i)
                );

                selectedMemberBusinessIds.add(
                        employeeBusinessIds.get(i)
                );
            }
        }

        if (selectedMemberIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "Select at least one employee.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String createdBy = "";

        if (com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .getCurrentUser() != null) {

            createdBy =
                    com.google.firebase.auth.FirebaseAuth
                            .getInstance()
                            .getCurrentUser()
                            .getUid();
        }

        if (createdBy.isEmpty()) {

            Toast.makeText(
                    this,
                    "Admin session expired.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        final String finalTeamName =
                teamName;

        String teamId =
                "TEA-" + System.currentTimeMillis();

        Map<String, Object> team =
                new HashMap<>();

        team.put(
                "team_id",
                teamId
        );

        team.put(
                "conversation_id",
                "team_" + teamId
        );

        team.put(
                "memberBusinessIds",
                selectedMemberBusinessIds
        );

        team.put(
                "name",
                finalTeamName
        );

        team.put(
                "memberIds",
                selectedMemberIds
        );

        team.put(
                "memberNames",
                selectedMemberNames
        );

        team.put(
                "createdAt",
                Timestamp.now()
        );

        team.put(
                "createdBy",
                createdBy
        );

        team.put(
                "active",
                true
        );

        firestore
                .collection("teams")
                .document(teamId)
                .set(team)
                .addOnSuccessListener(
                        documentReference -> {

                            Toast.makeText(
                                    this,
                                    "Team created successfully.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            etTeamName.setText("");

                            for (CheckBox checkBox :
                                    employeeCheckBoxes) {

                                checkBox.setChecked(
                                        false
                                );
                            }

                            loadTeams();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to create team: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // LOAD TEAMS
    // =========================================================

    private void loadTeams() {

        teamsContainer.removeAllViews();

        tvEmptyTeams.setVisibility(
                TextView.GONE
        );

        firestore
                .collection("teams")
                .whereEqualTo(
                        "active",
                        true
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            List<DocumentSnapshot> teams =
                                    new ArrayList<>(
                                            querySnapshot.getDocuments()
                                    );

                            Collections.sort(
                                    teams,
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

                            if (teams.isEmpty()) {

                                tvEmptyTeams.setVisibility(
                                        TextView.VISIBLE
                                );

                                return;
                            }

                            for (DocumentSnapshot document :
                                    teams) {

                                addTeamCard(
                                        document
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load teams: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // =========================================================
    // TEAM CARD
    // =========================================================

    private void addTeamCard(
            DocumentSnapshot document
    ) {

        String teamId =
                document.getId();

        String teamName =
                document.getString(
                        "name"
                );

        if (teamName == null
                || teamName.trim().isEmpty()) {

            teamName =
                    "Unnamed Team";
        }

        final String finalTeamId =
                teamId;

        final String finalTeamName =
                teamName;

        ArrayList<String> memberIds =
                new ArrayList<>();

        ArrayList<String> memberNames =
                new ArrayList<>();

        Object rawMemberIds =
                document.get(
                        "memberIds"
                );

        if (rawMemberIds instanceof List) {

            List<?> list =
                    (List<?>) rawMemberIds;

            for (Object item : list) {

                if (item != null) {

                    memberIds.add(
                            String.valueOf(item)
                    );
                }
            }
        }

        Object rawMemberNames =
                document.get(
                        "memberNames"
                );

        if (rawMemberNames instanceof List) {

            List<?> list =
                    (List<?>) rawMemberNames;

            for (Object item : list) {

                if (item != null) {

                    memberNames.add(
                            String.valueOf(item)
                    );
                }
            }
        }

        final ArrayList<String> finalMemberIds =
                new ArrayList<>(
                        memberIds
                );

        final ArrayList<String> finalMemberNames =
                new ArrayList<>(
                        memberNames
                );

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

        TextView tvTeamName =
                createText(
                        finalTeamName,
                        20,
                        Color.rgb(
                                37,
                                30,
                                50
                        )
                );

        tvTeamName.setGravity(
                Gravity.START
        );

        card.addView(
                tvTeamName
        );

        StringBuilder membersText =
                new StringBuilder();

        membersText.append(
                "Members: "
        );

        if (finalMemberNames.isEmpty()) {

            membersText.append(
                    "No members"
            );

        } else {

            for (int i = 0;
                 i < finalMemberNames.size();
                 i++) {

                if (i > 0) {
                    membersText.append(
                            ", "
                    );
                }

                membersText.append(
                        finalMemberNames.get(i)
                );
            }
        }

        TextView tvMembers =
                createText(
                        membersText.toString(),
                        14,
                        Color.rgb(
                                117,
                                112,
                                128
                        )
                );

        LinearLayout.LayoutParams membersParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        membersParams.setMargins(
                0,
                dp(8),
                0,
                dp(10)
        );

        tvMembers.setLayoutParams(
                membersParams
        );

        card.addView(
                tvMembers
        );

        Button btnAssign =
                new Button(this);

        btnAssign.setText(
                "ASSIGN WORK TO TEAM"
        );

        btnAssign.setTextColor(
                Color.WHITE
        );

        btnAssign.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(
                                118,
                                81,
                                232
                        )
                )
        );

        btnAssign.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    AdminWorkAssignmentActivity.class
                            );

                    intent.putExtra(
                            "mode",
                            "TEAM"
                    );

                    intent.putExtra(
                            "teamId",
                            finalTeamId
                    );

                    intent.putExtra(
                            "teamName",
                            finalTeamName
                    );

                    intent.putStringArrayListExtra(
                            "teamMemberIds",
                            new ArrayList<>(
                                    finalMemberIds
                            )
                    );

                    startActivity(
                            intent
                    );
                }
        );

        card.addView(
                btnAssign
        );

        teamsContainer.addView(
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

        textView.setText(
                text
        );

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