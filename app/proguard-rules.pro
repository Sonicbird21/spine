# Keep the entry class referenced by app/src/main/assets/xposed_init.
-keep class com.spotify.music.entry.CoreLoader { *; }

# Keep Xposed hook entry interface implementations discoverable.
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# Keep runtime-visible annotations and signatures used by Kotlin/Java reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Aggressively rename packages and merge classes into the root package
-repackageclasses ""
-allowaccessmodification
-dontusemixedcaseclassnames
