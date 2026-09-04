# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep RIME native methods
-keep class com.azime.input.core.rime.** { *; }

# Keep Lua script related classes
-keep class org.luaj.** { *; }

# Keep data models
-keep class com.azime.input.data.** { *; }
