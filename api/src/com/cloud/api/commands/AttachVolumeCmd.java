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
import com.cloud.api.response.VolumeResponse;
import com.cloud.async.AsyncJob;
import com.cloud.event.EventTypes;
import com.cloud.storage.Volume;
import com.cloud.user.Account;
import com.cloud.user.UserContext;

@Implementation(description="Attaches a disk volume to a virtual machine.", responseObject=VolumeResponse.class)
public class AttachVolumeCmd extends BaseAsyncCmd {
	public static final Logger s_logger = Logger.getLogger(AttachVolumeCmd.class.getName());
    private static final String s_name = "attachvolumeresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.DEVICE_ID, type=CommandType.LONG, description="the ID of the device to map the volume to within the guest OS. " +
    																"If no deviceId is passed in, the next available deviceId will be chosen. " +
    																"Possible values for a Linux OS are:" +
    																"* 1 - /dev/xvdb" +
    																"* 2 - /dev/xvdc" +
    																"* 4 - /dev/xvde" +
    																"* 5 - /dev/xvdf" +
    																"* 6 - /dev/xvdg" +
    																"* 7 - /dev/xvdh" +
    																"* 8 - /dev/xvdi" +
    																"* 9 - /dev/xvdj")
    private Long deviceId;

    @IdentityMapper(entityTableName="volumes")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, required=true, description="the ID of the disk volume")
    private Long id;

    @IdentityMapper(entityTableName="vm_instance")
    @Parameter(name=ApiConstants.VIRTUAL_MACHINE_ID, type=CommandType.LONG, required=true, description="	the ID of the virtual machine")
    private Long virtualMachineId;


    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getDeviceId() {
        return deviceId;
    }

    public Long getId() {
        return id;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }


    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
    
    public AsyncJob.Type getInstanceType() {
    	return AsyncJob.Type.Volume;
    }
    
    public Long getInstanceId() {
    	return getId();
    }

    @Override
    public long getEntityOwnerId() {
        Volume volume = _responseGenerator.findVolumeById(getId());
        if (volume == null) {
            return Account.ACCOUNT_ID_SYSTEM; // bad id given, parent this command to SYSTEM so ERROR events are tracked
        }
        return volume.getAccountId();
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VOLUME_ATTACH;
    }

    @Override
    public String getEventDescription() {
        return  "attaching volume: " + getId() + " to vm: " + getVirtualMachineId();
    }
    
    @Override
    public void execute(){
        UserContext.current().setEventDetails("Volume Id: "+getId()+" VmId: "+getVirtualMachineId());
        Volume result = _userVmService.attachVolumeToVM(this);
        if (result != null) {
            VolumeResponse response = _responseGenerator.createVolumeResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to attach volume");
        }
    }
}
