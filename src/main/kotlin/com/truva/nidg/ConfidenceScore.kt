package com.truva.nidg

/**
 * Confidence Score (CS) Algoritması
 *
 * Her NER raporuna ek açıklayıcı güven skoru eşlik eder.
 * CS = S_weighted × (1 − P_deficiency)
 *
 * Eksiklik Parametreleri:
 *   - Traceroute Bloklu:     +0.20
 *   - CIDR Listesi Bayat:    +0.20
 *   - Sinyal Verisi Yetersiz: +0.10
 *   - Bypass Socket Hatası:  +0.10
 *   - Kalibrasyon Yapılmamış: +0.15
 */
class ConfidenceScore {

    companion object {
        private const val P_TRACEROUTE_BLOCKED = 0.20
        private const val P_CIDR_STALE = 0.20
        private const val P_SIGNAL_INSUFFICIENT = 0.10
        private const val P_BYPASS_ERROR = 0.10
        private const val P_NO_CALIBRATION = 0.15
    }

    data class ScoreBreakdown(
        val score: Double,              // 0-100 arası nihai güven skoru
        val rawScore: Double,           // Ceza öncesi ham skor
        val totalDeficiency: Double,    // Toplam eksiklik cezası (0-1)
        val flags: List<DeficiencyFlag>,
        val explanation: String         // Kullanıcıya gösterilecek açıklama
    )

    data class DeficiencyFlag(
        val name: String,
        val penalty: Double,
        val description: String
    )

    /**
     * Confidence Score hesaplar.
     *
     * @param nerPercent NER verimlilik skoru
     * @param isTracerouteAvailable Traceroute çalıştırılabildi mi?
     * @param isCidrFresh CIDR listesi güncel mi?
     * @param signalSampleCount Sinyal örneği sayısı
     * @param isBypassAvailable Bypass socket çalışıyor mu?
     * @param isCalibrated Kalibrasyon yapılmış mı?
     * @param quicWeight QUIC verisinin NER'e katkı ağırlığı (0-1)
     */
    fun calculate(
        nerPercent: Double,
        isTracerouteAvailable: Boolean = false,
        isCidrFresh: Boolean = true,
        signalSampleCount: Int = 0,
        isBypassAvailable: Boolean = false,
        isCalibrated: Boolean = false,
        quicWeight: Double = 0.0
    ): ScoreBreakdown {
        val flags = mutableListOf<DeficiencyFlag>()
        var totalP = 0.0

        // Eksiklik cezalarını hesapla
        if (!isTracerouteAvailable) {
            totalP += P_TRACEROUTE_BLOCKED
            flags.add(DeficiencyFlag(
                "Traceroute Bloklu", P_TRACEROUTE_BLOCKED,
                "Yol analizi yapılamıyor, sadece L4 verisi mevcut"
            ))
        }

        if (!isCidrFresh) {
            totalP += P_CIDR_STALE
            flags.add(DeficiencyFlag(
                "CIDR Listesi Bayat", P_CIDR_STALE,
                "CDN/ABR filtresi yanlış pozitif üretebilir"
            ))
        }

        if (signalSampleCount < 5) {
            totalP += P_SIGNAL_INSUFFICIENT
            flags.add(DeficiencyFlag(
                "Sinyal Verisi Yetersiz", P_SIGNAL_INSUFFICIENT,
                "Zaman serisi korelasyonu zayıf (< 5 örnek)"
            ))
        }

        if (!isBypassAvailable) {
            totalP += P_BYPASS_ERROR
            flags.add(DeficiencyFlag(
                "Bypass Socket Hatası", P_BYPASS_ERROR,
                "Operatör segment ölçümü yapılamıyor"
            ))
        }

        if (!isCalibrated) {
            totalP += P_NO_CALIBRATION
            flags.add(DeficiencyFlag(
                "Kalibrasyon Yapılmamış", P_NO_CALIBRATION,
                "K katsayısı hesaplanamadı, operatör karşılaştırması yok"
            ))
        }

        // Toplam ceza 0.75'i geçemez (minimum %25 güven)
        totalP = totalP.coerceAtMost(0.75)

        // S_weighted: NER skoru (0-100 arası)
        val sWeighted = nerPercent

        // CS = S_weighted × (1 − P_deficiency)
        val cs = sWeighted * (1.0 - totalP)

        // Açıklama oluştur
        val explanation = buildExplanation(cs, flags)

        return ScoreBreakdown(
            score = cs.coerceIn(0.0, 100.0),
            rawScore = sWeighted,
            totalDeficiency = totalP,
            flags = flags,
            explanation = explanation
        )
    }

    private fun buildExplanation(score: Double, flags: List<DeficiencyFlag>): String {
        val sb = StringBuilder()
        when {
            score >= 90 -> sb.append("Analiz güvenilirliği yüksek.")
            score >= 70 -> sb.append("Analiz güvenilirliği iyi.")
            score >= 50 -> sb.append("Analiz güvenilirliği orta.")
            else -> sb.append("Analiz güvenilirliği düşük.")
        }

        if (flags.isNotEmpty()) {
            sb.append(" Kısıtlamalar: ")
            sb.append(flags.joinToString("; ") { it.description })
        }

        return sb.toString()
    }
}
