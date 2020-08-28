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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

public class LdapServer implements Serializable {

    private static final long serialVersionUID = -1;

    private static final Logger LOGGER = Logger.getLogger(LdapServer.class.getName());

    private static final String LDAP_TIMEOUT_PARAMETER = "com.sun.jndi.ldap.connect.connectTimeout";
    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    /**
     * Default connectTimeout for connecting.
     */
    private static final int LDAP_CONNECT_TIMEOUT = 5000; // ms

    private String url;
    private String username;
    private String password;
    private int connectTimeout;
    private int interval = 10 * 1000;

    private Hashtable<String, String> env;
    private LdapContext ctx;
    private long errorTimestamp = 0;

    public LdapServer() {
        this(prepareEnv());
    }

    public LdapServer(String server) {
        this(prepareEnv());
        setName(server);
    }

    public LdapServer(String server, String username, String password) {
        this(prepareEnv());
        setName(server);
        this.username = username;
        this.password = password;
    }

    public LdapServer(Hashtable<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public LdapServer setName(String name) {
        this.url = name;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public LdapServer setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public LdapServer setPassword(String password) {
        this.password = password;
        return this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public LdapServer setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    private String urlToHostname(String urlStr) throws URISyntaxException {
        URI uri = new URI(urlStr);
        return uri.getHost();
    }

    private static int getPort(String urlStr) throws URISyntaxException {
        URI uri = new URI(urlStr);
        switch (uri.getScheme()) {
            case "ldaps":
                return 636;
            case "ldap":
                return 389;
        }

        return -1;
    }

    private boolean isReachable(InetAddress addr, int port, int timeOutMillis) {
        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(addr, port), timeOutMillis);
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, String.format("LDAP server %s is not reachable", this), e);
            return false;
        }
    }

    /**
     * Go through all IP addresses and find out if they are reachable.
     * @return true if all IP addresses are reachable, false otherwise
     */
    public boolean isReachable() {
        try {
            for (InetAddress addr : InetAddress.getAllByName(urlToHostname(getUrl()))) {
                // InetAddr.isReachable() is not sufficient as it can only check ICMP and TCP echo.
                if (!isReachable(addr, getPort(getUrl()), getConnectTimeout())) {
                    LOGGER.log(Level.WARNING, "LDAP server {0} is not reachable on {1}",
                            new Object[]{this, addr});
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, String.format("cannot get IP addresses for LDAP server %s", this), e);
            return false;
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, String.format("not a valid URI: %s", getUrl()), e);
            return false;
        }

        return true;
    }

    /**
     * The LDAP server is working only when it is reachable and its connection is not null.
     * This method tries to establish the connection if it is not established already.
     *
     * @return true if it is working
     */
    public synchronized boolean isWorking() {
        if (!isReachable()) {
            return false;
        }

        if (ctx == null) {
            ctx = connect();
        }
        return ctx != null;
    }

    /**
     * Connects to the LDAP server.
     *
     * @return the new connection or null
     */
    private synchronized LdapContext connect() {
        LOGGER.log(Level.INFO, "Connecting to LDAP server {0} ", this.toString());

        if (errorTimestamp > 0 && errorTimestamp + interval > System.currentTimeMillis()) {
            LOGGER.log(Level.WARNING, "LDAP server {0} is down", this.url);
            close();
            return null;
        }

        if (ctx == null) {
            env.put(Context.PROVIDER_URL, this.url);

            if (this.username != null) {
                env.put(Context.SECURITY_PRINCIPAL, this.username);
            }
            if (this.password != null) {
                env.put(Context.SECURITY_CREDENTIALS, this.password);
            }
            if (this.connectTimeout > 0) {
                env.put(LDAP_TIMEOUT_PARAMETER, Integer.toString(this.connectTimeout));
            }

            try {
                ctx = new InitialLdapContext(env, null);
                ctx.reconnect(null);
                ctx.setRequestControls(null);
                LOGGER.log(Level.INFO, "Connected to LDAP server {0}", this.toString());
                errorTimestamp = 0;
            } catch (NamingException ex) {
                LOGGER.log(Level.WARNING, "LDAP server {0} is not responding", env.get(Context.PROVIDER_URL));
                errorTimestamp = System.currentTimeMillis();
                close();
                return ctx = null;
            }
        }

        return ctx;
    }

    /**
     * Lookups the LDAP server.
     *
     * @param name base dn for the search
     * @param filter LDAP filter
     * @param cons controls for the LDAP request
     * @return LDAP enumeration with the results
     *
     * @throws NamingException naming exception
     */
    public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons) throws NamingException {
        return search(name, filter, cons, false);
    }

    /**
     * Perform LDAP search.
     *
     * @param name base dn for the search
     * @param filter LDAP filter
     * @param controls controls for the LDAP request
     * @param reconnected flag if the request has failed previously
     * @return LDAP enumeration with the results
     *
     * @throws NamingException naming exception
     */
    public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls controls, boolean reconnected)
            throws NamingException {

        if (!isWorking()) {
            close();
            throw new CommunicationException(String.format("LDAP server \"%s\" is down",
                    env.get(Context.PROVIDER_URL)));
        }

        if (reconnected) {
            LOGGER.log(Level.INFO, "LDAP server {0} reconnect", env.get(Context.PROVIDER_URL));
            close();
            if ((ctx = connect()) == null) {
                throw new CommunicationException(String.format("LDAP server \"%s\" cannot reconnect",
                        env.get(Context.PROVIDER_URL)));
            }
        }

        try {
            synchronized (this) {
                return ctx.search(name, filter, controls);
            }
        } catch (CommunicationException ex) {
            if (reconnected) {
                throw ex;
            }
            return search(name, filter, controls, true);
        }
    }

    /**
     * Closes the server context.
     */
    public synchronized void close() {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ex) {
                LOGGER.log(Level.WARNING, "cannot close LDAP server {0}", getUrl());
            }
            ctx = null;
        }
    }

    private static Hashtable<String, String> prepareEnv() {
        Hashtable<String, String> e = new Hashtable<String, String>();

        e.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        e.put(LDAP_TIMEOUT_PARAMETER, Integer.toString(LDAP_CONNECT_TIMEOUT));

        return e;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getUrl());

        if (getConnectTimeout() > 0) {
            sb.append(" timeout: ");
            sb.append(getConnectTimeout());
        }

        if (getUsername() != null && !getUsername().isEmpty()) {
            sb.append(" username: ");
            sb.append(getUsername());
        }

        return sb.toString();
    }
}
