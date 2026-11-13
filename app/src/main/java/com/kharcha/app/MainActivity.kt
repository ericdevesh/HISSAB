package com.kharcha.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kharcha.app.sync.HisaabGoogleSyncWorker
import java.util.concurrent.TimeUnit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.room.Room
import com.kharcha.app.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Ink = Color(0xFF070B16)
private val Panel = Color(0xFF111A2B)
private val Panel2 = Color(0xFF18243A)
private val Blue = Color(0xFF54C7FF)
private val Violet = Color(0xFF9B8CFF)
private val Green = Color(0xFF50E3B0)
private val Red = Color(0xFFFF8295)
private val Muted = Color(0xFF9AAAC4)
private val monthKey get() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, KharchaDatabase::class.java, "kharcha.db")
            .addMigrations(KharchaDatabase.MIGRATION_1_2, KharchaDatabase.MIGRATION_2_3).build()
        setContent { HisaabApp(db) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisaabApp(db: KharchaDatabase) {
    val context = LocalContext.current
    val googleOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/spreadsheets"),
                Scope("https://www.googleapis.com/auth/drive.file")
            ).build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, googleOptions) }
    var signedInEmail by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)?.email) }
    fun queueGoogleSync() {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<HisaabGoogleSyncWorker>().build())
        val periodic = PeriodicWorkRequestBuilder<HisaabGoogleSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("hisaab-google-sheets-sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) { signedInEmail = task.result?.email; queueGoogleSync() }
    }
    LaunchedEffect(Unit) {
        if (GoogleSignIn.getLastSignedInAccount(context) != null) queueGoogleSync()
    }
    val txDao = db.transactionDao(); val finance = db.financeDao(); val scope = rememberCoroutineScope()
    val transactions by txDao.observeAll().collectAsState(initial = emptyList())
    val income by txDao.observeIncome().collectAsState(initial = 0L)
    val expenses by txDao.observeExpenses().collectAsState(initial = 0L)
    val debts by finance.observeDebts().collectAsState(initial = emptyList())
    val budgets by finance.observeBudgets(monthKey).collectAsState(initial = emptyList())
    val goals by finance.observeGoals().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }; var hindi by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }; var editingType by remember { mutableStateOf("EXPENSE") }
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Food") }
    var person by remember { mutableStateOf("") }; var direction by remember { mutableStateOf("GIVEN") }; var note by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }; var budgetCategory by remember { mutableStateOf("General") }
    var selectedId by remember { mutableStateOf("") }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) { title = spoken; if (amount.isBlank()) amount = Regex("\\d+(?:[.,]\\d{1,2})?").find(spoken)?.value?.replace(',', '.') ?: "" }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { contentResolverForBackup(context.contentResolver, uri, transactions, debts, goals) }
    }
    val label = { en: String, hi: String -> if (hindi) hi else en }
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, secondary = Violet, background = Ink, surface = Panel, onSurface = Color.White)) {
        Scaffold(containerColor = Ink, topBar = {
            TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = com.kharcha.app.R.mipmap.ic_launcher),
                    contentDescription = "HISAAB app logo",
                    modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("HISAAB", fontWeight = FontWeight.Black, color = Blue)
                    Text(label("Your money, in control", "Aapka paisa, aapke control mein"), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            } },
                actions = {
                    TextButton(onClick = {
                        if (GoogleSignIn.getLastSignedInAccount(context) == null) signInLauncher.launch(googleClient.signInIntent)
                        else queueGoogleSync()
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Blue)
                        Spacer(Modifier.width(4.dp))
                        Text(if (signedInEmail == null) "Sign in with Google" else "Sync now", color = Blue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { hindi = !hindi }) { Text(if (hindi) "EN" else "हि", color = Blue, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { backupLauncher.launch("hisaab-backup-${monthKey}.csv") }) { Icon(Icons.Default.Backup, null, tint = Blue) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink))
        }, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Blue) {
                val nav = listOf(Triple(Icons.Default.Home, "Home", "होम"), Triple(Icons.Default.ReceiptLong, "Hisaab", "लेन-देन"), Triple(Icons.Default.People, "Udhari", "उधारी"), Triple(Icons.Default.Analytics, "Reports", "रिपोर्ट"), Triple(Icons.Default.Savings, "Goals", "बचत"), Triple(Icons.Default.PieChart, "Budget", "बजट"))
                nav.forEachIndexed { i, item -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.first, null) }, label = { Text(label(item.second, item.third)) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = Blue.copy(alpha = .12f), unselectedIconColor = Muted, unselectedTextColor = Muted)) }
            }
        }, floatingActionButton = {
            FloatingActionButton(onClick = { dialog = when(tab) { 2 -> "debt"; 4 -> "goal"; 5 -> "budget"; else -> "transaction" }; editingType = "EXPENSE" }, containerColor = Blue, contentColor = Ink, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, "Add") }
        }) { padding ->
            when(tab) {
                0 -> HomeScreen(Modifier.padding(padding), income, expenses, transactions, debts, budgets, goals, label) { tab = it }
                1 -> TransactionScreen(Modifier.padding(padding), transactions, label) { id -> scope.launch { txDao.delete(id) } }
                2 -> DebtScreen(Modifier.padding(padding), debts, label, { item -> selectedId=item.id; dialog="payment" }, { id -> scope.launch { finance.deleteDebt(id) } })
                3 -> ReportScreen(Modifier.padding(padding), income, expenses, transactions, debts, label)
                4 -> GoalScreen(Modifier.padding(padding), goals, label, { item -> selectedId=item.id; dialog="saving" }, { id -> scope.launch { finance.deleteGoal(id) } })
                else -> BudgetScreen(Modifier.padding(padding), budgets, transactions, label)
            }
        }
        if (dialog.isNotEmpty()) {
            AlertDialog(onDismissRequest = { dialog = "" }, containerColor = Panel2,
                title = { Text(when(dialog) { "debt" -> label("Add udhari", "उधारी जोड़ें"); "goal" -> label("Create savings goal", "बचत लक्ष्य बनाएं"); "budget" -> label("Set monthly budget", "मासिक बजट सेट करें"); "payment" -> label("Record repayment", "वापसी दर्ज करें"); "saving" -> label("Add savings", "बचत जोड़ें"); else -> label("Add transaction", "लेन-देन जोड़ें") }, color = Color.White) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when(dialog) {
                        "debt" -> { OutlinedTextField(person, {person=it}, label={Text(label("Person name", "व्यक्ति का नाम"))}, singleLine=true); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(direction=="GIVEN", {direction="GIVEN"}, label={Text(label("I gave", "मैंने दिए"))}); FilterChip(direction=="TAKEN", {direction="TAKEN"}, label={Text(label("I borrowed", "मैंने लिए"))}) }; OutlinedTextField(amount,{amount=it.moneyInput()},label={Text("₹ Amount")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true); OutlinedTextField(note,{note=it},label={Text(label("Note (optional)", "नोट (वैकल्पिक)"))}) }
                        "goal" -> { OutlinedTextField(title,{title=it},label={Text(label("Goal name", "लक्ष्य का नाम"))}); OutlinedTextField(goalTarget,{goalTarget=it.moneyInput()},label={Text(label("Target amount ₹", "लक्ष्य राशि ₹"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal)) }
                        "budget" -> { OutlinedTextField(budgetCategory,{budgetCategory=it},label={Text(label("Category", "श्रेणी"))}); OutlinedTextField(amount,{amount=it.moneyInput()},label={Text(label("Monthly limit ₹", "मासिक सीमा ₹"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal)) }
                        "payment", "saving" -> OutlinedTextField(amount,{amount=it.moneyInput()},label={Text(label("Amount ₹", "राशि ₹"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                        else -> { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { FilterChip(editingType=="EXPENSE",{editingType="EXPENSE"},label={Text(label("Expense", "खर्च"))}); FilterChip(editingType=="INCOME",{editingType="INCOME"},label={Text(label("Income", "आमदनी"))}) }; OutlinedTextField(title,{title=it},label={Text(label("Description", "विवरण"))},singleLine=true); OutlinedTextField(amount,{amount=it.moneyInput()},label={Text("₹ Amount")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true); OutlinedTextField(category,{category=it},label={Text(label("Category", "श्रेणी"))},singleLine=true); OutlinedButton(onClick={ val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, if(hindi) "hi-IN" else "en-IN"); runCatching { voiceLauncher.launch(i) } }, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Mic,null); Spacer(Modifier.width(8.dp)); Text(label("Speak expense", "बोलकर खर्च लिखें")) } }
                    }
                } },
                confirmButton = { TextButton(onClick = {
                    val paise=((amount.toDoubleOrNull()?:0.0)*100).toLong()
                    scope.launch {
                        when(dialog) {
                            "debt" -> if(person.isNotBlank() && paise>0) finance.saveDebt(DebtEntity(UUID.randomUUID().toString(),person.trim(),note=note,direction=direction,totalPaise=paise,createdAt=System.currentTimeMillis()))
                            "goal" -> if(title.isNotBlank() && (goalTarget.toDoubleOrNull()?:0.0)>0) finance.saveGoal(GoalEntity(UUID.randomUUID().toString(),title.trim(),((goalTarget.toDoubleOrNull()?:0.0)*100).toLong(),createdAt=System.currentTimeMillis()))
                            "budget" -> if(budgetCategory.isNotBlank() && paise>0) finance.saveBudget(BudgetEntity(UUID.randomUUID().toString(),budgetCategory.trim(),paise,monthKey))
                            "payment" -> if(paise>0) finance.addDebtPayment(selectedId,paise)
                            "saving" -> if(paise>0) finance.addGoalSaving(selectedId,paise)
                            else -> if(title.isNotBlank() && paise>0) txDao.upsert(TransactionEntity(UUID.randomUUID().toString(),title.trim(),category.trim().ifBlank{"General"},paise,editingType,System.currentTimeMillis()))
                        }
                        title=""; amount=""; person=""; note=""; goalTarget=""; budgetCategory="General"; dialog=""
                    }
                }) { Text(label("Save", "सेव करें"), color=Blue) } }, dismissButton = { TextButton(onClick={dialog=""}) { Text(label("Cancel", "रद्द करें"), color=Muted) } })
        }
    }
}

@Composable private fun HomeScreen(modifier: Modifier, income: Long, expenses: Long, tx: List<TransactionEntity>, debts: List<DebtEntity>, budgets: List<BudgetEntity>, goals: List<GoalEntity>, label: (String,String)->String, navigate:(Int)->Unit) {
    val due = debts.filter { it.direction=="GIVEN" }.sumOf { (it.totalPaise-it.settledPaise).coerceAtLeast(0) }
    val owe = debts.filter { it.direction=="TAKEN" }.sumOf { (it.totalPaise-it.settledPaise).coerceAtLeast(0) }
    LazyColumn(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101C35),Ink,Color(0xFF090D19)))), contentPadding=PaddingValues(18.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(label("Good day 👋", "नमस्ते 👋"), color=Muted); Text(label("Your financial snapshot", "आपका पैसों का हिसाब"), style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold, color=Color.White) }
        item { Surface(Modifier.fillMaxWidth().shadow(18.dp,RoundedCornerShape(28.dp)), shape=RoundedCornerShape(28.dp), color=Color.Transparent) { Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF17466B),Color(0xFF25244F),Color(0xFF17233B)))).padding(22.dp)) { Text(label("TOTAL BALANCE", "कुल बैलेंस"), color=Color(0xFFB8D9F5), style=MaterialTheme.typography.labelMedium); Text(money(income-expenses), color=Color.White, style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Black); Spacer(Modifier.height(18.dp)); Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Metric(label("Income", "आमदनी"),income,Green,Modifier.weight(1f)); Metric(label("Expense", "खर्च"),expenses,Red,Modifier.weight(1f)) } } } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Metric(label("To receive", "लेने हैं"),due,Blue,Modifier.weight(1f)); Metric(label("To pay", "देने हैं"),owe,Violet,Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Text(label("Quick overview", "त्वरित जानकारी"), color=Color.White, fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleLarge); Text(label("View all", "सभी देखें"), color=Blue) } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { QuickTile("Udhari", debts.size.toString(), Icons.Default.People, Modifier.weight(1f)){navigate(2)}; QuickTile("Budgets", budgets.size.toString(), Icons.Default.PieChart, Modifier.weight(1f)){navigate(3)}; QuickTile("Goals", goals.size.toString(), Icons.Default.Savings, Modifier.weight(1f)){navigate(4)} } }
        item { Text(label("Recent activity", "हाल की गतिविधियां"), color=Color.White, fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleLarge) }
        if(tx.isEmpty()) item { EmptyCard(label("No entries yet. Tap + to start.", "अभी कोई एंट्री नहीं है। + दबाकर शुरू करें।")) }
        items(tx.take(5),key={it.id}) { TransactionRow(it) }
    }
}

