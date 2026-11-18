package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AbuseReport
import com.example.data.AllowedCollege
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.DoubtAnswer
import com.example.data.DoubtPost
import com.example.data.SponsoredAd
import com.example.data.StudyCircleRepository
import com.example.data.StudyPlanner
import com.example.data.UiState
import com.example.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "StudyCircleVM"

sealed interface AiAnswerState {
    data object Idle : AiAnswerState
    data object Loading : AiAnswerState
    data class Streaming(val partialText: String) : AiAnswerState
    data class Success(val answer: String, val fromCache: Boolean) : AiAnswerState
    data class Error(val message: String) : AiAnswerState
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudyCircleViewModel(
    private val repository: StudyCircleRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    // 1. User Profile State
    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .toUiStateFlow("Failed to load student profile")

    // 2. Allowed Colleges State
    val collegesState: StateFlow<UiState<List<AllowedCollege>>> = repository.observeAllowedColleges()
        .toUiStateFlow("Failed to load allowed colleges")

    // 3. Rooms for current user's college & department
    val roomsState: StateFlow<UiState<List<ChatRoom>>> = profileState
        .flatMapLatest { state ->
            val profile = (state as? UiState.Success)?.data
            if (profile != null && profile.collegeId.isNotBlank() && profile.department.isNotBlank()) {
                repository.observeRooms(profile.collegeId, profile.department)
                    .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load chat rooms")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    // 4. Active Room & Pagination (multiples of 30 messages, detached when user leaves room)
    private val _activeRoom = MutableStateFlow<ChatRoom?>(null)
    val activeRoom: StateFlow<ChatRoom?> = _activeRoom.asStateFlow()

    private val _messagePageSize = MutableStateFlow(30L)
    val messagePageSize: StateFlow<Long> = _messagePageSize.asStateFlow()

    val roomMessagesState: StateFlow<UiState<List<ChatMessage>>> = combine(_activeRoom, _messagePageSize) { room, limitCount ->
        room to limitCount
    }.flatMapLatest { (room, limitCount) ->
        if (room == null) {
            flowOf(UiState.Success(emptyList()))
        } else {
            repository.observeRoomMessages(room.id, room.collegeId, room.department, limitCount)
                .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                .catchFirestore("Failed to load messages")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    fun enterRoom(room: ChatRoom) {
        _messagePageSize.value = 30L
        _activeRoom.value = room
    }

    fun leaveRoom() {
        _activeRoom.value = null
    }

    fun loadOlderMessages() {
        _messagePageSize.value += 30L
    }

    // 5. Blocked User IDs
    val blockedUserIds: StateFlow<List<String>> = repository.observeBlockedUsers(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // 6. Doubts & Active Doubt Answers
    val doubtsState: StateFlow<UiState<List<DoubtPost>>> = profileState
        .flatMapLatest { state ->
            val profile = (state as? UiState.Success)?.data
            if (profile != null && profile.collegeId.isNotBlank() && profile.department.isNotBlank()) {
                repository.observeDoubts(profile.collegeId, profile.department)
                    .map<List<DoubtPost>, UiState<List<DoubtPost>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load doubt posts")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedDoubt = MutableStateFlow<DoubtPost?>(null)
    val selectedDoubt: StateFlow<DoubtPost?> = _selectedDoubt.asStateFlow()

    val doubtAnswersState: StateFlow<UiState<List<DoubtAnswer>>> = _selectedDoubt
        .flatMapLatest { doubt ->
            if (doubt == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeDoubtAnswers(doubt.id, doubt.collegeId, doubt.department)
                    .map<List<DoubtAnswer>, UiState<List<DoubtAnswer>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load answers")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    fun selectDoubt(doubt: DoubtPost?) {
        _selectedDoubt.value = doubt
    }

    // 7. Study Planner
    val plannerState: StateFlow<UiState<StudyPlanner?>> = repository.observePlanner(currentUserId)
        .toUiStateFlow("Failed to load study planner")

    // 8. Sponsored Ads
    val approvedAdsState: StateFlow<UiState<List<SponsoredAd>>> = repository.observeApprovedAds()
        .toUiStateFlow("Failed to load sponsored cards")

    // 9. Admin Streams (active only when current user is admin)
    private val isAdminFlow: Flow<Boolean> = profileState.map { state ->
        val p = (state as? UiState.Success)?.data
        p?.role == "admin" || p?.email.equals("sushilk75960@gmail.com", ignoreCase = true)
    }

    val adminUsersState: StateFlow<UiState<List<UserProfile>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) {
                repository.observeAllUsersForAdmin()
                    .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load users for admin")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    val adminReportsState: StateFlow<UiState<List<AbuseReport>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) {
                repository.observeAllReportsForAdmin()
                    .map<List<AbuseReport>, UiState<List<AbuseReport>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load abuse reports")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    val adminAdsState: StateFlow<UiState<List<SponsoredAd>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) {
                repository.observeAllAdsForAdmin()
                    .map<List<SponsoredAd>, UiState<List<SponsoredAd>>> { UiState.Success(it) }
                    .catchFirestore("Failed to load ads for admin")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    // 10. AI Study Helper (Firebase.ai gemini-3.8-flash + 15/day quota + in-memory cache + mandatory medical/technical label)
    private val studyAiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are the StudyCircle Academic AI Tutor for verified college students (such as MBBS, B.Tech, BDS, Nursing, and Sciences). " +
                        "STRICT RULES: " +
                        "1. Answer ONLY academic, university curriculum, exam preparation, and study-related questions. " +
                        "2. Politely refuse non-academic, entertainment, gossip, or off-topic requests. " +
                        "3. Be accurate, structured, and concise. Explicitly state when you are unsure or when clinical/engineering guidelines vary. " +
                        "4. Every medical or technical answer is for study purposes only."
                )
            }
        )
    }

    private val answerCache = mutableMapOf<String, String>()
    private val _aiState = MutableStateFlow<AiAnswerState>(AiAnswerState.Idle)
    val aiState: StateFlow<AiAnswerState> = _aiState.asStateFlow()

    fun askStudyAi(question: String, profile: UserProfile) {
        val cleanQuestion = question.trim().take(1000)
        if (cleanQuestion.length < 4) {
            _aiState.value = AiAnswerState.Error("Please enter a clear study question (at least 4 characters).")
            return
        }
        val cacheKey = "${profile.department.lowercase()}:${cleanQuestion.lowercase()}"
        val cached = answerCache[cacheKey]
        if (cached != null) {
            _aiState.value = AiAnswerState.Success(cached, fromCache = true)
            return
        }

        viewModelScope.launch {
            _aiState.value = AiAnswerState.Loading
            val quotaResult = repository.recordAiQuestionUsage(profile)
            if (quotaResult.isFailure) {
                _aiState.value = AiAnswerState.Error(
                    quotaResult.exceptionOrNull()?.localizedMessage
                        ?: "Daily AI study limit (15 questions) reached."
                )
                return@launch
            }

            val accumulated = StringBuilder()
            try {
                val promptWithContext = "Student Department: ${profile.department}, Semester: ${profile.semester}.\n" +
                    "Academic Question: $cleanQuestion"
                withTimeout(60_000L) {
                    studyAiModel.generateContentStream(promptWithContext).collect { chunk ->
                        accumulated.append(chunk.text.orEmpty())
                        _aiState.value = AiAnswerState.Streaming(accumulated.toString())
                    }
                }
                val finalAnswer = accumulated.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    answerCache[cacheKey] = finalAnswer
                    _aiState.value = AiAnswerState.Success(finalAnswer, fromCache = false)
                } else {
                    _aiState.value = AiAnswerState.Error("AI returned an empty response. Please rephrase your study question.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI study helper timed out", e)
                _aiState.value = AiAnswerState.Error("AI tutor timed out after 60 seconds. Please tap Retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI study helper failed", e)
                _aiState.value = AiAnswerState.Error(
                    e.localizedMessage ?: "AI quota or network error. Please try again shortly."
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // User Mutations
    // -------------------------------------------------------------------------
    fun completeProfileSetup(
        name: String,
        email: String,
        collegeId: String,
        department: String,
        semester: Long,
        country: String,
        timezone: String,
        referredBy: String
    ) {
        viewModelScope.launch {
            val res = repository.createInitialProfile(
                name, email, collegeId, department, semester, country, timezone, referredBy
            )
            res.onSuccess { showToast("Welcome to StudyCircle! 3-day free trial activated.") }
                .onFailure { showToast(it.localizedMessage ?: "Failed to save profile") }
        }
    }

    fun updateSemesterAndProfile(name: String, semester: Long, country: String, timezone: String) {
        viewModelScope.launch {
            repository.updateProfileSemesterAndDetails(name, semester, country, timezone)
                .onSuccess { showToast("Profile updated.") }
                .onFailure { showToast(it.localizedMessage ?: "Update failed") }
        }
    }

    fun createDiscussionGroup(name: String, type: String, profile: UserProfile) {
        viewModelScope.launch {
            repository.createDiscussionRoom(name, type, profile.collegeId, profile.department, profile.semester)
                .onSuccess { showToast("Room '$name' created!") }
                .onFailure { showToast(it.localizedMessage ?: "Could not create room") }
        }
    }

    fun sendChatMessage(
        room: ChatRoom,
        senderName: String,
        text: String,
        type: String = "text",
        callMode: String = "none",
        linkUrl: String = ""
    ) {
        viewModelScope.launch {
            repository.sendRoomMessage(room, senderName, text, type, callMode, linkUrl)
                .onFailure { showToast(it.localizedMessage ?: "Failed to send message") }
        }
    }

    fun deleteChatMessage(roomId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(roomId, messageId)
                .onSuccess { showToast("Message deleted.") }
                .onFailure { showToast(it.localizedMessage ?: "Cannot delete message") }
        }
    }

    fun toggleBlock(targetUserId: String) {
        viewModelScope.launch {
            repository.toggleBlockUser(targetUserId, blockedUserIds.value)
                .onSuccess { showToast("Updated blocked user list.") }
                .onFailure { showToast(it.localizedMessage ?: "Failed to update block list") }
        }
    }

    fun reportContent(targetUserId: String, messageId: String, roomId: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetUserId, messageId, roomId, reason)
                .onSuccess { showToast("Report submitted to moderators.") }
                .onFailure { showToast(it.localizedMessage ?: "Report failed") }
        }
    }

    fun postDoubt(profile: UserProfile, title: String, body: String) {
        viewModelScope.launch {
            repository.createDoubtPost(profile, title, body)
                .onSuccess { showToast("Doubt posted to ${profile.department} community!") }
                .onFailure { showToast(it.localizedMessage ?: "Failed to post doubt") }
        }
    }

    fun submitAnswer(doubt: DoubtPost, authorName: String, text: String) {
        viewModelScope.launch {
            repository.postDoubtAnswer(doubt, authorName, text)
                .onSuccess { showToast("Answer posted!") }
                .onFailure { showToast(it.localizedMessage ?: "Failed to post answer") }
        }
    }

    fun upvoteAnswer(doubtId: String, answer: DoubtAnswer) {
        viewModelScope.launch {
            repository.toggleUpvoteAnswer(doubtId, answer)
                .onFailure { showToast(it.localizedMessage ?: "Could not upvote") }
        }
    }

    fun markAnswerAsBest(doubt: DoubtPost, answer: DoubtAnswer) {
        viewModelScope.launch {
            repository.markBestAnswer(doubt, answer)
                .onSuccess { showToast("Marked as Best Answer!") }
                .onFailure { showToast(it.localizedMessage ?: "Could not mark best answer") }
        }
    }

    fun savePlanner(examName: String, examDate: String, sessions: List<String>) {
        viewModelScope.launch {
            repository.saveStudyPlanner(examName, examDate, sessions)
                .onSuccess { showToast("Exam countdown & schedule saved!") }
                .onFailure { showToast(it.localizedMessage ?: "Could not save planner") }
        }
    }

    fun submitAd(
        title: String,
        body: String,
        imageUrl: String,
        link: String,
        placement: String,
        targetDept: String,
        targetSem: Long,
        targetCountry: String,
        startDate: String,
        endDate: String
    ) {
        viewModelScope.launch {
            repository.createSponsoredAd(
                title, body, imageUrl, link, placement,
                targetDept, targetSem, targetCountry, startDate, endDate
            )
                .onSuccess { showToast("Ad submitted for Admin approval.") }
                .onFailure { showToast(it.localizedMessage ?: "Could not submit ad") }
        }
    }

    fun recordAdClick(ad: SponsoredAd) {
        viewModelScope.launch {
            repository.incrementAdMetric(ad, isClick = true)
        }
    }

    // Admin actions
    fun bootstrapDemoColleges() {
        viewModelScope.launch {
            repository.bootstrapInitialColleges()
                .onSuccess { showToast("Default colleges & domains seeded!") }
                .onFailure { showToast(it.localizedMessage ?: "Bootstrap failed") }
        }
    }

    fun adminAddCollege(name: String, domain: String) {
        viewModelScope.launch {
            repository.adminAddAllowedCollege(name, domain)
                .onSuccess { showToast("Added $name ($domain)") }
                .onFailure { showToast(it.localizedMessage ?: "Failed to add college") }
        }
    }

    fun adminModifyUser(
        user: UserProfile,
        role: String,
        subStatus: String,
        bonusDaysToAdd: Long,
        blocked: Boolean
    ) {
        viewModelScope.launch {
            repository.adminUpdateUserStatus(user, role, subStatus, bonusDaysToAdd, blocked)
                .onSuccess { showToast("Updated ${user.name}") }
                .onFailure { showToast(it.localizedMessage ?: "Admin update failed") }
        }
    }

    fun adminSetAdStatus(adId: String, status: String) {
        viewModelScope.launch {
            repository.adminUpdateAdStatus(adId, status)
                .onSuccess { showToast("Ad marked $status") }
                .onFailure { showToast(it.localizedMessage ?: "Ad update failed") }
        }
    }

    fun adminResolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            repository.adminUpdateReportStatus(reportId, status)
                .onSuccess { showToast("Report $status") }
                .onFailure { showToast(it.localizedMessage ?: "Report update failed") }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(defaultError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catchFirestore(defaultError)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private fun <T> Flow<UiState<T>>.catchFirestore(defaultError: String): Flow<UiState<T>> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, defaultError, rootError)
            emit(UiState.Error(rootError.message ?: defaultError))
        }
}
