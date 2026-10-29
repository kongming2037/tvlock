# 屏幕计时锁（TVLock）

给电视用的倒计时锁屏应用：设定每日可用时长，屏幕右上角实时显示倒计时，
时间用完自动全屏锁定，需管理员密码解锁。适合家长控制孩子看电视的时间。

包名：`com.yongge.tvlock` ｜ minSdk 26 ｜ targetSdk 28

## 功能

- **倒计时悬浮窗**：前台服务＋ `TYPE_APPLICATION_OVERLAY`，右上角实时显示"剩余 H:MM:SS"
- **到时锁定**：计时归零后全屏锁屏；其它应用切到前台会被拉回锁屏（UsageStats 监视）
- **管理员密码**：进入设置、解锁均需密码（默认 `112233`，SHA-256 存储，可修改）
- **每日重置**：每天 0 点自动重置为当日时长；密码解锁后当天免打扰
- **加时 / 重置**：设置里可"今日加时 30 分钟"或"重置今日计时"
- **开机自启**：重启后服务自动恢复
- **权限设置向导**：App 内置向导，逐项检查悬浮窗 / 使用情况访问 / 电池优化 / 服务状态，一键跳转系统设置
- **防杀机制**：计时按截止时间（deadline）计算，进程被杀重启后时间不丢失；
  60 秒闹钟自救（`KeepAliveReceiver`）＋外部看门狗定时拉起

## 构建

不依赖 Gradle，手工构建（需 Android SDK build-tools ＋ JDK）：

```bash
./build.sh
```

产物为 `tvlock-signed.apk`。签名 keystore 请自行生成并替换 `build.sh` 中的配置
（仓库内不包含 keystore，升级必须用同一 keystore 签名）。

## 安装与授权（ADB）

```bash
adb install -r tvlock-signed.apk
adb shell appops set com.yongge.tvlock SYSTEM_ALERT_WINDOW allow
adb shell appops set com.yongge.tvlock android:get_usage_stats allow
adb shell am start-foreground-service -n com.yongge.tvlock/.TimerService
```

也可用 App 内"权限设置向导"逐项跳转系统设置开启。

## 已知限制（小米电视实测）

- 小米电视固件不支持设备管理员（`dpm set-active-admin` 假成功），
  也设不上 device owner，因此**无法实现"卸载需密码"的系统级拦截**。
  替代方案：外部定时任务检测到被卸载后自动重装。
- MIUI 会定期强杀带悬浮窗的前台服务。本应用用 deadline 计时＋闹钟自救＋
  外部看门狗多层兜底，计时和锁定不受影响，但悬浮窗可能短暂消失后恢复。
- 电视一般无电池，"忽略电池优化"设置页可能不存在，向导会自动隐藏该项。

## 许可证

MIT