@Composable private fun TransactionScreen(modifier: Modifier, tx: List<TransactionEntity>, label:(String,String)->String, delete:(String)->Unit) {
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { item { Text(label("All transactions", "सभी लेन-देन"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text("${tx.size} ${label("records", "रिकॉर्ड")}",color=Muted) }; if(tx.isEmpty()) item{EmptyCard(label("Your transactions will appear here.","आपके लेन-देन यहां दिखेंगे।"))}; items(tx,key={it.id}) { item -> Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)){TransactionRow(item)}; IconButton(onClick={delete(item.id)}) { Icon(Icons.Default.DeleteOutline,null,tint=Muted) } } } }
}

@Composable private fun DebtScreen(modifier: Modifier, debts: List<DebtEntity>, label:(String,String)->String, pay:(DebtEntity)->Unit, delete:(String)->Unit) {
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { item { Text(label("Udhari ledger", "उधारी खाता"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(label("Track who owes you and whom you owe", "किससे लेने हैं और किसे देने हैं"),color=Muted) }; if(debts.isEmpty()) item{EmptyCard(label("No udhari added yet. Tap + to add a person.","अभी उधारी नहीं जोड़ी। + दबाकर व्यक्ति जोड़ें।"))}; items(debts,key={it.id}) { d -> val remaining=(d.totalPaise-d.settledPaise).coerceAtLeast(0); Surface(color=Panel,shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(d.person,color=Color.White,fontWeight=FontWeight.Bold); Text(if(d.direction=="GIVEN") label("They owe you", "आपको देने हैं") else label("You owe them", "आपको देने हैं"),color=Muted,style=MaterialTheme.typography.labelSmall) }; Text(money(remaining),color=if(d.direction=="GIVEN") Green else Red,fontWeight=FontWeight.Bold) }; Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress={if(d.totalPaise==0L)0f else d.settledPaise.toFloat()/d.totalPaise},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=Green,trackColor=Panel2); Text("${label("Settled", "चुकाया")}: ${money(d.settledPaise)} / ${money(d.totalPaise)}",color=Muted,style=MaterialTheme.typography.labelSmall); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { TextButton(onClick={delete(d.id)}) { Text(label("Delete", "हटाएं"),color=Red) }; TextButton(onClick={pay(d)}) { Text(label("Add payment", "भुगतान जोड़ें"),color=Blue) } } } } } }
}

