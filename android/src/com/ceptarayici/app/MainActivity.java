package com.ceptarayici.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int REQ_FILE = 41;

    private WebView web;
    private ValueCallback<Uri[]> pending;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        cleanFolder("camera");
        cleanFolder("share");

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                try {
                    InputStream in = getAssets().open("www" + path);
                    String mime = mimeOf(path);
                    boolean binary = mime.startsWith("image/") || mime.startsWith("font/");
                    return new WebResourceResponse(mime, binary ? null : "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null,
                            new ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                Intent intent;
                if (params.isCaptureEnabled()) {
                    cameraUri = FilesProvider.uriFor(MainActivity.this, "camera", "foto_" + System.currentTimeMillis() + ".jpg");
                    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    intent.setClipData(ClipData.newRawUri("foto", cameraUri));
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    cameraUri = null;
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    boolean pdf = false;
                    String[] accept = params.getAcceptTypes();
                    if (accept != null) for (String a : accept) {
                        if (a != null && (a.contains("pdf"))) pdf = true;
                    }
                    intent.setType(pdf ? "application/pdf" : "image/*");
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                }
                try {
                    startActivityForResult(intent, REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    pending = null;
                    cb.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, cameraUri != null ? "Kamera uygulaması bulunamadı." : "Dosya seçici açılamadı.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        if (saved != null) web.restoreState(saved);
        else web.loadUrl(START);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || pending == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (cameraUri != null) {
                try {
                    File f = FilesProvider.fileFor(this, cameraUri);
                    if (f.length() > 0) out = new Uri[]{cameraUri};
                } catch (IOException ignored) { }
            } else if (data != null) {
                ClipData cd = data.getClipData();
                if (cd != null && cd.getItemCount() > 0) {
                    out = new Uri[cd.getItemCount()];
                    for (int i = 0; i < cd.getItemCount(); i++) out[i] = cd.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
        }
        pending.onReceiveValue(out);
        pending = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.appBack && window.appBack()) ? 'yes' : 'no'", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (v == null || !v.contains("yes")) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private void cleanFolder(String name) {
        File dir = new File(getCacheDir(), name);
        File[] files = dir.listFiles();
        if (files == null) return;
        long old = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : files) if (f.lastModified() < old) f.delete();
    }

    private static String mimeOf(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webmanifest") || p.endsWith(".json")) return "application/json";
        if (p.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
        if (n.isEmpty()) n = "Tarama";
        if (!n.toLowerCase().endsWith(".pdf")) n += ".pdf";
        return n;
    }

    /** Sayfadaki JavaScript'in çağırdığı yerel işlemler. */
    private class Bridge {

        private File partFile;
        private FileOutputStream partOut;
        private String partName;

        /** Büyük PDF'ler için: yazmaya başla. */
        @JavascriptInterface
        public synchronized boolean beginPdf(String name) {
            closePart();
            partName = safeName(name);
            try {
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                partFile = new File(dir, partName + ".part");
                partOut = new FileOutputStream(partFile);
                return true;
            } catch (IOException e) { closePart(); return false; }
        }

        /** Base64 parçasını dosyanın sonuna ekler. */
        @JavascriptInterface
        public synchronized boolean appendPdf(String base64) {
            if (partOut == null) return false;
            try { partOut.write(Base64.decode(base64, Base64.DEFAULT)); return true; }
            catch (Exception e) { closePart(); return false; }
        }

        /** Dosyayı tamamlar, İndirilenler'e kopyalar ve paylaşım için hazırlar. */
        @JavascriptInterface
        public synchronized String finishPdf() {
            if (partOut == null || partFile == null) return "{\"ok\":false}";
            try { partOut.close(); } catch (IOException e) { closePart(); return "{\"ok\":false}"; }
            partOut = null;
            File done = new File(partFile.getParentFile(), partName);
            done.delete();
            if (!partFile.renameTo(done)) { partFile.delete(); partFile = null; return "{\"ok\":false}"; }
            partFile = null;

            boolean savedToDownloads = false;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    ContentResolver cr = getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, partName);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cep Tarayıcı");
                    Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u != null) {
                        OutputStream os = cr.openOutputStream(u);
                        InputStream in = new java.io.FileInputStream(done);
                        if (os != null) {
                            byte[] buf = new byte[65536]; int n;
                            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                            os.close(); savedToDownloads = true;
                        }
                        in.close();
                    }
                } catch (Exception ignored) { }
            }
            return "{\"ok\":true,\"downloads\":" + savedToDownloads + ",\"name\":\"" + partName.replace("\"", "") + "\"}";
        }

        private void closePart() {
            try { if (partOut != null) partOut.close(); } catch (IOException ignored) { }
            partOut = null;
            if (partFile != null) partFile.delete();
            partFile = null;
        }

        /** PDF'i İndirilenler/Cep Tarayıcı klasörüne kaydeder ve paylaşım için hazırlar. */
        @JavascriptInterface
        public String savePdf(String base64, String name) {
            String file = safeName(name);
            byte[] bytes;
            try { bytes = Base64.decode(base64, Base64.DEFAULT); }
            catch (IllegalArgumentException e) { return "{\"ok\":false}"; }

            boolean savedToDownloads = false;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    ContentResolver cr = getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cep Tarayıcı");
                    Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u != null) {
                        OutputStream os = cr.openOutputStream(u);
                        if (os != null) { os.write(bytes); os.close(); savedToDownloads = true; }
                    }
                } catch (Exception ignored) { }
            }

            boolean cached = false;
            try {
                File f = FilesProvider.fileFor(MainActivity.this, FilesProvider.uriFor(MainActivity.this, "share", file));
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes);
                fo.close();
                cached = true;
            } catch (IOException ignored) { }

            return "{\"ok\":" + (savedToDownloads || cached) + ",\"downloads\":" + savedToDownloads + ",\"name\":\"" + file.replace("\"", "") + "\"}";
        }

        /** Hazırlanan PDF için Android paylaşım menüsünü açar. */
        @JavascriptInterface
        public void sharePdf(String name) {
            final String file = safeName(name);
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Uri u = FilesProvider.uriFor(MainActivity.this, "share", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/pdf");
                    send.putExtra(Intent.EXTRA_STREAM, u);
                    send.setClipData(ClipData.newRawUri(file, u));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(send, "PDF'i paylaş")); }
                    catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "Paylaşacak uygulama bulunamadı.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
}
