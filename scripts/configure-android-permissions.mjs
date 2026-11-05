#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const manifestPath = resolve("android/app/src/main/AndroidManifest.xml");
const activityPath = resolve("android/app/src/main/java/com/revelacode/app/MainActivity.java");
const pluginPath = resolve("android/app/src/main/java/com/revelacode/app/RevelaPermissionsPlugin.java");

let manifest = await readFile(manifestPath, "utf8");
const requiredPermissions = [
  "android.permission.CAMERA",
  "android.permission.RECORD_AUDIO",
  "android.permission.MODIFY_AUDIO_SETTINGS",
  "android.permission.INTERNET",
  "android.permission.ACCESS_NETWORK_STATE",
  "android.permission.ACCESS_WIFI_STATE",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.POST_NOTIFICATIONS",
];
const requiredFeatures = [
  '<uses-feature android:name="android.hardware.camera" android:required="false" />',
  '<uses-feature android:name="android.hardware.microphone" android:required="false" />',
];
const missingPermissions = requiredPermissions
  .filter((permission) => !manifest.includes('android:name="' + permission + '"'))
  .map((permission) => '  <uses-permission android:name="' + permission + '" />');
const missingFeatures = requiredFeatures.filter((feature) => {
  const name = feature.match(/android:name="([^"]+)"/)?.[1];
  return name && !manifest.includes('android:name="' + name + '"');
});
const manifestAdditions = [...missingPermissions, ...missingFeatures];

if (manifestAdditions.length) {
  if (!/<application\b/.test(manifest)) {
    throw new Error("Could not locate the application element in AndroidManifest.xml.");
  }
  manifest = manifest.replace(/(\s*)<application\b/, "\n" + manifestAdditions.join("\n") + "\n$1<application");
  await writeFile(manifestPath, manifest, "utf8");
}

const pluginSource = [
  'package com.revelacode.app;',
  '',
  'import android.Manifest;',
  'import android.content.pm.PackageManager;',
  'import android.content.Intent;',
  'import android.net.Uri;',
  'import android.os.Build;',
  'import android.provider.Settings;',
  '',
  'import com.getcapacitor.JSObject;',
  'import com.getcapacitor.Plugin;',
  'import com.getcapacitor.PluginCall;',
  'import com.getcapacitor.annotation.CapacitorPlugin;',
  'import com.getcapacitor.annotation.Permission;',
  'import com.getcapacitor.annotation.PermissionCallback;',
  'import com.getcapacitor.PluginMethod;',
  '',
  '',
  '@CapacitorPlugin(',
  '    name = "RevelaPermissions",',
  '    permissions = {',
  '        @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }),',
  '        @Permission(alias = "location", strings = {',
  '            Manifest.permission.ACCESS_COARSE_LOCATION,',
  '            Manifest.permission.ACCESS_FINE_LOCATION',
  '        }),',
  '        @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),',
  '        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })',
  '    }',
  ')',
  'public class RevelaPermissionsPlugin extends Plugin {',
  '    @PluginMethod',
  '    public void checkPermissions(PluginCall call) {',
  '        resolvePermissionStates(call);',
  '    }',
  '',
  '    @PluginMethod',
  '    public void requestCamera(PluginCall call) {',
  '        requestAlias(call, "camera");',
  '    }',
  '',
  '    @PluginMethod',
  '    public void requestLocation(PluginCall call) {',
  '        requestAlias(call, "location");',
  '    }',
  '',
  '    @PluginMethod',
  '    public void requestMicrophone(PluginCall call) {',
  '        requestAlias(call, "microphone");',
  '    }',
  '',
  '    @PluginMethod',
  '    public void requestNotifications(PluginCall call) {',
  '        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {',
  '            resolvePermissionStates(call);',
  '            return;',
  '        }',
  '        requestAlias(call, "notifications");',
  '    }',
  '',
  '    private void requestAlias(PluginCall call, String alias) {',
  '        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {',
  '            resolvePermissionStates(call);',
  '            return;',
  '        }',
  '        if (hasAliasAccess(alias)) {',
  '            resolvePermissionStates(call);',
  '            return;',
  '        }',
  '        requestPermissionForAlias(alias, call, "permissionRequestCallback");',
  '    }',
  '',
  '    @PermissionCallback',
  '    private void permissionRequestCallback(PluginCall call) {',
  '        resolvePermissionStates(call);',
  '    }',
  '',
  '    private void resolvePermissionStates(PluginCall call) {',
  '        JSObject result = new JSObject();',
  '        result.put("camera", permissionState("camera"));',
  '        result.put("location", permissionState("location"));',
  '        result.put("microphone", permissionState("microphone"));',
  '        result.put("notifications", permissionState("notifications"));',
  '        call.resolve(result);',
  '    }',
  '',
  '    /* Read the state directly from Android, not Capacitor\'s nullable alias-state map. */',
  '    private String permissionState(String alias) {',
  '        if ("location".equals(alias)) {',
  '            if (hasLocationAccess()) return "granted";',
  '            return shouldShowRationale(Manifest.permission.ACCESS_COARSE_LOCATION)',
  '                || shouldShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)',
  '                ? "prompt-with-rationale" : "prompt";',
  '        }',
  '        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "granted";',
  '        String permission = permissionString(alias);',
  '        if (permission == null) return "prompt";',
  '        if (hasPermission(permission)) return "granted";',
  '        return shouldShowRationale(permission) ? "prompt-with-rationale" : "prompt";',
  '    }',
  '',
  '    private String permissionString(String alias) {',
  '        if ("camera".equals(alias)) return Manifest.permission.CAMERA;',
  '        if ("microphone".equals(alias)) return Manifest.permission.RECORD_AUDIO;',
  '        if ("notifications".equals(alias)) return Manifest.permission.POST_NOTIFICATIONS;',
  '        return null;',
  '    }',
  '',
  '    private boolean hasAliasAccess(String alias) {',
  '        if ("location".equals(alias)) return hasLocationAccess();',
  '        if ("notifications".equals(alias) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;',
  '        String permission = permissionString(alias);',
  '        return permission != null && hasPermission(permission);',
  '    }',
  '',
  '    private boolean hasPermission(String permission) {',
  '        return getContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;',
  '    }',
  '',
  '    private boolean shouldShowRationale(String permission) {',
  '        return getActivity() != null && getActivity().shouldShowRequestPermissionRationale(permission);',
  '    }',
  '',
  '    private boolean hasLocationAccess() {',
  '        return hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)',
  '            || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);',
  '    }',
  '',
  '    @PluginMethod',
  '    public void openAppSettings(PluginCall call) {',
  '        try {',
  '            Intent intent = new Intent(',
  '                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,',
  '                Uri.parse("package:" + getContext().getPackageName())',
  '            );',
  '            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);',
  '            getContext().startActivity(intent);',
  '            call.resolve();',
  '        } catch (Exception error) {',
  '            call.reject("Unable to open Android app settings: " + error.getMessage());',
  '        }',
  '    }',
  '}',
  ''
].join("\n");

