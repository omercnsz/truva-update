package com.truva.spoofing

/**
 * Truva Region Profile — Senkronize Bölge Kimliği
 *
 * Bir bölge profili seçildiğinde IP/SIM/GPS/Timezone/Locale verileri otomatik olarak eşleşir. Bu
 * sayede hedef uygulama tüm kontrol noktalarında tutarlı bir coğrafi kimlik görür.
 */
data class RegionProfile(
        val id: String, // "US", "DE", "TR", "GB", "JP"
        val displayName: String, // "Amerika Birleşik Devletleri"
        val flagEmoji: String, // "🇺🇸"

        // SIM Spoofing
        val simCountryIso: String, // "us"
        val simOperatorName: String, // "T-Mobile"
        val simOperatorMcc: String, // "310"
        val simOperatorMnc: String, // "260"
        val networkCountryIso: String, // "us"
        val networkOperatorName: String, // "T-Mobile"
        val networkOperatorMccMnc: String, // "310260"

        // GPS Spoofing
        val latitude: Double, // 40.7128
        val longitude: Double, // -74.0060
        val altitude: Double = 10.0, // metres
        val accuracy: Float = 3.0f, // metres

        // System Spoofing
        val timezone: String, // "America/New_York"
        val locale: String, // "en_US"
        val language: String, // "en"
        val country: String, // "US"

        // Ek bilgi
        val phoneCountryCode: String = "+1" // Telefon ülke kodu
) {
    /** MCC+MNC birleşik kodu */
    val mccMnc: String
        get() = "$simOperatorMcc$simOperatorMnc"

    companion object {
        /** Önceden tanımlı bölge profilleri */
        val PROFILES: List<RegionProfile> =
                listOf(
                        RegionProfile(
                                id = "US",
                                displayName = "ABD - New York",
                                flagEmoji = "🇺🇸",
                                simCountryIso = "us",
                                simOperatorName = "T-Mobile",
                                simOperatorMcc = "310",
                                simOperatorMnc = "260",
                                networkCountryIso = "us",
                                networkOperatorName = "T-Mobile",
                                networkOperatorMccMnc = "310260",
                                latitude = 40.7128,
                                longitude = -74.0060,
                                timezone = "America/New_York",
                                locale = "en_US",
                                language = "en",
                                country = "US",
                                phoneCountryCode = "+1"
                        ),
                        RegionProfile(
                                id = "US_LA",
                                displayName = "ABD - Los Angeles",
                                flagEmoji = "🇺🇸",
                                simCountryIso = "us",
                                simOperatorName = "Verizon",
                                simOperatorMcc = "311",
                                simOperatorMnc = "480",
                                networkCountryIso = "us",
                                networkOperatorName = "Verizon",
                                networkOperatorMccMnc = "311480",
                                latitude = 34.0522,
                                longitude = -118.2437,
                                timezone = "America/Los_Angeles",
                                locale = "en_US",
                                language = "en",
                                country = "US",
                                phoneCountryCode = "+1"
                        ),
                        RegionProfile(
                                id = "GB",
                                displayName = "İngiltere - Londra",
                                flagEmoji = "🇬🇧",
                                simCountryIso = "gb",
                                simOperatorName = "EE",
                                simOperatorMcc = "234",
                                simOperatorMnc = "30",
                                networkCountryIso = "gb",
                                networkOperatorName = "EE",
                                networkOperatorMccMnc = "23430",
                                latitude = 51.5074,
                                longitude = -0.1278,
                                timezone = "Europe/London",
                                locale = "en_GB",
                                language = "en",
                                country = "GB",
                                phoneCountryCode = "+44"
                        ),
                        RegionProfile(
                                id = "DE",
                                displayName = "Almanya - Berlin",
                                flagEmoji = "🇩🇪",
                                simCountryIso = "de",
                                simOperatorName = "Vodafone DE",
                                simOperatorMcc = "262",
                                simOperatorMnc = "02",
                                networkCountryIso = "de",
                                networkOperatorName = "Vodafone DE",
                                networkOperatorMccMnc = "26202",
                                latitude = 52.5200,
                                longitude = 13.4050,
                                timezone = "Europe/Berlin",
                                locale = "de_DE",
                                language = "de",
                                country = "DE",
                                phoneCountryCode = "+49"
                        ),
                        RegionProfile(
                                id = "JP",
                                displayName = "Japonya - Tokyo",
                                flagEmoji = "🇯🇵",
                                simCountryIso = "jp",
                                simOperatorName = "NTT Docomo",
                                simOperatorMcc = "440",
                                simOperatorMnc = "10",
                                networkCountryIso = "jp",
                                networkOperatorName = "NTT Docomo",
                                networkOperatorMccMnc = "44010",
                                latitude = 35.6762,
                                longitude = 139.6503,
                                timezone = "Asia/Tokyo",
                                locale = "ja_JP",
                                language = "ja",
                                country = "JP",
                                phoneCountryCode = "+81"
                        ),
                        RegionProfile(
                                id = "KR",
                                displayName = "Güney Kore - Seul",
                                flagEmoji = "🇰🇷",
                                simCountryIso = "kr",
                                simOperatorName = "SK Telecom",
                                simOperatorMcc = "450",
                                simOperatorMnc = "05",
                                networkCountryIso = "kr",
                                networkOperatorName = "SK Telecom",
                                networkOperatorMccMnc = "45005",
                                latitude = 37.5665,
                                longitude = 126.9780,
                                timezone = "Asia/Seoul",
                                locale = "ko_KR",
                                language = "ko",
                                country = "KR",
                                phoneCountryCode = "+82"
                        ),
                        RegionProfile(
                                id = "BR",
                                displayName = "Brezilya - São Paulo",
                                flagEmoji = "🇧🇷",
                                simCountryIso = "br",
                                simOperatorName = "Claro BR",
                                simOperatorMcc = "724",
                                simOperatorMnc = "05",
                                networkCountryIso = "br",
                                networkOperatorName = "Claro BR",
                                networkOperatorMccMnc = "72405",
                                latitude = -23.5505,
                                longitude = -46.6333,
                                timezone = "America/Sao_Paulo",
                                locale = "pt_BR",
                                language = "pt",
                                country = "BR",
                                phoneCountryCode = "+55"
                        ),
                        RegionProfile(
                                id = "TR",
                                displayName = "Türkiye - İstanbul",
                                flagEmoji = "🇹🇷",
                                simCountryIso = "tr",
                                simOperatorName = "Turkcell",
                                simOperatorMcc = "286",
                                simOperatorMnc = "01",
                                networkCountryIso = "tr",
                                networkOperatorName = "Turkcell",
                                networkOperatorMccMnc = "28601",
                                latitude = 41.0082,
                                longitude = 28.9784,
                                timezone = "Europe/Istanbul",
                                locale = "tr_TR",
                                language = "tr",
                                country = "TR",
                                phoneCountryCode = "+90"
                        ),
                        RegionProfile(
                                id = "SE",
                                displayName = "İsveç - Stockholm",
                                flagEmoji = "🇸🇪",
                                simCountryIso = "se",
                                simOperatorName = "Telia SE",
                                simOperatorMcc = "240",
                                simOperatorMnc = "01",
                                networkCountryIso = "se",
                                networkOperatorName = "Telia SE",
                                networkOperatorMccMnc = "24001",
                                latitude = 59.3293,
                                longitude = 18.0686,
                                timezone = "Europe/Stockholm",
                                locale = "sv_SE",
                                language = "sv",
                                country = "SE",
                                phoneCountryCode = "+46"
                        ),
                        RegionProfile(
                                id = "FI",
                                displayName = "Finlandiya - Helsinki",
                                flagEmoji = "🇫🇮",
                                simCountryIso = "fi",
                                simOperatorName = "Elisa",
                                simOperatorMcc = "244",
                                simOperatorMnc = "05",
                                networkCountryIso = "fi",
                                networkOperatorName = "Elisa",
                                networkOperatorMccMnc = "24405",
                                latitude = 60.1699,
                                longitude = 24.9384,
                                timezone = "Europe/Helsinki",
                                locale = "fi_FI",
                                language = "fi",
                                country = "FI",
                                phoneCountryCode = "+358"
                        ),
                        RegionProfile(
                                id = "SG",
                                displayName = "Singapur",
                                flagEmoji = "🇸🇬",
                                simCountryIso = "sg",
                                simOperatorName = "Singtel",
                                simOperatorMcc = "525",
                                simOperatorMnc = "01",
                                networkCountryIso = "sg",
                                networkOperatorName = "Singtel",
                                networkOperatorMccMnc = "52501",
                                latitude = 1.3521,
                                longitude = 103.8198,
                                timezone = "Asia/Singapore",
                                locale = "en_SG",
                                language = "en",
                                country = "SG",
                                phoneCountryCode = "+65"
                        ),
                        RegionProfile(
                                id = "IN",
                                displayName = "Hindistan - Mumbai",
                                flagEmoji = "🇮🇳",
                                simCountryIso = "in",
                                simOperatorName = "Jio",
                                simOperatorMcc = "405",
                                simOperatorMnc = "872",
                                networkCountryIso = "in",
                                networkOperatorName = "Jio",
                                networkOperatorMccMnc = "405872",
                                latitude = 19.0760,
                                longitude = 72.8777,
                                timezone = "Asia/Kolkata",
                                locale = "en_IN",
                                language = "en",
                                country = "IN",
                                phoneCountryCode = "+91"
                        )
                )

        fun findById(id: String): RegionProfile? = PROFILES.find { it.id == id }

        /** Proxy bölge koduna göre en yakın profili bul */
        fun findByRegionCode(regionCode: String): RegionProfile? =
                PROFILES.find { it.id == regionCode || it.simCountryIso == regionCode.lowercase() }
    }
}
