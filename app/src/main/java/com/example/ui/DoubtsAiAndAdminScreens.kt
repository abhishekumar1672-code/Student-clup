package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AbuseReport
import com.example.data.AllowedCollege
import com.example.data.DoubtAnswer
import com.example.data.DoubtPost
import com.example.data.SponsoredAd
import com.example.data.UiState
import com.example.data.UserProfile

@Composable
fun DoubtsAndAnswersScreen(
    profile: UserProfile,
    doubtsState: UiState<List<DoubtPost>>,
    selectedDoubt: DoubtPost?,
    answersState: UiState<List<DoubtAnswer>>,
    onSelectDoubt: (DoubtPost?) -> Unit,
    onCreateDoubt: (String, String) -> Unit,
    onPostAnswer: (DoubtPost, String) -> Unit,
    onUpvoteAnswer: (String, DoubtAnswer) -> Unit,
    onMarkBestAnswer: (DoubtPost, DoubtAnswer) -> Unit
) {
    if (selectedDoubt != null) {
        BackHandler { onSelectDoubt(null) }
        var answerDraft by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectDoubt(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to doubts")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedDoubt.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Asked by ${selectedDoubt.authorName} • Sem ${selectedDoubt.semester}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(selectedDoubt.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                item {
                    Text("Peer Answers", style = MaterialTheme.typography.titleMedium)
                }

                when (answersState) {
                    is UiState.Loading -> item { CircularProgressIndicator() }
                    is UiState.Error -> item { Text(answersState.message, color = MaterialTheme.colorScheme.error) }
                    is UiState.Success -> {
                        val answers = answersState.data
                        if (answers.isEmpty()) {
                            item {
                                Text(
                                    text = "No answers yet. Be the first classmate to solve this doubt!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(answers, key = { it.id }) { ans ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (ans.best) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ans.authorName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (ans.best) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = "Best Answer",
                                                            tint = MaterialTheme.colorScheme.onSecondary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "BEST ANSWER",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(ans.text, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(
                                                onClick = { onUpvoteAnswer(selectedDoubt.id, ans) }
                                            ) {
                                                Icon(Icons.Default.ThumbUp, contentDescription = "Upvote", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Upvote (${ans.upvotes})")
                                            }
                                            if (selectedDoubt.authorId == profile.id && !ans.best) {
                                                Button(
                                                    onClick = { onMarkBestAnswer(selectedDoubt, ans) }
                                                ) {
                                                    Text("Mark Best Answer")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = answerDraft,
                        onValueChange = { answerDraft = it },
                        placeholder = { Text("Write a clear academic answer...") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (answerDraft.trim().length >= 2) {
                                onPostAnswer(selectedDoubt, answerDraft)
                                answerDraft = ""
                            }
                        }
                    ) {
                        Text("Answer")
                    }
                }
            }
        }
        return
    }

    var showAskDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${profile.department} Doubts Q&A", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Ask questions, upvote peer explanations & mark Best Answer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAskDialog = true },
                modifier = Modifier.testTag("ask_doubt_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ask Doubt")
            }
        }

        when (doubtsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(doubtsState.message) }
            is UiState.Success -> {
                val list = doubtsState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (list.isEmpty()) {
                        item {
                            Text("No doubts posted yet for ${profile.department}. Tap 'Ask Doubt' to post the first question!")
                        }
                    } else {
                        items(list, key = { it.id }) { doubt ->
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDoubt(doubt) }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Sem ${doubt.semester} • ${doubt.authorName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (doubt.solved) "SOLVED • ${doubt.answerCount} answers" else "${doubt.answerCount} answers",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (doubt.solved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(doubt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(doubt.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAskDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAskDialog = false },
            title = { Text("Post Academic Doubt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Question Title (5–150 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Detailed Question / Clinical or Engineering Context") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.trim().length >= 5 && body.trim().length >= 5) {
                            onCreateDoubt(title, body)
                            showAskDialog = false
                        }
                    }
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAskDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AiStudyHelperScreen(
    profile: UserProfile,
    aiState: AiAnswerState,
    onAskQuestion: (String) -> Unit
) {
    var question by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Study Tutor",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini AI Study Helper", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Department: ${profile.department} (Sem ${profile.semester}) • Daily Free Quota: ${profile.aiUsedToday} / 15 questions used today. Repeated study questions are served from cache at 0 quota cost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask an academic or exam study question...") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_question_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            question = "Explain the mechanism of action and clinical uses of Beta-blockers."
                        },
                        label = { Text("Sample MBBS Doubt") }
                    )
                    AssistChip(
                        onClick = {
                            question = "Explain Dijkstra's shortest path algorithm time complexity with a min-heap."
                        },
                        label = { Text("Sample B.Tech Doubt") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onAskQuestion(question) },
                    enabled = aiState !is AiAnswerState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ask_ai_button")
                ) {
                    Text(if (aiState is AiAnswerState.Loading) "Consulting Gemini Study Tutor..." else "Ask AI Study Helper")
                }
            }
        }

        // Mandatory Medical & Technical Disclaimer Label (Rule #9)
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Disclaimer",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "For study only. Verify with textbooks or faculty.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.testTag("ai_medical_technical_disclaimer")
                )
            }
        }

        when (aiState) {
            is AiAnswerState.Idle -> {}
            is AiAnswerState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AiAnswerState.Streaming -> {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Streaming Study Explanation...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(aiState.partialText, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "For study only. Verify with textbooks or faculty.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            is AiAnswerState.Success -> {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (aiState.fromCache) "Study Answer (Instant Local Cache)" else "Study Answer (Gemini 3.8 Flash)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(aiState.answer, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "For study only. Verify with textbooks or faculty.",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
            is AiAnswerState.Error -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = aiState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { onAskQuestion(question) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAndAdvertiserScreen(
    profile: UserProfile,
    collegesState: UiState<List<AllowedCollege>>,
    adminUsersState: UiState<List<UserProfile>>,
    adminReportsState: UiState<List<AbuseReport>>,
    adminAdsState: UiState<List<SponsoredAd>>,
    onBootstrapColleges: () -> Unit,
    onAddCollege: (String, String) -> Unit,
    onModifyUser: (UserProfile, String, String, Long, Boolean) -> Unit,
    onSetAdStatus: (String, String) -> Unit,
    onResolveReport: (String, String) -> Unit,
    onCreateAd: (String, String, String, String, String, String, Long, String, String, String) -> Unit,
    onOpenPartAHub: () -> Unit
) {
    val isAdmin = profile.role == "admin" || profile.email.equals("sushilk75960@gmail.com", ignoreCase = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (isAdmin) {
        listOf("Colleges", "Users & Trial/Sub", "Reports", "Ads & Sponsors", "Part A SPCK Files")
    } else {
        listOf("Advertiser Portal", "Part A SPCK Files")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) }
                )
            }
        }

        if (!isAdmin) {
            if (selectedTab == 0) {
                AdvertiserCreateAdPane(onCreateAd = onCreateAd)
            } else {
                PartASpckExportDialogContent(onClose = null)
            }
            return
        }

        when (selectedTab) {
            0 -> AdminCollegesPane(
                collegesState = collegesState,
                onBootstrapColleges = onBootstrapColleges,
                onAddCollege = onAddCollege,
                onOpenPartAHub = onOpenPartAHub
            )
            1 -> AdminUsersPane(
                usersState = adminUsersState,
                onModifyUser = onModifyUser
            )
            2 -> AdminReportsPane(
                reportsState = adminReportsState,
                onResolveReport = onResolveReport
            )
            3 -> AdminAdsPane(
                adsState = adminAdsState,
                onSetAdStatus = onSetAdStatus,
                onCreateAd = onCreateAd
            )
            4 -> PartASpckExportDialogContent(onClose = null)
        }
    }
}

@Composable
private fun AdminCollegesPane(
    collegesState: UiState<List<AllowedCollege>>,
    onBootstrapColleges: () -> Unit,
    onAddCollege: (String, String) -> Unit,
    onOpenPartAHub: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add Allowed College Email Domain", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("College Name (e.g. CMC Vellore)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Email Domain (e.g. cmcvellore.ac.in)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (name.isNotBlank() && domain.isNotBlank()) {
                                    onAddCollege(name, domain)
                                    name = ""
                                    domain = ""
                                }
                            }
                        ) {
                            Text("Add College")
                        }
                        OutlinedButton(onClick = onBootstrapColleges) {
                            Text("Seed Default Colleges")
                        }
                    }
                }
            }
        }

        when (collegesState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item { Text(collegesState.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> {
                items(collegesState.data, key = { it.id }) { col ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(col.name, style = MaterialTheme.typography.titleMedium)
                                Text("@${col.emailDomain}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (col.active) "ACTIVE" else "INACTIVE", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersPane(
    usersState: UiState<List<UserProfile>>,
    onModifyUser: (UserProfile, String, String, Long, Boolean) -> Unit
) {
    when (usersState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(usersState.message) }
        is UiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usersState.data, key = { it.id }) { u ->
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${u.name} (${u.email})", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Role: ${u.role} • Dept: ${u.department} Sem ${u.semester} • Status: ${u.subscriptionStatus} • Bonus: ${u.premiumBonusDays}d",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onModifyUser(u, u.role, "active", 0L, u.blocked) }
                                ) {
                                    Text("Grant 30d Sub")
                                }
                                OutlinedButton(
                                    onClick = { onModifyUser(u, u.role, u.subscriptionStatus, 7L, u.blocked) }
                                ) {
                                    Text("+7 Bonus Days")
                                }
                                OutlinedButton(
                                    onClick = { onModifyUser(u, u.role, u.subscriptionStatus, 0L, !u.blocked) }
                                ) {
                                    Text(if (u.blocked) "Unblock" else "Block")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("student", "moderator", "advertiser", "admin").forEach { r ->
                                    FilterChip(
                                        selected = u.role == r,
                                        onClick = { onModifyUser(u, r, u.subscriptionStatus, 0L, u.blocked) },
                                        label = { Text(r) }
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

@Composable
private fun AdminReportsPane(
    reportsState: UiState<List<AbuseReport>>,
    onResolveReport: (String, String) -> Unit
) {
    when (reportsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(reportsState.message) }
        is UiState.Success -> {
            val reports = reportsState.data
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (reports.isEmpty()) {
                    item { Text("No abuse reports pending review.") }
                } else {
                    items(reports, key = { it.id }) { rep ->
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Reason: ${rep.reason}", style = MaterialTheme.typography.titleMedium)
                                Text("Status: ${rep.status} • Room: ${rep.roomId} • Msg: ${rep.messageId}", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onResolveReport(rep.id, "resolved") }) { Text("Mark Resolved") }
                                    OutlinedButton(onClick = { onResolveReport(rep.id, "dismissed") }) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAdsPane(
    adsState: UiState<List<SponsoredAd>>,
    onSetAdStatus: (String, String) -> Unit,
    onCreateAd: (String, String, String, String, String, String, Long, String, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AdvertiserCreateAdCard(onCreateAd = onCreateAd)
        }
        when (adsState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item { Text(adsState.message) }
            is UiState.Success -> {
                items(adsState.data, key = { it.id }) { ad ->
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${ad.title} [${ad.status.uppercase()}]", style = MaterialTheme.typography.titleMedium)
                            Text(ad.body, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Placement: ${ad.placement} • Target: ${ad.targetDepartment} Sem ${ad.targetSemester} (${ad.targetCountry}) • Views: ${ad.views} • Clicks: ${ad.clicks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSetAdStatus(ad.id, "approved") }) { Text("Approve") }
                                OutlinedButton(onClick = { onSetAdStatus(ad.id, "rejected") }) { Text("Reject") }
                                OutlinedButton(onClick = { onSetAdStatus(ad.id, "paused") }) { Text("Pause") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvertiserCreateAdPane(
    onCreateAd: (String, String, String, String, String, String, Long, String, String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AdvertiserCreateAdCard(onCreateAd = onCreateAd)
    }
}

@Composable
private fun AdvertiserCreateAdCard(
    onCreateAd: (String, String, String, String, String, String, Long, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("https://example.edu/course") }
    var placement by remember { mutableStateOf("home_card") }
    var targetDept by remember { mutableStateOf("ALL") }

    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Create Sponsored Ad (Requires Admin Approval)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Headline (3–120 chars)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Short Ad Description (5–300 chars)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Destination URL (https://...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("home_card", "room_banner", "room_pinned").forEach { p ->
                    FilterChip(
                        selected = placement == p,
                        onClick = { placement = p },
                        label = { Text(p) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "MBBS", "B.Tech").forEach { d ->
                    FilterChip(
                        selected = targetDept == d,
                        onClick = { targetDept = d },
                        label = { Text("Dept: $d") }
                    )
                }
            }
            Button(
                onClick = {
                    if (title.trim().length >= 3 && body.trim().length >= 5) {
                        onCreateAd(
                            title, body, "", link, placement,
                            targetDept, 0L, "ALL", "2026-11-01", "2027-12-31"
                        )
                        title = ""
                        body = ""
                    }
                }
            ) {
                Text("Submit Pending Ad")
            }
        }
    }
}

@Composable
fun PartASpckExportDialogContent(
    onClose: (() -> Unit)?
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableIntStateOf(0) }
    val files = listOf("README.txt", "js/firebase.js", "firestore.rules")

    val currentText = when (selectedFile) {
        0 -> PART_A_README_SUMMARY
        1 -> PART_A_FIREBASE_JS_SUMMARY
        else -> PART_A_RULES_SUMMARY
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Part A: Phone-Only SPCK Export Hub", style = MaterialTheme.typography.titleLarge)
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEachIndexed { idx, name ->
                FilterChip(
                    selected = selectedFile == idx,
                    onClick = { selectedFile = idx },
                    label = { Text(name) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(files[selectedFile], currentText))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy ${files[selectedFile]} to Clipboard for SPCK Editor")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = currentText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private const val PART_A_README_SUMMARY = """STUDYCIRCLE — PART A PHONE-ONLY SETUP SUMMARY
Full files are also saved in the project root: /README.txt, /js/firebase.js, /firestore.rules, and /firestore.test.js.

1. Free Spark Plan Limits (Honest Rule #10):
   - Firestore: 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day, 1 GiB storage.
   - Cannot support 2-3 lakh daily users on free tier; 50k reads/day breaks first (~300-800 daily active students).
   - Storage & Cloud Functions require Blaze plan, so Version 1 uses Google Drive/PDF links for notes, server timestamps for 3-day trial, and 15/day AI quota with caching.

2. First Admin Account & Allowed Colleges:
   - Your owner email (sushilk75960@gmail.com) is pre-authorized as Admin in firestore.rules.
   - Sign in with Google, tap 'Seed Allowed Colleges', and manage colleges, reports, ads, and user subscriptions in the Admin tab."""

private const val PART_A_FIREBASE_JS_SUMMARY = """// StudyCircle — js/firebase.js (ES Module for SPCK Editor)
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js";
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js";

export const firebaseConfig = {
  apiKey: "AIzaSyBhvx8lWBUkicNb3swx1IYHZ9PzKPbylmA",
  authDomain: "gen-lang-client-0400203041.firebaseapp.com",
  projectId: "gen-lang-client-0400203041",
  storageBucket: "gen-lang-client-0400203041.firebasestorage.app",
  messagingSenderId: "379450156492",
  appId: "1:379450156492:android:6fa10b30ca422af922a969"
};
export const FIRESTORE_DATABASE_ID = "ai-studio-android-studycir-400c1ef8-2e25-4f86-86ba-7a77564ded40";
export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() })
}, FIRESTORE_DATABASE_ID);"""

private const val PART_A_RULES_SUMMARY = """// StudyCircle — firestore.rules (Deployed & Emulator-Verified)
// Full 490-line ruleset is stored in /firestore.rules at the workspace root.
// Enforces:
// 1. request.auth != null && request.auth.token.email_verified == true
// 2. Self-registration only as role == 'student', blocked == false, subscriptionStatus == 'trial', name 2..59 chars
// 3. Students cannot mutate role, blocked, collegeId, department, trialStartedAt, or subscription fields
// 4. Rooms, messages, and doubts isolated by collegeId + department
// 5. Messages 1..2000 chars, server timestamp <= request.time, deletable only by sender or moderator/admin
// 6. Sponsored ads visible to students only when status == 'approved'"""
