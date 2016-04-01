/*************************** GO-LICENSE-START*********************************
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
package com.thoughtworks.go.buildsession;

import com.thoughtworks.go.domain.BuildCommand;

import java.io.File;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;
import static java.lang.String.format;

public class TestCommandExecutor implements BuildCommandExecutor {
    @Override
    public boolean execute(BuildCommand command, BuildSession buildSession) {

        String flag = command.getArgs().get("flag");
        String left = command.getArgs().get("left");
        if ("-d".equals(flag)) {
            File target = buildSession.resolveRelativeDir(command.getWorkingDirectory(), left);
            return target.isDirectory();
        }

        if ("-f".equals(flag)) {
            File target = buildSession.resolveRelativeDir(command.getWorkingDirectory(), left);
            return target.isFile();
        }

        if ("-eq".equals(flag)) {
            ConsoleCapture consoleCapture = new ConsoleCapture();
            BuildCommand targetCommand = command.getSubCommands().get(0);
            buildSession.newTestingSession(consoleCapture).processCommand(targetCommand);
            return left.equals(consoleCapture.captured());
        }

        throw bomb(format("Unknown flag %s for command: %s", flag, command));
    }
}
