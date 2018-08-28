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

package org.onos.sdxcoordination.cli;

/**
 * Created by aris on 1/12/17.
 */



import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.SubjectFactory;

/**
 * CLI to find to read the network-cfg.json
 */

@Command(scope="sdxcoordination", name="load-config", description = "Load network configuration")

public class LoadConfigCommand extends AbstractShellCommand {

    private final ObjectMapper mapper = new ObjectMapper();
    private NetworkConfigService configService;
    String subjectClassKey = "apps";
    String SDX_COORDINATION_APP = "org.onosproject.sdxcoordination";
    String MEMBERS = "members";

    @Override
    protected void execute() {

        configService = get(NetworkConfigService.class);

        JsonNode listMembers = mapper.createObjectNode();
        SubjectFactory subjectFactory = configService.getSubjectFactory(subjectClassKey);
        Object s = subjectFactory.createSubject(SDX_COORDINATION_APP);
        listMembers = getSubjectConfig(getConfig(s, subjectClassKey, MEMBERS));

        try {
            print("%s", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listMembers));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error writing JSON to string", e);
        }
    }

    private JsonNode getSubjectConfig(Config config) {
        return config != null ? config.node() : null;
    }

    private Config getConfig(Object s, String subjectKey, String ck) {
        Class<? extends Config> configClass = configService.getConfigClass(subjectKey, ck);
        return configClass != null ? configService.getConfig(s, configClass) : null;
    }
}