@Composable private fun ReportScreen(modifier: Modifier, income: Long, expenses: Long, tx: List<TransactionEntity>, debts: List<DebtEntity>, label:(String,String)->String) {
    val savings=income-expenses; val ratio=if(income<=0)0f else (expenses.toFloat()/income).coerceIn(0f,1f)
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Text(label("Reports & insights", "रिपोर्ट और जानकारी"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(label("All-time overview", "अब तक का सारांश"),color=Muted) }; item { Metric("Income",income,Green,Modifier.fillMaxWidth()); }; item { Metric("Expenses",expenses,Red,Modifier.fillMaxWidth()) }; item { Metric(label("Net savings", "कुल बचत"),savings,Blue,Modifier.fillMaxWidth()) }; item { Surface(color=Panel,shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text(label("Expense ratio", "खर्च अनुपात"),color=Color.White,fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress={ratio},modifier=Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),color=Violet,trackColor=Panel2); Text("${(ratio*100).toInt()}% ${label("of recorded income spent", "दर्ज आमदनी खर्च हुई")}",color=Muted) } } }; item { Text("${tx.size} ${label("transactions", "लेन-देन")}  •  ${debts.size} ${label("udhari records", "उधारी रिकॉर्ड")}",color=Muted) } }
}

@Composable private fun GoalScreen(modifier: Modifier, goals: List<GoalEntity>, label:(String,String)->String, add:(GoalEntity)->Unit, delete:(String)->Unit) {
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { item { Text(label("Savings goals", "बचत लक्ष्य"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(label("Small steps, bigger milestones", "छोटी बचत, बड़े लक्ष्य"),color=Muted) }; if(goals.isEmpty()) item{EmptyCard(label("Create your first savings goal with +.","+ दबाकर अपना पहला बचत लक्ष्य बनाएं।"))}; items(goals,key={it.id}) { g -> val progress=if(g.targetPaise==0L)0f else (g.savedPaise.toFloat()/g.targetPaise).coerceIn(0f,1f); Surface(color=Panel,shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Text(g.title,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); Text("${(progress*100).toInt()}%",color=Green,fontWeight=FontWeight.Bold) }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=Green,trackColor=Panel2); Text("${money(g.savedPaise)} / ${money(g.targetPaise)}",color=Muted); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { TextButton(onClick={delete(g.id)}){Text(label("Delete","हटाएं"),color=Red)}; TextButton(onClick={add(g)}){Text(label("Add savings","बचत जोड़ें"),color=Blue)} } } } } }
}

