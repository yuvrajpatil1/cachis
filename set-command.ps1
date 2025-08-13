$client = New-Object System.Net.Sockets.TcpClient('localhost', 6379)
$stream = $client.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$writer.AutoFlush = $true
$reader = New-Object System.IO.StreamReader($stream)

$pingcommand = "*1`r`n`$4`r`nPING`r`n"
$setcommand = "*3`r`n`$3`r`nSET`r`n`$3`r`nfoo`r`n`$3`r`nbar`r`n"
$getcommand = "*2`r`n`$3`r`nGET`r`n`$3`r`nfoo`r`n"
$writer.Write($setcommand)

$response = $reader.ReadLine()
Write-Host "Server Response: $response"

$reader.Close()
$writer.Close()
$client.Close()