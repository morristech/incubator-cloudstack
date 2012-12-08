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
package org.apache.cloudstack.api.user.securitygroup.command;

import java.util.List;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListTaggedResourcesCmd;
import org.apache.cloudstack.api.IdentityMapper;
import org.apache.cloudstack.api.Implementation;
import org.apache.cloudstack.api.Parameter;
import com.cloud.api.response.DomainRouterResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.SecurityGroupResponse;
import com.cloud.api.view.vo.DomainRouterJoinVO;
import com.cloud.api.view.vo.SecurityGroupJoinVO;
import com.cloud.async.AsyncJob;
import com.cloud.network.security.SecurityGroupRules;
import com.cloud.utils.Pair;

@Implementation(description="Lists security groups", responseObject=SecurityGroupResponse.class)
public class ListSecurityGroupsCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = Logger.getLogger(ListSecurityGroupsCmd.class.getName());

    private static final String s_name = "listsecuritygroupsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.SECURITY_GROUP_NAME, type=CommandType.STRING, description="lists security groups by name")
    private String securityGroupName;

    @IdentityMapper(entityTableName="vm_instance")
    @Parameter(name=ApiConstants.VIRTUAL_MACHINE_ID, type=CommandType.LONG, description="lists security groups by virtual machine id")
    private Long virtualMachineId;

    @IdentityMapper(entityTableName="security_group")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, description="list the security group by the id provided")
    private Long id;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public String getSecurityGroupName() {
        return securityGroupName;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public Long getId(){
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
    public void execute(){
        Pair<List<SecurityGroupJoinVO>, Integer> result = _securityGroupService.searchForSecurityGroupRules(this);
        ListResponse<SecurityGroupResponse> response = new ListResponse<SecurityGroupResponse>();
        List<SecurityGroupResponse> routerResponses = _responseGenerator.createSecurityGroupResponses(result.first());
        response.setResponses(routerResponses, result.second());

        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public AsyncJob.Type getInstanceType() {
        return AsyncJob.Type.SecurityGroup;
    }
}