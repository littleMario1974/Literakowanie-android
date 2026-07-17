package com.littleMario.litera

import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.SpannableString
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
import android.graphics.Color
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var nodes: Array<Node>
    private val rootId = 0

    private lateinit var inputField: EditText
    private lateinit var wordList: ListView
    private lateinit var adapter: ArrayAdapter<CharSequence>

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
    private var isEditing = false

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


        // UI
        adView = findViewById(R.id.adView)
        inputField = findViewById(R.id.inputField)

        inputField.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        inputField.isAllCaps = false
        inputField.setSingleLine(true)


        wordList = findViewById(R.id.wordList)

        clearButton = findViewById(R.id.clearButton)
        searchButton = findViewById(R.id.searchButton)

        closeButton = findViewById(R.id.closeButton)
        showDescriptionButton = findViewById(R.id.showDescriptionButton)

        infoLabel = findViewById(R.id.infoLabel)

        startFilter = findViewById(R.id.startFilter)
        containsFilter = findViewById(R.id.containsFilter)
        endFilter = findViewById(R.id.endFilter)

        val noSuggestions =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        startFilter.inputType = noSuggestions
        containsFilter.inputType = noSuggestions
        endFilter.inputType = noSuggestions

        startFilter.isAllCaps = false
        containsFilter.isAllCaps = false
        endFilter.isAllCaps = false

        minLengthField = findViewById(R.id.minLength)
        maxLengthField = findViewById(R.id.maxLength)

        setupLetterFilter(inputField, maxBlanks = 2)
        setupLetterFilter(startFilter)
        setupLetterFilter(containsFilter)
        setupLetterFilter(endFilter)

        // WEBVIEW INIT (FIX OOM)
        webContainer = findViewById(R.id.webContainer)
        webView = findViewById(R.id.webView)
        closeWebViewButton = findViewById(R.id.closeWebViewButton)

        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
        webView.webViewClient = WebViewClient()
        closeWebViewButton.setOnClickListener {
            closeDictionary()
        }

        adapter = object : ArrayAdapter<CharSequence>(
    this,
    android.R.layout.simple_list_item_1,
    mutableListOf()
) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {

        val view = super.getView(position, convertView, parent)

        val textView = view.findViewById<TextView>(android.R.id.text1)

        val isPowerSave =
    (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode

val nightMode =
    resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK

textView.setTextColor(
    when {
        isPowerSave -> Color.WHITE

        nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ->
            Color.WHITE

        else ->
            Color.BLACK
    }
)

        return view
    }
}

wordList.adapter = adapter

        wordList.setOnItemClickListener { _, _, position, _ ->

    val text = adapter.getItem(position).toString()

    if (text.startsWith("📌")) {
        return@setOnItemClickListener
    }

    openDictionary(text)
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

                val rack = buildRack(input.lowercase(Locale.getDefault()))

                val colored = result.map { word ->
                    colorWordBlanks(word, rack)
                }

                val grouped = groupWordsByLength(colored)

                adapter.clear()
                adapter.addAll(grouped)

                adapter.notifyDataSetChanged()

                infoLabel.visibility = View.VISIBLE
                infoLabel.text = "Znaleziono ${result.size} słów"
            }
        }
    }

    private fun groupWordsByLength(words: List<CharSequence>): List<CharSequence> {

        val map = words.groupBy { it.toString().length }
            .toSortedMap(compareByDescending { it })

        val result = mutableListOf<CharSequence>()

        for ((length, list) in map) {

            // HEADER (większy efekt niż zwykły tekst)
            val header = SpannableString("\uD83D\uDCCC $length-literowe")
            result.add(header)

            // WORDS
            result.addAll(list.sortedBy { it.toString() })
        }

        return result
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

    if (::adapter.isInitialized) {
        adapter.notifyDataSetChanged()
    }
}

    private fun setupLetterFilter(editText: EditText, maxBlanks: Int = Int.MAX_VALUE) {

        editText.filters = arrayOf(
            android.text.InputFilter { source, _, _, _, _, _ ->
                val allowed = POLISH_LETTERS + "?"

                if (source.all { it.lowercaseChar() in allowed }) {
                    source
                } else {
                    Toast.makeText(
                        this,
                        "Niedozwolony znak",
                        Toast.LENGTH_SHORT
                    ).show()
                    ""
                }
            }
        )

        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                if (isEditing) return

                val text = s.toString()

                val blanks = text.count { it == '?' }

                if (blanks > maxBlanks) {

                    Toast.makeText(
                        this@MainActivity,
                        "Maksymalnie $maxBlanks znaki ?",
                        Toast.LENGTH_SHORT
                    ).show()

                    isEditing = true
                    val corrected = text.dropLast(1)
                    editText.setText(corrected)
                    editText.setSelection(corrected.length)
                    isEditing = false
                }
            }
        })
    }

    private fun colorWordBlanks(word: String, rack: IntArray): CharSequence {

        val temp = rack.copyOf()
        val spannable = android.text.SpannableString(word)

        val blankIndex = POLISH_LETTERS.length

        for (i in word.indices) {

            val c = word[i]
            val idx = POLISH_LETTERS.indexOf(c)

            val usedFromLetterPool = idx != -1 && temp[idx] > 0

            if (usedFromLetterPool) {
                temp[idx]--
            } else if (temp[blankIndex] > 0) {
                // ❗ litera pochodzi z blanka
                temp[blankIndex]--

                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(0xFFFF0000.toInt()),
                    i,
                    i + 1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // ❗ nie powinno się zdarzyć (bez liter)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(0xFFFF0000.toInt()),
                    i,
                    i + 1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

    private fun buildRack(input: String): IntArray {
        val rack = IntArray(POLISH_LETTERS.length + 1)

        for (c in input) {
            if (c == '?') {
                rack[POLISH_LETTERS.length]++
            } else {
                val idx = POLISH_LETTERS.indexOf(c)
                if (idx != -1) {
                    rack[idx]++
                }
            }
        }
        return rack
    }

override fun onResume() {
    super.onResume()
    setThemeColors()
}
    
    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdown()
        adView.destroy()
    }
}


