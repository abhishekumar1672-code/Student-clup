package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.StudyCircleRepository
import com.example.data.UiState
import com.example.data.configureAppCheck
import com.example.ui.AdminAndAdvertiserScreen
import com.example.ui.AiStudyHelperScreen
import com.example.ui.AuthScreen
import com.example.ui.DoubtsAndAnswersScreen
import com.example.ui.HomePlannerAndReferralScreen
import com.example.ui.PartASpckExportDialogContent
import com.example.ui.PaywallScreen
import com.example.ui.ProfileSetupScreen
import com.example.ui.RoomsAndChatScreen
import com.example.ui.StudyCircleViewModel
import com.example.ui.VerifyEmailGateScreen
import com.example.ui.signOutUser
import com.example.ui.theme.StudyCircleTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1: Mandatory App Check configuration with launch intent before UI/ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            StudyCircleTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StudyCircleRootApp(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

@Composable
fun StudyCircleRootApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var showPartAOverlay by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showPartAOverlay) {
        BackHandler { showPartAOverlay = false }
        PartASpckExportDialogContent(onClose = { showPartAOverlay = false })
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onOpenPartAExport = { showPartAOverlay = true }
        )
    } else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember { CredentialManager.create(context) }

        if (!user.isEmailVerified) {
            VerifyEmailGateScreen(
                user = user,
                onVerified = { currentUser = Firebase.auth.currentUser },
                onSignOut = {
                    signOutUser(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        } else {
            val uid = user.uid
            key(uid) {
                val vm: StudyCircleViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            StudyCircleViewModel(StudyCircleRepository(db), uid)
                        }
                    }
                )
                AuthenticatedStudyCircleShell(
                    userEmail = user.email.orEmpty(),
                    userDisplayName = user.displayName.orEmpty(),
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenPartAOverlay = { showPartAOverlay = true },
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { vm.leaveRoom() },
                            onSignOutComplete = { currentUser = null }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedStudyCircleShell(
    userEmail: String,
    userDisplayName: String,
    viewModel: StudyCircleViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenPartAOverlay: () -> Unit,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val collegesState by viewModel.collegesState.collectAsStateWithLifecycle()
    val roomsState by viewModel.roomsState.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val messagesState by viewModel.roomMessagesState.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedUserIds.collectAsStateWithLifecycle()
    val doubtsState by viewModel.doubtsState.collectAsStateWithLifecycle()
    val selectedDoubt by viewModel.selectedDoubt.collectAsStateWithLifecycle()
    val answersState by viewModel.doubtAnswersState.collectAsStateWithLifecycle()
    val plannerState by viewModel.plannerState.collectAsStateWithLifecycle()
    val approvedAdsState by viewModel.approvedAdsState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val adminReportsState by viewModel.adminReportsState.collectAsStateWithLifecycle()
    val adminAdsState by viewModel.adminAdsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    when (val pState = profileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        is UiState.Success -> {
            val profile = pState.data
            if (profile == null) {
                ProfileSetupScreen(
                    userEmail = userEmail,
                    userDisplayName = userDisplayName,
                    collegesState = collegesState,
                    onBootstrapColleges = { viewModel.bootstrapDemoColleges() },
                    onSubmitProfile = { name, colId, dept, sem, country, tz, refBy ->
                        viewModel.completeProfileSetup(
                            name, userEmail, colId, dept, sem, country, tz, refBy
                        )
                    },
                    onSignOut = onSignOut
                )
                return
            }

            var currentTab by rememberSaveable { mutableIntStateOf(0) }
            val isAdmin = profile.role == "admin" || userEmail.equals("sushilk75960@gmail.com", ignoreCase = true)

            if (!profile.hasFullAccess() && currentTab != 4) {
                PaywallScreen(
                    profile = profile,
                    onSignOut = onSignOut,
                    onOpenAdminIfAdmin = if (isAdmin) ({ currentTab = 4 }) else null
                )
                return
            }

            if (currentTab != 0) {
                BackHandler {
                    if (activeRoom != null) {
                        viewModel.leaveRoom()
                    } else if (selectedDoubt != null) {
                        viewModel.selectDoubt(null)
                    } else {
                        currentTab = 0
                    }
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("StudyCircle", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "${profile.department} • S${profile.semester}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Dark/Light Theme"
                                )
                            }
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("top_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Sign Out"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = {
                                viewModel.leaveRoom()
                                currentTab = 0
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Rooms") },
                            label = { Text("Rooms") },
                            modifier = Modifier.testTag("nav_rooms")
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = {
                                viewModel.leaveRoom()
                                currentTab = 2
                            },
                            icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = "Doubts") },
                            label = { Text("Doubts") },
                            modifier = Modifier.testTag("nav_doubts")
                        )
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = {
                                viewModel.leaveRoom()
                                currentTab = 3
                            },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Tutor") },
                            label = { Text("AI Tutor") },
                            modifier = Modifier.testTag("nav_ai")
                        )
                        NavigationBarItem(
                            selected = currentTab == 4,
                            onClick = {
                                viewModel.leaveRoom()
                                currentTab = 4
                            },
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin & Ads") },
                            label = { Text(if (isAdmin) "Admin" else "Ads/Files") },
                            modifier = Modifier.testTag("nav_admin")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        0 -> HomePlannerAndReferralScreen(
                            profile = profile,
                            plannerState = plannerState,
                            approvedAdsState = approvedAdsState,
                            onSavePlanner = { exam, date, sessions ->
                                viewModel.savePlanner(exam, date, sessions)
                            },
                            onUpdateSemester = { name, sem, country, tz ->
                                viewModel.updateSemesterAndProfile(name, sem, country, tz)
                            },
                            onAdClick = { ad -> viewModel.recordAdClick(ad) },
                            onOpenPartAFiles = onOpenPartAOverlay
                        )
                        1 -> RoomsAndChatScreen(
                            profile = profile,
                            roomsState = roomsState,
                            activeRoom = activeRoom,
                            messagesState = messagesState,
                            blockedUserIds = blockedIds,
                            approvedAdsState = approvedAdsState,
                            onEnterRoom = { viewModel.enterRoom(it) },
                            onLeaveRoom = { viewModel.leaveRoom() },
                            onLoadOlderMessages = { viewModel.loadOlderMessages() },
                            onCreateDiscussionRoom = { name, type ->
                                viewModel.createDiscussionGroup(name, type, profile)
                            },
                            onSendMessage = { room, text, type, callMode, linkUrl ->
                                viewModel.sendChatMessage(room, profile.name, text, type, callMode, linkUrl)
                            },
                            onDeleteMessage = { rId, mId -> viewModel.deleteChatMessage(rId, mId) },
                            onToggleBlockUser = { targetId -> viewModel.toggleBlock(targetId) },
                            onReportMessage = { targetUid, mId, rId, reason ->
                                viewModel.reportContent(targetUid, mId, rId, reason)
                            },
                            onAdClick = { ad -> viewModel.recordAdClick(ad) }
                        )
                        2 -> DoubtsAndAnswersScreen(
                            profile = profile,
                            doubtsState = doubtsState,
                            selectedDoubt = selectedDoubt,
                            answersState = answersState,
                            onSelectDoubt = { viewModel.selectDoubt(it) },
                            onCreateDoubt = { title, body -> viewModel.postDoubt(profile, title, body) },
                            onPostAnswer = { doubt, text -> viewModel.submitAnswer(doubt, profile.name, text) },
                            onUpvoteAnswer = { dId, ans -> viewModel.upvoteAnswer(dId, ans) },
                            onMarkBestAnswer = { doubt, ans -> viewModel.markAnswerAsBest(doubt, ans) }
                        )
                        3 -> AiStudyHelperScreen(
                            profile = profile,
                            aiState = aiState,
                            onAskQuestion = { q -> viewModel.askStudyAi(q, profile) }
                        )
                        4 -> AdminAndAdvertiserScreen(
                            profile = profile,
                            collegesState = collegesState,
                            adminUsersState = adminUsersState,
                            adminReportsState = adminReportsState,
                            adminAdsState = adminAdsState,
                            onBootstrapColleges = { viewModel.bootstrapDemoColleges() },
                            onAddCollege = { name, dom -> viewModel.adminAddCollege(name, dom) },
                            onModifyUser = { u, role, sub, bonus, blk ->
                                viewModel.adminModifyUser(u, role, sub, bonus, blk)
                            },
                            onSetAdStatus = { adId, st -> viewModel.adminSetAdStatus(adId, st) },
                            onResolveReport = { repId, st -> viewModel.adminResolveReport(repId, st) },
                            onCreateAd = { title, body, img, link, place, dept, sem, ctry, start, end ->
                                viewModel.submitAd(title, body, img, link, place, dept, sem, ctry, start, end)
                            },
                            onOpenPartAHub = onOpenPartAOverlay
                        )
                    }
                }
            }
        }
    }
}
