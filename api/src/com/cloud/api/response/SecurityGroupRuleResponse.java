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

public class SecurityGroupRuleResponse extends BaseResponse {
    @SerializedName("ruleid") @Param(description="the id of the security group rule")
    private IdentityProxy ruleId = new IdentityProxy("security_group_rule");

    @SerializedName("protocol") @Param(description="the protocol of the security group rule")
    private String protocol;

    @SerializedName(ApiConstants.ICMP_TYPE) @Param(description="the type of the ICMP message response")
    private Integer icmpType;

    @SerializedName(ApiConstants.ICMP_CODE) @Param(description="the code for the ICMP message response")
    private Integer icmpCode;

    @SerializedName(ApiConstants.START_PORT) @Param(description="the starting IP of the security group rule")
    private Integer startPort;

    @SerializedName(ApiConstants.END_PORT) @Param(description="the ending IP of the security group rule ")
    private Integer endPort;

    @SerializedName(ApiConstants.SECURITY_GROUP_NAME) @Param(description="security group name")
    private String securityGroupName;

    @SerializedName(ApiConstants.ACCOUNT) @Param(description="account owning the security group rule")
    private String accountName;

    @SerializedName(ApiConstants.CIDR) @Param(description="the CIDR notation for the base IP address of the security group rule")
    private String cidr;

    public Long getRuleId() {
        return ruleId.getValue();
    }

    public void setRuleId(Long ruleId) {
        this.ruleId.setValue(ruleId);
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Integer getIcmpType() {
        return icmpType;
    }

    public void setIcmpType(Integer icmpType) {
        this.icmpType = icmpType;
    }

    public Integer getIcmpCode() {
        return icmpCode;
    }

    public void setIcmpCode(Integer icmpCode) {
        this.icmpCode = icmpCode;
    }

    public Integer getStartPort() {
        return startPort;
    }

    public void setStartPort(Integer startPort) {
        this.startPort = startPort;
    }

    public Integer getEndPort() {
        return endPort;
    }

    public void setEndPort(Integer endPort) {
        this.endPort = endPort;
    }

    public String getSecurityGroupName() {
        return securityGroupName;
    }

    public void setSecurityGroupName(String securityGroupName) {
        this.securityGroupName = securityGroupName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }
}
