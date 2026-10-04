package com.xiaoyan.archive;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
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

/**
 * 星空小站主界面：全屏 WebView 加载站点页面。
 *
 * 主要能力：
 * - JavaScript + DOM Storage + 多媒体 + 缩放
 * - 顶部进度条
 * - 菜单：刷新 / 分享 / 外部浏览器 / 切换服务器
 * - 后退键支持网页后退
 *
 * 深色模式：App 外壳（工具栏/启动页）通过 themes.xml 自动跟随系统；
 * WebView 内的网站内容由站点自身管理主题，不在 WebView 层面强制。
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "xiaoyan_settings";
    private static final String KEY_DOMAIN = "primary_domain";

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
