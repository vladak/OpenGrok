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
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
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

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE
)
public class LdapServer implements Serializable {

    private static final long serialVersionUID = -1;

    private static final Logger LOGGER = Logger.getLogger(LdapServer.class.getName());

    private static final String LDAP_CONNECT_TIMEOUT_PARAMETER = "com.sun.jndi.ldap.connect.timeout";
    private static final String LDAP_READ_TIMEOUT_PARAMETER = "com.sun.jndi.ldap.read.timeout";
    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    // default connectTimeout value in milliseconds
    private static final int DEFAULT_LDAP_CONNECT_TIMEOUT = 5000;
    // default readTimeout value in milliseconds
    private static final int DEFAULT_LDAP_READ_TIMEOUT = 3000;

    @JsonProperty
    private String url;
    @JsonProperty
    private String username;
    @JsonProperty
    private String password;
    @JsonProperty
    private int connectTimeout;
    @JsonProperty
    private int readTimeout;

    private int interval = 10 * 1000;
    private final transient Map<String, String> env;
    private transient LdapContext ctx;
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

    public LdapServer(Map<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public final LdapServer setName(String name) {
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

    public int getReadTimeout() {
        return readTimeout;
    }

    public LdapServer setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
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

    /**
     * This method converts the scheme from URI to port number.
     * It is limited to the ldap/ldaps schemes.
     * The method could be static however then it cannot be easily mocked in testing.
     * @return port number or -1 if the scheme in given URI is not known
     * @throws URISyntaxException if the URI is not valid
     */
    public int getPort() throws URISyntaxException {
        URI uri = new URI(getUrl());
        switch (uri.getScheme()) {
            case "ldaps":
                return 636;
            case "ldap":
                return 389;
            default: return -1;
        }
    }

    @JsonIgnore
    private boolean isReachable(InetAddress addr, int port, int timeOutMillis) {
        try (Socket soc = new Socket()) {
            soc.connect(new InetSocketAddress(addr, port), timeOutMillis);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Wraps InetAddress.getAllByName() so that it can be mocked in testing.
     * (mocking static methods is not really possible with Mockito)
     * @param hostname hostname string
     * @return array of InetAddress objects
     * @throws UnknownHostException if the host cannot be resolved to any IP address
     */
    public InetAddress[] getAddresses(String hostname) throws UnknownHostException {
        return InetAddress.getAllByName(hostname);
    }

    /**
     * Go through all IP addresses and find out if they are reachable.
     * @return true if all IP addresses are reachable, false otherwise
     */
    @JsonIgnore
    public boolean isReachable() {
        try {
            InetAddress[] addresses = getAddresses(urlToHostname(getUrl()));
            if (addresses.length == 0) {
                LOGGER.log(Level.WARNING, "LDAP server {0} does not resolve to any IP address", this);
                return false;
            }

            for (InetAddress addr : addresses) {
                // InetAddr.isReachable() is not sufficient as it can only check ICMP and TCP echo.
                int port = getPort();
                if (!isReachable(addr, port, getConnectTimeout())) {
                    LOGGER.log(Level.WARNING, "LDAP server {0} is not reachable on {1}:{2}",
                            new Object[]{this, addr, Integer.toString(port)});
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
    @JsonIgnore
    public synchronized boolean isWorking() {
        if (ctx == null) {
            if (!isReachable()) {
                return false;
            }

            ctx = connect();
        }
        return ctx != null;
    }

    /**
     * Connects to the LDAP server.
     *
     * @return the new connection or null
     */
    @Nullable
    private synchronized LdapContext connect() {
        LOGGER.log(Level.INFO, "Connecting to LDAP server {0} ", this);

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
                env.put(LDAP_CONNECT_TIMEOUT_PARAMETER, Integer.toString(this.connectTimeout));
            }
            if (this.readTimeout > 0) {
                env.put(LDAP_READ_TIMEOUT_PARAMETER, Integer.toString(this.readTimeout));
            }

            try {
                ctx = new InitialLdapContext(new Hashtable<>(env), null);
                ctx.setRequestControls(null);
                LOGGER.log(Level.INFO, "Connected to LDAP server {0}", this);
                errorTimestamp = 0;
            } catch (NamingException ex) {
                LOGGER.log(Level.WARNING,
                        String.format("LDAP server %s is not responding", env.get(Context.PROVIDER_URL)), ex);
                errorTimestamp = System.currentTimeMillis();
                close();
                ctx = null;
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

    private static Map<String, String> prepareEnv() {
        var e = new HashMap<String, String>();

        e.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        e.put(LDAP_CONNECT_TIMEOUT_PARAMETER, Integer.toString(DEFAULT_LDAP_CONNECT_TIMEOUT));
        e.put(LDAP_READ_TIMEOUT_PARAMETER, Integer.toString(DEFAULT_LDAP_READ_TIMEOUT));

        return e;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getUrl());

        if (getConnectTimeout() > 0) {
            sb.append(", connect timeout: ");
            sb.append(getConnectTimeout());
        }
        if (getReadTimeout() > 0) {
            sb.append(", read timeout: ");
            sb.append(getReadTimeout());
        }

        if (getUsername() != null && !getUsername().isEmpty()) {
            sb.append(", username: ");
            sb.append(getUsername());
        }

        return sb.toString();
    }
}
