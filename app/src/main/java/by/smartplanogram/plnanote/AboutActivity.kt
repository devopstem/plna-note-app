package by.smartplanogram.plnanote

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import by.smartplanogram.plnanote.databinding.ActivityAboutBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.aboutRoot.applyTopContentInset(8)
        binding.btnBack.setOnClickListener { finish() }
    }
}
