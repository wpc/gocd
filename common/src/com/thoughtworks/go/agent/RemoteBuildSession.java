package com.thoughtworks.go.agent;

import com.thoughtworks.go.remote.work.Callback;

public interface RemoteBuildSession {
    void start(String buildLocator, String buildLocatorForDisplay, Long buildId, String consoleURI, String uploadBase, String propertiesBaseUrl, Callback<CommandResult> callback);
    void addCommand(BuildCommand command);
    void end();
}
