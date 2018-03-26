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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * RESTful API for {@code Message}.
 *
 * @author Vladimir Kotal
 */
public class MessageAPI extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAPI.class);
    
    private final int JSON_MSG_BUFFER_SIZE = 16384; // read buffer size in bytes, specifically for Messages in JSON
    private final int JSON_MAX_LEN = 1024 * 1024; // impose limit on the size of incoming JSON
    
    private final String ATTRIBUTE_RESULT = "result";
    
    private class RequestTooLongException extends Exception {
        private final int maxLen;
        
        private RequestTooLongException(String message, int max) {
            maxLen = max;
        }
    }
    
    /**
     * Read all data from the request and return it as a string.
     * @param req request
     * @return data converted to string
     * @throws IOException 
     */
    private String getStringFromReq(HttpServletRequest req, int len,
            int maxLength) throws IOException, RequestTooLongException {
        
        StringBuilder stringBuilder = new StringBuilder();

        try (BufferedReader bufferedReader = req.getReader()) {
            char[] charBuffer = new char[len];
            int bytesRead, sumBytes = 0;
            
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                if (sumBytes > maxLength) {
                    throw new RequestTooLongException("incoming data exceeded maximum",
                            maxLength);
                }
                stringBuilder.append(charBuffer, 0, bytesRead);
                sumBytes += bytesRead;
            }
        }
        
        return stringBuilder.toString();
    }

    /**
     * Decode Message object from incoming JSON, apply it and send the JSON 
     * result back.
     * @param req request
     * @param resp response
     * @throws java.io.IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        
        String jsonStr; // JSON string candidate
        try {
            jsonStr = getStringFromReq(req, JSON_MSG_BUFFER_SIZE, JSON_MAX_LEN);
        } catch (RequestTooLongException ex) {
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "the request is too long");
            LOGGER.log(Level.SEVERE, "request size exceeded limit");
            return;
        }

        Message message = Message.fromJson(jsonStr);
        if (message == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "failed to convert JSON to Message object");
            LOGGER.log(Level.SEVERE, "cannot convert JSON to Message: {0}", jsonStr);
            return;
        }
    
        byte[] resultBytes;
        try {
            resultBytes = message.apply(RuntimeEnvironment.getInstance());
        } catch (Exception ex) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "failed to apply incoming Message");
            LOGGER.log(Level.SEVERE, "cannot apply message decoded from JSON: " + jsonStr, ex);
            return;
        }
        
        // XXX Convert the bytes of the result to JSON.
        // XXX need to escape the embedded XML or convert to Base64
        
        resp.setContentType("application/json");
        // resp.getWriter().write(result.toString());
    }
}
