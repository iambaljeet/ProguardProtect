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

## Fix for Crash Type 1: Reflection ClassNotFoundException
#-keep class app.proguard.models.UserProfile { *; }

## Fix for Crash Type 2: Reflection NoSuchMethodError
#-keepclassmembers class app.proguard.models.PaymentProcessor { *; }

## Fix for Crash Type 3: Gson serialization field renaming
#-keep class app.proguard.models.ApiResponse { *; }

## Fix for Crash Type 4: Enum class loaded dynamically
#-keep class app.proguard.models.OrderStatus { *; }

## Fix for Crash Type 5: Callback interface implementation loaded dynamically
#-keep class app.proguard.models.AnalyticsEventHandler { *; }

## Fix for Crash Type 6: Reflection field access
#-keepclassmembers class app.proguard.models.AppConfig { *; }

## Fix for Crash Type 8: ServiceLoader-like dynamic class loading
#-keep class app.proguard.models.PluginRegistry { *; }