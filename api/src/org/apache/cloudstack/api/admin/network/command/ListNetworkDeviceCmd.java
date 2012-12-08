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
package org.apache.cloudstack.api.admin.network.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Implementation;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.network.ExternalNetworkDeviceManager;
import org.apache.cloudstack.api.admin.network.response.NetworkDeviceResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.server.ManagementService;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.exception.CloudRuntimeException;

@Implementation(description="List network devices", responseObject = NetworkDeviceResponse.class)
public class ListNetworkDeviceCmd extends BaseListCmd {
	public static final Logger s_logger = Logger.getLogger(ListNetworkDeviceCmd.class);
	private static final String s_name = "listnetworkdevice";
	
    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

	@Parameter(name = ApiConstants.NETWORK_DEVICE_TYPE, type = CommandType.STRING, description = "Network device type, now supports ExternalDhcp, PxeServer, NetscalerMPXLoadBalancer, NetscalerVPXLoadBalancer, NetscalerSDXLoadBalancer, F5BigIpLoadBalancer, JuniperSRXFirewall")
    private String type;
	
	@Parameter(name = ApiConstants.NETWORK_DEVICE_PARAMETER_LIST, type = CommandType.MAP, description = "parameters for network device")
    private Map paramList;
    
	public String getDeviceType() {
		return type;
	}
	
	public Map getParamList() {
		return paramList;
	}
	
	@Override
	public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
			ResourceAllocationException {
		try {
			ExternalNetworkDeviceManager nwDeviceMgr;
			ComponentLocator locator = ComponentLocator.getLocator(ManagementService.Name);
			nwDeviceMgr = locator.getManager(ExternalNetworkDeviceManager.class);
			List<Host> devices = nwDeviceMgr.listNetworkDevice(this);
			List<NetworkDeviceResponse> nwdeviceResponses = new ArrayList<NetworkDeviceResponse>();
			ListResponse<NetworkDeviceResponse> listResponse = new ListResponse<NetworkDeviceResponse>();
			for (Host d : devices) {
				NetworkDeviceResponse response = nwDeviceMgr.getApiResponse(d);
				response.setObjectName("networkdevice");
				response.setResponseName(getCommandName());
				nwdeviceResponses.add(response);
			}
			
	        listResponse.setResponses(nwdeviceResponses);
	        listResponse.setResponseName(getCommandName());
	        this.setResponseObject(listResponse);
		} catch (InvalidParameterValueException ipve) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, ipve.getMessage());
		} catch (CloudRuntimeException cre) {
			throw new ServerApiException(BaseCmd.INTERNAL_ERROR, cre.getMessage());
		}
	}

	@Override
	public String getCommandName() {
		return s_name;
	}

}