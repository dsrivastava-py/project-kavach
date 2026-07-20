# Keep Moshi model classes
-keep class com.kavach.app.data.remote.dto.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep Retrofit interfaces
-keep interface com.kavach.app.data.remote.api.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Firebase
-keep class com.google.firebase.** { *; }
