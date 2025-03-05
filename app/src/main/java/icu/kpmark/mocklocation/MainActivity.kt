package icu.kpmark.mocklocation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    // 声明UI组件
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusTextView: TextView

    private lateinit var mockLocation: EnhancedMockLocation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 使用根视图处理窗口插图
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        mockLocation = EnhancedMockLocation()

        setupButtonListeners()
    }

    private fun initViews() {
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusTextView = findViewById(R.id.statusTextView)
    }

    private fun setupButtonListeners() {
        startButton.setOnClickListener {
            mockLocation.start(this)
            updateUIState(true)
        }

        stopButton.setOnClickListener {
            mockLocation.stop(this)
            updateUIState(false)
        }
    }

    private fun updateUIState(isRunning: Boolean) {
        if (isRunning) {
            // 模拟位置运行中
            startButton.isEnabled = false
            stopButton.isEnabled = true
            statusTextView.text = "状态：运行中"
        } else {
            // 模拟位置已停止
            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusTextView.text = "状态：未运行"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 确保停止位置模拟服务
        mockLocation.stop(this)
    }
}