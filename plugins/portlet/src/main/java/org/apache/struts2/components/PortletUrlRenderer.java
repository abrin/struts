/*
 * $Id: PortletUrlRenderer.java 612406 2008-01-16 10:05:06Z nilsga $
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.components;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.StrutsException;
import org.apache.struts2.dispatcher.mapper.ActionMapper;
import org.apache.struts2.portlet.context.PortletActionContext;
import org.apache.struts2.portlet.util.PortletUrlHelper;
import org.apache.struts2.portlet.util.PortletUrlHelperJSR286;

import java.io.IOException;
import java.io.Writer;

/**
 * Implementation of the {@link UrlRenderer} interface that renders URLs for portlet environments.
 *
 * @see UrlRenderer
 */
public class PortletUrlRenderer implements UrlRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(PortletUrlRenderer.class);

    /**
     * The servlet renderer used when not executing in a portlet context.
     */
    private UrlRenderer servletRenderer = null;
    private PortletUrlHelper portletUrlHelper = null;

    public PortletUrlRenderer() {
        this.servletRenderer = new ServletUrlRenderer();

        if (PortletActionContext.isJSR268Supported()) {
            this.portletUrlHelper = new PortletUrlHelperJSR286();
        } else {
            this.portletUrlHelper = new PortletUrlHelper();
        }
    }

    @Inject
    public void setActionMapper(ActionMapper actionMapper) {
        servletRenderer.setActionMapper(actionMapper);
    }

    /**
     * {@inheritDoc}
     */
    public void renderUrl(Writer writer, UrlProvider urlComponent) {
        if (PortletActionContext.getPortletContext() == null || "none".equalsIgnoreCase(urlComponent.getPortletUrlType())) {
            servletRenderer.renderUrl(writer, urlComponent);
            return;
        }

        String result;
        urlComponent.setNamespace(urlComponent.determineNamespace(urlComponent.getNamespace(), urlComponent.getStack(), urlComponent.getHttpServletRequest()));
        if (onlyActionSpecified(urlComponent)) {
            result = portletUrlHelper.buildUrl(urlComponent.getAction(), urlComponent.getNamespace(), urlComponent.getMethod(),
                    urlComponent.getParameters(), urlComponent.getPortletUrlType(), urlComponent.getPortletMode(), urlComponent.getWindowState());
        } else if (onlyValueSpecified(urlComponent)) {
            result = portletUrlHelper.buildResourceUrl(urlComponent.getValue(), urlComponent.getParameters());
        } else {
            result = createDefaultUrl(urlComponent);
        }
        final String anchor = urlComponent.getAnchor();
        if (anchor != null && anchor.length() > 0) {
            result += '#' + anchor;
        }

        String var = urlComponent.getVar();

        if (var != null) {
            urlComponent.putInContext(result);

            // add to the request and page scopes as well
            urlComponent.getHttpServletRequest().setAttribute(var, result);
        } else {
            try {
                writer.write(result);
            } catch (IOException e) {
                throw new StrutsException("IOError: " + e.getMessage(), e);
            }
        }
    }

    private String createDefaultUrl(UrlProvider urlComponent) {
        String result;
        ActionInvocation ai = (ActionInvocation) urlComponent.getStack().getContext().get(
                ActionContext.ACTION_INVOCATION);
        String action = ai.getProxy().getActionName();
        result = portletUrlHelper.buildUrl(action, urlComponent.getNamespace(), urlComponent.getMethod(), urlComponent.getParameters(),
                urlComponent.getPortletUrlType(), urlComponent.getPortletMode(), urlComponent.getWindowState());
        return result;
    }

    private boolean onlyValueSpecified(UrlProvider urlComponent) {
        return urlComponent.getValue() != null && urlComponent.getAction() == null;
    }

    private boolean onlyActionSpecified(UrlProvider urlComponent) {
        return urlComponent.getValue() == null && urlComponent.getAction() != null;
    }

    /**
     * {@inheritDoc}
     */
    public void renderFormUrl(Form formComponent) {
        if (PortletActionContext.getPortletContext() == null) {
            servletRenderer.renderFormUrl(formComponent);
            return;
        }
        String namespace = formComponent.determineNamespace(formComponent.namespace, formComponent.getStack(),
                formComponent.request);
        String action = null;
        if (formComponent.action != null) {
            action = formComponent.findString(formComponent.action);
        } else {
            ActionInvocation ai = (ActionInvocation) formComponent.getStack().getContext().get(ActionContext.ACTION_INVOCATION);
            action = ai.getProxy().getActionName();
        }

        String type = "action";
        if (StringUtils.isNotEmpty(formComponent.method)) {
            if ("GET".equalsIgnoreCase(formComponent.method.trim())) {
                type = "render";
            }
        }
        if (action != null) {
            String result = portletUrlHelper.buildUrl(action, namespace, null,
                    formComponent.getParameters(), type, formComponent.portletMode, formComponent.windowState);
            formComponent.addParameter("action", result);


            // name/id: cut out anything between / and . should be the id and
            // name
            String id = formComponent.getId();
            if (id == null) {
                int slash = action.lastIndexOf('/');
                int dot = action.indexOf('.', slash);
                if (dot != -1) {
                    id = action.substring(slash + 1, dot);
                } else {
                    id = action.substring(slash + 1);
                }
                formComponent.addParameter("id", formComponent.escape(id));
            }
        }
    }

    public void beforeRenderUrl(UrlProvider urlComponent) {
        if (PortletActionContext.getPortletContext() == null) {
            servletRenderer.beforeRenderUrl(urlComponent);
        }
    }

}
