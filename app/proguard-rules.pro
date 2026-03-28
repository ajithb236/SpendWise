# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfig buildTypes.release.signingConfig property
# in build.gradle.

# For more details, see
# http://developer.android.com/guide/developing/tools/proguard.html

# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.spendwise.** { *; }

# Room  
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }

# Room Persistence Library
-dontwarn androidx.room.RoomDatabase
-keep class androidx.room.** { *; }

# Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep buildConfigField
-keep class **.BuildConfig { *; }

# Keep my project's classes
-keep class com.spendwise.** { *; }
-keep interface com.spendwise.** { *; }

# Generic signatures
-keepattributes Signature

# Annotations
-keepattributes *Annotation*

# Stacktrace
-keepattributes SourceFile,LineNumberTable

# Keep methods in Activity that could be called from manifest
-keepclasseswithmembernames class * {
    native <methods>;
}
