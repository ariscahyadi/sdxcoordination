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
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.routing.bgp.BgpConstants;
import org.onosproject.routing.bgp.BgpInfoService;
import org.onosproject.routing.bgp.BgpRouteEntry;
import org.onosproject.routing.bgp.BgpSession;

import java.util.ArrayList;
import java.util.Collection;

/**
 * CLI to find the ASN from the prefix.
 */
@Command(scope="sdxcoordination", name="route-to-asn", description = "find the origin ASN for the route")

public class RouteToASNCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "route", description = "Route Prefix",
            required = true, multiValued = false)
    String route = null;

    @Override
    protected void execute() {
        BgpInfoService service = AbstractShellCommand.get(BgpInfoService.class);
        BgpSession foundBgpSession = null;
        for (BgpSession bgpSession : service.getBgpSessions())
            foundBgpSession = bgpSession;
            Collection<BgpRouteEntry> routes4 = foundBgpSession.getBgpRibIn4();
            for (BgpRouteEntry route4 : routes4) {
                String route4string = String.format("%s",route4.prefix());
                if ((route4string.equals(route))) {
                    print("%s", route4.prefix());
                    print("%s", asPath4Cli(route4.getAsPath()));
                    print("%s", lastAsNumber(route4.getAsPath()));
                }
            }
    }

    /**
     * Formats the AS Path as a string that can be shown on the CLI.
     *
     * @param asPath the AS Path to format
     * @return the AS Path as a string
     */
    private String asPath4Cli(BgpRouteEntry.AsPath asPath) {
        ArrayList<BgpRouteEntry.PathSegment> pathSegments =
                asPath.getPathSegments();

        if (pathSegments.isEmpty()) {
            return "[none]";
        }

        final StringBuilder builder = new StringBuilder();
        for (BgpRouteEntry.PathSegment pathSegment : pathSegments) {
            String prefix = null;
            String suffix = null;
            switch (pathSegment.getType()) {
                case BgpConstants.Update.AsPath.AS_SET:
                    prefix = "[AS-Set";
                    suffix = "]";
                    break;
                case BgpConstants.Update.AsPath.AS_SEQUENCE:
                    break;
                case BgpConstants.Update.AsPath.AS_CONFED_SEQUENCE:
                    prefix = "[AS-Confed-Seq";
                    suffix = "]";
                    break;
                case BgpConstants.Update.AsPath.AS_CONFED_SET:
                    prefix = "[AS-Confed-Set";
                    suffix = "]";
                    break;
                default:
                    builder.append(String.format("(type = %s)",
                                                 BgpConstants.Update.AsPath.typeToString(pathSegment.getType())));
                    break;
            }

            if (prefix != null) {
                if (builder.length() > 0) {
                    builder.append(" ");        // Separator
                }
                builder.append(prefix);
            }
            // Print the AS numbers
            for (Long asn : pathSegment.getSegmentAsNumbers()) {
                if (builder.length() > 0) {
                    builder.append(" ");        // Separator
                }
                builder.append(String.format("%d", asn));
            }
            if (suffix != null) {
                // No need for separator
                builder.append(prefix);
            }
        }
        return builder.toString();
    }

    private String lastAsNumber(BgpRouteEntry.AsPath asPath) {

        String lastSegment = "";

        ArrayList<BgpRouteEntry.PathSegment> pathSegments =
                asPath.getPathSegments();

        for (BgpRouteEntry.PathSegment pathSegment : pathSegments) {
            for (Long asn : pathSegment.getSegmentAsNumbers()) {
                if (pathSegment == pathSegments.get(pathSegments.size()-1)) {
                    lastSegment = asn.toString();
                }

            }
        }
        return lastSegment;
    }
}