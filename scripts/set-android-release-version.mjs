#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const appVersion = String(process.env.ANDROID_VERSION_NAME || "2.2.3").trim();
const rawCode = String(process.env.ANDROID_VERSION_CODE || "7").trim();

if (!/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(appVersion)) {
  throw new Error("ANDROID_VERSION_NAME must be a semantic version such as 2.2.2.");
}
if (!/^\d+$/.test(rawCode) || Number(rawCode) < 1) {
  throw new Error("ANDROID_VERSION_CODE must be a positive integer.");
}

const gradlePath = resolve("android/app/build.gradle");
let source;
try {
  source = await readFile(gradlePath, "utf8");
} catch {
  throw new Error("Android project not found. Run npx cap add android before preparing the release.");
}

if (!/\bversionCode\s+\d+/.test(source) || !/\bversionName\s+["'][^"']+["']/.test(source)) {
  throw new Error("Could not locate versionCode and versionName in android/app/build.gradle.");
}

source = source.replace(/\bversionCode\s+\d+/, "versionCode " + Number(rawCode));
source = source.replace(/\bversionName\s+["'][^"']+["']/, 'versionName "' + appVersion + '"');
await writeFile(gradlePath, source, "utf8");

const packagePath = resolve("package.json");
const lockPath = resolve("package-lock.json");
const packageJson = JSON.parse(await readFile(packagePath, "utf8"));
packageJson.version = appVersion;
await writeFile(packagePath, JSON.stringify(packageJson, null, 2) + "\n", "utf8");

const packageLock = JSON.parse(await readFile(lockPath, "utf8"));
packageLock.version = appVersion;
if (packageLock.packages && packageLock.packages[""]) {
  packageLock.packages[""].version = appVersion;
}
await writeFile(lockPath, JSON.stringify(packageLock, null, 2) + "\n", "utf8");

console.log("Prepared Android version " + appVersion + " (versionCode " + Number(rawCode) + ").");
