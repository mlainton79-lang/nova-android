package com.mlainton.nova

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tony Status screen (R1.20 Phase B).
 *
 * Will read /api/v1/status and render Tony's operational self-knowledge:
 * health, state, infrastructure, identity. The Run Ledger made visible.
 *
 * B1a: skeleton only - placeholder TextView, no API call yet.
 *      Wired into the drawer in B1b. API call lives in B2.
 */
class TonyStatusActivity : AppCompatActivity() {

    private lateinit var placeholderText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tony_status)
        placeholderText = findViewById(R.id.tonyStatusPlaceholder)
        placeholderText.text = "Tony Status — placeholder.\nLive data lands in B2."
    }
}
