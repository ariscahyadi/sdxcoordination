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
 * Created by aris on 4/6/17.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onos.sdxcoordination.SdxCoordination;
import org.onos.sdxcoordination.SdxCoordinationConfig;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.routing.bgp.BgpInfoService;
import org.onosproject.routing.bgp.BgpRouteEntry;
import org.onosproject.routing.bgp.BgpSession;
import org.onosproject.net.FilteredConnectPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * CLI to activate the control over local/remote controller with
 * given detail information (controller, asn, installed intents)
 * for specific given route prefix
 */

@Command(scope="sdxcoordination", name="sdx-coordination-activate",
        description = "activate the sdx coordination for the route prefix")

public class SdxCoordinationActivate extends AbstractShellCommand {

    private static final String INTENT_API = "/onos/v1/intents/";
    private static final String SDN_IP_APP = "org.onosproject.sdnip";

    public static final String SDX_COORDINATION_APP = "org.onosproject.sdxcoordination";
    private static final int PRIORITY = 50;
    Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
    private static String SINKPORT = "";
    private static final String LOCAL_ASN = "65011";

    @Argument(index = 0, name = "route", description = "Route Prefix",
            required = true, multiValued = false)
    String route = null;

    /**
     * Check and Execute the SDX Coordination
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
                modifyIntent(route);
            }

        } else {

            print ("This prefix %s is originating from Remote AS Number %s", route, RouteToASN(route));

            String matchRemoteIntent = checkRemoteIntent(RouteToASN(route), route);
            print (matchRemoteIntent);

            if ( matchRemoteIntent == null ) {
                print ("No remote intent is installed for this prefix %s", route);
            } else {
                print ("remote intent is installed for this prefix %s", route);
                modifyRemoteIntent(RouteToASN(route), matchRemoteIntent);
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
                    installedIntent = checkIntentWithAPI(memberController.ip(), memberController.username(), memberController.password(), route);
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
     * Check and print the status of installed intents through intent REST API
     * for specific route prefix in remote controller participating in SDX Coordination
     * with given configuration from SDX coordination application config
     *
     * @param OnosIp Remote Controller's IP address
     * @param user Remote Controller's username
     * @param password Remote Controller's password
     * @param route route prefix to be checked
     */

    private String checkIntentWithAPI(String OnosIp, String user, String password, String route) throws IOException {

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
            //print (intent);
            print ("Intent Exist");
        }
        else {
            //print (intent);
            print ("Intent Not Exist");
        }

