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
package org.opensolaris.opengrok.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * utilities for date/time manipulation
 * 
 * @author Vladimir Kotal
 */
public class DateUtils {
    /**
     * Compare two dates with granularity in seconds.
     * @param date1 first date
     * @param date2 second date
     * @return true if equal within seconds, false otherwise
     */
    public static boolean dateEqualsSeconds(Date date1, Date date2) {
        // quick and dirty way how to compare two dates seconds-wise
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        
        return (df.format(date1).equals(df.format(date2)));
    }
}
