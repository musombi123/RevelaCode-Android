import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const ROOT = process.cwd();
const TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713";
const APP_ID_KEY = "VITE_ADMOB_ANDROID_APP_ID";
const UNIT_ID_KEYS = [
  "VITE_ADMOB_COMMUNITY_NATIVE_AD_UNIT_ID",
  "VITE_ADMOB_COMMUNITY_OVERVIEW_NATIVE_AD_UNIT_ID",
  "VITE_ADMOB_FAITH_NATIVE_AD_UNIT_ID",
];

function parseDotEnv(text, key) {
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const equalsAt = trimmed.indexOf("=");
    if (equalsAt < 0) continue;
    if (trimmed.slice(0, equalsAt).trim() !== key) continue;

    let value = trimmed.slice(equalsAt + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    } else {
      value = value.replace(/\s+#.*$/, "").trim();
    }
    return value;
  }
  return "";
}

async function readConfigValue(key) {
  if (process.env[key]?.trim()) return process.env[key].trim();
  try {
    const envText = await readFile(resolve(ROOT, ".env"), "utf8");
    return parseDotEnv(envText, key);
  } catch {
    return "";
  }
}

function escapeXml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

async function main() {
  const configuredAppId = await readConfigValue(APP_ID_KEY);
  const configuredUnits = await Promise.all(
    UNIT_ID_KEYS.map(async (key) => ({
      key,
      value: await readConfigValue(key),
    })),
  );
  const hasConfiguredUnit = configuredUnits.some((item) => Boolean(item.value));
  const useProductionIds = Boolean(configuredAppId && hasConfiguredUnit);
  const appId = useProductionIds ? configuredAppId : TEST_APP_ID;

  if (
    configuredAppId &&
    !/^ca-app-pub-\d+~\d+$/.test(configuredAppId)
  ) {
    throw new Error(
      `${APP_ID_KEY} must be a valid AdMob application ID (format ca-app-pub-...~...).`,
    );
  }

  for (const item of configuredUnits) {
    if (item.value && !/^ca-app-pub-\d+\/\d+$/.test(item.value)) {
      throw new Error(
        `${item.key} must be a valid AdMob ad unit ID (format ca-app-pub-.../...).`,
      );
    }
  }

  const manifestPath = resolve(
    ROOT,
    "android/app/src/main/AndroidManifest.xml",
  );
  const stringsPath = resolve(
    ROOT,
    "android/app/src/main/res/values/strings.xml",
  );

  let manifest;
  try {
    manifest = await readFile(manifestPath, "utf8");
  } catch {
    throw new Error(
      "Android project not found. Run 'npx cap sync android' before configuring AdMob.",
    );
  }

  const metaTag =
    '<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="@string/admob_app_id" />';
  const metaPattern =
    /<meta-data\b(?=[^>]*\bandroid:name=["']com\.google\.android\.gms\.ads\.APPLICATION_ID["'])[^>]*\/?\s*>/s;

  if (metaPattern.test(manifest)) {
    manifest = manifest.replace(metaPattern, metaTag);
  } else {
    const applicationOpen = /<application\b[^>]*>/i;
    if (!applicationOpen.test(manifest)) {
      throw new Error("Could not locate the <application> element in AndroidManifest.xml.");
    }
    manifest = manifest.replace(
      applicationOpen,
      (openingTag) => `${openingTag}\n        ${metaTag}`,
    );
  }
  await writeFile(manifestPath, manifest, "utf8");

  let stringsXml;
  try {
    stringsXml = await readFile(stringsPath, "utf8");
  } catch {
    stringsXml = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n';
  }

  const appIdLine = `    <string name="admob_app_id">${escapeXml(appId)}</string>`;
  const appIdStringPattern =
    /<string\b(?=[^>]*\bname=["']admob_app_id["'])[^>]*>[\s\S]*?<\/string>/;

  if (appIdStringPattern.test(stringsXml)) {
    stringsXml = stringsXml.replace(appIdStringPattern, appIdLine);
  } else {
    stringsXml = stringsXml.replace(
      /<\/resources>/i,
      `  \n${appIdLine}\n</resources>`,
    );
  }
  await writeFile(stringsPath, stringsXml, "utf8");

  console.log(
    useProductionIds
      ? `AdMob Android manifest configured with your production App ID and ${configuredUnits.filter((item) => item.value).length} production native ad unit ID(s). Any unconfigured placement remains in test mode.`
      : "AdMob Android manifest configured with Google's test application ID. Configure the production App ID and at least one native ad unit to enable production inventory.",
  );
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
