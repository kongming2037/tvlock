# 看门狗 (tvlock-watchdog)

屏幕计时锁的独立看门狗 App，包名 `com.yongge.tvlock.watchdog`。

## 功能

- 每 2 秒检查主 App（`com.yongge.tvlock`）的 TimerService，死了就拉起
- 主 App 也会反向拉起看门狗，双向保活
- 带桌面图标（小米风格小狗），进管理界面需验证管理员密码（默认 `112233`）
- 管理界面：停用/启用看门功能、修改密码、隐藏/显示主程序图标
- 主 App 被卸载时，尝试用内置 APK 触发重装（需"安装未知应用"权限，会弹用户确认）

## 构建

```bash
./build.sh
```

需要把主程序的 APK 放到 `assets/main.apk`（构建时会自动打包进去）。

## 注意

- `assets/main.apk` 不进仓库（太大），构建前自己拷进去
- 图标 `res/drawable/ic_watchdog.png` 是生成的
