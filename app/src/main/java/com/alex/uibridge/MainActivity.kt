package com.alex.uibridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alex.uibridge.ui.theme.AlexUIBridgeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val TAG = "AlexUIBridge"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 处理通知权限请求结果
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // 检查并请求悬浮窗权限
        checkOverlayPermission()

        setContent {
            AlexUIBridgeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Alex UI Bridge",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "请求悬浮窗权限")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Log.d(TAG, "已有悬浮窗权限")
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isHttpServiceRunning by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }

    // 定期检查服务状态
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = BridgeAccessibilityService.instance != null
            isHttpServiceRunning = BridgeHttpService.instance != null
            hasOverlayPermission = Settings.canDrawOverlays(context)
            ipAddress = getDeviceIpAddress(context)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 副标题
        Text(
            text = "HTTP获取UI树 + ADB执行操作",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 状态卡片
        StatusCard(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isHttpServiceRunning = isHttpServiceRunning,
            hasOverlayPermission = hasOverlayPermission,
            ipAddress = ipAddress,
            port = 8080
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 开启悬浮窗权限按钮
        if (!hasOverlayPermission) {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开启悬浮窗权限（后台获取UI需要）")
            }
        }

        // 开启无障碍权限按钮
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isAccessibilityEnabled) "无障碍权限已开启" else "开启无障碍权限"
            )
        }

        // 启动HTTP服务按钮
        Button(
            onClick = {
                if (!isHttpServiceRunning) {
                    val intent = Intent(context, BridgeHttpService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isHttpServiceRunning
        ) {
            Text(
                if (isHttpServiceRunning) "HTTP服务运行中" else "启动HTTP服务"
            )
        }
    }
}

@Composable
fun StatusCard(
    isAccessibilityEnabled: Boolean,
    isHttpServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    ipAddress: String,
    port: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "服务状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            StatusItem(
                label = "无障碍服务",
                isEnabled = isAccessibilityEnabled
            )

            StatusItem(
                label = "悬浮窗权限",
                isEnabled = hasOverlayPermission
            )

            StatusItem(
                label = "HTTP服务",
                isEnabled = isHttpServiceRunning
            )

            if (isHttpServiceRunning && ipAddress.isNotEmpty()) {
                Text(
                    text = "访问地址: http://$ipAddress:$port",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StatusItem(
    label: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isEnabled) "✅ 已开启" else "❌ 未开启",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getDeviceIpAddress(context: android.content.Context): String {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Suppress("DEPRECATION")
    return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AlexUIBridgeTheme {
        Scaffold { innerPadding ->
            MainScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
