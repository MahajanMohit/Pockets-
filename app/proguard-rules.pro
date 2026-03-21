# ZenDeck ProGuard Rules

# Room – keep entity and DAO classes
-keep class com.zendeck.app.data.db.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# MediaPipe – keep all native bridge classes
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# AutoValue / JavaPoet shaded inside MediaPipe – annotation-processor only,
# not present on Android at runtime. Suppress R8 missing-class errors.
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn javax.annotation.processing.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**

# Jsoup – no reflection usage, safe to shrink
-dontwarn org.jsoup.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose – required for reflection-based previews in debug; release is fine
-dontwarn androidx.compose.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance widget
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Keep application entry points
-keep class com.zendeck.app.ZenDeckApplication { *; }
-keep class com.zendeck.app.MainActivity { *; }
-keep class com.zendeck.app.service.ShareActivity { *; }
-keep class com.zendeck.app.widget.ZenDeckWidgetReceiver { *; }
-keep class com.zendeck.app.worker.TTLWorker { *; }
