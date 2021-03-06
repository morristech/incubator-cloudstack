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

import java.util.Date;

import com.cloud.api.ApiConstants;
import com.cloud.utils.IdentityProxy;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class UserResponse extends BaseResponse {
    @SerializedName("id") @Param(description="the user ID")
    private IdentityProxy id = new IdentityProxy("user");

    @SerializedName("username") @Param(description="the user name")
    private String username;

    @SerializedName("firstname") @Param(description="the user firstname")
    private String firstname;

    @SerializedName("lastname") @Param(description="the user lastname")
    private String lastname;

    @SerializedName("email") @Param(description="the user email address")
    private String email;

    @SerializedName("created") @Param(description="the date and time the user account was created")
    private Date created;

    @SerializedName("state") @Param(description="the user state")
    private String state;

    @SerializedName("account") @Param(description="the account name of the user")
    private String accountName;

    @SerializedName("accounttype") @Param(description="the account type of the user")
    private Short accountType;

    @SerializedName("domainid") @Param(description="the domain ID of the user")
    private IdentityProxy domainId = new IdentityProxy("domain");

    @SerializedName("domain") @Param(description="the domain name of the user")
    private String domainName;

    @SerializedName("timezone") @Param(description="the timezone user was created in")
    private String timezone;

    @SerializedName("apikey") @Param(description="the api key of the user")
    private String apiKey;

    @SerializedName("secretkey") @Param(description="the secret key of the user")
    private String secretKey;

    @SerializedName("accountid") @Param(description="the account ID of the user")
    private IdentityProxy accountId = new IdentityProxy("account");

    
    public Long getId() {
        return id.getValue();
    }

    public void setId(Long id) {
        this.id.setValue(id);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public Short getAccountType() {
        return accountType;
    }

    public void setAccountType(Short accountType) {
        this.accountType = accountType;
    }

    public Long getDomainId() {
        return domainId.getValue();
    }

    public void setDomainId(Long domainId) {
        this.domainId.setValue(domainId);
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    public Long getAccountId() {
        return accountId.getValue();
    }

    public void setAccountId(Long accountId) {
        this.accountId.setValue(accountId);
    }
}
