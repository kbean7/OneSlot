
$baseUrl = "http://localhost:8080"
$spaceId = 1

# 미래의 테스트 날짜와 시간을 설정
$testDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
$startAt = "${testDate}T10:00:00"
$endAt = "${testDate}T11:00:00"

Write-Host "테스트 날짜: $testDate"

# 1. 해당 시간이 예약 가능한지 확인
try {
    $availability = Invoke-RestMethod `
        -Uri "$baseUrl/api/reservations/spaces/$spaceId/availability?date=$testDate" `
        -Method Get
}
catch {
    Write-Host "예약 가능 시간 조회 실패. Spring Boot 서버를 확인하세요."
    exit 1
}

$isFree = @($availability.availableSlots | Where-Object {
    $_.start -eq "10:00:00"
}).Count -eq 1

if (-not $isFree) {
    Write-Host "테스트 중단: 해당 시간은 이미 예약되어 있거나 예약할 수 없습니다."
    exit 1
}

# 2. 10명 동시 예약 요청
$jobs = 1..10 | ForEach-Object {
    $userId = $_

    Start-Job -ScriptBlock {
        param($baseUrl, $userId, $spaceId, $startAt, $endAt)

        $body = @{
            userId = $userId
            spaceId = $spaceId
            startAt = $startAt
            endAt = $endAt
        } | ConvertTo-Json

        try {
            $response = Invoke-WebRequest `
                -Uri "$baseUrl/api/reservations" `
                -Method Post `
                -ContentType "application/json" `
                -Body $body

            $data = $response.Content | ConvertFrom-Json

            [PSCustomObject]@{
                UserId = $userId
                Status = [int]$response.StatusCode
                ReservationId = $data.reservationId
            }
        }
        catch {
            $status = 0

            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            }

            [PSCustomObject]@{
                UserId = $userId
                Status = $status
                ReservationId = $null
            }
        }

    } -ArgumentList $baseUrl, $userId, $spaceId, $startAt, $endAt
}

# 3. 요청 결과 수집
$results = @($jobs | Receive-Job -Wait)
$jobs | Remove-Job

$results | Sort-Object UserId | Format-Table -AutoSize

# 4. 성공 및 실패 건수 확인
$successCount = @($results | Where-Object {
    $_.Status -eq 201
}).Count

$conflictCount = @($results | Where-Object {
    $_.Status -eq 409
}).Count

Write-Host "전체 요청: $($results.Count)"
Write-Host "예약 성공: $successCount"
Write-Host "중복 거절: $conflictCount"

if ($successCount -eq 1 -and $conflictCount -eq 9) {
    Write-Host "동시성 테스트 통과!"
}
else {
    Write-Host "동시성 테스트 결과 확인 필요"
}