@Composable private fun BudgetScreen(modifier: Modifier, budgets: List<BudgetEntity>, tx: List<TransactionEntity>, label:(String,String)->String) {
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(label("Monthly budgets", "मासिक बजट"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(label("Track spending against your limits", "अपनी खर्च सीमा पर नज़र रखें"),color=Muted) }
        if(budgets.isEmpty()) item { EmptyCard(label("No budgets yet. Tap + to set a monthly limit.","अभी बजट नहीं है। + दबाकर सीमा तय करें।")) }
        items(budgets,key={it.id}) { b ->
            val spent=tx.filter { it.type=="EXPENSE" && it.category.equals(b.category,true) && SimpleDateFormat("yyyy-MM",Locale.US).format(Date(it.createdAt))==b.monthKey }.sumOf { it.amountPaise }
            val progress=if(b.limitPaise==0L)0f else (spent.toFloat()/b.limitPaise).coerceIn(0f,1f)
            Surface(color=Panel,shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Text(b.category,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); Text("${(progress*100).toInt()}%",color=if(progress>=1f)Red else Blue,fontWeight=FontWeight.Bold) }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=if(progress>=1f)Red else Blue,trackColor=Panel2); Text("${money(spent)} / ${money(b.limitPaise)}",color=Muted); if(progress>=1f) Text(label("Budget exceeded", "बजट सीमा पार"),color=Red,style=MaterialTheme.typography.labelSmall) } }
        }
    }
}

