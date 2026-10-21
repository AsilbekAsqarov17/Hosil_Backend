$broken = @()
Get-ChildItem -Path docs -Recurse -Filter *.md | ForEach-Object {
    $file = $_
    $content = Get-Content -Path $file.FullName -Raw
    $regex = '(?<!!)\[.*?\]\((?!http://|https://|#|mailto:)(.*?)\)|!\[.*?\]\((?!http://|https://|#|mailto:)(.*?)\)'
    $matches = [regex]::Matches($content, $regex)
    foreach ($m in $matches) {
        $link = if ($m.Groups[1].Value) { $m.Groups[1].Value } else { $m.Groups[2].Value }
        $cleanLink = ($link -split '#')[0].Trim()
        
        if ($cleanLink.StartsWith("file:///")) {
            $localFile = $cleanLink.Substring(8).Replace('/', '\')
            if (-not (Test-Path -Path $localFile)) {
                $broken += [PSCustomObject]@{
                    Source = $file.FullName
                    Link = $link
                    ResolvedPath = $localFile
                }
            }
        } elseif (-not [string]::IsNullOrWhiteSpace($cleanLink)) {
            $targetPath = Join-Path -Path $file.DirectoryName -ChildPath $cleanLink
            if (-not (Test-Path -Path $targetPath)) {
                $broken += [PSCustomObject]@{
                    Source = $file.FullName
                    Link = $link
                    ResolvedPath = $targetPath
                }
            }
        }
    }
}

if ($broken.Count -eq 0) {
    Write-Host "ALL LINKS VALID! 0 broken links detected."
} else {
    Write-Host "Detected $($broken.Count) broken links:"
    $broken | Format-Table -AutoSize
}
