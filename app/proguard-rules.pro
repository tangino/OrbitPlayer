# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.antigravity.equalizer.native.** { *; }
-keep class com.antigravity.equalizer.data.model.** { *; }
