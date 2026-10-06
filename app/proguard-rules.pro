# Add your ProGuard rules here for the release build
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# Keep baseline profile metadata
-keepclassmembers class androidx.profileinstaller.ProfileInstallReceiver {
    void onReceive(android.content.Context, android.content.Intent);
}
# Keep update checker serialization classes
-keep class com.example.dbviewer.data.GitHubRelease { *; }
-keep class com.example.dbviewer.data.GitHubAsset { *; }
-keep class com.example.dbviewer.data.UpdateInfo { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.dbviewer.**$$serializer { *; }
-keepclassmembers class com.example.dbviewer.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.dbviewer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
