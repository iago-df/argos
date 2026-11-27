# the adb client, the pairing (spake2) and tls 1.3 are partly loaded by name or from native code
-keep class io.github.muntashirakon.** { *; }
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
# certificate code looks some of its classes up by name (extensions, algorithm ids)
-keep class android.sun.security.x509.** { *; }
-keep class android.sun.security.util.** { *; }
-dontwarn android.sun.**
-dontwarn javax.naming.**
-dontwarn sun.**
