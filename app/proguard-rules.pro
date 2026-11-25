# Ktor / OkHttp
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fredsystems.brilliantboard.**$$serializer { *; }
-keepclassmembers class com.fredsystems.brilliantboard.** {
    *** Companion;
}
-keepclasseswithmembers class com.fredsystems.brilliantboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
