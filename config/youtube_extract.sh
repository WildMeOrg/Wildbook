#!/bin/sh

mp4in=$1
outputdir=$2
fps=$3

if [ "$fps" = "" ] ; then
	fps=0.5
fi

if [ ! -d $outputdir ] ; then
	mkdir -p $outputdir
fi
rm -f $outputdir/*jpg
export FFREPORT="file=$outputdir/ffreport.log:level=32"

### note: keyframes doesnt really seem to work well... :( best to go with framerate
#keyframes
###ffmpeg -i mn5a3XJhJd4/mn5a3XJhJd4.mp4 -qscale:v 2 -vf select="eq(pict_type\,I)" -vsync 0  frame%05d.jpg

#by framerate (r = fps)
ffmpeg -report -loglevel quiet -i $mp4in -r $fps -f image2 $outputdir/frame%05d.jpg

ls $outputdir/frame*jpg
