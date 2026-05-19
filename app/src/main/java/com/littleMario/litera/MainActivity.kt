package com.littleMario.litera

import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.io.DataInputStream
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var nodes: Array<Node>
    private val rootId = 0

    private lateinit var inputField: EditText
    private lateinit var wordList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var infoLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var clearButton: Button
    private lateinit var searchAllButton: Button
    private lateinit var searchFromAllButton: Button
    private lateinit var programDescription: TextView
    private lateinit var adView: AdView
    private lateinit var webView: WebView
    private lateinit var closeButton: ImageButton
    private lateinit var closeWebViewButton: Button
    private lateinit var showDescriptionButton: Button

    private val executorService = Executors.newFixedThreadPool(4)

    private val POLISH_LETTERS =
        "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val requestConfiguration = RequestConfiguration.Builder()
            .setMaxAdContentRating(
                RequestConfiguration.MAX_AD_CONTENT_RATING_G
            )
            .build()

        MobileAds.initialize(this) {}

        adView = findViewById(R.id.adView)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        inputField = findViewById(R.id.inputField)
        wordList = findViewById(R.id.wordList)
        infoLabel = findViewById(R.id.infoLabel)
        progressBar = findViewById(R.id.progressBar)
        clearButton = findViewById(R.id.clearButton)
        searchAllButton = findViewById(R.id.searchAllButton)
        searchFromAllButton = findViewById(R.id.searchFromAllButton)
        programDescription = findViewById(R.id.programDescription)
        closeButton = findViewById(R.id.closeButton)
        closeWebViewButton = findViewById(R.id.closeWebViewButton)
        showDescriptionButton = findViewById(R.id.showDescriptionButton)

        setThemeColors()

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )

        wordList.adapter = adapter

        infoLabel.visibility = View.INVISIBLE
        progressBar.visibility = View.GONE
        clearButton.visibility = View.GONE
        searchAllButton.visibility = View.GONE
        searchFromAllButton.visibility = View.GONE
        inputField.visibility = View.GONE

        loadDatabaseFromFile()

        inputField.apply {

            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            addTextChangedListener(object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val newText = s.toString()
                        .lowercase(Locale.getDefault())

                    adapter.clear()
                    infoLabel.visibility = View.INVISIBLE

                    val blanks =
                        newText.count { it == '?' }

                    if (blanks > 2) {

                        Toast.makeText(
                            this@MainActivity,
                            "Dozwolone są maksymalnie 2 blanki.",
                            Toast.LENGTH_SHORT
                        ).show()

                        val cleaned =
                            newText.dropLast(1)

                        inputField.setText(cleaned)
                        inputField.setSelection(cleaned.length)

                        return
                    }

                    val disallowedChar = newText.find {
                        it !in POLISH_LETTERS && it != '?'
                    }

                    if (disallowedChar != null) {

                        Toast.makeText(
                            this@MainActivity,
                            "Nieprawidłowy znak.",
                            Toast.LENGTH_SHORT
                        ).show()

                        val cleaned =
                            newText.replace(
                                disallowedChar.toString(),
                                ""
                            )

                        inputField.setText(cleaned)
                        inputField.setSelection(cleaned.length)

                        return
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }

        clearButton.setOnClickListener {

            inputField.text.clear()
            adapter.clear()

            infoLabel.visibility = View.INVISIBLE
        }

        searchAllButton.setOnClickListener {

            val input =
                inputField.text.toString()

            if (input.length >= 2) {
                searchAllWords(input)
            }
        }

        searchFromAllButton.setOnClickListener {

            val input =
                inputField.text.toString()

            if (input.length >= 2) {
                searchWords(input)
            }
        }
    }

    // =====================================================
    // LOAD DAWG
    // =====================================================

    private fun loadDatabaseFromFile() {

        progressBar.visibility = View.VISIBLE

        executorService.submit {

            try {

                val reader = DawgReader()

                nodes = reader.load(
                    DataInputStream(
                        assets.open("dictionary.dawg")
                    )
                )

                runOnUiThread {

                    progressBar.visibility = View.GONE

                    inputField.visibility = View.VISIBLE
                    clearButton.visibility = View.VISIBLE
                    searchAllButton.visibility = View.VISIBLE
                    searchFromAllButton.visibility = View.VISIBLE
                }

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {

                    progressBar.visibility = View.GONE

                    Toast.makeText(
                        this,
                        "Błąd ładowania słownika",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // =====================================================
    // SEARCH
    // =====================================================

    private fun searchWords(inputLetters: String) {

        val cleaned =
            inputLetters.lowercase(Locale.getDefault())

        executorService.submit {

            val result =
                findAllWords(cleaned)

            runOnUiThread {

                adapter.clear()
                adapter.addAll(result)

                infoLabel.visibility = View.VISIBLE

                if (result.isEmpty()) {
                    infoLabel.text =
                        "Nie znaleziono słów."
                } else {
                    infoLabel.text =
                        "Znaleziono ${result.size} słów."
                }
            }
        }
    }

    private fun searchAllWords(inputLetters: String) {

        val cleaned =
            inputLetters.lowercase(Locale.getDefault())

        executorService.submit {

            val result =
                findAllWords(cleaned)

            runOnUiThread {

                adapter.clear()
                adapter.addAll(result)

                infoLabel.visibility = View.VISIBLE

                if (result.isEmpty()) {
                    infoLabel.text =
                        "Nie znaleziono słów."
                } else {
                    infoLabel.text =
                        "Znaleziono ${result.size} słów."
                }
            }
        }
    }

    // =====================================================
    // FIND ALL WORDS
    // =====================================================

    private fun findAllWords(
        input: String
    ): List<String> {

        val rack = input
            .groupingBy { it }
            .eachCount()
            .toMutableMap()

        val result = mutableSetOf<String>()

        dfs(
            rootId,
            StringBuilder(),
            rack,
            result
        )

        return result.sortedWith(
            compareByDescending<String> {
                it.length
            }.thenBy { it }
        )
    }

    // =====================================================
    // DFS
    // =====================================================

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

            val childId = node.next[i]

            if (childId == -1) {
                continue
            }

            val letter = POLISH_LETTERS[i]

            // NORMAL LETTER

            val count = rack[letter] ?: 0

            if (count > 0) {

                rack[letter] = count - 1

                path.append(letter)

                dfs(
                    childId,
                    path,
                    rack,
                    result
                )

                path.deleteCharAt(
                    path.length - 1
                )

                rack[letter] = count
            }

            // BLANK '?'

            val blank =
                rack['?'] ?: 0

            if (blank > 0) {

                rack['?'] = blank - 1

                path.append(letter)

                dfs(
                    childId,
                    path,
                    rack,
                    result
                )

                path.deleteCharAt(
                    path.length - 1
                )

                rack['?'] = blank
            }
        }
    }

    // =====================================================
    // THEME
    // =====================================================

    private fun setThemeColors() {

        val isPowerSaveMode =
            (
                    getSystemService(POWER_SERVICE)
                            as PowerManager
                    ).isPowerSaveMode

        if (isPowerSaveMode) {

            findViewById<ConstraintLayout>(
                R.id.mainLayout
            ).setBackgroundResource(
                R.drawable.background_energysaver
            )

        } else {

            findViewById<ConstraintLayout>(
                R.id.mainLayout
            ).setBackgroundResource(
                R.drawable.background
            )
        }
    }

    // =====================================================

    override fun onDestroy() {

        super.onDestroy()

        executorService.shutdown()
        adView.destroy()
    }
}