@Composable private fun Metric(title:String,value:Long,color:Color,modifier:Modifier=Modifier) { Surface(modifier,color=Panel,shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Text(title,color=Muted,style=MaterialTheme.typography.labelMedium); Text(money(value),color=color,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge) } } }
@Composable private fun QuickTile(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,click:()->Unit) { Surface(onClick=click, modifier=modifier,color=Panel,shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Icon(icon,null,tint=Blue); Text(value,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge); Text(title,color=Muted,style=MaterialTheme.typography.labelSmall) } } }
@Composable private fun TransactionRow(tx:TransactionEntity) { Surface(color=Panel,shape=RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Surface(color=if(tx.type=="INCOME")Green.copy(alpha=.12f) else Red.copy(alpha=.12f),shape=CircleShape) { Icon(if(tx.type=="INCOME")Icons.Default.TrendingUp else Icons.Default.TrendingDown,null,tint=if(tx.type=="INCOME")Green else Red,modifier=Modifier.padding(10.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(tx.title,color=Color.White,fontWeight=FontWeight.SemiBold); Text("${tx.category} • ${SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(Date(tx.createdAt))}",color=Muted,style=MaterialTheme.typography.labelSmall) }; Text((if(tx.type=="INCOME")"+" else "−")+money(tx.amountPaise),color=if(tx.type=="INCOME")Green else Red,fontWeight=FontWeight.Bold) } } }
@Composable private fun EmptyCard(message:String) { Surface(color=Panel,shape=RoundedCornerShape(20.dp)) { Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) { Text(message,color=Muted) } } }
private fun String.moneyInput(): String { val clean = filter { it.isDigit() || it=='.' }; val first = clean.indexOf('.'); return if(first < 0) clean else clean.take(first+1) + clean.substring(first+1).replace(".", "") }
private fun money(paise:Long):String = NumberFormat.getCurrencyInstance(Locale("en","IN")).format(paise/100.0)
private fun contentResolverForBackup(resolver:android.content.ContentResolver, uri:android.net.Uri, tx:List<TransactionEntity>, debts:List<DebtEntity>, goals:List<GoalEntity>) {
    // A portable CSV backup: records are tagged by type so they can be archived and reviewed in any spreadsheet app.
    val csv=buildString { appendLine("record_type,id,title_or_person,category_or_direction,amount_paise,settled_or_saved_paise,timestamp,note"); tx.forEach { appendLine("TRANSACTION,${it.id},\"${it.title.csv()}\",${it.category.csv()},${it.amountPaise},,${it.createdAt},${it.type}") }; debts.forEach { appendLine("UDHARI,${it.id},\"${it.person.csv()}\",${it.direction},${it.totalPaise},${it.settledPaise},${it.createdAt},${it.note.csv()}") }; goals.forEach { appendLine("GOAL,${it.id},\"${it.title.csv()}\",,${it.targetPaise},${it.savedPaise},${it.createdAt},") } }
    resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
}
private fun String.csv()=replace("\"","\"\"").replace(","," ").replace("\n"," ")
