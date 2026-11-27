package io.github.k8home;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * test66: 시트(위)·공조(아래) 팝업. ccNC 테마 하단바 가운데나 순정 내비 하단바(미디어 칸 밖)에서 위로 쓸면 아래에서 올라온다.
 * 전체 화면 투명 창(뒤는 HTML 이 어둡게 깔고, 어두운 곳을 누르면 닫힘). 닫기는 아래로 끌기, 바깥 누르기.
 * 창이 떠 있는 동안만 0.7초마다 시트·공조 상태를 다시 읽어 보낸다.
 * 순정 라이브러리 호출(바인더)은 화면을 그리는 메인 스레드를 막지 않도록 전용 작업 스레드에서만 한다.
 */
public final class ClimateSheetService extends Service {
    private static final String TAG = "K8ClimateSheet";
    private static final String PAGE = "file:///android_asset/climate_sheet.html";
    static final String OPEN = "io.github.k8home.action.OPEN_CLIMATE_SHEET";

    private final Handler handler = new Handler();
    private WindowManager windows;
    private WebView web;
    private boolean ready;
    private StockClimate climate;
    private StockCabin cabin;
    private HandlerThread workerThread;
    private Handler worker;

    static void open(Context context) {
        try {
            context.startService(new Intent(context, ClimateSheetService.class).setAction(OPEN));
        } catch (RuntimeException failure) {
            Log.w(TAG, "open failed", failure);
        }
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            push();
            handler.postDelayed(this, 700);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        cabin = new StockCabin(this);
        workerThread = new HandlerThread("K8ClimateSheet");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        climate = new StockClimate(this, new Runnable() {
            @Override public void run() { push(); }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && OPEN.equals(intent.getAction()) && web == null) show();
        return START_NOT_STICKY;
    }

    private void show() {
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setBackgroundColor(0);
        web.addJavascriptInterface(new Bridge(), "K8Climate");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) {
                if (view != web) return;
                ready = true;
                push();
            }
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                // 서비스 창은 이 깃발이 없으면 CPU 로 그려 올라오는 움직임이 끊긴다(test62 재생 시트와 같다).
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("K8HomeClimateSheet");
        try {
            windows.addView(web, params);
            climate.start();
            worker.post(new Runnable() { @Override public void run() { cabin.listen(true); cabin.listenVr(true); } });
            cabin.useDatc(climate.manager());
            web.loadUrl(PAGE);
            handler.post(poll);
        } catch (RuntimeException failure) {
            Log.w(TAG, "sheet window unavailable", failure);
            close();
        }
    }

    private volatile JSONObject climateState = new JSONObject();
    private String lastState = "";

    private void push() {
        if (web == null || !ready) return;
        climateState = climate.snapshot();   // 리스너가 메인에서 채운 값이라 메인에서 읽는다
        worker.removeCallbacks(read);         // 밀린 읽기는 하나로 합친다
        worker.post(read);
    }

    private final Runnable read = new Runnable() {
        @Override public void run() {
            JSONObject state = cabin.snapshot();
            try {
                if (!state.has("climate")) state.put("climate", climateState);
            } catch (JSONException ignored) { }
            String text = state.toString();
            if (!text.equals(lastState)) { lastState = text; Log.i(TAG, "state " + text); }   // 차에서 읽은 값 확인용
            final String script = "window.K8Sheet && window.K8Sheet.state(" + JSONObject.quote(text) + ")";
            handler.post(new Runnable() {
                @Override public void run() {
                    if (web != null && ready) web.evaluateJavascript(script, null);
                }
            });
        }
    };

    private void close() {
        handler.removeCallbacks(poll);
        if (climate != null) climate.stop();
        if (web != null && worker != null) worker.post(new Runnable() { @Override public void run() { cabin.listen(false); cabin.listenVr(false); } });
        if (web != null) {
            WebView view = web;
            web = null;
            ready = false;
            try { windows.removeViewImmediate(view); } catch (IllegalArgumentException ignored) { }
            view.removeJavascriptInterface("K8Climate");
            view.destroy();
        }
        stopSelf();
    }

    private void handle(final String action, final int value) {
        if ("close".equals(action)) { close(); return; }
        worker.post(new Runnable() { @Override public void run() { send(action, value); } });
        handler.postDelayed(new Runnable() { @Override public void run() { push(); } }, 250);
    }

    /** 작업 스레드에서만 부른다. */
    private void send(String action, int value) {
        // 뒷유리 열선은 K8 공조 번호를 아직 몰라 넣지 않았다(차에서 확인 뒤 추가).
        boolean heat = action.endsWith("-heat");
        if (action.startsWith("seat-")) {
            String which = action.substring(5, action.lastIndexOf('-'));
            cabin.seat(which, heat, value);
        } else if ("wheel".equals(action)) {
            cabin.wheel();
        } else if ("auto".equals(action)) {
            cabin.climate(StockCabin.C_AUTO, 1);   // K8: AUTO 는 누름(1)만 받는다. 0 은 무시됨(2026-11-27 차). 다시 누르면 단계가 돈다
        } else if ("ac".equals(action)) {
            cabin.climate(StockCabin.C_AC, value == 1 ? 0 : 1);
        } else if ("defog".equals(action)) {
            cabin.climate(StockCabin.C_FRONT_DEFOG, value == 1 ? 0 : 1);
        } else if ("intake".equals(action)) {
            cabin.climate(StockCabin.C_INTAKE, value == 1 ? 0 : 1);
        } else if ("sync".equals(action)) {
            cabin.climate(StockCabin.C_SYNC, value == 1 ? 1 : 2);   // K8: SYNC 켜기 2, 끄기 1(0 은 무시, 2026-11-27 차)
        } else if ("aircln".equals(action)) {
            cabin.climate(StockCabin.C_AIR_CLEANING, value == 1 ? 0 : 1);
        } else if ("mode".equals(action)) {   // 바람 방향: 1 정면, 2 정면+발, 3 발, 4 발+앞유리, 5 앞유리
            if (value >= 1 && value <= 5) cabin.climate(StockCabin.C_BLOWER_MODE, value);
        } else if ("off".equals(action)) {
            cabin.climate(StockCabin.C_OFF_REQ, 1);
        } else if ("fan".equals(action)) {   // value = 원하는 바람 단계 1..8, 순정은 2..9 를 받는다
            if (value >= 1 && value <= 8) cabin.climate(StockCabin.C_BLOWER_LEVEL, value + 1);
        } else if ("temp-dr".equals(action) || "temp-ps".equals(action)) {   // value 1 = 올림, 2 = 내림
            if (value == 1 || value == 2) cabin.climate("temp-dr".equals(action) ? StockCabin.C_TEMP_DR_UPDN : StockCabin.C_TEMP_PS_UPDN, value);
        }
    }

    private final class Bridge {
        @JavascriptInterface public void action(final String action, final int value) {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (web != null) handle(action, value);
                }
            });
        }
    }

    @Override public void onDestroy() {
        close();
        workerThread.quit();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
