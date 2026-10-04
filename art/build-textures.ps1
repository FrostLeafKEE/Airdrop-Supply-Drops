$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source/supply-atlas.png'))
$tiles = @('block/crate_wood','block/crate_lid','block/crate_metal','block/crate_mineral',
    'block/crate_food','entity/aircraft_body','entity/aircraft_frame','entity/aircraft_glass',
    'entity/canopy_ivory','entity/canopy_red','entity/aircraft_rubber','block/crate_latch',
    $null,$null,$null,'particle/red_smoke')
try {
    for ($index = 0; $index -lt 16; $index++) {
        if ($null -eq $tiles[$index]) { continue }
        $x = [int][Math]::Round(($index % 4) * $source.Width / 4)
        $y = [int][Math]::Round([Math]::Floor($index / 4) * $source.Height / 4)
        $right = [int][Math]::Round((($index % 4) + 1) * $source.Width / 4)
        $bottom = [int][Math]::Round(([Math]::Floor($index / 4) + 1) * $source.Height / 4)
        # Trim cell boundary contamination in the transparent sprite row.
        $inset = if ($index -ge 12) { 10 } else { 2 }
        $rect = [System.Drawing.Rectangle]::new($x+$inset, $y+$inset, $right-$x-2*$inset, $bottom-$y-2*$inset)
        $size = if ($index -ge 12) { 32 } else { 64 }
        # Refined aircraft materials have independent sources; do not overwrite them with atlas cells.
        $override = switch ($tiles[$index]) {
            'entity/aircraft_body' { 'source/aircraft-body-v2.png' }
            'entity/aircraft_glass' { 'source/aircraft-glass-v2.png' }
            default { $null }
        }
        $aircraftSource = $null
        if ($null -ne $override) {
            $aircraftSource = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot $override))
            $rect = [System.Drawing.Rectangle]::new(0,0,$aircraftSource.Width,$aircraftSource.Height)
            $size = 128
        }
        $output = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($output)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $inputImage = if ($null -ne $aircraftSource) { $aircraftSource } else { $source }
            $graphics.DrawImage($inputImage, [System.Drawing.Rectangle]::new(0,0,$size,$size), $rect, [System.Drawing.GraphicsUnit]::Pixel)
            $destination = Join-Path $projectRoot ('src/main/resources/assets/airdrop_supply_drops/textures/' + $tiles[$index] + '.png')
            [System.IO.Directory]::CreateDirectory((Split-Path $destination -Parent)) | Out-Null
            $output.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose(); $output.Dispose()
            if ($null -ne $aircraftSource) { $aircraftSource.Dispose() }
        }
    }
} finally { $source.Dispose() }
