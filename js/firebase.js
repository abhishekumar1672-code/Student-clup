// =============================================================================
// StudyCircle — js/firebase.js
// Complete, copy-paste-ready Firebase Modular SDK (ES Module) initialization
// Works directly in SPCK Editor / GitHub Pages / Firebase Hosting with zero npm
// =============================================================================

import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  signOut,
  reload
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js";
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  query,
  where,
  orderBy,
  limit,
  startAfter,
  onSnapshot,
  serverTimestamp,
  increment,
  arrayUnion,
  arrayRemove,
  Timestamp
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js";

/**
 * PUBLIC FIREBASE WEB CONFIGURATION
 * ---------------------------------
 * Security Note (Rule #4):
 * The Firebase Web Config below is safe and intended by Google to be public in
 * client code. It only identifies your Firebase project; it does NOT grant
 * read or write access. All data security is enforced on Google's servers by
 * `firestore.rules`.
 *
 * Pre-filled with your provisioned StudyCircle Firebase project (`gen-lang-client-0400203041`).
 * If you create a separate Web App in the Firebase Console, paste its `appId` below.
 */
export const firebaseConfig = {
  apiKey: "AIzaSyBhvx8lWBUkicNb3swx1IYHZ9PzKPbylmA",
  authDomain: "gen-lang-client-0400203041.firebaseapp.com",
  projectId: "gen-lang-client-0400203041",
  storageBucket: "gen-lang-client-0400203041.firebasestorage.app",
  messagingSenderId: "379450156492",
  appId: "1:379450156492:android:6fa10b30ca422af922a969"
};

/**
 * Named Firestore Database ID provisioned for StudyCircle.
 * If you use the default "(default)" database in a manually created project,
 * change this constant to "(default)".
 */
export const FIRESTORE_DATABASE_ID = "ai-studio-android-studycir-400c1ef8-2e25-4f86-86ba-7a77564ded40";

// 1. Initialize Firebase App
export const app = initializeApp(firebaseConfig);

// 2. Initialize Firebase Authentication
export const auth = getAuth(app);

// 3. Initialize Cloud Firestore with offline persistence for fast Android loading
let firestoreInstance;
try {
  firestoreInstance = initializeFirestore(
    app,
    {
      localCache: persistentLocalCache({
        tabManager: persistentMultipleTabManager()
      })
    },
    FIRESTORE_DATABASE_ID
  );
} catch (err) {
  // Fallback if already initialized
  firestoreInstance = getFirestore(app, FIRESTORE_DATABASE_ID);
}

export const db = firestoreInstance;

/**
 * WHERE DOES THE GEMINI API KEY LIVE? (Rule #4 & AI Helper Section)
 * -----------------------------------------------------------------
 * Because Firebase Cloud Functions require the paid Blaze plan, Version 1 of
 * the standalone SPCK PWA calls the Gemini API directly from `js/ai.js`.
 *
 * 1. NEVER hard-code an unrestricted API key in public repositories.
 * 2. In `js/ai.js`, the key is read from:
 *    - A domain-restricted key configured in `GEMINI_CONFIG.restrictedApiKey`
 *      that is locked strictly via HTTP Referrer restrictions in Google Cloud Console
 *      to ONLY your hosting URL (e.g., `https://your-username.github.io/*` or
 *      `https://gen-lang-client-0400203041.web.app/*`), OR
 *    - A student/owner key stored locally in `localStorage` (`studycircle_gemini_key`)
 *      if testing on localhost in SPCK Editor.
 * 3. Furthermore, inside the native Android StudyCircle app, Gemini calls use
 *    **Firebase AI Logic (`Firebase.ai`) + Firebase App Check**, which requires
 *    ZERO API key in client code!
 */

// Re-export modular SDK helpers so every other JS file imports only from `./firebase.js`
export {
  onAuthStateChanged,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  signOut,
  reload,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  query,
  where,
  orderBy,
  limit,
  startAfter,
  onSnapshot,
  serverTimestamp,
  increment,
  arrayUnion,
  arrayRemove,
  Timestamp
};
