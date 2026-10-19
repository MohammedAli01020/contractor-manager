# يولّد سرًّا عشوائيًا قويًا لـ APP_JWT_SECRET
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
