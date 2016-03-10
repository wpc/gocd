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
package com.thoughtworks.go.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.go.remote.work.Work;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;

public class MessageEncoding {

    private static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    public static String encodeWork(Work work) {
        try {
            ByteArrayOutputStream binaryOutput = new ByteArrayOutputStream();
            try (ObjectOutputStream objectStream = new ObjectOutputStream(binaryOutput)) {
                objectStream.writeObject(work);
            }

            return Base64.encodeBase64String(binaryOutput.toByteArray());
        } catch (IOException e) {
            throw bomb(e);
        }
    }

    public static Work decodeWork(String data) {
        try {
            byte[] binary = Base64.decodeBase64(data);
            try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(binary))) {
                return (Work) objectStream.readObject();
            }
        } catch (ClassNotFoundException | IOException e) {
            throw bomb(e);
        }
    }


    public static byte[] encodeMessage(Message msg) {
        String encode = gson.toJson(msg);
        org.apache.commons.io.output.ByteArrayOutputStream bytes = new org.apache.commons.io.output.ByteArrayOutputStream();
        try {
            GZIPOutputStream out = new GZIPOutputStream(bytes);
            out.write(encode.getBytes(StandardCharsets.UTF_8));
            out.finish();
        } catch (IOException e) {
            throw bomb(e);
        }
        return bytes.toByteArray();
    }

    public static Message decodeMessage(InputStream input) {
        try {
            GZIPInputStream zipStream = new GZIPInputStream(input);
            String jsonStr = new String(IOUtils.toByteArray(zipStream), StandardCharsets.UTF_8);
            return gson.fromJson(jsonStr, Message.class);
        } catch (IOException e) {
            throw bomb(e);
        }
    }

    public static String encodeData(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T decodeData(String data, Class<T> aClass) {
        return gson.fromJson(data, aClass);
    }
}
