package com.mahakaal.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MahakaalApp(applicationContext) }
    }
}

@Composable
fun MahakaalApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("mahakaal", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(prefs.getString("token", null)) }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var role by remember { mutableStateOf("user") }
    var password by remember { mutableStateOf("") }
    var referralInput by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0") }
    var available by remember { mutableStateOf("0") }
    var mining by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("1") }
    var wallet by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("KAAL") }
    var referralCode by remember { mutableStateOf("") }
    var referralCount by remember { mutableStateOf("0") }
    var withdrawalAmount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var registerMode by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("home") }

    fun saveLogin(newToken: String, newUsername: String) {
        token = newToken
        username = newUsername
        role = "user"
        prefs.edit().putString("token", newToken).putString("username", newUsername).apply()
        ApiClient.token = newToken
    }

    fun logout() {
        token = null
        ApiClient.token = null
        prefs.edit().remove("token").apply()
        tab = "home"
        message = "Logged out"
    }

    suspend fun refreshAll() {
        ApiClient.token = token
        val s = JSONObject(ApiClient.status())
        balance = s.optString("balance", "0")
        available = s.optString("available", balance)
        mining = s.optBoolean("mining")
        startedAt = s.optString("startedAt", "")
        rate = s.optString("ratePerHour", "1")
        role = JSONObject(ApiClient.me()).optString("role", role)
        val w = ApiClient.wallet()
        if (!w.isNullOrBlank() && w != "null") {
            val j = JSONObject(w)
            wallet = j.optString("address", wallet)
            network = j.optString("network", network)
        }
        val r = JSONObject(ApiClient.referral())
        referralCode = r.optString("referralCode", "")
        referralCount = r.optString("referrals", "0")
    }

    LaunchedEffect(token) {
        if (!token.isNullOrBlank()) {
            ApiClient.token = token
            try { refreshAll() } catch (e: Exception) { message = "Session refresh failed" }
        }
    }

    LaunchedEffect(mining, token) {
        if (mining && !token.isNullOrBlank()) {
            while (true) {
                delay(15000)
                try { refreshAll() } catch (_: Exception) {}
            }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (token.isNullOrBlank()) {
                AuthScreen(
                    registerMode, username, password, referralInput, busy, message,
                    onUser={username=it}, onPass={password=it}, onReferral={referralInput=it},
                    onToggle={registerMode=!registerMode; message=""},
                    onSubmit={
                        if (username.isBlank() || password.length < 8) {
                            message = "Username and password (8+ chars) required"
                        } else {
                            scope.launch {
                                busy = true
                                try {
                                    val raw = if (registerMode)
                                        ApiClient.register(username, password, referralInput)
                                    else ApiClient.login(username, password)
                                    val j = JSONObject(raw)
                                    saveLogin(j.getString("token"), j.getJSONObject("user").optString("username", username))
                                    password = ""
                                    referralInput = ""
                                    message = "Welcome to Mahakaal"
                                } catch (e: Exception) {
                                    message = parseError(e)
                                } finally { busy = false }
                            }
                        }
                    }
                )
            } else {
                Dashboard(
                    tab, balance, available, mining, startedAt, rate, wallet, network,
                    referralCode, referralCount, referralInput, withdrawalAmount, message, busy, role,
                    onTab={tab=it}, onWallet={wallet=it}, onNetwork={network=it},
                    onReferral={referralInput=it}, onWithdrawal={withdrawalAmount=it},
                    onRefresh={
                        scope.launch { busy=true; try { refreshAll(); message="Updated" } catch(e:Exception){message=parseError(e)} finally{busy=false} }
                    },
                    onMining={
                        scope.launch {
                            busy=true
                            try {
                                if (mining) {
                                    val j=JSONObject(ApiClient.claim())
                                    message="Claimed " + j.optString("claimed","0") + " KAAL"
                                } else {
                                    ApiClient.start()
                                    message="Mining started"
                                }
                                refreshAll()
                            } catch(e:Exception){message=parseError(e)} finally{busy=false}
                        }
                    },
                    onSaveWallet={
                        scope.launch { busy=true; try { ApiClient.saveWallet(wallet,network); message="Wallet saved"; refreshAll() } catch(e:Exception){message=parseError(e)} finally{busy=false} }
                    },
                    onApplyReferral={
                        scope.launch { busy=true; try { ApiClient.applyReferral(referralInput); message="Referral applied"; refreshAll() } catch(e:Exception){message=parseError(e)} finally{busy=false} }
                    },
                    onWithdraw={
                        scope.launch { busy=true; try { ApiClient.withdraw(withdrawalAmount,wallet); message="Withdrawal request submitted"; withdrawalAmount=""; refreshAll() } catch(e:Exception){message=parseError(e)} finally{busy=false} }
                    },
                    onLogout={logout()}
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    register: Boolean, username: String, password: String, referral: String, busy: Boolean, message: String,
    onUser:(String)->Unit, onPass:(String)->Unit, onReferral:(String)->Unit,
    onToggle:()->Unit, onSubmit:()->Unit
) {
    Column(
        Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Mahakaal", style=MaterialTheme.typography.displaySmall)
        Text("KAAL Web3 Rewards", style=MaterialTheme.typography.titleMedium)
        Text(if(register) "Create account" else "Sign in", style=MaterialTheme.typography.headlineSmall)
        OutlinedTextField(username,onUser,label={Text("Username")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,onPass,label={Text("Password")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        if(register) OutlinedTextField(referral,onReferral,label={Text("Referral code (optional)")},modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy,onClick=onSubmit,modifier=Modifier.fillMaxWidth()){Text(if(register)"Create Account" else "Login")}
        TextButton(onClick=onToggle){Text(if(register)"Already have an account? Login" else "New here? Create account")}
        if(message.isNotBlank()) Text(message)
    }
}

@Composable
fun Dashboard(
    tab:String,balance:String,available:String,mining:Boolean,startedAt:String,rate:String,wallet:String,network:String,
    referralCode:String,referralCount:String,referralInput:String,withdrawalAmount:String,message:String,busy:Boolean,role:String,
    onTab:(String)->Unit,onWallet:(String)->Unit,onNetwork:(String)->Unit,onReferral:(String)->Unit,onWithdrawal:(String)->Unit,
    onRefresh:()->Unit,onMining:()->Unit,onSaveWallet:()->Unit,onApplyReferral:()->Unit,onWithdraw:()->Unit,onLogout:()->Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Column { Text("MAHAKAAL",style=MaterialTheme.typography.headlineMedium); Text("KAAL Network") }
            Row {
                if (role == "admin") TextButton(onClick={
                    context.startActivity(android.content.Intent(context, AdminActivity::class.java))
                }) { Text("Admin") }
                TextButton(onClick=onLogout){Text("Logout")}
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("KAAL Balance")
                Text(balance+" KAAL",style=MaterialTheme.typography.displaySmall)
                Text("Available: "+available+" KAAL")
                Text(if(mining)"Mining active" else "Mining inactive")
                Text("Rate: "+rate+" KAAL/hour")
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=!busy,onClick=onMining,modifier=Modifier.weight(1f)){Text(if(mining)"Claim KAAL" else "Start Mining")}
            OutlinedButton(enabled=!busy,onClick=onRefresh,modifier=Modifier.weight(1f)){Text("Refresh")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("home","wallet","referral","withdraw").forEach { key ->
                TextButton(onClick={onTab(key)},modifier=Modifier.weight(1f)){Text(key.replaceFirstChar{it.uppercase()})}
            }
        }
        when(tab) {
            "wallet" -> {
                Text("Wallet",style=MaterialTheme.typography.headlineSmall)
                OutlinedTextField(wallet,onWallet,label={Text("KAAL wallet address")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(network,onNetwork,label={Text("Network")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick=onSaveWallet,modifier=Modifier.fillMaxWidth()){Text("Save Wallet")}
            }
            "referral" -> {
                Text("Referral",style=MaterialTheme.typography.headlineSmall)
                Text("Your code: "+referralCode)
                Text("Referrals: "+referralCount)
                OutlinedTextField(referralInput,onReferral,label={Text("Apply referral code")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick=onApplyReferral,modifier=Modifier.fillMaxWidth()){Text("Apply Code")}
            }
            "withdraw" -> {
                Text("Withdraw KAAL",style=MaterialTheme.typography.headlineSmall)
                OutlinedTextField(withdrawalAmount,onWithdrawal,label={Text("Amount")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy && wallet.isNotBlank(),onClick=onWithdraw,modifier=Modifier.fillMaxWidth()){Text("Request Withdrawal")}
                Text("Withdrawals are reviewed by the server admin. Blockchain payout is not enabled yet.")
            }
            else -> {
                Text("Mining session: "+if(startedAt.isBlank())"Not running" else startedAt)
                Text("Keep the app connected to the server to refresh mining status.")
                Text("Phase 5 dashboard is connected to the Mahakaal API.")
            }
        }
        if(message.isNotBlank()) Text(message)
    }
}

fun parseError(e: Exception): String {
    val raw = e.message ?: "Request failed"
    return try {
        JSONObject(raw).optString("error", "Request failed")
    } catch (_: Exception) { "Request failed" }
}
