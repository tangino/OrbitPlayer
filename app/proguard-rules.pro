# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.orbit.music.native.** { *; }
-keep class com.orbit.music.data.model.** { *; }
