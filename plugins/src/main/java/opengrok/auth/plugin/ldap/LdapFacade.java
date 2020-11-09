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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LdapFacade implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(LdapFacade.class.getName());

    /**
     * Map of currently used configurations.<br>
     * file path => object.
     */
    private static final Map<String, Configuration> LOADED_CONFIGURATIONS = new ConcurrentHashMap<>();

    private static final Map<String, LdapPool> map = new HashMap<>();

    private LdapFacade() {
        // private to ensure everyone has to go through getPool()
    }

    public static LdapPool getPool(String configurationPath) {
        LdapPool pool = map.get(configurationPath);
        if (pool == null) {
            Configuration cfg;
            try {
                cfg = getConfiguration(configurationPath);
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Unable to read the configuration from %s",
                        configurationPath), ex);
            }

            pool = new LdapPool(cfg);
            map.put(configurationPath, pool);
        }

        return pool;
    }

    /**
     * Return the configuration for the given path. If the configuration is
     * already loaded, use that one. Otherwise try to load the file into the
     * configuration.
     *
     * @param configurationPath the path to the file with the configuration
     * @return the object (new or from cache)
     * @throws IOException when any IO error occurs
     */
    protected static Configuration getConfiguration(String configurationPath) throws IOException {
        Configuration cfg;

        if ((cfg = LOADED_CONFIGURATIONS.get(configurationPath)) == null) {
            LOADED_CONFIGURATIONS.put(configurationPath,
                    cfg = Configuration.read(new File(configurationPath)));
        }
        return cfg;
    }

    @Override
    public void close() throws IOException {
        // TODO: synchronize with getPool()
        for (LdapPool pool : map.values()) {
            pool.close();
        }

        LOADED_CONFIGURATIONS.clear();
    }
}
