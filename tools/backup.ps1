# 变更前备份 / 回滚脚本
#   用法：.\tools\backup.ps1            # 备份源码与资源
#         .\tools\backup.ps1 -Restore <zip 路径>
# 备份内容：app/src、app/assets（排除大体积 rootfs）、AndroidManifest.xml、build.ps1
# 备份位置：.tooling\backups\src-<时间戳>.zip（不入库）

param(
    [string]$Restore = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $root ".tooling\backups"

if ($Restore -ne "") {
    if (-not (Test-Path $Restore)) { throw "找不到备份文件：$Restore" }
    $tmp = Join-Path $env:TEMP ("bf_restore_" + [guid]::NewGuid().ToString("N"))
    Expand-Archive -Path $Restore -DestinationPath $tmp -Force
    Copy-Item -Recurse -Force (Join-Path $tmp "src\*") (Join-Path $root "app\src")
    Copy-Item -Recurse -Force (Join-Path $tmp "assets\*") (Join-Path $root "app\assets")
    Copy-Item -Force (Join-Path $tmp "AndroidManifest.xml") (Join-Path $root "app\AndroidManifest.xml")
    Copy-Item -Force (Join-Path $tmp "build.ps1") (Join-Path $root "app\build.ps1")
    Remove-Item -Recurse -Force $tmp
    Write-Host "已回滚到：$Restore"
    Write-Host "接下来请重新构建：.\app\build.ps1"
    exit 0
}

$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$stage = Join-Path $env:TEMP ("bf_backup_" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item -Recurse -Force (Join-Path $root "app\src") (Join-Path $stage "src")
Copy-Item -Recurse -Force (Join-Path $root "app\assets") (Join-Path $stage "assets")
Copy-Item -Force (Join-Path $root "app\AndroidManifest.xml") (Join-Path $stage "AndroidManifest.xml")
Copy-Item -Force (Join-Path $root "app\build.ps1") (Join-Path $stage "build.ps1")

# 大体积静态资源不入备份（可重新下载/已 gitignore）
Get-ChildItem -Path (Join-Path $stage "assets") -Recurse -File |
    Where-Object { $_.Length -gt 20MB } |
    ForEach-Object { Remove-Item $_.FullName -Force }

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$zip = Join-Path $backupDir ("src-" + $stamp + ".zip")
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zip -Force
Remove-Item -Recurse -Force $stage

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 2)
Write-Host "已备份：$zip（$mb MB）"
Write-Host "回滚命令：.\tools\backup.ps1 -Restore $zip && .\app\build.ps1"
