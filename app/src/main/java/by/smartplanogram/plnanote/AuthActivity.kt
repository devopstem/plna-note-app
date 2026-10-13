package by.smartplanogram.plnanote

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import by.smartplanogram.plnanote.data.UserSettings
import by.smartplanogram.plnanote.databinding.ActivityAuthBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var settings: UserSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authRoot.applyTopContentInset(8)
        settings = UserSettings.get(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { submit() }
        binding.loginInput.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }
    }

    private fun submit() {
        val raw = binding.loginInput.text?.toString().orEmpty()
        if (!settings.login(raw)) {
            Toast.makeText(this, R.string.auth_error, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.auth_success, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
