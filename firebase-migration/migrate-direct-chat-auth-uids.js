
const {
  initializeApp,
  applicationDefault,
  getApps,
  deleteApp
} = require("firebase-admin/app");

const { getFirestore } = require("firebase-admin/firestore");

const ADMIN_PARTICIPANT_ID = "ADM-001";
const ADMIN_FIREBASE_UID = "ezvZfsphCqUpkgvu18ldvi7XW9D2";

// Confirmed remaining UID for AFI-9074
const VERIFIED_OVERRIDES = new Map([
  ["AFI-9074", "yNjPIN6upJh8VLpmDbMIXdxQC9H3"]
]);

// false = dry run; true = live update
const APPLY = process.env.APPLY === "true";

const app = initializeApp({
  credential: applicationDefault(),
  projectId: "arshith-connect-attendance"
});

const db = getFirestore(app);

async function loadEmployeeMappings() {
  const snapshot = await db
    .collection("employees")
    .select("employeeId", "uid", "email")
    .get();

  const mappings = new Map();
  const ambiguous = new Set();

  let excluded = 0;

  snapshot.forEach((doc) => {
    const data = doc.data();

    const employeeId = data.employeeId;
    const uid = data.uid;
    const email = data.email;

    // Only official company email addresses
    if (
      typeof email !== "string" ||
      !email.trim().toLowerCase().endsWith("@arshithgroup.com")
    ) {
      excluded++;
      return;
    }

    if (
      typeof employeeId !== "string" ||
      typeof uid !== "string" ||
      !employeeId.trim() ||
      !uid.trim()
    ) {
      return;
    }

    if (
      mappings.has(employeeId) &&
      mappings.get(employeeId) !== uid
    ) {
      ambiguous.add(employeeId);
      mappings.delete(employeeId);
      return;
    }

    if (!ambiguous.has(employeeId)) {
      mappings.set(employeeId, uid);
    }
  });

  // Apply the explicitly confirmed UID override
  for (const [employeeId, uid] of VERIFIED_OVERRIDES) {
    mappings.set(employeeId, uid);
    ambiguous.delete(employeeId);
  }

  console.log("Excluded non-company email records:", excluded);

  return { mappings, ambiguous };
}

function resolveParticipant(id, mappings, ambiguous) {
  if (typeof id !== "string") return null;

  if (id === ADMIN_PARTICIPANT_ID) {
    return ADMIN_FIREBASE_UID;
  }

  if (ambiguous.has(id)) return null;

  return mappings.get(id) || null;
}

async function migrate() {
  console.log("Starting direct-chat UID migration...");
  console.log("Mode:", APPLY ? "LIVE UPDATE" : "DRY RUN");

  const { mappings, ambiguous } =
    await loadEmployeeMappings();

  console.log("Employee mappings:", mappings.size);
  console.log("Ambiguous employee IDs:", [...ambiguous]);

  const snapshot = await db
    .collection("chat_messages")
    .where("chat_type", "==", "direct")
    .get();

  console.log("Direct messages found:", snapshot.size);

  let ready = 0;
  let skipped = 0;
  let alreadyMigrated = 0;

  let batch = db.batch();
  let batchCount = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();

    if (
      Object.prototype.hasOwnProperty.call(
        data,
        "participant_auth_uids"
      )
    ) {
      alreadyMigrated++;
      continue;
    }

    const participantIds = data.participant_ids;

    if (
      !Array.isArray(participantIds) ||
      participantIds.length === 0
    ) {
      skipped++;
      console.log("SKIPPED: Invalid participants:", doc.id);
      continue;
    }

    const authUids = participantIds.map((id) =>
      resolveParticipant(id, mappings, ambiguous)
    );

    if (
      authUids.some((uid) => !uid) ||
      new Set(authUids).size !== authUids.length
    ) {
      skipped++;

      const unresolved = participantIds.filter(
        (id, index) => !authUids[index]
      );

      console.log("SKIPPED:", doc.id);
      console.log("Unresolved IDs:", unresolved);
      continue;
    }

    ready++;

    console.log(
      APPLY ? "UPDATING:" : "WOULD UPDATE:",
      doc.id,
      authUids
    );

    if (APPLY) {
      batch.update(doc.ref, {
        participant_auth_uids: authUids
      });

      batchCount++;

      if (batchCount >= 400) {
        await batch.commit();
        batch = db.batch();
        batchCount = 0;
      }
    }
  }

  if (APPLY && batchCount > 0) {
    await batch.commit();
  }

  console.log("\n========== SUMMARY ==========");
  console.log("Mode:", APPLY ? "LIVE UPDATE" : "DRY RUN");
  console.log("Ready:", ready);
  console.log("Skipped:", skipped);
  console.log("Already migrated:", alreadyMigrated);
  console.log("Total direct messages:", snapshot.size);
  console.log("==============================");

  if (!APPLY) {
    console.log("DRY RUN ONLY. No documents were changed.");
  }
}

migrate()
  .catch((error) => {
    console.error("Migration failed:", error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await Promise.all(
      getApps().map((app) => deleteApp(app))
    );
  });
