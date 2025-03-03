package icu.kpmark.mocklocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.*
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

class EnhancedMockLocation {
    private val isRunning = AtomicBoolean(false)
    private var mockLocationJob: Job? = null

    // 所有可能的位置提供者
    private val providers = arrayOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        "passive",
        "fused",
        "merged",
        "combined"
    )

    // 用于反射的方法
    private var reflectionMethods = mutableMapOf<String, Method?>()

    // 用于存储伪造轨迹点
    private val locationQueue = ConcurrentLinkedQueue<LocationPoint>()

    // 真实轨迹生成器
    private val pathGenerator = RealisticPathGenerator()

    // 设备信息模拟
    private val deviceInfoMocker = DeviceInfoMocker()

    // 位置更新频率 (毫秒)
    private val updateIntervalMs = 1000L  // 降低频率，防止频繁更新导致的崩溃

    // 模拟运动状态
    private var isPaused = false
    private var currentSpeed = 0.0
    private var targetSpeed = 0.0
    private var lastUpdateTime = 0L

    // 记录历史位置用于计算更流畅的速度和方向
    private val locationHistory = mutableListOf<LocationPoint>()
    private val historyMaxSize = 5

    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        startLat: Double = 39.0851850966623,
        startLng: Double = 121.80852250614294,
        initialSpeed: Double = 0.1
    ) {
        // 防止重复启动
        if (isRunning.getAndSet(true)) {
            Toast.makeText(context, "位置模拟服务已在运行", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "位置模拟服务启动中...", Toast.LENGTH_SHORT).show()

        // 设置初始目标速度
        targetSpeed = initialSpeed
        currentSpeed = 0.0

        // 初始化位置历史记录
        locationHistory.clear()

        // 预先生成轨迹 - 生成一个更自然的跑步轨迹
        loadRealisticPath(startLat, startLng)

        // 尝试通过反射获取所有可能有用的方法
        initReflectionMethods()

        // 启动模拟位置服务
        mockLocationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // 为所有提供者设置模拟位置
                setupAllProviders(locationManager)

                // 预热系统，使用较少的预热次数
                warmupLocationServices(locationManager, 3)

                // 创建主线程handler处理UI更新和额外的位置回调
                val mainHandler = Handler(Looper.getMainLooper())

                // 记录上次更新时间
                lastUpdateTime = SystemClock.elapsedRealtime()

                // 主循环
                while (isRunning.get() && isActive) {
                    try {
                        // 计算时间差
                        val currentTime = SystemClock.elapsedRealtime()
                        val deltaTimeSeconds = (currentTime - lastUpdateTime) / 1000.0
                        lastUpdateTime = currentTime

                        // 如果暂停状态，则逐渐降低速度
                        if (isPaused) {
                            currentSpeed = max(0.0, currentSpeed - 0.5 * deltaTimeSeconds)
                        } else {
                            // 平滑过渡到目标速度
                            val speedDiff = targetSpeed - currentSpeed
                            if (abs(speedDiff) > 0.01) {
                                // 加速度限制，模拟自然加减速
                                val maxSpeedChange = 0.3 * deltaTimeSeconds
                                currentSpeed += speedDiff.coerceIn(-maxSpeedChange, maxSpeedChange)
                            } else {
                                currentSpeed = targetSpeed
                            }
                        }

                        // 如果有足够的点，获取下一个位置点
                        if (locationQueue.isEmpty()) {
                            // 生成更多点
                            val lastPoint = locationHistory.lastOrNull() ?: LocationPoint(
                                startLat,
                                startLng,
                                0.0,
                                0.0
                            )
                            extendPath(lastPoint.latitude, lastPoint.longitude)
                        }

                        // 获取下一个位置，安全处理队列可能为空的情况
                        val nextLocation = if (currentSpeed > 0.01 && !locationQueue.isEmpty()) {
                            locationQueue.poll()
                        } else {
                            // 原地振动以模拟GPS漂移
                            val lastPoint = locationHistory.lastOrNull() ?: LocationPoint(
                                startLat,
                                startLng,
                                0.0,
                                0.0
                            )
                            val jitter = 0.000005 * Random.nextDouble() * sin(currentTime / 1000.0)
                            LocationPoint(
                                lastPoint.latitude + jitter,
                                lastPoint.longitude + jitter,
                                0.0,
                                Random.nextDouble() * 360
                            )
                        }

                        // 维护位置历史以便计算平滑的速度和方向
                        if (locationHistory.size >= historyMaxSize) {
                            locationHistory.removeAt(0)
                        }

                        // 安全检查：确保nextLocation不为null
                        nextLocation?.let { location ->
                            locationHistory.add(location)

                            // 计算平滑的方向和速度
                            val smoothedBearing = calculateSmoothedBearing()
                            val smoothedSpeed = if (currentSpeed > 0.1) currentSpeed else 0.0

                            // 应用位置
                            val randomizedSpeed = smoothedSpeed * (0.95 + 0.1 * Random.nextDouble())
                            val randomizedBearing =
                                (smoothedBearing + Random.nextDouble() * 3 - 1.5).toFloat()

                            // 使用标准方法设置位置，避免反射导致的问题
                            for (provider in providers) {
                                try {
                                    if (locationManager.getProvider(provider) != null) {
                                        setEnhancedMockLocation(
                                            locationManager,
                                            provider,
                                            location.latitude,
                                            location.longitude,
                                            randomizedSpeed,
                                            randomizedBearing
                                        )
                                    }
                                } catch (e: Exception) {
                                    // 忽略个别提供者的错误
                                }
                            }
                        }

                        // 等待到下一个更新周期
                        delay(updateIntervalMs)
                    } catch (e: Exception) {
                        // 捕获循环内的异常但继续执行
                        continue
                    }
                }

                // 清理
                cleanupProviders(locationManager)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "模拟位置服务错误: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
                stop(context)
            }
        }
    }

    // 设置暂停/继续运动
    fun togglePause() {
        isPaused = !isPaused
    }

    // 设置目标速度 (米/秒)
    fun setTargetSpeed(speedMps: Double) {
        targetSpeed = speedMps.coerceIn(0.0, 1.0)  // 限制最高速度为6m/s
    }

    // 预热位置服务，发送一系列初始位置以稳定系统
    private fun warmupLocationServices(locationManager: LocationManager, count: Int) {
        try {
            val firstPoint = locationQueue.peek() ?: return

            // 发送多个位置更新以预热系统
            for (i in 0 until count) {
                for (provider in providers) {
                    try {
                        if (locationManager.getProvider(provider) != null) {
                            val jitter = 0.000001 * (i - count / 2)
                            setEnhancedMockLocation(
                                locationManager,
                                provider,
                                firstPoint.latitude + jitter,
                                firstPoint.longitude + jitter,
                                0.0,
                                0f
                            )
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }

                // 短暂延迟
                Thread.sleep(100)
            }
        } catch (e: Exception) {
            // 忽略整个预热过程的错误
        }
    }

    // 计算基于历史位置的平滑方向
    private fun calculateSmoothedBearing(): Double {
        if (locationHistory.size < 2) return 0.0

        // 计算最近几个点的加权平均方向
        var sinSum = 0.0
        var cosSum = 0.0

        for (i in 1 until locationHistory.size) {
            val weight = i.toDouble() / locationHistory.size
            val angle = locationHistory[i].bearing * Math.PI / 180.0
            sinSum += weight * sin(angle)
            cosSum += weight * cos(angle)
        }

        return (atan2(sinSum, cosSum) * 180.0 / Math.PI + 360) % 360
    }

    // 初始化可能有用的反射方法 - 简化反射操作以减少崩溃可能
    private fun initReflectionMethods() {
        // 仅保留最可能成功的方法
        try {
            reflectionMethods["setLocation"] = LocationManager::class.java.getDeclaredMethod(
                "setLocation",
                String::class.java,
                Location::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            // 忽略错误
        }
    }

    // 设置所有位置提供者
    @SuppressLint("MissingPermission")
    private fun setupAllProviders(locationManager: LocationManager) {
        // 只使用主要的提供者，减少可能的错误
        val mainProviders = arrayOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        for (provider in mainProviders) {
            try {
                // 检查提供者是否已经存在
                if (locationManager.getAllProviders().contains(provider)) {
                    try {
                        locationManager.removeTestProvider(provider)
                    } catch (e: Exception) {
                        // 忽略可能的异常
                    }
                }

                // 添加测试提供者，使用适当的精度和权限设置
                locationManager.addTestProvider(
                    provider,
                    true,   // requiresNetwork
                    true,   // requiresSatellite
                    true,   // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )

                // 启用测试提供者
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                // 忽略个别提供者的错误
            }
        }
    }

    // 创建增强的模拟位置对象 - 简化版本，确保兼容性
    private fun createEnhancedMockLocation(
        provider: String,
        latitude: Double,
        longitude: Double,
        speedMps: Double,
        bearing: Float
    ): Location {
        val mockLocation = Location(provider)
        mockLocation.latitude = latitude
        mockLocation.longitude = longitude

        // 根据运动状态计算海拔
        val altitude = 50.0 + 5.0 * sin(latitude * 100) + 5.0 * cos(longitude * 100)
        mockLocation.altitude = altitude

        // 精度设置
        mockLocation.accuracy = 3.0f
        mockLocation.time = System.currentTimeMillis()
        mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

        // 设置速度
        mockLocation.speed = speedMps.toFloat()

        // 设置方向
        mockLocation.bearing = bearing

        // 高版本的精度设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mockLocation.bearingAccuracyDegrees = 1.0f
            mockLocation.speedAccuracyMetersPerSecond = 0.5f
            mockLocation.verticalAccuracyMeters = 2.0f
        }

        return mockLocation
    }

    // 设置增强的模拟位置 - 简化版本
    private fun setEnhancedMockLocation(
        locationManager: LocationManager,
        provider: String,
        latitude: Double,
        longitude: Double,
        speedMps: Double,
        bearing: Float
    ) {
        try {
            val mockLocation =
                createEnhancedMockLocation(provider, latitude, longitude, speedMps, bearing)

            // 设置模拟位置
            locationManager.setTestProviderLocation(provider, mockLocation)
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    // 清理所有提供者
    private fun cleanupProviders(locationManager: LocationManager) {
        for (provider in providers) {
            try {
                if (locationManager.getAllProviders().contains(provider)) {
                    locationManager.removeTestProvider(provider)
                }
            } catch (e: Exception) {
                // 忽略可能的异常
            }
        }
    }

    fun stop(context: Context) {
        if (isRunning.getAndSet(false)) {
            mockLocationJob?.cancel()
            mockLocationJob = null

            // 移除所有测试提供者
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                cleanupProviders(locationManager)
            } catch (e: Exception) {
                // 忽略可能的异常
            }

            Toast.makeText(context, "高级位置模拟服务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    // 加载一个真实的跑步路线
    private fun loadRealisticPath(startLat: Double, startLng: Double) {
        locationQueue.clear()

        // 生成一个真实的跑步路线
        val pathPoints = pathGenerator.generateRunningPath(startLat, startLng)

        // 加入队列
        locationQueue.addAll(pathPoints)
    }

    // 扩展路径
    private fun extendPath(lastLat: Double, lastLng: Double) {
        val newPoints = pathGenerator.extendPath(lastLat, lastLng)
        locationQueue.addAll(newPoints)
    }

    // 位置点数据类
    data class LocationPoint(
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val bearing: Double
    )

    // 真实路径生成器
    inner class RealisticPathGenerator {
        // 路线规划参数
        private val segmentLength = 3.0  // 每段路径长度（米）
        private val turnProbability = 0.2  // 转弯概率
        private val maxTurnAngle = 30.0  // 最大转弯角度

        // 路径随机性
        private val random = Random(System.currentTimeMillis())

        // 生成初始跑步路径
        fun generateRunningPath(startLat: Double, startLng: Double): List<LocationPoint> {
            val points = mutableListOf<LocationPoint>()

            var currentLat = startLat
            var currentLng = startLng
            var currentBearing = 0.0  // 初始方向（正北）

            // 减少生成的点数，避免内存问题
            for (i in 0 until 100) {
                // 路径规划逻辑
                if (random.nextDouble() < turnProbability) {
                    // 随机转弯
                    val turnAngle = random.nextDouble() * maxTurnAngle * 2 - maxTurnAngle
                    currentBearing = (currentBearing + turnAngle + 360.0) % 360.0
                }

                // 计算下一个点
                val distance = segmentLength / 111320.0  // 转换为度
                val dx = distance * sin(Math.toRadians(currentBearing))
                val dy = distance * cos(Math.toRadians(currentBearing))

                currentLat += dy
                currentLng += dx

                // 添加到路径
                points.add(LocationPoint(currentLat, currentLng, 0.0, currentBearing))
            }

            return points
        }

        // 扩展现有路径
        fun extendPath(lastLat: Double, lastLng: Double): List<LocationPoint> {
            val points = mutableListOf<LocationPoint>()

            var currentLat = lastLat
            var currentLng = lastLng

            // 计算当前方向（如果是连续的路径）
            val prevBearing = locationHistory.lastOrNull()?.bearing ?: 0.0
            var currentBearing = prevBearing

            // 减少生成的点数
            for (i in 0 until 50) {
                // 路径规划逻辑
                if (random.nextDouble() < turnProbability) {
                    // 随机转弯，但保持转弯幅度温和
                    val turnAngle = random.nextDouble() * maxTurnAngle * 2 - maxTurnAngle
                    currentBearing = (currentBearing + turnAngle + 360.0) % 360.0
                }

                // 计算下一个点
                val distance = segmentLength / 111320.0  // 转换为度
                val dx = distance * sin(Math.toRadians(currentBearing))
                val dy = distance * cos(Math.toRadians(currentBearing))

                currentLat += dy
                currentLng += dx

                // 添加到路径
                points.add(LocationPoint(currentLat, currentLng, 0.0, currentBearing))
            }

            return points
        }
    }

    // 设备信息模拟器 - 简化版本
    inner class DeviceInfoMocker {
        // 卫星信息数据类
        inner class SatelliteInfo(
            val id: Int,
            val elevation: Int,
            val azimuth: Int,
            val snr: Float,
            val used: Boolean
        )

        // 随机信号强度变化
        fun getGpsSignalStrength(): Float {
            return 0.8f + Random.nextFloat() * 0.2f
        }

        // 模拟卫星信息
        fun getSatellitesInfo(): List<SatelliteInfo> {
            val satellites = mutableListOf<SatelliteInfo>()
            // 减少卫星数量，避免可能的内存问题
            val visibleCount = 5 + Random.nextInt(3)

            for (i in 0 until visibleCount) {
                satellites.add(
                    SatelliteInfo(
                        id = Random.nextInt(1, 32),
                        elevation = Random.nextInt(15, 90),
                        azimuth = Random.nextInt(0, 360),
                        snr = 25f + Random.nextFloat() * 20f,
                        used = Random.nextBoolean()
                    )
                )
            }

            return satellites
        }
    }
}