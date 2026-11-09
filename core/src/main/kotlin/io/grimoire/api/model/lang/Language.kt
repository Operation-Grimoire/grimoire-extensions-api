package io.grimoire.api.model.lang

/**
 * A content / source language. [code] is the ISO 639-1 two-letter code; [MULTI]
 * (a source serving many languages, historically `lang = "all"`) and [UNKNOWN] are
 * sentinels with non-standard codes. [displayName] is the English name and
 * [nativeName] the endonym (the language's own name), so the host needs no separate
 * name table.
 *
 * Not exhaustive — a broad common set. Append entries as needed (appending is
 * binary-compatible). Resolve a code with [fromCode], which falls back to [UNKNOWN].
 */
enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
) {
    MULTI("all", "Multi-language", "Multi-language"),
    UNKNOWN("", "Unknown", "Unknown"),

    EN("en", "English", "English"),
    ES("es", "Spanish", "Español"),
    PT("pt", "Portuguese", "Português"),
    FR("fr", "French", "Français"),
    DE("de", "German", "Deutsch"),
    IT("it", "Italian", "Italiano"),
    NL("nl", "Dutch", "Nederlands"),
    RU("ru", "Russian", "Русский"),
    UK("uk", "Ukrainian", "Українська"),
    PL("pl", "Polish", "Polski"),
    CS("cs", "Czech", "Čeština"),
    SK("sk", "Slovak", "Slovenčina"),
    SL("sl", "Slovenian", "Slovenščina"),
    HR("hr", "Croatian", "Hrvatski"),
    SR("sr", "Serbian", "Српски"),
    BG("bg", "Bulgarian", "Български"),
    RO("ro", "Romanian", "Română"),
    EL("el", "Greek", "Ελληνικά"),
    HU("hu", "Hungarian", "Magyar"),
    TR("tr", "Turkish", "Türkçe"),
    AR("ar", "Arabic", "العربية"),
    HE("he", "Hebrew", "עברית"),
    FA("fa", "Persian", "فارسی"),
    UR("ur", "Urdu", "اردو"),
    HI("hi", "Hindi", "हिन्दी"),
    BN("bn", "Bengali", "বাংলা"),
    TA("ta", "Tamil", "தமிழ்"),
    TE("te", "Telugu", "తెలుగు"),
    ML("ml", "Malayalam", "മലയാളം"),
    MR("mr", "Marathi", "मराठी"),
    GU("gu", "Gujarati", "ગુજરાતી"),
    KN("kn", "Kannada", "ಕನ್ನಡ"),
    NE("ne", "Nepali", "नेपाली"),
    SI("si", "Sinhala", "සිංහල"),
    ZH("zh", "Chinese", "中文"),
    JA("ja", "Japanese", "日本語"),
    KO("ko", "Korean", "한국어"),
    VI("vi", "Vietnamese", "Tiếng Việt"),
    TH("th", "Thai", "ไทย"),
    ID("id", "Indonesian", "Bahasa Indonesia"),
    MS("ms", "Malay", "Bahasa Melayu"),
    FIL("fil", "Filipino", "Filipino"),
    MY("my", "Burmese", "မြန်မာ"),
    KM("km", "Khmer", "ខ្មែរ"),
    LO("lo", "Lao", "ລາວ"),
    MN("mn", "Mongolian", "Монгол"),
    KA("ka", "Georgian", "ქართული"),
    HY("hy", "Armenian", "Հայերեն"),
    AZ("az", "Azerbaijani", "Azərbaycan"),
    KK("kk", "Kazakh", "Қазақ"),
    UZ("uz", "Uzbek", "Oʻzbek"),
    SV("sv", "Swedish", "Svenska"),
    NO("no", "Norwegian", "Norsk"),
    DA("da", "Danish", "Dansk"),
    FI("fi", "Finnish", "Suomi"),
    IS("is", "Icelandic", "Íslenska"),
    CA("ca", "Catalan", "Català"),
    LT("lt", "Lithuanian", "Lietuvių"),
    LV("lv", "Latvian", "Latviešu"),
    ET("et", "Estonian", "Eesti"),
    AF("af", "Afrikaans", "Afrikaans"),
    SW("sw", "Swahili", "Kiswahili"),
    CY("cy", "Welsh", "Cymraeg"),
    GA("ga", "Irish", "Gaeilge"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code.lowercase() }

        /** Resolve an ISO 639-1 code (or `all`) to a [Language]; unknown codes → [UNKNOWN]. */
        fun fromCode(code: String): Language = byCode[code.trim().lowercase()] ?: UNKNOWN
    }
}
