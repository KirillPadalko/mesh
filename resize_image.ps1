
Add-Type -AssemblyName System.Drawing

$sourcePath = "C:\Users\Bob\.gemini\antigravity\brain\14ed332b-cb45-4396-996c-273714a30bcd\mesh_feature_graphic_v6_1768555610395.png"
$destPath = "d:\DEV_DATA\mesh-client\feature_graphic.png"

$image = [System.Drawing.Image]::FromFile($sourcePath)
$targetWidth = 1024
$targetHeight = 500

$bmp = New-Object System.Drawing.Bitmap $targetWidth, $targetHeight
$graph = [System.Drawing.Graphics]::FromImage($bmp)
$graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graph.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$graph.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Calculate aspect ratio to fill
$ratioX = $targetWidth / $image.Width
$ratioY = $targetHeight / $image.Height
$ratio = [Math]::Max($ratioX, $ratioY)

$newWidth = [int]($image.Width * $ratio)
$newHeight = [int]($image.Height * $ratio)

$posX = [int](($targetWidth - $newWidth) / 2)
$posY = [int](($targetHeight - $newHeight) / 2)

$graph.DrawImage($image, $posX, $posY, $newWidth, $newHeight)

$bmp.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)

$image.Dispose()
$bmp.Dispose()
$graph.Dispose()

Write-Host "Image saved to $destPath"
