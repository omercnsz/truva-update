package com.truva.sandbox.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TruvaAdbClient — Orijinal ADB Binary Motoru Android 11+ TLS eşleştirmesini ve komut yürütmeyi
 * cihazın içine gömülü 'adb' ile yapar.
 */
object TruvaAdbClient {
    private const val TAG = "TruvaAdbBinary"

    var isConnected: Boolean = false
        private set

    /**
     * Android, libadb.so dosyasını nativeLibraryDir dizinine çıkartır. Bu dizin SELinux tarafından
     * 'çalıştırılabilir' (executable) olarak işaretlenmiştir.
     */
    private fun getAdbPath(context: Context): String {
        return "${context.applicationInfo.nativeLibraryDir}/libadb.so"
    }

    /**
     * Bir shell komutunu ProcessBuilder ile çalıştırır.
     */
    fun runAdbCommand(context: Context, vararg args: String): String {
        return try {
            val adbPath = getAdbPath(context)
            val commandList = mutableListOf(adbPath).apply { addAll(args) }

            val pb = ProcessBuilder(commandList)
            // ADB, anahtarlarını yazacak bir HOME ve geçici dosyalar için TMPDIR dizini ister
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = context.filesDir.absolutePath
            pb.environment()["TEMP"] = context.filesDir.absolutePath
            pb.environment()["TMP"] = context.filesDir.absolutePath
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)

            val process = pb.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            Log.d(TAG, "Komut: ${args.joinToString(" ")} -> Çıktı: $output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "Shell komutu çalıştırılamadı", e)
            "error: ${e.message}"
        }
    }

    /**
     * Bağlı cihazları listeler (adb devices)
     */
    fun getConnectedDevices(context: Context): String {
        return runAdbCommand(context, "devices")
    }

    /**
     * Cihazın bağlı olup olmadığını kontrol eder
     */
    fun checkIsConnected(context: Context): Boolean {
        val output = getConnectedDevices(context)
        return output.contains("127.0.0.1") && output.contains("device") && !output.contains("offline")
    }

    /**
     * Android 11+ Kablosuz Eşleştirme (TLS Pairing) Örn komut: ./adb pair 127.0.0.1:41233 123456
     */
    suspend fun pair(context: Context, port: Int, pairingCode: String): Boolean =
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Eşleştirme başlatılıyor... Port: $port")
                val output = runAdbCommand(context, "pair", "127.0.0.1:$port", pairingCode)
                val result = !output.contains("failed", ignoreCase = true) &&
                        !output.contains("error", ignoreCase = true)
                return@withContext result
            }

    /** Android 11+ Kablosuz Eşleştirme ve Bağlanma */
    suspend fun pairAndConnect(
            context: Context,
            pairingPort: Int,
            connectionPort: Int,
            pairingCode: String
    ): Boolean =
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Eşleştirme başlatılıyor... Port: $pairingPort")

                // 1. Önce cihazla anahtar takası yap (Pair)
                val pairOutput =
                        runAdbCommand(context, "pair", "127.0.0.1:$pairingPort", pairingCode)
                val pairResult = !pairOutput.contains("failed", ignoreCase = true) &&
                        !pairOutput.contains("error", ignoreCase = true)

                if (!pairResult) {
                    Log.e(TAG, "Eşleştirme başarısız oldu!")
                    return@withContext false
                }

                Log.i(
                        TAG,
                        "Eşleştirme başarılı. Shell bağlantısı kuruluyor... Port: $connectionPort"
                )

                // 2. Eşleşme başarılıysa komut göndermek için Bağlantı portuna bağlan (Connect)
                connect(context, connectionPort)
            }

    /** Mevcut eşleşmiş cihaza bağlanır */
    suspend fun connect(context: Context, port: Int): Boolean = withContext(Dispatchers.IO) {
        val output = runAdbCommand(context, "connect", "127.0.0.1:$port")
        val result = output.contains("connected to", ignoreCase = true)
        if (result) {
            isConnected = true
            Log.i(TAG, "ADB Bağlantısı TAMAMLANDI!")
        }
        result
    }

    /** Eşleşmiş cihaza kısıtlama komutunu yollar. ./adb shell appops set <pkg> <op> <mode> */
    fun executeAppOpsCommand(
            context: Context,
            packageName: String,
            op: String,
            mode: String
    ): Boolean {
        Log.i(TAG, "AppOps kısıtlaması basılıyor: $packageName")
        val output = runAdbCommand(context, "shell", "appops", "set", packageName, op, mode)
        return !output.contains("error", ignoreCase = true)
    }

    fun disconnect(context: Context) {
        runAdbCommand(context, "disconnect")
        isConnected = false
    }
}
