package com.example.exerciseevaluator

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.exerciseevaluator.databinding.ActivityHomePageBinding

class HomePage : AppCompatActivity() {
    private lateinit var binding: ActivityHomePageBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        binding = ActivityHomePageBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        /* listen to touch on card */

        binding.pushUpCard.setOnClickListener {
            val cameraIntent = Intent(this@HomePage, LivePreviewActivity::class.java)
            cameraIntent.putExtra("used_for", "push_up")
            startActivity(cameraIntent)
        }
    }
}