await mkdir(dirname(pluginPath), { recursive: true });
await writeFile(pluginPath, pluginSource, "utf8");

let activity = await readFile(activityPath, "utf8");
if (!activity.includes("import android.os.Bundle;")) {
  activity = activity.replace(
    /(package\s+[\w.]+;\s*)/,
    "$1\nimport android.os.Bundle;\n"
  );
}

if (!activity.includes("registerPlugin(RevelaPermissionsPlugin.class)")) {
  const emptyClass = /public\s+class\s+MainActivity\s+extends\s+BridgeActivity\s*\{\s*\}/;
  if (emptyClass.test(activity)) {
    activity = activity.replace(
      emptyClass,
      'public class MainActivity extends BridgeActivity {\n' +
      '    @Override\n' +
      '    public void onCreate(Bundle savedInstanceState) {\n' +
      '        registerPlugin(RevelaPermissionsPlugin.class);\n' +
      '        super.onCreate(savedInstanceState);\n' +
      '    }\n' +
      '}'
    );
  } else if (/public\s+void\s+onCreate\s*\(\s*Bundle\s+savedInstanceState\s*\)\s*\{/.test(activity)) {
    activity = activity.replace(
      /(public\s+void\s+onCreate\s*\(\s*Bundle\s+savedInstanceState\s*\)\s*\{)/,
      "$1\n        registerPlugin(RevelaPermissionsPlugin.class);"
    );
  } else if (/public\s+class\s+MainActivity\s+extends\s+BridgeActivity\s*\{/.test(activity)) {
    activity = activity.replace(
      /public\s+class\s+MainActivity\s+extends\s+BridgeActivity\s*\{/,
      'public class MainActivity extends BridgeActivity {\n' +
      '    @Override\n' +
      '    public void onCreate(Bundle savedInstanceState) {\n' +
      '        registerPlugin(RevelaPermissionsPlugin.class);\n' +
      '        super.onCreate(savedInstanceState);\n' +
      '    }\n'
    );
  } else {
    throw new Error("Could not safely register RevelaPermissionsPlugin in MainActivity.java.");
  }
}
await writeFile(activityPath, activity, "utf8");

console.log("Configured Android camera, location, microphone, speaker audio settings, internet and network-state permissions.");
console.log("Files are accessed through Android's system picker; no unrestricted storage permission was added.");
