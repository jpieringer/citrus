/*
 * Copyright 2006-2012 the original author or authors.
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

package com.consol.citrus.ssh;

import com.consol.citrus.endpoint.EndpointAdapter;
import com.consol.citrus.message.DefaultMessage;
import com.consol.citrus.message.Message;
import com.consol.citrus.ssh.client.SshEndpointConfiguration;
import com.consol.citrus.ssh.model.*;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.easymock.IArgumentMatcher;
import org.springframework.xml.transform.StringResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Roland Huss
 * @since 05.09.12
 */
public class SshCommandTest {

    private ByteArrayOutputStream stdout, stderr;
    private SshCommand cmd;
    private EndpointAdapter adapter;

    private static String COMMAND = "shutdown";
    private SshMarshaller marshaller;
    private ExitCallback exitCallback;

    @BeforeMethod
    public void setup() {
        adapter = createMock(EndpointAdapter.class);

        cmd = new SshCommand(COMMAND, adapter, new SshEndpointConfiguration());

        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        cmd.setErrorStream(stderr);
        cmd.setOutputStream(stdout);

        exitCallback = createMock(ExitCallback.class);
        cmd.setExitCallback(exitCallback);

        marshaller = new SshMarshaller();
    }
    
    @Test
    public void base() throws IOException {
        String input = "Hello world";
        String output = "Think positive!";
        String error = "Error, Error";
        int exitCode = 12;

        assertEquals(cmd.getCommand(),COMMAND);

        prepare(input, output, error, exitCode);
        cmd.run();

        assertEquals(stdout.toByteArray(),output.getBytes());
        assertEquals(stderr.toByteArray(),error.getBytes());
    }

    @Test
    public void start() throws IOException {
        Environment env = createMock(Environment.class);
        Map<String,String> map = new HashMap<String,String>();
        map.put(Environment.ENV_USER,"roland");
        expect(env.getEnv()).andReturn(map);
        replay(env);

        prepare("input","output",null,0);
        cmd.start(env);
        verify(env);
    }

    @Test
    public void ioException() throws IOException {
        InputStream i = createMock(InputStream.class);
        expect(i.read((byte[]) anyObject())).andThrow(new IOException("No"));
        i.close();

        exitCallback.onExit(1,"No");
        replay(i, exitCallback);
        cmd.setInputStream(i);

        cmd.run();
    }
    
    /**
     * Prepare actions.
     * @param pInput
     * @param pOutput
     * @param pError
     * @param pExitCode
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void prepare(String pInput, String pOutput, String pError, int pExitCode) {
        StringResult request = new StringResult();
        marshaller.marshal(new SshRequest(COMMAND, pInput), request);

        SshResponse resp = new SshResponse(pOutput, pError, pExitCode);
        StringResult response = new StringResult();
        marshaller.marshal(resp, response);

        Message respMsg = new DefaultMessage(response.toString());
        expect(adapter.handleMessage(eqMessage(request.toString()))).andReturn(respMsg);
        replay(adapter);

        exitCallback.onExit(pExitCode);
        replay(exitCallback);

        cmd.setInputStream(new ByteArrayInputStream(pInput.getBytes()));
    }

    /**
     * Special report matcher for mocking reasons.
     * @param expected
     * @return
     */
    public Message eqMessage(final String expected) {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                Message msg = (Message) argument;
                String payload = (String) msg.getPayload();
                return expected.equals(payload);
            }

            public void appendTo(StringBuffer buffer) {
                buffer.append("message matcher");
            }
        });
        return null;
    }
}
