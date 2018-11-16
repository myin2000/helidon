/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.provider.httpsign;

import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.security.SecurityEnvironment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HttpSignature}.
 */
public class HttpSignatureTest {
    @Test
    public void testValid() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\"";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",hurhur=\"ignored\"";

        testValid(validSignature);
    }

    @Test
    public void testValidRepeatedComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\"";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent1() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent2() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent3() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=\"asf";

        testValid(validSignature);
    }

    @Test
    public void testInvalid1() {
        String invalidSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                // missing quotes for headers
                + "headers=(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=\"asf";

        HttpSignature httpSignature = HttpSignature.fromHeader(invalidSignature);
        Optional<String> validate = httpSignature.validate();

        OptionalHelper.from(validate).ifPresentOrElse(msg -> assertThat(msg, containsString("signature is a mandatory")),
                                                      () -> fail("Should have failed validation"));
    }

    @Test
    public void testInvalid2() {
        String invalidSignature = "This is a wrong signature";

        HttpSignature httpSignature = HttpSignature.fromHeader(invalidSignature);
        Optional<String> validate = httpSignature.validate();

        OptionalHelper.from(validate).ifPresentOrElse(msg -> assertThat(msg, containsString("keyId is a mandatory")),
                                                      () -> fail("Should have failed validation"));
    }

    @Test
    public void testSignRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", CollectionsHelper.listOf("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", CollectionsHelper.listOf("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", CollectionsHelper.listOf("example.org"));

        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);
        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("rsa-key-12345")
                .privateKeyConfig(KeyConfig.keystoreBuilder()
                                          .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                                          .keystorePassphrase("password".toCharArray())
                                          .keyAlias("myPrivateKey")
                                          .build())
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(CollectionsHelper.listOf("date",
                                                                                               "host",
                                                                                               "(request-target)",
                                                                                               "authorization")))
                                       .build())
                .build();

        HttpSignature signature = HttpSignature.sign(env, outboundDef, new HashMap<>());
        assertThat(signature.getBase64Signature(),
                   is("Rm5PjuUdJ927esGQ2gm/6QBEM9IM7J5qSZuP8NV8+GXUfboUV6ST2EYLYniFGt5/3BO/2+vqQdqezdTVPr/JCwqBx"
                              + "+9T9ZynG7YqRjKvXzcmvQOu5vQmCK5x/HR0fXU41Pjq+jywsD0k6KdxF6TWr6tvWRbwFet+YSb0088o"
                              + "/65Xeqghw7s0vShf7jPZsaaIHnvM9SjWgix9VvpdEn4NDvqhebieVD3Swb1VG5+/7ECQ9VAlX30U5"
                              + "/jQ5hPO3yuvRlg5kkMjJiN7tf/68If/5O2Z4H+7VmW0b1U69/JoOQJA0av1gCX7HVfa"
                              + "/YTCxIK4UFiI6h963q2x7LSkqhdWGA=="));
    }

    @Test
    public void testSignHmac() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", CollectionsHelper.listOf("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", CollectionsHelper.listOf("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", CollectionsHelper.listOf("example.org"));
        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(CollectionsHelper.listOf("date",
                                                                                               "host",
                                                                                               "(request-target)",
                                                                                               "authorization")))
                                       .build())
                .build();

        HttpSignature signature = HttpSignature.sign(env, outboundDef, new HashMap<>());

        assertThat(signature.getBase64Signature(), is("0BcQq9TckrtGvlpHiMxNqMq0vW6dPVTGVDUVDrGwZyI="));
    }

    @Test
    public void testSignHmacAddHeaders() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost/test/path"))
                .build();

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(CollectionsHelper.listOf("date",
                                                                                               "host")))
                                       .build())
                .build();

        // just make sure this does not throw an exception for missing headers
        HttpSignature.sign(env, outboundDef, new HashMap<>());
    }

    private SecurityEnvironment buildSecurityEnv(String path, Map<String, List<String>> headers) {
        return SecurityEnvironment.builder()
                .path(path)
                .headers(headers)
                .build();
    }

    @Test
    public void testVerifyRsa() {
        HttpSignature signature = HttpSignature.fromHeader("keyId=\"rsa-key-12345\",algorithm=\"rsa-sha256\",headers=\"date "
                                                                   + "host (request-target) authorization\","
                                                                   + "signature=\"Rm5PjuUdJ927esGQ2gm/6QBEM9IM7J5qSZuP8NV8+GXUf"
                                                                   + "boUV6ST2EYLYniFGt5/3BO/2+vqQdqezdTVPr/JCwqBx+9T9ZynG7YqRj"
                                                                   + "KvXzcmvQOu5vQmCK5x/HR0fXU41Pjq+jywsD0k6KdxF6TWr6tvWRbwFet"
                                                                   + "+YSb0088o/65Xeqghw7s0vShf7jPZsaaIHnvM9SjWgix9VvpdEn4NDvqh"
                                                                   + "ebieVD3Swb1VG5+/7ECQ9VAlX30U5/jQ5hPO3yuvRlg5kkMjJiN7tf/68"
                                                                   + "If/5O2Z4H+7VmW0b1U69/JoOQJA0av1gCX7HVfa/YTCxIK4UFiI6h963q"
                                                                   + "2x7LSkqhdWGA==\"");
        signature.validate().ifPresent(Assertions::fail);

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", CollectionsHelper.listOf("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", CollectionsHelper.listOf("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", CollectionsHelper.listOf("example.org"));

        InboundClientDefinition inboundClientDef = InboundClientDefinition.builder("rsa-key-12345")
                .principalName("theService")
                .publicKeyConfig(KeyConfig.keystoreBuilder()
                                         .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                                         .keystorePassphrase("password".toCharArray())
                                         .certAlias("service_cert")
                                         .build())
                .build();

        signature.validate(buildSecurityEnv("/my/resource", headers),
                           inboundClientDef,
                           CollectionsHelper.listOf("date"))
                .ifPresent(Assertions::fail);
    }

    @Test
    public void testVerifyHmac() {
        HttpSignature signature = HttpSignature.fromHeader(
                "keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\",headers=\"date host (request-target) authorization\","
                        + "signature=\"0BcQq9TckrtGvlpHiMxNqMq0vW6dPVTGVDUVDrGwZyI=\"");

        signature.validate().ifPresent(Assertions::fail);

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", CollectionsHelper.listOf("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", CollectionsHelper.listOf("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", CollectionsHelper.listOf("example.org"));
        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);

        InboundClientDefinition inboundClientDef = InboundClientDefinition.builder("myServiceKeyId")
                .principalName("theService")
                .hmacSecret("MyPasswordForHmac")
                .build();

        signature.validate(env,
                           inboundClientDef,
                           CollectionsHelper.listOf("date"))
                .ifPresent(Assertions::fail);
    }

    private void testValid(String validSignature) {
        HttpSignature httpSignature = HttpSignature.fromHeader(validSignature);

        assertThat(httpSignature.getAlgorithm(), is("rsa-sha256"));
        assertThat(httpSignature.getKeyId(), is("rsa-key-1"));
        assertThat(httpSignature.getBase64Signature(), is("Base64(RSA-SHA256(signing string))"));
        assertThat(httpSignature.getHeaders(),
                   equalTo(CollectionsHelper.listOf("(request-target)", "host", "date", "digest", "content-length")));
    }

}
