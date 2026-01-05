# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Prevent R8 from stripping generic signatures from TypeToken subclasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Application specific Gson rules
# Ensure we keep generic signatures for our data classes
-keep class com.mesh.client.data.** { *; }
-keepclassmembers class com.mesh.client.data.** { *; }
-keep class com.mesh.client.network.** { *; }
-keep class com.mesh.client.updates.UpdateManager$UpdateResponse { *; }
-keep class com.mesh.client.updates.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }
-keep class org.chromium.build.BuildHooksAndroid { *; }
-keep class org.jni_zero.** { *; }
-dontwarn org.chromium.build.BuildHooksAndroid
-dontwarn org.webrtc.**
-dontwarn org.jni_zero.**

# EdDSA / BouncyCastle
-dontwarn sun.security.x509.**
-dontwarn net.i2p.crypto.eddsa.**