#!/bin/sh

if [ ! -f lena_std.tif ]; then
  curl http://www.cs.cmu.edu/~chuck/lennapg/lena_std.tif -O
fi

ffmpeg -i lena_std.tif -pix_fmt yuv420p -codec:v libx265 -crf 15 -preset placebo -x265-params info=0 -f hevc lena_std.hvc -y
MP4Box -add-image lena_std.hvc:primary -ab heic -new lena_std.heic
