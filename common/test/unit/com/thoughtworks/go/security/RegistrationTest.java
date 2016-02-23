/*
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.security;

import com.google.gson.Gson;
import com.thoughtworks.go.util.TestFileUtil;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class RegistrationTest {
    private static String authorityKeystorePath = "tempAuthorityKeystore";

    @Test
    public void testJsonFormat() {
        String json = createRegistration().toJson();
        Map map = new Gson().fromJson(json, Map.class);
        assertThat(map.get("privateKey"), is(instanceOf(Map.class)));
        Map<String, String> key = (Map<String, String>) map.get("privateKey");
        assertThat(key.get("algorithm"), is("RSA"));
        assertThat(key.get("format"), is("PKCS#8"));
    }

    @Test
    public void decodeFromJson() {
        String json = createRegistration().toJson();
        Registration reg = Registration.fromJson(json);
        assertNotNull(reg.getPrivateKey());
        assertThat(reg.getChain().length, is(3));
    }

    public static Registration createRegistration() {
        File tempKeystoreFile = TestFileUtil.createUniqueTempFile(authorityKeystorePath);
        X509CertificateGenerator certificateGenerator = new X509CertificateGenerator();
        certificateGenerator.createAndStoreCACertificates(tempKeystoreFile);
        return certificateGenerator.createAgentCertificate(tempKeystoreFile, "blah");
    }

}
