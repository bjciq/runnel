# kotlinx.serialization：保留生成的序列化器
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# readability4j / jsoup 的可选依赖
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**
# 便于看崩溃栈：只裁剪不混淆（个人自用 App，包体积不敏感）
-dontobfuscate
