# ===========================================================
# Project-specific ProGuard rules
# ===========================================================

# Preserve line number info for debugging (optional)
-keepattributes SourceFile,LineNumberTable

# Keep annotations (needed for Kotlinx Serialization, Gson, Moshi)
-keepattributes *Annotation*

# ===========================================================
# XR library warnings (your existing rules)
# ===========================================================
-dontwarn com.android.extensions.xr.XrExtensionResult
-dontwarn com.android.extensions.xr.XrExtensions
-dontwarn com.android.extensions.xr.function.Consumer
-dontwarn com.android.extensions.xr.node.InputEvent$HitInfo
-dontwarn com.android.extensions.xr.node.InputEvent
-dontwarn com.android.extensions.xr.node.Mat4f
-dontwarn com.android.extensions.xr.node.Node
-dontwarn com.android.extensions.xr.node.NodeTransaction
-dontwarn com.android.extensions.xr.node.NodeTransform
-dontwarn com.android.extensions.xr.node.Vec3
-dontwarn com.android.extensions.xr.splitengine.BufferHandle
-dontwarn com.android.extensions.xr.splitengine.MessageGroupCallback
-dontwarn com.android.extensions.xr.splitengine.RequestCallback
-dontwarn com.android.extensions.xr.splitengine.SystemRendererConnection
-dontwarn com.android.extensions.xr.subspace.Subspace

# ===========================================================
# Serialization rules (fix release APK crash)
# ===========================================================

# Keep all fields of your model/data classes
-keep class com.apro.ProMaherQuickBetAnalyzer.** {
    <fields>;
}

# Keep serializable classes annotated with @Serializable (Kotlinx Serialization)
-keep class com.apro.ProMaherQuickBetAnalyzer.** { @kotlinx.serialization.Serializable *; }

# Keep all Kotlinx Serialization library classes
-keep class kotlinx.serialization.** { *; }

# ===========================================================
# Optional: WebView JS interface (if used)
# ===========================================================
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ===========================================================
# Optional: preserve generic type info for serialization
# ===========================================================
-keepattributes Signature

# ===========================================================
# Additional optional rules to prevent R8 stripping
# ===========================================================
# Prevent obfuscation of classes used in reflection
-keepclassmembers class * {
    public <init>(...);
}
