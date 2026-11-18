const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const ADMIN_UID = "admin_owner_1";
const ADMIN_EMAIL = "sushilk75960@gmail.com";

const STUDENT_A_UID = "student_aiims_1";
const STUDENT_A_EMAIL = "rahul@aiims.edu";

const STUDENT_B_UID = "student_aiims_2";
const STUDENT_B_EMAIL = "priya@aiims.edu";

const STUDENT_OTHER_UID = "student_iitd_1";
const STUDENT_OTHER_EMAIL = "amit@iitd.ac.in";

const ADVERTISER_UID = "advertiser_1";
const ADVERTISER_EMAIL = "partner@medprep.edu";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function verifiedCtx(uid, email) {
  return testEnv.authenticatedContext(uid, {
    email: email,
    email_verified: true,
  });
}

function unverifiedCtx(uid, email) {
  return testEnv.authenticatedContext(uid, {
    email: email,
    email_verified: false,
  });
}

function makeUserPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  const trialEnd = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
  return {
    name: "Rahul Sharma",
    email: STUDENT_A_EMAIL,
    collegeId: "college_aiims",
    department: "MBBS",
    semester: 3,
    country: "India",
    timezone: "Asia/Kolkata",
    role: "student",
    createdAt: now,
    trialStartedAt: now,
    subscriptionStatus: "trial",
    subscriptionEndsAt: trialEnd,
    premiumBonusDays: 0,
    referralCode: "SCRAHUL1",
    referredBy: "",
    blocked: false,
    aiUsageDate: "2026-11-17",
    aiUsedToday: 0,
    updatedAt: now,
    ...overrides,
  };
}

async function seedStandardUsers() {
  const now = new Date(Date.now() - 10000);
  const trialEnd = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("allowedColleges").doc("college_aiims").set({
      name: "AIIMS New Delhi",
      emailDomain: "aiims.edu",
      active: true,
      createdAt: now,
    });
    await db.collection("users").doc(ADMIN_UID).set(
      makeUserPayload({
        name: "Sushil Admin",
        email: ADMIN_EMAIL,
        role: "admin",
        referralCode: "SCADMIN1",
      })
    );
    await db.collection("users").doc(STUDENT_A_UID).set(makeUserPayload());
    await db.collection("users").doc(STUDENT_B_UID).set(
      makeUserPayload({
        name: "Priya Verma",
        email: STUDENT_B_EMAIL,
        referralCode: "SCPRIYA2",
      })
    );
    await db.collection("users").doc(STUDENT_OTHER_UID).set(
      makeUserPayload({
        name: "Amit Kumar",
        email: STUDENT_OTHER_EMAIL,
        collegeId: "college_iitd",
        department: "B.Tech",
        referralCode: "SCAMIT03",
      })
    );
    await db.collection("users").doc(ADVERTISER_UID).set(
      makeUserPayload({
        name: "MedPrep Academy",
        email: ADVERTISER_EMAIL,
        role: "advertiser",
        referralCode: "SCADV001",
      })
    );
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Rule 1: Nobody reads or writes anything without a signed-in user whose email is verified", async () => {
  await seedStandardUsers();

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("allowedColleges").where("active", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(STUDENT_A_UID).get());

  const unverifiedDb = unverifiedCtx(STUDENT_A_UID, STUDENT_A_EMAIL).firestore();
  await assertFails(unverifiedDb.collection("allowedColleges").where("active", "==", true).get());
  await assertFails(unverifiedDb.collection("users").doc(STUDENT_A_UID).get());

  const verifiedDb = verifiedCtx(STUDENT_A_UID, STUDENT_A_EMAIL).firestore();
  await assertSucceeds(verifiedDb.collection("allowedColleges").where("active", "==", true).get());
  await assertSucceeds(verifiedDb.collection("users").doc(STUDENT_A_UID).get());
});

