#!/bin/sh

ytid=$1
dir=$2
yturl="https://www.youtube.com/watch?v=$ytid"

if [ ! -d $dir ] ; then
	mkdir $dir
fi


	#--write-annotations \
	#--write-description \

youtube-dl -o $dir/$ytid.mp4 \
	-q \
	-f mp4 \
	--write-info-json \
	--write-all-thumbnails \
	$yturl
