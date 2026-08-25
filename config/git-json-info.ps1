# Windows equivalent of git-json-info.sh
$ErrorActionPreference = 'Stop'

$date   = git log -1 --format='%ai'
$hash   = git log -1 --format='%H'
$branch = git name-rev --name-only HEAD
$now    = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss+00:00")

@"
{
   "date": "$date",
   "built": "$now",
   "hash": "$hash",
   "branch": "$branch"
}
"@ | Out-File -FilePath "git-info.json" -Encoding utf8NoBOM
