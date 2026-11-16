package com.revelacode.app;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "RevelaDownload")
public class RevelaDownloadPlugin extends Plugin {

    @PluginMethod
    public void downloadUrl(PluginCall call) {
        String source = call.getString("url", "");
        String requestedName = call.getString("fileName", "revelacode-download");
        String mimeType = call.getString("mimeType", "application/octet-stream");
        Uri uri;
        try {
            uri = Uri.parse(source);
        } catch (Exception error) {
            call.reject("The download URL is invalid.");
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            call.reject("Only secure HTTPS downloads are supported on Android.");
            return;
        }

        try {
            String fileName = sanitizeFileName(requestedName);
            DownloadManager manager = (DownloadManager) getContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Android download service is unavailable.");

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(fileName);
            request.setDescription("RevelaCode file download");
            request.setMimeType(mimeType);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS + "/RevelaCode", fileName);
            } else {
                File root = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (root == null) throw new IllegalStateException("App downloads storage is unavailable.");
                File directory = new File(root, "RevelaCode");
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create the download folder.");
                request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, "RevelaCode/" + fileName);
            }

            long downloadId = manager.enqueue(request);
            JSObject result = new JSObject();
            result.put("downloadId", downloadId);
            result.put("fileName", fileName);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Unable to start the download: " + error.getMessage(), error);
        }
    }

    @PluginMethod
    public void saveFile(PluginCall call) {
        String fileName = call.getString("fileName", "revelacode-download.pdf");
        String mimeType = call.getString("mimeType", "application/octet-stream");
        String base64 = call.getString("data", "");

        if (base64 == null || base64.trim().isEmpty()) {
            call.reject("No file data was provided.");
            return;
        }

        fileName = sanitizeFileName(fileName);
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length == 0) {
                call.reject("The generated file is empty.");
                return;
            }

            Uri savedUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savedUri = saveToPublicDownloads(fileName, mimeType, bytes);
            } else {
                savedUri = saveToAppDownloads(fileName, bytes);
            }

            if (savedUri == null) {
                call.reject("Android could not create the download file.");
                return;
            }

            JSObject result = new JSObject();
            result.put("fileName", fileName);
            result.put("uri", savedUri.toString());
            result.put("bytes", bytes.length);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Unable to save the file: " + error.getMessage(), error);
        }
    }

    private Uri saveToPublicDownloads(String fileName, String mimeType, byte[] bytes) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RevelaCode");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = getContext().getContentResolver().insert(collection, values);
        if (uri == null) throw new IllegalStateException("Downloads storage is unavailable.");

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("Could not open the download destination.");
            output.write(bytes);
            output.flush();
        } catch (Exception error) {
            getContext().getContentResolver().delete(uri, null, null);
            throw error;
        }

        ContentValues complete = new ContentValues();
        complete.put(MediaStore.Downloads.IS_PENDING, 0);
        getContext().getContentResolver().update(uri, complete, null, null);
        return uri;
    }

    private Uri saveToAppDownloads(String fileName, byte[] bytes) throws Exception {
        File root = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) throw new IllegalStateException("App downloads storage is unavailable.");
        File directory = new File(root, "RevelaCode");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create the download folder.");
        }
        File destination = uniqueFile(directory, fileName);
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(bytes);
            output.flush();
        }
        return Uri.fromFile(destination);
    }

    private File uniqueFile(File directory, String requestedName) {
        File candidate = new File(directory, requestedName);
        if (!candidate.exists()) return candidate;
        int dot = requestedName.lastIndexOf('.');
        String stem = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String extension = dot > 0 ? requestedName.substring(dot) : "";
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(directory, stem + " (" + index++ + ")" + extension);
        }
        return candidate;
    }

    private String sanitizeFileName(String value) {
        String name = value == null ? "revelacode-download" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "revelacode-download";
        }
        return name;
    }
}
