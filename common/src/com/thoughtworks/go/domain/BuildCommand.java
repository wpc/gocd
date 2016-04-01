/************************** GO-LICENSE-START*********************************
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.domain;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.thoughtworks.go.util.GoConstants;

import java.util.*;

import static com.thoughtworks.go.util.MapBuilder.map;

public class BuildCommand {

    public static final Gson GSON = new Gson();

    public static BuildCommand echoWithPrefix(String format, String...args) {
        String prefix = String.format("[%s] ", GoConstants.PRODUCT_NAME);
        return new BuildCommand("echo", prefix + String.format(format, args));
    }

    public static BuildCommand exec(String command, String...args) {
        String[] combinedArgs = new String[args.length + 1];
        combinedArgs[0] = command;
        System.arraycopy(args, 0, combinedArgs, 1, args.length);
        return new BuildCommand("exec", combinedArgs);
    }

    public static BuildCommand test(String flag, String left) {
        return new BuildCommand("test", map("flag", flag, "left", left));
    }

    public static BuildCommand test(String flag, String left, BuildCommand subCommand) {
        return new BuildCommand("test", map("flag", flag, "left", left))
                .setSubCommands(Collections.singletonList(subCommand));
    }

    public static BuildCommand reportCurrentStatus(JobState status) {
        return new BuildCommand("reportCurrentStatus", map("status", status.name()));
    }

    public static BuildCommand reportCompleting() {
        return new BuildCommand("reportCompleting");
    }

    public static BuildCommand compose(BuildCommand...subCommands) {
        return new BuildCommand("compose").setSubCommands(Arrays.asList(subCommands));
    }

    public static BuildCommand compose(List<BuildCommand> subCommands) {
        return new BuildCommand("compose").setSubCommands(subCommands);
    }

    public static BuildCommand echo(String...args) {
        return new BuildCommand("echo", args);
    }

    public static BuildCommand mkdirs(String path) {
        return new BuildCommand("mkdirs", map("path", path));
    }

    public static BuildCommand cleandir(String path, String...allowedPaths) {
        return new BuildCommand("cleandir", map("path", path, "allowed", GSON.toJson(allowedPaths)));
    }

    public static BuildCommand noop() {
        return new BuildCommand("compose");
    }

    public static BuildCommand fail(String format, String... args) {
        return new BuildCommand("fail", String.format(format, args));
    }

    // set environment variable with displaying it
    public static BuildCommand export(String name, String value, boolean isSecure) {
        return new BuildCommand("export", map("name", name, "value", value, "secure", String.valueOf(isSecure)));
    }

    // display environment variable
    public static BuildCommand export(String name) {
        return new BuildCommand("export", map("name", name));
    }

    public static BuildCommand secret(String secretValue) {
        return new BuildCommand("secret", map("value", secretValue));
    }

    public static BuildCommand secret(String secretValue, String substitution) {
        return new BuildCommand("secret", map("value", secretValue, "substitution", substitution));
    }


    public static BuildCommand uploadArtifact(String src, String dest) {
        return new BuildCommand("uploadArtifact", map("src", src, "dest", dest));
    }

    public static BuildCommand generateTestReport(List<String> srcs) {
        return new BuildCommand("generateTestReport", srcs.toArray(new String[srcs.size()]));
    }

    public static BuildCommand downloadFile(Map<String, String> args) {
        return new BuildCommand("downloadFile", args);
    }

    public static BuildCommand downloadDir(Map<String, String> args) {
        return new BuildCommand("downloadDir", args);
    }

    public static class Test {
        @Expose
        public final BuildCommand command;
        @Expose
        public final Boolean expectation;

        public Test(BuildCommand command, Boolean expectation) {
            this.command = command;
            this.expectation = expectation;
        }

        @Override
        public String toString() {
            return "Test{" +
                    "command=" + command +
                    ", expectation=" + expectation +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Test test = (Test) o;

            if (command != null ? !command.equals(test.command) : test.command != null) return false;
            return expectation != null ? expectation.equals(test.expectation) : test.expectation == null;

        }

        @Override
        public int hashCode() {
            int result = command != null ? command.hashCode() : 0;
            result = 31 * result + (expectation != null ? expectation.hashCode() : 0);
            return result;
        }
    }


    @Expose
    private final String name;
    @Expose
    private Map<String, String> args;
    @Expose
    private List<BuildCommand> subCommands;
    @Expose
    private String workingDirectory;
    @Expose
    private Test test;
    @Expose
    private String runIfConfig = "passed";
    @Expose
    private BuildCommand onCancel;

    public BuildCommand(String name) {
        this.name = name;
        this.subCommands = Collections.emptyList();
        this.args = Collections.emptyMap();
    }

    public BuildCommand(String name, String... listArgs) {
        this(name);
        this.args = toMapArgs(listArgs);
    }

    public BuildCommand(String name, Map<String, String> args) {
        this(name);
        this.args = args;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public String[] getListArgs() {
        String[] ret = new String[args.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = args.get(String.valueOf(i));
        }
        return ret;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        doDump(0, 4, sb);
        return sb.toString();
    }

    private void doDump(int level, int indent, StringBuilder sb) {
        for (int i = 0; i < level * indent; i++) {
            sb.append(' ');
        }

        sb.append(name);

        for (String argName : args.keySet()) {
            sb.append(' ').append('\"').append(argName).append(":").append(args.get(argName)).append('\"');
        }

        if (!"passed".equals(runIfConfig)) {
            sb.append(" ").append("(runIf:").append(runIfConfig).append(")");
        }

        for (BuildCommand subCommand : subCommands) {
            sb.append("\n");
            subCommand.doDump(level + 1, indent, sb);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuildCommand that = (BuildCommand) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (args != null ? !args.equals(that.args) : that.args != null) return false;
        if (subCommands != null ? !subCommands.equals(that.subCommands) : that.subCommands != null) return false;
        if (workingDirectory != null ? !workingDirectory.equals(that.workingDirectory) : that.workingDirectory != null)
            return false;
        if (test != null ? !test.equals(that.test) : that.test != null) return false;
        if (runIfConfig != null ? !runIfConfig.equals(that.runIfConfig) : that.runIfConfig != null) return false;
        return onCancel != null ? onCancel.equals(that.onCancel) : that.onCancel == null;

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (args != null ? args.hashCode() : 0);
        result = 31 * result + (subCommands != null ? subCommands.hashCode() : 0);
        result = 31 * result + (workingDirectory != null ? workingDirectory.hashCode() : 0);
        result = 31 * result + (test != null ? test.hashCode() : 0);
        result = 31 * result + (runIfConfig != null ? runIfConfig.hashCode() : 0);
        result = 31 * result + (onCancel != null ? onCancel.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BuildCommand{" +
                "name='" + name + '\'' +
                ", args=" + args +
                ", subCommands=" + subCommands +
                ", workingDirectory='" + workingDirectory + '\'' +
                ", test=" + test +
                ", runIfConfig='" + runIfConfig + '\'' +
                ", onCancel=" + onCancel +
                '}';
    }

    public BuildCommand setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public BuildCommand setWorkingDirectoryRecursively(String workingDirectory) {
        this.setWorkingDirectory(workingDirectory);
        for (BuildCommand subCommand : subCommands) {
            subCommand.setWorkingDirectoryRecursively(workingDirectory);
        }
        return this;
    }

    public String getWorkingDirectory() {
        return workingDirectory == null ? "" : workingDirectory;
    }

    public Test getTest() {
        return test;
    }

    public BuildCommand setTest(BuildCommand command, boolean expectation) {
        this.test = new Test(command, expectation);
        return this;
    }

    public List<BuildCommand> getSubCommands() {
        return subCommands;
    }

    public BuildCommand setSubCommands(List<BuildCommand> subCommands) {
        this.subCommands = subCommands;
        return this;
    }

    public String getRunIfConfig() {
        return runIfConfig;
    }

    public void setRunIfConfig(String runIfConfig) {
        this.runIfConfig = runIfConfig;
    }

    public BuildCommand getOnCancel() {
        return onCancel;
    }

    public BuildCommand setOnCancel(BuildCommand onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    public BuildCommand runIf(String runIfConfig) {
        setRunIfConfig(runIfConfig);
        return this;
    }

    public BuildCommand setRunIfRecurisvely(String runIfConfig) {
        runIf(runIfConfig);
        for (BuildCommand subCommand : subCommands) {
            subCommand.setRunIfRecurisvely(runIfConfig);
        }
        return this;
    }

    private Map<String, String> toMapArgs(String[] listArgs) {
        HashMap<String, String> ret = new HashMap<>(listArgs.length);
        for (int i = 0; i < listArgs.length; i++) {
            ret.put(String.valueOf(i), listArgs[i]);
        }
        return ret;
    }
}
