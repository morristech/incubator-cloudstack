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
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.BaseCmd;
import com.cloud.api.IdentityMapper;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.PrivateGatewayResponse;
import com.cloud.async.AsyncJob;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.Vpc;
import com.cloud.user.Account;

@Implementation(description="Creates a private gateway", responseObject=PrivateGatewayResponse.class)
public class CreatePrivateGatewayCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = Logger.getLogger(CreatePrivateGatewayCmd.class.getName());

    private static final String s_name = "createprivategatewayresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    
    @IdentityMapper(entityTableName="physical_network")
    @Parameter(name=ApiConstants.PHYSICAL_NETWORK_ID, type=CommandType.LONG, description="the Physical Network ID the network belongs to")
    private Long physicalNetworkId;

    @Parameter(name=ApiConstants.GATEWAY, type=CommandType.STRING, required=true, description="the gateway of the Private gateway")
    private String gateway;
    
    @Parameter(name=ApiConstants.NETMASK, type=CommandType.STRING, required=true, description="the netmask of the Private gateway")
    private String netmask;
    
    @Parameter(name=ApiConstants.IP_ADDRESS, type=CommandType.STRING, required=true, description="the IP address of the Private gateaway")
    private String ipAddress;
    
    @Parameter(name=ApiConstants.VLAN, type=CommandType.STRING, required=true, description="the Vlan for the private gateway")
    private String vlan;
    
    @IdentityMapper(entityTableName="vpc")
    @Parameter(name=ApiConstants.VPC_ID, type=CommandType.LONG, required=true, description="the VPC network belongs to")
    private Long vpcId;
    
    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getGateway() {
        return gateway;
    }

    public String getVlan() {
        return vlan;
    }
    
    public String getNetmask() {
        return netmask;
    }

    public String getStartIp() {
        return ipAddress;
    }
    
    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }
    
    public Long getVpcId() {
        return vpcId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }
    
    
    @Override
    public void create() throws ResourceAllocationException {
        PrivateGateway result = null;
        try {
            result = _vpcService.createVpcPrivateGateway(getVpcId(), getPhysicalNetworkId(),
                    getVlan(), getStartIp(), getGateway(), getNetmask(), getEntityOwnerId());
        } catch (InsufficientCapacityException ex){
            s_logger.info(ex);
            s_logger.trace(ex);
            throw new ServerApiException(BaseCmd.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
        } catch (ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }
        
        if (result != null) {
            this.setEntityId(result.getId());
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create private gateway");
        }
    }
    
    @Override
    public void execute() throws InsufficientCapacityException, ConcurrentOperationException, 
    ResourceAllocationException, ResourceUnavailableException {
        PrivateGateway result = _vpcService.applyVpcPrivateGateway(getEntityId(), true);
        if (result != null) {
            PrivateGatewayResponse response = _responseGenerator.createPrivateGatewayResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create private gateway");
        }
    }
    
    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PRIVATE_GATEWAY_CREATE;
    }

    @Override
    public String getEventDescription() {
        return  "creating private gateway";
    }
    
    @Override
    public String getEntityTable() {
        return "vpc_gateways";
    }
    
    
    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.vpcSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        Vpc vpc =  _vpcService.getVpc(vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("Invalid id is specified for the vpc");
        }
        return vpc.getId();
    }
    
    @Override
    public AsyncJob.Type getInstanceType() {
        return AsyncJob.Type.PrivateGateway;
    }
}
