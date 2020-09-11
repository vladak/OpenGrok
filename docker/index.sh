#!/bin/bash

#
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
#

LOCK_NAME="index"
URI="http://localhost:8080"
# $OPS can be overwritten by environment variable
OPS=${INDEXER_FLAGS:='-H -P -S -G'}

lockfile-create ${LOCK_NAME}
lockfile-touch ${LOCK_NAME} &
BADGER="$!"

if [ -z $NOMIRROR ]; then
	date +"%F %T Mirroring starting"
	opengrok-mirror --all --uri "$URI"
	date +"%F %T Mirroring finished"
fi

date +"%F %T Indexing starting"
opengrok-indexer \
    -a /opengrok/lib/opengrok.jar -- \
    -s /opengrok/src \
    -d /opengrok/data \
    --remote on \
    -W /opengrok/etc/configuration.xml \
    -U "$URI" \
    $OPS \
    $INDEXER_OPT "$@"
date +"%F %T Indexing finished"

kill "${BADGER}"
lockfile-remove ${LOCK_NAME}
