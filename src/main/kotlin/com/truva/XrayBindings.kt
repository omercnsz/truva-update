@file:Suppress("UNRESOLVED_REFERENCE")
package com.truva

import android.util.Log

/**
 * Gomobile'ın ürettiği xray.Xray Java sınıfını saran köprü katmanı.
 *
 * gomobile bind, Go fonksiyonlarını şu şekilde çevirir:
 *   Go:  func Init(config string) error    →  Java:  void xray.Xray.init(String) throws Exception
 *   Go:  func SetTunFD(fd uintptr) error   →  Java:  void xray.Xray.setTunFD(long) throws Exception
 *   Go:  func ProcessPacket(pkt []byte) []byte  →  Java:  byte[] xray.Xray.processPacket(byte[])
 *
 * Bu wrapper, mevcut Kotlin kodunun (MyVpnService vb.) beklediği
 * Int-dönüşlü API'yi korur: 0 = başarılı, negatif = hata.
 */
object Xray {

    private const val TAG = "XrayBindings"

    /**
     * Xray motorunu verilen JSON konfigürasyonuyla başlatır.
     * @return 0 başarılı, -1 hata
     */
    fun Init(config: String): Int {
        return try {
            xray.Xray.init(config)
            Log.i(TAG, "Xray motoru başarıyla başlatıldı")
            lastError = null
            0
        } catch (e: Exception) {
            Log.e(TAG, "Xray.Init hatası: ${e.message}", e)
            lastError = e.message ?: "Bilinmeyen Go hatası"
            -1
        }
    }

    /** Son hata mesajı (Go katmanından) */
    var lastError: String? = null
        private set

    /**
     * TUN dosya tanımlayıcısını Xray'in netstack'ine bağlar.
     * @return 0 başarılı, -1 hata
     */
    fun SetTunFD(fd: Int): Int {
        return try {
            xray.Xray.setTunFD(fd.toLong())
            Log.i(TAG, "TUN FD bağlandı: $fd")
            0
        } catch (e: Exception) {
            Log.e(TAG, "Xray.SetTunFD hatası: ${e.message}", e)
            -1
        }
    }

    /**
     * Xray pipeline bağlantı testi (gVisor'dan bağımsız).
     * SOCKS5 → Xray → VLESS → 1.1.1.1:80 HTTP GET yapar.
     * @return "OK:HTTP/1.1 ..." veya "FAIL:adım:hata"
     */
    fun TestConnection(): String {
        return try {
            val result = xray.Xray.testConnection()
            Log.i(TAG, "TestConnection: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "TestConnection hatası: ${e.message}", e)
            "FAIL:exception:${e.message}"
        }
    }

    /**
     * Xray motorunu ve gVisor netstack’i durdurur.
     */
    fun Stop() {
        try {
            xray.Xray.stop()
            Log.i(TAG, "Xray motoru durduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "Xray.Stop hatası: ${e.message}", e)
        }
    }

    /**
     * Eski paket-paket API (artık kullanılmıyor, geriye uyumluluk için).
     */
    fun ProcessPacket(packet: ByteArray): ByteArray {
        return try {
            xray.Xray.processPacket(packet) ?: ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Xray.ProcessPacket hatası: ${e.message}", e)
            ByteArray(0)
        }
    }
}
