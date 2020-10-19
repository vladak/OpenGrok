#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#

#
# Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
#

import json
import logging

import requests

from .patterns import COMMAND_PROPERTY
from .webutil import get_proxies

CONTENT_TYPE = 'Content-Type'
APPLICATION_JSON = 'application/json'   # default


def do_api_call(verb, uri, params=None, headers=None, data=None):
    handler = getattr(requests, verb.lower())
    if handler is None or not callable(handler):
        raise Exception('Unknown HTTP verb: {}'.format(verb))

    return handler(
        uri,
        data=data,
        params=params,
        headers=headers,
        proxies=get_proxies(uri)
    )


def call_rest_api(command, pattern, name):
    """
    Make RESTful API call. Occurrence of the pattern in the URI
    (first part of the command) or data payload will be replaced by the name.

    Default content type is application/json.

    :param command: command (list of URI, HTTP verb, data payload)
    :param pattern: pattern for command name and/or data substitution
    :param name: command name
    :return return value from given requests method
    """

    logger = logging.getLogger(__name__)

    if not isinstance(command, dict) or command.get(COMMAND_PROPERTY) is None:
        raise Exception("invalid command")

    command = command[COMMAND_PROPERTY]

    uri, verb, data, *_ = command
    try:
        headers = command[3]
        if headers and not isinstance(headers, dict):
            raise Exception("headers must be a dictionary")
    except IndexError:
        headers = {}

    if headers is None:
        headers = {}

    if pattern and name:
        uri = uri.replace(pattern, name)

    header_names = [x.lower() for x in headers.keys()]

    if data:
        if CONTENT_TYPE.lower() not in header_names:
            logger.debug("Adding header: {} = {}".
                         format(CONTENT_TYPE, APPLICATION_JSON))
            headers[CONTENT_TYPE] = APPLICATION_JSON

        for (k, v) in headers.items():
            if k.lower() == CONTENT_TYPE.lower():
                if headers[k].lower() == APPLICATION_JSON.lower():
                    logger.debug("Converting {} to JSON".format(data))
                    data = json.dumps(data)
                break

        if pattern and name:
            data = data.replace(pattern, name)
        logger.debug("entity data: {}".format(data))

    logger.debug("{} API call: {} with data '{}' and headers: {}".
                 format(verb, uri, data, headers))
    r = do_api_call(verb, uri, headers=headers, data=data)
    if r is not None:
        logger.debug("API call result: {}".format(r))
        r.raise_for_status()
    return r
