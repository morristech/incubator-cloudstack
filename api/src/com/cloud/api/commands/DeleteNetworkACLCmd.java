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

import org.apache.log4j.Logger;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseCmd;
import com.cloud.api.IdentityMapper;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.SuccessResponse;
import com.cloud.async.AsyncJob;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.rules.FirewallRule;
import com.cloud.user.UserContext;

@Implementation(description="Deletes a Network ACL", responseObject=SuccessResponse.class)
public class DeleteNetworkACLCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(DeleteNetworkACLCmd.class.getName());
    private static final String s_name = "deletenetworkaclresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @IdentityMapper(entityTableName="firewall_rules")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, required=true, description="the ID of the network ACL")
    private Long id;

    // unexposed parameter needed for events logging
    @IdentityMapper(entityTableName="account")
    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.LONG, expose=false)
    private Long ownerId;
    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }
    
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }
    
    @Override
    public String getEventType() {
        return EventTypes.EVENT_FIREWALL_CLOSE;
    }

    @Override
    public String getEventDescription() {
        return  ("Deleting Network ACL id=" + id);
    }
    
    @Override
    public long getEntityOwnerId() {
        if (ownerId == null) {
            FirewallRule rule = _networkACLService.getNetworkACL(id);
            if (rule == null) {
                throw new InvalidParameterValueException("Unable to find network ACL by id=" + id);
            } else {
                ownerId = rule.getAccountId();
            }
        }
        return ownerId;
    }
    
    @Override
    public void execute() throws ResourceUnavailableException {
        UserContext.current().setEventDetails("Network ACL Id: " + id);
        boolean result = _networkACLService.revokeNetworkACL(id, true);
        
        if (result) {
            SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to delete network ACL");
        }
    }
    
    
    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return _firewallService.getFirewallRule(id).getNetworkId();
    }
    
    @Override
    public AsyncJob.Type getInstanceType() {
        return AsyncJob.Type.FirewallRule;
    }
}

