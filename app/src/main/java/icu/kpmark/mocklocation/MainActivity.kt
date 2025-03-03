package icu.kpmark.mocklocation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// MainActivity 继承自 AppCompatActivity，用于管理应用界面和生命周期
class MainActivity : AppCompatActivity() {
    // 声明UI组件
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusTextView: TextView

    // 声明位置模拟器
    private lateinit var mockLocation: EnhancedMockLocation

    // onCreate 方法在 Activity 创建时调用，是程序的入口方法
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

        // 初始化UI组件
        initViews()

        // 初始化位置模拟器
        mockLocation = EnhancedMockLocation()

        // 设置按钮点击事件
        setupButtonListeners()
    }

    // 初始化UI组件
    private fun initViews() {
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusTextView = findViewById(R.id.statusTextView)
    }

    // 设置按钮点击事件
    private fun setupButtonListeners() {
        // 启动按钮点击事件
        startButton.setOnClickListener {
            // 启动位置模拟
            mockLocation.start(this)

            // 更新UI状态
            updateUIState(true)
        }

        // 停止按钮点击事件
        stopButton.setOnClickListener {
            // 停止位置模拟
            mockLocation.stop(this)

            // 更新UI状态
            updateUIState(false)
        }
    }

    // 更新UI状态
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

    // 在Activity销毁时停止位置模拟
    override fun onDestroy() {
        super.onDestroy()

        // 确保停止位置模拟服务
        mockLocation.stop(this)
    }
}