package com.truva.nidg

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * NIDG Engine — Tam Orkestratör (Sprint 1 + 2 + 3)
 *
 * Pipeline:
 *   PacketInterceptor → L4HeaderParser →
 *     ├─ RetransmissionAnalyzer (TCP)
 *     ├─ CdnAbrFilter → QuicJitterAnalyzer (UDP/QUIC)
 *     └─ BlackBoxLogger (tüm veriler)
 *   SignalTelemetry ─┤
 *   SplitTraceroute  ─┤
 *   AdaptiveHeartbeat ─┤
 *   CalibrationEngine ─┤
 *   ConfidenceScore   ─┘→ NidgReport
 */
object NidgEngine {

    private const val TAG = "NidgEngine"
    private const val REPORT_UPDATE_INTERVAL_MS = 1000L
    private const val TRACEROUTE_INTERVAL_MS = 60_000L  // 1 dakikada bir traceroute

    // ── Sprint 1 Bileşenler ──
    private val interceptor = PacketInterceptor()
    private val analyzer = RetransmissionAnalyzer()

    // ── Sprint 2 Bileşenler ──
    private var signalTelemetry: SignalTelemetry? = null
    private var splitTraceroute: SplitTraceroute? = null
    private val blackBoxLogger = BlackBoxLogger()
    private val calibrationEngine = CalibrationEngine()
    private var adaptiveHeartbeat: AdaptiveHeartbeat? = null

    // ── Sprint 3 Bileşenler ──
    private val quicJitterAnalyzer = QuicJitterAnalyzer()
    private val cdnAbrFilter = CdnAbrFilter()
    private val confidenceScore = ConfidenceScore()

    // ── CDN Sayaçları ──
    private val _cdnPacketCount = AtomicLong(0)
    private val _nonCdnPacketCount = AtomicLong(0)

    // ── Coroutine Scope ──
    private var engineJob: Job? = null
    private var engineScope: CoroutineScope? = null

    // ── Context ──
    private var appContext: Context? = null

    // ── Durum ──
    private val _report = MutableStateFlow(NidgReport())
    val report: StateFlow<NidgReport> = _report.asStateFlow()

    private var startTimeMs: Long = 0

    /**
     * Context'i ayarlar (Activity.onCreate'de çağrılmalı).
     * Signal ve Traceroute modülleri Context gerektirir.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * NIDG analiz motorunu başlatır — tam pipeline.
     */
    fun start(tunFd: Int) {
        stop()

        Log.i(TAG, "━━━ NIDG Engine başlatılıyor (fd=$tunFd) ━━━")

        // Reset
        analyzer.reset()
        quicJitterAnalyzer.reset()
        blackBoxLogger.reset()
        _cdnPacketCount.set(0)
        _nonCdnPacketCount.set(0)
        startTimeMs = System.currentTimeMillis()

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        engineJob = job
        engineScope = scope

        // ── Sprint 1: Paket yakalama ve analiz ──
        interceptor.start(tunFd, scope)

        // ── Sprint 2: Context-bağımlı bileşenler ──
        appContext?.let { ctx ->
            // Sinyal Telemetri
            signalTelemetry = SignalTelemetry(ctx).also { it.start(scope) }

            // Split Traceroute
            splitTraceroute = SplitTraceroute(ctx).also { it.bindCellularNetwork() }

            // Adaptive Heartbeat
            adaptiveHeartbeat = AdaptiveHeartbeat(ctx).also { it.start() }
        }

        // ── Ana analiz worker ──
        scope.launch {
            Log.i(TAG, "Analiz worker başladı (tam pipeline)")
            try {
                for (rawPacket in interceptor.packetChannel) {
                    // Adaptive Heartbeat kontrolü
                    if (adaptiveHeartbeat?.shouldAnalyze() == false) continue

                    // Parse
                    val parsed = L4HeaderParser.parse(rawPacket) ?: continue

                    // TCP Retransmission Analizi
                    if (parsed.isTcp) {
                        analyzer.analyze(parsed)
                    }

                    // QUIC/UDP Analizi (CDN filtrelemeli)
                    if (parsed.isQuic) {
                        if (cdnAbrFilter.isCdnTraffic(parsed)) {
                            _cdnPacketCount.incrementAndGet()
                        } else {
                            _nonCdnPacketCount.incrementAndGet()
                            quicJitterAnalyzer.analyze(parsed)
                        }
                    } else if (parsed.isUdp) {
                        analyzer.analyze(parsed)
                    }
                }
            } catch (e: CancellationException) {
                // Normal kapatma
            } catch (e: Exception) {
                Log.e(TAG, "Analiz worker hatası: ${e.message}", e)
            }
        }

        // ── Rapor güncelleme döngüsü ──
        scope.launch {
            while (isActive) {
                delay(REPORT_UPDATE_INTERVAL_MS)
                adaptiveHeartbeat?.checkTrafficAndUpdateMode()
                updateFullReport()
            }
        }

        // ── Periyodik Traceroute ──
        scope.launch {
            while (isActive) {
                delay(TRACEROUTE_INTERVAL_MS)
                if (adaptiveHeartbeat?.shouldRunTraceroute() != false) {
                    try {
                        splitTraceroute?.runAnalysis()
                    } catch (e: Exception) {
                        Log.w(TAG, "Traceroute hatası: ${e.message}")
                    }
                }
            }
        }

        _report.value = NidgReport(isActive = true)
        Log.i(TAG, "━━━ NIDG Engine aktif (tam pipeline) ━━━")
    }

