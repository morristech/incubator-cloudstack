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
import java.util.EnumSet;
import java.util.List;

import org.apache.log4j.Logger;

import com.cloud.api.ApiConstants;
import com.cloud.api.ApiConstants.HostDetails;
import com.cloud.api.BaseListCmd;
import com.cloud.api.IdentityMapper;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.response.HostResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.async.AsyncJob;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.utils.Pair;

@Implementation(description="Lists hosts.", responseObject=HostResponse.class)
public class ListHostsCmd extends BaseListCmd {
    public static final Logger s_logger = Logger.getLogger(ListHostsCmd.class.getName());

    private static final String s_name = "listhostsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @IdentityMapper(entityTableName="cluster")
    @Parameter(name=ApiConstants.CLUSTER_ID, type=CommandType.LONG, description="lists hosts existing in particular cluster")
    private Long clusterId;

    @IdentityMapper(entityTableName="host")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, description="the id of the host")
    private Long id;

    @Parameter(name=ApiConstants.NAME, type=CommandType.STRING, description="the name of the host")
    private String hostName;

    @IdentityMapper(entityTableName="host_pod_ref")
    @Parameter(name=ApiConstants.POD_ID, type=CommandType.LONG, description="the Pod ID for the host")
    private Long podId;

    @Parameter(name=ApiConstants.STATE, type=CommandType.STRING, description="the state of the host")
    private String state;

    @Parameter(name=ApiConstants.TYPE, type=CommandType.STRING, description="the host type")
    private String type;

    @IdentityMapper(entityTableName="data_center")
    @Parameter(name=ApiConstants.ZONE_ID, type=CommandType.LONG, description="the Zone ID for the host")
    private Long zoneId;

    @IdentityMapper(entityTableName="vm_instance")
    @Parameter(name=ApiConstants.VIRTUAL_MACHINE_ID, type=CommandType.LONG, required=false, description="lists hosts in the same cluster as this VM and flag hosts with enough CPU/RAm to host this VM")
    private Long virtualMachineId;
    
    @Parameter(name=ApiConstants.RESOURCE_STATE, type=CommandType.STRING, description="list hosts by resource state. Resource state represents current state determined by admin of host, valule can be one of [Enabled, Disabled, Unmanaged, PrepareForMaintenance, ErrorInMaintenance, Maintenance, Error]")
    private String resourceState;   
    
    @Parameter(name=ApiConstants.DETAILS, type=CommandType.LIST, collectionType=CommandType.STRING, description="comma separated list of host details requested, value can be a list of [ min, all, capacity, events, stats]" )
    private List<String> viewDetails;
    
    @Parameter(name=ApiConstants.HA_HOST, type=CommandType.BOOLEAN, description="if true, list only hosts dedicated to HA")
    private Boolean haHost;
    
    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getClusterId() {
        return clusterId;
    }

    public Long getId() {
        return id;
    }

    public String getHostName() {
        return hostName;
    }

    public Long getPodId() {
        return podId;
    }

    public String getState() {
        return state;
    }

    public String getType() {
        return type;
    }
    
    public Boolean getHaHost() {
        return haHost;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }
    
    public EnumSet<HostDetails> getDetails() throws InvalidParameterValueException {
        EnumSet<HostDetails> dv;
        if (viewDetails==null || viewDetails.size() <=0){
            dv = EnumSet.of(HostDetails.all);
        }
        else {
            try {
                ArrayList<HostDetails> dc = new ArrayList<HostDetails>();
                for (String detail: viewDetails){
                    dc.add(HostDetails.valueOf(detail));
                }
                dv = EnumSet.copyOf(dc);
            }
            catch (IllegalArgumentException e){
                throw new InvalidParameterValueException("The details parameter contains a non permitted value. The allowed values are " + EnumSet.allOf(HostDetails.class));
            }
        }
        return dv;
    }
    
    public String getResourceState() {
    	return resourceState;
    }
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
    
    public AsyncJob.Type getInstanceType() {
    	return AsyncJob.Type.Host;
    }

    @Override
    public void execute(){
    	List<? extends Host> result = new ArrayList<Host>();
    	List<? extends Host> hostsWithCapacity = new ArrayList<Host>();
    	 
    	if(getVirtualMachineId() != null){
            Pair<List<? extends Host>, List<? extends Host>> hostsForMigration = _mgr.listHostsForMigrationOfVM(getVirtualMachineId(), this.getStartIndex(), this.getPageSizeVal());
            result = hostsForMigration.first();
            hostsWithCapacity = hostsForMigration.second();
    	}else{
    		result = _mgr.searchForServers(this);
    	}

        ListResponse<HostResponse> response = new ListResponse<HostResponse>();
        List<HostResponse> hostResponses = new ArrayList<HostResponse>();
        for (Host host : result) {
            HostResponse hostResponse = _responseGenerator.createHostResponse(host, getDetails());
            Boolean suitableForMigration = false;
            if(hostsWithCapacity.contains(host)){
                suitableForMigration = true;
            }
            hostResponse.setSuitableForMigration(suitableForMigration);
            hostResponse.setObjectName("host");
            hostResponses.add(hostResponse);
        }

        response.setResponses(hostResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }
}
