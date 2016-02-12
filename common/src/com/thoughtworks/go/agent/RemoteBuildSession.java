package com.thoughtworks.go.agent;

import com.thoughtworks.go.domain.JobState;
import com.thoughtworks.go.remote.work.Callback;

import java.io.File;
import java.util.Map;

public interface RemoteBuildSession {
    void start(String buildLocator, String buildLocatorForDisplay, Long buildId, String consoleURI, Callback<CommandResult> callback);
    void export(Map<String, String> envs);
    void export();
    void flush(Callback<CommandResult> callback);
    void echo(String s);
    void end();
    void addCommand(BuildCommand command);

    void report(JobState state);
}
