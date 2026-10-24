$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$project=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$dest=Join-Path $project 'src\main\resources\assets\white_lily\textures\item'
$previews=Join-Path $project 'docs\previews\state-test-items-20261024'
New-Item -ItemType Directory -Force -Path $previews | Out-Null
function C([string]$v){[System.Drawing.ColorTranslator]::FromHtml($v)}
$palette=@{'O'='#252C35';'S'='#485563';'M'='#84969B';'H'='#D4E0D0';'R'='#8B3040';'V'='#D34A51';'P'='#F38A79';'G'='#49785A';'L'='#9BDC88';'B'='#744725';'T'='#BB7C38';'Y'='#E9BF69';'W'='#EEE0B4'}
$icons=@(
@{Id='health_restore_test';Name='生命恢复器';Rows=@'
................
...OOOOOOOOOO...
..OHMMMMMMMMSO..
.OHSSSSSSSSSSSO.
.OMSOVVOVVOSSSO.
.OMSOVPPVVOSSSO.
.OMSRVVVVVOSSSO.
.OMSSRVVVROSSSO.
.OMSSSRVROSSSSO.
.OMSSSSRSSGLSSO.
.OMSSSSSSGLLLGO.
.OMSSSSSSSGLSSO.
.OSSSSSSSSSSSSO.
..OSMMMMMMMMSO..
...OOOOOOOOOO...
................
'@},
@{Id='food_restore_test';Name='饥饿与饱和恢复器';Rows=@'
................
...OOOOOOOOOO...
..OHMMMMMMMMSO..
.OHSSSSSSSSSSSO.
.OMSSSSOBBOSSSO.
.OMSSSOBYYBSSSO.
.OMSSOBYTTBSSSO.
.OMSSOBTTTBSSSO.
.OMSSSOBBBOSSSO.
.OMSSOWWOSSSSSO.
.OMSOWWSSSSSSSO.
.OMSSSSYYYYYSSO.
.OSSSSSBBBBBSSO.
..OSMMMMMMMMSO..
...OOOOOOOOOO...
................
'@},
@{Id='instant_damage_test';Name='瞬间扣血器';Rows=@'
................
...OOOOOOOOOO...
..OHMMMMMMMMSO..
.OHSSSSSSSSSSSO.
.OMSSSSSSRPSSSO.
.OMSSSSSRPVSSSO.
.OMSSSSRPVSSSSO.
.OMSSSRPVSSSSSO.
.OMSSRPVVVPSSSO.
.OMSSSSSRPVSSSO.
.OMSSSSRPVSSSSO.
.OMSSSRPVSSSSSO.
.OSSSSRVSSSSSSO.
..OSMMMMMMMMSO..
...OOOOOOOOOO...
................
'@})
$sheet=[System.Drawing.Bitmap]::new(576,224)
$g=[System.Drawing.Graphics]::FromImage($sheet)
$g.Clear((C '#252A2D'))
$g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font=[System.Drawing.Font]::new('Microsoft YaHei',10)
for($i=0;$i -lt $icons.Count;$i++){
  $icon=$icons[$i]
  $rows=$icon.Rows.Trim() -split '\r?\n'
  $b=[System.Drawing.Bitmap]::new(16,16,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  if($rows.Count -ne 16){throw 'Height must be 16'}
  for($y=0;$y -lt 16;$y++){
    $row=$rows[$y].Replace(' ','')
    if($row.Length -ne 16){throw "Invalid row $y in $($icon.Id): $row"}
    for($x=0;$x -lt 16;$x++){
      $v=$row.Substring($x,1)
      if($v -ne '.'){$b.SetPixel($x,$y,(C $palette[$v]))}
    }
  }
  $b.Save((Join-Path $dest ($icon.Id+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
  for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
    $hex=if(($x+$y)%2 -eq 0){'#363D41'}else{'#444B50'}
    $brush=[System.Drawing.SolidBrush]::new((C $hex))
    $g.FillRectangle($brush,($i*192+16+$x*10),(16+$y*10),10,10);$brush.Dispose()
  }}
  $g.DrawImage($b,($i*192+16),16,160,160)
  $g.DrawString($icon.Name,$font,[System.Drawing.Brushes]::White,($i*192+20),185)
  $large=[System.Drawing.Bitmap]::new(256,256)
  $lg=[System.Drawing.Graphics]::FromImage($large)
  $lg.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $lg.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
  $lg.DrawImage($b,0,0,256,256)
  $large.Save((Join-Path $previews ($icon.Id+'_preview_do_not_import.png')),[System.Drawing.Imaging.ImageFormat]::Png)
  $lg.Dispose();$large.Dispose();$b.Dispose()
}
$sheet.Save((Join-Path $previews 'state_test_items_preview_do_not_import.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$font.Dispose();$g.Dispose();$sheet.Dispose()
Write-Output 'Created three native 16x16 textures and separate nearest-neighbor previews.'
