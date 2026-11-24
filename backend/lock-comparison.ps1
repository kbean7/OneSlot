
$baseUrl = "http://localhost:8080"
$spaceId = 1

$tests = @(
    @{
        Name = "PESSIMISTIC"
        Url = "$baseUrl/api/reservations"
        Date = "2026-12-10"
    },
    @{
        Name = "OPTIMISTIC"
        Url = "$baseUrl/api/reservations/experiments/optimistic"
        Date = "2026-12-11"
    }
)

foreach ($test in $tests) {
    Write-Host ""
    Write-Host "===== $($test.Name) ====="

    $startAt = "$($test.Date)T09:00:00"
    $endAt = "$($test.Date)T10:10:00"

    $jobs = 1..10 | ForEach-Object {
        $userId = $_

        Start-Job -ScriptBlock {
            param($url, $userId, $spaceId, $startAt, $endAt)

            $body = @{
                userId = $userId
                spaceId = $spaceId
                startAt = $startAt
                endAt = $endAt
            } | ConvertTo-Json

            $watch = [System.Diagnostics.Stopwatch]::StartNew()

            try {
                $response = Invoke-WebRequest `
                    -Uri $url `
                    -Method Post `
                    -ContentType "application/json" `
                    -UseBasicParsing `
                    -Body $body

                $statusCode = [int]$response.StatusCode
            }
            catch {
                $statusCode = 0

                if ($_.Exception.Response) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
            }

            $watch.Stop()

            [PSCustomObject]@{
                UserId = $userId
                Status = $statusCode
                Milliseconds = $watch.ElapsedMilliseconds
            }

        } -ArgumentList $test.Url, $userId, $spaceId, $startAt, $endAt
    }

    $results = @($jobs | Receive-Job -Wait)
    $jobs | Remove-Job

    $results | Sort-Object UserId | Format-Table -AutoSize

    $success = @($results | Where-Object Status -eq 201).Count
    $conflict = @($results | Where-Object Status -eq 409).Count

    $average = (
        $results |
        Measure-Object Milliseconds -Average
    ).Average

    Write-Host "Requests: $($results.Count)"
    Write-Host "Success: $success"
    Write-Host "Conflict: $conflict"
    Write-Host "Average(ms): $([math]::Round($average, 2))"
}
