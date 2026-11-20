package io.github.k8home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    private static final ComponentName NAVIGATION = new ComponentName("com.mnsoft.navi", "com.mnsoft.navi.NaviApp");
    private static final String[] SHORTCUTS = {"media", "phone", "radio", "projection", "settings"};
    private static final String[] LABELS = {"미디어", "전화", "라디오", "폰 프로젝션", "설정"};
    private FrameLayout root;
    private FrameLayout mapSlot;
    private WebView web;
    private SharedPreferences shortcuts;
    private boolean pageReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        shortcuts = getSharedPreferences("shortcuts", MODE_PRIVATE);
        root = new FrameLayout(this);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        web.setBackgroundColor(0xff0c121b);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new HomeBridge(), "K8Host");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (PAGE.equals(url)) {
                    pageReady = true;
                    syncMapContent();
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        mapSlot = new FrameLayout(this);
        mapSlot.setVisibility(View.GONE);
        root.addView(mapSlot, new FrameLayout.LayoutParams(0, 0));
        setContentView(root);
        web.loadUrl(PAGE);
    }

    /** Runs on the UI thread. Pass a detached map view, or null to restore the empty state. */
    void setMapContent(View content) {
        if (content != null && content.getParent() == mapSlot) return;
        if (content != null && content.getParent() != null) {
            throw new IllegalArgumentException("Map view already has a parent");
        }
        mapSlot.removeAllViews();
        mapSlot.setVisibility(View.GONE);
        if (content != null) mapSlot.addView(content, new FrameLayout.LayoutParams(-1, -1));
        syncMapContent();
    }

    private void syncMapContent() {
        if (pageReady) web.evaluateJavascript(
            "window.K8Home.mapAttached(" + (mapSlot.getChildCount() != 0) + ")", null);
    }

    public final class HomeBridge {
        @JavascriptInterface public void performAction(final String action) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!isFinishing() && !isDestroyed()) handleAction(action);
                }
            });
        }

        @JavascriptInterface public void updateBounds(final int viewport, final int left, final int top,
                                                       final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    if (viewport <= 0 || width <= 0 || height <= 0 || left < 0 || top < 0) {
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    float scale = web.getWidth() / (float) viewport;
                    int x = Math.round(left * scale);
                    int y = Math.round(top * scale);
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (w <= 0 || h <= 0) {
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(w, h);
                    bounds.leftMargin = x;
                    bounds.topMargin = y;
                    mapSlot.setLayoutParams(bounds);
                    mapSlot.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void handleAction(String action) {
        if ("exit".equals(action)) {
            finish();
        } else if ("navi".equals(action)) {
            if (!open(NAVIGATION)) toast("이 기기에서 순정 내비게이션을 열 수 없습니다.");
        } else if ("configure".equals(action)) {
            new AlertDialog.Builder(this).setTitle("바로가기 설정")
                .setItems(LABELS, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                        chooseApp(index, false);
                    }
                })
                .setNegativeButton("닫기", null).show();
        } else {
            for (int i = 0; i < SHORTCUTS.length; i++) {
                if (!SHORTCUTS[i].equals(action)) continue;
                ComponentName saved = ComponentName.unflattenFromString(shortcuts.getString(action, ""));
                if (saved == null || !open(saved)) chooseApp(i, true);
                return;
            }
        }
    }

    private boolean open(ComponentName component) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void chooseApp(final int index, final boolean launch) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> entries = new ArrayList<>(pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Collections.sort(entries, new ResolveInfo.DisplayNameComparator(pm));
        final List<ComponentName> components = new ArrayList<>();
        List<CharSequence> names = new ArrayList<>();
        int selected = -1;
        String saved = shortcuts.getString(SHORTCUTS[index], "");
        for (ResolveInfo entry : entries) {
            if (entry.activityInfo == null || !entry.activityInfo.exported
                || getPackageName().equals(entry.activityInfo.packageName)) continue;
            ComponentName component = new ComponentName(entry.activityInfo.packageName, entry.activityInfo.name);
            if (components.contains(component)) continue;
            if (saved.equals(component.flattenToString())) selected = components.size();
            components.add(component);
            names.add(entry.loadLabel(pm));
        }
        if (components.isEmpty()) {
            toast("이 기기에 선택할 수 있는 앱이 없습니다.");
            return;
        }
        new AlertDialog.Builder(this).setTitle(LABELS[index] + " 앱 선택")
            .setSingleChoiceItems(names.toArray(new CharSequence[0]), selected, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int choice) {
                    ComponentName component = components.get(choice);
                    shortcuts.edit().putString(SHORTCUTS[index], component.flattenToString()).apply();
                    dialog.dismiss();
                    if (launch && !open(component)) toast("선택한 앱을 열 수 없습니다.");
                }
            })
            .setNeutralButton("연결 해제", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int choice) {
                    shortcuts.edit().remove(SHORTCUTS[index]).apply();
                }
            })
            .setNegativeButton("취소", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (pageReady) web.evaluateJavascript("window.K8Home.back()", null);
        else finish();
    }

    @Override protected void onDestroy() {
        mapSlot.removeAllViews();
        root.removeView(web);
        web.removeJavascriptInterface("K8Host");
        web.destroy();
        super.onDestroy();
    }
}
