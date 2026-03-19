package com.example.watermeter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imgSplashLogo)
        val welcomeText = findViewById<TextView>(R.id.txtWelcome)

        // Reset alpha just in case
        logo.alpha = 0f
        welcomeText.alpha = 0f

        // Use ViewPropertyAnimator for more reliable animation
        logo.animate()
            .alpha(1f)
            .setDuration(3000)
            .start()

        welcomeText.animate()
            .alpha(1f)
            .setDuration(3000)
            .start()

        // 5 second delay before moving to LoginActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 5000)
    }
}
