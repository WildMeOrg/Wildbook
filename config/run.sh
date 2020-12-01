#!/bin/bash


function join_by { local d=$1; shift; local f=$1; shift; printf %s "$f" "${@/#/$d}"; }


log=/tmp/blur.log
echo === START `/bin/date` $* ================ >> $log


export TEST_IMAGE_LIST="['"`join_by "', '" $*`"']"

#echo $TEST_IMAGE_LIST


python3 src/models/app.py >> $log 2>&1

for i in $* ; do
	echo $i `cat labels/$i.txt`
done

echo === END   `/bin/date` $* ================ >> $log
