package com.example.watermeter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val loginPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val userPrefs = getSharedPreferences("UserAccounts", Context.MODE_PRIVATE)

        // Pre-populate admin account if it's the first time
        if (!userPrefs.contains("admin")) {
            userPrefs.edit().putString("admin", "dauin2024").apply()
        }

        // Check if already logged in
        if (loginPrefs.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        setContentView(R.layout.activity_login)

        val editUsername = findViewById<EditText>(R.id.editUsername)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = editUsername.text.toString().trim()
            val password = editPassword.text.toString().trim()

            val storedPassword = userPrefs.getString(username, null)

            if (storedPassword != null && storedPassword == password) {
                loginPrefs.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("currentUsername", username)
                    .apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
