# Copies skills that Claude (Cowork) staged in skills-sync\ into .claude\skills\, then removes skills-sync\.
# Claude can't write inside .claude from the Claude app, so it drops updates here instead.
# Run from the project root:  powershell -ExecutionPolicy Bypass -File .\sync-skills.ps1
$ErrorActionPreference = 'Stop'
$root   = $PSScriptRoot
$inbox  = Join-Path $root 'skills-sync'
$target = Join-Path (Join-Path $root '.claude') 'skills'

if (-not (Test-Path $inbox)) { Write-Host 'Nothing to sync: skills-sync\ does not exist.'; exit 0 }

$skills = @(Get-ChildItem $inbox -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'SKILL.md') })
if ($skills.Count -eq 0) { Write-Host 'Nothing to sync: no SKILL.md found under skills-sync\.'; exit 0 }

foreach ($s in $skills) {
    $dest   = Join-Path $target $s.Name
    $srcMd  = Join-Path $s.FullName 'SKILL.md'
    $destMd = Join-Path $dest 'SKILL.md'
    if (-not (Test-Path $destMd))                                          { $state = 'added    ' }
    elseif ((Get-FileHash $srcMd).Hash -eq (Get-FileHash $destMd).Hash)    { $state = 'unchanged' }
    else                                                                   { $state = 'updated  ' }
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item -Path (Join-Path $s.FullName '*') -Destination $dest -Recurse -Force
    Write-Host "$state $($s.Name)"
}

Remove-Item $inbox -Recurse -Force
Write-Host "Done: $($skills.Count) skill(s) copied into .claude\skills. Review with: git diff -- .claude/skills"