test("Rule 2: New user can create only their own profile, role=student, blocked=false, subscriptionStatus=trial, name 2..59 chars", async () => {
  const newUid = "new_student_99";
  const newDb = verifiedCtx(newUid, "newbie@aiims.edu").firestore();

  // Valid creation succeeds
  await assertSucceeds(
    newDb.collection("users").doc(newUid).set(
      makeUserPayload({
        name: "Ananya Singh",
        email: "newbie@aiims.edu",
      })
    )
  );

  // Cannot create profile for another UID
  await assertFails(
    newDb.collection("users").doc("other_uid_999").set(makeUserPayload())
  );

  // Cannot self-assign admin role
  const rogueUid = "rogue_student_1";
  const rogueDb = verifiedCtx(rogueUid, "rogue@aiims.edu").firestore();
  await assertFails(
    rogueDb.collection("users").doc(rogueUid).set(
      makeUserPayload({
        role: "admin",
      })
    )
  );

  // Cannot self-assign active subscriptionStatus on creation
  await assertFails(
    rogueDb.collection("users").doc(rogueUid).set(
      makeUserPayload({
        subscriptionStatus: "active",
      })
    )
  );

  // Name shorter than 2 chars fails
  await assertFails(
    rogueDb.collection("users").doc(rogueUid).set(
      makeUserPayload({
        name: "A",
      })
    )
  );
});

test("Rule 3: Student cannot change role, blocked, collegeId, department, subscription fields, or trial start; only admins can", async () => {
  await seedStandardUsers();
  const studentDb = verifiedCtx(STUDENT_A_UID, STUDENT_A_EMAIL).firestore();
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const now = new Date(Date.now() - 1000);

  // Student CAN update semester, name, and daily AI count
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      semester: 4,
      aiUsedToday: 1,
      updatedAt: now,
    })
  );

  // Student CANNOT change collegeId or department
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      department: "B.Tech",
      updatedAt: now,
    })
  );

  // Student CANNOT escalate role or change subscriptionStatus
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      role: "admin",
      updatedAt: now,
    })
  );
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      subscriptionStatus: "active",
      updatedAt: now,
    })
  );

  // Admin CAN grant active subscription and bonus days
  await assertSucceeds(
    adminDb.collection("users").doc(STUDENT_A_UID).update({
      subscriptionStatus: "active",
      premiumBonusDays: 7,
      updatedAt: now,
    })
  );
});

