package com.example.classlist

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.classlist.data.PortalSettings
import com.example.classlist.data.ScheduleParser
import com.example.classlist.data.ScheduleStore
import com.example.classlist.web.PortalEntryExtractor
import com.example.classlist.web.PortalExtractor
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import org.json.JSONTokener

class PortalActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var importButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var errorState: View
    private lateinit var errorTitle: TextView
    private lateinit var errorDetail: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var extractionAttempts = 0
    private var mainPageFailed = false
    private var currentUrl = PortalSettings.HOME_URL
    private var entryDiscoveryInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        setContentView(R.layout.activity_portal)

        webView = findViewById(R.id.portalWebView)
        importButton = findViewById(R.id.importButton)
        progress = findViewById(R.id.pageProgress)
        errorState = findViewById(R.id.errorState)
        errorTitle = findViewById(R.id.errorTitle)
        errorDetail = findViewById(R.id.errorDetail)

        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        findViewById<MaterialButton>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener { loadEntry() }
        findViewById<MaterialButton>(R.id.browserButton).setOnClickListener {
            openExternal(PortalSettings.HOME_URL.toUri())
        }
        importButton.setOnClickListener {
            extractionAttempts = 0
            extractSchedule()
        }

        if (savedInstanceState == null) loadEntry() else webView.restoreState(savedInstanceState)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "javascript" || uri.scheme == "about") return false
                if (PortalSettings.isAllowed(uri.toString())) return false
                if (request.isForMainFrame) openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && PortalSettings.isAllowed(url)) currentUrl = url
                entryDiscoveryInProgress = false
                mainPageFailed = false
                errorState.visibility = View.GONE
                webView.visibility = View.VISIBLE
                importButton.isEnabled = false
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                if (mainPageFailed || url == null) return
                importButton.isEnabled = PortalSettings.canImport(url)
                if (PortalSettings.isWebsiteHome(url)) discoverOfficialEntry()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val message = when (error.errorCode) {
                    ERROR_TIMEOUT -> getString(R.string.network_timeout)
                    ERROR_HOST_LOOKUP -> getString(R.string.dns_failed)
                    ERROR_CONNECT, ERROR_IO -> getString(R.string.network_unavailable)
                    ERROR_FAILED_SSL_HANDSHAKE -> getString(R.string.ssl_failed)
                    else -> getString(R.string.page_failed, error.errorCode)
                }
                showLoadError(message)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showLoadError(getString(R.string.http_failed, errorResponse.statusCode))
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showLoadError(getString(R.string.ssl_failed))
            }
        }
    }

    private fun loadEntry() {
        currentUrl = PortalSettings.HOME_URL
        entryDiscoveryInProgress = false
        mainPageFailed = false
        errorState.visibility = View.GONE
        webView.visibility = View.VISIBLE
        importButton.isEnabled = false
        progress.visibility = View.VISIBLE
        webView.loadUrl(currentUrl)
    }

    private fun discoverOfficialEntry() {
        if (entryDiscoveryInProgress) return
        entryDiscoveryInProgress = true
        progress.visibility = View.VISIBLE
        webView.evaluateJavascript(PortalEntryExtractor.script) { encodedResult ->
            val entryUrl = runCatching { JSONTokener(encodedResult).nextValue() as? String }
                .getOrNull()
                .orEmpty()
            if (PortalSettings.isOfficialEntry(entryUrl) && PortalSettings.isWebsiteHome(webView.url.orEmpty())) {
                webView.loadUrl(entryUrl)
            } else {
                entryDiscoveryInProgress = false
                progress.visibility = View.GONE
                Toast.makeText(this, R.string.official_entry_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoadError(message: String) {
        mainPageFailed = true
        progress.visibility = View.GONE
        importButton.isEnabled = false
        webView.visibility = View.GONE
        errorTitle.setText(R.string.portal_load_failed)
        errorDetail.text = message
        errorState.visibility = View.VISIBLE
    }

    private fun extractSchedule() {
        importButton.isEnabled = false
        progress.visibility = View.VISIBLE
        webView.evaluateJavascript(PortalExtractor.script) { encodedResult ->
            val payload = runCatching {
                val decoded = JSONTokener(encodedResult).nextValue()
                if (decoded is String) decoded else encodedResult
            }.getOrDefault(encodedResult)
            val result = runCatching { JSONObject(payload) }.getOrElse {
                finishExtraction("页面返回格式无法识别")
                return@evaluateJavascript
            }
            when (result.optString("status")) {
                "ok" -> importResult(payload)
                "navigating" -> {
                    extractionAttempts++
                    if (extractionAttempts <= MAX_EXTRACTION_ATTEMPTS) {
                        handler.postDelayed(::extractSchedule, 1600L)
                    } else {
                        finishExtraction("课表页面加载超时")
                    }
                }
                else -> finishExtraction(result.optString("message", "没有找到课表"))
            }
        }
    }

    private fun importResult(payload: String) {
        val courses = runCatching { ScheduleParser.fromExtractionJson(payload) }.getOrElse {
            finishExtraction("课表结构暂不支持")
            return
        }
        if (courses.isEmpty()) {
            finishExtraction("课表中没有可识别的课程")
            return
        }

        ScheduleStore(this).save(courses)
        val uniqueCourseCount = courses.distinctBy { it.identityKey }.size
        CookieManager.getInstance().flush()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COURSE_COUNT, uniqueCourseCount))
        Toast.makeText(this, getString(R.string.imported_count, uniqueCourseCount), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun finishExtraction(message: String) {
        handler.removeCallbacksAndMessages(null)
        importButton.isEnabled = true
        progress.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COURSE_COUNT = "course_count"
        private const val MAX_EXTRACTION_ATTEMPTS = 5
    }
}
