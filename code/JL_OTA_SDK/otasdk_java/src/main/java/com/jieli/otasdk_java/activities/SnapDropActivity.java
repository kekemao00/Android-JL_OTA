package com.jieli.otasdk_java.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anggrayudi.storage.callback.FileCallback;
import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.DocumentFileType;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.media.FileDescription;
import com.anggrayudi.storage.media.MediaFile;
import com.google.android.material.snackbar.Snackbar;
import com.jieli.filepicker.FilePicker;
import com.jieli.filepicker.bean.ChooseMode;
import com.jieli.filepicker.bean.SortWayEnum;
import com.jieli.filepicker.constant.Constant;
import com.jieli.jl_bt_ota.util.FileUtil;
import com.jieli.otasdk_java.BuildConfig;
import com.jieli.otasdk_java.MainApplication;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.base.BaseActivity;
import com.jieli.otasdk_java.tool.snapdrop.JavaScriptInterface;
import com.jieli.otasdk_java.util.CustomFileFilter;
import com.jieli.otasdk_java.util.JL_Constant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author:HuanMing
 * @time: 21:08
 * @descript:
 */
public class SnapDropActivity extends BaseActivity {
    private static final int MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 12321;
    private static final int LAUNCH_SETTINGS_ACTIVITY = 12;
    public static final int REQUEST_SELECT_FILE = 100;
    public static final int REQUEST_PERMISSION = 101;

    private String baseURL;

    public WebView webView;
    public SharedPreferences prefs;
    public LinearLayoutCompat imageViewLayout;
    public SwipeRefreshLayout pullToRefresh;
    public ConstraintLayout coordinatorLayout;

    public ValueCallback<Uri[]> uploadMessage;

    private boolean currentlyOffline = true;
    private boolean currentlyLoading = false;
    public boolean forceRefresh = false;
    public Boolean transfer = false;
    public boolean onlyText = false;

    public List<JavaScriptInterface.FileHeader> downloadFilesList = new ArrayList<>(); // name - size

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Intent uploadIntent = null;

