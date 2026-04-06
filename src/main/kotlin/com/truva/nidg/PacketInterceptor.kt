package com.truva.nidg

import android.system.Os
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import java.io.FileDescriptor
import java.io.FileInputStream
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicLong

/**
 * Packet Interceptor — TUN arayüzünden paket yakalama
 *
 * VPN tünelinin ana görevi (paket iletimi) hiçbir koşulda bloklanmaz.
 * Analiz, ana döngünün dışında bağımsız bir worker üzerinde çalışır.
 *
 * DROP_OLDEST politikası: Analiz kuyruğu taştığında en eski analiz paketi düşer,
 * tünel asla durmaz.
 *
 * Mimari:
 *   TUN FD (dup) → FileInputStream.read() → Channel<ByteArray> → Consumer
 */
class PacketInterceptor {

    companion object {
        private const val TAG = "NidgInterceptor"
        private const val CHANNEL_CAPACITY = 512
        private const val READ_BUFFER_SIZE = 1500  // Standart MTU
    }

    /** Analiz paketi kuyruğu */
    val packetChannel = Channel<ByteArray>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Düşürülen paket sayısı — CPU kapasitesi izleme metriği */
    val droppedPacketCount = AtomicLong(0)

    private var readJob: Job? = null
    private var dupFd: FileDescriptor? = null
    private var inputStream: FileInputStream? = null

    /**
     * TUN fd'yi kopyalayıp okuma döngüsünü başlatır.
     *
     * @param originalFd VPN TUN file descriptor (int)
     * @param scope Coroutine scope (NidgEngine tarafından sağlanır)
     */
    fun start(originalFd: Int, scope: CoroutineScope) {
        stop()

        try {
            // Os.dup() — fd'yi kopyala, Go'nun kullandığı orijinali etkilemez
            val rawDupFd = Os.dup(createFileDescriptor(originalFd))
            dupFd = rawDupFd
            inputStream = FileInputStream(rawDupFd)

            Log.i(TAG, "TUN fd kopyalandı ve okuma başlatıldı (orijinal=$originalFd)")

            readJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(READ_BUFFER_SIZE)
                try {
                    while (isActive) {
                        val bytesRead = try {
                            inputStream?.read(buffer) ?: -1
                        } catch (e: Exception) {
                            if (isActive) {
                                Log.w(TAG, "TUN okuma hatası: ${e.message}")
                            }
                            -1
                        }

                        if (bytesRead <= 0) {
                            if (isActive) delay(10)  // Kısa bekle, tekrar dene
                            continue
                        }

                        // Paketi kopyala ve kanala gönder
                        val packet = buffer.copyOf(bytesRead)
                        val sent = packetChannel.trySend(packet)
                        if (sent.isFailure) {
                            droppedPacketCount.incrementAndGet()
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal kapatma
                } catch (e: Exception) {
                    Log.e(TAG, "Okuma döngüsü hatası: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TUN fd kopyalama hatası: ${e.message}", e)
        }
    }

    /** Okuma döngüsünü ve fd'yi temizle */
    fun stop() {
        readJob?.cancel()
        readJob = null

        try { inputStream?.close() } catch (_: Exception) {}
        inputStream = null

        try {
            dupFd?.let { Os.close(it) }
        } catch (_: Exception) {}
        dupFd = null
    }

    /**
     * Int fd'den FileDescriptor oluşturur.
     * Android'de FileDescriptor.descriptor alanı private olduğu için
     * reflection ile erişiyoruz.
     */
    private fun createFileDescriptor(fd: Int): FileDescriptor {
        val fileDescriptor = FileDescriptor()
        val field: Field = FileDescriptor::class.java.getDeclaredField("descriptor")
        field.isAccessible = true
        field.setInt(fileDescriptor, fd)
        return fileDescriptor
    }
}
