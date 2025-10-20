# Add project specific ProGuard rules here.
-keep class com.x360games.archivedownloader.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
