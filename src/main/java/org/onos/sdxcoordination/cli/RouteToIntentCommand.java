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
 * Created by aris on 1/11/17.
 */

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onos.sdxcoordination.SdxCoordination;
import org.onos.sdxcoordination.SdxCoordinationConfig;
import org.onosproject.cli.net.ConnectivityIntentCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;

/**
 * CLI to list networks.
 */
@Command(scope="sdxcoordination", name="route-to-intent", description = "list all the intents for the route")

public class RouteToIntentCommand extends ConnectivityIntentCommand {

    public static final String SDX_COORDINATION_APP = "org.onosproject.sdxcoordination";
    private static final int PRIORITY = 300;
    Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
    private static String SINKPORT = "";

    @Argument(index = 0, name = "route", description = "Route Prefix",
            required = true, multiValued = false)
    String route = null;

    @Override
    protected void execute() {
        CoreService coreService = get(CoreService.class);
        ApplicationId sdxCoordinationAppId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        IntentService service = get(IntentService.class);

        NetworkConfigService configService = get(NetworkConfigService.class);
        SdxCoordinationConfig memberConfig = configService.getConfig(sdxCoordinationAppId, CONFIG_CLASS);

        if (memberConfig == null || memberConfig.controllers().isEmpty()) {
            print("no configuration");
            return;
        }

        for (Intent intent : service.getIntents()) {
            if (intent instanceof MultiPointToSinglePointIntent) {
                MultiPointToSinglePointIntent pi = (MultiPointToSinglePointIntent) intent;
                String key = String.format("%s",pi.key());
                if (key.equals(route))
                    print("%s", pi.toString());
                    print("%s", pi.id().toString());
                    print("%s", pi.key().toString());
                    print("%s", pi.priority());
                    //print("%s", pi.resources().toString());
                    print("%s", pi.selector().toString());
                    print("%s", pi.treatment().toString());

                    /**
                    Set<ConnectPoint> filteredIngressPoint = new HashSet<>();
                    for ( ConnectPoint ingressPoint : pi.ingressPoints() ) {
                        if ( ingressPoint.toString().equals(pi.egressPoint().toString()) ) {
                            print("same interface");
                        } else {
                            print("%s", ingressPoint.toString());
                            filteredIngressPoint.add(ingressPoint);
                        }
                    }
                    print("%s", pi.egressPoint().toString());
                    print("%s", pi.constraints());
                    **/

                    ConnectPoint modifiedIngressPoint = pi.egressPoint();
                    SINKPORT = "of:0000000000000001/5";
                    FilteredConnectPoint sinkPoint =
                        new FilteredConnectPoint(ConnectPoint.deviceConnectPoint(SINKPORT));

                    /**
                    Intent modifiedIntent = MultiPointToSinglePointIntent.builder()
                        .appId(sdxCoordinationAppId)
                        .key(Key.of(pi.key().toString(), sdxCoordinationAppId))
                        .selector(pi.selector())
                        .treatment(pi.treatment())
                        .ingressPoints(filteredIngressPoint)
                        .egressPoint(pi.egressPoint())
                        .constraints(pi.constraints())
                        .priority(PRIORITY)
                        .build();
                     **/

                    Intent modifiedIntent = PointToPointIntent.builder()
                        .appId(sdxCoordinationAppId)
                        .key(Key.of(modifiedIngressPoint.toString() + "-" + SINKPORT, sdxCoordinationAppId))
                        .filteredIngressPoint(pi.filteredEgressPoint())
                        .filteredEgressPoint(sinkPoint)
                        .priority(PRIORITY)
                        .build();

                    service.submit(modifiedIntent);
                    print("Modified Multipoint to single point intent submitted:\n%s", modifiedIntent.toString());
            }
        }
    }
}