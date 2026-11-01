package com.revelacode.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PluginMethod;

import java.util.Locale;

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
        if ("location".equals(alias) ? hasLocationAccess() : getPermissionState(alias) == PermissionState.GRANTED) {
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
        result.put("location", locationPermissionState());
        result.put("microphone", permissionState("microphone"));
        result.put("notifications", notificationPermissionState());
        call.resolve(result);
    }

    private String permissionState(String alias) {
        return getPermissionState(alias).toString().toLowerCase(Locale.ROOT);
    }

    private String notificationPermissionState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "granted";
        }
        return permissionState("notifications");
    }

    private boolean hasLocationAccess() {
        return getContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private String locationPermissionState() {
        return hasLocationAccess() ? "granted" : permissionState("location");
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
