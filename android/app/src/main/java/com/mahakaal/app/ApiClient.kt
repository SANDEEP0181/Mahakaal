package com.mahakaal.app

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApiClient {
    var baseUrl: String = BuildConfig.MAHAAKAAL_API_URL.trimEnd('/')
    var token: String? = null

    private suspend fun call(path: String, method: String = "GET", body: String? = null): String =
        withContext(Dispatchers.IO) {
            val c = URL(baseUrl + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                OutputStreamWriter(c.outputStream).use { it.write(body) }
            }
            val response = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val text = response?.bufferedReader()?.use { it.readText() } ?: ""
            if (c.responseCode !in 200..299) throw Exception(text)
            text
        }

    suspend fun login(username: String, password: String) =
        call("/api/v1/auth/login", "POST", """{"username":"${esc(username)}","password":"${esc(password)}"}""")

    suspend fun register(username: String, password: String, referralCode: String = "") =
        call("/api/v1/auth/register", "POST", """{"username":"${esc(username)}","password":"${esc(password)}","referralCode":"${esc(referralCode)}"}""")

    suspend fun me() = call("/api/v1/me")
    suspend fun status() = call("/api/v1/mining/status")
    suspend fun start() = call("/api/v1/mining/start", "POST", "{}")
    suspend fun claim() = call("/api/v1/mining/claim", "POST", "{}")
    suspend fun rewards() = call("/api/v1/rewards")
    suspend fun wallet() = call("/api/v1/wallet")
    suspend fun saveWallet(address: String, network: String) =
        call("/api/v1/wallet", "POST", """{"address":"${esc(address)}","network":"${esc(network)}"}""")
    suspend fun referral() = call("/api/v1/referral")
    suspend fun applyReferral(code: String) =
        call("/api/v1/referral/apply", "POST", """{"referralCode":"${esc(code)}"}""")
    suspend fun withdrawals() = call("/api/v1/withdrawals")
    suspend fun adminReconciliation() = call("/api/v1/admin/system/reconciliation")
    suspend fun adminPayoutPause(paused: Boolean) = call("/api/v1/admin/system/payout-pause", "POST", """{"paused":$paused}""")
    suspend fun withdraw(amount: String, address: String) =
        call("/api/v1/withdrawals", "POST", """{"amount":$amount,"address":"${esc(address)}"}""")

    suspend fun adminUsers() = call("/api/v1/admin/users")
    suspend fun adminConfig() = call("/api/v1/admin/config")
    suspend fun adminWithdrawals() = call("/api/v1/admin/withdrawals")
    suspend fun adminAudit() = call("/api/v1/admin/audit")
    suspend fun adminSetConfig(key: String, value: String) = call("/api/v1/admin/config", "POST", """{"key":"${esc(key)}","value":"${esc(value)}"}""")
    suspend fun adminFreeze(id: String) = call("/api/v1/admin/users/" + id + "/freeze", "POST", "{}")
    suspend fun adminUnfreeze(id: String) = call("/api/v1/admin/users/" + id + "/unfreeze", "POST", "{}")
    suspend fun adminApproveWithdrawal(id: String) = call("/api/v1/admin/withdrawals/" + id + "/approve", "POST", "{}")
    suspend fun adminRejectWithdrawal(id: String) = call("/api/v1/admin/withdrawals/" + id + "/reject", "POST", "{}")

    suspend fun blockchainConfig() = call("/api/v1/blockchain/config")
    suspend fun adminBlockchainTransactions() = call("/api/v1/admin/blockchain-transactions")
    suspend fun adminConfirmBlockchainTransaction(id: String) = call("/api/v1/admin/blockchain-transactions/" + id + "/confirm", "POST", "{}")

    private fun esc(value: String) =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
