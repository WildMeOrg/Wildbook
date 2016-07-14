#!/bin/bash

###   imageTransformCommand = /usr/local/bin/imageTransformWrapper.sh %imagesource %imagetarget %width %height %t0 %t1 %t2 %t3 %t4 %t5

#create a placeholder (as the below may take "time")

source=$1
target=$2
width=$3
height=$4
t0=$5
t1=$6
t2=$7
t3=$8
t4=$9
t5=${10}



/usr/bin/convert -gravity Center -background '#AAA' -size 800x600 -pointsize 80 'label:Processing image...' $target

echo "$source
$target
$width
$height
$t0
$t1
$t2
$t3
$t4
$t5" > /tmp/args



#identity matrix means we dont need to do crazy transform, only crop
if [ "$t0" = "1.0" -a "$t1" = "0.0" -a "$t2" = "0.0" -a "$t3" = "1.0" ] ; then
	if [[ $t4 == \-* ]] ; then
		offset=$t4
	else
		offset="+$t4"
	fi
	if [[ $t5 == \-* ]] ; then
		offset="$offset$t5"
	else
		offset="$offset+$t5"
	fi
	crop="${width}x$height$offset"

	cmd="/usr/bin/convert -strip +repage -crop $crop $source $target"


#for using the transform matrix, we set it to translate of 0,0 and let the crop work by negating these offset values
else
	if [[ $t4 == \-* ]] ; then
		offset="+${t4:1}"
	else
		offset="-$t4"
	fi
	if [[ $t5 == \-* ]] ; then
		offset="$offset+${t5:1}"
	else
		offset="$offset-$t5"
	fi
	crop="${width}x$height$offset"

	cmd="/usr/bin/convert $source -strip -matte -virtual-pixel Transparent -affine $t0,$t1,$t2,$t3,0,0 -transform +repage -gravity Center -crop $crop $target"
fi


echo $cmd > /tmp/TEST
echo $cmd



$cmd

