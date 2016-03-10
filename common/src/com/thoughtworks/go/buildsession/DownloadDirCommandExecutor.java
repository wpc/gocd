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
import com.thoughtworks.go.domain.ChecksumFileHandler;
import com.thoughtworks.go.domain.DirHandler;
import com.thoughtworks.go.util.TempFiles;
import com.thoughtworks.go.util.URLService;

import java.io.File;

public class DownloadDirCommandExecutor implements BuildCommandExecutor {
    @Override
    public boolean execute(BuildCommand command, BuildSession buildSession) {
        URLService urlService = new URLService();
        String url = urlService.prefixPartialUrl(command.getArgs().get("url"));
        String dest = command.getArgs().get("dest");
        String src = command.getArgs().get("src");
        String checksumUrl = null;
        ChecksumFileHandler checksumFileHandler = null;

        if (command.getArgs().containsKey("checksumUrl")) {
            checksumUrl = new URLService().prefixPartialUrl(command.getArgs().get("checksumUrl"));
            File checksumFile;
            if (command.getArgs().containsKey("checksumFile")) {
                checksumFile = new File(command.getArgs().get("checksumFile"));
            } else {
                checksumFile = TempFiles.createUniqueFile("checksum");
            }
            checksumFileHandler = new ChecksumFileHandler(checksumFile);
        }

        DirHandler handler = new DirHandler(src, new File(dest));
        buildSession.download(handler, url, checksumFileHandler, checksumUrl);
        return true;
    }
}
