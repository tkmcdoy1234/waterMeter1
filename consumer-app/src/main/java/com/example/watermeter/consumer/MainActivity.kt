package com.example.watermeter.consumer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.watermeter.consumer.ui.theme.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            WaterMeterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    ConsumerApp()
                }
            }
        }
    }
}

enum class AuthScreen {
    LOGIN, REGISTER, FORGOT_PASSWORD
}

@Composable
fun ConsumerApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var meterId by remember { mutableStateOf("") }
    var consumerName by remember { mutableStateOf("") }
    var profilePicBase64 by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn, meterId) {
        if (isLoggedIn && meterId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("accounts").document(meterId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        consumerName = snapshot.getString("name") ?: "Consumer"
                        profilePicBase64 = snapshot.getString("profilePic")
                    }
                }
        }
    }

    if (!isLoggedIn) {
        when (currentScreen) {
            AuthScreen.LOGIN -> LoginScreen(
                onLoginSuccess = { id, name ->
                    meterId = id
                    consumerName = name
                    isLoggedIn = true
                },
                onNavigateToRegister = { currentScreen = AuthScreen.REGISTER },
                onNavigateToForgot = { currentScreen = AuthScreen.FORGOT_PASSWORD }
            )
            AuthScreen.REGISTER -> RegisterScreen(
                onBackToLogin = { currentScreen = AuthScreen.LOGIN }
            )
            AuthScreen.FORGOT_PASSWORD -> ForgotPasswordScreen(
                onBackToLogin = { currentScreen = AuthScreen.LOGIN }
            )
        }
    } else {
        MainDashboard(meterId, consumerName, profilePicBase64, onLogout = { isLoggedIn = false })
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit, onNavigateToRegister: () -> Unit, onNavigateToForgot: () -> Unit) {
    var mId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WaterDrop,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Dauin Water", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Consumer Portal", fontSize = 16.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = mId,
            onValueChange = { mId = it },
            label = { Text("Meter ID") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = CardBg,
                focusedLabelColor = Primary
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = CardBg,
                focusedLabelColor = Primary
            )
        )
        
        if (error.isNotEmpty()) {
            Text(error, color = Danger, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (mId.isEmpty() || password.isEmpty()) {
                    error = "Please fill all fields"
                    return@Button
                }
                FirebaseFirestore.getInstance().collection("accounts").document(mId).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists() && doc.getString("password") == password) {
                            onLoginSuccess(mId, doc.getString("name") ?: "Consumer")
                        } else {
                            error = "Invalid ID or Password"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SIGN IN", color = BgDark, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToRegister) {
            Text("Register New Account", color = Primary)
        }
        TextButton(onClick = onNavigateToForgot) {
            Text("Forgot Password?", color = TextMuted)
        }
    }
}

