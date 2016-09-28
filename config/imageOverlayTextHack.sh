#!/bin/bash
#imageResizeMaintainAspectCommand = /usr/local/bin/imageOverlayTextHack.sh %imagesource %imagetarget %width %height %comment %arg TRUE
#imageResizeCommand = /usr/bin/convert -strip -quality 80 -resize %widthx%height^ %imagesource -gravity center -crop %widthx%height+0+0 -set comment %comment %imagetarget
#imageResizeMaintainAspectCommand = /usr/bin/convert -strip -quality 80 -resize %widthx%height\> %imagesource -set comment %comment -font Verdana-Normal -pointsize 50 -gravity NorthEast -fill white -undercolor #00000030 -annotate +20+20 %arg %imagetarget
imagesource=$1
imagetarget=$2
width=$3
height=$4
comment=$5
arg=$6
keepAspectRatio="$7"

pointsize=$(($width / 25))
if (( $pointsize > 30 )) ; then
	pointsize=30
fi
if (( $pointsize < 7 )) ; then
	arg=""
fi

margin=$((width / 60))

if [ "$arg" = "" ] ; then
	/usr/bin/convert -strip -quality 80 -resize ${width}x$height^ "$imagesource" -gravity center -crop ${width}x${height}+0+0 -set comment "$comment" "$imagetarget"
elif [ "$keepAspectRatio" = "" ] ; then
	arg=$'\u00A9'$arg
	/usr/bin/convert -strip -quality 80 -resize ${width}x$height^ "$imagesource" -gravity center -crop ${width}x${height}+0+0 -set comment "$comment" +repage -font Verdana-Normal -pointsize $pointsize -gravity NorthEast -fill white -undercolor '#00000030' -annotate +$margin+$margin "$arg" "$imagetarget"
else
	arg=$'\u00A9'$arg
	/usr/bin/convert -strip -quality 80 -resize ${width}x$height\> "$imagesource" -set comment "$comment" -font Verdana-Normal -pointsize $pointsize -gravity NorthEast -fill white -undercolor '#00000030' -annotate +$margin+$margin "$arg" "$imagetarget"
fi
