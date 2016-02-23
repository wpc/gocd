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

package com.thoughtworks.go.security;

import com.google.gson.Gson;
import com.thoughtworks.xstream.core.util.Base64Encoder;
import sun.misc.BASE64Decoder;
import sun.security.x509.X509CertImpl;

import java.io.IOException;
import java.io.Serializable;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;

public class Registration implements Serializable {
    private static Gson gson = new Gson();

    public static Registration fromJson(String json) {
        Map map = gson.fromJson(json, Map.class);
        BASE64Decoder decoder = new BASE64Decoder();
        List<Certificate> chain = new ArrayList<>();
        try {
            Map<String, String> key = (Map<String, String>) map.get("privateKey");
            KeyFactory kf = KeyFactory.getInstance(key.get("algorithm"));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoder.decodeBuffer(key.get("data")));
            PrivateKey privateKey = kf.generatePrivate(spec);
            List stringChain = (List) map.get("chain");
            for (Object obj : stringChain) {
                Map<String, String> cert = (Map<String, String>) obj;
                Certificate c = new X509CertImpl(decoder.decodeBuffer(cert.get("data")));
                chain.add(c);
            }
            return new Registration(privateKey, chain.toArray(new Certificate[chain.size()]));
        } catch (IOException | NoSuchAlgorithmException | CertificateException | InvalidKeySpecException e) {
            throw bomb(e);
        }
    }

    private final PrivateKey privateKey;
    private final Certificate[] chain;

    public static Registration createNullPrivateKeyEntry() {
        return new Registration(emptyPrivateKey());
    }

    public Registration(PrivateKey privateKey, Certificate... chain) {
        this.privateKey = privateKey;
        this.chain = chain;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return getFirstCertificate().getPublicKey();
    }

    public Certificate[] getChain() {
        return chain;
    }

    public X509Certificate getFirstCertificate() {
        return (X509Certificate) chain[0];
    }

    public Date getCertificateNotBeforeDate() {
        return getFirstCertificate().getNotBefore();
    }

    private static PrivateKey emptyPrivateKey() {
        return new PrivateKey() {
            public String getAlgorithm() {
                return null;
            }

            public String getFormat() {
                return null;
            }

            public byte[] getEncoded() {
                return new byte[0];
            }
        };
    }

    public KeyStore.PrivateKeyEntry asKeyStoreEntry() {
        return new KeyStore.PrivateKeyEntry(privateKey, chain);
    }

    public String toJson() {
        Map<String, Object> ret = new HashMap<>();
        ret.put("privateKey", serialize(privateKey));
        List<Map<String, String>> cs = new ArrayList<>();
        for (Certificate c : chain) {
            cs.add(serialize(c));
        }
        ret.put("chain", cs.toArray());
        return gson.toJson(ret);
    }

    private Map<String, String> serialize(Certificate certificate) {
        Base64Encoder encoder = new Base64Encoder();
        Map<String, String> ret = new HashMap<>();
        try {
            ret.put("data", encoder.encode(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
            bomb(e);
        }
        ret.put("type", "X509");
        ret.put("format", "ASN.1 DER");
        return ret;
    }

    private Map<String, String> serialize(PrivateKey privateKey) {
        Base64Encoder encoder = new Base64Encoder();
        Map<String, String> ret = new HashMap<>();
        ret.put("data", encoder.encode(privateKey.getEncoded()));
        ret.put("format", privateKey.getFormat());
        ret.put("algorithm", privateKey.getAlgorithm());
        return ret;
    }

}
