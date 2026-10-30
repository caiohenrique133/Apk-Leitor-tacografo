package pt.caio.tacografo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * App Android do Controle de Tacógrafo.
 * Mostra a app publicada no Vercel e dá-lhe acesso nativo ao leitor de cartões USB
 * (window.AndroidUsb), que o Chrome bloqueia em páginas web.
 */
public class MainActivity extends Activity {

    static final String URL = "https://apk-leitor-tacografo.vercel.app/";
    static final String HOST = "apk-leitor-tacografo.vercel.app";
    static final String ACTION_PERM = "pt.caio.tacografo.USB_PERMISSION";
    static final int REQ_FILE = 41;
    static final int REQ_CAM = 42;
    static final int REQ_PERM_CAM = 43;
    PermissionRequest pedidoCam;
    static final String PASTA_FOTOS = "Discos Tacografo";

    WebView web;
    ValueCallback<Uri[]> fileCb;
    Uri camUri;
    String fotoSub = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false); // pré-visualização da câmara dentro da app

        web.addJavascriptInterface(new UsbPonte(), "AndroidUsb");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl("file:///android_asset/offline.html");
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // câmara integrada: a página pede a câmara (getUserMedia) e a app autoriza só o seu próprio site
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean video = false;
                    for (String r : req.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) video = true;
                    if (!video || req.getOrigin() == null || !HOST.equals(req.getOrigin().getHost())) { req.deny(); return; }
                    if (temCamara()) { req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); return; }
                    if (pedidoCam != null) pedidoCam.deny();
                    pedidoCam = req;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM_CAM);
                });
            }
            @Override
            public void onPermissionRequestCanceled(PermissionRequest req) {
                if (pedidoCam == req) pedidoCam = null;
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                boolean imagem = false;
                for (String a : p.getAcceptTypes()) if (a != null && a.startsWith("image")) imagem = true;
                // "Tirar foto" na aba Discos: abre a câmara e grava em Imagens/Discos Tacografo/AAAA-MM
                if (imagem && p.isCaptureEnabled() && abreCamara()) return true;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(imagem ? "image/*" : "*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(Intent.createChooser(i, "Escolher ficheiros"), REQ_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });

        if (b != null) { web.restoreState(b); camUri = b.getParcelable("camUri"); }
        else web.loadUrl(URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
        if (camUri != null) out.putParcelable("camUri", camUri);
    }

    String pastaRelativa(String sub) {
        String s = sub == null ? "" : sub.replaceAll("[^0-9A-Za-z_-]", "");
        return Environment.DIRECTORY_PICTURES + "/" + PASTA_FOTOS + (s.isEmpty() ? "" : "/" + s);
    }

    boolean abreCamara() {
        if (Build.VERSION.SDK_INT < 29) return false; // nos Android antigos fica a galeria
        if (!temCamara()) { requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM_CAM); return false; }
        try {
            String nome = "disco_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, nome);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, pastaRelativa(fotoSub));
            camUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (camUri == null) return false;
            Intent c = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            c.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
            c.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(c, REQ_CAM);
            return true;
        } catch (Exception e) {
            if (camUri != null) try { getContentResolver().delete(camUri, null, null); } catch (Exception x) { }
            camUri = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_CAM) {
            Uri u = camUri; camUri = null;
            boolean ok = res == RESULT_OK && u != null;
            if (!ok && u != null) try { getContentResolver().delete(u, null, null); } catch (Exception e) { }
            if (fileCb != null) { fileCb.onReceiveValue(ok ? new Uri[]{u} : null); fileCb = null; }
            return;
        }
        if (req != REQ_FILE || fileCb == null) { super.onActivityResult(req, res, data); return; }
        Uri[] r = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                r = new Uri[n];
                for (int k = 0; k < n; k++) r[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) {
                r = new Uri[]{data.getData()};
            }
        }
        fileCb.onReceiveValue(r);
        fileCb = null;
    }

    boolean temCamara() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req != REQ_PERM_CAM || pedidoCam == null) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
            pedidoCam.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        else pedidoCam.deny();
        pedidoCam = null;
    }

    @android.annotation.SuppressLint({"MissingSuperCall", "GestureBackNavigation"})
    @Override
    public void onBackPressed() {
        // o botão "voltar" fecha primeiro a câmara / visualizador da página
        web.evaluateJavascript("window.voltarApp?window.voltarApp():false", v -> {
            if ("true".equals(v)) return;
            if (web.canGoBack()) web.goBack(); else finish();
        });
    }

    /* ============ Ponte para a página: window.AndroidUsb ============ */
    class UsbPonte {
        UsbDeviceConnection con;
        UsbInterface itf;
        UsbEndpoint epIn, epOut;

        UsbManager um() { return (UsbManager) getSystemService(Context.USB_SERVICE); }

        @JavascriptInterface
        public String list() {
            JSONArray a = new JSONArray();
            try {
                for (UsbDevice d : um().getDeviceList().values()) {
                    JSONObject o = new JSONObject();
                    o.put("id", d.getDeviceId());
                    String nome = null;
                    try { nome = d.getProductName(); } catch (Exception e) { }
                    if (nome != null) o.put("nome", nome);
                    o.put("vid", String.format("%04X", d.getVendorId()));
                    o.put("pid", String.format("%04X", d.getProductId()));
                    a.put(o);
                }
            } catch (Exception e) { }
            return a.toString();
        }

        boolean pedePermissao(UsbDevice d) throws InterruptedException {
            if (um().hasPermission(d)) return true;
            final CountDownLatch fim = new CountDownLatch(1);
            final boolean[] ok = {false};
            BroadcastReceiver rx = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    ok[0] = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    fim.countDown();
                }
            };
            IntentFilter f = new IntentFilter(ACTION_PERM);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(rx, f);
            Intent pi = new Intent(ACTION_PERM).setPackage(getPackageName());
            int fl = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            um().requestPermission(d, PendingIntent.getBroadcast(MainActivity.this, 0, pi, fl));
            fim.await(120, TimeUnit.SECONDS);
            try { unregisterReceiver(rx); } catch (Exception e) { }
            return ok[0] || um().hasPermission(d);
        }

        @JavascriptInterface
        public String open(int id) {
            JSONObject r = new JSONObject();
            try {
                close();
                UsbDevice dev = null;
                for (UsbDevice d : um().getDeviceList().values()) if (d.getDeviceId() == id) dev = d;
                if (dev == null) return erro(r, "O leitor já não está ligado.");
                if (!pedePermissao(dev)) return erro(r, "Sem autorização para usar o leitor. Toca em OK quando o Android perguntar.");

                // escolhe a interface com 2 endpoints bulk; prefere a de smart card (0x0B)
                UsbInterface melhor = null;
                for (int k = 0; k < dev.getInterfaceCount(); k++) {
                    UsbInterface i = dev.getInterface(k);
                    int bulks = 0;
                    for (int e = 0; e < i.getEndpointCount(); e++)
                        if (i.getEndpoint(e).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) bulks++;
                    if (bulks < 2) continue;
                    if (melhor == null || i.getInterfaceClass() == 0x0B) melhor = i;
                }
                if (melhor == null) return erro(r, "Este aparelho não tem interface de dados — não parece ser um leitor de cartões.");

                UsbDeviceConnection c = um().openDevice(dev);
                if (c == null) return erro(r, "Não consegui abrir o leitor.");
                if (!c.claimInterface(melhor, true)) { c.close(); return erro(r, "O leitor está ocupado por outra app."); }

                epIn = null; epOut = null;
                for (int e = 0; e < melhor.getEndpointCount(); e++) {
                    UsbEndpoint ep = melhor.getEndpoint(e);
                    if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) { if (epIn == null) epIn = ep; }
                    else if (epOut == null) epOut = ep;
                }
                con = c; itf = melhor;
                String nome = null;
                try { nome = dev.getProductName(); } catch (Exception e) { }
                r.put("ok", true);
                r.put("nome", nome != null ? nome : "Leitor USB");
                r.put("itf", melhor.getId());
                r.put("cls", melhor.getInterfaceClass());
            } catch (Exception e) {
                return erro(r, "Erro: " + e.getMessage());
            }
            return r.toString();
        }

        String erro(JSONObject r, String m) {
            try { r.put("ok", false); r.put("erro", m); } catch (Exception e) { }
            return r.toString();
        }

        @JavascriptInterface
        public boolean out(String b64) {
            if (con == null || epOut == null) return false;
            byte[] d = Base64.decode(b64, Base64.DEFAULT);
            return con.bulkTransfer(epOut, d, d.length, 5000) >= 0;
        }

        @JavascriptInterface
        public String inp(int timeout) {
            if (con == null || epIn == null) return "";
            byte[] buf = new byte[16384];
            int n = con.bulkTransfer(epIn, buf, buf.length, timeout);
            if (n <= 0) return "";
            return Base64.encodeToString(buf, 0, n, Base64.NO_WRAP);
        }

        @JavascriptInterface
        public String descritores() {
            if (con == null) return "";
            byte[] d = con.getRawDescriptors();
            return d == null ? "" : Base64.encodeToString(d, Base64.NO_WRAP);
        }

        @JavascriptInterface
        public void close() {
            try { if (con != null && itf != null) con.releaseInterface(itf); } catch (Exception e) { }
            try { if (con != null) con.close(); } catch (Exception e) { }
            con = null; itf = null; epIn = null; epOut = null;
        }

        /* Guarda um ficheiro (.ddd, .csv, .xlsx, .json) na pasta Transferências. */
        @JavascriptInterface
        public String guardar(String nome, String b64) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, nome);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(d); }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    try (FileOutputStream o = new FileOutputStream(new File(dir, nome))) { o.write(d); }
                }
                return "Guardado em Transferências: " + nome;
            } catch (Exception e) {
                return "Não consegui guardar o ficheiro: " + e.getMessage();
            }
        }

        /* Mês da próxima foto tirada com a câmara (subpasta AAAA-MM). */
        @JavascriptInterface
        public void pastaFoto(String sub) { fotoSub = sub == null ? "" : sub; }

        /* Guarda uma foto de disco em Imagens/Discos Tacografo/AAAA-MM. */
        @JavascriptInterface
        public String guardarFoto(String nome, String sub, String b64) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                String n = (nome == null || nome.isEmpty() ? "disco.jpg" : nome).replaceAll("[^0-9A-Za-z_.-]", "_");
                String rel = pastaRelativa(sub);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, n);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, rel);
                    Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(d); }
                } else {
                    File dir = new File(Environment.getExternalStorageDirectory(), rel);
                    dir.mkdirs();
                    try (FileOutputStream o = new FileOutputStream(new File(dir, n))) { o.write(d); }
                }
                return "Guardado em " + rel.replace(Environment.DIRECTORY_PICTURES, "Imagens").replace("/", " › ");
            } catch (Exception e) {
                return "Não consegui guardar a foto: " + e.getMessage();
            }
        }

        /* Dossier de fiscalização → imprimir ou guardar em PDF. */
        @JavascriptInterface
        public void imprimir() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                pm.print("Dossier tacógrafo", web.createPrintDocumentAdapter("Dossier tacógrafo"),
                        new PrintAttributes.Builder().build());
            });
        }
    }
}
