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
 * Created by aris on 2/6/17.
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
import org.onosproject.net.config.NetworkConfigService;

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



/**
 * CLI to check intent for the specific route in the remote controller
 */

@Command(scope="sdxcoordination", name="check-remote-intent", description = "Check intent for specific routes")

public class CheckRemoteIntentCommand extends AbstractShellCommand {

    private static final String INTENT_API = "/onos/v1/intents/";
    private static final String SDN_IP_APP = "org.onosproject.sdnip";
    Class<SdxCoordinationConfig> CONFIG_CLASS = SdxCoordinationConfig.class;
    public static final String SDX_COORDINATION_APP = "org.onosproject.sdxcoordination";

    @Argument(index = 0, name = "asn", description = "AS Number",
            required = true, multiValued = false)
    String asn = null;

    @Argument(index = 1, name = "route", description = "Route Prefix",
            required = true, multiValued = false)
    String route = null;

    @Override
    protected void execute() {

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

        memberConfig.controllers().forEach(controllerConfig -> {
            print("%s", controllerConfig.asn());
            print("%s", controllerConfig.ip());
            print("%s", controllerConfig.username());
            print("%s", controllerConfig.password());
            print("%s", controllerConfig.sinkPort());

            if (asn.equals(controllerConfig.asn())) {
                try {
                    String matchIntent = checkIntent(controllerConfig.ip(), controllerConfig.username(), controllerConfig.password());
                    print (matchIntent);
                    modifyIntent(controllerConfig.ip(), controllerConfig.username(), controllerConfig.password(), matchIntent, controllerConfig.sinkPort());
                } catch(IOException ie) {
                    ie.printStackTrace();
            }
            return;
            }
        });

    }

    private String checkIntent(String OnosIp, String user, String password) throws IOException {

        //String route2 = route.replaceAll("/","%2F");
        String intentUrl = "http://" + OnosIp + ":8181" + INTENT_API + SDN_IP_APP + "/" + route.replaceAll("/","%2F");
        //print ("%s", intentUrl);
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
            print ("Intent Not Exist");
        }
        return intent;
    }

    private void modifyIntent(String OnosIp, String user, String password, String matchIntent, String sinkPort) throws IOException {

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
}
