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
package org.opensolaris.opengrok.configuration.messages;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Message factory class
 *
 * @author Krystof Tulinger
 * @author Vladimir Kotal
 */
public class MessageFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFactory.class);
    
    private static String getClassName(String type) {
        String classname = Message.class.getPackage().getName();
        classname += "." + type.substring(0, 1).toUpperCase(Locale.getDefault());
        classname += type.substring(1) + "Message";
        return classname;
    }
    
    /**
     * return Class of message according to its type
     * @param type of message
     * @return concrete Class of the message
     */
    protected static Class getMessageClass(String type) {
        try {
            Class<?> concreteClass = Class.forName(getClassName(type));
            return concreteClass;
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
    
    /**
     * Factory method for particular message types.
     *
     * @param type the message type
     * @return specific message instance for the given type or null
     */
    public static Message createMessage(String type) {
        try {
            Class<?> concreteClass = getMessageClass(type);
            return (Message) concreteClass.getDeclaredConstructor().newInstance();
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Couldn't create message object of type \"{0}\".", type);
        }
        return null;
    }
}
