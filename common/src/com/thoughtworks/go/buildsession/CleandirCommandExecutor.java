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

import com.google.gson.Gson;
import com.thoughtworks.go.domain.BuildCommand;
import com.thoughtworks.go.domain.materials.DirectoryCleaner;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CleandirCommandExecutor implements BuildCommandExecutor {
    @Override
    public boolean execute(BuildCommand command, BuildSession buildSession) {
        String path = command.getArgs().get("path");
        List<String> allowed = Collections.emptyList();
        if (command.getArgs().containsKey("allowed")) {
            allowed = new Gson().<List<String>>fromJson(command.getArgs().get("allowed"), List.class);
        }

        if (!allowed.isEmpty()) {
            DirectoryCleaner cleaner = new DirectoryCleaner(new File(path), buildSession.processOutputStreamConsumer());
            cleaner.allowed(allowed);
            cleaner.clean();
        } else {
            try {
                FileUtils.cleanDirectory(new File(path));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }
}
