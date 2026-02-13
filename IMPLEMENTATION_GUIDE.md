# Alex UI Bridge - 技术实现文档

> 目标：让本地编程Agent（Android Studio）实现一个极简APK
> 
> 核心功能：暴露HTTP接口返回UI树，操作还是走ADB

---

## 一、项目创建（Android Studio）

### 1.1 新建项目
- **Template**: Empty Views Activity (Java)
- **Name**: AlexUIBridge
- **Package**: com.alex.uibridge
- **Language**: Java
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 33 (Android 13)

### 1.2 依赖添加

**build.gradle (Module: app)**
```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.alex.uibridge'
    compileSdk 33

    defaultConfig {
        applicationId "com.alex.uibridge"
        minSdk 24
        targetSdk 33
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    // 只用这一个依赖
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
}
```

**gradle.properties** (确保有)
```
android.useAndroidX=true
```

---

## 二、核心功能设计

### 2.1 整体架构

```
┌─────────────────────────────────┐
│  MainActivity (UI控制)          │
├─────────────────────────────────┤
│  UIBridgeService (前台服务)     │
├─────────────────────────────────┤
│  NanoHTTPD (HTTP服务器)         │
│  ├── GET /ping                  │
│  └── GET /dump                  │
├─────────────────────────────────┤
│  AccessibilityService (无障碍)  │
│  └── getRootInActiveWindow()    │
└─────────────────────────────────┘
```

### 2.2 关键逻辑

**HTTP请求处理逻辑：**
```
收到 GET /dump
  ↓
获取 AccessibilityService 实例
  ↓
getRootInActiveWindow() 获取根节点
  ↓
递归遍历所有子节点
  ↓
提取：text, desc, id, class, package, clickable, bounds
  ↓
生成 JSON 数组
  ↓
返回 Response
```

**节点遍历递归逻辑：**
```java
void traverseNode(AccessibilityNodeInfo node, List<Map> result, int depth) {
    if (node == null || depth > 50) return;
    
    // 提取信息
    Map<String, Object> element = new HashMap<>();
    element.put("text", node.getText() != null ? node.getText().toString() : "");
    element.put("desc", node.getContentDescription() != null ? ... : "");
    element.put("clickable", node.isClickable());
    
    // 获取边界
    Rect bounds = new Rect();
    node.getBoundsInScreen(bounds);
    element.put("x1", bounds.left);
    element.put("y1", bounds.top);
    element.put("x2", bounds.right);
    element.put("y2", bounds.bottom);
    element.put("cx", (bounds.left + bounds.right) / 2);
    element.put("cy", (bounds.top + bounds.bottom) / 2);
    
    result.add(element);
    
    // 递归子节点
    for (int i = 0; i < node.getChildCount(); i++) {
        traverseNode(node.getChild(i), result, depth + 1);
    }
}
```

---

## 三、文件清单与实现细节

### 3.1 AndroidManifest.xml

位置: `app/src/main/AndroidManifest.xml`

必须包含:
- Internet权限
- 前台服务权限
- AccessibilityService声明
- 2个Service声明

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.alex.uibridge">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Alex UI Bridge"
        android:theme="@style/Theme.AppCompat">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 无障碍服务 -->
        <service
            android:name=".BridgeAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- HTTP服务 -->
        <service
            android:name=".BridgeHttpService"
            android:enabled="true"
            android:exported="false" />

    </application>
</manifest>
```

### 3.2 accessibility_service_config.xml

位置: `app/src/main/res/xml/accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_desc"
    android:packageNames=""
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true" />
```

### 3.3 strings.xml

位置: `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Alex UI Bridge</string>
    <string name="accessibility_desc">提供HTTP接口获取UI树</string>
</resources>
```

### 3.4 activity_main.xml

位置: `app/src/main/res/layout/activity_main.xml`

简单UI: 2个按钮 + 1个状态TextView

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Alex UI Bridge"
        android:textSize="28sp"
        android:textStyle="bold"
        android:layout_marginBottom="8dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="HTTP获取UI树 + ADB执行操作"
        android:textSize="14sp"
        android:layout_marginBottom="24dp" />

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="等待启动..."
        android:padding="16dp"
        android:background="#EEEEEE"
        android:layout_marginBottom="24dp" />

    <Button
        android:id="@+id/btnEnableAccessibility"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="1. 开启无障碍权限"
        android:layout_marginBottom="8dp" />

    <Button
        android:id="@+id/btnStartService"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="2. 启动HTTP服务" />

</LinearLayout>
```