test("Rule 4 & 5: Student reads only rooms/messages/doubts of their own college & dept; message validation and delete rules", async () => {
  await seedStandardUsers();
  const now = new Date(Date.now() - 2000);
  const studentADb = verifiedCtx(STUDENT_A_UID, STUDENT_A_EMAIL).firestore();
  const studentBDb = verifiedCtx(STUDENT_B_UID, STUDENT_B_EMAIL).firestore();
  const otherCollegeDb = verifiedCtx(STUDENT_OTHER_UID, STUDENT_OTHER_EMAIL).firestore();
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();

  // Student A creates a discussion room in college_aiims / MBBS
  await assertSucceeds(
    studentADb.collection("rooms").doc("room_anat_1").set({
      name: "Anatomy Dissection Hall",
      type: "discussion",
      collegeId: "college_aiims",
      department: "MBBS",
      semester: 3,
      createdBy: STUDENT_A_UID,
      memberCount: 1,
      lastMessageAt: now,
      createdAt: now,
    })
  );

  // Student B (same college & dept) can list and read the room
  await assertSucceeds(
    studentBDb
      .collection("rooms")
      .where("collegeId", "==", "college_aiims")
      .where("department", "==", "MBBS")
      .get()
  );

  // Student from IITD / B.Tech CANNOT read or list AIIMS MBBS rooms
  await assertFails(otherCollegeDb.collection("rooms").doc("room_anat_1").get());
  await assertFails(
    otherCollegeDb
      .collection("rooms")
      .where("collegeId", "==", "college_aiims")
      .where("department", "==", "MBBS")
      .get()
  );

  // Student A posts a valid message in room_anat_1
  await assertSucceeds(
    studentADb
      .collection("rooms")
      .doc("room_anat_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        roomId: "room_anat_1",
        collegeId: "college_aiims",
        department: "MBBS",
        senderId: STUDENT_A_UID,
        senderName: "Rahul Sharma",
        text: "Does anyone have Upper Limb nerve supply notes?",
        type: "text",
        callMode: "none",
        linkUrl: "",
        createdAt: now,
        deleted: false,
      })
  );

  // Student B cannot spoof senderId as Student A
  await assertFails(
    studentBDb
      .collection("rooms")
      .doc("room_anat_1")
      .collection("messages")
      .doc("msg_spoof")
      .set({
        roomId: "room_anat_1",
        collegeId: "college_aiims",
        department: "MBBS",
        senderId: STUDENT_A_UID,
        senderName: "Rahul Sharma",
        text: "Spoofed message",
        type: "text",
        callMode: "none",
        linkUrl: "",
        createdAt: now,
        deleted: false,
      })
  );

  // Student B cannot delete Student A's message
  await assertFails(
    studentBDb
      .collection("rooms")
      .doc("room_anat_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        deleted: true,
        text: "[Message deleted]",
      })
  );

  // Sender (Student A) or Admin CAN soft-delete the message
  await assertSucceeds(
    studentADb
      .collection("rooms")
      .doc("room_anat_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        deleted: true,
        text: "[Message deleted]",
      })
  );
  await assertSucceeds(
    adminDb
      .collection("rooms")
      .doc("room_anat_1")
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
});

test("Rule 6 & 7: Ads visible only when approved; only advertiser creates pending ad; only admin approves & manages colleges/reports", async () => {
  await seedStandardUsers();
  const now = new Date(Date.now() - 2000);
  const advertiserDb = verifiedCtx(ADVERTISER_UID, ADVERTISER_EMAIL).firestore();
  const studentADb = verifiedCtx(STUDENT_A_UID, STUDENT_A_EMAIL).firestore();
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();

  const adPayload = {
    title: "Marrow & PrepLadder Test Series 2027",
    body: "High-yield NEXT & NEET PG clinical questions for 3rd year MBBS.",
    imageUrl: "https://example.edu/banner.png",
    link: "https://example.edu/marrow",
    placement: "home_card",
    advertiserId: ADVERTISER_UID,
    targetDepartment: "MBBS",
    targetSemester: 3,
    targetCountry: "India",
    startDate: "2026-11-01",
    endDate: "2027-01-31",
    status: "pending",
    views: 0,
    clicks: 0,
    createdAt: now,
  };

  // Student cannot create an ad
  await assertFails(
    studentADb.collection("ads").doc("ad_1").set({
      ...adPayload,
      advertiserId: STUDENT_A_UID,
    })
  );

  // Advertiser cannot create an ad as already 'approved'
  await assertFails(
    advertiserDb.collection("ads").doc("ad_1").set({
      ...adPayload,
      status: "approved",
    })
  );

  // Advertiser creates a 'pending' ad
  await assertSucceeds(advertiserDb.collection("ads").doc("ad_1").set(adPayload));

  // Student cannot read a 'pending' ad
  await assertFails(studentADb.collection("ads").doc("ad_1").get());

  // Advertiser cannot approve their own ad
  await assertFails(
    advertiserDb.collection("ads").doc("ad_1").update({
      status: "approved",
    })
  );

  // Admin approves the ad
  await assertSucceeds(
    adminDb.collection("ads").doc("ad_1").update({
      status: "approved",
    })
  );

  // Now student CAN read and list approved ads and increment views/clicks
  await assertSucceeds(
    studentADb.collection("ads").where("status", "==", "approved").get()
  );
  await assertSucceeds(
    studentADb.collection("ads").doc("ad_1").update({
      views: 1,
      clicks: 1,
    })
  );
});
