$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$srcPath = "E:\AAAtranslator\logo\logo_preview.png"
$resDir  = "E:\AAAtranslator\app\src\main\res"

$img = [System.Drawing.Image]::FromFile($srcPath)
$bmp = New-Object System.Drawing.Bitmap $img
$img.Dispose()
$W = $bmp.Width; $H = $bmp.Height
Write-Output "SOURCE ${W}x${H}"

# ---------- 1. 采样白色背景色（右侧白区平均） ----------
$rSum=0.0;$gSum=0.0;$bSum=0.0;$n=0
for ($y = [int]($H*0.05); $y -lt [int]($H*0.95); $y += 3) {
  for ($x = [int]($W*0.66); $x -lt [int]($W*0.97); $x += 3) {
    $p = $bmp.GetPixel($x, $y)
    $mx = [Math]::Max($p.R,[Math]::Max($p.G,$p.B)); $mn = [Math]::Min($p.R,[Math]::Min($p.G,$p.B))
    if ($mx -gt 200 -and ($mx-$mn) -lt 18) { $rSum+=$p.R; $gSum+=$p.G; $bSum+=$p.B; $n++ }
  }
}
if ($n -eq 0) { $bgR=255; $bgG=255; $bgB=255 } else { $bgR=[int]($rSum/$n); $bgG=[int]($gSum/$n); $bgB=[int]($bSum/$n) }
$bgHex = ("#{0:X2}{1:X2}{2:X2}" -f $bgR,$bgG,$bgB)
Write-Output "BG $bgHex (samples=$n)"

# ---------- 2. 蓝色块包围盒（用于通知小图标字形） ----------
$minX=$W; $minY=$H; $maxX=-1; $maxY=-1
for ($y = 0; $y -lt $H; $y += 2) {
  for ($x = 0; $x -lt $W; $x += 2) {
    $p = $bmp.GetPixel($x, $y)
    $mx = [Math]::Max($p.R,[Math]::Max($p.G,$p.B)); $mn = [Math]::Min($p.R,[Math]::Min($p.G,$p.B))
    $sat = 0.0; if ($mx -gt 0) { $sat = ($mx-$mn)/$mx }
    if ($sat -gt 0.4 -and $p.B -gt 150 -and $p.B -gt $p.R*1.4) {
      if ($x -lt $minX) { $minX=$x }; if ($x -gt $maxX) { $maxX=$x }
      if ($y -lt $minY) { $minY=$y }; if ($y -gt $maxY) { $maxY=$y }
    }
  }
}
# 若检测失败，退回整图左 2/3
if ($maxX -lt 0) { $minX=0; $minY=0; $maxX=[int]($W*0.63); $maxY=$H-1 }
$bboxW = $maxX-$minX+1; $bboxH = $maxY-$minY+1
Write-Output "BLUE_BBOX x=$minX..$maxX y=$minY..$maxY (${bboxW}x${bboxH})"

$bgColor = [System.Drawing.Color]::FromArgb(255,$bgR,$bgG,$bgB)

# ---------- 通用：整图等比居中放方形画布 ----------
function New-LegacyIcon {
  param([int]$S, [string]$path)
  $canvas = New-Object System.Drawing.Bitmap $S, $S, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($canvas)
  $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear($bgColor)
  $scale = [Math]::Min([double]$S/$W, [double]$S/$H)
  $dw = [int]($W*$scale); $dh = [int]($H*$scale)
  $dx = [int](($S-$dw)/2); $dy = [int](($S-$dh)/2)
  $g.DrawImage($bmp, $dx, $dy, $dw, $dh)
  $g.Dispose()
  $canvas.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $canvas.Dispose()
}

function New-Foreground {
  param([int]$S, [double]$frac, [string]$path)
  $canvas = New-Object System.Drawing.Bitmap $S, $S, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($canvas)
  $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  $maxDim = $S * $frac
  $scale = [Math]::Min([double]$maxDim/$W, [double]$maxDim/$H)
  $dw = [int]($W*$scale); $dh = [int]($H*$scale)
  $dx = [int](($S-$dw)/2); $dy = [int](($S-$dh)/2)
  $g.DrawImage($bmp, $dx, $dy, $dw, $dh)
  $g.Dispose()
  $canvas.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $canvas.Dispose()
}

# ---------- 3. 生成资源 ----------
New-LegacyIcon 48  "$resDir\mipmap-mdpi\ic_launcher.png"
New-LegacyIcon 72  "$resDir\mipmap-hdpi\ic_launcher.png"
New-LegacyIcon 96  "$resDir\mipmap-xhdpi\ic_launcher.png"
New-LegacyIcon 144 "$resDir\mipmap-xxhdpi\ic_launcher.png"
New-LegacyIcon 192 "$resDir\mipmap-xxxhdpi\ic_launcher.png"
New-Foreground 432 0.61 "$resDir\drawable\ic_launcher_foreground_bitmap.png"
Write-Output "LEGACY + FOREGROUND done"

# ---------- 4. 通知小图标：蓝色块内白色字形 ----------
$notif = New-Object System.Drawing.Bitmap 96, 96, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 96; $y++) {
  for ($x = 0; $x -lt 96; $x++) {
    $sx = $minX + [int](($x + 0.5) / 96 * $bboxW)
    $sy = $minY + [int](($y + 0.5) / 96 * $bboxH)
    $p = $bmp.GetPixel($sx, $sy)
    $mx = [Math]::Max($p.R,[Math]::Max($p.G,$p.B)); $mn = [Math]::Min($p.R,[Math]::Min($p.G,$p.B))
    $sat = 0.0; if ($mx -gt 0) { $sat = ($mx-$mn)/$mx }
    if ($p.A -gt 40 -and $mx -gt 185 -and $sat -lt 0.30) {
      $notif.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,255,255,255))
    }
  }
}
$notif.Save("$resDir\drawable\ic_notification.png", [System.Drawing.Imaging.ImageFormat]::Png)
$notif.Dispose()
Write-Output "NOTIFICATION done"

# ---------- 5. 预览图 256 ----------
New-LegacyIcon 256 "E:\AAAtranslator\logo\icon_preview.png"
Write-Output "PREVIEW done"

$bmp.Dispose()
Write-Output "ALL DONE bg=$bgHex"
