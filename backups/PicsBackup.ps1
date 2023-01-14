Write-Output "Syncing from $PSScriptRoot..."
Write-Output (Get-Location)
$date = Get-Date -Format "yyyy-MM-dd"
$folder = (Join-Path $PSScriptRoot $date)
& "aws" s3 sync s3://malliina-pics-small (Join-Path $folder "small") --profile fritid
& "aws" s3 sync s3://malliina-pics-medium (Join-Path $folder "medium") --profile fritid
& "aws" s3 sync s3://malliina-pics-large (Join-Path $folder "large") --profile fritid
& "aws" s3 sync s3://malliina-pics (Join-Path $folder "original") --profile fritid
Compress-Archive -Path $folder -DestinationPath (Join-Path $PSScriptRoot "pics-$date.zip")
