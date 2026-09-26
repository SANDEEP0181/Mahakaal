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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AdminWithdrawal(val id:String,val username:String,val amount:String,val address:String,val status:String)
data class AdminTx(val id:String,val amount:String,val status:String,val hash:String)

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminScreen() }
    }
}

@Composable
fun AdminScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Checking admin access…") }
    var users by remember { mutableStateOf(listOf<String>()) }
    var withdrawals by remember { mutableStateOf(listOf<AdminWithdrawal>()) }
    var blockchainTx by remember { mutableStateOf(listOf<AdminTx>()) }
    var rate by remember { mutableStateOf("1") }
    var maxHours by remember { mutableStateOf("24") }
    var busy by remember { mutableStateOf(false) }
    var payoutPaused by remember { mutableStateOf(false) }
    var reconciliation by remember { mutableStateOf("Not loaded") }

    fun load() {
        scope.launch {
            busy = true
            try {
                ApiClient.token = SecurePrefs.getToken(context)
                val me = JSONObject(ApiClient.me())
                allowed = me.optString("role") == "admin"
                if (!allowed) { message = "Admin access required"; return@launch }
                val u = JSONObject(ApiClient.adminUsers()).optJSONArray("items") ?: JSONArray()
                users = (0 until u.length()).map { i ->
                    val q=u.getJSONObject(i)
                    q.optString("username")+" • "+q.optString("status")+" • "+q.optString("role")
                }
                val w = JSONObject(ApiClient.adminWithdrawals()).optJSONArray("items") ?: JSONArray()
                withdrawals = (0 until w.length()).map { i ->
                    val q=w.getJSONObject(i)
                    AdminWithdrawal(q.optString("id"),q.optString("username"),q.optString("amount"),q.optString("address"),q.optString("status"))
                }
                val bt = JSONObject(ApiClient.adminBlockchainTransactions()).optJSONArray("items") ?: JSONArray()
                blockchainTx = (0 until bt.length()).map { i ->
                    val q=bt.getJSONObject(i)
                    AdminTx(q.optString("id"),q.optString("amount"),q.optString("status"),q.optString("tx_hash"))
                }
                val c = JSONObject(ApiClient.adminConfig()).optJSONArray("items") ?: JSONArray()
                for (i in 0 until c.length()) {
                    val q=c.getJSONObject(i)
                    if(q.optString("key")=="KAAL_RATE_PER_HOUR") rate=q.optString("value")
                    if(q.optString("key")=="MAX_SESSION_HOURS") maxHours=q.optString("value")
                    if(q.optString("key")=="PAYOUT_PAUSED") payoutPaused=q.optString("value")=="true"
                }
                val rec=JSONObject(ApiClient.adminReconciliation())
                reconciliation="Ledger: "+rec.optString("ledgerBalance","0")+" KAAL • Provider: "+rec.optString("blockchainProvider","disabled")+" • Payouts paused: "+rec.optBoolean("payoutPaused")
                message="Admin dashboard ready"
            } catch (_: Exception) { message="Admin request failed" }
            finally { busy=false }
        }
    }

    fun action(block:suspend()->String, success:String) {
        scope.launch {
            busy=true
            try { block(); message=success; load() }
            catch(e:Exception){ message=parseError(e) }
            finally { busy=false }
        }
    }

    LaunchedEffect(Unit) { load() }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Mahakaal Admin",style=MaterialTheme.typography.headlineMedium)
            Text(message)
            if (allowed) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Emergency payout control",style=MaterialTheme.typography.titleMedium)
                        Text(if(payoutPaused)"Payouts PAUSED" else "Payouts ACTIVE")
                        Button(enabled=!busy,onClick={
                            action({ApiClient.adminPayoutPause(!payoutPaused)},"Payout control updated")
                        },modifier=Modifier.fillMaxWidth()) { Text(if(payoutPaused)"Resume Payouts" else "Pause Payouts") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Reconciliation: "+reconciliation)
                OutlinedTextField(rate,{rate=it},label={Text("KAAL/hour")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(maxHours,{maxHours=it},label={Text("Max session hours")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick={
                    action({ApiClient.adminSetConfig("KAAL_RATE_PER_HOUR",rate);ApiClient.adminSetConfig("MAX_SESSION_HOURS",maxHours)},"Configuration saved")
                },modifier=Modifier.fillMaxWidth()){Text("Save Mining Config")}
                OutlinedButton(enabled=!busy,onClick={ { load() } },modifier=Modifier.fillMaxWidth()){Text("Refresh Admin Data")}
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item { Text("Users",style=MaterialTheme.typography.titleLarge) }
                    items(users){ Text(it) }
                    item { Text("Withdrawals",style=MaterialTheme.typography.titleLarge) }
                    items(withdrawals,key={it.id}) { w ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(w.username+" • "+w.amount+" KAAL")
                                Text(w.status+" • "+w.address)
                                if(w.status=="pending") {
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Button(enabled=!busy,onClick={action({ApiClient.adminApproveWithdrawal(w.id)},"Withdrawal approved and queued")},modifier=Modifier.weight(1f)){Text("Approve")}
                                        OutlinedButton(enabled=!busy,onClick={action({ApiClient.adminRejectWithdrawal(w.id)},"Withdrawal rejected")},modifier=Modifier.weight(1f)){Text("Reject")}
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Blockchain Transactions",style=MaterialTheme.typography.titleLarge) }
                    items(blockchainTx,key={it.id}) { tx ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(tx.amount+" KAAL • "+tx.status)
                                if(tx.hash.isNotBlank()) Text("TX: "+tx.hash)
                                if(tx.status=="submitted") {
                                    OutlinedButton(enabled=!busy,onClick={
                                        action({ApiClient.adminConfirmBlockchainTransaction(tx.id)},"Test settlement confirmed")
                                    },modifier=Modifier.fillMaxWidth()){Text("Confirm Test Settlement")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
