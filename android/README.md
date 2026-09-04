# Personal Workstation Android

原生 Kotlin + Jetpack Compose 客户端骨架，最低支持 Android 8.0（API 26）。

当前包含：

- 5 个底部导航页面入口
- 看板真实数据加载、创建任务、删除任务、移动任务和刷新
- 仪表盘 API 调用和统计卡片
- 服务器 IP/端口设置界面
- Ktor HTTP 客户端基础封装

在 Android Studio 中打开 `android/`，同步 Gradle 后运行 `app`。默认服务地址为 `192.168.1.100:8080`，可在设置页修改。

## 真机测试

1. 电脑和手机连接同一个 Wi-Fi。
2. 启动 Windows 服务端，记下启动窗口打印的局域网 IP，例如 `192.168.1.34`。
3. Windows 防火墙允许 TCP `8080` 入站访问。
4. Android Studio 连接开启 USB 调试的手机，运行 `app`。
5. 在“连接设置”中填入电脑局域网 IP 和 `8080`，点击“测试连接”。

模拟器访问宿主机时使用 `10.0.2.2`，不要填写 `127.0.0.1`；真机填写电脑的局域网 IP。
