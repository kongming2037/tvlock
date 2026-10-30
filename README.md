# 屏幕计时锁 (tvlock)

小米电视用的屏幕计时锁，Kotlin？不，Java 写的。

## 功能

- 左上角常驻倒计时悬浮窗（默认 1 分钟，可改）
- 倒计时结束进入全屏锁屏，输管理员密码解锁后开始新周期
- 锁屏屏蔽返回/菜单键，桌面键 1 秒拉回
- 设置界面：改倒计时周期、改管理员密码、重置倒计时、立即锁屏
- 双 App 互保活（见下）

## 保活架构

- 主 App `com.yongge.tvlock`：TimerService（倒计时+锁屏）
- 看门狗 App `com.yongge.tvlock.watchdog`：独立应用，每 2 秒检查主服务
- 主服务也会拉起看门狗，双向保活
- 看门狗带管理界面（密码验证后可停用），可隐藏/显示主程序图标

## 构建

需要 Android SDK（build-tools 34.0.0，platform android-34，JDK 17）：

```bash
./build.sh
```

签名用 `tvlock2.keystore`（不进仓库，自己生成）。

## 部署

```bash
adb install tvlock-signed.apk
adb shell appops set com.yongge.tvlock SYSTEM_ALERT_WINDOW allow
adb shell appops set com.yongge.tvlock GET_USAGE_STATS allow
```

## 默认密码

`112233`
