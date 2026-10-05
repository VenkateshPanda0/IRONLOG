# Backups are kotlinx.serialization JSON: keep the generated serializers of @Serializable classes
# (the library's own consumer rules cover the runtime).
-keepclassmembers @kotlinx.serialization.Serializable class app.ironlog.personal.** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.ironlog.personal.**$$serializer { *; }

# Stack traces in crash reports stay readable with the mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
