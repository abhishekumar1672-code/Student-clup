package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class AllowedCollege(
    @DocumentId val id: String = "",
    val name: String = "",
    val emailDomain: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val collegeId: String = "",
    val department: String = "",
    val semester: Long = 1L,
    val country: String = "India",
    val timezone: String = "Asia/Kolkata",
    val role: String = "student", // student, moderator, admin, advertiser
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val trialStartedAt: Timestamp? = null,
    val subscriptionStatus: String = "trial", // trial, active, expired
    val subscriptionEndsAt: Timestamp? = null,
    val premiumBonusDays: Long = 0L,
    val referralCode: String = "",
    val referredBy: String = "",
    val blocked: Boolean = false,
    val aiUsageDate: String = "",
    val aiUsedToday: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasFullAccess(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (blocked) return false
        if (role == "admin" || role == "moderator") return true
        val bonusMillis = premiumBonusDays * 24L * 60L * 60L * 1000L
        if (subscriptionStatus == "active") {
            val endMillis = (subscriptionEndsAt?.toDate()?.time ?: nowMillis) + bonusMillis
            return nowMillis <= endMillis
        }
        val trialStartMillis = trialStartedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: nowMillis
        val trialLimitMillis = (3L * 24L * 60L * 60L * 1000L) + bonusMillis
        return (nowMillis - trialStartMillis) <= trialLimitMillis
    }

    fun remainingTrialOrSubDays(nowMillis: Long = System.currentTimeMillis()): Long {
        val bonusMillis = premiumBonusDays * 24L * 60L * 60L * 1000L
        val targetEndMillis = if (subscriptionStatus == "active" && subscriptionEndsAt != null) {
            subscriptionEndsAt.toDate().time + bonusMillis
        } else {
            val start = trialStartedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: nowMillis
            start + (3L * 24L * 60L * 60L * 1000L) + bonusMillis
        }
        val diff = targetEndMillis - nowMillis
        return if (diff <= 0L) 0L else (diff / (24L * 60L * 60L * 1000L)) + 1L
    }
}

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val name: String = "",
    val type: String = "general", // semester, subject, general, discussion
    val collegeId: String = "",
    val department: String = "",
    val semester: Long = 0L,
    val createdBy: String = "",
    val memberCount: Long = 1L,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val collegeId: String = "",
    val department: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val type: String = "text", // text, meetLink, link
    val callMode: String = "none", // none, voice, video
    val linkUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val deleted: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class DoubtPost(
    @DocumentId val id: String = "",
    val collegeId: String = "",
    val department: String = "",
    val semester: Long = 1L,
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val body: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val answerCount: Long = 0L,
    val solved: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class DoubtAnswer(
    @DocumentId val id: String = "",
    val doubtId: String = "",
    val collegeId: String = "",
    val department: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val upvotes: Long = 0L,
    val upvotedBy: List<String> = emptyList(),
    val best: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AbuseReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetUserId: String = "",
    val messageId: String = "",
    val roomId: String = "",
    val reason: String = "",
    val status: String = "open", // open, resolved, dismissed
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserBlockList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val blockedIds: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyPlanner(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examName: String = "",
    val examDate: String = "",
    val sessions: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SponsoredAd(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val link: String = "",
    val placement: String = "home_card", // home_card, room_banner, room_pinned
    val advertiserId: String = "",
    val targetDepartment: String = "ALL",
    val targetSemester: Long = 0L,
    val targetCountry: String = "ALL",
    val startDate: String = "2026-01-01",
    val endDate: String = "2027-12-31",
    val status: String = "pending", // pending, approved, rejected, paused
    val views: Long = 0L,
    val clicks: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referralCode: String = "",
    val newStudentUid: String = "",
    val newStudentName: String = "",
    val bonusDaysGranted: Long = 0L,
    val status: String = "pending", // pending, granted
    @ServerTimestamp val createdAt: Timestamp? = null
)
