# No reflection-based application classes are currently used.
# Keep WebView JavaScript entry points if a future version adds any.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
