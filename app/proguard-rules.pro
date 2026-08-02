-keep class org.bitcoindevkit.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# JNA includes an unused desktop-only helper whose signature mentions AWT.
-dontwarn java.awt.Component
