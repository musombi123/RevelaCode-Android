package com.revelacode.app;

import android.app.DownloadManager;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.net.URLConnection;
import java.util.Locale;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RevelaPermissionsPlugin.class);
        super.onCreate(savedInstanceState);
        applyImmersiveAppNavigation();
        attachSecureDesignDownloads();
    }

    /**
     * Hide Android's three-button navigation strip so the app's own mobile
     * navigation dock is the only persistent navigation bar on screen.
     * Users can still reveal the Android system bar with a swipe.
     */
    private void applyImmersiveAppNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
            return;
        }

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveAppNavigation();
        }
    }

    /**
     * Route HTML download actions through Android DownloadManager so files
     * are saved to the user's public Downloads collection, not trapped in the
     * WebView or an app-private folder.
     */
    private void attachSecureDesignDownloads() {
        if (getBridge() == null || getBridge().getWebView() == null) {
            return;
        }

        WebView webView = getBridge().getWebView();
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);

            boolean trustedHost = host.equals("revelaai.onrender.com")
                    || host.equals("revelacode-backend.onrender.com")
                    || host.equals("revelacode.co.ke")
                    || host.endsWith(".revelacode.co.ke")
                    || host.equals("res.cloudinary.com")
                    || host.endsWith(".cloudinary.com")
                    || host.equals("storage.googleapis.com");
            String guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            String resolvedMime = mimeType == null || mimeType.isEmpty()
                    ? URLConnection.guessContentTypeFromName(guessedName)
                    : mimeType;
            boolean supportedExtension = path.matches("(?i).*\\.(pdf|png|jpe?g|webp|gif|mp4|mov|m4v|webm|mp3|wav|ogg|csv|txt|json|xml|zip|xlsx|xls|docx|doc|pptx|ppt)$");
            boolean supportedMime = resolvedMime != null && (
                    resolvedMime.startsWith("image/")
                    || resolvedMime.startsWith("video/")
                    || resolvedMime.startsWith("audio/")
                    || resolvedMime.equals("application/pdf")
                    || resolvedMime.equals("text/plain")
                    || resolvedMime.equals("text/csv")
                    || resolvedMime.equals("application/csv")
                    || resolvedMime.equals("application/json")
                    || resolvedMime.equals("application/zip")
                    || resolvedMime.contains("spreadsheet")
                    || resolvedMime.contains("excel")
                    || resolvedMime.contains("word")
                    || resolvedMime.contains("presentation")
            );

            if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost || (!supportedExtension && !supportedMime)) {
                Toast.makeText(this, "This file source or format is not allowed.", Toast.LENGTH_SHORT).show();
                return;
            }

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(this, "Android download service is unavailable.", Toast.LENGTH_SHORT).show();
                return;
            }

            String fileName = guessedName == null || guessedName.trim().isEmpty()
                    ? "revelacode-download"
                    : guessedName.replaceAll("[^A-Za-z0-9._-]", "_");
            int extensionIndex = fileName.lastIndexOf('.');
            String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
            String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
            fileName = baseName + "-" + System.currentTimeMillis() + extension;

            DownloadManager.Request download = new DownloadManager.Request(uri);
            download.setTitle(guessedName == null ? "RevelaCode download" : guessedName);
            download.setDescription("Downloading a file from RevelaCode");
            if (resolvedMime != null && !resolvedMime.isEmpty()) {
                download.setMimeType(resolvedMime);
            }
            if (userAgent != null && !userAgent.isEmpty()) {
                download.addRequestHeader("User-Agent", userAgent);
            }
            download.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            download.setAllowedOverMetered(true);
            download.setAllowedOverRoaming(false);
            download.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            manager.enqueue(download);
            Toast.makeText(this, "Download started. Check your Downloads folder.", Toast.LENGTH_SHORT).show();
        });
    }
}
