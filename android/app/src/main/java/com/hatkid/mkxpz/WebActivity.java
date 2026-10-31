package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.AtomicFile;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.RelativeLayout;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental offline MV host. Game resources remain inside the APK; saves are private files. */
public final class WebActivity extends Activity implements MotorJuego {
    private static final String HOST="portemon.invalid", ORIGIN="https://"+HOST+"/";
    private WebView web;
    private OptionsMenu menu;
    private File saves;
    private String root="";
    private int speed=1, stretch, target;
    private volatile int drawnFrames;
    private boolean ready;
    private final Set<Integer> keys=new HashSet<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        hideBars();
        saves=new File(getFilesDir(),"mv-save");
        if(!saves.isDirectory() && !saves.mkdirs()) { fail("No se pudo abrir la carpeta de partidas."); return; }
        try(InputStream in=getAssets().open("game/portemon_runtime.json")) {
            root=new JSONObject(read(in)).optString("root");
            if(!root.equals("") && !root.equals("www/"))throw new IOException("Raíz MV inválida");
        } catch(Exception e) { fail(e.getMessage()); return; }
        RelativeLayout layout=new RelativeLayout(this);
        web=new WebView(this);
        layout.addView(web,new RelativeLayout.LayoutParams(-1,-1));
        setContentView(layout);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true); // Only our intercepted APK origin can reach the save bridge.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new SaveBridge(),"PortemonAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                android.util.Log.i("Portemon-MV",m.sourceId()+":"+m.lineNumber()+" "+m.message());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {
                return !local(r.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r) {
                return resource(r);
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                ready=false; keys.clear();
            }
            @Override public void onPageFinished(WebView v,String url) {
                if(!url.equals(ORIGIN+root+"index.html"))return;
                ready=true;
                js("window.PortemonMV && PortemonMV.configure("+speed+","+stretch+","+target+")");
                web.requestFocus();
            }
        });
        menu=new OptionsMenu(this,saves.getAbsolutePath(),this);
        menu.attachTo(layout);
        menu.partidas.sincronizar();
        menu.partidas.vigilar();
        menu.partidas.pedirCarpeta(true);
        web.loadUrl(ORIGIN+root+"index.html");
    }

    private static boolean local(android.net.Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && uri.getPort()==-1;
    }
    private WebResourceResponse resource(WebResourceRequest request) {
        try {
            if(!local(request.getUrl()) || !"GET".equals(request.getMethod()))return error(403,"Forbidden");
            String path=request.getUrl().getPath();
            if(path==null || path.contains("\\") || path.indexOf('\0')>=0)return error(403,"Forbidden");
            for(String part:path.split("/"))if(part.equals("..") || part.equals("."))return error(403,"Forbidden");
            if(path.equals("/portemon/mv.js"))
                return new WebResourceResponse("application/javascript","UTF-8",getAssets().open("portemon/mv.js"));
            String relative=path.substring(1);
            InputStream stream=getAssets().open("game/"+relative);
            String ext=android.webkit.MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase(Locale.ROOT);
            String mime=ext.equals("js")?"application/javascript":ext.equals("json")?"application/json":
                ext.equals("html")?"text/html":android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if(mime==null)mime="application/octet-stream";
            if(relative.equals(root+"index.html")) {
                String html=read(stream);
                String bridge="<script src=\"/portemon/mv.js\"></script>";
                java.util.regex.Matcher head=java.util.regex.Pattern.compile("(?i)<head\\b[^>]*>").matcher(html);
                html=head.find()?html.substring(0,head.end())+bridge+html.substring(head.end()):bridge+html;
                stream=new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
            }
            Map<String,String> headers=new HashMap<>();
            headers.put("Content-Security-Policy","frame-src 'none'; object-src 'none'");
            return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,stream);
        } catch(IOException e) { return error(404,"Not Found"); }
    }
    private static WebResourceResponse error(int status,String reason) {
        return new WebResourceResponse("text/plain","UTF-8",status,reason,Collections.emptyMap(),new ByteArrayInputStream(reason.getBytes(StandardCharsets.UTF_8)));
    }
    private static String read(InputStream stream) throws IOException {
        try(InputStream in=stream;ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)bytes.write(buf,0,n);
            return bytes.toString("UTF-8");
        }
    }
    private void fail(String text) {
        new AlertDialog.Builder(this).setTitle("RPG Maker MV").setMessage(text)
            .setPositiveButton(android.R.string.ok,(d,w)->finish()).show();
    }
    private void js(String script) { if(web!=null && ready)web.evaluateJavascript(script,null); }

    /** No paths from JavaScript: only MV's conventional save names can be accessed. */
    public final class SaveBridge {
        private File file(String name) {
            if(name==null || !name.matches("(?:config|global|file[0-9]{1,6})\\.rpgsave(?:\\.bak)?"))
                throw new IllegalArgumentException("Nombre de partida inválido");
            return new File(saves,name);
        }
        @JavascriptInterface public synchronized String load(String name) {
            AtomicFile file=new AtomicFile(file(name));
            try(InputStream in=file.openRead()) { return read(in); }
            catch(FileNotFoundException e) { return null; }
            catch(IOException e) { throw new IllegalStateException("No se pudo leer la partida",e); }
        }
        @JavascriptInterface public synchronized boolean save(String name,String data) {
            if(data==null)return false;
            AtomicFile file=new AtomicFile(file(name)); FileOutputStream out=null;
            try {
                out=file.startWrite();out.write(data.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
                runOnUiThread(()->{if(menu!=null)menu.partidas.sincronizar();});
                return true;
            } catch(IOException e) { if(out!=null)file.failWrite(out);return false; }
        }
        @JavascriptInterface public synchronized void remove(String name) {
            new AtomicFile(file(name)).delete();
            if(menu!=null && menu.partidas.carpetaPermitida())new File(menu.partidas.carpeta(),name).delete();
        }
        @JavascriptInterface public void frames(int value) { drawnFrames=value; }
    }

    public boolean web() { return true; }
    public int frames() { return drawnFrames; }
    public void speed(int value) { speed=value;configure(); }
    public void stretch(int value) { stretch=value;configure(); }
    public void fps(int value,int hz) { target=value;configure(); }
    private void configure() { if(menu!=null && (menu.isOpen() || menu.controls.isEditing() || speed==0))releaseKeys();js("window.PortemonMV && PortemonMV.configure("+speed+","+stretch+","+target+")"); }
    public void keyDown(int code) {
        keys.add(code); int mapped=keyCode(code);
        if(mapped!=0)js("window.PortemonMV && PortemonMV.key("+mapped+",true)");
    }
    public void keyUp(int code) {
        keys.remove(code);int mapped=keyCode(code);
        if(mapped!=0)js("window.PortemonMV && PortemonMV.key("+mapped+",false)");
    }
    public void text(String value) { js("window.PortemonMV && PortemonMV.text("+JSONObject.quote(value)+")"); }
    private void releaseKeys() { for(int key:new HashSet<>(keys))keyUp(key);js("window.Input && Input.clear()"); }
    private static int keyCode(int c) {
        if(c>=KeyEvent.KEYCODE_A && c<=KeyEvent.KEYCODE_Z)return 65+c-KeyEvent.KEYCODE_A;
        if(c>=KeyEvent.KEYCODE_0 && c<=KeyEvent.KEYCODE_9)return 48+c-KeyEvent.KEYCODE_0;
        if(c>=KeyEvent.KEYCODE_F1 && c<=KeyEvent.KEYCODE_F12)return 112+c-KeyEvent.KEYCODE_F1;
        if(c>=KeyEvent.KEYCODE_NUMPAD_0 && c<=KeyEvent.KEYCODE_NUMPAD_9)return 96+c-KeyEvent.KEYCODE_NUMPAD_0;
        switch(c) {
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:return 13;
            case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_BACK:return 27;
            case KeyEvent.KEYCODE_DEL:return 8;
            case KeyEvent.KEYCODE_TAB:return 9;
            case KeyEvent.KEYCODE_SPACE:return 32;
            case KeyEvent.KEYCODE_DPAD_LEFT:return 37;case KeyEvent.KEYCODE_DPAD_UP:return 38;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return 39;case KeyEvent.KEYCODE_DPAD_DOWN:return 40;
            case KeyEvent.KEYCODE_SHIFT_LEFT:case KeyEvent.KEYCODE_SHIFT_RIGHT:return 16;
            case KeyEvent.KEYCODE_CTRL_LEFT:case KeyEvent.KEYCODE_CTRL_RIGHT:return 17;
            case KeyEvent.KEYCODE_ALT_LEFT:case KeyEvent.KEYCODE_ALT_RIGHT:return 18;
            case KeyEvent.KEYCODE_PAGE_UP:return 33;case KeyEvent.KEYCODE_PAGE_DOWN:return 34;
            case KeyEvent.KEYCODE_MOVE_END:return 35;case KeyEvent.KEYCODE_MOVE_HOME:return 36;
            case KeyEvent.KEYCODE_INSERT:return 45;case KeyEvent.KEYCODE_FORWARD_DEL:return 46;
            case KeyEvent.KEYCODE_COMMA:return 188;case KeyEvent.KEYCODE_PERIOD:return 190;
            case KeyEvent.KEYCODE_MINUS:return 189;case KeyEvent.KEYCODE_EQUALS:return 187;
            case KeyEvent.KEYCODE_SLASH:return 191;case KeyEvent.KEYCODE_BACKSLASH:return 220;
            case KeyEvent.KEYCODE_SEMICOLON:return 186;case KeyEvent.KEYCODE_APOSTROPHE:return 222;
            case KeyEvent.KEYCODE_LEFT_BRACKET:return 219;case KeyEvent.KEYCODE_RIGHT_BRACKET:return 221;
            case KeyEvent.KEYCODE_GRAVE:return 192;case KeyEvent.KEYCODE_CAPS_LOCK:return 20;
            case KeyEvent.KEYCODE_NUM_LOCK:return 144;case KeyEvent.KEYCODE_SCROLL_LOCK:return 145;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:return 111;case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:return 106;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:return 109;case KeyEvent.KEYCODE_NUMPAD_ADD:return 107;
            case KeyEvent.KEYCODE_NUMPAD_DOT:return 110;case KeyEvent.KEYCODE_NUMPAD_COMMA:return 188;
            case KeyEvent.KEYCODE_NUMPAD_EQUALS:return 187;case KeyEvent.KEYCODE_BREAK:return 19;
            case KeyEvent.KEYCODE_SYSRQ:return 44;case KeyEvent.KEYCODE_META_LEFT:return 91;
            case KeyEvent.KEYCODE_META_RIGHT:return 92;default:return 0;
        }
    }
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(5894);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused) { hideBars();if(menu!=null){menu.onBackground(false);menu.refreshDisplay();} }
        else releaseKeys();
    }
    @Override protected void onPause() {
        if(menu!=null){menu.onBackground(true);menu.partidas.sincronizar();}
        releaseKeys();if(web!=null)web.onPause();super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();if(web!=null)web.onResume();
        if(menu!=null)menu.partidas.ofrecerReinicio(menu.partidas.sincronizar());
    }
    @Override protected void onDestroy() {
        if(menu!=null)menu.dispose();
        if(web!=null){web.removeJavascriptInterface("PortemonAndroid");web.destroy();web=null;}
        super.onDestroy();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        if(menu!=null && (menu.partidas.onResult(request,result,data)||menu.perfil.onResult(request,result,data)||menu.registro.onResult(request,result,data)))return;
        super.onActivityResult(request,result,data);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(menu==null)return super.dispatchKeyEvent(e);
        int c=e.getKeyCode();boolean pad=com.hatkid.mkxpz.controles.PadMapper.isGamepad(e.getDevice());
        if(c==KeyEvent.KEYCODE_VOLUME_UP || c==KeyEvent.KEYCODE_VOLUME_DOWN || c==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(e);
        if(menu.captureKey(e))return true;
        if(c==KeyEvent.KEYCODE_BACK && !pad) {
            if(e.getAction()==KeyEvent.ACTION_UP) {
                if(PanelLateral.dismissOpen(this))return true;
                if(menu.teclado.shown())menu.teclado.hide();else if(!menu.controls.isEditing()){releaseKeys();menu.toggle();}
            }return true;
        }
        if(e.getAction()==KeyEvent.ACTION_UP && keys.contains(c) && (menu.isOpen() || menu.controls.isEditing()))keyUp(c);
        if(menu.isOpen() || menu.controls.isEditing()) {
            if(pad && (c==menu.pad.cancelButton() || c==KeyEvent.KEYCODE_BACK)) {
                if(e.getAction()==KeyEvent.ACTION_UP && !PanelLateral.dismissOpen(this))menu.close();return true;
            }
            return getWindow().superDispatchKeyEvent(menu.navigationKey(e));
        }
        InputDevice device=e.getDevice();if(device!=null && !device.isVirtual())menu.controls.onPhysicalInput();
        if(menu.pad.onKey(e))return true;
        // Let WebView perform real text editing; canvas games receive our translated PC keys.
        if(menu.teclado.shown())return getWindow().superDispatchKeyEvent(e);
        if(e.getAction()==KeyEvent.ACTION_DOWN)keyDown(c);else if(e.getAction()==KeyEvent.ACTION_UP)keyUp(c);
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if(menu!=null && menu.teclado.onOutsideTouch(e))return true;
        if(menu!=null && !menu.teclado.shown() && e.getActionMasked()==MotionEvent.ACTION_DOWN)menu.controls.onScreenTouched();
        return super.dispatchTouchEvent(e);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if(menu!=null && com.hatkid.mkxpz.controles.PadMapper.isGamepad(e.getDevice())) {
            if(menu.isOpen() || menu.controls.isEditing()){menu.menuMotion(e);return true;}
            menu.controls.onPhysicalInput();return menu.pad.onMotion(e);
        }return super.dispatchGenericMotionEvent(e);
    }
}
