#!/bin/bash

psid=$$

cd /data/blur

source ./env/bin/activate

function join_by { local d=$1; shift; local f=$1; shift; printf %s "$f" "${@/#/$d}"; }


log=/tmp/blur.log
echo === START psid=$psid `/bin/date` $* ================ >> $log


export TEST_IMAGE_LIST="['"`join_by "', '" $*`"']"

#echo $TEST_IMAGE_LIST


python3 src/models/app.py >> $log 2>&1

for i in $* ; do
	fn=${i##*/}
	fn="${fn%.*}"
	echo $i `cat labels/$fn.txt`
	cat labels/$fn.txt >> $log
	mv labels/$fn.txt labels/$psid-$fn.txt
done

echo === END    psid=$psid `/bin/date` $* ================ >> $log
