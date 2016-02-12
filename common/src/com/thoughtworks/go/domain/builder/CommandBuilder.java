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

package com.thoughtworks.go.domain.builder;

import java.io.File;

import com.thoughtworks.go.agent.BuildCommand;
import com.thoughtworks.go.domain.RunIfConfigs;
import com.thoughtworks.go.plugin.access.pluggabletask.TaskExtension;
import com.thoughtworks.go.util.SystemUtil;
import com.thoughtworks.go.util.command.CommandLine;
import org.apache.commons.lang.StringUtils;

public class CommandBuilder extends BaseCommandBuilder {
    protected String args;

    public CommandBuilder(String command, String args, File workingDir, RunIfConfigs conditions, Builder cancelBuilder,
                          String description,
                          String errorString) {
        this(command, args, workingDir, conditions, cancelBuilder, description);
        this.errorString = errorString;
    }

    public CommandBuilder(String command, String args, File workingDir, RunIfConfigs conditions, Builder cancelBuilder,
                          String description) {
        super(conditions, cancelBuilder, description, command, workingDir);
        this.args = args;
    }

    protected CommandLine buildCommandLine() {
        CommandLine command = null;
        if (SystemUtil.isWindows()) {
            command = CommandLine.createCommandLine("cmd").withWorkingDir(workingDir);
            command.withArg("/c");
            command.withArg(translateToWindowsPath(this.command));
        }
        else {
            command = CommandLine.createCommandLine(this.command).withWorkingDir(workingDir);
        }
        String[] argsArray = CommandLine.translateCommandLine(args);
        for (int i = 0; i < argsArray.length; i++) {
            String arg = argsArray[i];
            command.withArg(arg);
        }
        return command;
    }

    private String translateToWindowsPath(String command) {
        return StringUtils.replace(command, "/", "\\");
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public String getArgs() {
        return args;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public BuildCommand buildCommand(TaskExtension taskExtension) {
        String[] argsArray = CommandLine.translateCommandLine(args);
        String[] cmdArgs = new String[argsArray.length + 1];
        cmdArgs[0] = this.command;
        System.arraycopy(argsArray, 0, cmdArgs, 1, argsArray.length);
        BuildCommand exec = new BuildCommand("exec", cmdArgs);
        exec.setRunIfConfig(super.conditions.aggregate().toString());
        if (workingDir != null) {
            exec.setWorkingDirectory(workingDir.getPath());
        }
        return exec;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        CommandBuilder that = (CommandBuilder) o;

        if (args != null ? !args.equals(that.args) : that.args != null) {
            return false;
        }
        if (command != null ? !command.equals(that.command) : that.command != null) {
            return false;
        }
        if (errorString != null ? !errorString.equals(that.errorString) : that.errorString != null) {
            return false;
        }
        if (workingDir != null ? !workingDir.equals(that.workingDir) : that.workingDir != null) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (command != null ? command.hashCode() : 0);
        result = 31 * result + (args != null ? args.hashCode() : 0);
        result = 31 * result + (workingDir != null ? workingDir.hashCode() : 0);
        result = 31 * result + (errorString != null ? errorString.hashCode() : 0);
        return result;
    }

    @Override public String toString() {
        return "CommandBuilder{" +
                "args='" + args + '\'' +
                "} " + super.toString();
    }
}
