package com.amin.pocketgba;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

final class NativeContentWebViewClient extends WebViewClient {
    private final WebViewClient delegate;
    private final NativeLocalContentRouter router;

    NativeContentWebViewClient(WebViewClient delegate, NativeLocalContentRouter router) {
        this.delegate = delegate;
        this.router = router;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        WebResourceResponse local = router.intercept(request);
        return local != null ? local : delegate.shouldInterceptRequest(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return delegate.shouldOverrideUrlLoading(view, request);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return delegate.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        delegate.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        delegate.onPageFinished(view, url);
    }

    @Override
    public void onLoadResource(WebView view, String url) {
        delegate.onLoadResource(view, url);
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        delegate.doUpdateVisitedHistory(view, url, isReload);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        delegate.onReceivedError(view, request, error);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        delegate.onReceivedError(view, errorCode, description, failingUrl);
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        delegate.onReceivedHttpError(view, request, errorResponse);
    }

    @Override
    public void onFormResubmission(WebView view, Message dontResend, Message resend) {
        delegate.onFormResubmission(view, dontResend, resend);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        delegate.onReceivedSslError(view, handler, error);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        delegate.onReceivedHttpAuthRequest(view, handler, host, realm);
    }

    @Override
    public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
        delegate.onReceivedClientCertRequest(view, request);
    }

    @Override
    public void onScaleChanged(WebView view, float oldScale, float newScale) {
        delegate.onScaleChanged(view, oldScale, newScale);
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        return delegate.onRenderProcessGone(view, detail);
    }
}
