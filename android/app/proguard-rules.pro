-keep class bridge.** { *; }
-keep class go.** { *; }

# Readable stack traces in the in-app log.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
