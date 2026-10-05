package com.xiaoyan.archive;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 星空小站主界面：全屏 WebView 加载站点页面。
 *
 * 主要能力：
 * - JavaScript + DOM Storage + 多媒体 + 缩放
 * - 顶部进度条
 * - 菜单：刷新 / 分享 / 外部浏览器 / 切换服务器
 * - 后退键支持网页后退
 * - 启动时静默检查 GitHub Releases 是否有新版（build-N），有就弹窗
 *
 * 深色模式：App 外壳（工具栏/启动页）通过 themes.xml 自动跟随系统；
 * WebView 内的网站内容由站点自身管理主题，不在 WebView 层面强制。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "xiaoyan-app";
    private static final String PREFS = "xiaoyan_settings";
    private static final String KEY_DOMAIN = "primary_domain";
    private static final String KEY_LAST_UPDATE_DISMISS = "last_update_dismiss";
    /** 升级提示冷却时间（毫秒），用户点「稍后」后 24 小时内不再打扰 */
    private static final long UPDATE_DISMISS_COOLDOWN_MS = 24L * 60 * 60 * 1000;

    /** 花生壳域名（主人自有） */
    public static final String DOMAIN_PRIMARY = "https://drugsthatthis.dpdns.org";
    /** Cloudflare Workers 域名（备用） */
    public static final String DOMAIN_BACKUP = "https://xiaoyan-archive.3394073613.workers.dev";

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        setupWebView();

        String domain = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_DOMAIN, DOMAIN_PRIMARY);
        webView.loadUrl(domain);

        // 启动时静默检查更新（后台线程，失败不打扰）
        checkForUpdate();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // JS + 本地存储 + 缓存
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 视口
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // 多媒体与文件访问
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 让所有 http/https 链接在 WebView 内打开
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    return false;
                }
                // 其他协议（tel:, mailto: 等）交给系统处理
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) { }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                }
            }
        });
    }

    /**
     * 启动时静默检查 GitHub Releases 是否有新版本（build-N）。
     * 匿名限流 60 次/小时够用，失败静默不打扰用户。
     * 用户点「稍后」后 24 小时内不再弹窗。
     */
    private void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String owner = BuildConfig.GITHUB_OWNER;
                String repo = BuildConfig.GITHUB_REPO;
                URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "xiaoyan-archive-app/" + BuildConfig.VERSION_CODE);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    Log.w(TAG, "checkForUpdate: GitHub API returned " + code);
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                String tag = json.optString("tag_name", "");
                String htmlUrl = json.optString("html_url", "");
                String releaseName = json.optString("name", "");

                // 解析 build-N
                Matcher m = Pattern.compile("build-(\\d+)", Pattern.CASE_INSENSITIVE).matcher(tag);
                if (!m.find()) {
                    Log.w(TAG, "checkForUpdate: tag_name 不匹配 build-N: " + tag);
                    return;
                }
                int latestBuild = Integer.parseInt(m.group(1));
                int currentBuild = BuildConfig.VERSION_CODE;

                if (latestBuild <= currentBuild) {
                    Log.d(TAG, "checkForUpdate: 已是最新版 current=" + currentBuild + " latest=" + latestBuild);
                    return;
                }

                // 24 小时冷却
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                long lastDismiss = prefs.getLong(KEY_LAST_UPDATE_DISMISS, 0);
                if (lastDismiss > 0 && System.currentTimeMillis() - lastDismiss < UPDATE_DISMISS_COOLDOWN_MS) {
                    Log.d(TAG, "checkForUpdate: 用户 24h 内已 dismiss 升级提示，跳过");
                    return;
                }

                // 弹窗（必须回主线程）
                final String finalReleaseName = releaseName.isEmpty() ? tag : releaseName;
                final String finalHtmlUrl = htmlUrl;
                final int finalLatestBuild = latestBuild;
                runOnUiThread(() -> showUpdateDialog(finalReleaseName, finalHtmlUrl, finalLatestBuild, currentBuild));
            } catch (Exception e) {
                Log.w(TAG, "checkForUpdate: 失败（静默）: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "update-checker").start();
    }

    /**
     * 弹出升级提示对话框。
     * 「立即更新」→ 跳系统浏览器到 GitHub Release 页
     * 「稍后」→ 记录 dismissed_at，24h 内不再打扰
     */
    private void showUpdateDialog(String releaseName, String htmlUrl, int latestBuild, int currentBuild) {
        if (isFinishing() || htmlUrl == null || htmlUrl.isEmpty()) return;
        String message = "当前版本：build-" + currentBuild + "\n"
                + "最新版本：" + releaseName + "\n\n"
                + "建议升级以获得最新功能。点击「立即更新」将跳转浏览器下载新 APK。\n"
                + "（旧版需先卸载再安装新版，因签名已升级为 release）";
        new AlertDialog.Builder(this)
                .setTitle("🌙 发现新版本")
                .setMessage(message)
                .setPositiveButton("立即更新", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)));
                    } catch (Exception e) {
                        Toast.makeText(this, "无法打开浏览器：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("稍后", (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_UPDATE_DISMISS, System.currentTimeMillis())
                            .apply();
                    Toast.makeText(this, "24 小时内不再提醒", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            webView.reload();
            return true;
        } else if (id == R.id.action_share) {
            String url = webView.getUrl();
            if (url != null) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                share.putExtra(Intent.EXTRA_TEXT, url);
                startActivity(Intent.createChooser(share, getString(R.string.toast_share)));
            }
            return true;
        } else if (id == R.id.action_open_browser) {
            String url = webView.getUrl();
            if (url != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
            return true;
        } else if (id == R.id.action_switch_domain) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String current = prefs.getString(KEY_DOMAIN, DOMAIN_PRIMARY);
            String next = current.startsWith("drugsthatthis") ? DOMAIN_BACKUP : DOMAIN_PRIMARY;
            prefs.edit().putString(KEY_DOMAIN, next).apply();
            webView.loadUrl(next);
            Toast.makeText(this, getString(R.string.toast_domain_switched, next), Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
