package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.SponsoredAd
import com.example.data.StudyPlanner
import com.example.data.UiState
import com.example.data.UserProfile

@Composable
fun HomePlannerAndReferralScreen(
    profile: UserProfile,
    plannerState: UiState<StudyPlanner?>,
    approvedAdsState: UiState<List<SponsoredAd>>,
    onSavePlanner: (String, String, List<String>) -> Unit,
    onUpdateSemester: (String, Long, String, String) -> Unit,
    onAdClick: (SponsoredAd) -> Unit,
    onOpenPartAFiles: () -> Unit
) {
    val context = LocalContext.current
    var showEditPlannerDialog by remember { mutableStateOf(false) }
    var showEditSemesterDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Welcome & Trial / Subscription Status Banner
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hi, ${profile.name}",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "${profile.department} • Semester ${profile.semester} • ${profile.country}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${profile.subscriptionStatus.uppercase()} (${profile.remainingTrialOrSubDays()}d left)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showEditSemesterDialog = true },
                            modifier = Modifier.testTag("update_semester_button")
                        ) {
                            Text("Update Semester (${profile.semester})")
                        }
                        OutlinedButton(
                            onClick = onOpenPartAFiles,
                            modifier = Modifier.testTag("home_part_a_button")
                        ) {
                            Text("Part A Files & Guide")
                        }
                    }
                }
            }
        }

        // 2. Exam Countdown & Study Session Scheduler
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Exam Countdown",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exam Countdown & Study Sessions",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        TextButton(
                            onClick = { showEditPlannerDialog = true },
                            modifier = Modifier.testTag("edit_planner_button")
                        ) {
                            Text("Edit")
                        }
                    }

                    when (plannerState) {
                        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        is UiState.Error -> Text(plannerState.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            val planner = plannerState.data
                            if (planner == null) {
                                Text(
                                    text = "No exam countdown scheduled yet. Tap Edit to set your target exam date and study sessions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Target Exam: ${planner.examName} (${planner.examDate})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (planner.sessions.isEmpty()) {
                                    Text("No study blocks added yet.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    planner.sessions.forEachIndexed { idx, session ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${idx + 1}. $session",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Classmate Referral Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Referral Program",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Invite Classmates & Earn Free Premium Days",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your Referral Code: ${profile.referralCode} • Bonus Days Earned: ${profile.premiumBonusDays} days",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When your ${profile.department} classmates sign up with your code, an admin grants bonus premium days to your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Referral Code", profile.referralCode))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Code (${profile.referralCode})")
                    }
                }
            }
        }

        // 4. Sponsored Home Card (Only approved ads matching department/semester/country; subscribers see fewer ads)
        item {
            val ads = (approvedAdsState as? UiState.Success)?.data.orEmpty().filter { ad ->
                ad.placement == "home_card" &&
                    (ad.targetDepartment.equals("ALL", ignoreCase = true) ||
                        ad.targetDepartment.equals(profile.department, ignoreCase = true)) &&
                    (ad.targetSemester == 0L || ad.targetSemester == profile.semester)
            }
            val adToShow = ads.firstOrNull()
            if (adToShow != null) {
                SponsoredAdCard(ad = adToShow, onAdClick = onAdClick)
            }
        }
    }

    if (showEditPlannerDialog) {
        val existing = (plannerState as? UiState.Success)?.data
        var examName by remember { mutableStateOf(existing?.examName ?: "${profile.department} Sem ${profile.semester} Finals") }
        var examDate by remember { mutableStateOf(existing?.examDate ?: "2026-12-20") }
        var sessionsText by remember {
            mutableStateOf(
                existing?.sessions?.joinToString("\n")
                    ?: "Mon 18:00 | Core Subject Revision | 90m\nWed 19:30 | Past Question Papers | 60m"
            )
        }
        AlertDialog(
            onDismissRequest = { showEditPlannerDialog = false },
            title = { Text("Edit Exam Countdown & Study Sessions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text("Exam Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = examDate,
                        onValueChange = { examDate = it },
                        label = { Text("Exam Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sessionsText,
                        onValueChange = { sessionsText = it },
                        label = { Text("Study Sessions (one per line)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePlanner(
                            examName,
                            examDate,
                            sessionsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        showEditPlannerDialog = false
                    }
                ) {
                    Text("Save Planner")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPlannerDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditSemesterDialog) {
        var name by remember { mutableStateOf(profile.name) }
        var sem by remember { mutableStateOf(profile.semester.toString()) }
        var country by remember { mutableStateOf(profile.country) }
        var tz by remember { mutableStateOf(profile.timezone) }
        AlertDialog(
            onDismissRequest = { showEditSemesterDialog = false },
            title = { Text("Update Profile & Semester") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "College (${profile.collegeId}) and Department (${profile.department}) are permanently locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sem,
                        onValueChange = { sem = it },
                        label = { Text("Semester (1-12)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tz,
                        onValueChange = { tz = it },
                        label = { Text("Timezone") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSemester(name, sem.toLongOrNull() ?: profile.semester, country, tz)
                        showEditSemesterDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSemesterDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SponsoredAdCard(
    ad: SponsoredAd,
    onAdClick: (SponsoredAd) -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onAdClick(ad)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.link)))
                }
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Sponsored",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open Sponsored Link",
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = ad.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = ad.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RoomsAndChatScreen(
    profile: UserProfile,
    roomsState: UiState<List<ChatRoom>>,
    activeRoom: ChatRoom?,
    messagesState: UiState<List<ChatMessage>>,
    blockedUserIds: List<String>,
    approvedAdsState: UiState<List<SponsoredAd>>,
    onEnterRoom: (ChatRoom) -> Unit,
    onLeaveRoom: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onCreateDiscussionRoom: (String, String) -> Unit,
    onSendMessage: (ChatRoom, String, String, String, String) -> Unit,
    onDeleteMessage: (String, String) -> Unit,
    onToggleBlockUser: (String) -> Unit,
    onReportMessage: (String, String, String, String) -> Unit,
    onAdClick: (SponsoredAd) -> Unit
) {
    if (activeRoom != null) {
        BackHandler { onLeaveRoom() }
        DisposableEffect(activeRoom.id) {
            onDispose {
                // Ensure room real-time listener detaches when leaving screen
            }
        }
        ActiveChatRoomView(
            profile = profile,
            room = activeRoom,
            messagesState = messagesState,
            blockedUserIds = blockedUserIds,
            approvedAdsState = approvedAdsState,
            onBack = onLeaveRoom,
            onLoadOlder = onLoadOlderMessages,
            onSendMessage = onSendMessage,
            onDeleteMessage = onDeleteMessage,
            onToggleBlockUser = onToggleBlockUser,
            onReportMessage = onReportMessage,
            onAdClick = onAdClick
        )
        return
    }

    var showCreateRoomDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${profile.department} Rooms",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "College: ${profile.collegeId} • Semester & Subject Groups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCreateRoomDialog = true },
                modifier = Modifier.testTag("create_discussion_room_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Group")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Group")
            }
        }

        when (roomsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(roomsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val rooms = roomsState.data
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rooms.isEmpty()) {
                        item {
                            Text(
                                text = "No rooms found for ${profile.department}. Tap 'New Group' to create the first discussion room!",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(rooms, key = { it.id }) { room ->
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEnterRoom(room) }
                                    .testTag("room_card_${room.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Groups,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Type: ${room.type.uppercase()} • Sem ${if (room.semester == 0L) "All" else room.semester}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom banner ad in room list (Rule: banner at the bottom of the room list)
                val bannerAd = (approvedAdsState as? UiState.Success)?.data.orEmpty()
                    .firstOrNull { it.placement == "room_banner" }
                if (bannerAd != null) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SponsoredAdCard(ad = bannerAd, onAdClick = onAdClick)
                    }
                }
            }
        }
    }

    if (showCreateRoomDialog) {
        var roomName by remember { mutableStateOf("") }
        var roomType by remember { mutableStateOf("discussion") }
        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            title = { Text("Create Discussion / Subject Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Group or Subject Name (2-80 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("discussion", "subject", "semester").forEach { t ->
                            AssistChip(
                                onClick = { roomType = t },
                                label = { Text(t.uppercase()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomName.trim().length >= 2) {
                            onCreateDiscussionRoom(roomName, roomType)
                            showCreateRoomDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveChatRoomView(
    profile: UserProfile,
    room: ChatRoom,
    messagesState: UiState<List<ChatMessage>>,
    blockedUserIds: List<String>,
    approvedAdsState: UiState<List<SponsoredAd>>,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onSendMessage: (ChatRoom, String, String, String, String) -> Unit,
    onDeleteMessage: (String, String) -> Unit,
    onToggleBlockUser: (String) -> Unit,
    onReportMessage: (String, String, String, String) -> Unit,
    onAdClick: (SponsoredAd) -> Unit
) {
    val context = LocalContext.current
    var draftText by remember { mutableStateOf("") }
    var showShareNotesDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Room Header with Voice & Video Meet Call Buttons
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("leave_room_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to rooms")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${room.department} • Real-time listener active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                // Voice-only Meet call button
                IconButton(
                    onClick = {
                        onSendMessage(
                            room,
                            "Started a Voice-Only Study Call — Tap to join on Google Meet",
                            "meetLink",
                            "voice",
                            "https://meet.google.com/new"
                        )
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meet.google.com/new")))
                        }
                    },
                    modifier = Modifier.testTag("start_voice_meet_button")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Start Voice Call")
                }
                // Video Meet call button
                IconButton(
                    onClick = {
                        onSendMessage(
                            room,
                            "Started a Group Video Study Call — Tap to join on Google Meet",
                            "meetLink",
                            "video",
                            "https://meet.google.com/new"
                        )
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meet.google.com/new")))
                        }
                    },
                    modifier = Modifier.testTag("start_video_meet_button")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Start Video Call")
                }
                // Share Notes / PDF Link button
                IconButton(
                    onClick = { showShareNotesDialog = true },
                    modifier = Modifier.testTag("share_notes_link_button")
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Share Notes or PDF Link")
                }
            }
        }

        // Optional Pinned Sponsored Post inside room
        val pinnedAd = (approvedAdsState as? UiState.Success)?.data.orEmpty()
            .firstOrNull { it.placement == "room_pinned" }
        if (pinnedAd != null) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SponsoredAdCard(ad = pinnedAd, onAdClick = onAdClick)
            }
        }

        // Paginated Messages List (hides messages from blocked users)
        when (messagesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(messagesState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val visibleMessages = messagesState.data.filter { !blockedUserIds.contains(it.senderId) }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedButton(
                            onClick = onLoadOlder,
                            modifier = Modifier.fillMaxWidth().testTag("load_older_messages_button")
                        ) {
                            Text("Load Older Messages (30 per page)")
                        }
                    }

                    if (visibleMessages.isEmpty()) {
                        item {
                            Text(
                                text = "No messages yet. Say hello, share study notes, or start a Google Meet session!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(visibleMessages, key = { it.id }) { msg ->
                            val isMine = msg.senderId == profile.id
                            val canDelete = isMine || profile.role == "admin" || profile.role == "moderator"
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        msg.type == "meetLink" -> MaterialTheme.colorScheme.secondaryContainer
                                        msg.type == "link" -> MaterialTheme.colorScheme.tertiaryContainer
                                        isMine -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg.senderName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row {
                                            if (!isMine) {
                                                IconButton(
                                                    onClick = { reportTarget = msg },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Flag,
                                                        contentDescription = "Report Message",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onToggleBlockUser(msg.senderId) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Block,
                                                        contentDescription = "Block User",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (canDelete && !msg.deleted) {
                                                IconButton(
                                                    onClick = { onDeleteMessage(room.id, msg.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete Message",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (msg.type == "meetLink") {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (msg.callMode == "voice") "GOOGLE MEET • VOICE ONLY" else "GOOGLE MEET • VIDEO CALL",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (msg.type == "link") {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiary,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "STUDY NOTES / PDF LINK",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (msg.linkUrl.isNotBlank() && !msg.deleted) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.linkUrl)))
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (msg.type == "meetLink") "Open Google Meet" else "Open Notes / PDF")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Message Composer Bar
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it.take(2000) },
                    placeholder = { Text("Write a study message (max 2000 chars)...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draftText.trim().isNotEmpty()) {
                            onSendMessage(room, draftText, "text", "none", "")
                            draftText = ""
                        }
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .testTag("send_chat_message_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showShareNotesDialog) {
        var noteTitle by remember { mutableStateOf("") }
        var noteUrl by remember { mutableStateOf("https://drive.google.com/") }
        AlertDialog(
            onDismissRequest = { showShareNotesDialog = false },
            title = { Text("Share Study Notes / PDF Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Free Spark Plan Storage Note: Paste a Google Drive, OneDrive, or institutional PDF link to share notes at zero storage cost.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Topic / PDF Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = noteUrl,
                        onValueChange = { noteUrl = it },
                        label = { Text("Google Drive / PDF Link (https://...)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitle.trim().isNotEmpty() && noteUrl.startsWith("http")) {
                            onSendMessage(room, "Shared Notes: ${noteTitle.trim()}", "link", "none", noteUrl.trim())
                            showShareNotesDialog = false
                        }
                    }
                ) {
                    Text("Share in Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareNotesDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (reportTarget != null) {
        val target = reportTarget!!
        var reason by remember { mutableStateOf("Spam or off-topic content") }
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report Message by ${target.senderName}") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for report") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportMessage(target.senderId, target.id, room.id, reason)
                        reportTarget = null
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}
