param([string]$ImagePath='')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$assets=Join-Path $root 'src/windows/Assets'
New-Item -ItemType Directory -Force $assets | Out-Null
if($ImagePath){Copy-Item -LiteralPath $ImagePath -Destination "$assets/NyxLink.png" -Force}
Add-Type -AssemblyName System.Drawing
$original=[Drawing.Image]::FromFile("$assets/NyxLink.png")
function Render-Png([int]$Size,[double]$Scale=1){
    $bitmap=[Drawing.Bitmap]::new($Size,$Size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics=[Drawing.Graphics]::FromImage($bitmap)
    try{$graphics.Clear([Drawing.Color]::Transparent);$graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic;$graphics.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::HighQuality;$side=[int]($Size*$Scale);$offset=[int](($Size-$side)/2);$graphics.DrawImage($original,$offset,$offset,$side,$side)}finally{$graphics.Dispose()}
    return ,$bitmap
}
try{
    $frames=@();foreach($size in @(16,24,32,48,64,128,256)){$bitmap=Render-Png $size;$memory=[IO.MemoryStream]::new();try{$bitmap.Save($memory,[Drawing.Imaging.ImageFormat]::Png);$frames+=@{size=$size;bytes=$memory.ToArray()}}finally{$bitmap.Dispose();$memory.Dispose()}}
    $file=[IO.File]::Create("$assets/NyxLink.ico");$writer=[IO.BinaryWriter]::new($file)
    try{$writer.Write([uint16]0);$writer.Write([uint16]1);$writer.Write([uint16]$frames.Count);$offset=6+16*$frames.Count;foreach($frame in $frames){$dimension=if($frame.size -eq 256){0}else{$frame.size};$writer.Write([byte]$dimension);$writer.Write([byte]$dimension);$writer.Write([byte]0);$writer.Write([byte]0);$writer.Write([uint16]1);$writer.Write([uint16]32);$writer.Write([uint32]$frame.bytes.Length);$writer.Write([uint32]$offset);$offset+=$frame.bytes.Length};foreach($frame in $frames){$writer.Write([byte[]]$frame.bytes)}}finally{$writer.Dispose();$file.Dispose()}
    foreach($density in @(@('mdpi',48,108),@('hdpi',72,162),@('xhdpi',96,216),@('xxhdpi',144,324),@('xxxhdpi',192,432))){$folder=Join-Path $root ('src/android/app/src/main/res/mipmap-'+$density[0]);$legacy=Render-Png $density[1];try{$legacy.Save("$folder/ic_launcher.png",[Drawing.Imaging.ImageFormat]::Png)}finally{$legacy.Dispose()};$adaptive=Render-Png $density[2] (66.0/108);try{$adaptive.Save("$folder/ic_launcher_foreground.png",[Drawing.Imaging.ImageFormat]::Png)}finally{$adaptive.Dispose()}}
}finally{$original.Dispose()}
