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

/**
 * Created by aris on 2/2/17.
 */

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.component.ComponentService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;

import org.slf4j.Logger;


import static org.slf4j.LoggerFactory.getLogger;

/**
 * Component for the SDX-Coordination application.
 */

@Component(immediate = true)

public class SdxCoordination {

    public static final String SDX_COORDINATION_APP = "org.onosproject.sdxcoordination";

    private static final Logger log = getLogger(SdxCoordination.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationService applicationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentService componentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry registry;

    private ApplicationId appId;

    Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
    String CONFIG_KEY = "members";

    private ConfigFactory configFactory =
            new ConfigFactory(SubjectFactories.APP_SUBJECT_FACTORY,
                              CONFIG_CLASS, CONFIG_KEY) {
                @Override
                public SdxCoordinationConfig createConfig() {
                    return new SdxCoordinationConfig();
                }
            };

    @Activate
    protected void activate() {
        appId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        componentService.activate(appId, SdxCoordination.class.getName());
        appId = coreService.registerApplication(SDX_COORDINATION_APP);
        registry.registerConfigFactory(configFactory);
        log.info("SDX Coordination started");
        }

}
