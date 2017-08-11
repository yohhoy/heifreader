#!/bin/sh

if [ ! -f lena_std.tif ]; then
  curl http://www.cs.cmu.edu/~chuck/lennapg/lena_std.tif -O
fi

CRF=25
X265OPTS="ssim-rd=1:wpp=0:ssim=1"
#X265OPTS+=":sao=0:deblock=0:strong-intra-smoothing=0"

ffmpeg -i lena_std.tif -pix_fmt yuv420p -codec:v libx265 -crf $CRF -preset placebo -tune ssim -x265-params keyint=1:info=0:${X265OPTS} -f hevc lena_std.hvc -y -hide_banner
MP4Box -add-image lena_std.hvc:primary -ab heic -new lena_std.heic
ffmpeg -i lena_std.hvc hena_std.heic.png -y -hide_banner
