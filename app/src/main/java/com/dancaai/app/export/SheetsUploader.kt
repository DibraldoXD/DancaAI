package com.dancaai.app.export

import android.util.Log
import com.dancaai.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envia cada captura de pontos corporais pra um Google Apps Script (ver README) que
 * adiciona a linha numa planilha do Google Sheets. A URL vem de [BuildConfig] (definida
 * em `local.properties`, nunca commitada); sem ela, [upload] é um no-op silencioso.
 *
 * Fire-and-forget de propósito: uma falha de rede durante o treino não deve travar a
 * sessão nem derrubar a captura — ela já está salva localmente de qualquer forma.
 */
object SheetsUploader {

    private const val TAG = "SheetsUploader"
    private const val TIMEOUT_MS = 8_000

    val enabled: Boolean get() = BuildConfig.SHEETS_WEBHOOK_URL.isNotBlank()

    /** [values] vira uma linha na planilha, na ordem em que forem passados. */
    fun upload(scope: CoroutineScope, values: List<Any>) {
        if (!enabled) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("values", JSONArray(values)).toString()
                val connection = URL(BuildConfig.SHEETS_WEBHOOK_URL).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                    val code = connection.responseCode
                    if (code !in 200..299) Log.w(TAG, "Falha ao enviar captura pro Sheets: HTTP $code")
                } finally {
                    connection.disconnect()
                }
            }.onFailure { e -> Log.w(TAG, "Falha ao enviar captura pro Sheets", e) }
        }
    }
}
