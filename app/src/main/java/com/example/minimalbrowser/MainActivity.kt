package com.example.minimalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private data class BrowserTab(val web: WebView, var title: String = "空白页", var url: String = "")
    private data class Shortcut(val name: String, val url: String)
    private val shortcuts = mutableListOf<Shortcut>()
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
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        loadShortcuts()
        buildInterface()
        createTab()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = false
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                insets.systemWindowInsetTop
            }
            val navigationBarBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                insets.systemWindowInsetBottom
            }
            view.setPadding(view.paddingLeft, statusBarTop, view.paddingRight, navigationBarBottom)
            insets
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
            setHintTextColor(Color.LTGRAY)
            hint = "搜索或输入网址"
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(Color.BLACK, 24)
            setOnLongClickListener {
                val value = text.toString().trim()
                if (value.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("地址", value))
                    Toast.makeText(this@MainActivity, "地址已复制", Toast.LENGTH_SHORT).show()
                    true
                } else false
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    loadFromAddress()
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(-1, dp(44)))
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
        root.requestFocus()
    }

    private fun createTab(openUrl: String? = null) {
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
                    if (view === currentWeb()) showWebContent(view)
                    updateNavigation()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    val tab = tabs.firstOrNull { it.web === view } ?: return
                    tab.url = url.orEmpty()
                    if (view === currentWeb() && !address.hasFocus()) address.setText(tab.url)
                    if (view === currentWeb()) showWebContent(view)
                    updateNavigation()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    tabs.firstOrNull { it.web === view }?.title = title?.takeIf { it.isNotBlank() } ?: "网页"
                }
            }
            setOnLongClickListener {
                val hit = hitTestResult
                val link = hit.extra
                if ((hit.type == WebView.HitTestResult.ANCHOR_TYPE ||
                     hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                     hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                     hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                    !link.isNullOrBlank()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setItems(arrayOf("在新标签页打开", "复制链接")) { _, which ->
                            when (which) {
                                0 -> createTab(link)
                                1 -> {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("链接", link))
                                    Toast.makeText(this@MainActivity, "链接已复制", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                } else false
            }
            loadUrl("about:blank")
        }
        tabs.add(BrowserTab(web))
        switchTo(tabs.lastIndex)
        address.setText("")
        address.clearFocus()
        pageHost.requestFocus()
        if (!openUrl.isNullOrBlank()) web.loadUrl(openUrl)
    }

    private fun switchTo(index: Int) {
        if (index !in tabs.indices) return
        currentIndex = index
        pageHost.removeAllViews()
        val tab = tabs[index]
        val web = tab.web
        if (tab.url.isBlank() || tab.url == "about:blank") {
            pageHost.removeAllViews()
            pageHost.addView(buildBlankPage(), FrameLayout.LayoutParams(-1, -1))
        } else {
            showWebContent(web)
        }
        address.setText(tabs[index].url)
        address.setSelection(address.text.length)
        address.clearFocus()
        pageHost.requestFocus()
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

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("标签页（${tabs.size}）")
            .setView(scroll)
            .setNegativeButton("完成", null)
            .create()

        fun renderRows() {
            list.removeAllViews()
            tabs.forEachIndexed { index, tab ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(3), dp(4), dp(3))
                    setBackgroundColor(if (index == currentIndex) Color.BLACK else Color.BLACK)
                }
                val title = TextView(this).apply {
                    text = (if (index == currentIndex) "●  " else "") +
                        "${index + 1}. " + tab.title.ifBlank { "网页" }
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                    isClickable = true
                    setOnClickListener {
                        switchTo(index)
                        dialog.dismiss()
                    }
                }
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

                val close = TextView(this).apply {
                    text = "×"
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    isClickable = true
                    contentDescription = "关闭第 ${index + 1} 个标签页"
                    setPadding(dp(14), dp(4), dp(14), dp(4))
                    setOnClickListener {
                        closeTabAt(index)
                        if (tabs.isEmpty()) {
                            dialog.dismiss()
                        } else {
                            dialog.setTitle("标签页（${tabs.size}）")
                            renderRows()
                        }
                    }
                }
                row.addView(close, LinearLayout.LayoutParams(dp(52), dp(48)))
                list.addView(row, LinearLayout.LayoutParams(-1, -2))

                val divider = android.view.View(this).apply {
                    setBackgroundColor(Color.WHITE)
                }
                list.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }

        renderRows()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }

    private fun closeTabAt(index: Int) {
        if (index !in tabs.indices) return
        val wasCurrent = index == currentIndex
        val removed = tabs.removeAt(index)
        removed.web.stopLoading()
        (removed.web.parent as? ViewGroup)?.removeView(removed.web)
        removed.web.destroy()

        when {
            tabs.isEmpty() -> {
                currentIndex = -1
                createTab()
            }
            wasCurrent -> switchTo(index.coerceAtMost(tabs.lastIndex))
            index < currentIndex -> {
                currentIndex--
                updateNavigation()
            }
            else -> updateNavigation()
        }
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

    private fun showWebContent(web: WebView) {
        pageHost.removeAllViews()
        val tab = tabs.firstOrNull { it.web === web } ?: return
        if (tab.url.isBlank() || tab.url == "about:blank") {
            pageHost.addView(buildBlankPage(), FrameLayout.LayoutParams(-1, -1))
        } else {
            (web.parent as? ViewGroup)?.removeView(web)
            pageHost.addView(web, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun loadShortcuts() {
        val saved = getSharedPreferences("browser", Context.MODE_PRIVATE).getString("shortcuts", null)
        if (saved == null) {
            shortcuts.addAll(listOf(
                Shortcut("Google", "https://www.google.com"),
                Shortcut("Instagram", "https://www.instagram.com"),
                Shortcut("X", "https://x.com"),
                Shortcut("Claude", "https://claude.ai"),
                Shortcut("ChatGPT", "https://chatgpt.com")
            ))
            saveShortcuts()
            return
        }
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                if (name.isNotEmpty() && url.isNotEmpty()) shortcuts.add(Shortcut(url, url))
            }
        } catch (_: Exception) {
            shortcuts.clear()
        }
    }

    private fun saveShortcuts() {
        val array = JSONArray()
        shortcuts.forEach {
            array.put(JSONObject().put("name", it.name).put("url", it.url))
        }
        getSharedPreferences("browser", Context.MODE_PRIVATE)
            .edit().putString("shortcuts", array.toString()).apply()
    }

    private fun buildBlankPage(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }
        val grid = GridLayout(this).apply {
            columnCount = 1
            useDefaultMargins = false
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        fun addTile(name: String, url: String?, index: Int = -1) {
            val tile = FrameLayout(this).apply {
                minimumHeight = dp(52)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (url != null) currentWeb()?.loadUrl(url) else showAddShortcutDialog()
                }
                if (index >= 0) {
                    setOnLongClickListener {
                        if (index in shortcuts.indices) {
                            shortcuts.removeAt(index)
                            saveShortcuts()
                            refreshBlankPage()
                            Toast.makeText(this@MainActivity, "快捷入口已删除", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(3), dp(5), dp(3))
            }
            content.addView(TextView(this).apply {
                text = url ?: "＋"
                textSize = if (url == null) 27f else 16f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                maxLines = if (url == null) 1 else 5
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(dp(1).toFloat(), 1f)
            })
            tile.addView(content, FrameLayout.LayoutParams(-1, -2))
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = -2
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, dp(1), 0, dp(1))
            })
        }
        shortcuts.forEachIndexed { index, shortcut -> addTile(shortcut.name, shortcut.url, index) }
        addTile("＋", null)
        scroll.addView(grid, ViewGroup.LayoutParams(-1, -2))
        return scroll
    }

    private fun refreshBlankPage() {
        val tab = tabs.getOrNull(currentIndex) ?: return
        if (tab.url.isBlank() || tab.url == "about:blank") {
            pageHost.removeAllViews()
            pageHost.addView(buildBlankPage(), FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun showAddShortcutDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val urlInput = EditText(this).apply {
            hint = "输入网址"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        fields.addView(urlInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle("新增快捷入口")
            .setView(fields)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var url = urlInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "请输入网址", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
                    url = "https://$url"
                }
                shortcuts.add(Shortcut(url, url))
                saveShortcuts()
                dialog.dismiss()
                refreshBlankPage()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }

    private fun currentWeb(): WebView? = tabs.getOrNull(currentIndex)?.web

    private fun updateNavigation() {
        if (!::backButton.isInitialized) return
        val web = currentWeb()
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
            addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.BLACK, 12))
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
