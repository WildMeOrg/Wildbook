#!/bin/sh

###   imageFeatureCommand = /usr/local/bin/imageFeatureWrapper.sh %imagesource %imagetarget %fx %fy %fw %fh

x2=`echo "$3+$5" | bc`
y2=`echo "$4+$6" | bc`
convert -strip -quality 70 $1 -draw "fill none stroke-linecap round stroke-width 3 stroke rgba(255,255,0,0.4) rectangle $3,$4 $x2,$y2" $2

