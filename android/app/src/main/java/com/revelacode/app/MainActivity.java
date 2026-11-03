package com.revelacode.app;

import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.util.Locale;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RevelaPermissionsPlugin.class);
        super.onCreate(savedInstanceState);
        attachSecureDesignDownloads();
    }

    /**
     * Handle the standard HTML download attribute inside the Android WebView.
     * Generated files are public media URLs; no admin credential or browser
     * storage token is required to fetch them.
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
                    || host.equals("revelacode.co.ke")
                    || host.equals("www.revelacode.co.ke");
            boolean supportedFile = path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".pdf");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost || !supportedFile) {
                Toast.makeText(this, "Only secure RevelaAI design downloads are allowed.", Toast.LENGTH_SHORT).show();
                return;
            }

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(this, "Android download service is unavailable.", Toast.LENGTH_SHORT).show();
                return;
            }

            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            DownloadManager.Request download = new DownloadManager.Request(uri);
            download.setTitle(fileName);
            download.setDescription("RevelaAI generated design");
            if (mimeType != null && !mimeType.isEmpty()) {
                download.setMimeType(mimeType);
            }
            download.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            download.setAllowedOverMetered(true);
            download.setAllowedOverRoaming(false);
            download.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
            manager.enqueue(download);
            Toast.makeText(this, "Design download started.", Toast.LENGTH_SHORT).show();
        });
    }
}
