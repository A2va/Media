#!/bin/bash

source ${SCRIPTS_DIR}/common-functions.sh

VPX_VERSION=v1.9.0

downloadTarArchive \
  "libvpx" \
  "https://chromium.googlesource.com/webm/libvpx/+archive/${VPX_VERSION}.tar.gz" \
  true