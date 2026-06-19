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

    private lateinit var startFilter: EditText
    private lateinit var containsFilter: EditText
    private lateinit var endFilter: EditText
    private lateinit var minLengthField: EditText
    private lateinit var maxLengthField: EditText
    private lateinit var webProgress: ProgressBar

    // WEBVIEW FIX
    private lateinit var webView: WebView
    private lateinit var webContainer: View
    private lateinit var closeWebViewButton: ImageButton

    private var isDictionaryLoaded = false
    private val executorService = Executors.newFixedThreadPool(4)

    private val POLISH_LETTERS =
        "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż"

    // ================= FILTRY =================
    private var startsWithPattern = ""
    private var endsWithPattern = ""
    private var containsPattern = ""

    private var minLength = 2
    private var maxLength = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}

        val config = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf("A791D9B6753D942BB05D366370ED876D"))
            .build()
        MobileAds.setRequestConfiguration(config)

        // UI
        adView = findViewById(R.id.adView)
        inputField = findViewById(R.id.inputField)
        inputField.inputType =
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        wordList = findViewById(R.id.wordList)

        clearButton = findViewById(R.id.clearButton)
        searchButton = findViewById(R.id.searchButton)

        closeButton = findViewById(R.id.closeButton)
        showDescriptionButton = findViewById(R.id.showDescriptionButton)

        infoLabel = findViewById(R.id.infoLabel)

        startFilter = findViewById(R.id.startFilter)
        containsFilter = findViewById(R.id.containsFilter)
        endFilter = findViewById(R.id.endFilter)
        minLengthField = findViewById(R.id.minLength)
        maxLengthField = findViewById(R.id.maxLength)

        // WEBVIEW INIT (FIX OOM)
        webContainer = findViewById(R.id.webContainer)
        webView = findViewById(R.id.webView)
        closeWebViewButton = findViewById(R.id.closeWebViewButton)
        webProgress = findViewById(R.id.webProgress)
        webProgress.visibility = View.GONE

        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
        webView.webViewClient = WebViewClient()
        closeWebViewButton.setOnClickListener {
            closeDictionary()
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        wordList.adapter = adapter

        wordList.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { openDictionary(it) }
        }

        adView.loadAd(AdRequest.Builder().build())

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
            startFilter.setText("")
            containsFilter.setText("")
            endFilter.setText("")
            minLengthField.setText("")
            maxLengthField.setText("")

            adapter.clear()
            infoLabel.visibility = View.GONE

            inputField.requestFocus()
            inputField.setSelection(0, inputField.text.length)

            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(inputField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        searchButton.setOnClickListener {
            if (!isDictionaryLoaded) {
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
                    Toast.makeText(this@MainActivity, "Max 2 znaki ?", Toast.LENGTH_SHORT).show()
                }

                val bad = text.find { it !in POLISH_LETTERS && it != '?' }
                if (bad != null) {
                    Toast.makeText(this@MainActivity, "Niedozwolony znak", Toast.LENGTH_SHORT).show()
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

        startsWithPattern = startFilter.text.toString().trim().lowercase()
        endsWithPattern = endFilter.text.toString().trim().lowercase()
        containsPattern = containsFilter.text.toString().trim().lowercase()

        minLength = minLengthField.text.toString().toIntOrNull() ?: 2
        maxLength = maxLengthField.text.toString().toIntOrNull() ?: 50

        if (minLength > maxLength) {
            Toast.makeText(this, "Błąd długości", Toast.LENGTH_SHORT).show()
            return
        }

        executorService.submit {

            val result = findAllWords(input.lowercase(Locale.getDefault()))

            runOnUiThread {
                if (isFinishing) return@runOnUiThread

                adapter.clear()
                adapter.addAll(result)

                infoLabel.visibility = View.VISIBLE
                infoLabel.text = "Znaleziono ${result.size} słów"
            }
        }
    }

    // ================= WEBVIEW FIX =================
    private fun openDictionary(word: String) {

        // ukryj górne elementy UI
        showDescriptionButton.visibility = View.GONE
        closeButton.visibility = View.GONE

        webContainer.visibility = View.VISIBLE
        webView.loadUrl("https://sjp.pl/$word")
    }

    private fun closeDictionary() {

        showDescriptionButton.visibility = View.VISIBLE
        closeButton.visibility = View.VISIBLE

        webContainer.visibility = View.GONE
        webView.loadUrl("about:blank")
    }
    override fun onBackPressed() {
        if (webContainer.visibility == View.VISIBLE) {
            closeDictionary()
        } else {
            super.onBackPressed()
        }
    }

    // ================= LOAD =================
    private fun loadDatabaseFromFile() {
        executorService.submit {
            try {
                val reader = DawgReader()
                nodes = reader.load(DataInputStream(assets.open("dictionary.dawg")))
                isDictionaryLoaded = true

                runOnUiThread {
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

        // ❌ PRUNING: jeśli już za długie → stop
        if (path.length > maxLength) return

        // ✔ sprawdzenie słowa TYLKO gdy w zakresie
        if (node.terminal) {
            val len = path.length
            if (len in minLength..maxLength) {
                val word = path.toString()
                if (matchesFilters(word)) {
                    result.add(word)
                }
            }
        }

        val blankIndex = POLISH_LETTERS.length

        for (i in POLISH_LETTERS.indices) {

            val child = node.next[i]
            if (child == -1) continue

            val availableLetter = rack[i] > 0
            val availableBlank = rack[blankIndex] > 0

            if (!availableLetter && !availableBlank) continue

            val letter = POLISH_LETTERS[i]

            if (availableLetter) {
                rack[i]--
                path.append(letter)

                dfsFast(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack[i]++
            } else {
                rack[blankIndex]--
                path.append(letter)

                dfsFast(child, path, rack, result)

                path.deleteCharAt(path.length - 1)
                rack[blankIndex]++
            }
        }
    }

    // ================= FILTRY =================
    private fun matchesFilters(word: String): Boolean {

        if (word.length !in minLength..maxLength) return false

        if (startsWithPattern.isNotEmpty())
            if (!matchPattern(word, startsWithPattern, true)) return false

        if (endsWithPattern.isNotEmpty())
            if (!matchPattern(word, endsWithPattern, false)) return false

        if (containsPattern.isNotEmpty())
            if (!containsWildcardAnywhere(word, containsPattern)) return false

        return true
    }

    private fun matchPattern(word: String, pattern: String, fromStart: Boolean): Boolean {
        if (pattern.length > word.length) return false

        val offset = if (fromStart) 0 else word.length - pattern.length

        for (i in pattern.indices) {
            val p = pattern[i]
            val w = word[offset + i]
            if (p != '?' && p != w) return false
        }
        return true
    }

    private fun containsWildcardAnywhere(word: String, pattern: String): Boolean {

        if (pattern.length > word.length) return false

        for (start in 0..word.length - pattern.length) {
            var ok = true

            for (i in pattern.indices) {
                val p = pattern[i]
                val w = word[start + i]

                if (p != '?' && p != w) {
                    ok = false
                    break
                }
            }

            if (ok) return true
        }
        return false
    }

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

