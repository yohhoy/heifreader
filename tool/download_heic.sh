#!/bin/sh
URL_BASE=https://github.com/nokiatech/heif/raw/gh-pages/content/images

function download() {
  if [ ! -f $1 ]; then
    curl -L ${URL_BASE}/$1 -O
  fi
}

download autumn_1440x960.heic
download cheers_1440x960.heic
download crowd_1440x960.heic
download old_bridge_1440x960.heic
download random_collection_1440x960.heic
download season_collection_1440x960.heic
download ski_jump_1440x960.heic
download spring_1440x960.heic
download summer_1440x960.heic
download surfer_1440x960.heic
download winter_1440x960.heic

