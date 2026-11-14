(function () {
  "use strict";

  function byId(id) {
    return document.getElementById(id);
  }

  function formatBytes(bytes) {
    if (!bytes || bytes <= 0) return "";
    var mb = bytes / (1024 * 1024);
    if (mb >= 1) {
      return mb.toFixed(1) + " MB";
    }
    var kb = bytes / 1024;
    return Math.round(kb) + " KB";
  }

  function loadVersionInfo() {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", "version.json?t=" + new Date().getTime(), true);
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          var data = JSON.parse(xhr.responseText);
          if (data.versionName && byId("app-version")) {
            var buildText = data.versionCode ? " (Build " + data.versionCode + ")" : "";
            byId("app-version").textContent = data.versionName + buildText;
          }
          if (data.sizeBytes && byId("apk-size")) {
            byId("apk-size").textContent = formatBytes(data.sizeBytes);
          }
          if (data.minAndroid && byId("min-android")) {
            byId("min-android").textContent = data.minAndroid;
          }
          if (data.abi && byId("apk-abi")) {
            byId("apk-abi").textContent = data.abi;
          }
          if (data.sha256 && byId("sha-display")) {
            byId("sha-display").textContent = "SHA-256: " + data.sha256;
          }
        } catch (e) {
          // Keep HTML fallback values on parse failure.
        }
      }
    };
    xhr.send();
  }

  function verifyApkAvailability() {
    var btn = byId("download-btn");
    var statusEl = byId("download-status");
    if (!btn || !statusEl) return;

    var xhr = new XMLHttpRequest();
    xhr.open("HEAD", btn.getAttribute("href"), true);
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      if (xhr.status >= 200 && xhr.status < 300) {
        var len = parseInt(xhr.getResponseHeader("Content-Length") || "0", 10);
        if (len > 0 && byId("apk-size")) {
          byId("apk-size").textContent = formatBytes(len);
        }
        statusEl.className = "download-status status-ready";
        statusEl.textContent = "ملف Z-Downloader.apk جاهز للتنزيل المباشر.";
      } else if (xhr.status === 404) {
        statusEl.className = "download-status status-warn";
        statusEl.textContent = "ملف Z-Downloader.apk لم يُنشر بعد — شغّل GitHub Actions لبناء النسخة ونشرها.";
      }
    };
    xhr.send();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      loadVersionInfo();
      verifyApkAvailability();
    });
  } else {
    loadVersionInfo();
    verifyApkAvailability();
  }
})();
