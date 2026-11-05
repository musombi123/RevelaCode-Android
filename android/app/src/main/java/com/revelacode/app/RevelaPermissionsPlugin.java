package com.revelacode.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PluginMethod;

@CapacitorPlugin(
    name = "RevelaPermissions",
    permissions = {
        @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }),
        @Permission(alias = "location", strings = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        }),
        @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class RevelaPermissionsPlugin extends Plugin {
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        resolvePermissionStates(call);
    }

    @PluginMethod
    public void requestCamera(PluginCall call) {
        requestAlias(call, "camera");
    }

    @PluginMethod
    public void requestLocation(PluginCall call) {
        requestAlias(call, "location");
    }

    @PluginMethod
    public void requestMicrophone(PluginCall call) {
        requestAlias(call, "microphone");
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            resolvePermissionStates(call);
            return;
        }
        requestAlias(call, "notifications");
    }

    private void requestAlias(PluginCall call, String alias) {
        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            resolvePermissionStates(call);
            return;
        }
        if (hasAliasAccess(alias)) {
            resolvePermissionStates(call);
            return;
        }
        requestPermissionForAlias(alias, call, "permissionRequestCallback");
    }

    @PermissionCallback
    private void permissionRequestCallback(PluginCall call) {
        resolvePermissionStates(call);
    }

    private void resolvePermissionStates(PluginCall call) {
        JSObject result = new JSObject();
        result.put("camera", permissionState("camera"));
        result.put("location", permissionState("location"));
        result.put("microphone", permissionState("microphone"));
        result.put("notifications", permissionState("notifications"));
        call.resolve(result);
    }

    /*
     * Read permission state from Android itself. Capacitor's alias-state map can
     * be empty in an R8-minified release if an annotation is not retained; the
     * app must not dereference a nullable PermissionState during startup.
     */
    private String permissionState(String alias) {
        if ("location".equals(alias)) {
            if (hasLocationAccess()) return "granted";
            return shouldShowRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                || shouldShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                ? "prompt-with-rationale"
                : "prompt";
        }

        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "granted";
        }

        String permission = permissionString(alias);
        if (permission == null) return "prompt";
        if (hasAndroidPermission(permission)) return "granted";
        return shouldShowRationale(permission) ? "prompt-with-rationale" : "prompt";
    }

    private String permissionString(String alias) {
        if ("camera".equals(alias)) return Manifest.permission.CAMERA;
        if ("microphone".equals(alias)) return Manifest.permission.RECORD_AUDIO;
        if ("notifications".equals(alias)) return Manifest.permission.POST_NOTIFICATIONS;
        return null;
    }

    private boolean hasAliasAccess(String alias) {
        if ("location".equals(alias)) return hasLocationAccess();
        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        String permission = permissionString(alias);
        return permission != null && hasAndroidPermission(permission);
    }

    private boolean hasAndroidPermission(String permission) {
        return getContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowRationale(String permission) {
        return getActivity() != null && getActivity().shouldShowRequestPermissionRationale(permission);
    }

    private boolean hasLocationAccess() {
        return hasAndroidPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            || hasAndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getContext().getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("Unable to open Android app settings: " + error.getMessage());
        }
    }
}
