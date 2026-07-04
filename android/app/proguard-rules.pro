# Chaquopy Python 集成（反射调用 Python 模块，必须保留）
-keep class com.chaquo.python.** { *; }
-keep class org.python.** { *; }
-dontwarn com.chaquo.python.**
-dontwarn org.python.**

# 应用自身代码（含 Python 桥接调用、数据模型反射）
-keep class com.iptv.scanner.editor.pro.** { *; }

# JNI native 方法（VLC/IJK/MPV/ExoPlayer 的 native 绑定）
-keepclasseswithmembernames class * {
    native <methods>;
}

# MPVLib 回调方法（libplayer.so 通过 JNI 反射调用 eventProperty/event/logMessage 等
# 非原生方法，R8 静态分析看不到 Java/Kotlin 端引用，必须显式保留，否则启动即闪退）
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }

# VLC Java 绑定（org.videolan.libvlc 反射加载）
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# IJKPlayer Java 绑定（tv.danmaku.ijk 反射加载）
-keep class tv.danmaku.ijk.media.player.** { *; }
-dontwarn tv.danmaku.**

# kotlinx-serialization（@Serializable 生成的序列化器）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose（部分反射 + 修饰符保留）
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Coil 图片加载（反射加载解码器）
-keep class coil.** { *; }
-dontwarn coil.**

# Google ZXing（反射加载解码器）
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# AndroidX Media3（ExoPlayer 反射加载渲染器）
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