@Composable
fun RegisterScreen(onBackToLogin: () -> Unit) {
    var mId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Register", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Link your water meter", fontSize = 16.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(value = mId, onValueChange = { mId = it }, label = { Text("Meter ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name (as per bill)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        Text("Security Question: Mother's Maiden Name?", fontSize = 12.sp, color = TextMuted, modifier = Modifier.align(Alignment.Start))
        OutlinedTextField(value = securityAnswer, onValueChange = { securityAnswer = it }, label = { Text("Your Answer") }, modifier = Modifier.fillMaxWidth())

        if (error.isNotEmpty()) {
            Text(error, color = Danger, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (mId.isEmpty() || name.isEmpty() || phone.isEmpty() || password.isEmpty() || securityAnswer.isEmpty()) {
                    error = "Please fill all fields"
                    return@Button
                }
                val db = FirebaseFirestore.getInstance()
                db.collection("consumers").document(mId).get().addOnSuccessListener { consumerDoc ->
                    if (!consumerDoc.exists()) {
                        error = "Meter ID not recognized."
                        return@addOnSuccessListener
                    }
                    db.collection("accounts").document(mId).get().addOnSuccessListener { accountDoc ->
                        if (accountDoc.exists()) {
                            error = "Already registered."
                            return@addOnSuccessListener
                        }
                        db.collection("consumers").document(mId).update("phoneNumber", phone)
                        val account = hashMapOf(
                            "meterId" to mId,
                            "name" to name,
                            "phoneNumber" to phone,
                            "password" to password,
                            "securityAnswer" to securityAnswer.lowercase(),
                            "status" to (consumerDoc.getString("status") ?: "Connected"),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("accounts").document(mId).set(account).addOnSuccessListener {
                            Toast.makeText(context, "Successful! Login now.", Toast.LENGTH_SHORT).show()
                            onBackToLogin()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CREATE ACCOUNT", color = BgDark, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBackToLogin) {
            Text("Back to Login", color = Primary)
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBackToLogin: () -> Unit) {
    var mId by remember { mutableStateOf("") }
    var step2 by remember { mutableStateOf(false) }
    var securityAnswer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var recoveryDoc by remember { mutableStateOf<Map<String, Any>?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recover", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Reset your password", fontSize = 16.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(value = mId, onValueChange = { mId = it }, label = { Text("Meter ID") }, modifier = Modifier.fillMaxWidth(), enabled = !step2)
        
        if (step2) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Security Question: Mother's Maiden Name?", fontSize = 12.sp, color = TextMuted, modifier = Modifier.align(Alignment.Start))
            OutlinedTextField(value = securityAnswer, onValueChange = { securityAnswer = it }, label = { Text("Your Answer") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }

        if (error.isNotEmpty()) {
            Text(error, color = Danger, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (mId.isEmpty()) {
                    error = "Enter Meter ID"
                    return@Button
                }
                val db = FirebaseFirestore.getInstance()
                if (!step2) {
                    db.collection("accounts").document(mId).get().addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            recoveryDoc = doc.data
                            step2 = true
                            error = ""
                        } else {
                            error = "Meter ID not found."
                        }
                    }
                } else {
                    if (securityAnswer.lowercase() == recoveryDoc?.get("securityAnswer")) {
                        db.collection("accounts").document(mId).update("password", newPassword).addOnSuccessListener {
                            Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                            onBackToLogin()
                        }
                    } else {
                        error = "Incorrect answer."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (!step2) "NEXT" else "UPDATE PASSWORD", color = BgDark, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBackToLogin) {
            Text("Back to Login", color = Primary)
        }
    }
}

@Composable
fun MainDashboard(meterId: String, name: String, profilePicBase64: String?, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CardBg.copy(alpha = 0.8f)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, unselectedIconColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, null) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, unselectedIconColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Mail, null) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, unselectedIconColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, unselectedIconColor = TextMuted)
                )
            }
        },
        containerColor = BgDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Header(name, profilePicBase64)
            when (selectedTab) {
                0 -> DashboardTab(meterId)
                1 -> HistoryTab(meterId)
                2 -> InboxTab(meterId, name)
                3 -> SettingsTab(meterId, profilePicBase64, onLogout)
            }
        }
    }
}

@Composable
fun Header(name: String, profilePicBase64: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            val sdf = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())
            Text(sdf.format(Date()), fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Text(name.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
        }
        
        val bitmap = remember(profilePicBase64) {
            profilePicBase64?.let { base64ToBitmap(it) }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(2.dp, CardBg, RoundedCornerShape(12.dp))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun DashboardTab(meterId: String) {
    var billings by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var latestBilling by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showUnsettledDialog by remember { mutableStateOf(false) }

    LaunchedEffect(meterId) {
        FirebaseFirestore.getInstance().collection("billings")
            .whereEqualTo("meterId", meterId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val records = snap?.documents?.map { it.data ?: mapOf() } ?: listOf()
                billings = records
                latestBilling = records.firstOrNull()
            }
    }

    val unsettledBillings = billings.filter { it["status"] == "Unpaid" }
    val totalBalance = unsettledBillings.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val usage = latestBilling?.get("usage") as? Double ?: 0.0
                Text("LIVE FLOW RATE", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${(usage / 720).format(2)}", fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text("M³/HR", fontSize = 14.sp, color = TextMuted)
            }
        }

        Row(modifier = Modifier.padding(horizontal = 20.dp)) {
            val usage = latestBilling?.get("usage") as? Double ?: 0.0
            StatCard("MONTH TOTAL", usage.format(2), "M³", Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            StatCard("EST. MONTHLY", (usage * 1.05).format(2), "M³", Modifier.weight(1f))
        }

        // Total Balance Card
        Card(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clickable { if (totalBalance > 0) showUnsettledDialog = true },
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("TOTAL BALANCE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (totalBalance > 0) {
                        Text("Click to view details", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("₱${totalBalance.format(2)}", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = if (totalBalance > 0) Danger else TextMain)
            }
        }

        latestBilling?.let { billing ->
            val billAmount = billing["amount"] as? Double ?: 0.0
            val billStatus = billing["status"] as? String ?: "Unpaid"
            val period = billing["periodCovered"] as? String ?: billing["date"] as? String ?: "N/A"
            Card(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("LAST STATEMENT", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Surface(color = if (billStatus == "Paid") Success.copy(0.2f) else Danger.copy(0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(billStatus.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (billStatus == "Paid") Success else Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("₱${billAmount.format(2)}", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("Period: $period", fontSize = 12.sp, color = TextMuted)
                    Button(onClick = { if (totalBalance > 0) showUnsettledDialog = true }, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("PAY NOW", color = BgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showUnsettledDialog) {
        UnsettledBillsDialog(unsettledBillings) { showUnsettledDialog = false }
    }
}

@Composable
fun UnsettledBillsDialog(unsettledBills: List<Map<String, Any>>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedBills by remember { mutableStateOf(unsettledBills.toSet()) }
    val totalSelected = selectedBills.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("SELECT BILLS TO PAY", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(unsettledBills) { bill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedBills.contains(bill)) {
                                        selectedBills = selectedBills - bill
                                    } else {
                                        selectedBills = selectedBills + bill
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedBills.contains(bill),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedBills = selectedBills + bill
                                    } else {
                                        selectedBills = selectedBills - bill
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Primary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = BgDark
                                )
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(bill["date"].toString(), fontWeight = FontWeight.Bold, color = TextMain, fontSize = 14.sp)
                                    Text("UNPAID", color = Danger, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                                val period = bill["periodCovered"] as? String ?: bill["date"] as? String ?: "N/A"
                                Text("Period: $period", fontSize = 11.sp, color = Primary.copy(0.7f))
                                Text("Usage: ${bill["usage"]} m³ | Amount: ₱${(bill["amount"] as? Number)?.toDouble()?.format(2)}", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        if (totalSelected > 0) {
                            Toast.makeText(context, "Redirecting to HitPay...", Toast.LENGTH_SHORT).show()
                            // In a real app, you'd trigger the web redirect here
                        }
                    },
                    enabled = selectedBills.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("PAY SELECTED (₱${totalSelected.format(2)})", color = BgDark, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("CLOSE", color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text(unit, fontSize = 14.sp, color = TextMuted)
        }
    }
}

@Composable
fun HistoryTab(meterId: String) {
    var billings by remember { mutableStateOf(listOf<Map<String, Any>>()) }

    LaunchedEffect(meterId) {
        FirebaseFirestore.getInstance().collection("billings")
            .whereEqualTo("meterId", meterId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                billings = snap?.documents?.map { it.data ?: mapOf() } ?: listOf()
            }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Card(
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("USAGE HISTORY", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                    UsageChart(billings.take(6).reversed())
                }
            }
        }
        
        item { Text("BILLING RECORDS", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp)) }
        
        items(billings) { billing ->
            Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(billing["date"].toString(), fontWeight = FontWeight.Bold, color = TextMain)
                        val period = billing["periodCovered"] as? String ?: billing["date"] as? String ?: "N/A"
                        Text("Period: $period", fontSize = 11.sp, color = Primary.copy(0.7f))
                        Text("Usage: ${billing["usage"]} m³ | Amount: ₱${(billing["amount"] as? Number)?.toDouble()?.format(2)}", fontSize = 12.sp, color = TextMuted)
                    }
                    Text(billing["status"].toString(), color = if (billing["status"] == "Paid") Success else Danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun UsageChart(data: List<Map<String, Any>>) {
    val usagePoints = data.map { (it["usage"] as? Number)?.toDouble() ?: 0.0 }
    if (usagePoints.isEmpty()) return

    val maxUsage = usagePoints.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / (usagePoints.size - 1).coerceAtLeast(1)
        
        val path = Path()
        val fillPath = Path()
        
        usagePoints.forEachIndexed { i, usage ->
            val x = i * spacing
            val y = height - (usage.toFloat() / maxUsage.toFloat() * height)
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                // Smooth line using cubic bezier (simplified)
                val prevX = (i - 1) * spacing
                val prevY = height - (usagePoints[i-1].toFloat() / maxUsage.toFloat() * height)
                path.cubicTo(
                    prevX + spacing / 2, prevY,
                    x - spacing / 2, y,
                    x, y
                )
                fillPath.cubicTo(
                    prevX + spacing / 2, prevY,
                    x - spacing / 2, y,
                    x, y
                )
            }
            
            if (i == usagePoints.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        // Draw Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Primary.copy(alpha = 0.3f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw Line
        drawPath(
            path = path,
            color = Primary,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw Points
        usagePoints.forEachIndexed { i, usage ->
            val x = i * spacing
            val y = height - (usage.toFloat() / maxUsage.toFloat() * height)
            drawCircle(
                color = Primary,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = BgDark,
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
    
    // Labels
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        data.forEach { billing ->
            val datePart = billing["date"].toString().split(" ")[0].split("-").lastOrNull() ?: ""
            Text(datePart, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun InboxTab(meterId: String, name: String) {
    var tickets by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var showDialog by remember { mutableStateOf(false) }
    var accountStatus by remember { mutableStateOf("Connected") }

    LaunchedEffect(meterId) {
        FirebaseFirestore.getInstance().collection("tickets")
            .whereEqualTo("meterId", meterId)
            .addSnapshotListener { snap, _ ->
                tickets = snap?.documents?.map { it.data ?: mapOf() } ?: listOf()
            }
        
        FirebaseFirestore.getInstance().collection("accounts").document(meterId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    accountStatus = snapshot.getString("status") ?: "Connected"
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text("+ NEW REQUEST", color = BgDark, fontWeight = FontWeight.Bold)
        }
        LazyColumn {
            items(tickets) { t ->
                Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t["type"].toString(), fontWeight = FontWeight.Bold, color = TextMain)
                            Text(t["status"].toString().uppercase(), color = if (t["status"] == "Resolved") Success else Warning, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text(t["details"].toString(), fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        }
    }

    if (showDialog) {
        var type by remember { mutableStateOf("Leak Report") }
        var details by remember { mutableStateOf("") }
        val options = mutableListOf("Leak Report", "Meter Issue")
        
        if (accountStatus.equals("Connected", ignoreCase = true)) {
            options.add("Disconnection Request")
        } else {
            options.add("Reconnection Request")
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("New Request") },
            text = {
                Column {
                    Text("Select Type", fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(type, color = TextMain)
                            Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        type = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = details, 
                        onValueChange = { details = it }, 
                        label = { Text("Provide details...") }, 
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (details.isBlank()) return@Button
                        val ticket = hashMapOf(
                            "meterId" to meterId,
                            "accountName" to name,
                            "type" to type,
                            "details" to details,
                            "status" to "Open",
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        FirebaseFirestore.getInstance().collection("tickets").add(ticket)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Submit", color = BgDark) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun SettingsTab(meterId: String, profilePicBase64: String?, onLogout: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("CONNECTED") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(context, it)
            if (bitmap != null) {
                val base64 = bitmapToBase64(bitmap)
                FirebaseFirestore.getInstance().collection("accounts").document(meterId)
                    .update("profilePic", base64)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    LaunchedEffect(meterId) {
        FirebaseFirestore.getInstance().collection("accounts").document(meterId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    status = snapshot.getString("status") ?: "CONNECTED"
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(profilePicBase64) {
            profilePicBase64?.let { base64ToBitmap(it) }
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(3.dp, Primary, CircleShape)
                .clickable { photoPickerLauncher.launch("image/*") }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Profile Pic",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    null,
                    modifier = Modifier.size(60.dp).align(Alignment.Center),
                    tint = Primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text("Tap photo to change", fontSize = 12.sp, color = TextMuted)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Meter ID: $meterId", color = TextMain)
        Text(
            text = "Status: ${status.uppercase()}", 
            color = if (status.equals("CONNECTED", ignoreCase = true)) Success else Danger, 
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CHANGE PASSWORD", color = TextMain, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("LOGOUT", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(meterId) { showPasswordDialog = false }
    }
}

@Composable
fun ChangePasswordDialog(meterId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("CHANGE PASSWORD", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (newPass != confirmPass) {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPass.length < 4) {
                            Toast.makeText(context, "Password too short", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val db = FirebaseFirestore.getInstance()
                        db.collection("accounts").document(meterId).get().addOnSuccessListener { doc ->
                            if (doc.getString("password") == oldPass) {
                                db.collection("accounts").document(meterId).update("password", newPass)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                            } else {
                                Toast.makeText(context, "Current password incorrect", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("UPDATE PASSWORD", color = BgDark, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        }
    }
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val pureBase64 = if (base64Str.contains(",")) base64Str.split(",")[1] else base64Str
        val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    val byteArray = outputStream.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
}

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
