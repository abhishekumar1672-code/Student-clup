================================================================================
STUDYCIRCLE — PART A: PHONE-ONLY OWNER SETUP GUIDE (SPCK EDITOR + FIREBASE)
================================================================================

Welcome to StudyCircle! This guide is written specifically for an owner working
100% on an Android phone (using Chrome browser and the SPCK Code Editor app)
with zero computer, zero npm/compiler, and zero cost (Firebase Spark Free Plan).

--------------------------------------------------------------------------------
1. HONEST LIMITS OF THE FREE FIREBASE SPARK PLAN (RULE #3 & #10)
--------------------------------------------------------------------------------
Before launching, you must know the exact limits of the free Firebase Spark plan
(no credit card required) and what breaks first as your student community grows:

1. Cloud Firestore Free Quotas (What breaks FIRST):
   - Stored Data: 1 GiB total.
   - Document Reads: 50,000 reads per day.
   - Document Writes: 20,000 writes per day.
   - Document Deletes: 20,000 deletes per day.
   - Concurrent Connections: ~100 simultaneous real-time listeners on Spark.
   * REALITY CHECK: The free plan CANNOT handle 2 to 3 lakh (200,000–300,000)
     active users! With 30 messages loaded per room open, 50,000 reads/day
     supports roughly 300 to 800 daily active students depending on how many
     rooms and doubts they browse. Document reads (`50,000/day`) will be the
     FIRST limit to run out. When it hits 50,000 reads in a day, Firestore
     temporarily returns "Quota exceeded" until midnight Pacific Time.

2. Firebase Authentication Free Quotas:
   - Email/Password & Google Sign-In are free up to 50,000 Monthly Active Users
     (MAU) on Spark.
   - Email Verification links have daily anti-abuse sending limits per IP/project.

3. Firebase Storage & Notes/PDF Sharing (Why we use Links in Version 1):
   - In new Firebase projects, Cloud Storage requires upgrading to the Blaze
     (pay-as-you-go) plan with a credit card.
   - Therefore, StudyCircle Version 1 implements Notes & PDF sharing using
     validated Google Drive / OneDrive / institutional PDF links inside rooms,
     costing 0 bytes of paid Storage.

4. Cloud Functions & Subscriptions / AI Key:
   - Cloud Functions for Firebase require the paid Blaze plan.
   - Because Cloud Functions are not free, Version 1 enforces:
     a) 3-day free trial and subscription lock using server timestamps in
        Firestore (`trialStartedAt` and `subscriptionEndsAt`), with manual Admin
        activation after you confirm a student's UPI/bank payment outside the app.
     b) Gemini AI Study Helper with a strict 15-questions/day counter stored in
        Firestore (`aiUsedToday` + `aiUsageDate`) and local answer caching so
        repeated study questions cost 0 API calls.

5. Cheapest Next Step When You Outgrow Free Spark:
   - Upgrade to Firebase Blaze (Pay-As-You-Go) and set a strict Budget Alert in
     Google Cloud Billing (e.g., Rs. 500 or $5). You still get the free daily
     tier (50k reads/day free), and only pay $0.06 per 100,000 extra reads.

--------------------------------------------------------------------------------
2. STEP-BY-STEP PHONE SETUP IN FIREBASE CONSOLE (CHROME ON ANDROID)
--------------------------------------------------------------------------------
Step 1: Open Chrome on your Android phone, tap the 3-dot menu (top right), and
        check "Desktop site" (Firebase Console works much better in Desktop view
        on a phone screen).
Step 2: Go to https://console.firebase.google.com and sign in with your Google
        account (`sushilk75960@gmail.com`).
Step 3: Open your project (`gen-lang-client-0400203041`) or tap "Add project"
        if creating a fresh one (disable Google Analytics to keep setup fast).
Step 4: Enable Authentication:
        1. In the left menu, tap "Build" -> "Authentication" -> "Get started".
        2. Tap the "Sign-in method" tab.
        3. Tap "Email/Password", switch Enable ON, and tap "Save".
        4. Tap "Google", switch Enable ON, choose your support email, and tap "Save".
Step 5: Enable Cloud Firestore:
        1. In the left menu, tap "Build" -> "Firestore Database".
        2. Your AI Studio database (`ai-studio-android-studycir-400c1ef8-2e25-4f86-86ba-7a77564ded40`)
           is already provisioned! If creating a separate project manually, tap
           "Create database", select a location (e.g., `asia-south1` Mumbai),
           and start in Production mode.
Step 6: Paste Security Rules (`firestore.rules`):
        1. Inside Firestore Database, tap the "Rules" tab at the top.
        2. Select everything in the editor, delete it, and paste the entire
           contents of `firestore.rules` from this project.
        3. Tap "Publish".
Step 7: Get Your Web App Config (if hosting as a standalone PWA):
        1. Tap the Gear icon (Project Overview) -> "Project settings".
        2. Scroll down to "Your apps" and tap the `</>` (Web) icon.
        3. Enter nickname `StudyCircle PWA` and tap "Register app".
        4. Copy the `firebaseConfig` object and paste it into `js/firebase.js`
           (already pre-filled for your project).

--------------------------------------------------------------------------------
3. WHERE THE GEMINI API KEY LIVES & HOW TO PROTECT IT (RULE #4)
--------------------------------------------------------------------------------
1. In the Native Android App (built in this project):
   - The app uses **Firebase AI Logic (`Firebase.ai`) + Firebase App Check**.
   - NO API key is stored in the app code or `.env`! Google verifies the APK
     signature via App Check automatically.

2. In the SPCK Web App (`js/ai.js`):
   - Because you have no Cloud Functions on the free Spark plan, any key placed
     in browser JavaScript can be inspected by a technical user.
   - To protect your free quota:
     a) Go to https://aistudio.google.com/app/apikey on your phone and create a
        free Gemini API key.
     b) Open https://console.cloud.google.com/apis/credentials in Chrome.
     c) Tap on your API Key -> under "Set an application restriction", choose
        "Websites" (HTTP referrers).
     d) Add your exact GitHub Pages URL (for example:
        `https://yourusername.github.io/*`) and tap "Save".
     e) Now, even if someone copies your key, Google's servers will reject calls
        from any other website!

