#!/bin/sh
set -e

date=$(git log -1 --format='%ai')
hash=$(git log -1 --format='%H')
branch=$(git name-rev --name-only HEAD)
now=$(date -u '+%Y-%m-%d %H:%M:%S+00:00')

cat > git-info.json << EOF
{
   "date": "$date",
   "built": "$now",
   "hash": "$hash",
   "branch": "$branch"
}
EOF
