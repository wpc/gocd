package com.thoughtworks.go.agent;

import com.thoughtworks.go.remote.work.Callback;

import java.io.File;
import java.util.Map;

public interface CommandSession {
    void start(String buildLocator, String buildLocatorForDisplay, String consoleURI, Callback<CommandResult> callback);
    void export(Map<String, String> envs);
    void export();
    void chdir(File workingDirectory);
    void flush(Callback<CommandResult> callback);
    void echo(String s);
    void end();
}
