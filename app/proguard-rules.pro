# AndroidX Biometric & Keystore
-keepclassmembers class * extends androidx.biometric.BiometricPrompt { *; }
-keep class androidx.biometric.** { *; }
-keep class com.applock.magic.security.** { *; }
-keep class com.applock.magic.service.** { *; }
-keep class com.applock.magic.data.** { *; }
-keep class com.applock.magic.overlay.** { *; }
-keep class com.applock.magic.ui.** { *; }

# Compose Keep Rules
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**
