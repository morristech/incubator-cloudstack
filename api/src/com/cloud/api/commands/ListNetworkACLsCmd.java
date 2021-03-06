// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.api.commands;


import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd.CommandType;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.IdentityMapper;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.NetworkACLResponse;
import com.cloud.network.rules.FirewallRule;
import com.cloud.utils.Pair;

@Implementation(description="Lists all network ACLs", responseObject=NetworkACLResponse.class)
public class ListNetworkACLsCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = Logger.getLogger(ListNetworkACLsCmd.class.getName());

    private static final String s_name = "listnetworkaclsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @IdentityMapper(entityTableName="firewall_rules")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, description="Lists network ACL with the specified ID.")
    private Long id;
    
    @IdentityMapper(entityTableName="networks")
    @Parameter(name=ApiConstants.NETWORK_ID, type=CommandType.LONG, description="list network ACLs by network Id")
    private Long networkId;
    
    @Parameter(name=ApiConstants.TRAFFIC_TYPE, type=CommandType.STRING, description="list network ACLs by traffic type - Ingress or Egress")
    private String trafficType;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    
    public Long getNetworkId() {
        return networkId;
    }
    
    public Long getId() {
        return id;
    }
    
    public String getTrafficType() {
        return trafficType;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
    
    @Override
    public void execute(){
        Pair<List<? extends FirewallRule>,Integer> result = _networkACLService.listNetworkACLs(this);
        ListResponse<NetworkACLResponse> response = new ListResponse<NetworkACLResponse>();
        List<NetworkACLResponse> aclResponses = new ArrayList<NetworkACLResponse>();
        
        for (FirewallRule acl : result.first()) {
            NetworkACLResponse ruleData = _responseGenerator.createNetworkACLResponse(acl);
            aclResponses.add(ruleData);
        }
        response.setResponses(aclResponses, result.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response); 
    }
}
