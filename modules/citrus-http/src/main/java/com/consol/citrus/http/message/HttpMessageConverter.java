/*
 * Copyright 2006-2014 the original author or authors.
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

package com.consol.citrus.http.message;

import com.consol.citrus.http.client.HttpEndpointConfiguration;
import com.consol.citrus.message.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Message converter implementation able to convert HTTP request and response entities to internal message
 * representation and other way round.
 *
 * @author Christoph Deppisch
 * @since 2.0
 */
public class HttpMessageConverter implements MessageConverter<HttpEntity, HttpEndpointConfiguration> {

    @Override
    public HttpEntity convertOutbound(Message message, HttpEndpointConfiguration endpointConfiguration) {
        HttpMessage httpMessage;
        if (message instanceof HttpMessage) {
            httpMessage = (HttpMessage) message;
        } else {
            httpMessage = new HttpMessage(message);
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        endpointConfiguration.getHeaderMapper().fromHeaders(new org.springframework.messaging.MessageHeaders(httpMessage.copyHeaders()), httpHeaders);

        Map<String, ?> messageHeaders = httpMessage.copyHeaders();
        for (Map.Entry<String, ?> header : messageHeaders.entrySet()) {
            if (!header.getKey().startsWith(MessageHeaders.PREFIX) &&
                    !MessageHeaderUtils.isSpringInternalHeader(header.getKey()) &&
                    !httpHeaders.containsKey(header.getKey())) {
                httpHeaders.add(header.getKey(), header.getValue().toString());
            }
        }

        if (httpHeaders.getContentType() == null) {
            httpHeaders.setContentType(MediaType.parseMediaType(endpointConfiguration.getContentType().contains("charset") ?
                    endpointConfiguration.getContentType() : endpointConfiguration.getContentType() + ";charset=" + endpointConfiguration.getCharset()));
        }

        Object payload = httpMessage.getPayload();
        if (httpMessage.getStatusCode() != null) {
            return new ResponseEntity(payload, httpHeaders, httpMessage.getStatusCode());
        }

        HttpMethod method = endpointConfiguration.getRequestMethod();
        if (httpMessage.getRequestMethod() != null) {
            method = httpMessage.getRequestMethod();
        }

        if (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)) {
            return new HttpEntity(payload, httpHeaders);
        }

        return new HttpEntity<Object>(httpHeaders);
    }

    @Override
    public HttpMessage convertInbound(HttpEntity message, HttpEndpointConfiguration endpointConfiguration) {
        Map<String, ?> mappedHeaders = endpointConfiguration.getHeaderMapper().toHeaders(message.getHeaders());
        HttpMessage httpMessage = new HttpMessage(message.getBody() != null ? message.getBody() : "", convertHeaderTypes(mappedHeaders))
                .version("HTTP/1.1"); //TODO check if we have access to version information

        for (Map.Entry<String, String> customHeader : getCustomHeaders(message.getHeaders(), mappedHeaders).entrySet()) {
            httpMessage.setHeader(customHeader.getKey(), customHeader.getValue());
        }

        if (message instanceof ResponseEntity) {
            httpMessage.statusCode(((ResponseEntity) message).getStatusCode());
            httpMessage.reasonPhrase(((ResponseEntity) message).getStatusCode().name());
        }

        return httpMessage;
    }

    /**
     * Message headers consist of standard HTTP message headers and custom headers.
     * This method assumes that all header entries that were not initially mapped
     * by header mapper implementations are custom headers.
     *
     * @param httpHeaders all message headers in their pre nature.
     * @param mappedHeaders the previously mapped header entries (all standard headers).
     * @return
     */
    private Map<String, String> getCustomHeaders(HttpHeaders httpHeaders, Map<String, ?> mappedHeaders) {
        Map<String, String> customHeaders = new HashMap<String, String>();

        for (Map.Entry<String, List<String>> header : httpHeaders.entrySet()) {
            if (!mappedHeaders.containsKey(header.getKey())) {
                customHeaders.put(header.getKey(), StringUtils.collectionToCommaDelimitedString(header.getValue()));
            }
        }

        return customHeaders;
    }

    /**
     * Checks for collection typed header values and convert them to comma delimited String.
     * We need this for further header processing e.g when forwarding headers to JMS queues.
     *
     * @param headers the http request headers.
     */
    private Map<String, Object> convertHeaderTypes(Map<String, ?> headers) {
        Map<String, Object> convertedHeaders = new HashMap<String, Object>();

        for (Map.Entry<String, ?> header : headers.entrySet()) {
            if (header.getValue() instanceof Collection<?>) {
                Collection<?> value = (Collection<?>)header.getValue();
                convertedHeaders.put(header.getKey(), StringUtils.collectionToCommaDelimitedString(value));
            } else if (header.getValue() instanceof MediaType) {
                convertedHeaders.put(header.getKey(), header.getValue().toString());
            } else {
                convertedHeaders.put(header.getKey(), header.getValue());
            }
        }

        return convertedHeaders;
    }

    @Override
    public void convertOutbound(HttpEntity externalMessage, Message internalMessage, HttpEndpointConfiguration endpointConfiguration) {
        throw new UnsupportedOperationException("HttpMessageConverter doe not support predefined HttpEntity objects");
    }
}
