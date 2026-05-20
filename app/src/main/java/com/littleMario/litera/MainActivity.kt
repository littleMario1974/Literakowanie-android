package com.littleMario.litera

import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.DataInputStream
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var nodes: Array<Node>
    private val rootId = 0

    private lateinit var inputField: EditText
    private lateinit var wordList: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private lateinit var clearButton: Button
    private lateinit var searchButton: Button

    private lateinit var programDescription: TextView
    private lateinit var adView: AdView
    private lateinit var webView: WebView
    private lateinit var closeButton: ImageButton
    private lateinit var showDescriptionButton: Button

    private lateinit var infoLabel: TextView

    private var isDictionaryLoaded = false
    private val executorService = Executors.newFixedThreadPool(4)

    private val POLISH_LETTERS =
        "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this)

        adView = findViewById(R.id.adView)
        webView = findViewById(R.id.webView)

        inputField = findViewById(R.id.inputField)
        wordList = findViewById(R.id.wordList)

        clearButton = findViewById(R.id.clearButton)
        searchButton = findViewById(R.id.searchButton)

        programDescription = findViewById(R.id.programDescription)
        closeButton = findViewById(R.id.closeButton)
        showDescriptionButton = findViewById(R.id.showDescriptionButton)

        infoLabel = findViewById(R.id.infoLabel)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        // ================= WEBVIEW FIX =================
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.d("WEBVIEW", "LOADED: $url")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadData(
                        "<html><body style='font-size:18px;text-align:center;padding:20px;'>Błąd ładowania</body></html>",
                        "text/html",
                        "UTF-8"
                    )
                }
            }
        }

        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        wordList.adapter = adapter

        wordList.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { openDictionary(it) }
        }

        showDescriptionButton.setOnClickListener {
            programDescription.visibility =
                if (programDescription.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        closeButton.setOnClickListener { finish() }

        inputField.visibility = View.GONE
        clearButton.visibility = View.GONE
        infoLabel.visibility = View.GONE

        setThemeColors()
        loadDatabaseFromFile()

        searchButton.setOnClickListener {
            val input = inputField.text.toString()
            if (input.isNotBlank()) searchWords(input)
        }

        findViewById<Button>(R.id.closeWebViewButton).setOnClickListener {
            webView.visibility = View.GONE
            wordList.visibility = View.VISIBLE
            inputField.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE
            searchButton.visibility = View.VISIBLE
            infoLabel.visibility = View.VISIBLE
            adView.visibility = View.VISIBLE
        }
    }

    // ================= OPEN DICTIONARY =================
    private fun openDictionary(word: String) {

        val url = "https://sjp.pwn.pl/szukaj/$word.html"

        wordList.visibility = View.GONE
        inputField.visibility = View.GONE
        clearButton.visibility = View.GONE
        searchButton.visibility = View.GONE
        infoLabel.visibility = View.GONE
        adView.visibility = View.GONE

        webView.visibility = View.VISIBLE
        webView.bringToFront()

        webView.loadUrl(url)
    }

    // ================= REST (bez zmian logicznych) =================
    private fun loadDatabaseFromFile() {
        executorService.submit {
            val reader = DawgReader()
            nodes = reader.load(DataInputStream(assets.open("dictionary.dawg")))
            isDictionaryLoaded = true

            runOnUiThread {
                inputField.visibility = View.VISIBLE
                clearButton.visibility = View.VISIBLE
            }
        }
    }

    private fun searchWords(input: String) {
        executorService.submit {
            val result = findAllWords(input.lowercase(Locale.getDefault()))

            runOnUiThread {
                adapter.clear()
                adapter.addAll(result)
                infoLabel.visibility = View.VISIBLE
                infoLabel.text = "Znaleziono ${result.size} słów"
            }
        }
    }

    private fun findAllWords(input: String): List<String> {
        val rack = input.groupingBy { it }.eachCount().toMutableMap()
        val result = mutableSetOf<String>()
        dfs(rootId, StringBuilder(), rack, result)

        return result.sortedWith(
            compareByDescending<String> { it.length }.thenBy { it }
        )
    }

    private fun dfs(
        nodeId: Int,
        path: StringBuilder,
        rack: MutableMap<Char, Int>,
        result: MutableSet<String>
    ) {
        val node = nodes[nodeId]

        if (node.terminal && path.length > 1) {
            result.add(path.toString())
        }

        for (i in POLISH_LETTERS.indices) {
            val child = node.next[i]
            if (child == -1) continue

            val letter = POLISH_LETTERS[i]
            val count = rack[letter] ?: 0

            if (count > 0) {
                rack[letter] = count - 1
                path.append(letter)

                dfs(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack[letter] = count
            }

            val blank = rack['?'] ?: 0
            if (blank > 0) {
                rack['?'] = blank - 1
                path.append(letter)

                dfs(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack['?'] = blank
            }
        }
    }

    private fun setThemeColors() {
        val isPowerSave =
            (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode

        findViewById<ConstraintLayout>(R.id.mainLayout).setBackgroundResource(
            if (isPowerSave) R.drawable.background_energysaver
            else R.drawable.background
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdown()
        adView.destroy()
    }
}