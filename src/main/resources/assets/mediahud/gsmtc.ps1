# ==============================================================
#  gsmtc.ps1 - Lee la sesión multimedia ACTIVA de Windows (GSMTC)
#  y escribe UNA línea JSON por segundo en la salida estándar.
#
#  El mod lanza este script como proceso hijo (en un hilo aparte)
#  y lee sus líneas. Así Java no necesita librerías WinRT.
#  Funciona con Windows PowerShell 5.1 (el que trae Windows).
# ==============================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

# PowerShell no sabe "esperar" operaciones asíncronas de WinRT por sí solo.
# Este truco convierte IAsyncOperation<T> en un Task de .NET y lo espera.
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
	$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, [Type]$resultType) {
	$task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

# Lee TODOS los bytes de un stream de Windows (WinRT).
# Camino 1: DataReader (el que mejor funciona desde PowerShell).
# Camino 2 (respaldo): convertir a Stream de .NET con el tipo indicado explicitamente.
#
# PowerShell recibe el stream como un "System.__ComObject" y NO deja convertirlo a IInputStream
# (ni con casts ni con New-Object). La salida es usar REFLEXION: .Invoke() entrega el objeto
# tal cual a .NET, que si conoce su tipo real.
$asStreamForReadMethod = ([System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 -and
	$_.GetParameters()[0].ParameterType.Name -eq 'IInputStream'
})[0]

function Read-StreamBytes($stream) {
	$raw = $stream.psobject.BaseObject   # el objeto COM "desnudo", sin envoltorio de PowerShell
	$firstError = ''

	# Camino 1: AsStreamForRead por reflexion -> Stream de .NET -> bytes
	try {
		$netStream = $asStreamForReadMethod.Invoke($null, [object[]]@($raw))
		$memory = New-Object System.IO.MemoryStream
		$netStream.CopyTo($memory)
		$result = $memory.ToArray()
		$memory.Dispose(); $netStream.Dispose()
		return , $result
	} catch {
		$firstError = $_.Exception.Message
		if ($_.Exception.InnerException) { $firstError += ' / ' + $_.Exception.InnerException.Message }
	}

	# Camino 2: DataReader creado por reflexion
	try {
		$reader = [Activator]::CreateInstance([Windows.Storage.Streams.DataReader], [object[]]@($raw))
		$size = [uint32]$stream.Size
		$loaded = Await ($reader.LoadAsync($size)) ([uint32])
		$buffer = New-Object byte[] $loaded
		$reader.ReadBytes($buffer)
		return , $buffer
	} catch {
		throw ("AsStreamForRead: " + $firstError + " | DataReader: " + $_.Exception.Message + " | tipo=" + $raw.GetType().FullName)
	}
}

# Descarga la portada, la recorta cuadrada, la reduce a 64x64 y la devuelve como PNG en Base64.
# Devuelve un objeto con: data (Base64 o '') y error (texto para depurar, o '').
function Get-Cover($props) {
	$result = @{ data = ''; error = '' }
	try {
		if ($null -eq $props.Thumbnail) {
			$result.error = 'la sesion no ofrece miniatura (todavia)'
			return $result
		}
		$stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
		$bytes = Read-StreamBytes $stream
		# (No llamamos $stream.Dispose(): el objeto COM de Windows no expone ese metodo en PowerShell.)

		if ($bytes.Length -eq 0) {
			$result.error = 'miniatura vacia (todavia)'
			return $result
		}

		# Convertimos a PNG 64x64 con System.Drawing (lee JPEG, PNG, BMP, GIF...).
		# Si falla, mandamos los bytes originales y Java intenta decodificarlos.
		try {
			$inStream = New-Object System.IO.MemoryStream(, $bytes)
			$img = [System.Drawing.Image]::FromStream($inStream)
			$side = [Math]::Min($img.Width, $img.Height)
			$srcX = [int](($img.Width - $side) / 2)
			$srcY = [int](($img.Height - $side) / 2)
			$bmp = New-Object System.Drawing.Bitmap 64, 64
			$gfx = [System.Drawing.Graphics]::FromImage($bmp)
			$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
			$dest = New-Object System.Drawing.Rectangle 0, 0, 64, 64
			$gfx.DrawImage($img, $dest, $srcX, $srcY, [int]$side, [int]$side, [System.Drawing.GraphicsUnit]::Pixel)
			$outStream = New-Object System.IO.MemoryStream
			$bmp.Save($outStream, [System.Drawing.Imaging.ImageFormat]::Png)
			$bytes = $outStream.ToArray()
			$gfx.Dispose(); $bmp.Dispose(); $img.Dispose(); $inStream.Dispose(); $outStream.Dispose()
		} catch {
			$result.error = 'no se pudo convertir a PNG: ' + $_.Exception.Message
		}

		$result.data = [Convert]::ToBase64String($bytes)
	} catch {
		$result.error = $_.Exception.Message
	}
	return $result
}

$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
	([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

$lastKey = ''
$coverDone = $false   # ya tenemos la portada de la canción actual
$coverTries = 0       # cuántas veces la hemos pedido
$sendClear = $false   # al cambiar de canción, primero borramos la portada vieja

while ($true) {
	try {
		$session = $manager.GetCurrentSession()

		if ($null -eq $session) {
			$lastKey = ''
			[Console]::Out.WriteLine('{"session":false}')
		} else {
			$props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			$playback = $session.GetPlaybackInfo()
			$timeline = $session.GetTimelineProperties()

			$playing = ($playback.PlaybackStatus.ToString() -eq 'Playing')

			$durationMs = [long]($timeline.EndTime.TotalMilliseconds - $timeline.StartTime.TotalMilliseconds)
			$positionMs = [long]($timeline.Position.TotalMilliseconds - $timeline.StartTime.TotalMilliseconds)

			if ($playing) {
				$elapsed = ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds
				if ($elapsed -gt 0) { $positionMs = [long]($positionMs + $elapsed) }
			}
			if ($durationMs -gt 0 -and $positionMs -gt $durationMs) { $positionMs = $durationMs }
			if ($positionMs -lt 0) { $positionMs = 0 }

			$key = "$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
			$data = [ordered]@{
				session  = $true
				title    = [string]$props.Title
				artist   = [string]$props.Artist
				album    = [string]$props.AlbumTitle
				playing  = $playing
				position = $positionMs
				duration = $durationMs
			}

			# Canción nueva: reiniciamos el proceso de la portada.
			if ($key -ne $lastKey) {
				$lastKey = $key
				$coverDone = $false
				$coverTries = 0
				$sendClear = $true
			}

			# IMPORTANTE: muchos reproductores (p. ej. Apple Music) publican la miniatura UNOS SEGUNDOS DESPUES del título.
			# Por eso seguimos intentando (hasta 20 veces) hasta que llegue una imagen.
			if (-not $coverDone -and $coverTries -lt 20) {
				$cover = Get-Cover $props
				$coverTries++
				if ($cover.data -ne '') {
					$data['cover'] = $cover.data
					$coverDone = $true
				} elseif ($sendClear) {
					$data['cover'] = ''   # sin portada por ahora: borra la de la canción anterior
				}
				$sendClear = $false
				if ($cover.error -ne '') { $data['coverError'] = $cover.error }
			}

			[Console]::Out.WriteLine(($data | ConvertTo-Json -Compress))
		}
		[Console]::Out.Flush()
	} catch {
		[Console]::Out.WriteLine('{"session":false,"error":true}')
		[Console]::Out.Flush()
	}
	Start-Sleep -Milliseconds 1000
}
