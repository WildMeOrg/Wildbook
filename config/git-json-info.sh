#!/bin/sh

date=`git log -1 --format='%ai'`
hash=`git log -1 --format='%H'`
branch=`git name-rev --name-only HEAD`
now=`date --rfc-3339=seconds`

echo "{
   \"date\": \"$date\",
   \"built\": \"$now\",
   \"hash\": \"$hash\",
   \"branch\": \"$branch\"
}" > git-info.json
