
package com.example.guarddns

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("guarddns_admin", MODE_PRIVATE)
    }

    private fun hashPassword(
        password: CharArray,
        salt: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private fun encode(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String) =
        Base64.decode(value, Base64.NO_WRAP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getString("salt", null) == null) {
            showPasswordSetup()
        } else {
            showUnlockScreen()
        }
    }

    private fun page(title: String): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 32)
        }

        layout.addView(TextView(this).apply {
            text = title
            textSize = 26f
        })

        setContentView(layout)
        return layout
    }

    private fun addText(
        layout: LinearLayout,
        text: String
    ) {
        layout.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 20, 0, 20)
        })
    }

    private fun addButton(
        layout: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        layout.addView(Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }, ViewGroup.LayoutParams.MATCH_PARENT,
           ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun passwordField(
        hintText: String
    ) = EditText(this).apply {
        hint = hintText
        inputType = 129 // textPassword
        singleLine = true
    }

    private fun showPasswordSetup() {
        val layout = page("Set administrator password")
        addText(
            layout,
            "Ask your trusted administrator to create a password " +
            "and keep it somewhere other than this phone."
        )

        val password = passwordField("New administrator password")
        layout.addView(password)

        addButton(layout, "Save password") {
            val chars = password.text.toString().toCharArray()
            if (chars.size < 12) {
                chars.fill('\u0000')
                Toast.makeText(
                    this,
                    "Use at least 12 characters.",
                    Toast.LENGTH_LONG
                ).show()
                return@addButton
            }

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val hash = hashPassword(chars, salt)

            prefs.edit()
                .putString("salt", encode(salt))
                .putString("hash", encode(hash))
                .apply()

            showUnlockScreen()
        }
    }

    private fun showUnlockScreen() {
        val layout = page("GuardDNS")
        addText(
            layout,
            "Enter the administrator password to open the controls."
        )

        val password = passwordField("Administrator password")
        layout.addView(password)

        addButton(layout, "Unlock controls") {
            val savedSalt = prefs.getString("salt", null)
            val savedHash = prefs.getString("hash", null)

            if (savedSalt == null || savedHash == null) {
                showPasswordSetup()
                return@addButton
            }

            val entered = hashPassword(
                password.text.toString().toCharArray(),
                decode(savedSalt)
            )

            val expected = decode(savedHash)
            val matches = java.security.MessageDigest
                .isEqual(entered, expected)

            if (matches) {
                showDashboard()
            } else {
                Toast.makeText(
                    this,
                    "Incorrect password.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showDashboard() {
        val layout = page("GuardDNS protection")
        addText(
            layout,
            "Prototype status: DNS filtering is not verified by this app."
        )

        addText(
            layout,
            "1. Open Android connection settings.\n" +
            "2. Find Private DNS.\n" +
            "3. Set your adult-filtering provider hostname.\n" +
            "4. Test that adult domains are blocked and normal sites work."
        )

        addButton(layout, "Open connection settings") {
            startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        }

        addButton(layout, "Lock administrator controls") {
            showUnlockScreen()
        }
    }
}
