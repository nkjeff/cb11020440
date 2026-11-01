package com.example.minimalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class MainActivity : Activity() {
    private data class BrowserTab(val web: WebView, var title: String = "空白页", var url: String = "")
    private val tabs = mutableListOf<BrowserTab>()
    private var currentIndex = -1
    private lateinit var pageHost: FrameLayout
    private lateinit var address: EditText
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var refreshButton: TextView
    private lateinit var tabsButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildInterface()
        createTab()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
        }
        setContentView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.BLACK)
        }
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "搜索或输入网址"
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(Color.rgb(24, 24, 24), 24)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    loadFromAddress()
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f))
        val go = button("↵", 24f) { loadFromAddress() }
        go.setTextColor(Color.rgb(155, 190, 255))
        top.addView(go, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(top)

        pageHost = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(7))
            setBackgroundColor(Color.BLACK)
        }
        backButton = button("‹", 30f) { currentWeb()?.let { if (it.canGoBack()) it.goBack() } }
        forwardButton = button("›", 30f) { currentWeb()?.let { if (it.canGoForward()) it.goForward() } }
        refreshButton = button("⟳", 27f) { currentWeb()?.reload() }
        val plus = button("+", 29f) { createTab() }
        plus.setOnLongClickListener {
            closeCurrentAndCreateBlank()
            true
        }
        tabsButton = button("1", 17f) { showTabs() }
        listOf(backButton, forwardButton, refreshButton, plus, tabsButton).forEach {
            bottom.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(bottom)
    }

    private fun createTab() {
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    val tab = tabs.firstOrNull { it.web === view } ?: return
                    tab.url = url.orEmpty()
                    if (view === currentWeb() && !address.hasFocus()) address.setText(tab.url)
                    updateNavigation()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    val tab = tabs.firstOrNull { it.web === view } ?: return
                    tab.url = url.orEmpty()
                    if (view === currentWeb() && !address.hasFocus()) address.setText(tab.url)
                    updateNavigation()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    tabs.firstOrNull { it.web === view }?.title = title?.takeIf { it.isNotBlank() } ?: "网页"
                }
            }
            loadUrl("about:blank")
        }
        tabs.add(BrowserTab(web))
        switchTo(tabs.lastIndex)
        address.setText("")
        address.clearFocus()
    }

    private fun switchTo(index: Int) {
        if (index !in tabs.indices) return
        currentIndex = index
        pageHost.removeAllViews()
        val web = tabs[index].web
        (web.parent as? ViewGroup)?.removeView(web)
        pageHost.addView(web, FrameLayout.LayoutParams(-1, -1))
        address.setText(tabs[index].url)
        address.setSelection(address.text.length)
        updateNavigation()
    }

    private fun closeCurrentAndCreateBlank() {
        if (currentIndex in tabs.indices) {
            val old = tabs.removeAt(currentIndex)
            pageHost.removeView(old.web)
            old.web.stopLoading()
            old.web.destroy()
        }
        currentIndex = -1
        createTab()
        Toast.makeText(this, "已关闭当前标签页并新建空白页", Toast.LENGTH_SHORT).show()
    }

    private fun showTabs() {
        if (tabs.isEmpty()) return
        val items = tabs.mapIndexed { i, tab ->
            (if (i == currentIndex) "● " else "") + "${i + 1}. " + tab.title
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("标签页（${tabs.size}）")
            .setItems(items) { _, which -> switchTo(which) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadFromAddress() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val target = when {
            raw.startsWith("https://", true) || raw.startsWith("http://", true) -> raw
            raw.contains(" ") || !raw.substringBefore("/").contains(".") ->
                "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8")
            else -> "https://$raw"
        }
        currentWeb()?.loadUrl(target)
        address.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
    }

    private fun currentWeb(): WebView? = tabs.getOrNull(currentIndex)?.web

    private fun updateNavigation() {
        if (!::backButton.isInitialized) return
        val web = currentWeb()
        backButton.alpha = if (web?.canGoBack() == true) 1f else 0.3f
        forwardButton.alpha = if (web?.canGoForward() == true) 1f else 0.3f
        refreshButton.alpha = if (web != null) 1f else 0.3f
        tabsButton.text = if (tabs.size > 9) "9+" else tabs.size.toString()
    }

    private fun button(label: String, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.rgb(35, 35, 35), 12))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, 12))
        }
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val web = currentWeb()
        if (web?.canGoBack() == true) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
