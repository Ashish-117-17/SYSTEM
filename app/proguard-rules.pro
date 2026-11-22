# ProGuard rules for Prism (NewPipeExtractor & Firestore)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.schabi.newpipe.extractor.**
-keepattributes *Annotation*
-keep class com.example.data.model.** { *; }
