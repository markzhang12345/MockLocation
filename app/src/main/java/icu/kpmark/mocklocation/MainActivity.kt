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

    private lateinit var mockLocation: EnhancedMockLocation // 位置模拟器

    override fun onCreate(savedInstanceState: Bundle?) { // 空Bundle对象，用于保存Activity的状态
        super.onCreate(savedInstanceState) // 调用父类的onCreate方法，确保正常初始化
        enableEdgeToEdge()
        setContentView(R.layout.activity_main) // 找到布局文件并加载

        // 使用根视图处理窗口插图
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews() // 初始化UI组件

        mockLocation = EnhancedMockLocation() // 初始化位置模拟器

        setupButtonListeners() // 设置按钮点击事件
    }

    // 初始化UI组件
    private fun initViews() {
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusTextView = findViewById(R.id.statusTextView)
    }

    // 设置按钮点击事件
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