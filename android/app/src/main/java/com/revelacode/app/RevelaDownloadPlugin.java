package com.revelacode.app;

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
    public void saveFile(PluginCall call) {
        String requestedName = call.getString("fileName", "revelacode-download");
        String mimeType = call.getString("mimeType", "application/octet-stream");
        String encoded = call.getString("data", "");

        if (encoded == null || encoded.trim().isEmpty()) {
            call.reject("No file data was provided.");
            return;
        }

        String safeName = requestedName == null ? "revelacode-download" : requestedName;
        safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isEmpty()) safeName = "revelacode-download";
        int extensionIndex = safeName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? safeName.substring(0, extensionIndex) : safeName;
        String extension = extensionIndex > 0 ? safeName.substring(extensionIndex) : "";
        safeName = baseName + "-" + System.currentTimeMillis() + extension;

        byte[] bytes;
        try {
            bytes = Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            call.reject("The file data is not valid Base64.", error);
            return;
        }

        if (bytes.length == 0) {
            call.reject("The generated file is empty.");
            return;
        }

        try {
            Uri savedUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RevelaCode");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                savedUri = getContext().getContentResolver().insert(collection, values);
                if (savedUri == null) throw new IllegalStateException("Android could not create the download entry.");

                try (OutputStream output = getContext().getContentResolver().openOutputStream(savedUri)) {
                    if (output == null) throw new IllegalStateException("Android could not open the download destination.");
                    output.write(bytes);
                    output.flush();
                }

                ContentValues completed = new ContentValues();
                completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContext().getContentResolver().update(savedUri, completed, null, null);
            } else {
                File directory = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) throw new IllegalStateException("Android download storage is unavailable.");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Android could not create the download folder.");
                }
                File destination = new File(directory, safeName);
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    output.write(bytes);
                    output.flush();
                }
                savedUri = Uri.fromFile(destination);
            }

            JSObject result = new JSObject();
            result.put("fileName", safeName);
            result.put("uri", savedUri.toString());
            result.put("bytes", bytes.length);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("The PDF could not be saved to Android Downloads.", error);
        }
    }
}
