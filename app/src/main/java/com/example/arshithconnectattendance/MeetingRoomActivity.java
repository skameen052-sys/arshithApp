package com.example.arshithconnectattendance;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jitsi.meet.sdk.BroadcastEvent;
import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;

import java.net.MalformedURLException;
import java.net.URL;

public class MeetingRoomActivity extends AppCompatActivity {

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final int PERMISSION_REQUEST_CODE = 1001;


    // ============================================================
    // MEETING DATA
    // ============================================================

    private String roomName;

    private String meetingId;

    private boolean isAdmin;


    // ============================================================
    // JITSI BROADCAST RECEIVER
    // ============================================================

    private final BroadcastReceiver jitsiBroadcastReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    if (intent == null) {
                        return;
                    }

                    String action =
                            intent.getAction();


                    // ------------------------------------------------
                    // Jitsi conference terminated
                    // ------------------------------------------------
                    //
                    // IMPORTANT:
                    //
                    // This does NOT mean that the admin intentionally
                    // ended the meeting.
                    //
                    // It can happen when:
                    // - Admin leaves Jitsi
                    // - Jitsi Activity closes
                    // - User goes back
                    // - Conference terminates
                    //
                    // Therefore we ONLY close this Activity.
                    //
                    // We DO NOT change Firestore status here.
                    //
                    // The meeting remains:
                    //
                    // Scheduled -> Active
                    //
                    // until Admin explicitly presses END MEETING.
                    // ------------------------------------------------

                    if (BroadcastEvent.Type
                            .CONFERENCE_TERMINATED
                            .getAction()
                            .equals(action)) {

                        finish();
                    }
                }
            };


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);


        // --------------------------------------------------------
        // Get meeting information
        // --------------------------------------------------------

        roomName =
                getIntent().getStringExtra(
                        "roomName"
                );

        meetingId =
                getIntent().getStringExtra(
                        "meetingId"
                );

        isAdmin =
                getIntent().getBooleanExtra(
                        "isAdmin",
                        false
                );


        // --------------------------------------------------------
        // Check room
        // --------------------------------------------------------

        if (roomName == null
                || roomName.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Meeting room not found.",
                    Toast.LENGTH_LONG
            ).show();

            finish();

            return;
        }


        // --------------------------------------------------------
        // Register Jitsi event receiver
        // --------------------------------------------------------

        registerJitsiReceiver();


        // --------------------------------------------------------
        // Request camera + microphone permissions
        // --------------------------------------------------------

        requestMeetingPermissions();
    }


    // ============================================================
    // REGISTER JITSI RECEIVER
    // ============================================================

    private void registerJitsiReceiver() {

        IntentFilter intentFilter =
                new IntentFilter();


        intentFilter.addAction(
                BroadcastEvent.Type
                        .CONFERENCE_TERMINATED
                        .getAction()
        );


        LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(
                        jitsiBroadcastReceiver,
                        intentFilter
                );
    }


    // ============================================================
    // REQUEST CAMERA + MICROPHONE
    // ============================================================

    private void requestMeetingPermissions() {

        boolean cameraGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                )
                        == PackageManager.PERMISSION_GRANTED;


        boolean microphoneGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                )
                        == PackageManager.PERMISSION_GRANTED;


        // --------------------------------------------------------
        // Both permissions already granted
        // --------------------------------------------------------

        if (cameraGranted
                && microphoneGranted) {

            startMeeting();

            return;
        }


        // --------------------------------------------------------
        // Request permissions
        // --------------------------------------------------------

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                },
                PERMISSION_REQUEST_CODE
        );
    }


    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );


        if (requestCode != PERMISSION_REQUEST_CODE) {

            return;
        }


        // --------------------------------------------------------
        // Check camera + microphone
        // --------------------------------------------------------

        if (grantResults.length >= 2
                && grantResults[0]
                == PackageManager.PERMISSION_GRANTED
                && grantResults[1]
                == PackageManager.PERMISSION_GRANTED) {

            startMeeting();

        } else {

            Toast.makeText(
                    this,
                    "Camera and microphone permissions are required.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        }
    }


    // ============================================================
    // START JITSI MEETING
    // ============================================================

    private void startMeeting() {

        try {

            URL serverURL =
                    new URL(
                            "https://meet.jit.si"
                    );


            JitsiMeetConferenceOptions options =
                    new JitsiMeetConferenceOptions.Builder()

                            .setServerURL(
                                    serverURL
                            )

                            .setRoom(
                                    roomName
                            )

                            .setFeatureFlag(
                                    "welcomepage.enabled",
                                    false
                            )

                            .setAudioMuted(
                                    false
                            )

                            .setVideoMuted(
                                    false
                            )

                            .build();


            // ----------------------------------------------------
            // Open Jitsi inside the application using the
            // Jitsi Android SDK.
            //
            // No custom approval UI is inside Jitsi.
            // Firebase handles your application's approval.
            // ----------------------------------------------------

            JitsiMeetActivity.launch(
                    this,
                    options
            );

        } catch (MalformedURLException e) {

            Toast.makeText(
                    this,
                    "Unable to connect to Jitsi.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        }
    }


    // ============================================================
    // CLEANUP
    // ============================================================

    @Override
    protected void onDestroy() {

        // --------------------------------------------------------
        // Unregister Jitsi receiver
        // --------------------------------------------------------

        try {

            LocalBroadcastManager
                    .getInstance(this)
                    .unregisterReceiver(
                            jitsiBroadcastReceiver
                    );

        } catch (Exception ignored) {

            // Receiver may already be unregistered.
        }


        super.onDestroy();
    }
}