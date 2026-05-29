package com.littleMario.litera

import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.ads.*
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

    private lateinit var adView: AdView

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

        // ================= ADS INIT =================
        MobileAds.initialize(this) {}

        val testDeviceIds = listOf("A791D9B6753D942BB05D366370ED876D")
        val config = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(config)

        // ================= UI =================
        adView = findViewById(R.id.adView)
        inputField = findViewById(R.id.inputField)
        wordList = findViewById(R.id.wordList)

        clearButton = findViewById(R.id.clearButton)
        searchButton = findViewById(R.id.searchButton)

        closeButton = findViewById(R.id.closeButton)
        showDescriptionButton = findViewById(R.id.showDescriptionButton)

        infoLabel = findViewById(R.id.infoLabel)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        wordList.adapter = adapter

        wordList.setOnItemClickListener { _, _, position, _ ->
            val word = adapter.getItem(position)
            if (word != null) openDictionary(word)
        }

        // ================= ADS LOAD =================
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                android.util.Log.d("ADS", "Ad loaded")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                android.util.Log.e("ADS", "Failed: ${error.message}")
            }
        }

        // ================= BUTTONS =================
        closeButton.setOnClickListener { finish() }

        showDescriptionButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Opis programu")
                .setMessage(getString(R.string.program_description_html))
                .setPositiveButton("OK", null)
                .show()
        }

        clearButton.setOnClickListener {
            inputField.setText("")
            adapter.clear()
            infoLabel.visibility = View.GONE
        }

        searchButton.setOnClickListener {
            if (!isDictionaryLoaded || !::nodes.isInitialized) {
                Toast.makeText(this, "Słownik się ładuje...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val input = inputField.text.toString()
            if (input.isBlank()) {
                Toast.makeText(this, "Wpisz litery", Toast.LENGTH_SHORT).show()
            } else {
                searchWords(input)
            }
        }

        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString().lowercase(Locale.getDefault())

                val blanks = text.count { it == '?' }
                if (blanks > 2) {
                    inputField.setText(text.dropLast(1))
                    inputField.setSelection(inputField.text.length)
                    return
                }

                val bad = text.find { it !in POLISH_LETTERS && it != '?' }
                if (bad != null) {
                    inputField.setText(text.replace(bad.toString(), ""))
                    inputField.setSelection(inputField.text.length)
                }
            }
        })

        inputField.visibility = View.GONE
        clearButton.visibility = View.GONE
        infoLabel.visibility = View.GONE

        setThemeColors()
        loadDatabaseFromFile()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top, bottom = sys.bottom)
            insets
        }
    }

    // ================= SEARCH =================
    private fun searchWords(input: String) {
        if (!isDictionaryLoaded || !::nodes.isInitialized) return

        executorService.submit {
            val result = findAllWords(input.lowercase(Locale.getDefault()))

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                adapter.clear()
                adapter.addAll(result)

                infoLabel.visibility = View.VISIBLE
                infoLabel.text = "Znaleziono ${result.size} słów"
            }
        }
    }

    // ================= OPEN DICTIONARY =================
    private fun openDictionary(word: String) {

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
        }

        webView.loadUrl("https://sjp.pl/$word")

        AlertDialog.Builder(this)
            .setView(webView)
            .setPositiveButton("Zamknij") { dialog, _ ->
                webView.destroy()
                dialog.dismiss()
            }
            .show()
    }

    // ================= LOAD =================
    private fun loadDatabaseFromFile() {

        executorService.submit {
            try {
                val reader = DawgReader()
                nodes = reader.load(DataInputStream(assets.open("dictionary.dawg")))
                isDictionaryLoaded = true

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    inputField.visibility = View.VISIBLE
                    clearButton.visibility = View.VISIBLE
                    searchButton.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Błąd słownika", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================= DFS =================
    private fun findAllWords(input: String): List<String> {

        if (!::nodes.isInitialized) return emptyList()

        val rack = IntArray(POLISH_LETTERS.length + 1)

        for (c in input) {
            if (c == '?') rack[POLISH_LETTERS.length]++
            else {
                val idx = POLISH_LETTERS.indexOf(c)
                if (idx != -1) rack[idx]++
            }
        }

        val result = mutableSetOf<String>()
        dfsFast(rootId, StringBuilder(), rack, result)

        return result.sortedByDescending { it.length }
    }

    private fun dfsFast(
        nodeId: Int,
        path: StringBuilder,
        rack: IntArray,
        result: MutableSet<String>
    ) {
        val node = nodes[nodeId]

        if (node.terminal && path.length > 1) {
            result.add(path.toString())
        }

        val blankIndex = POLISH_LETTERS.length

        for (i in POLISH_LETTERS.indices) {

            val child = node.next[i]
            if (child == -1) continue

            val letter = POLISH_LETTERS[i]

            if (rack[i] > 0) {
                rack[i]--
                path.append(letter)

                dfsFast(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack[i]++
            } else if (rack[blankIndex] > 0) {
                rack[blankIndex]--
                path.append(letter)

                dfsFast(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack[blankIndex]++
            }
        }
    }

    // ================= THEME =================
    private fun setThemeColors() {
        val isPowerSave =
            (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode

        findViewById<ConstraintLayout>(R.id.mainLayout)
            .setBackgroundResource(
                if (isPowerSave)
                    R.drawable.background_energysaver
                else
                    R.drawable.background
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdown()
        adView.destroy()
    }
}