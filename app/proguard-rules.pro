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

# Gson specific rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Keep model classes used by Gson
-keep class com.application.zaona.weather.model.** { *; }
-keep class com.application.zaona.weather.service.UpdateService$AppUpdateInfo { *; }

# Keep TypeToken to prevent R8 from stripping the generic type information
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep Wearable library classes as they might be used via JNI or reflection
-keep class com.xiaomi.xms.wearable.** { *; }

# Microsoft Clarity
-keep class com.microsoft.clarity.** { *; }
-keepclassmembers class com.microsoft.clarity.** { *; }
-keep interface com.microsoft.clarity.** { *; }
-keep enum com.microsoft.clarity.** { *; }
-keepnames class com.microsoft.clarity.** { *; }
-dontwarn com.microsoft.clarity.**