        return intent;
    }

    /**
     * Modify installed intents for specific route prefix.
     *
     * @param route intent for specific route prefix to be checked
     */

    private void modifyIntent(String route) {

        CoreService coreService = get(CoreService.class);
        ApplicationId sdxCoordinationAppId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        IntentService service = get(IntentService.class);

        NetworkConfigService configService = get(NetworkConfigService.class);
        SdxCoordinationConfig memberConfig = configService.getConfig(sdxCoordinationAppId, CONFIG_CLASS);
        Set<ConnectPoint> filteredIngressPoint = new HashSet<>();


        if (memberConfig == null || memberConfig.controllers().isEmpty()) {
            print("no configuration");
            return;
        }

        for (Intent intent : service.getIntents()) {
            if (intent instanceof MultiPointToSinglePointIntent) {
                MultiPointToSinglePointIntent pi = (MultiPointToSinglePointIntent) intent;
                String key = String.format("%s",pi.key());
                if (key.equals(route))
                //    print("%s", pi.toString());
                //    print("%s", pi.id().toString());
                //    print("%s", pi.key().toString());
                //    print("%s", pi.priority());
                //print("%s", pi.resources().toString());
                //    print("%s", pi.selector().toString());
                //    print("%s", pi.treatment().toString());

                /*
                for ( ConnectPoint ingressPoint : pi.ingressPoints() ) {

                    if ( ingressPoint.toString().equals(pi.egressPoint().toString()) ) {
                        //print("same interface");
                    } else {
                        //print("%s", ingressPoint.toString());
                        filteredIngressPoint.add(ingressPoint);
                    }
                }
            */

                //print("%s", pi.egressPoint().toString());
                //print("%s", pi.constraints());

                    SINKPORT = findSinkPort(route);
                    FilteredConnectPoint sinkPoint =
                        new FilteredConnectPoint(ConnectPoint.deviceConnectPoint(SINKPORT));

                //SINKPORT = findSinkPort(route);
                //print("%s", SINKPORT);
                //ConnectPoint sinkPortCP = ConnectPoint.deviceConnectPoint(SINKPORT);
                //print("%s", sinkPortCP.toString());

                /*
                Intent modifiedIntent = MultiPointToSinglePointIntent.builder()
                        .appId(sdxCoordinationAppId)
                        .key(Key.of(pi.key().toString(), sdxCoordinationAppId))
                        .selector(pi.selector())
                        .treatment(pi.treatment())
                        .ingressPoints(filteredIngressPoint)
                        .egressPoint(ConnectPoint.deviceConnectPoint(SINKPORT))
                        .constraints(pi.constraints())
                        .priority(PRIORITY)
                        .build();
                        */

                Intent modifiedIntent = PointToPointIntent.builder()
                        .appId(appId())
                        .key(Key.of(pi.key().toString(), appId()))
                        .filteredIngressPoint(pi.filteredEgressPoint())
                        .filteredEgressPoint(sinkPoint)
                        .priority(PRIORITY)
                        .build();

                service.submit(modifiedIntent);
                print("Modified Multipoint to single point intent submitted:\n%s", modifiedIntent.toString());

            }
        }
    }

    /**
     * Modify installed remote intents for specific route prefix.
     *
     * @param asn of originating route prefix
     * @param matchIntent installed intent for specific route prefix to be modified
     */

    private void modifyRemoteIntent(String asn, String matchIntent) {
        NetworkConfigService configService = get(NetworkConfigService.class);
        CoreService coreService = get(CoreService.class);
        ApplicationId sdxCoordinationAppId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        //print("%s", sdxCoordinationAppId.toString());
        SdxCoordinationConfig memberConfig = configService.getConfig(sdxCoordinationAppId, CONFIG_CLASS);
        //print("%s", memberConfig.toString());
        if (memberConfig == null || memberConfig.controllers().isEmpty()) {
            print("no configuration");
            return;
        }

        print("%s", matchIntent);

        memberConfig.controllers().forEach(controllerConfig -> {
            print("%s", controllerConfig.asn());
            print("%s", controllerConfig.ip());
            print("%s", controllerConfig.username());
            print("%s", controllerConfig.password());
            print("%s", controllerConfig.sinkPort());
            print("%s", matchIntent);

            if (asn.equals(controllerConfig.asn())) {
                try {
                    print(matchIntent);
                    modifyWithIntentAPI(controllerConfig.ip(), controllerConfig.username(), controllerConfig.password(), matchIntent, controllerConfig.sinkPort());
                } catch(IOException ie) {
                    ie.printStackTrace();
                }
                return;
            }
        });
    }

    /**
     * Check and print the status of installed intents through intent REST API
     * for specific route prefix in remote controller participating in SDX Coordination
     * with given configuration from SDX coordination application config
     *
     * @param OnosIp Remote Controller's IP address
     * @param user Remote Controller's username
     * @param password Remote Controller's password
     * @param matchIntent Intent need to be modified/override
     * @param sinkPort port for redirecting the traffic
     */

    private void modifyWithIntentAPI(String OnosIp, String user, String password, String matchIntent, String sinkPort) throws IOException {

        print (matchIntent);
        URL url=null;
        //String intentUrl = "http://172.30.91.112:8181/onos/v1/intents";
        String intentUrl = "http://" + OnosIp + ":8181" + INTENT_API;

        // Parse JSON String to get MP2SP intent egress Port
        ObjectMapper mapper = new ObjectMapper();
        JsonNode matchIntentJson = mapper.readValue(matchIntent, JsonNode.class);
        JsonNode matchIntentEgressPoint = matchIntentJson.get("egressPoint");
        ObjectNode modifyIngressPort = mapper.createObjectNode();
        modifyIngressPort.put("port",matchIntentEgressPoint.get("port"));
        modifyIngressPort.put("device",matchIntentEgressPoint.get("device"));

        // Create egress Port for Policy Config
        String[] sinkPortComponent = sinkPort.split("/");

        // Create JSON for P2P intent
        JsonNode intentJson = mapper.createObjectNode();
        ((ObjectNode) intentJson).put("type","PointToPointIntent");
        ((ObjectNode) intentJson).put("appId",SDX_COORDINATION_APP);
        ((ObjectNode) intentJson).put("priority",300);
        //JsonNode ingressPoint = mapper.createObjectNode();
        //((ObjectNode) ingressPoint).put("port", "4");
        //((ObjectNode) ingressPoint).put("device", "of:0000000000000002");
        //((ObjectNode) intentJson).set("ingressPoint", ingressPoint);
        ((ObjectNode) intentJson).set("ingressPoint", modifyIngressPort);
        JsonNode egressPoint = mapper.createObjectNode();
        ((ObjectNode) egressPoint).put("port", sinkPortComponent[1]);
        ((ObjectNode) egressPoint).put("device", sinkPortComponent[0]);
        ((ObjectNode) intentJson).set("egressPoint", egressPoint);

        System.out.println(intentJson);

        try {
            url = new URL(intentUrl);
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user,password.toCharArray());
                }
            });

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type","application/json");
            OutputStream os = urlConnection.getOutputStream();
            os.write(intentJson.toString().getBytes());
            os.flush();

            if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                throw new RuntimeException("Failed : HTTP error code : "
                                                   + urlConnection.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (urlConnection.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                System.out.println(output);
            }
            urlConnection.disconnect();

        }

        catch (MalformedURLException e){
            e.printStackTrace();
        }

    }

    /**
     * Find the sink port configuration from the member controller
     * based on the route prefix
     *
     * @param route route prefix to be checked
     *
     * @return the sink port location
     */

    private String findSinkPort (String route){
        Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
        NetworkConfigService configService = get(NetworkConfigService.class);
        CoreService coreService = get(CoreService.class);
        ApplicationId sdxCoordinationAppId = coreService.getAppId(SdxCoordination.SDX_COORDINATION_APP);
        SdxCoordinationConfig memberConfig = configService.getConfig(sdxCoordinationAppId, CONFIG_CLASS);
        if (memberConfig == null || memberConfig.controllers().isEmpty()) {
            print("no configuration");
            return null;
        }

        /**
         * Find the AS Number from the prefix
         */

        String asn = RouteToASN(route);

        /**
         * Iterate all members configuration and stop for matching AS Number
         */

        memberConfig.controllers().forEach(controllerConfig -> {
            //print("%s", controllerConfig.sinkPort());
            if (asn.equals(controllerConfig.asn())) {
                SINKPORT = controllerConfig.sinkPort();
            }
        });
        return SINKPORT;
    }

}
