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
 * Created by aris on 9/17/17.
 */


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;

import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

/**
 * CLI to check intent for the specific route in the remote controller
 */

@Command(scope="sdxcoordination", name="check-flow", description = "Check flow statistics for specific routes")

public class CheckFlowCommand extends AbstractShellCommand {

    private static final String VISIBILITY_SERVER = "210.125.84.140";
    private static final String FLOW_API = "/api/onosbuild2017/";

    @Argument(index = 0, name = "threshold", description = "packet count threshold",
            required = true, multiValued = false)
    String threshold= null;

    @Override
    protected void execute() {

        String listFlows = "";
        Integer packetCount = 0;
        String sourceAddress = "";

        try {
            //listFlows = "{\"FlowArray\" : " + checkFlow() + "}";
            listFlows = "{\"FlowArray\" : " + EStoJson() + "}";
            //print (listFlows.toString());
            }catch (IOException ie){
                ie.printStackTrace();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            //JsonNode flow = objectMapper.readValue(listFlows, JsonNode.class);
            //JsonArray flowArray = objectMapper.readValue(listFlows, JsonArray.class);
            JsonNode flowArray = objectMapper.readTree(listFlows).get("FlowArray");

            for (JsonNode flow : flowArray) {
                print (flow.toString());
                sourceAddress = flow.get("source_address").asText();
                packetCount = flow.get("number_of_packet").asInt();
                //print (sourceAddress + ":" + packetCount.toString());

                if (packetCount >= Integer.valueOf(threshold)) {
                    String[] IP = sourceAddress.split("\\.");
                    String subnetAddress = IP[0] + "." + IP[1] + "." + IP[2] + ".0/24";
                    print ("Activate rule for route : %s", subnetAddress);
                }
            }

        } catch (IOException ie) {
            ie.printStackTrace();
        }

    }

    private String checkFlow() throws IOException {

        String flowAPIURL = "http://" + VISIBILITY_SERVER + ":8000" + FLOW_API;
        //print ("%s", flowAPIURL);
        URL url = null;
        String flows = "";

        try {
            url = new URL(flowAPIURL);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        int responseCode = urlConnection.getResponseCode();
        if (responseCode == 200) {
            InputStream is = urlConnection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            flows = in.readLine();
            print ("Flows Exist");
            //print (flows);
        }
        else {
            print ("Flows Not Exist");
        }
        return flows;
    }

    public static String EStoJson() throws IOException {

        RestClient restClient = RestClient.builder(
                new HttpHost("103.22.221.56", 9200, "http"))
                .build();

        //String aggQuery = "{\"query\":{ \"range\": {\"@timestamp\": {\"gte\": \"now-5d\",\"lte\": \"now\" } } }, "
        //        + "\"aggs\" : {\"group_by_flowkey\" : { \"terms\" : { \"field\" : \"flowKey\" }, "
        //        + "\"aggs\" : { \"AvgBytes\" : { \"avg\" : { \"field\" : \"Bytes\" } } } } } }";

        String aggQuery = "{\"query\":{\"match_all\":{}}}";

        Response response = restClient.performRequest
                ("GET","/flow-management-policy/flowPolicy/_search?pretty=true",
                 new Hashtable<>(), new StringEntity(aggQuery));
        String restOutput = EntityUtils.toString(response.getEntity());
        System.out.println(restOutput);

        String AvgBytes = "";
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode node = objectMapper.readValue(restOutput, JsonNode.class);
            JsonNode aggNode = node.get("aggregations");
            JsonNode bucketNode = aggNode.get("group_by_flowkey");
            AvgBytes = bucketNode.get("buckets").toString();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("AvgBytes = " + AvgBytes);
        restClient.close();
        return AvgBytes = "{ \"AvgBytes\":" + AvgBytes.substring(0, AvgBytes.length()-1) + "]}";
    }

}
