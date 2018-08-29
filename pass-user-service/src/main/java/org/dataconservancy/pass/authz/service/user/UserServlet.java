/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.pass.authz.service.user;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataconservancy.pass.authz.AuthUser;
import org.dataconservancy.pass.authz.AuthUserProvider;
import org.dataconservancy.pass.authz.LogUtil;
import org.dataconservancy.pass.authz.ShibAuthUserProvider;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.model.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class gets an {@link AuthUser} object from the {@link ShibAuthUserProvider} and creates {@link User} to be
 * stored in the back end storage for PASS.
 *
 * @author apb@jhu.edu
 * @author jrm@jhu.edu
 */
@SuppressWarnings("serial")
public class UserServlet extends HttpServlet {

    static final Logger LOG = LoggerFactory.getLogger(UserServlet.class);

    PassJsonAdapter json = new PassJsonAdapterBasic();

    PassClient fedoraClient = PassClientFactory.getPassClient();

    AuthUserProvider provider = new ShibAuthUserProvider(fedoraClient);

    static {
        LogUtil.adjustLogLevels();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        getServletContext().setAttribute("authUserProvider", provider);
    }

    /**
     * A method which calls {@link ShibAuthUserProvider#getUser(HttpServletRequest)} to get an {@link AuthUser} in
     * order to populate a {@link User} object and create/update and store it
     *
     * @param request - the {@code HttpServletRequest}
     * @param response - the {@code HttpServletResponse}
     * @throws IOException - if the
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        final AuthUser shibUser = provider.getUser(request, authUser -> {
            // Create if warranted
            if (authUser.getId() == null && authUser.isFaculty()) {
                final User user = new User();
                user.setUsername(authUser.getPrincipal());
                user.setLocalKey(authUser.getEmployeeId());
                user.setInstitutionalId(authUser.getInstitutionalId());
                user.setDisplayName(authUser.getName());
                user.setEmail(authUser.getEmail());
                user.getRoles().add(User.Role.SUBMITTER);;
                authUser.setId(fedoraClient.createResource(user));
                LOG.info("Created new User resource <{}> for {} ({})", authUser.getId(), user.getLocalKey(), user
                        .getInstitutionalId());
                return authUser;
            } else {
                return authUser;
            }
        });

        final URI id = shibUser.getId();

        final User user;

        // does the user already exist in the repository?
        if (id != null) {
            LOG.info("User {} found at {}", shibUser.getPrincipal(), id);

            user = fedoraClient.readResource(id, User.class);

            if (user == null) {
                LOG.warn("Resource {} does not exist, this should never happen", shibUser.getId());
                response.setStatus(500);
                return;
            }
            boolean update = false;

            // employeeId should never change
            // each user provider will only adjust fields for which it is authoritative
            // shib is authoritative for these
            if (user.getUsername() == null || !user.getUsername().equals(shibUser.getPrincipal())) {
                user.setUsername(shibUser.getPrincipal());
                update = true;
            }
            if (user.getEmail() == null || !user.getEmail().equals(shibUser.getEmail())) {
                user.setEmail(shibUser.getEmail());
                update = true;
            }
            if (user.getDisplayName() == null || !user.getDisplayName().equals(shibUser.getName())) {
                user.setDisplayName(shibUser.getName());
                update = true;
            }
            if (user.getInstitutionalId() == null || !user.getInstitutionalId().equals(shibUser
                    .getInstitutionalId())) {
                user.setInstitutionalId(shibUser.getInstitutionalId());
                update = true;
            }

            if (update) {
                LOG.info("User record for {} in repository is out of date, updating {} ", shibUser.getPrincipal(),
                        user.getId());
                fedoraClient.updateResource(user);
            }

        } else {// no id, so they're not allowed in
            user = null;
        }

        // at this point, any eligible person will have an up to date User object in Fedora
        // and the up to date User object and valid id here
        // if the person is not eligible, id and user will be null

        if (id != null && user != null) {

            rewriteUri(user, request);

            try (OutputStream out = response.getOutputStream()) {
                out.write(json.toJson(user, true));
                response.setStatus(200);
            }
        } else {
            LOG.info("{} not authorized", shibUser.getPrincipal());
            try (Writer out = response.getWriter()) {
                response.setStatus(401);
                out.append("Unauthorized");
            }
        }
    }

    private void rewriteUri(User user, HttpServletRequest request) {

        final Protocol proto = Protocol.of(request, user.getId());
        final Host host = Host.of(request, user.getId());

        final URI u = user.getId();

        try {
            user.setId(new URI(
                    proto.get(),
                    u.getUserInfo(),
                    host.getHost(),
                    host.getPort(),
                    u.getPath(),
                    u.getQuery(),
                    u.getFragment()));
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Error rewriting URI " + user.getId());
        }

    }

    private static class Host {

        final String host;

        final int port;

        static Host of(HttpServletRequest request, URI defaults) {
            final String host = request.getHeader("host");
            if (host != null && host != "") {
                return new Host(host);
            } else {
                if (request.getRequestURL() != null) {
                    return new Host(URI.create(request.getRequestURL().toString()).getHost());
                } else {
                    return new Host(defaults.getHost(), defaults.getPort());
                }
            }
        }

        private Host(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private Host(String hostname) {
            if (hostname.contains(":")) {
                final String[] parts = hostname.split(":");
                host = parts[0];
                port = Integer.valueOf(parts[1]);
            } else {
                host = hostname;
                port = -1;
            }
        }

        String getHost() {
            return host;
        }

        int getPort() {
            return port;
        }
    }

    private static class Protocol {

        final String proto;

        static Protocol of(HttpServletRequest request, URI defaults) {
            if (request.getHeader("X-Forwarded-Proto") != null) {
                return new Protocol(request.getHeader("X-Forwarded-Proto"));
            } else if (request.getRequestURL() != null) {
                return new Protocol(URI.create(request.getRequestURL().toString()).getScheme());
            } else {
                return new Protocol(defaults.getScheme());
            }
        }

        private Protocol(String proto) {
            this.proto = proto;
        }

        String get() {
            return proto;
        }
    }

}
