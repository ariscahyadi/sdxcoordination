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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onos.sdxcoordination.SdxCoordination;
import org.onos.sdxcoordination.SdxCoordinationConfig;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.routing.bgp.BgpInfoService;
import org.onosproject.routing.bgp.BgpRouteEntry;
import org.onosproject.routing.bgp.BgpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * CLI to check the detail information (controller, asn, installed intents)
 * for specific given route prefix
 */

@Command(scope="sdxcoordination", name="sdx-coordination-check",
        description = "check sdx information for the route prefix")

public class SdxCoordinationCheck extends AbstractShellCommand {

    private static final String INTENT_API = "/onos/v1/intents/";
    private static final String SDN_IP_APP = "org.onosproject.sdnip";
    private static final String LOCAL_ASN = "65011";
    Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;

    @Argument(index = 0, name = "route", description = "Route Prefix",
            required = true, multiValued = false)
    String route = null;

    /**
     * Check and Print the result only
     */

    @Override
    protected void execute() {

        String asn = "";

        if ( RouteToASN(route) == null ) {
            print ("No originating AS Number for this prefix %s", route);
        }

        if ( RouteToASN(route).equals(LOCAL_ASN)) {

            print ("This prefix %s is originating from Local AS Number (AS %s)", route, LOCAL_ASN);

            if ( RouteToIntent(route) == null ) {
                print ("No local intent is installed for this prefix %s", route);
            } else {
                print ("Local intent is installed for this prefix %s", route);
            }

        } else {

            print ("This prefix %s is originating from Remote AS Number %s", route, RouteToASN(route));

            if ( checkRemoteIntent(RouteToASN(route), route) != null ) {
                print ("Remote intent is installed for this prefix %s", route);
            } else {
                print ("No remote intent is installed for this prefix %s", route);
            }

        }

    }

    /**
     * Gets specific installed intents for specific route prefix.
     *
     * @param route route prefix to be checked
     *
     * @return the MultiPointToSinglePoint intent of SDN-IP.
     */

    private Intent RouteToIntent(String route) {

        IntentService intentService = get(IntentService.class);
        Intent intentRoute = null;
        for (Intent intent : intentService.getIntents())
        {
            /**
             * Only get intent MultiPointToSinglePoint type
             */

            if (intent instanceof MultiPointToSinglePointIntent) {
                MultiPointToSinglePointIntent pi = (MultiPointToSinglePointIntent) intent;
                String key = String.format("%s", pi.key());
                if (key.equals(route)) {
                    //print("%s", pi);
                    intentRoute = pi;
                    break;
                }
            }
        }
        return intentRoute;
    }

    /**
     * Gets specific last/originating AS Number for specific route prefix.
     *
     * @param route route prefix to be checked
     *
     * @return the last/origination AS Number.
     */

    private String RouteToASN(String route){

        String lastAsn = "";
        BgpInfoService bgpInfoService = get(BgpInfoService.class);
        BgpSession foundBgpSession = null;
        for (BgpSession bgpSession : bgpInfoService.getBgpSessions())
            foundBgpSession = bgpSession;

        /**
         * Get the ASPath for specific BGP Peering
         */

        Collection<BgpRouteEntry> routes4 = foundBgpSession.getBgpRibIn4();
        for (BgpRouteEntry route4 : routes4) {
            String route4string = String.format("%s",route4.prefix());
            if ((route4string.equals(route))) {
                //print("%s", lastAsNumber(route4.getAsPath()));
                lastAsn = lastAsNumber(route4.getAsPath());
            }
        }
        return lastAsn;
    }

    /**
     * Check and print installed intents for specific route prefix
     * in remote controller participating in SDX Coordination
     *
     * @param route route prefix to be checked
     * @param asn origination AS Number for the route
     *
     * @return Installed Intent
     */

    private String checkRemoteIntent (String asn, String route){

        Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
        NetworkConfigService configService = get(NetworkConfigService.class);
        CoreService coreService = get(CoreService.class);
        ApplicationId sdxCoordinationAppId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        print("%s", sdxCoordinationAppId.toString());
        SdxCoordinationConfig memberConfig = configService.getConfig(sdxCoordinationAppId, CONFIG_CLASS);
        Set<SdxCoordinationConfig.ControllerConfig> memberControllers;
        String installedIntent = "";

        if (memberConfig == null || memberConfig.controllers().isEmpty()) {
            print("no configuration");
        }

        /**
         * Iterate all members configuration and stop for matching AS Number
         */

        memberControllers = memberConfig.controllers();

        /*memberConfig.controllers().forEach(controllerConfig -> {
            print("%s", controllerConfig.asn());
            print("%s", controllerConfig.ip());
            print("%s", controllerConfig.username());
            print("%s", controllerConfig.password());
            print("%s", controllerConfig.sinkPort());
            if (asn.equals(controllerConfig.asn())) {
                originatingMember = controllerConfig;
            }
        });*/

        for ( SdxCoordinationConfig.ControllerConfig memberController : memberControllers ) {
            if (memberController.asn().equals(asn)) {
                try {
                    checkIntent(memberController.ip(), memberController.username(), memberController.password(), route);
                } catch(IOException ie) {
                    ie.printStackTrace();
                }
            }
        }
        return installedIntent;
    }

    /**
     * Gets specific last/originating AS Number from AS Path
     *
     * @param asPath AS Path to be checked from specific route
     *
     * @return the last/origination AS Number.
     */

    private String lastAsNumber(BgpRouteEntry.AsPath asPath) {

        String lastSegment = "";

        ArrayList<BgpRouteEntry.PathSegment> pathSegments =
                asPath.getPathSegments();

        /**
         * Only get last segment of the AS Path
         */

        for (BgpRouteEntry.PathSegment pathSegment : pathSegments) {
            for (Long asn : pathSegment.getSegmentAsNumbers()) {
                if (pathSegment == pathSegments.get(pathSegments.size()-1)) {
                    lastSegment = asn.toString();
                }

            }
        }
        return lastSegment;
    }

    /**
     * Check and print the status of installed intents for specific route prefix
     * in remote controller participating in SDX Coordination
     * with given configuration from SDX coordination application config
     *
     * @param OnosIp Remote Controller's IP address
     * @param user Remote Controller's username
     * @param password Remote Controller's password
     * @param route route prefix to be checked
     */

    private String checkIntent(String OnosIp, String user, String password, String route) throws IOException {

        String intentUrl = "http://" + OnosIp + ":8181" + INTENT_API + SDN_IP_APP + "/" + route.replaceAll("/","%2F");
        URL url = null;
        String intent = "";

        try {
            url = new URL(intentUrl);
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password.toCharArray());
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        /**
         * Make REST API Call to remote Controller
         */

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        int responseCode = urlConnection.getResponseCode();
        if (responseCode == 200) {
            InputStream is = urlConnection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            intent = in.readLine();
            print (intent);
            print ("Intent Exist");
        }
        else {
            print (intent);
            print ("Intent Not Exist");
        }

        return intent;
    }
}
