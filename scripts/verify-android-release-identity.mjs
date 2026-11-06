#!/usr/bin/env node
import { readFile } from "node:fs/promises";

const expectedVersion = String(process.env.ANDROID_VERSION_NAME || "2.2.1").trim();
const expectedCode = Number(process.env.ANDROID_VERSION_CODE || "5");

const [gradle, variables, packageJson, capacitor] = await Promise.all([
  readFile("android/app/build.gradle", "utf8"),
  readFile("android/variables.gradle", "utf8"),
  readFile("package.json", "utf8").then(JSON.parse),
  readFile("capacitor.config.json", "utf8").then(JSON.parse),
]);

const readMatch = (source, expression, label) => {
  const match = source.match(expression);
  if (!match) throw new Error("Could not read " + label + " from Android configuration.");
  return match[1];
};

const applicationId = readMatch(gradle, /\bapplicationId\s+["']([^"']+)["']/, "applicationId");
const namespace = readMatch(gradle, /\bnamespace\s*=\s*["']([^"']+)["']/, "namespace");
const actualVersion = readMatch(gradle, /\bversionName\s+["']([^"']+)["']/, "versionName");
const actualCode = Number(readMatch(gradle, /\bversionCode\s+(\d+)/, "versionCode"));
const minSdk = Number(readMatch(variables, /\bminSdkVersion\s*=\s*(\d+)/, "minSdkVersion"));
const compileSdk = Number(readMatch(variables, /\bcompileSdkVersion\s*=\s*(\d+)/, "compileSdkVersion"));
const targetSdk = Number(readMatch(variables, /\btargetSdkVersion\s*=\s*(\d+)/, "targetSdkVersion"));

const errors = [];
if (applicationId !== "com.revelacode.app") errors.push("applicationId changed unexpectedly: " + applicationId);
if (namespace !== "com.revelacode.app") errors.push("namespace changed unexpectedly: " + namespace);
if (capacitor.appId !== "com.revelacode.app") errors.push("Capacitor appId changed unexpectedly: " + capacitor.appId);
if (actualVersion !== expectedVersion) errors.push("versionName is " + actualVersion + "; expected " + expectedVersion);
if (actualCode !== expectedCode) errors.push("versionCode is " + actualCode + "; expected " + expectedCode);
if (packageJson.version !== expectedVersion) errors.push("Android package metadata version is " + packageJson.version + "; expected " + expectedVersion);
if (minSdk !== 24) errors.push("minSdkVersion is " + minSdk + "; expected 24");
if (compileSdk !== 36) errors.push("compileSdkVersion is " + compileSdk + "; expected 36");
if (targetSdk !== 36) errors.push("targetSdkVersion is " + targetSdk + "; expected 36");

if (errors.length) {
  for (const error of errors) console.error("::error::" + error);
  process.exitCode = 1;
} else {
  console.log("Android release identity verified: " + applicationId + " · " + actualVersion + " (" + actualCode + ") · min " + minSdk + " / compile " + compileSdk + " / target " + targetSdk);
}