---

## 四、Java代码实现

### 4.1 BridgeAccessibilityService.java

位置: `app/src/main/java/com/alex/uibridge/BridgeAccessibilityService.java`

**核心要求:**
- 继承 `AccessibilityService`
- 提供单例实例访问
- 提供 `getUiTree()` 方法返回 `List<Map<String, Object>>`
- 递归遍历节点，提取必要信息

**伪代码:**
```java
public class BridgeAccessibilityService extends AccessibilityService {
    private static BridgeAccessibilityService instance;
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }
    
    public static BridgeAccessibilityService getInstance() {
        return instance;
    }
    
    public List<Map<String, Object>> getUiTree() {
        List<Map<String, Object>> elements = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            traverseNode(root, elements, 0);
        }
        return elements;
    }
    
    private void traverseNode(AccessibilityNodeInfo node, List<Map<String, Object>> elements, int depth) {
        // 实现细节见上文
    }
}
```

### 4.2 BridgeHttpService.java

位置: `app/src/main/java/com/alex/uibridge/BridgeHttpService.java`

**核心要求:**
- 继承 `Service`
- 内部类继承 `NanoHTTPD`
- 端口: 8080
- 只处理2个端点: `/ping` 和 `/dump`
- 启动为前台服务（保活）

**伪代码:**
```java
public class BridgeHttpService extends Service {
    private HttpServer server;
    
    @Override
    public void onCreate() {
        super.onCreate();
        server = new HttpServer(8080);
        server.start();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 启动前台服务
        Notification notification = createNotification();
        startForeground(1, notification);
        return START_STICKY;
    }
    
    private class HttpServer extends NanoHTTPD {
        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/ping".equals(uri)) {
                return newFixedLengthResponse("{\"status\":\"ok\"}");
            } else if ("/dump".equals(uri)) {
                List<Map> elements = BridgeAccessibilityService.getInstance().getUiTree();
                String json = toJson(elements);
                return newFixedLengthResponse(json);
            }
            return newFixedLengthResponse(NOT_FOUND, "text/plain", "Not Found");
        }
    }
}
```

### 4.3 MainActivity.java

位置: `app/src/main/java/com/alex/uibridge/MainActivity.java`

**核心要求:**
- 2个按钮：开启无障碍权限、启动HTTP服务
- 检查无障碍服务是否已连接
- 跳转到系统设置开启无障碍

**伪代码:**
```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Button btnEnable = findViewById(R.id.btnEnableAccessibility);
        btnEnable.setOnClickListener(v -> {
            // 跳转到系统设置
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        
        Button btnStart = findViewById(R.id.btnStartService);
        btnStart.setOnClickListener(v -> {
            // 启动BridgeHttpService
            Intent intent = new Intent(this, BridgeHttpService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });
    }
}
```

---

## 五、测试验证

### 5.1 APK安装
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 5.2 启动服务
1. 打开应用
2. 点击"开启无障碍权限" → 在系统设置中找到"Alex UI Bridge"开启
3. 返回应用，点击"启动HTTP服务"

### 5.3 测试HTTP接口
```bash
# 测试ping
curl http://localhost:8080/ping
# 期望: {"status":"ok"}

# 测试dump
curl http://localhost:8080/dump
# 期望: {"count":123,"elements":[...]}
```

---

## 六、交付物

Agent实现完成后，应该产出:

1. **app-debug.apk** - 可安装的APK
2. **项目源码** - 完整的Android Studio项目
3. **测试通过** - `/ping` 和 `/dump` 都能正常返回

---

## 七、Agent实现建议

1. **先跑通NanoHTTPD** - 先实现一个简单的HTTP服务能返回"hello"，确保依赖正确
2. **再集成无障碍** - 确保能获取到UI树
3. **最后加前台服务** - 确保后台保活
4. **遇到问题** - 先看logcat日志，定位是HTTP问题还是无障碍问题

---

**文档完成。让Agent按这个实现，有问题随时问我。**
