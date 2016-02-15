package com.thoughtworks.go.agent;

import com.thoughtworks.go.remote.work.Callback;

import java.util.Map;

public interface RemoteBuildSession {
    void start(String buildLocator, String buildLocatorForDisplay, Long buildId, String consoleURI, String uploadBase, String propertiesBaseUrl, Callback<CommandResult> callback);

    void flush(Callback<CommandResult> callback);

    void end();

    void addCommand(BuildCommand command);
}
