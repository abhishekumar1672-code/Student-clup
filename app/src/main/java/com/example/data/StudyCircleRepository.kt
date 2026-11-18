package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudyCircleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun currentUtcDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // -------------------------------------------------------------------------
    // 1. ALLOWED COLLEGES & USER PROFILE
    // -------------------------------------------------------------------------
    fun observeAllowedColleges(): Flow<List<AllowedCollege>> = flow {
        val uid = requireUserId()
        val path = "allowedColleges"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AllowedCollege::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        requireUserId()
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    rethrowOrLog(error, userId, OperationType.GET, path)
                }
        )
    }

    suspend fun createInitialProfile(
        name: String,
        email: String,
        collegeId: String,
        department: String,
        semester: Long,
        country: String,
        timezone: String,
        referredByCode: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$uid"
        return try {
            val verifiedUid = requireUserId()
            val cleanName = sanitizeInput(name, 59)
            require(cleanName.length >= 2) { "Full name must be between 2 and 59 characters." }
            val cleanDept = sanitizeInput(department, 60)
            val cleanCountry = sanitizeInput(country, 60)
            val cleanTz = sanitizeInput(timezone, 60)
            val cleanRefBy = sanitizeInput(referredByCode.uppercase(Locale.US), 24)
            val generatedRefCode = ("SC" + verifiedUid.take(6).uppercase(Locale.US))
                .replace(Regex("[^A-Z0-9]"), "X")
                .padEnd(8, '1')

            val isOwnerEmail = email.equals("sushilk75960@gmail.com", ignoreCase = true)
            val initialRole = if (isOwnerEmail) "admin" else "student"
            val trialEndDate = Timestamp(Date(System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L))

            val payload = mapOf(
                "name" to cleanName,
                "email" to email.trim().take(120),
                "collegeId" to collegeId.trim().take(100),
                "department" to cleanDept,
                "semester" to semester.coerceIn(1L, 12L),
                "country" to cleanCountry,
                "timezone" to cleanTz,
                "role" to initialRole,
                "createdAt" to FieldValue.serverTimestamp(),
                "trialStartedAt" to FieldValue.serverTimestamp(),
                "subscriptionStatus" to "trial",
                "subscriptionEndsAt" to trialEndDate,
                "premiumBonusDays" to 0L,
                "referralCode" to generatedRefCode,
                "referredBy" to cleanRefBy,
                "blocked" to false,
                "aiUsageDate" to currentUtcDateString(),
                "aiUsedToday" to 0L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(verifiedUid).set(payload).await()

            // Ensure default rooms exist for this college & department
            ensureDefaultRoomsForCollegeDept(collegeId.trim(), cleanDept, semester.coerceIn(1L, 12L))

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfileSemesterAndDetails(
        name: String,
        semester: Long,
        country: String,
        timezone: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$uid"
        return try {
            val verifiedUid = requireUserId()
            val cleanName = sanitizeInput(name, 59)
            require(cleanName.length >= 2) { "Name must be 2 to 59 characters." }
            db.collection("users").document(verifiedUid).update(
                mapOf(
                    "name" to cleanName,
                    "semester" to semester.coerceIn(1L, 12L),
                    "country" to sanitizeInput(country, 60),
                    "timezone" to sanitizeInput(timezone, 60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordAiQuestionUsage(profile: UserProfile): Result<Long> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$uid"
        return try {
            val verifiedUid = requireUserId()
            val today = currentUtcDateString()
            val usedToday = if (profile.aiUsageDate == today) profile.aiUsedToday else 0L
            if (usedToday >= 15L) {
                return Result.failure(IllegalStateException("Daily limit of 15 AI study questions reached. Resets at 00:00 UTC."))
            }
            val nextCount = usedToday + 1L
            db.collection("users").document(verifiedUid).update(
                mapOf(
                    "aiUsageDate" to today,
                    "aiUsedToday" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(nextCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 2. ROOMS & PAGINATED MESSAGES
    // -------------------------------------------------------------------------
    suspend fun ensureDefaultRoomsForCollegeDept(
        collegeId: String,
        department: String,
        semester: Long
    ) {
        val uid = auth.currentUser?.uid ?: return
        val safeCol = collegeId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val safeDept = department.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val defaults = listOf(
            Triple("rm_${safeCol}_${safeDept}_general", "$department General Lounge", "general" to 0L),
            Triple("rm_${safeCol}_${safeDept}_sem$semester", "Semester $semester Official Cohort", "semester" to semester),
            Triple("rm_${safeCol}_${safeDept}_subj1", "$department Core Subjects & Labs", "subject" to semester)
        )
        for ((roomId, title, typeAndSem) in defaults) {
            try {
                val ref = db.collection("rooms").document(roomId)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    ref.set(
                        mapOf(
                            "name" to title.take(80),
                            "type" to typeAndSem.first,
                            "collegeId" to collegeId,
                            "department" to department,
                            "semester" to typeAndSem.second,
                            "createdBy" to uid,
                            "memberCount" to 1L,
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {
                // Ignore if already created concurrently
            }
        }
    }

    fun observeRooms(collegeId: String, department: String): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereEqualTo("collegeId", collegeId)
                .whereEqualTo("department", department)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    suspend fun createDiscussionRoom(
        name: String,
        type: String,
        collegeId: String,
        department: String,
        semester: Long
    ): Result<Unit> {
        val path = "rooms"
        return try {
            val uid = requireUserId()
            val cleanName = sanitizeInput(name, 80)
            require(cleanName.length >= 2) { "Room name must be 2 to 80 characters." }
            val docRef = db.collection("rooms").document()
            docRef.set(
                mapOf(
                    "name" to cleanName,
                    "type" to if (type in listOf("semester", "subject", "general", "discussion")) type else "discussion",
                    "collegeId" to collegeId,
                    "department" to department,
                    "semester" to semester.coerceIn(0L, 12L),
                    "createdBy" to uid,
                    "memberCount" to 1L,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Real-time listener attached ONLY to the currently open room, limited to `pageSize` (multiples of 30).
     */
    fun observeRoomMessages(
        roomId: String,
        collegeId: String,
        department: String,
        limitCount: Long
    ): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId).collection("messages")
                .whereEqualTo("collegeId", collegeId)
                .whereEqualTo("department", department)
                .limit(limitCount)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    suspend fun sendRoomMessage(
        room: ChatRoom,
        senderName: String,
        text: String,
        type: String = "text", // text, meetLink, link
        callMode: String = "none", // none, voice, video
        linkUrl: String = ""
    ): Result<Unit> {
        val path = "rooms/${room.id}/messages"
        return try {
            val uid = requireUserId()
            val cleanText = sanitizeInput(text, 2000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val cleanUrl = linkUrl.trim().take(500)
            val msgRef = db.collection("rooms").document(room.id).collection("messages").document()
            msgRef.set(
                mapOf(
                    "roomId" to room.id,
                    "collegeId" to room.collegeId,
                    "department" to room.department,
                    "senderId" to uid,
                    "senderName" to sanitizeInput(senderName, 59),
                    "text" to cleanText,
                    "type" to type,
                    "callMode" to callMode,
                    "linkUrl" to cleanUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "deleted" to false
                )
            ).await()
            db.collection("rooms").document(room.id).update(
                mapOf("lastMessageAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> {
        val path = "rooms/$roomId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).collection("messages").document(messageId)
                .update(
                    mapOf(
                        "deleted" to true,
                        "text" to "[Message deleted]"
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 3. DOUBTS & ANSWERS
    // -------------------------------------------------------------------------
    fun observeDoubts(collegeId: String, department: String): Flow<List<DoubtPost>> = flow {
        val uid = requireUserId()
        val path = "doubts"
        emitAll(
            db.collection(path)
                .whereEqualTo("collegeId", collegeId)
                .whereEqualTo("department", department)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DoubtPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    fun observeDoubtAnswers(
        doubtId: String,
        collegeId: String,
        department: String
    ): Flow<List<DoubtAnswer>> = flow {
        val uid = requireUserId()
        val path = "doubts/$doubtId/answers"
        emitAll(
            db.collection("doubts").document(doubtId).collection("answers")
                .whereEqualTo("collegeId", collegeId)
                .whereEqualTo("department", department)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DoubtAnswer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<DoubtAnswer> { it.best }.thenByDescending { it.upvotes })
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    suspend fun createDoubtPost(
        profile: UserProfile,
        title: String,
        body: String
    ): Result<Unit> {
        val path = "doubts"
        return try {
            val uid = requireUserId()
            val cleanTitle = sanitizeInput(title, 150)
            val cleanBody = sanitizeInput(body, 3000)
            require(cleanTitle.length >= 5) { "Doubt title must be at least 5 characters." }
            require(cleanBody.length >= 5) { "Doubt description must be at least 5 characters." }
            db.collection("doubts").document().set(
                mapOf(
                    "collegeId" to profile.collegeId,
                    "department" to profile.department,
                    "semester" to profile.semester,
                    "authorId" to uid,
                    "authorName" to profile.name,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "answerCount" to 0L,
                    "solved" to false
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun postDoubtAnswer(
        doubt: DoubtPost,
        authorName: String,
        text: String
    ): Result<Unit> {
        val path = "doubts/${doubt.id}/answers"
        return try {
            val uid = requireUserId()
            val cleanText = sanitizeInput(text, 3000)
            require(cleanText.length >= 2) { "Answer must be at least 2 characters." }
            val ansRef = db.collection("doubts").document(doubt.id).collection("answers").document()
            ansRef.set(
                mapOf(
                    "doubtId" to doubt.id,
                    "collegeId" to doubt.collegeId,
                    "department" to doubt.department,
                    "authorId" to uid,
                    "authorName" to sanitizeInput(authorName, 59),
                    "text" to cleanText,
                    "upvotes" to 0L,
                    "upvotedBy" to emptyList<String>(),
                    "best" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("doubts").document(doubt.id).update(
                mapOf("answerCount" to (doubt.answerCount + 1L))
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleUpvoteAnswer(doubtId: String, answer: DoubtAnswer): Result<Unit> {
        val path = "doubts/$doubtId/answers/${answer.id}"
        return try {
            val uid = requireUserId()
            val alreadyUpvoted = answer.upvotedBy.contains(uid)
            val updatedList = if (alreadyUpvoted) {
                answer.upvotedBy - uid
            } else {
                (answer.upvotedBy + uid).take(200)
            }
            val newUpvotes = updatedList.size.toLong()
            db.collection("doubts").document(doubtId)
                .collection("answers").document(answer.id)
                .update(
                    mapOf(
                        "upvotes" to newUpvotes,
                        "upvotedBy" to updatedList
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markBestAnswer(doubt: DoubtPost, answer: DoubtAnswer): Result<Unit> {
        val path = "doubts/${doubt.id}/answers/${answer.id}"
        return try {
            requireUserId()
            db.collection("doubts").document(doubt.id)
                .collection("answers").document(answer.id)
                .update(mapOf("best" to true)).await()
            db.collection("doubts").document(doubt.id)
                .update(mapOf("solved" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 4. EXAM COUNTDOWN & STUDY SESSION PLANNER
    // -------------------------------------------------------------------------
    fun observePlanner(userId: String): Flow<StudyPlanner?> = flow {
        requireUserId()
        val path = "planner/$userId"
        emitAll(
            db.collection("planner").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(StudyPlanner::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    rethrowOrLog(error, userId, OperationType.GET, path)
                }
        )
    }

    suspend fun saveStudyPlanner(
        examName: String,
        examDate: String,
        sessions: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "planner/$uid"
        return try {
            val verifiedUid = requireUserId()
            val cleanExam = sanitizeInput(examName, 100).ifBlank { "Upcoming University Exam" }
            val cleanDate = sanitizeInput(examDate, 20).ifBlank { "2026-12-15" }
            val cleanSessions = sessions.map { sanitizeInput(it, 160) }.filter { it.isNotBlank() }.take(50)
            db.collection("planner").document(verifiedUid).set(
                mapOf(
                    "userId" to verifiedUid,
                    "examName" to cleanExam,
                    "examDate" to cleanDate,
                    "sessions" to cleanSessions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 5. BLOCKS & ABUSE REPORTS
    // -------------------------------------------------------------------------
    fun observeBlockedUsers(userId: String): Flow<List<String>> = flow {
        requireUserId()
        val path = "blocks/$userId"
        emitAll(
            db.collection("blocks").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserBlockList::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.blockedIds
                            ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
                .catch { error ->
                    rethrowOrLog(error, userId, OperationType.GET, path)
                }
        )
    }

    suspend fun toggleBlockUser(targetUserId: String, currentBlocked: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "blocks/$uid"
        return try {
            val verifiedUid = requireUserId()
            val updated = if (currentBlocked.contains(targetUserId)) {
                currentBlocked - targetUserId
            } else {
                (currentBlocked + targetUserId).distinct().take(200)
            }
            db.collection("blocks").document(verifiedUid).set(
                mapOf(
                    "userId" to verifiedUid,
                    "blockedIds" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetUserId: String,
        messageId: String,
        roomId: String,
        reason: String
    ): Result<Unit> {
        val path = "reports"
        return try {
            val uid = requireUserId()
            val cleanReason = sanitizeInput(reason, 500)
            require(cleanReason.length >= 3) { "Please provide a brief reason (3+ chars)." }
            db.collection("reports").document().set(
                mapOf(
                    "reporterId" to uid,
                    "targetUserId" to targetUserId.ifBlank { "unknown" },
                    "messageId" to messageId.ifBlank { "profile" },
                    "roomId" to roomId.ifBlank { "general" },
                    "reason" to cleanReason,
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 6. SPONSORED ADS (TARGETED BY DEPARTMENT, SEMESTER, COUNTRY)
    // -------------------------------------------------------------------------
    fun observeApprovedAds(): Flow<List<SponsoredAd>> = flow {
        val uid = requireUserId()
        val path = "ads"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "approved")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SponsoredAd::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    suspend fun createSponsoredAd(
        title: String,
        body: String,
        imageUrl: String,
        link: String,
        placement: String,
        targetDepartment: String,
        targetSemester: Long,
        targetCountry: String,
        startDate: String,
        endDate: String
    ): Result<Unit> {
        val path = "ads"
        return try {
            val uid = requireUserId()
            val cleanTitle = sanitizeInput(title, 120)
            val cleanBody = sanitizeInput(body, 300)
            require(cleanTitle.length >= 3) { "Ad title must be at least 3 characters." }
            require(cleanBody.length >= 5) { "Ad copy must be at least 5 characters." }
            db.collection("ads").document().set(
                mapOf(
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "imageUrl" to imageUrl.trim().take(500),
                    "link" to link.trim().take(500).ifBlank { "https://example.edu" },
                    "placement" to if (placement in listOf("home_card", "room_banner", "room_pinned")) placement else "home_card",
                    "advertiserId" to uid,
                    "targetDepartment" to sanitizeInput(targetDepartment, 60).ifBlank { "ALL" },
                    "targetSemester" to targetSemester.coerceIn(0L, 12L),
                    "targetCountry" to sanitizeInput(targetCountry, 60).ifBlank { "ALL" },
                    "startDate" to startDate.trim().take(16).ifBlank { "2026-01-01" },
                    "endDate" to endDate.trim().take(16).ifBlank { "2027-12-31" },
                    "status" to "pending",
                    "views" to 0L,
                    "clicks" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementAdMetric(ad: SponsoredAd, isClick: Boolean): Result<Unit> {
        val path = "ads/${ad.id}"
        return try {
            requireUserId()
            val updates = if (isClick) {
                mapOf("clicks" to (ad.clicks + 1L))
            } else {
                mapOf("views" to (ad.views + 1L))
            }
            db.collection("ads").document(ad.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 7. ADMIN PANEL OPERATIONS & BOOTSTRAP SEEDER
    // -------------------------------------------------------------------------
    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    fun observeAllReportsForAdmin(): Flow<List<AbuseReport>> = flow {
        val uid = requireUserId()
        val path = "reports"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AbuseReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    fun observeAllAdsForAdmin(): Flow<List<SponsoredAd>> = flow {
        val uid = requireUserId()
        val path = "ads"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SponsoredAd::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    rethrowOrLog(error, uid, OperationType.LIST, path)
                }
        )
    }

    suspend fun adminAddAllowedCollege(name: String, emailDomain: String): Result<Unit> {
        val path = "allowedColleges"
        return try {
            requireUserId()
            val cleanName = sanitizeInput(name, 120)
            val cleanDomain = emailDomain.trim().lowercase(Locale.US).removePrefix("@").take(100)
            require(cleanName.length >= 2) { "College name must be at least 2 characters." }
            require(cleanDomain.matches(Regex("^[a-z0-9.-]+\\.[a-z]{2,}$"))) {
                "Enter a valid domain like aiims.edu or iitd.ac.in"
            }
            val docId = cleanDomain.replace(Regex("[^a-z0-9_-]"), "_")
            db.collection("allowedColleges").document(docId).set(
                mapOf(
                    "name" to cleanName,
                    "emailDomain" to cleanDomain,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserStatus(
        targetUser: UserProfile,
        newRole: String,
        newSubStatus: String,
        addBonusDays: Long,
        blocked: Boolean
    ): Result<Unit> {
        val path = "users/${targetUser.id}"
        return try {
            requireUserId()
            val subEnd = if (newSubStatus == "active") {
                Timestamp(Date(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L))
            } else {
                targetUser.subscriptionEndsAt ?: Timestamp.now()
            }
            db.collection("users").document(targetUser.id).update(
                mapOf(
                    "role" to newRole,
                    "subscriptionStatus" to newSubStatus,
                    "subscriptionEndsAt" to subEnd,
                    "premiumBonusDays" to (targetUser.premiumBonusDays + addBonusDays).coerceIn(0L, 3650L),
                    "blocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateAdStatus(adId: String, status: String): Result<Unit> {
        val path = "ads/$adId"
        return try {
            requireUserId()
            db.collection("ads").document(adId).update(
                mapOf("status" to status)
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateReportStatus(reportId: String, status: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            requireUserId()
            db.collection("reports").document(reportId).update(
                mapOf("status" to status)
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * One-tap helper for the bootstrapped owner to seed initial allowed colleges (including gmail.com for testing)
     */
    suspend fun bootstrapInitialColleges(): Result<Unit> {
        val samples = listOf(
            "aiims_edu" to ("AIIMS New Delhi" to "aiims.edu"),
            "iitd_ac_in" to ("IIT Delhi" to "iitd.ac.in"),
            "ox_ac_uk" to ("University of Oxford" to "ox.ac.uk"),
            "gmail_com" to ("StudyCircle Owner & Demo Campus (gmail.com)" to "gmail.com")
        )
        return try {
            requireUserId()
            for ((id, nameAndDomain) in samples) {
                db.collection("allowedColleges").document(id).set(
                    mapOf(
                        "name" to nameAndDomain.first,
                        "emailDomain" to nameAndDomain.second,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "allowedColleges")
            Result.failure(e)
        }
    }

    private fun rethrowOrLog(
        error: Throwable,
        expectedUid: String,
        op: OperationType,
        path: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }
}