--------------------------------------------------------------------------------
4. HOW TO CREATE YOUR FIRST ADMIN ACCOUNT & FIRST ALLOWED COLLEGE
--------------------------------------------------------------------------------
Because `firestore.rules` blocks normal students from making themselves Admin,
your owner email (`sushilk75960@gmail.com`) is pre-authorized in `firestore.rules`
via `isBootstrappedOwnerEmail()`. Here is how to set up your Admin account and
add your first college from your phone:

Step 1: Sign in to StudyCircle using your owner account (`sushilk75960@gmail.com`),
        OR create a user account and verify your email link.
Step 2: In the app, tap the "Bootstrap First Admin & Demo Colleges" button on
        the Admin / Profile Setup screen (or create them manually in the
        Firebase Console -> Firestore Database -> Data tab):
        - Collection: `allowedColleges`
          Document ID: `aiims_delhi`
          Fields:
            * `name` (string): `"AIIMS New Delhi"`
            * `emailDomain` (string): `"aiims.edu"`
            * `active` (boolean): `true`
            * `createdAt` (timestamp): choose current date/time
Step 3: To promote any other user to `admin`, `moderator`, or `advertiser`:
        - Open the Admin Panel inside StudyCircle (visible automatically when
          signed in as `sushilk75960@gmail.com` or when `role == "admin"`), OR
        - In Firebase Console -> Firestore Database -> `users` -> tap the user's
          UID document -> edit `role` from `"student"` to `"admin"`,
          `"moderator"`, or `"advertiser"`.

--------------------------------------------------------------------------------
5. PHONE-ONLY HOSTING ON GITHUB PAGES USING SPCK EDITOR
--------------------------------------------------------------------------------
Step 1: Install "Spck Editor / Git Client" from the Google Play Store.
Step 2: Create a free account at https://github.com in Chrome on your phone.
Step 3: Create a new public repository named `studycircle-pwa`.
Step 4: In Spck Editor, create the project folders (`css`, `js`) and copy-paste
        each file (`index.html`, `manifest.json`, `sw.js`, `css/styles.css`,
        and all `js/*.js` files).
Step 5: Commit and Push to GitHub directly from Spck Editor's Git menu.
Step 6: On GitHub in Chrome, open your repository -> tap "Settings" -> "Pages"
        -> under "Branch", select `main` and `/ (root)` -> tap "Save".
Step 7: Within 60 seconds, your PWA is live at:
        `https://<your-github-username>.github.io/studycircle-pwa/`
Step 8: Don't forget to add `<your-github-username>.github.io` to Firebase
        Console -> Authentication -> Settings -> "Authorized domains"!

--------------------------------------------------------------------------------
6. SECURITY RULES EXPLANATION & VERIFICATION CHECKLIST
--------------------------------------------------------------------------------
Every rule in `firestore.rules` is enforced on Google's servers:

[x] Rule 1 — Verified Email Gate (`isSignedIn()`):
    Checks `request.auth != null && request.auth.token.email_verified == true`.
    Unauthenticated users or unverified accounts cannot read or write any document.

[x] Rule 2 — Safe Student Registration (`match /users/{userId}` `allow create`):
    Checks `request.auth.uid == userId`, `incoming().role == 'student'` (except
    the bootstrapped owner email), `incoming().blocked == false`,
    `incoming().subscriptionStatus == 'trial'`, and `name` between 2 and 59 chars.

[x] Rule 3 — Immutable Role, College, Department & Subscription Fields (`allow update`):
    Students can update only `['name', 'semester', 'country', 'timezone',
    'aiUsageDate', 'aiUsedToday', 'referredBy', 'updatedAt']` while keeping
    `role`, `blocked`, `collegeId`, `department`, `trialStartedAt`,
    `subscriptionStatus`, `subscriptionEndsAt`, and `premiumBonusDays` unchanged.
    Only `isAdmin()` can modify those protected fields.

[x] Rule 4 — College & Department Isolation (`isSameCollegeAndDept`):
    `rooms`, `rooms/{roomId}/messages`, `doubts`, and `doubts/{doubtId}/answers`
    require the signed-in student's `collegeId` and `department` in `/users/{uid}`
    to match the document's `collegeId` and `department`.

[x] Rule 5 — Message Integrity & Deletion (`match /rooms/{roomId}/messages/{messageId}`):
    Enforces `incoming().senderId == request.auth.uid`, `text.size() >= 1 &&
    text.size() <= 2000`, and `createdAt is timestamp && createdAt <= request.time`.
    Only `request.auth.uid == existing().senderId || isModeratorOrAdmin()` can
    soft-delete (`deleted = true`) or delete a message.

[x] Rule 6 — Sponsored Ads Approval Gate (`match /ads/{adId}`):
    Students can read/list an ad ONLY when `existing().status == 'approved'`.
    Only an `advertiser` (or `admin`) can create an ad with
    `incoming().advertiserId == request.auth.uid` and `incoming().status == 'pending'`.
    Only `isAdmin()` can change `status` to `'approved'` or `'rejected'`.

[x] Rule 7 — Admin-Only Governance:
    Only `isAdmin()` can create/update/delete `allowedColleges`, change user
    roles/subscriptions, or grant referral bonus days. Only `isModeratorOrAdmin()`
    can resolve `reports`.
================================================================================
