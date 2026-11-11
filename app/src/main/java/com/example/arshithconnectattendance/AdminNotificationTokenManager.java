package com.example.arshithconnectattendance;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class AdminNotificationTokenManager {

    private AdminNotificationTokenManager() {
    }

    public static void saveToken(
            String token
    ) {

        if (
                token == null
                        || token.trim().isEmpty()
        ) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "fcmToken",
                token
        );

        data.put(
                "fcmTokenUpdatedAt",
                FieldValue.serverTimestamp()
        );

        FirebaseFirestore
                .getInstance()
                .collection("admins")
                .document(user.getUid())
                .set(
                        data,
                        SetOptions.merge()
                );
    }
}
