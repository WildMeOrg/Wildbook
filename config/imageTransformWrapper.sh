#!/bin/sh

#create a placeholder (as the below may take "time"

echo $3
/usr/bin/convert -gravity Center -background '#AAA' -size 800x600 -pointsize 80 'label:Processing image...' $2


#imageTransformCommand = /usr/local/bin/imageTransformWrapper.sh %imagesource %imagetarget %widthx%height%T4%T5 %t0,%t1,%t2,%t3,0,0

#/usr/bin/convert $1 -matte -virtual-pixel Transparent -affine $4 -transform +repage -gravity Center -crop $3 $2
cmd="/usr/bin/convert $1 -matte -virtual-pixel Transparent -affine $4 -transform +repage -gravity Center -crop $3 $2"

echo $cmd > /tmp/TEST

$cmd
