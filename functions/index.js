
const { setGlobalOptions } = require("firebase-functions");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();

setGlobalOptions({
  maxInstances: 10,
});

const CHAT_CONVERSATIONS = "chat_conversations";
const EMPLOYEES = "employees";
const ADMINS = "admins";

function requireAuth(request) {
  if (!request.auth) {
    throw new HttpsError(
      "unauthenticated",
      "Please sign in before using Chat."
    );
  }

  return request.auth.uid;
}

function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

async function getUserRole(uid) {
  const adminDoc = await db.collection(ADMINS).doc(uid).get();

  if (
    adminDoc.exists &&
    ["admin", "Admin", "ADMIN"].includes(adminDoc.data().role)
  ) {
    return "admin";
  }

  const employeeDoc = await db.collection(EMPLOYEES).doc(uid).get();

  if (
    employeeDoc.exists &&
    employeeDoc.data().status === "approved"
  ) {
    return "employee";
  }

  throw new HttpsError(
    "permission-denied",
    "You are not an approved Chat user."
  );
}

async function validateMembers(memberIds) {
  if (
    !Array.isArray(memberIds) ||
    memberIds.length < 2 ||
    memberIds.length > 100
  ) {
    throw new HttpsError(
      "invalid-argument",
      "A group must have between 2 and 100 members."
    );
  }

  const uniqueIds = [...new Set(memberIds)];

  if (uniqueIds.length !== memberIds.length) {
    throw new HttpsError(
      "invalid-argument",
      "Duplicate group members are not allowed."
    );
  }

  for (const uid of uniqueIds) {
    await getUserRole(uid);
  }

  return uniqueIds;
}

function cleanMemberMap(source, memberIds) {
  const result = {};

  if (!source || typeof source !== "object") {
    return result;
  }

  for (const uid of memberIds) {
    const value = cleanString(source[uid]);

    if (value) {
      result[uid] = value;
    }
  }

  return result;
}

/**
 * Creates a group conversation.
 */
exports.createGroupConversation = onCall(async (request) => {
  const uid = requireAuth(request);
  const creatorRole = await getUserRole(uid);

  const groupName = cleanString(request.data?.groupName);

  if (!groupName || groupName.length > 100) {
    throw new HttpsError(
      "invalid-argument",
      "Enter a group name between 1 and 100 characters."
    );
  }

  let memberIds = request.data?.memberIds;

  if (!Array.isArray(memberIds)) {
    throw new HttpsError(
      "invalid-argument",
      "Group members are required."
    );
  }

  memberIds = [...new Set([...memberIds, uid])];
  memberIds = await validateMembers(memberIds);

  const memberNames = cleanMemberMap(
    request.data?.memberNames,
    memberIds
  );

  const memberBusinessIds = cleanMemberMap(
    request.data?.memberBusinessIds,
    memberIds
  );

  const unreadCounts = {};

  for (const memberUid of memberIds) {
    unreadCounts[memberUid] = 0;
  }

  const conversationRef = db.collection(CHAT_CONVERSATIONS).doc();

  const groupAdmins = creatorRole === "employee" ? [uid] : [];

  const groupData = {
    type: "group",
    name: groupName,
    memberIds,
    memberNames,
    memberBusinessIds,
    createdBy: uid,
    creatorRole,
    groupAdmins,
    createdAt: FieldValue.serverTimestamp(),
    lastMessage: "",
    lastMessageTime: FieldValue.serverTimestamp(),
    lastSenderId: "",
    unreadCounts,
  };

  await conversationRef.set(groupData);

  return {
    success: true,
    conversationId: conversationRef.id,
  };
});


/**
 * Adds/removes members, manages group admins,
 * transfers ownership, and allows members to leave.
 *
 * Actions:
 * add, remove, promote, demote,
 * transferOwnership, leave
 */
