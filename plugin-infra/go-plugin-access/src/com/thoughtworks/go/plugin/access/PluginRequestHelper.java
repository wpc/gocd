package com.thoughtworks.go.plugin.access;

import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.infra.PluginManager;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class PluginRequestHelper {
    protected PluginManager pluginManager;
    private List<String> goSupportedVersions;
    private String extensionName;

    public PluginRequestHelper(PluginManager pluginManager, List<String> goSupportedVersions, String extensionName) {
        this.pluginManager = pluginManager;
        this.goSupportedVersions = goSupportedVersions;
        this.extensionName = extensionName;
    }

    public <T> T submitRequest(String pluginId, String requestName, PluginInteractionCallback<T> pluginInteractionCallback) {
        if (!pluginManager.isPluginOfType(extensionName, pluginId)) {
            throw new RuntimeException(format("Did not find '%s' plugin with id '%s'. Looks like plugin is missing", extensionName, pluginId));
        }
        try {
            String resolvedExtensionVersion = pluginManager.resolveExtensionVersion(pluginId, goSupportedVersions);
            String responseBody = submitRequest(pluginId, requestName, resolvedExtensionVersion, pluginInteractionCallback.requestBody(resolvedExtensionVersion), pluginInteractionCallback.requestParams(resolvedExtensionVersion));
            return pluginInteractionCallback.onSuccess(responseBody, resolvedExtensionVersion);
        } catch (Exception e) {
            throw new RuntimeException(format("Interaction with plugin with id '%s' implementing '%s' extension failed while requesting for '%s'. Reason: [%s]", pluginId, extensionName, requestName, e.getMessage()), e);
        }
    }

    public String submitRequest(String pluginId, String requestName, String resolvedExtensionVersion, String requestBody, Map<String, String> requestParams) {
        if (!pluginManager.isPluginOfType(extensionName, pluginId)) {
            throw new RuntimeException(format("Did not find '%s' plugin with id '%s'. Looks like plugin is missing", extensionName, pluginId));
        }
        try {
            DefaultGoPluginApiRequest apiRequest = new DefaultGoPluginApiRequest(extensionName, resolvedExtensionVersion, requestName);
            apiRequest.setRequestBody(requestBody);
            apiRequest.setRequestParams(requestParams);
            GoPluginApiResponse response = pluginManager.submitTo(pluginId, apiRequest);
            if (DefaultGoApiResponse.SUCCESS_RESPONSE_CODE == response.responseCode()) {
                return response.responseBody();
            }
            throw new RuntimeException(format("The plugin sent a response that could not be understood by Go. Plugin returned with code '%s' and the following response: '%s'", response.responseCode(), response.responseBody()));
        } catch (Exception e) {
            throw new RuntimeException(format("Interaction with plugin with id '%s' implementing '%s' extension failed while requesting for '%s'. Reason: [%s]", pluginId, extensionName, requestName, e.getMessage()), e);
        }
    }
}
