package com.chatapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    companion object {
        private const val CHAT_URL = "http://8.138.224.117:3001"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                uploadCallback?.onReceiveValue(arrayOf(it))
                uploadCallback = null
            } ?: run {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = null
            }
        }

        setupWebView()
        checkPermissions()

        webView.loadUrl(CHAT_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setGeolocationEnabled(true)
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadCallback = filePathCallback
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                fileChooserLauncher.launch(acceptTypes)
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val code = error?.errorCode ?: -1
                    val desc = error?.description?.toString() ?: "未知错误"
                    handleError(code, desc)
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                handleError(errorCode, description ?: "未知错误")
            }
        }
    }

    private fun handleError(code: Int, description: String) {
        progressBar.visibility = View.GONE
        val msg = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP -> "无法解析服务器地址，请检查网络连接"
            WebViewClient.ERROR_CONNECT -> "无法连接到服务器，请确认服务器已启动"
            WebViewClient.ERROR_TIMEOUT -> "连接超时，请检查网络"
            WebViewClient.ERROR_AUTHENTICATION -> "认证失败"
            WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "不支持的协议"
            else -> "连接失败: $description"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

        val errorHtml = """
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background: #1a1a2e;
                        color: #eee;
                        font-family: sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 20px;
                        text-align: center;
                    }
                    h2 { color: #e94560; }
                    p { color: #aaa; font-size: 14px; margin: 8px 0; }
                    button {
                        margin-top: 20px;
                        padding: 12px 32px;
                        border: none;
                        border-radius: 8px;
                        background: #e94560;
                        color: white;
                        font-size: 15px;
                        cursor: pointer;
                    }
                    .code { font-size: 11px; color: #666; margin-top: 8px; }
                </style>
            </head>
            <body>
                <h2>连接失败</h2>
                <p>$msg</p>
                <p>服务器地址: ${CHAT_URL}</p>
                <button onclick="location.reload()">重试连接</button>
                <div class="code">错误码: $code</div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i] == Manifest.permission.CAMERA ||
                        permissions[i] == Manifest.permission.RECORD_AUDIO
                    ) {
                        Toast.makeText(
                            this,
                            "需要摄像头和麦克风权限才能进行视频通话",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
