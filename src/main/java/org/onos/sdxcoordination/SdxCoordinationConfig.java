/*
 * Copyright 2017-present Open Networking Laboratory
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

package org.onos.sdxcoordination;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configuration Object for controllers who participating in SDX Coordination
 */

public class SdxCoordinationConfig extends Config<ApplicationId> {
    public static final String CONTROLLERS = "controllers";
    public static final String ASN = "asn";
    public static final String IP = "ip";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String SINKPORT = "sinkPort";

    /**
     * Gets the set of configured Controllers.
     *
     * @return Controllers List Configuration
     */

    public Set<ControllerConfig> controllers(){
        Set<ControllerConfig> controllerList = Sets.newHashSet();
        JsonNode controllerNode = object.get(CONTROLLERS);

        if (controllerNode == null) {
            return controllerList;
        }

        controllerNode.forEach(jsonNode -> {
            controllerList.add(new ControllerConfig(
                    jsonNode.get(ASN).asText(),
                    jsonNode.get(IP).asText(),
                    jsonNode.get(USERNAME).asText(),
                    jsonNode.get(PASSWORD).asText(),
                    jsonNode.get(SINKPORT).asText()));
        });
        return controllerList;
    }

    /**
     * Configuration for controller in SDX Coordination Application.
     */

    public static class ControllerConfig{
        private String asn;
        private String ip;
        private String username;
        private String password;
        private String sinkPort;

        public ControllerConfig(String asn, String ip, String username, String password, String sinkPort){
            this.asn = checkNotNull(asn);
            this.ip = checkNotNull(ip);
            this.username = checkNotNull(username);
            this.password = checkNotNull(password);
            this.sinkPort = checkNotNull(sinkPort);
        }

        public String asn() {
            return asn;
        }
        public String ip() {
            return ip;
        }
        public String username() {
            return username;
        }
        public String password() {
            return password;
        }
        public String sinkPort() {
            return sinkPort;
        }

        public int hashCode() {
            return Objects.hash(asn, ip, username, password, sinkPort);
        }
    }
}
