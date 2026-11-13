$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = 'Q:\MC BDS APP\.tooling\android-sdk'
$bt = Join-Path $sdk 'build-tools\35.0.0'
$platform = Join-Path $sdk 'platforms\android-35\android.jar'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'

if (!(Test-Path $platform)) { throw "android.jar not found: $platform" }
if (!(Test-Path (Join-Path $jdk 'bin\javac.exe'))) { throw "JDK not found: $jdk" }

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $bt + ';' + $env:Path

$build = Join-Path $root 'build'
$gen = Join-Path $root 'gen'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'

if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build, $gen, $classes, $dex | Out-Null

$resZip = Join-Path $build 'res.zip'
$baseApk = Join-Path $build 'base.apk'
$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'
$outApk = Join-Path $build 'app-debug.apk'

Write-Host '== aapt2 compile =='
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $root 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '== aapt2 link =='
& (Join-Path $bt 'aapt2.exe') link -o $baseApk -I $platform --manifest (Join-Path $root 'AndroidManifest.xml') -R $resZip --java $gen --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '== javac =='
$libJars = @(Get-ChildItem (Join-Path $root 'libs') -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
$compileCp = $platform
if ($libJars.Count -gt 0) { $compileCp = $compileCp + ';' + ($libJars -join ';') }
$srcs = @()
$srcs += (Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$srcs += (Get-ChildItem $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$genAidl = Join-Path $root 'gen-aidl'
if (Test-Path $genAidl) {
    $srcs += (Get-ChildItem $genAidl -Recurse -Filter *.java | ForEach-Object { $_.FullName })
}
& (Join-Path $jdk 'bin\javac.exe') -source 8 -target 8 -encoding UTF-8 -Xlint:-options -classpath $compileCp -d $classes $srcs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '== d8 =='
Add-Type -AssemblyName System.IO.Compression.FileSystem
$libClasses = Join-Path $build 'libclasses'
New-Item -ItemType Directory -Force -Path $libClasses | Out-Null
$li = 0
foreach ($jar in $libJars) {
    $d = Join-Path $libClasses ("j" + $li)
    New-Item -ItemType Directory -Force -Path $d | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jar, $d)
    $li++
}
$classFiles = @()
$classFiles += (Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
$classFiles += (Get-ChildItem $libClasses -Recurse -Filter *.class | ForEach-Object { $_.FullName })
$allJar = Join-Path $build 'allclasses.jar'
& (Join-Path $jdk 'bin\jar.exe') cf $allJar -C $classes . -C $libClasses .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& (Join-Path $bt 'd8.bat') --lib $platform --min-api 26 --output $dex $allJar
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== package =='
Copy-Item $baseApk $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
try {
    $dexEntry = $zip.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $dexStream = $dexEntry.Open()
    $dexBytes = [System.IO.File]::ReadAllBytes((Join-Path $dex 'classes.dex'))
    $dexStream.Write($dexBytes, 0, $dexBytes.Length)
    $dexStream.Close()

    $soPath = Join-Path $root 'jniLibs\arm64-v8a\libqemu.so'
    if (!(Test-Path $soPath)) { throw "missing $soPath" }
    $soEntry = $zip.CreateEntry('lib/arm64-v8a/libqemu.so', [System.IO.Compression.CompressionLevel]::NoCompression)
    $soStream = $soEntry.Open()
    $soBytes = [System.IO.File]::ReadAllBytes($soPath)
    $soStream.Write($soBytes, 0, $soBytes.Length)
    $soStream.Close()

    $assetsDir = Join-Path $root 'assets'
    if (Test-Path $assetsDir) {
        $assetsRoot = (Get-Item $assetsDir).FullName
        Get-ChildItem $assetsDir -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($assetsRoot.Length + 1).Replace('\', '/')
            $entryName = 'assets/' + $rel
            $e = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
            $s = $e.Open()
            $b = [System.IO.File]::ReadAllBytes($_.FullName)
            $s.Write($b, 0, $b.Length)
            $s.Close()
        }
    }
} finally {
    $zip.Dispose()
}

Write-Host '== zipalign =='
& (Join-Path $bt 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '== sign =='
$ks = Join-Path $root 'debug.keystore'
if (!(Test-Path $ks)) {
    & (Join-Path $jdk 'bin\keytool.exe') -genkeypair -keystore $ks -alias androiddebugkey -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass pass:android --key-pass pass:android --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $outApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '== verify =='
& (Join-Path $bt 'apksigner.bat') verify -v $outApk
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

Write-Host ''
Write-Host "APK: $outApk"
Get-Item $outApk | Select-Object FullName, Length, LastWriteTime
