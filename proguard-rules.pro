# Truva VPN - Aggressive Release Rules

# --- OBFUSCATION DICTIONARIES ---
# Bu ayarlar R8'in sinif, degisken ve paket isimlerini a,b,c yerine
# anlamsiz l1Il l1ll gibi karakterlere cevirmesini saglar.
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# Tersine muhendisligi zorlastirmak icin extra ayarlar
-repackageclasses ''
-flattenpackagehierarchy ''
-allowaccessmodification

# Remove Log outputs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# --- KESİNLİKLE KORUNMASI GEREKENLER (ENTRY POINTS) ---

# 1. Android Sistem Bileşenleri (Manifest'te bizzat cagirilanlar)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 2. Vpn Service 
-keep class com.truva.MyVpnService { *; }

# 3. Room Database & Entities (SQLite yansimasinin cokmemesi icin)
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.Entity
-keep @androidx.room.Entity class *
# Tablo isimleri ve kolon isimlerinin karismamasi icin
-keepclassmembers @androidx.room.Entity class * { *; }

# 4. JNI Sızıntıları (C++ Native Baglantilari)
-keepclasseswithmembernames class * {
    native <methods>;
}

# 5. Xposed/LSPatch Korumasi (Disaridan Hooklanmasi Gerekenler)
-keep class com.truva.xposed.TruvaHookModule { *; }
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# 6. Gson Serialization (JSON Parsing)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 7. Xray GoMobile Motoru (Core Xray interface'leri)
-keep class go.** { *; }
-keep interface go.** { *; }
-keep class xray.** { *; }
-keep interface xray.** { *; }
-keep class com.truva.Xray { *; }

# --- UYARILARI BASTIR (Build'in basarili olmasi icin) ---
-dontwarn androidx.room.paging.**
-dontwarn com.google.android.gms.**
-dontwarn com.android.apksig.**
