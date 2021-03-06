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
package com.cloud.api.response;

import com.cloud.api.ApiConstants;
import com.cloud.utils.IdentityProxy;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class VpnUsersResponse extends BaseResponse implements ControlledEntityResponse{
    @SerializedName(ApiConstants.ID) @Param(description="the vpn userID")
    private IdentityProxy id = new IdentityProxy("vpn_users");

    @SerializedName(ApiConstants.USERNAME) @Param(description="the username of the vpn user")
    private String userName;

    @SerializedName(ApiConstants.ACCOUNT) @Param(description="the account of the remote access vpn")
    private String accountName;

    @SerializedName(ApiConstants.DOMAIN_ID) @Param(description="the domain id of the account of the remote access vpn")
	private IdentityProxy domainId = new IdentityProxy("domain");

    @SerializedName(ApiConstants.DOMAIN) @Param(description="the domain name of the account of the remote access vpn")
	private String domainName;
    
    @SerializedName(ApiConstants.PROJECT_ID) @Param(description="the project id of the vpn")
    private IdentityProxy projectId = new IdentityProxy("projects");
    
    @SerializedName(ApiConstants.PROJECT) @Param(description="the project name of the vpn")
    private String projectName;
    

	public void setId(Long id) {
		this.id.setValue(id);
	}
	
	public void setUserName(String name) {
		this.userName = name;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public void setDomainId(Long domainId) {
		this.domainId.setValue(domainId);
	}

	public void setDomainName(String name) {
		this.domainName = name;		
	}
	
    @Override
    public void setProjectId(Long projectId) {
        this.projectId.setValue(projectId);
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }	

}