exports.manageGroupMembership = onCall(async (request) => {
  const uid = requireAuth(request);
  const callerRole = await getUserRole(uid);

  const conversationId = cleanString(request.data?.conversationId);
  const action = cleanString(request.data?.action);
  const targetUid = cleanString(request.data?.targetUid);

  if (!conversationId) {
    throw new HttpsError(
      "invalid-argument",
      "Conversation ID is required."
    );
  }

  const supportedActions = [
    "add",
    "remove",
    "promote",
    "demote",
    "transferOwnership",
    "leave",
    "deleteForMe",
  ];

  if (!supportedActions.includes(action)) {
    throw new HttpsError(
      "invalid-argument",
      "Unsupported group management action."
    );
  }

  if (
    ["add", "remove", "promote", "demote", "transferOwnership"]
      .includes(action) &&
    !targetUid
  ) {
    throw new HttpsError(
      "invalid-argument",
      "Target user is required for this action."
    );
  }

  const conversationRef = db
    .collection(CHAT_CONVERSATIONS)
    .doc(conversationId);

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(conversationRef);

    if (!snapshot.exists) {
      throw new HttpsError("not-found", "Group not found.");
    }

    const group = snapshot.data();

    if (group.type !== "group") {
      throw new HttpsError(
        "failed-precondition",
        "This conversation is not a group."
      );
    }

    const memberIds = Array.isArray(group.memberIds)
      ? [...group.memberIds]
      : [];

    const groupAdmins = Array.isArray(group.groupAdmins)
      ? [...group.groupAdmins]
      : [];

    const isMember = memberIds.includes(uid);
    const isAppAdmin = callerRole === "admin";
    const isCreator = group.createdBy === uid;
    // --------------------------------------------------
    // DELETE GROUP FROM MY INBOX
    // Only allowed after leaving or being removed.
    // Does not delete the group or its messages.
    // --------------------------------------------------
    if (action === "deleteForMe") {
      if (isMember) {
        throw new HttpsError(
          "failed-precondition",
          "You must leave the group before deleting it from your inbox."
        );
      }

      const inboxRef = db
        .collection("chat_user_inbox")
        .doc(uid)
        .collection("items")
        .doc(conversationId);

      transaction.set(
        inboxRef,
        {
          deletedForMe: true,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      return;
    }

    // A former creator must not retain creator permissions.
    const canManage =
      isAppAdmin ||
      (
        group.creatorRole === "employee" &&
        isMember &&
        (isCreator || groupAdmins.includes(uid))
      );

    // --------------------------------------------------
    // LEAVE GROUP
    // --------------------------------------------------
    if (action === "leave") {
      if (!isMember) {
        throw new HttpsError(
          "failed-precondition",
          "You are not a member of this group."
        );
      }

      if (isCreator) {
        throw new HttpsError(
          "failed-precondition",
          "Transfer group ownership to another member before leaving."
        );
      }

      const updatedMembers = memberIds.filter(
        (memberUid) => memberUid !== uid
      );

      const updatedAdmins = groupAdmins.filter(
        (adminUid) => adminUid !== uid
      );

      const memberNames = {
        ...(group.memberNames || {}),
      };

      const memberBusinessIds = {
        ...(group.memberBusinessIds || {}),
      };

      const unreadCounts = {
        ...(group.unreadCounts || {}),
      };

      delete memberNames[uid];
      delete memberBusinessIds[uid];
      delete unreadCounts[uid];

      transaction.update(conversationRef, {
        memberIds: updatedMembers,
        groupAdmins: updatedAdmins,
        memberNames,
        memberBusinessIds,
        unreadCounts,
        updatedAt: FieldValue.serverTimestamp(),
      });

      return;
    }

    // --------------------------------------------------
    // TRANSFER OWNERSHIP
    // --------------------------------------------------
    if (action === "transferOwnership") {
      if (!isCreator || !isMember) {
        throw new HttpsError(
          "permission-denied",
          "Only the current group creator can transfer ownership."
        );
      }

      if (targetUid === uid) {
        throw new HttpsError(
          "invalid-argument",
          "You are already the group creator."
        );
      }

      if (!memberIds.includes(targetUid)) {
        throw new HttpsError(
          "failed-precondition",
          "The new creator must already be a group member."
        );
      }

      const targetRole = await getUserRole(targetUid);

      const updatedAdmins = groupAdmins.filter(
        (adminUid) => adminUid !== uid
      );

      if (!updatedAdmins.includes(targetUid)) {
        updatedAdmins.push(targetUid);
      }

      transaction.update(conversationRef, {
        createdBy: targetUid,
        creatorRole: targetRole,
        groupAdmins: updatedAdmins,
        updatedAt: FieldValue.serverTimestamp(),
      });

      return;
    }

    // --------------------------------------------------
    // OTHER GROUP MANAGEMENT ACTIONS
    // --------------------------------------------------
    if (!canManage) {
      throw new HttpsError(
        "permission-denied",
        "You do not have permission to manage this group."
      );
    }

    if (action === "add") {
      if (memberIds.includes(targetUid)) {
        throw new HttpsError(
          "already-exists",
          "This user is already a group member."
        );
      }

      if (memberIds.length >= 100) {
        throw new HttpsError(
          "resource-exhausted",
          "The group has reached its member limit."
        );
      }

      await getUserRole(targetUid);

      const updatedMembers = [...memberIds, targetUid];

      const memberNames = {
        ...(group.memberNames || {}),
      };

      const memberBusinessIds = {
        ...(group.memberBusinessIds || {}),
      };

      const suppliedName = cleanString(request.data?.targetName);
      const suppliedBusinessId = cleanString(
        request.data?.targetBusinessId
      );

      if (suppliedName) {
        memberNames[targetUid] = suppliedName;
      }

      if (suppliedBusinessId) {
        memberBusinessIds[targetUid] = suppliedBusinessId;
      }

      const unreadCounts = {
        ...(group.unreadCounts || {}),
        [targetUid]: 0,
      };

      transaction.update(conversationRef, {
        memberIds: updatedMembers,
        memberNames,
        memberBusinessIds,
        unreadCounts,
        updatedAt: FieldValue.serverTimestamp(),
      });

      return;
    }

    if (action === "remove") {
      if (targetUid === group.createdBy) {
        throw new HttpsError(
          "failed-precondition",
          "The group creator cannot be removed."
        );
      }

      if (!memberIds.includes(targetUid)) {
        throw new HttpsError(
          "not-found",
          "This user is not a group member."
        );
      }

      const updatedMembers = memberIds.filter(
        (memberUid) => memberUid !== targetUid
      );

      const memberNames = {
        ...(group.memberNames || {}),
      };

      const memberBusinessIds = {
        ...(group.memberBusinessIds || {}),
      };

      const unreadCounts = {
        ...(group.unreadCounts || {}),
      };

      delete memberNames[targetUid];
      delete memberBusinessIds[targetUid];
      delete unreadCounts[targetUid];

      const updatedAdmins = groupAdmins.filter(
        (adminUid) => adminUid !== targetUid
      );

      transaction.update(conversationRef, {
        memberIds: updatedMembers,
        memberNames,
        memberBusinessIds,
        unreadCounts,
        groupAdmins: updatedAdmins,
        updatedAt: FieldValue.serverTimestamp(),
      });

      return;
    }

    if (action === "promote") {
      if (callerRole !== "admin" && group.creatorRole !== "employee") {
        throw new HttpsError(
          "permission-denied",
          "Only app admins can manage this group's membership."
        );
      }

      if (!memberIds.includes(targetUid)) {
        throw new HttpsError(
          "failed-precondition",
          "The target user must already be a group member."
        );
      }

      if (targetUid === group.createdBy) {
        throw new HttpsError(
          "failed-precondition",
          "The group creator is already a group administrator."
        );
      }

      const targetRole = await getUserRole(targetUid);

      if (targetRole !== "employee") {
        throw new HttpsError(
          "failed-precondition",
          "Only employees can be promoted to group admin."
        );
      }

      if (!groupAdmins.includes(targetUid)) {
        groupAdmins.push(targetUid);
      }

      transaction.update(conversationRef, {
        groupAdmins,
        updatedAt: FieldValue.serverTimestamp(),
      });

      return;
    }

    if (action === "demote") {
      if (targetUid === group.createdBy) {
        throw new HttpsError(
          "failed-precondition",
          "The group creator cannot be demoted."
        );
      }

      if (!groupAdmins.includes(targetUid)) {
        throw new HttpsError(
          "failed-precondition",
          "This user is not a group administrator."
        );
      }

      const updatedAdmins = groupAdmins.filter(
        (adminUid) => adminUid !== targetUid
      );

      transaction.update(conversationRef, {
        groupAdmins: updatedAdmins,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  });

  return {
    success: true,
    action,
    conversationId,
  };
});


/**
 * Deletes a group when an app admin removes themselves.
 *
 * Deletes:
 * 1. Files in chatAttachments/{conversationId}/
 * 2. The conversation and all nested message documents.
 *
 * Only an app admin who is currently a group member can call this.
 */
exports.deleteGroupWhenAdminLeaves = onCall(async (request) => {
  const uid = requireAuth(request);
  const callerRole = await getUserRole(uid);

  if (callerRole !== "admin") {
    throw new HttpsError(
      "permission-denied",
      "Only app admins can perform this action."
    );
  }

  const conversationId = cleanString(request.data?.conversationId);

  if (!conversationId) {
    throw new HttpsError(
      "invalid-argument",
      "Conversation ID is required."
    );
  }

  const conversationRef = db
    .collection(CHAT_CONVERSATIONS)
    .doc(conversationId);

  const snapshot = await conversationRef.get();

  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Group not found.");
  }

  const group = snapshot.data();

  if (group.type !== "group") {
    throw new HttpsError(
      "failed-precondition",
      "This conversation is not a group."
    );
  }

  const memberIds = Array.isArray(group.memberIds)
    ? group.memberIds
    : [];

  if (!memberIds.includes(uid)) {
    throw new HttpsError(
      "permission-denied",
      "You must be a member of this group."
    );
  }

  const bucket = getStorage().bucket();

  await bucket.deleteFiles({
    prefix: `chatAttachments/${conversationId}/`,
  });

  await db.recursiveDelete(conversationRef);

  return {
    success: true,
    deletedConversationId: conversationId,
  };
});

/**
 * Sends an FCM notification when a new chat message is created.
 *
 * Firestore path:
 * chat_conversations/{conversationId}/messages/{messageId}
 *
 * Reads recipient tokens from:
 * employees/{uid}.fcmToken
 *
 * The sender never receives their own notification.
 *
 * Uses a data-only FCM payload so MyFirebaseMessagingService
 * handles notification display and chat navigation on Android.
 */
exports.sendChatMessageNotification = onDocumentCreated(
  `${CHAT_CONVERSATIONS}/{conversationId}/messages/{messageId}`,
  async (event) => {
    try {
      const snapshot = event.data;

      if (!snapshot || !snapshot.exists) {
        console.log("Chat message document does not exist.");
        return;
      }

      const message = snapshot.data();
      const conversationId = event.params.conversationId;
      const messageId = event.params.messageId;

      const senderUid =
        cleanString(message.senderAuthUid) ||
        cleanString(message.senderId);

      if (!senderUid) {
        console.log("Message has no sender UID. Notification skipped.");
        return;
      }

      // Get the conversation document.
      const conversationRef = db
        .collection(CHAT_CONVERSATIONS)
        .doc(conversationId);

      const conversationSnapshot = await conversationRef.get();

      if (!conversationSnapshot.exists) {
        console.log("Conversation not found:", conversationId);
        return;
      }

      const conversation = conversationSnapshot.data();

      const memberIds = Array.isArray(conversation.memberIds)
        ? [...new Set(
            conversation.memberIds.filter(
              (uid) => typeof uid === "string" && uid.trim()
            )
          )]
        : [];

      if (!memberIds.includes(senderUid)) {
        console.log("Sender is not a conversation member.");
        return;
      }

      // Exclude the sender.
      const recipientIds = memberIds.filter(
        (uid) => uid !== senderUid
      );

      if (recipientIds.length === 0) {
        console.log("No recipients for this message.");
        return;
      }

      // Get sender's display name.
      const senderSnapshot = await db
        .collection(EMPLOYEES)
        .doc(senderUid)
        .get();

      const senderData = senderSnapshot.exists
        ? senderSnapshot.data()
        : {};

      const senderName =
        cleanString(senderData.name) ||
        cleanString(senderData.employeeName) ||
        cleanString(senderData.displayName) ||
        cleanString(
          conversation.memberNames &&
          conversation.memberNames[senderUid]
        ) ||
        "New message";

      // Read message type and content.
      const messageType =
        cleanString(message.messageType) ||
        cleanString(message.type) ||
        "text";

      const messageText =
        cleanString(message.content) ||
        cleanString(message.text);

      let notificationBody = messageText;

      if (!notificationBody) {
        if (messageType === "image") {
          notificationBody = "📷 Photo";
        } else if (messageType === "document") {
          notificationBody = "📎 Document";
        } else {
          notificationBody = "You received a new message.";
        }
      }

      if (notificationBody.length > 150) {
        notificationBody =
          notificationBody.substring(0, 147) + "...";
      }

      // Fetch recipient employee documents.
      const recipientSnapshots = await Promise.all(
        recipientIds.map((uid) =>
          db.collection(EMPLOYEES).doc(uid).get()
        )
      );

      const tokenToOwners = new Map();

      recipientSnapshots.forEach((userSnapshot) => {
        if (!userSnapshot.exists) {
          return;
        }

        const token = cleanString(
          userSnapshot.data().fcmToken
        );

        if (!token) {
          return;
        }

        if (!tokenToOwners.has(token)) {
          tokenToOwners.set(token, []);
        }

        tokenToOwners.get(token).push(userSnapshot.id);
      });

      const uniqueTokens = [...tokenToOwners.keys()];

      if (uniqueTokens.length === 0) {
        console.log(
          "No FCM tokens found for chat recipients."
        );
        return;
      }

      const isGroup = conversation.type === "group";

      const notificationTitle = isGroup
        ? `${senderName} · ${
            cleanString(conversation.name) || "Group"
          }`
        : senderName;

      // Send notifications in batches of at most 500 tokens.
      const messaging = getMessaging();

      for (let i = 0; i < uniqueTokens.length; i += 500) {
        const tokenBatch = uniqueTokens.slice(i, i + 500);

        // DATA-ONLY FCM PAYLOAD:
        // Android MyFirebaseMessagingService displays the notification.
        const response = await messaging.sendEachForMulticast({
          tokens: tokenBatch,

          data: {
            notificationType: "chat_message",
            title: String(notificationTitle),
            body: String(notificationBody),
            conversationId: String(conversationId),
            messageId: String(messageId),
            senderId: String(senderUid),
            senderName: String(senderName),
            messageType: String(messageType),
          },

          android: {
            priority: "high",
          },
        });

        console.log(
          `Chat FCM result: ${response.successCount} successful, ` +
          `${response.failureCount} failed.`
        );

        // Remove invalid tokens from the employee documents
        // that own those tokens.
        const cleanupTasks = [];

        response.responses.forEach((result, index) => {
          if (
            result.success ||
            !result.error ||
            ![
              "messaging/invalid-registration-token",
              "messaging/registration-token-not-registered",
            ].includes(result.error.code)
          ) {
            return;
          }

          const invalidToken = tokenBatch[index];
          const ownerUids = tokenToOwners.get(invalidToken) || [];

          ownerUids.forEach((ownerUid) => {
            const employeeRef = db
              .collection(EMPLOYEES)
              .doc(ownerUid);

            cleanupTasks.push(
              employeeRef.get().then((employeeSnapshot) => {
                if (
                  employeeSnapshot.exists &&
                  employeeSnapshot.data().fcmToken === invalidToken
                ) {
                  return employeeRef.update({
                    fcmToken: FieldValue.delete(),
                  });
                }

                return null;
              })
            );
          });
        });

        await Promise.all(cleanupTasks);
      }

      console.log(
        "Chat notification processing completed:",
        conversationId,
        messageId
      );

    } catch (error) {
      console.error(
        "Chat notification error:",
        error
      );

      throw error;
    }
  }
);
