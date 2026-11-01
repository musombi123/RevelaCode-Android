#!/usr/bin/env node
import { access, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const variablesPath = resolve("android/variables.gradle");
const appGradlePath = resolve("android/app/build.gradle");
const rulesPath = resolve("android/app/proguard-rules.pro");

async function exists(path) {
  try { await access(path); return true; } catch { return false; }
}

if (await exists(variablesPath)) {
  let variables = await readFile(variablesPath, "utf8");
  for (const [key, value] of [
    ["minSdkVersion", 24],
    ["compileSdkVersion", 36],
    ["targetSdkVersion", 36],
  ]) {
    const pattern = new RegExp("(\\b" + key + "\\s*=\\s*)\\d+");
    if (!pattern.test(variables)) {
      throw new Error("Expected " + key + " in android/variables.gradle; refusing to silently change SDK policy.");
    }
    variables = variables.replace(pattern, "$1" + value);
  }
  await writeFile(variablesPath, variables, "utf8");
} else {
  throw new Error("Capacitor Android variables.gradle was not generated.");
}

let gradle = await readFile(appGradlePath, "utf8");
const releasePattern = /(release\s*\{)([\s\S]*?)(\n\s*\})/;
const match = gradle.match(releasePattern);
if (!match) throw new Error("Could not locate the Android release build type.");

let release = match[2];
const setBoolean = (key) => {
  const pattern = new RegExp("(^\\s*" + key + "\\s+)(true|false)", "m");
  if (pattern.test(release)) release = release.replace(pattern, "$1true");
  else release = "\n            " + key + " true" + release;
};
setBoolean("minifyEnabled");
setBoolean("shrinkResources");

const proguardLine = "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'";
if (/^\s*proguardFiles\s+/m.test(release)) {
  release = release.replace(/^\s*proguardFiles\s+.*$/m, proguardLine);
} else {
  release += "\n" + proguardLine + "\n        ";
}

gradle = gradle.replace(releasePattern, match[1] + release + match[3]);
await writeFile(appGradlePath, gradle, "utf8");

const rules = "# Keep Capacitor plugin registration and the app's custom permission bridge.\n" +
  "-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }\n" +
  "-keepclassmembers class * { @com.getcapacitor.PluginMethod <methods>; }\n" +
  "-keep class com.revelacode.app.MainActivity { *; }\n" +
  "-keep class com.revelacode.app.RevelaPermissionsPlugin { *; }\n";
let existingRules = "";
if (await exists(rulesPath)) existingRules = await readFile(rulesPath, "utf8");
if (!existingRules.includes("@com.getcapacitor.annotation.CapacitorPlugin")) {
  await writeFile(rulesPath, (existingRules.trim() ? existingRules.trim() + "\n\n" : "") + rules, "utf8");
}
console.log("Android SDK policy set to min 24 / compile 36 / target 36; release R8 and resource shrinking enabled.");
