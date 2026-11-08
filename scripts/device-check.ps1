# 真机验证辅助：检查 adb 与已连接设备，并打印 README 中的验证步骤。
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "未找到 adb。安装 Android Studio 并将 platform-tools 加入 PATH 后重试。"
    exit 1
}
Write-Host "=== adb devices ==="
& adb devices
Write-Host ""
Write-Host "请在手机上安装 debug APK 后按 README「真机验证清单」逐项测试（单音 / 和弦 / 力度 / 停手）。"
