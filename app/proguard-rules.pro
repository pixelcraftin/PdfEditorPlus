# ProGuard rules for PdfEditor+

# iText 7 & SLF4J & BouncyCastle
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn java.lang.invoke.**

# ML Kit Play Services
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.vision.**

# uCrop
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep data classes
-keep class com.pixelcraftin.pdfeditorplus.data.model.** { *; }