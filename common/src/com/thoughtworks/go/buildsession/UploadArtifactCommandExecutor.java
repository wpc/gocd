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
import com.thoughtworks.go.domain.WildcardScanner;
import com.thoughtworks.go.util.GoConstants;

import java.io.File;

import static java.lang.String.format;

public class UploadArtifactCommandExecutor implements BuildCommandExecutor {
    @Override
    public boolean execute(BuildCommand command, BuildSession buildSession) {
        final String src = command.getArgs().get("src");
        final String dest = command.getArgs().get("dest");
        final File rootPath = buildSession.resolveRelativeDir(command.getWorkingDirectory());

        WildcardScanner scanner = new WildcardScanner(rootPath, src);
        File[] files = scanner.getFiles();
        if (files.length == 0) {
            String message = "The rule [" + src + "] cannot match any resource under [" + rootPath + "]";
            buildSession.printlnWithPrefix(message);
            return false;
        }
        for (File file : files) {
            buildSession.upload(file, dest);
        }
        return true;
    }
}