    /** Motoru durdurur ve kaynakları serbest bırakır */
    fun stop() {
        if (engineJob == null) return

        Log.i(TAG, "NIDG Engine durduruluyor...")

        interceptor.stop()
        signalTelemetry?.stop()
        adaptiveHeartbeat?.stop()
        engineJob?.cancel()
        engineJob = null
        engineScope = null

        updateFullReport()
        _report.value = _report.value.copy(isActive = false)

        Log.i(TAG, "NIDG Engine durduruldu")
    }

    val isRunning: Boolean get() = engineJob?.isActive == true

    /** Kalibrasyon API'si — UI'dan çağrılır */
    val calibration: CalibrationEngine get() = calibrationEngine

    /** Black Box API'si — UI'dan çağrılır */
    val blackBox: BlackBoxLogger get() = blackBoxLogger

    private fun updateFullReport() {
        val tcpData = analyzer.getReportData()
        val quicData = quicJitterAnalyzer.getReportData()
        val signalCorr = signalTelemetry?.correlation?.value
        val segmentData = splitTraceroute?.segmentAnalysis?.value
        val calibStatus = calibrationEngine.status.value
        val mode = adaptiveHeartbeat?.currentMode?.name ?: "ACTIVE"

        // ── Byte hesaplamaları ──
        val totalBytes = tcpData.totalTcpBytes + tcpData.totalUdpBytes
        val overheadBytes = tcpData.retransmittedBytes

        // ── Hibrit NER (TCP dominanant, QUIC katkılı) ──
        val tcpNer = if (tcpData.totalTcpPackets > 0) {
            (1.0 - tcpData.retransmittedPackets.toDouble() / tcpData.totalTcpPackets) * 100.0
        } else 100.0

        // QUIC katkısı: Jitter index tersini NER'e ekle
        val quicContribution = if (quicData.totalQuicPackets > 10) {
            (1.0 - quicData.jitterIndex) * 100.0
        } else 100.0

        // Ağırlıklı ortalama: TCP %70, QUIC %30 (QUIC varsa)
        val nerPercent = if (quicData.totalQuicPackets > 10) {
            (tcpNer * 0.7 + quicContribution * 0.3)
        } else {
            tcpNer
        }.coerceIn(0.0, 100.0)

        // ── Confidence Score ──
        val cs = confidenceScore.calculate(
            nerPercent = nerPercent,
            isTracerouteAvailable = segmentData?.isAvailable == true,
            isCidrFresh = !cdnAbrFilter.isCidrStale(),
            signalSampleCount = signalCorr?.sampleCount ?: 0,
            isBypassAvailable = segmentData?.bypassRttMs?.let { it > 0 } == true,
            isCalibrated = calibStatus.isCalibrated,
            quicWeight = if (quicData.totalQuicPackets > 10) 0.3 else 0.0
        )

        // ── Black Box snapshot ──
        val retransmissionRate = if (tcpData.totalTcpPackets > 0) {
            tcpData.retransmittedPackets.toDouble() / tcpData.totalTcpPackets
        } else 0.0

        blackBoxLogger.recordSnapshot(
            nerPercent = nerPercent,
            retransmissionRate = retransmissionRate,
            rsrp = signalCorr?.avgRsrp ?: -999,
            rsrq = signalCorr?.avgRsrq ?: -999,
            totalPackets = tcpData.totalTcpPackets + tcpData.totalUdpPackets,
            retransmittedPackets = tcpData.retransmittedPackets,
            droppedPackets = interceptor.droppedPacketCount.get()
        )

        // ── Tam rapor ──
        _report.value = NidgReport(
            // Byte Sayaçları
            totalBytes = totalBytes,
            netUsefulBytes = totalBytes - overheadBytes,
            overheadBytes = overheadBytes,

            // TCP
            totalTcpPackets = tcpData.totalTcpPackets,
            retransmittedPackets = tcpData.retransmittedPackets,
            retransmittedBytes = tcpData.retransmittedBytes,

            // UDP / QUIC
            totalUdpPackets = tcpData.totalUdpPackets,
            totalUdpBytes = tcpData.totalUdpBytes,
            quicPackets = tcpData.quicPackets,
            quicJitterMs = quicData.jitterMs,
            quicAvgIatMs = quicData.avgIatMs,
            quicBurstCount = quicData.burstCount,
            quicGapCount = quicData.gapCount,
            quicConnectionDropRate = quicData.connectionDropRate,
            quicJitterIndex = quicData.jitterIndex,

            // Verimlilik
            nerPercent = nerPercent,

            // Sinyal
            signalRsrp = signalCorr?.avgRsrp ?: -999,
            signalRsrq = signalCorr?.avgRsrq ?: -999,
            signalQuality = signalCorr?.quality?.label ?: "Bilinmiyor",
            signalNetworkType = signalCorr?.currentSample?.networkType ?: "Bilinmiyor",
            signalIsStable = signalCorr?.isStable == true,
            signalSampleCount = signalCorr?.sampleCount ?: 0,

            // Traceroute
            tunnelRttMs = segmentData?.tunnelRttMs ?: 0,
            bypassRttMs = segmentData?.bypassRttMs ?: 0,
            operatorSegmentMs = segmentData?.operatorSegmentMs ?: 0,
            tunnelHops = segmentData?.tunnelHops ?: 0,
            bypassHops = segmentData?.bypassHops ?: 0,
            tracerouteAvailable = segmentData?.isAvailable == true,

            // Confidence Score
            confidenceScore = cs.score,
            confidenceExplanation = cs.explanation,
            confidenceFlags = cs.flags.map { it.name },

            // Kalibrasyon
            isCalibrated = calibStatus.isCalibrated,
            kFactor = calibStatus.kFactor,
            hasKAnomaly = calibStatus.hasAnomaly,

            // CDN
            cdnPackets = _cdnPacketCount.get(),
            nonCdnPackets = _nonCdnPacketCount.get(),
            isCidrStale = cdnAbrFilter.isCidrStale(),

            // Analiz Kalitesi
            droppedAnalysisPackets = interceptor.droppedPacketCount.get(),
            activeFlows = analyzer.activeFlowCount(),
            analysisUptimeMs = System.currentTimeMillis() - startTimeMs,
            analysisMode = mode,

            // Black Box
            frozenLogCount = blackBoxLogger.getFrozenLogs().size,

            // Durum
            isActive = engineJob?.isActive == true
        )
    }
}
