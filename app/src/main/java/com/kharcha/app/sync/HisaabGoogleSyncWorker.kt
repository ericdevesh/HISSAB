package com.kharcha.app.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.Scopes
import com.kharcha.app.data.KharchaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HisaabGoogleSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
                ?: return@withContext Result.failure()
            val accountName = account.email ?: return@withContext Result.failure()
            val scopes = "oauth2:https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file"
            val token = GoogleAuthUtil.getToken(applicationContext, account.account!!, scopes)
            val prefs = applicationContext.getSharedPreferences("hisaab_google_sync", Context.MODE_PRIVATE)
            var spreadsheetId = prefs.getString("spreadsheet_id_$accountName", null)

            if (spreadsheetId.isNullOrBlank()) {
                spreadsheetId = findOrCreateSpreadsheet(token)
                prefs.edit().putString("spreadsheet_id_$accountName", spreadsheetId).apply()
            }

            val db = KharchaDatabase.getInstance(applicationContext)
            val transactions = db.transactionDao().getAllForSync()
            val debts = db.financeDao().getAllDebtsForSync()
            val budgets = db.financeDao().getAllBudgetsForSync()
            val goals = db.financeDao().getAllGoalsForSync()

            writeTab(token, spreadsheetId, "Transactions", rows(
                listOf("id", "title", "category", "amount_paise", "type", "created_at"),
                transactions.map { listOf(it.id, it.title, it.category, it.amountPaise, it.type, it.createdAt) }
            ))
            writeTab(token, spreadsheetId, "Udhari", rows(
                listOf("id", "person", "phone", "note", "direction", "total_paise", "settled_paise", "created_at", "due_at"),
                debts.map { listOf(it.id, it.person, it.phone, it.note, it.direction, it.totalPaise, it.settledPaise, it.createdAt, it.dueAt ?: "") }
            ))
            writeTab(token, spreadsheetId, "Budgets", rows(
                listOf("id", "category", "limit_paise", "month"),
                budgets.map { listOf(it.id, it.category, it.limitPaise, it.monthKey) }
            ))
            writeTab(token, spreadsheetId, "Savings Goals", rows(
                listOf("id", "title", "target_paise", "saved_paise", "created_at"),
                goals.map { listOf(it.id, it.title, it.targetPaise, it.savedPaise, it.createdAt) }
            ))
            prefs.edit().putLong("last_sync_at", System.currentTimeMillis()).apply()
            Result.success()
        } catch (_: UserRecoverableAuthException) {
            Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun findOrCreateSpreadsheet(token: String): String {
        val body = JSONObject().put("properties", JSONObject().put("title", "HISAAB Backup"))
            .put("sheets", JSONArray().put(sheet("Transactions")).put(sheet("Udhari"))
                .put(sheet("Budgets")).put(sheet("Savings Goals")))
        val response = request(
            "https://sheets.googleapis.com/v4/spreadsheets",
            "POST", token, body.toString()
        )
        return JSONObject(response).getString("spreadsheetId")
    }

    private fun sheet(title: String) = JSONObject().put("properties", JSONObject().put("title", title))

    private fun writeTab(token: String, id: String, tab: String, values: JSONArray) {
        val range = Uri.encode("'$tab'!A1:Z")
        request(
            "https://sheets.googleapis.com/v4/spreadsheets/$id/values/$range?valueInputOption=RAW",
            "PUT", token, JSONObject().put("majorDimension", "ROWS").put("values", values).toString()
        )
    }

    private fun rows(header: List<Any>, data: List<List<Any>>): JSONArray {
        val result = JSONArray()
        result.put(JSONArray(header))
        data.forEach { result.put(JSONArray(it)) }
        return result
    }

    private fun request(url: String, method: String, token: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("Google API error $code: $response")
        return response
    }
}
