<#
.SYNOPSIS
  Đóng gói EMS Desktop thành ứng dụng Windows bằng jpackage (kèm sẵn Java runtime, máy cài không cần Java).

.EXAMPLE
  .\build-installer.ps1                 # thư mục chạy được: target\installer\EMS\EMS.exe (không cần WiX)
  .\build-installer.ps1 -Type msi       # bộ cài .msi  (cần WiX Toolset 3.x trong PATH)
  .\build-installer.ps1 -Type exe -Version 1.0.1

.NOTES
  Yêu cầu: JDK 21 (có jpackage), Maven. Với -Type exe/msi cần WiX Toolset 3.14 (candle.exe, light.exe trong PATH):
  https://github.com/wixtoolset/wix3/releases  (JDK 21 chưa hỗ trợ WiX 4/5).
#>
param(
    [ValidateSet('app-image', 'exe', 'msi')]
    [string]$Type = 'app-image',
    [string]$Version = '1.0.0',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Find-Tool($name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\$name.exe")) { return "$env:JAVA_HOME\bin\$name.exe" }
    return $null
}

$jpackage = Find-Tool 'jpackage'
if (-not $jpackage) { throw 'Không tìm thấy jpackage. Cài JDK 21 và đặt JAVA_HOME hoặc thêm %JAVA_HOME%\bin vào PATH.' }
if ($Type -ne 'app-image' -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "Tạo .$Type cần WiX Toolset 3.x (candle.exe/light.exe) trong PATH. Hoặc chạy với -Type app-image."
}

# 1. Build jar + target\lib (thư viện, gồm JavaFX bản Windows)
if (-not $SkipBuild) {
    mvn -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'mvn package thất bại' }
}

# 2. Gom đầu vào cho jpackage: jar chính + toàn bộ thư viện cùng một thư mục
$inDir = Join-Path $PSScriptRoot 'target\jpackage-input'
$dest = Join-Path $PSScriptRoot 'target\installer'
Remove-Item $inDir, $dest -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $inDir | Out-Null
Copy-Item 'target\ems-desktop.jar' $inDir
Copy-Item 'target\lib\*.jar' $inDir

# 3. jpackage – runtime rút gọn (~95 MB) chỉ gồm module cần thiết
$modules = 'java.base,java.desktop,java.logging,java.net.http,java.sql,java.xml,java.naming,jdk.unsupported,jdk.crypto.ec,jdk.jsobject,jdk.xml.dom,jdk.localedata'
$jpArgs = @(
    '--type', $Type,
    '--name', 'EMS',
    '--app-version', $Version,
    '--vendor', 'NPCore',
    '--description', 'Phần mềm quản lý chuyên gia',
    '--input', $inDir,
    '--main-jar', 'ems-desktop.jar',
    '--main-class', 'com.npcore.ems.desktop.Launcher',
    '--add-modules', $modules,
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages --include-locales=en,vi',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--icon', (Join-Path $PSScriptRoot 'packaging\ems.ico'),
    '--dest', $dest
)
if ($Type -ne 'app-image') {
    $jpArgs += @(
        '--win-menu', '--win-menu-group', 'NPCore',
        '--win-shortcut', '--win-dir-chooser', '--win-per-user-install',
        # Giữ cố định để bản cài mới tự nâng cấp bản cũ
        '--win-upgrade-uuid', '6c1d7f0e-3b7a-4d43-9a57-2f1f6e0b8e51'
    )
}
& $jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage thất bại' }

Write-Host ''
if ($Type -eq 'app-image') {
    Write-Host "Xong: $dest\EMS\EMS.exe  (nén cả thư mục EMS để gửi cho máy khác)" -ForegroundColor Green
} else {
    Write-Host "Xong: $(Get-ChildItem $dest -Filter "*.$Type" | Select-Object -First 1 -ExpandProperty FullName)" -ForegroundColor Green
}
Write-Host 'Máy chủ mặc định: http://localhost:8080 – đổi trong màn hình đăng nhập (mục "Máy chủ") hoặc biến môi trường EMS_SERVER_URL.'
