/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Simple servlet to produce a page with API help.
 *
 * @author vkotal
 */
public class APIhelp extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAPI.class);
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // go through all Message types (reflection) and produce HTML page with API doc by calling getHelp() methods for each
        // and convert the JSON strings to HTML somehow
        
        // plus get documentation for the JSON search servlet
        throw new UnsupportedOperationException("not implemented");
    }
}
