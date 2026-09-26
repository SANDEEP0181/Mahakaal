package com.mahakaal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminScreen() }
    }
}

@Composable
fun AdminScreen() {
    val prefs = androidx.compose.runtime.remember {
        androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("mahakaal", 0)
    }
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Checking admin access…") }
    var users by remember { mutableStateOf(listOf<String>()) }
    var withdrawals by remember { mutableStateOf(listOf<String>()) }
    var blockchainTx by remember { mutableStateOf(listOf<String>()) }
    var rate by remember { mutableStateOf("1") }
    var maxHours by remember { mutableStateOf("24") }
    var busy by remember { mutableStateOf(false) }
    var payoutPaused by remember { mutableStateOf(false) }
    var reconciliation by remember { mutableStateOf("Not loaded") }

    fun load() {
        scope.launch {
            busy = true
            try {
                ApiClient.token = prefs.getString("token", null)
                val me = JSONObject(ApiClient.me())
                allowed = me.optString("role") == "admin"
                if (!allowed) { message = "Admin access required"; return@launch }
                val u = JSONObject(ApiClient.adminUsers()).optJSONArray("items") ?: JSONArray()
                users = (0 until u.length()).map { i ->
                    val x = u.getJSONObject(i)
                    x.optString("username") + " • " + x.optString("status")
                }
                val w = JSONObject(ApiClient.adminWithdrawals()).optJSONArray("items") ?: JSONArray()
                withdrawals = (0 until w.length()).map { i ->
                    val x = w.getJSONObject(i)
                    x.optString("id") + " • " + x.optString("amount") + " KAAL • " + x.optString("status")
                }
                val bt = JSONObject(ApiClient.adminBlockchainTransactions()).optJSONArray("items") ?: JSONArray()
                blockchainTx = (0 until bt.length()).map { i ->
                    val x = bt.getJSONObject(i)
                    x.optString("id") + " • " + x.optString("amount") + " KAAL • " + x.optString("status") + " • " + x.optString("tx_hash")
                }
                val c = JSONObject(ApiClient.adminConfig()).optJSONArray("items") ?: JSONArray()
                for (i in 0 until c.length()) {
                    val x=c.getJSONObject(i)
                    if(x.optString("key")=="KAAL_RATE_PER_HOUR") rate=x.optString("value")
                    if(x.optString("key")=="MAX_SESSION_HOURS") maxHours=x.optString("value")
                }
                val rec=JSONObject(ApiClient.adminReconciliation())
                reconciliation="Ledger: \${rec.optString("ledgerBalance","0")} KAAL • Provider: \${rec.optString("blockchainProvider","disabled")} • Payouts paused: \${rec.optBoolean("payoutPaused")}"
                message = "Admin dashboard ready"
            } catch (e: Exception) {
                message = "Admin request failed"
            } finally { busy=false }
        }
    }

    LaunchedEffect(Unit) { load() }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Mahakaal Admin", style=MaterialTheme.typography.headlineMedium)
            Text(message)
            if (allowed) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Emergency payout control", style=MaterialTheme.typography.titleMedium)
                        Text(if(payoutPaused) "Payouts PAUSED" else "Payouts ACTIVE")
                        Button(enabled=!busy,onClick={
                            scope.launch {
                                busy=true
                                try { ApiClient.adminPayoutPause(!payoutPaused); payoutPaused=!payoutPaused; message=if(payoutPaused)"Payouts paused" else "Payouts resumed" }
                                catch(e:Exception){message="Payout control failed"} finally{busy=false}
                            }
                        },modifier=Modifier.fillMaxWidth()){Text(if(payoutPaused)"Resume Payouts" else "Pause Payouts")}
                    }
                }
                Text("Reconciliation: "+reconciliation)

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(rate,{rate=it},label={Text("KAAL/hour")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(maxHours,{maxHours=it},label={Text("Max session hours")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick={
                    scope.launch {
                        busy=true
                        try {
                            ApiClient.adminSetConfig("KAAL_RATE_PER_HOUR",rate)
                            ApiClient.adminSetConfig("MAX_SESSION_HOURS",maxHours)
                            message="Configuration saved"
                        } catch(e:Exception){message="Config update failed"} finally{busy=false}
                    }
                },modifier=Modifier.fillMaxWidth()){Text("Save Mining Config")}
                OutlinedButton(onClick={load},modifier=Modifier.fillMaxWidth()){Text("Refresh Admin Data")}
                Spacer(Modifier.height(10.dp))
                Text("Users",style=MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f)) {
                    items(users) { Text(it,Modifier.padding(vertical=5.dp)) }
                    item { Spacer(Modifier.height(8.dp)); Text("Withdrawals",style=MaterialTheme.typography.titleLarge) }
                    items(withdrawals) { Text(it,Modifier.padding(vertical=5.dp)) }
                    item { Spacer(Modifier.height(8.dp)); Text("Blockchain Transactions",style=MaterialTheme.typography.titleLarge) }
                    items(blockchainTx) { Text(it,Modifier.padding(vertical=5.dp)) }
                }
            }
        }
    }
}
