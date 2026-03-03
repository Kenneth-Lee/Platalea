# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/share/java/proguard/proguard-android.txt

# JMX stubs used by Apache SSHD on Android (not available in runtime)
-keep class javax.management.** { *; }