    private ConnectivityManager connMgr = null;
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if ((!isInitialStickyBroadcast()) && currentlyOffline) {
                final Handler handler = new Handler();
                handler.postDelayed(() -> {
                    if (isWifiAvailable()) {
                        refreshWebsite();
                    }
                }, 1500); // wait a bit until connection is ready

            }
        }
    };


    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = FileUtil.splicingFilePath(MainApplication.getInstance(), MainApplication.getInstance().getPackageName(), JL_Constant.DIR_UPGRADE, null, null);

        Log.d(TAG, "onCreate: path : " + path);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        baseURL = "https://snapdrop.net/";

        setContentView(R.layout.activity_snap_drop);
        webView = findViewById(R.id.webview);
        pullToRefresh = findViewById(R.id.pullToRefresh);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        imageViewLayout = findViewById(R.id.splashImage);
        connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        webView.getSettings().setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; Build/HUAWEI" + "JLOTA" + ") Version/" + BuildConfig.VERSION_NAME + (isTablet(this) ? " Tablet " : " Mobile ") + "Safari/537.36");
        webView.addJavascriptInterface(new JavaScriptInterface(SnapDropActivity.this), "SnapdropAndroid");
        webView.setWebChromeClient(new MyWebChromeClient());
        webView.setWebViewClient(new CustomWebViewClient());

        // Allow webContentsDebugging if APK was build as debuggable
        if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }


        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            final Iterator<JavaScriptInterface.FileHeader> iterator = downloadFilesList.iterator();
            while (iterator.hasNext()) {
                final JavaScriptInterface.FileHeader file = iterator.next();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    if (Integer.parseInt(file.getSize()) > 0) {
                        copyTempToDownloads(file);
                        iterator.remove();
                        break;
                    }
                } else {
                    if (file.getSize().equals(String.valueOf(contentLength))) {
                        copyTempToDownloads(file);
                        iterator.remove();
                        break;
                    }
                }
            }
        });

        refreshWebsite();
        onNewIntent(getIntent());

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                && (ContextCompat.checkSelfPermission(SnapDropActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
        }

        pullToRefresh.setOnRefreshListener(() -> {
            refreshWebsite(true);
            pullToRefresh.setRefreshing(false);
        });

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(receiver, intentFilter);
        ImageButton backBtn = findViewById(R.id.btn_back);
        backBtn.setOnClickListener(v -> {
            this.finish();
        });
    }

    private void refreshWebsite(final boolean pulled) {
        if (isWifiAvailable() && !transfer || forceRefresh) {
            webView.loadUrl(baseURL);
            forceRefresh = false;
        } else if (transfer) {
            forceRefresh = pulled; //reset forceRefresh if after pullToRefresh the refresh request did come from another source eg onResume, so pullToRefresh doesn't unexpectedly force refreshes by "first time"
        } else {
            showScreenNoConnection();
        }
    }

    private void refreshWebsite() {
        refreshWebsite(false);
    }

    private void showScreenNoConnection() {
        webView.loadUrl("file:///android_asset/offline.html?text=" + getString(R.string.error_network) + "&button=" + getString(R.string.ignore_error_network));
        currentlyOffline = true;
    }

    private boolean isWifiAvailable() {
        if (connMgr != null) {
            final NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }
        return false;
    }

    private boolean isInternetAvailable() {
        if (connMgr != null) {
            final NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    public static boolean isTablet(final Context ctx) {
        return (ctx.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if ((Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) && intent.getType() != null) {
            uploadIntent = intent;

            final String clipText = getTextFromUploadIntent();
            webView.loadUrl(JavaScriptInterface.getSendTextDialogWithPreInsertedString(clipText));

            final Snackbar snackbar = Snackbar
                    .make(coordinatorLayout, clipText.isEmpty() ? (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) ? R.string.intent_files : R.string.intent_file) : R.string.intent_content, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.cancel, button -> resetUploadIntent());
            snackbar.show();

            onlyText = true;
            final Uri[] results = getUploadFromIntentUris(intent);
            if (results != null) {
                for (Uri uri : results) {
                    if (uri != null) {
                        onlyText = false;
                        break;
                    }
                }
            }
        }
    }

    public void resetUploadIntent() {
        uploadIntent = null;
        onlyText = false;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_SELECT_FILE) {
            if (resultCode == RESULT_OK) {
                uploadFromIntent(intent);
            } else if (uploadMessage != null) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
            }
        } else if (requestCode == LAUNCH_SETTINGS_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK);
                recreate();
            }
        } else if (requestCode == REQUEST_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    //遍历目录
                    startFileBrowse();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.getUrl() != null && webView.getUrl().endsWith("#about")) {
            webView.loadUrl(baseURL + "#");
        } else {
            super.onBackPressed();
        }
    }

    /**
     * @return true if there was no server connection for more than three minutes
     */
    private boolean onlinePastThreeMin() {
        return System.currentTimeMillis() - prefs.getLong("last_server_connection", 0) > 1000 * 60 * 3;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (onlinePastThreeMin()) {
            refreshWebsite();
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        webView.loadUrl("about:blank");
        super.onDestroy();
    }

    private void uploadFromIntent(final Intent intent) {
        if (uploadMessage != null) {
            uploadMessage.onReceiveValue(getUploadFromIntentUris(intent));
            uploadMessage = null;
        }
    }

    private Uri[] getUploadFromIntentUris(final Intent intent) {
        Uri[] results = null;
        try {
            List<String> list = intent.getStringArrayListExtra(Constant.INSTANCE.getRESULT_INFO());
            results = new Uri[list.size()];
            for (int i = 0; i < list.size(); i++) {
                String path = list.get(i);
                Log.d(TAG, "getUploadFromIntentUris: " + path);
                results[i] = Uri.parse("file://" + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private String getTextFromUploadIntent() {
        final StringBuilder result = new StringBuilder();
        if (uploadIntent != null) {
            final ClipData clip = uploadIntent.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    final ClipData.Item item = clip.getItemAt(i);
                    if (item.getText() != null) {
                        result.append(item.getText()).append(" ");
                    }
                }
            }
        }
        return result.toString().trim();
    }

    private void startFileBrowse() {
        String path = FileUtil.splicingFilePath(MainApplication.getInstance(), MainApplication.getInstance().getPackageName(), JL_Constant.DIR_LOGCAT, null, null);
        new FilePicker()
                .withActivity(SnapDropActivity.this)
                .withChooseMode(ChooseMode.CHOOSE_MODE_ONLY_FILE)
                .withSortWay(SortWayEnum.FILE_UPDATE_TIME_DESC)
                .withTitle("OTA")
                .withMaxSelected(5)
                .withFilFilter(CustomFileFilter.class)
                .withRequestCode(REQUEST_SELECT_FILE)
                .start(path);
    }

    class MyWebChromeClient extends WebChromeClient {

        public boolean onShowFileChooser(final WebView mWebView, final ValueCallback<Uri[]> filePathCallback, final FileChooserParams fileChooserParams) {
            if (uploadMessage != null) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
            }

            uploadMessage = filePathCallback;

            if (uploadIntent != null) {
                try {
                    Log.d(TAG, "onShowFileChooser: uploadIntent != null");
                    uploadFromIntent(uploadIntent);
                    return true;
                } catch (Exception e) {
                    // pass - can happen, when a text is selected for sharing instead of a file
                }
            }
            checkManagerPermission();
            return true;
        }

        //申请存储管理权限 Android11
        private void checkManagerPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //检查是否已经有权限
                if (!Environment.isExternalStorageManager()) {
                    //跳转新页面申请权限
                    startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQUEST_PERMISSION
                    );
                } else {
                    startFileBrowse();
                }
            } else {
                startFileBrowse();
            }
        }

        @Override
        public boolean onCreateWindow(final WebView view, final boolean dialog, final boolean userGesture, final Message resultMsg) {
            final String url = view.getHitTestResult().getExtra();
            if (url.endsWith("update.html#settings")) {
                final Intent browserIntent = new Intent(SnapDropActivity.this, SettingsActivity.class);
                startActivityForResult(browserIntent, LAUNCH_SETTINGS_ACTIVITY);
            } else if (url.endsWith("offlineButForceRefresh")) {
                forceRefresh = true;
                imageViewLayout.setVisibility(View.VISIBLE);
                pullToRefresh.setVisibility(View.GONE);
                refreshWebsite();
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    Snackbar.make(coordinatorLayout, R.string.err_no_browser, Snackbar.LENGTH_SHORT).show();
                    resetUploadIntent(); // the snackbar will dismiss the "files are selected" message, therefore also reset the upload intent.
                }
            }
            return false;
        }

    }

    private class CustomWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(final WebView view, final String url) {
            currentlyLoading = false;

            if (url.startsWith(baseURL) || currentlyOffline) {
                currentlyOffline = !url.startsWith(baseURL);

                initSnapdrop();

            }
            super.onPageFinished(view, url);
        }

        private void initSnapdrop() {
            if (SnapDropActivity.this.isFinishing()) {
                return; // too late to do anything at this point in time...
            }
            //website initialisation
            if (!currentlyOffline) {
                webView.loadUrl(JavaScriptInterface.initialiseWebsite());
                webView.loadUrl(JavaScriptInterface.getSendTextDialogWithPreInsertedString(getTextFromUploadIntent()));

                // welcome dialog
                if (prefs.getBoolean(getString(R.string.pref_first_use), true)) {
                    new AlertDialog.Builder(SnapDropActivity.this)
                            .setCancelable(false)
                            .setTitle(R.string.app_welcome)
                            .setMessage(R.string.app_welcome_summary)
                            .setPositiveButton(android.R.string.ok, null)
                            .create()
                            .show();

                    prefs.edit().putBoolean(getString(R.string.pref_first_use), false).apply();
                }
            }

            imageViewLayout.setVisibility(View.GONE);
            pullToRefresh.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            final Handler handler = new Handler();
            final int delay = 500; // milliseconds
            currentlyLoading = true;

            handler.postDelayed(new Runnable() {
                public void run() {
                    //do something
                    if (currentlyLoading) {
                        if (isInternetAvailable()) {
                            handler.postDelayed(this, delay);
                        } else {
                            refreshWebsite();
                        }
                    }
                }
            }, delay);

        }

        @Override
        public void onReceivedError(final WebView view, final WebResourceRequest request, final WebResourceError error) {
            showScreenNoConnection();
        }
    }

    private void copyTempToDownloads(final JavaScriptInterface.FileHeader fileHeader) {
        if (Long.parseLong(fileHeader.getSize()) > 25 * 1024 * 1024) {
            Snackbar.make(coordinatorLayout, R.string.download_save_pending, Snackbar.LENGTH_INDEFINITE).show();
            resetUploadIntent(); // the snackbar will dismiss the "files are selected" message, therefore also reset the upload intent.
        }

        executor.execute(() -> {
            final FileDescription fileDescription = new FileDescription(fileHeader.getName(), "", fileHeader.getMime());
            final DocumentFile source = DocumentFile.fromFile(fileHeader.getTempFile());
//            final String downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            String path = FileUtil.splicingFilePath(MainApplication.getInstance(), MainApplication.getInstance().getPackageName(), JL_Constant.DIR_UPGRADE, null, null);
            final DocumentFile saveLocation = DocumentFileCompat.fromFullPath(getApplicationContext(), path, DocumentFileType.FOLDER, true);
            if (saveLocation != null) {
                DocumentFileUtils.moveFileTo(source, getApplicationContext(), saveLocation, fileDescription, fileCallback(fileHeader));
            } else {
                DocumentFileUtils.moveFileToDownloadMedia(source, getApplicationContext(), fileDescription, fileCallback(fileHeader));
            }
        });
    }

    private FileCallback fileCallback(final JavaScriptInterface.FileHeader fileHeader) {
        return new FileCallback() {
            @Override
            public void onFailed(@NonNull final ErrorCode errorCode) {
                Log.d("SimpleStorage", errorCode.toString());
                Snackbar.make(coordinatorLayout, errorCode.toString(), Snackbar.LENGTH_LONG).show();
                resetUploadIntent(); // the snackbar will dismiss the "files are selected" message, therefore also reset the upload intent.
            }

            @Override
            public void onCompleted(@NonNull final Object file) {
                final Uri uri;

                if (file instanceof MediaFile) {
                    final MediaFile mediaFile = (MediaFile) file;
                    uri = mediaFile.getUri();
                } else if (file instanceof DocumentFile) {
                    final DocumentFile documentFile = (DocumentFile) file;
                    final Context context = getApplicationContext();
                    uri = DocumentFileUtils.isRawFile(documentFile)
                            ? FileProvider.getUriForFile(context, getPackageName() + ".provider", DocumentFileUtils.toRawFile(documentFile, context))
                            : documentFile.getUri();
                } else {
                    return;
                }

                fileDownloadedIntent(uri, fileHeader);
            }
        };
    }

    private void fileDownloadedIntent(final Uri uri, final JavaScriptInterface.FileHeader fileHeader) {
        final int notificationId = (int) SystemClock.uptimeMillis();

        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, fileHeader.getMime());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        final PendingIntent pendingIntent = PendingIntent.getActivity(SnapDropActivity.this, 1, intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT : PendingIntent.FLAG_CANCEL_CURRENT);
        final String channelId = "MYCHANNEL";
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel notificationChannel = new NotificationChannel(channelId, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            final Notification notification = new Notification.Builder(SnapDropActivity.this, channelId)
                    .setContentText(fileHeader.getName())
                    .setContentTitle(getString(R.string.download_successful))
                    .setContentIntent(pendingIntent)
                    .setChannelId(channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build();
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
                notificationManager.notify(notificationId, notification);
            }

        } else {
            final NotificationCompat.Builder b = new NotificationCompat.Builder(SnapDropActivity.this, channelId)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle(getString(R.string.download_successful))
                    .setContentText(fileHeader.getName());

            if (notificationManager != null) {
                notificationManager.notify(notificationId, b.build());
            }
        }

        final Snackbar snackbar = Snackbar.make(coordinatorLayout, R.string.download_successful, Snackbar.LENGTH_LONG)
                .setAction(R.string.open, button -> {
                    try {
                        startActivity(intent);
                        notificationManager.cancel(notificationId);
                    } catch (Exception e) {
                        Snackbar.make(coordinatorLayout, R.string.err_no_app, Snackbar.LENGTH_SHORT).show();
                    }

                });
        snackbar.show();

        // the snackbar will dismiss the "files are selected" message, therefore also reset the upload intent.
        resetUploadIntent();
    }

}
