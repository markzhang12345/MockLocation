package icu.kpmark.mocklocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.*
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

    private val locationQueue = ConcurrentLinkedQueue<LocationPoint>()
    private val updateIntervalMs = 1000L  // 降低频率

    // 模拟运动状态
    private var isPaused = false
    private var currentSpeed = 0.0
    private var targetSpeed = 1.5
    private var lastUpdateTime = 0L

    // 记录历史位置用于计算更流畅的速度和方向
    private val locationHistory = mutableListOf<LocationPoint>()
    private val historyMaxSize = 5

    // 存储固定路径的坐标点
    private val fixedPath = listOf(
        Pair(121.80852250614294, 39.0851850966623),
        Pair(121.80853150614294, 39.0861470966623),
        Pair(121.80849950614294, 39.0862590966623),
        Pair(121.80847250614293, 39.0862910966623),
        Pair(121.80838350614295, 39.0863570966623),
        Pair(121.80817250614294, 39.0864660966623),
        Pair(121.80780850614293, 39.0864060966623),
        Pair(121.80768650614294, 39.0863050966623),
        Pair(121.80764250614294, 39.0862000966623),
        Pair(121.80762850614293, 39.0851640966623),
        Pair(121.80766950614294, 39.0850620966623),
        Pair(121.80804650614294, 39.0848380966623),
        Pair(121.80827950614294, 39.0848590966623),
        Pair(121.80827950614294, 39.0848590966623),
        Pair(121.80846850614294, 39.0849850966623),
        Pair(121.80846850614294, 39.0849850966623)
    )

    // 当前路径点索引
    private var currentPathIndex = 0
    private var interpolationPosition = 0.0

    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        startLat: Double = 39.0851850966623,
        startLng: Double = 121.80852250614294,
        initialSpeed: Double = 1.5
    ) {
        if (isRunning.getAndSet(true)) {
            Toast.makeText(context, "位置模拟服务已在运行", Toast.LENGTH_SHORT).show()
            return
        }

        // 设置初始目标速度
        targetSpeed = initialSpeed
        currentSpeed = 0.0

        // 初始化位置历史记录
        locationHistory.clear()
        currentPathIndex = 0
        interpolationPosition = 0.0

        // 启动模拟位置服务
        mockLocationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                setupAllProviders(locationManager)

                warmupLocationServices(locationManager, 3)

                lastUpdateTime = SystemClock.elapsedRealtime()

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
                            val speedDiff = targetSpeed - currentSpeed
                            if (abs(speedDiff) > 0.01) {
                                val maxSpeedChange = 0.3 * deltaTimeSeconds
                                currentSpeed += speedDiff.coerceIn(-maxSpeedChange, maxSpeedChange)
                            } else {
                                currentSpeed = targetSpeed
                            }
                        }

                        val nextLocation = getNextFixedPathLocation(deltaTimeSeconds)

                        if (locationHistory.size >= historyMaxSize) {
                            locationHistory.removeAt(0)
                        }

                        // 安全检查
                        nextLocation?.let { location ->
                            locationHistory.add(location)

                            // 计算平滑的方向和速度
                            val smoothedBearing = calculateSmoothedBearing()
                            val smoothedSpeed = if (currentSpeed > 0.1) currentSpeed else 0.0

                            // 应用位置
                            val randomizedSpeed = smoothedSpeed * (0.95 + 0.1 * Random.nextDouble())
                            val randomizedBearing =
                                (smoothedBearing + Random.nextDouble() * 3 - 1.5).toFloat()

                            // 使用标准方法设置位置
                            for (provider in providers) {
                                try {
                                    setEnhancedMockLocation(
                                        locationManager,
                                        provider,
                                        location.latitude,
                                        location.longitude,
                                        randomizedSpeed,
                                        randomizedBearing
                                    )
                                } catch (e: Exception) {
                                    // 忽略错误
                                }
                            }
                        }

                        delay(updateIntervalMs)
                    } catch (e: Exception) {
                        continue
                    }
                }
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

    // 获取固定路径上的下一个位置点
    private fun getNextFixedPathLocation(deltaTimeSeconds: Double): LocationPoint? {
        if (fixedPath.isEmpty()) return null

        val currentIndex = currentPathIndex
        val nextIndex = (currentPathIndex + 1) % fixedPath.size
        val currentPoint = fixedPath[currentIndex]
        val nextPoint = fixedPath[nextIndex]

        val distance = calculateDistance(
            currentPoint.second, currentPoint.first,
            nextPoint.second, nextPoint.first
        )

        val travelDistance = currentSpeed * deltaTimeSeconds

        interpolationPosition += travelDistance / distance

        if (interpolationPosition >= 1.0) {
            currentPathIndex = nextIndex
            interpolationPosition -= 1.0
        }

        // 插值计算当前位置
        val lat =
            currentPoint.second + (nextPoint.second - currentPoint.second) * interpolationPosition
        val lng =
            currentPoint.first + (nextPoint.first - currentPoint.first) * interpolationPosition

        // 计算当前方向（从当前点到下一点的方向）
        val bearing = calculateBearing(
            currentPoint.second, currentPoint.first,
            nextPoint.second, nextPoint.first
        )

        return LocationPoint(lat, lng, currentSpeed, bearing)
    }

    // 计算两点间的距离（米）
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // 地球半径，单位米
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    // 计算两点间的方位角
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        return (Math.toDegrees(theta) + 360) % 360
    }

    // 预热位置服务
    private fun warmupLocationServices(locationManager: LocationManager, count: Int) {
        try {
            val firstPoint = locationQueue.peek() ?: return

            // 发送多个位置更新以预热系统
            for (i in 0 until count) {
                for (provider in providers) {
                    try {
                        val jitter = 0.000001 * (i - count / 2)
                        setEnhancedMockLocation(
                            locationManager,
                            provider,
                            firstPoint.latitude + jitter,
                            firstPoint.longitude + jitter,
                            0.0,
                            0f
                        )
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
                if (locationManager.allProviders.contains(provider)) {
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

    // 创建增强的模拟位置对象
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
                if (locationManager.allProviders.contains(provider)) {
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

            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                cleanupProviders(locationManager)
            } catch (e: Exception) {
                // 忽略可能的异常
            }

            Toast.makeText(context, "位置模拟服务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    // 位置点数据类
    data class LocationPoint(
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val bearing: Double
    )
}