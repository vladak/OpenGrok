package org.opengrok.web.api.v1.filter;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.web.api.v1.controller.SearchController;
import org.opengrok.web.api.v1.controller.SuggesterController;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures that requests to selected API endpoints contain user information
 * (i.e. authentication).
 * This is handy in case the application server authentication is mis-configured.
 */
@Provider
@PreMatching
public class AuthenticationFilter implements ContainerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);

    /**
     * Endpoint paths that are checked by this filter.
     * @see SearchController#search(HttpServletRequest, String, String, String, String, String, String,
     * java.util.List, int, int)
     * @see SuggesterController#getSuggestions(org.opengrok.web.api.v1.suggester.model.SuggesterQueryData)
     * @see SuggesterController#getConfig()
     */
    private static final Set<String> checkedPaths = new HashSet<>(Arrays.asList(
            SearchController.PATH, SuggesterController.PATH, SuggesterController.PATH + "/config"));

    @Context
    private HttpServletRequest request;

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        // Short-circuit in case of no authorization configuration.
        if (env.getPluginStack() == null) {
            return;
        }

        String path = context.getUriInfo().getPath();
        if (checkedPaths.contains(path) && request.getRemoteUser() == null) {
            // There is authorization stack defined. There is no authorization without authentication.
            // TODO this should do the same thing as AuthorizationFilter modulo HTTP error code - refactor
            LOGGER.log(Level.FINE, "Request to path {0} is missing user info", path);
            context.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }
}
