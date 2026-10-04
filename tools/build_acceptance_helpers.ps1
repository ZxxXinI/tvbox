param(
    [string]$SdkPath = 'E:\Soft\Tools\AndroidSDK',
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$output = Join-Path $workspace 'build\acceptance\helpers'
$toolsPath = Join-Path $SdkPath 'build-tools\36.1.0'
$androidJar = Join-Path $SdkPath 'platforms\android-28\android.jar'
$sourceDir = Join-Path $PSScriptRoot 'acceptance'
New-Item -ItemType Directory -Force -Path "$output\focus-classes","$output\focus-dex","$output\key-classes","$output\key-dex" | Out-Null
javac -source 8 -target 8 -cp $androidJar -d "$output\focus-classes" "$sourceDir\FocusService.java"
if ($LASTEXITCODE -ne 0) { throw 'Focus probe compilation failed' }
& "$toolsPath\d8.bat" --lib $androidJar --output "$output\focus-dex" "$output\focus-classes\com\tvbox\acceptance\probe\FocusService.class"
& "$toolsPath\aapt.exe" package -f -M "$sourceDir\AndroidManifest.xml" -I $androidJar -F "$output\focus-unsigned.apk"
Push-Location "$output\focus-dex"
try { & "$toolsPath\aapt.exe" add "$output\focus-unsigned.apk" classes.dex } finally { Pop-Location }
& "$toolsPath\apksigner.bat" sign --ks (Join-Path $env:USERPROFILE '.android\debug.keystore') --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$output\focus-probe.apk" "$output\focus-unsigned.apk"
javac -source 8 -target 8 -cp $androidJar -d "$output\key-classes" "$sourceDir\KeyProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'Key probe compilation failed' }
& "$toolsPath\d8.bat" --lib $androidJar --output "$output\key-dex" "$output\key-classes\com\tvbox\acceptance\KeyProbe.class"
jar cf "$output\keyprobe.jar" -C "$output\key-dex" classes.dex
adb -s $Serial install -r "$output\focus-probe.apk"
adb -s $Serial push "$output\keyprobe.jar" /data/local/tmp/tvbox-keyprobe.jar
