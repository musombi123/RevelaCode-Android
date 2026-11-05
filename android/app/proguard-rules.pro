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

# Keep Capacitor plugin registration and the app's custom permission bridge.
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * { @com.getcapacitor.PluginMethod <methods>; }
-keep class com.revelacode.app.MainActivity { *; }
-keep class com.revelacode.app.RevelaPermissionsPlugin { *; }

# Keep runtime permission metadata and callback methods readable after R8 shrinking.
# Capacitor resolves @CapacitorPlugin/@Permission annotations reflectively in release builds.
-keepattributes *Annotation*
-keep @interface com.getcapacitor.annotation.** { *; }
-keep @interface com.getcapacitor.PluginMethod { *; }
-keepclassmembers class * extends com.getcapacitor.Plugin {
    @com.getcapacitor.annotation.PermissionCallback <methods>;
}
