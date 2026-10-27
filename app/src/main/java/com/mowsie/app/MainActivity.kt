
package com.mowsie.app

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Mowsie interface
        val IdentityName = findViewById<TextView>(R.id.identityName)
        val QrPlaceholder = findViewById<TextView>(R.id.qrPlaceholder)
        val SendButton = findViewById<Button>(R.id.sendButton)

        // Remember the local Stash name
        val Preferences = getSharedPreferences(
            "MowsieNest",
            MODE_PRIVATE
        )

        IdentityName.text = Preferences.getString(
            "StashName",
            getString(R.string.stash_name)
        )

        // Tap the identity name to rename it
        IdentityName.setOnClickListener {

            val NameInput = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS

                filters = arrayOf(
                    InputFilter.LengthFilter(40)
                )

                setSingleLine(true)
                setText(IdentityName.text)
                selectAll()
            }

            val Padding = (24 * resources.displayMetrics.density).toInt()

            val Container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Padding, 0, Padding, 0)
                addView(NameInput)
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.name_stash)
                .setView(Container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->

                    val NewName = NameInput.text
                        .toString()
                        .trim()

                    if (NewName.isNotEmpty()) {
                        IdentityName.text = NewName

                        Preferences.edit()
                            .putString("StashName", NewName)
                            .apply()
                    }
                }
                .show()
        }

        // Receive is the default mode
        var Sending = false

        // Switch between Send and Receive
        SendButton.setOnClickListener {

            Sending = !Sending

            if (Sending) {
                QrPlaceholder.setText(R.string.scan_placeholder)
                SendButton.setText(R.string.receive)
            } else {
                QrPlaceholder.setText(R.string.qr_placeholder)
                SendButton.setText(R.string.send)
            }
        }

        // Respect Android system bars
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v, insets ->

            val SystemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                SystemBars.left,
                SystemBars.top,
                SystemBars.right,
                SystemBars.bottom
            )

            insets
        }
    }
}
