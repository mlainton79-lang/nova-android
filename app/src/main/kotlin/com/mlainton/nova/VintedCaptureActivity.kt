package com.mlainton.nova

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class VintedCaptureActivity : AppCompatActivity() {

    private var platform: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platformExtra = intent.getStringExtra("platform")
        if (platformExtra.isNullOrBlank()) {
            Log.w("VintedCapture", "No platform extra supplied — finishing")
            finish()
            return
        }
        platform = platformExtra
        Log.i("VintedCapture", "Launched for platform: $platform")

        setContentView(R.layout.activity_vinted_capture)
    }
}
