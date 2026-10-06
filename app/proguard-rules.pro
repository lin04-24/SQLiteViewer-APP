# Add your ProGuard rules here for the release build
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# Keep baseline profile metadata
-keepclassmembers class androidx.profileinstaller.ProfileInstallReceiver {
    void onReceive(android.content.Context, android.content.Intent);
}
