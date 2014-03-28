/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.web;

import java.io.StringWriter;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;

import com.thoughtworks.go.server.security.GoAuthority;
import com.thoughtworks.go.server.util.UserHelper;
import com.thoughtworks.go.util.SystemEnvironment;
import org.apache.velocity.Template;
import org.apache.velocity.context.Context;
import org.springframework.security.Authentication;
import org.springframework.security.GrantedAuthority;
import static org.springframework.security.context.HttpSessionContextIntegrationFilter.SPRING_SECURITY_CONTEXT_KEY;
import org.springframework.security.context.SecurityContext;
import org.springframework.web.servlet.view.velocity.VelocityToolboxView;

public class GoVelocityView extends VelocityToolboxView {
    public static final String PRINCIPAL = "principal";
    public static final String ADMINISTRATOR = "userHasAdministratorRights";
    public static final String TEMPLATE_ADMINISTRATOR = "userHasTemplateAdministratorRights";
    public static final String VIEW_ADMINISTRATOR_RIGHTS = "userHasViewAdministratorRights";
    public static final String GROUP_ADMINISTRATOR = "userHasGroupAdministratorRights";
    public static final String USE_COMPRESS_JS = "useCompressJS";


    protected void exposeHelpers(Context velocityContext, HttpServletRequest request) throws Exception {
        velocityContext.put(ADMINISTRATOR, true);
        velocityContext.put(GROUP_ADMINISTRATOR, true);
        velocityContext.put(TEMPLATE_ADMINISTRATOR, true);
        velocityContext.put(VIEW_ADMINISTRATOR_RIGHTS, true);
        velocityContext.put(USE_COMPRESS_JS, new SystemEnvironment().useCompressedJs());
        SecurityContext securityContext = (SecurityContext) request.getSession().getAttribute(
                SPRING_SECURITY_CONTEXT_KEY);
        if (securityContext == null || securityContext.getAuthentication() == null) {
            return;
        }
        final Authentication authentication = securityContext.getAuthentication();
        setPrincipal(velocityContext, authentication);
        setAdmininstratorRole(velocityContext, authentication);
    }

    private void setPrincipal(Context velocityContext, Authentication authentication) throws NamingException {
        velocityContext.put(PRINCIPAL, UserHelper.getUserName(authentication).getDisplayName());
    }

    private void setAdmininstratorRole(Context velocityContext, Authentication authentication) {
        final GrantedAuthority[] authorities = authentication.getAuthorities();
        if (authorities == null) {
            return;
        }
        removeAdminFromContextIfNecessary(velocityContext, authorities);
        removeGroupAdminFromContextIfNecessary(velocityContext, authorities);
        removeTemplateAdminFromContextIfNecessary(velocityContext, authorities);
        removeViewAdminRightsFromContextIfNecessary(velocityContext);

    }

    private void removeViewAdminRightsFromContextIfNecessary(Context context) {
        if(!(context.containsKey(ADMINISTRATOR) || context.containsKey(GROUP_ADMINISTRATOR) || context.containsKey(TEMPLATE_ADMINISTRATOR)))
            context.remove(VIEW_ADMINISTRATOR_RIGHTS);
    }

    private void removeGroupAdminFromContextIfNecessary(Context velocityContext, GrantedAuthority[] authorities) {
        boolean administrator = false;
        for (GrantedAuthority authority : authorities) {
            if (isGroupAdministrator(authority)) {
                administrator = true;
            }
        }
        if (!administrator) {
            velocityContext.remove(GROUP_ADMINISTRATOR);
        }
    }

    private void removeTemplateAdminFromContextIfNecessary(Context velocityContext, GrantedAuthority[] authorities) {
        boolean administrator = false;
        for (GrantedAuthority authority : authorities) {
            if (isTemplateAdministrator(authority)) {
                administrator = true;
            }
        }
        if (!administrator) {
            velocityContext.remove(TEMPLATE_ADMINISTRATOR);
        }
    }

    private void removeAdminFromContextIfNecessary(Context velocityContext, GrantedAuthority[] authorities) {
        boolean administrator = false;
        for (GrantedAuthority authority : authorities) {
            if (isAdministrator(authority)) {
                administrator = true;
            }
        }
        if (!administrator) {
            velocityContext.remove(ADMINISTRATOR);
        }
    }

    private boolean isAdministrator(GrantedAuthority authority) {
        return authority.getAuthority().equals(GoAuthority.ROLE_SUPERVISOR.toString());
    }

    private boolean isGroupAdministrator(GrantedAuthority authority) {
        return authority.getAuthority().equals(GoAuthority.ROLE_GROUP_SUPERVISOR.toString());
    }

    private boolean isTemplateAdministrator(GrantedAuthority authority) {
        return authority.getAuthority().equals(GoAuthority.ROLE_TEMPLATE_SUPERVISOR.toString());
    }

    public String getContentAsString() {
        try {
            Template template = getTemplate();
            StringWriter writer = new StringWriter();
            template.merge(null, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}