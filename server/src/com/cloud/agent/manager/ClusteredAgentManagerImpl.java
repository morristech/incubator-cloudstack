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
package com.cloud.agent.manager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CancelCommand;
import com.cloud.agent.api.ChangeAgentCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.TransferAgentCommand;
import com.cloud.agent.transport.Request;
import com.cloud.agent.transport.Request.Version;
import com.cloud.agent.transport.Response;
import com.cloud.cluster.ClusterManager;
import com.cloud.cluster.ClusterManagerListener;
import com.cloud.cluster.ClusteredAgentRebalanceService;
import com.cloud.cluster.ManagementServerHost;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.StackMaid;
import com.cloud.cluster.agentlb.AgentLoadBalancerPlanner;
import com.cloud.cluster.agentlb.HostTransferMapVO;
import com.cloud.cluster.agentlb.HostTransferMapVO.HostTransferState;
import com.cloud.cluster.agentlb.dao.HostTransferMapDao;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.configuration.Config;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.Status.Event;
import com.cloud.resource.ServerResource;
import com.cloud.storage.resource.DummySecondaryStorageResource;
import com.cloud.utils.DateUtil;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.Adapters;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.component.Inject;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.SearchCriteria2;
import com.cloud.utils.db.SearchCriteriaService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.nio.Link;
import com.cloud.utils.nio.Task;

@Local(value = { AgentManager.class, ClusteredAgentRebalanceService.class })
public class ClusteredAgentManagerImpl extends AgentManagerImpl implements ClusterManagerListener, ClusteredAgentRebalanceService {
    final static Logger s_logger = Logger.getLogger(ClusteredAgentManagerImpl.class);
    private static final ScheduledExecutorService s_transferExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Cluster-AgentTransferExecutor"));
    private final long rebalanceTimeOut = 300000; // 5 mins - after this time remove the agent from the transfer list 

    public final static long STARTUP_DELAY = 5000;
    public final static long SCAN_INTERVAL = 90000; // 90 seconds, it takes 60 sec for xenserver to fail login
    public final static int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 5; // 5 seconds
    public long _loadSize = 100;
    protected Set<Long> _agentToTransferIds = new HashSet<Long>();  

    @Inject
    protected ClusterManager _clusterMgr = null;

    protected HashMap<String, SocketChannel> _peers;
    protected HashMap<String, SSLEngine> _sslEngines;
    private final Timer _timer = new Timer("ClusteredAgentManager Timer");

    @Inject
    protected ManagementServerHostDao _mshostDao;
    @Inject
    protected HostTransferMapDao _hostTransferDao;
    
    @Inject(adapter = AgentLoadBalancerPlanner.class)
    protected Adapters<AgentLoadBalancerPlanner> _lbPlanners;
    
    @Inject
    protected AgentManager _agentMgr;

    protected ClusteredAgentManagerImpl() {
        super();
    }

    @Override
    public boolean configure(String name, Map<String, Object> xmlParams) throws ConfigurationException {
        _peers = new HashMap<String, SocketChannel>(7);
        _sslEngines = new HashMap<String, SSLEngine>(7);
        _nodeId = _clusterMgr.getManagementNodeId();
        
        s_logger.info("Configuring ClusterAgentManagerImpl. management server node id(msid): " + _nodeId);

        ConfigurationDao configDao = ComponentLocator.getCurrentLocator().getDao(ConfigurationDao.class);
        Map<String, String> params = configDao.getConfiguration(xmlParams);
        String value = params.get(Config.DirectAgentLoadSize.key());
        _loadSize = NumbersUtil.parseInt(value, 16);

        ClusteredAgentAttache.initialize(this);

        _clusterMgr.registerListener(this);
        
        return super.configure(name, xmlParams);
    }

    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        _timer.schedule(new DirectAgentScanTimerTask(), STARTUP_DELAY, SCAN_INTERVAL);

        // schedule transfer scan executor - if agent LB is enabled
        if (_clusterMgr.isAgentRebalanceEnabled()) {
            s_transferExecutor.scheduleAtFixedRate(getTransferScanTask(), 60000, ClusteredAgentRebalanceService.DEFAULT_TRANSFER_CHECK_INTERVAL,
                    TimeUnit.MILLISECONDS);
        }

        return true;
    }

    private void runDirectAgentScanTimerTask() {
        scanDirectAgentToLoad();
    }

    private void scanDirectAgentToLoad() {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Begin scanning directly connected hosts");
        }

        // for agents that are self-managed, threshold to be considered as disconnected is 3 ping intervals
        long cutSeconds = (System.currentTimeMillis() >> 10) - (_pingInterval * 3);
        List<HostVO> hosts = _hostDao.findAndUpdateDirectAgentToLoad(cutSeconds, _loadSize, _nodeId);
        List<HostVO> appliances = _hostDao.findAndUpdateApplianceToLoad(cutSeconds, _nodeId);
        hosts.addAll(appliances);
        
        if (hosts != null && hosts.size() > 0) {
            s_logger.debug("Found " + hosts.size() + " unmanaged direct hosts, processing connect for them...");
            for (HostVO host : hosts) {
                try {
                    AgentAttache agentattache = findAttache(host.getId());
                    if (agentattache != null) {
                        // already loaded, skip
                        if (agentattache.forForward()) {
                            if (s_logger.isInfoEnabled()) {
                                s_logger.info(host + " is detected down, but we have a forward attache running, disconnect this one before launching the host");
                            }
                            removeAgent(agentattache, Status.Disconnected);
                        } else {
                            continue;
                        }
                    }

                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Loading directly connected host " + host.getId() + "(" + host.getName() + ")");
                    }
                    loadDirectlyConnectedHost(host, false);
                } catch (Throwable e) {
                    s_logger.warn(" can not load directly connected host " + host.getId() + "(" + host.getName() + ") due to ",e);
                }
            }
        }

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("End scanning directly connected hosts");
        }
    }

    private class DirectAgentScanTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                runDirectAgentScanTimerTask();
            } catch (Throwable e) {
                s_logger.error("Unexpected exception " + e.getMessage(), e);
            }
        }
    }

    @Override
    public Task create(Task.Type type, Link link, byte[] data) {
        return new ClusteredAgentHandler(type, link, data);
    }

    protected AgentAttache createAttache(long id) {
        s_logger.debug("create forwarding ClusteredAgentAttache for " + id);
        final AgentAttache attache = new ClusteredAgentAttache(this, id);
        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.get(id);
            _agents.put(id, attache);
        }
        if (old != null) {
            old.disconnect(Status.Removed);
        }
        return attache;
    }

    @Override
    protected AgentAttache createAttacheForConnect(HostVO host, Link link) {
        s_logger.debug("create ClusteredAgentAttache for " + host.getId());
        final AgentAttache attache = new ClusteredAgentAttache(this, host.getId(), link, host.isInMaintenanceStates());
        link.attach(attache);
        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.get(host.getId());
            _agents.put(host.getId(), attache);
        }
        if (old != null) {
            old.disconnect(Status.Removed);
        }
        return attache;
    }

    @Override
    protected AgentAttache createAttacheForDirectConnect(HostVO host, ServerResource resource) {
        if (resource instanceof DummySecondaryStorageResource) {
            return new DummyAttache(this, host.getId(), false);
        }
        s_logger.debug("create ClusteredDirectAgentAttache for " + host.getId());
        final DirectAgentAttache attache = new ClusteredDirectAgentAttache(this, host.getId(), _nodeId, resource, host.isInMaintenanceStates(), this);
        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.get(host.getId());
            _agents.put(host.getId(), attache);
        }
        if (old != null) {
            old.disconnect(Status.Removed);
        }
        return attache;
    }

    @Override
    protected boolean handleDisconnectWithoutInvestigation(AgentAttache attache, Status.Event event, boolean transitState) {
        return handleDisconnect(attache, event, false, true);
    }
    
    @Override
    protected boolean handleDisconnectWithInvestigation(AgentAttache attache, Status.Event event) {
        return handleDisconnect(attache, event, true, true);
    }
    
    protected boolean handleDisconnect(AgentAttache agent, Status.Event event, boolean investigate, boolean broadcast) {
        boolean res;
        if (!investigate) {
            res = super.handleDisconnectWithoutInvestigation(agent, event, true);
        } else {
            res = super.handleDisconnectWithInvestigation(agent, event);
        }

		if (res) {
			if (broadcast) {
				notifyNodesInCluster(agent);
			}
			return true;
		} else {
			return false;
		}
    }

    @Override
    public boolean executeUserRequest(long hostId, Event event) throws AgentUnavailableException {
        if (event == Event.AgentDisconnected) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Received agent disconnect event for host " + hostId);
            }
            AgentAttache attache = findAttache(hostId);
            if (attache != null) {
                //don't process disconnect if the host is being rebalanced
                if (_clusterMgr.isAgentRebalanceEnabled()) {
                    HostTransferMapVO transferVO = _hostTransferDao.findById(hostId);
                    if (transferVO != null) {
                        if (transferVO.getFutureOwner() == _nodeId && transferVO.getState() == HostTransferState.TransferStarted) {
                            s_logger.debug("Not processing " + Event.AgentDisconnected + " event for the host id="
                                    + hostId +" as the host is being connected to " + _nodeId);
                            return true;
                        }
                    }
                }
                
                //don't process disconnect if the disconnect came for the host via delayed cluster notification,
                //but the host has already reconnected to the current management server
                if (!attache.forForward()) {
                    s_logger.debug("Not processing " + Event.AgentDisconnected + " event for the host id="
                            + hostId +" as the host is directly connected to the current management server " + _nodeId);
                    return true;
                }
                
                return super.handleDisconnectWithoutInvestigation(attache, Event.AgentDisconnected, false);
            }

            return true;
        } else {
            return super.executeUserRequest(hostId, event);
        }
    }

    @Override
    public boolean reconnect(final long hostId) {
        Boolean result;
        try {
	        result = _clusterMgr.propagateAgentEvent(hostId, Event.ShutdownRequested);
	        if (result != null) {
	            return result;
	        }
        } catch (AgentUnavailableException e) {
	        s_logger.debug("cannot propagate agent reconnect because agent is not available", e);
	        return false;
        }
        
        return super.reconnect(hostId);
    }

    public void notifyNodesInCluster(AgentAttache attache) {
        s_logger.debug("Notifying other nodes of to disconnect");
        Command[] cmds = new Command[] { new ChangeAgentCommand(attache.getId(), Event.AgentDisconnected) };
        _clusterMgr.broadcast(attache.getId(), cmds);
    }

    protected static void logT(byte[] bytes, final String msg) {
        s_logger.trace("Seq " + Request.getAgentId(bytes) + "-" + Request.getSequence(bytes) + ": MgmtId " + Request.getManagementServerId(bytes) + ": "
                + (Request.isRequest(bytes) ? "Req: " : "Resp: ") + msg);
    }

    protected static void logD(byte[] bytes, final String msg) {
        s_logger.debug("Seq " + Request.getAgentId(bytes) + "-" + Request.getSequence(bytes) + ": MgmtId " + Request.getManagementServerId(bytes) + ": "
                + (Request.isRequest(bytes) ? "Req: " : "Resp: ") + msg);
    }

    protected static void logI(byte[] bytes, final String msg) {
        s_logger.info("Seq " + Request.getAgentId(bytes) + "-" + Request.getSequence(bytes) + ": MgmtId " + Request.getManagementServerId(bytes) + ": "
                + (Request.isRequest(bytes) ? "Req: " : "Resp: ") + msg);
    }

    public boolean routeToPeer(String peer, byte[] bytes) {
        int i = 0;
        SocketChannel ch = null;
        SSLEngine sslEngine = null;
        while (i++ < 5) {
            ch = connectToPeer(peer, ch);
            if (ch == null) {
                try {
                    logD(bytes, "Unable to route to peer: " + Request.parse(bytes).toString());
                } catch (Exception e) {
                }
                return false;
            }
            sslEngine = getSSLEngine(peer);
            if (sslEngine == null) {
                logD(bytes, "Unable to get SSLEngine of peer: " + peer);
                return false;
            }
            try {
                if (s_logger.isDebugEnabled()) {
                    logD(bytes, "Routing to peer");
                }
                Link.write(ch, new ByteBuffer[] { ByteBuffer.wrap(bytes) }, sslEngine);
                return true;
            } catch (IOException e) {
                try {
                    logI(bytes, "Unable to route to peer: " + Request.parse(bytes).toString() + " due to " + e.getMessage());
                } catch (Exception ex) {
                }
            }
        }
        return false;
    }

    public String findPeer(long hostId) {
        return _clusterMgr.getPeerName(hostId);
    }
    
    public SSLEngine getSSLEngine(String peerName) {
        return _sslEngines.get(peerName);
    }

    public void cancel(String peerName, long hostId, long sequence, String reason) {
        CancelCommand cancel = new CancelCommand(sequence, reason);
        Request req = new Request(hostId, _nodeId, cancel, true);
        req.setControl(true);
        routeToPeer(peerName, req.getBytes());
    }

    public void closePeer(String peerName) {
        synchronized (_peers) {
            SocketChannel ch = _peers.get(peerName);
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException e) {
                    s_logger.warn("Unable to close peer socket connection to " + peerName);
                }
            }
            _peers.remove(peerName);
            _sslEngines.remove(peerName);
        }
    }

    public SocketChannel connectToPeer(String peerName, SocketChannel prevCh) {
        synchronized (_peers) {
            SocketChannel ch = _peers.get(peerName);
            SSLEngine sslEngine = null;
            if (prevCh != null) {
                try {
                    prevCh.close();
                } catch (Exception e) {
                }
            }
            if (ch == null || ch == prevCh) {
                ManagementServerHostVO ms = _clusterMgr.getPeer(peerName);
                if (ms == null) {
                    s_logger.info("Unable to find peer: " + peerName);
                    return null;
                }
                String ip = ms.getServiceIP();
                InetAddress addr;
                try {
                    addr = InetAddress.getByName(ip);
                } catch (UnknownHostException e) {
                    throw new CloudRuntimeException("Unable to resolve " + ip);
                }
                try {
                    ch = SocketChannel.open(new InetSocketAddress(addr, _port));
                    ch.configureBlocking(true); // make sure we are working at blocking mode
                    ch.socket().setKeepAlive(true);
                    ch.socket().setSoTimeout(60 * 1000);
                    try {
                        SSLContext sslContext = Link.initSSLContext(true);
                        sslEngine = sslContext.createSSLEngine(ip, _port);
                        sslEngine.setUseClientMode(true);

                        Link.doHandshake(ch, sslEngine, true);
                        s_logger.info("SSL: Handshake done");
                    } catch (Exception e) {
                        throw new IOException("SSL: Fail to init SSL! " + e);
                    }
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Connection to peer opened: " + peerName + ", ip: " + ip);
                    }
                    _peers.put(peerName, ch);
                    _sslEngines.put(peerName, sslEngine);
                } catch (IOException e) {
                    s_logger.warn("Unable to connect to peer management server: " + peerName + ", ip: " + ip + " due to " + e.getMessage(), e);
                    return null;
                }
            }

            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Found open channel for peer: " + peerName);
            }
            return ch;
        }
    }

    public SocketChannel connectToPeer(long hostId, SocketChannel prevCh) {
        String peerName = _clusterMgr.getPeerName(hostId);
        if (peerName == null) {
            return null;
        }

        return connectToPeer(peerName, prevCh);
    }

    @Override
    protected AgentAttache getAttache(final Long hostId) throws AgentUnavailableException {
        assert (hostId != null) : "Who didn't check their id value?";
        HostVO host = _hostDao.findById(hostId);
        if (host == null) {
            throw new AgentUnavailableException("Can't find the host ", hostId);
        }

        AgentAttache agent = findAttache(hostId);
        if (agent == null) {
            if (host.getStatus() == Status.Up && (host.getManagementServerId() != null && host.getManagementServerId() != _nodeId)) {
                agent = createAttache(hostId);
            }
        }
        if (agent == null) {
        	AgentUnavailableException ex = new AgentUnavailableException("Host with specified id is not in the right state: " + host.getStatus(), hostId);
            ex.addProxyObject(host, hostId, "hostId");
            throw ex;
        }

        return agent;
    }

    @Override
    public boolean stop() {
        if (_peers != null) {
            for (SocketChannel ch : _peers.values()) {
                try {
                    s_logger.info("Closing: " + ch.toString());
                    ch.close();
                } catch (IOException e) {
                }
            }
        }
        _timer.cancel();
        
        //cancel all transfer tasks
        s_transferExecutor.shutdownNow();
        cleanupTransferMap(_nodeId);
        
        return super.stop();
    }

    @Override
    public void startDirectlyConnectedHosts() {
        // override and let it be dummy for purpose, we will scan and load direct agents periodically.
        // We may also pickup agents that have been left over from other crashed management server
    }

    public class ClusteredAgentHandler extends AgentHandler {

        public ClusteredAgentHandler(Task.Type type, Link link, byte[] data) {
            super(type, link, data);
        }

        @Override
        protected void doTask(final Task task) throws Exception {
            Transaction txn = Transaction.open(Transaction.CLOUD_DB);
            try {
                if (task.getType() != Task.Type.DATA) {
                    super.doTask(task);
                    return;
                }

                final byte[] data = task.getData();
                Version ver = Request.getVersion(data);
                if (ver.ordinal() != Version.v1.ordinal() && ver.ordinal() != Version.v3.ordinal()) {
                    s_logger.warn("Wrong version for clustered agent request");
                    super.doTask(task);
                    return;
                }

                long hostId = Request.getAgentId(data);
                Link link = task.getLink();

                if (Request.fromServer(data)) {

                    AgentAttache agent = findAttache(hostId);

                    if (Request.isControl(data)) {
                        if (agent == null) {
                            logD(data, "No attache to process cancellation");
                            return;
                        }
                        Request req = Request.parse(data);
                        Command[] cmds = req.getCommands();
                        CancelCommand cancel = (CancelCommand) cmds[0];
                        if (s_logger.isDebugEnabled()) {
                            logD(data, "Cancel request received");
                        }
                        agent.cancel(cancel.getSequence());
                        final Long current = agent._currentSequence;
                        //if the request is the current request, always have to trigger sending next request in sequence,
                        //otherwise the agent queue will be blocked
                        if (req.executeInSequence() && (current != null && current == Request.getSequence(data))) {
                            agent.sendNext(Request.getSequence(data));
                        }
                        return;
                    }

                    try {
                        if (agent == null || agent.isClosed()) {
                            throw new AgentUnavailableException("Unable to route to agent ", hostId);
                        }

                        if (Request.isRequest(data) && Request.requiresSequentialExecution(data)) {
                            // route it to the agent.
                            // But we have the serialize the control commands here so we have
                            // to deserialize this and send it through the agent attache.
                            Request req = Request.parse(data);
                            agent.send(req, null);
                            return;
                        } else {
                            if (agent instanceof Routable) {
                                Routable cluster = (Routable) agent;
                                cluster.routeToAgent(data);
                            } else {
                                agent.send(Request.parse(data));
                            }
                            return;
                        }
                    } catch (AgentUnavailableException e) {
                        logD(data, e.getMessage());
                        cancel(Long.toString(Request.getManagementServerId(data)), hostId, Request.getSequence(data), e.getMessage());
                    }
                } else {

                    long mgmtId = Request.getManagementServerId(data);
                    if (mgmtId != -1 && mgmtId != _nodeId) {
                        routeToPeer(Long.toString(mgmtId), data);
                        if (Request.requiresSequentialExecution(data)) {
                            AgentAttache attache = (AgentAttache) link.attachment();
                            if (attache != null) {
                                attache.sendNext(Request.getSequence(data));
                            } else if (s_logger.isDebugEnabled()) {
                                logD(data, "No attache to process " + Request.parse(data).toString());
                            }
                        }
                        return;
                    } else {
                        if (Request.isRequest(data)) {
                            super.doTask(task);
                        } else {
                            // received an answer.
                            final Response response = Response.parse(data);
                            AgentAttache attache = findAttache(response.getAgentId());
                            if (attache == null) {
                                s_logger.info("SeqA " + response.getAgentId() + "-" + response.getSequence() + "Unable to find attache to forward " + response.toString());
                                return;
                            }
                            if (!attache.processAnswers(response.getSequence(), response)) {
                                s_logger.info("SeqA " + attache.getId() + "-" + response.getSequence() + ": Response is not processed: " + response.toString());
                            }
                        }
                        return;
                    }
                }
            } finally {
                txn.close();
            }
        }
    }

    @Override
    public void onManagementNodeJoined(List<ManagementServerHostVO> nodeList, long selfNodeId) {
    }

    @Override
    public void onManagementNodeLeft(List<ManagementServerHostVO> nodeList, long selfNodeId) {
        for (ManagementServerHostVO vo : nodeList) {
            s_logger.info("Marking hosts as disconnected on Management server" + vo.getMsid());
            long lastPing = (System.currentTimeMillis() >> 10) - _pingTimeout;
            _hostDao.markHostsAsDisconnected(vo.getMsid(), lastPing);
            s_logger.info("Deleting entries from op_host_transfer table for Management server " + vo.getMsid());
            cleanupTransferMap(vo.getMsid());
        }
    }

    @Override
    public void onManagementNodeIsolated() {
    }

    @Override
    public void removeAgent(AgentAttache attache, Status nextState) {
        if (attache == null) {
            return;
        }

        super.removeAgent(attache, nextState);
    }

    @Override
    public boolean executeRebalanceRequest(long agentId, long currentOwnerId, long futureOwnerId, Event event) throws AgentUnavailableException, OperationTimedoutException {
    	boolean result = false;
        if (event == Event.RequestAgentRebalance) {
            return setToWaitForRebalance(agentId, currentOwnerId, futureOwnerId);
        } else if (event == Event.StartAgentRebalance) {
            try {
            	result = rebalanceHost(agentId, currentOwnerId, futureOwnerId);
            } catch (Exception e) {
                s_logger.warn("Unable to rebalance host id=" + agentId, e);
            }
        }
        return result;
    }
    
    @Override
    public void scheduleRebalanceAgents() {
        _timer.schedule(new AgentLoadBalancerTask(), 30000);
    }

    public class AgentLoadBalancerTask extends TimerTask {
        protected volatile boolean cancelled = false;

        public AgentLoadBalancerTask() {
            s_logger.debug("Agent load balancer task created");
        }

        @Override
        public synchronized boolean cancel() {
            if (!cancelled) {
                cancelled = true;
                s_logger.debug("Agent load balancer task cancelled");
                return super.cancel();
            }
            return true;
        }

        @Override
        public synchronized void run() {
        	try {
	            if (!cancelled) {
	                startRebalanceAgents();
	                if (s_logger.isInfoEnabled()) {
	                    s_logger.info("The agent load balancer task is now being cancelled");
	                }
	                cancelled = true;
	            }
        	} catch(Throwable e) {
        		s_logger.error("Unexpected exception " + e.toString(), e);
        	}
        }
    }
   
    public void startRebalanceAgents() {
        s_logger.debug("Management server " + _nodeId + " is asking other peers to rebalance their agents");
        List<ManagementServerHostVO> allMS = _mshostDao.listBy(ManagementServerHost.State.Up);
        SearchCriteriaService<HostVO, HostVO> sc = SearchCriteria2.create(HostVO.class);
        sc.addAnd(sc.getEntity().getManagementServerId(), Op.NNULL);
        sc.addAnd(sc.getEntity().getType(), Op.EQ, Host.Type.Routing);
        List<HostVO> allManagedAgents = sc.list();

        int avLoad = 0;

        if (!allManagedAgents.isEmpty() && !allMS.isEmpty()) {
            avLoad = allManagedAgents.size() / allMS.size();
        } else {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("There are no hosts to rebalance in the system. Current number of active management server nodes in the system is " + allMS.size() + "; number of managed agents is " + allManagedAgents.size());
            }
            return;
        }
        
        if (avLoad == 0L) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("As calculated average load is less than 1, rounding it to 1");
            }
            avLoad = 1;
        }

        for (ManagementServerHostVO node : allMS) {
            if (node.getMsid() != _nodeId) {
                
                List<HostVO> hostsToRebalance = new ArrayList<HostVO>();
                for (AgentLoadBalancerPlanner lbPlanner : _lbPlanners) {
                    hostsToRebalance = lbPlanner.getHostsToRebalance(node.getMsid(), avLoad);
                    if (hostsToRebalance != null && !hostsToRebalance.isEmpty()) {
                        break;
                    } else {
                        s_logger.debug("Agent load balancer planner " + lbPlanner.getName() + " found no hosts to be rebalanced from management server " + node.getMsid());
                    }
                }

                
                if (hostsToRebalance != null && !hostsToRebalance.isEmpty()) {
                    s_logger.debug("Found " + hostsToRebalance.size() + " hosts to rebalance from management server " + node.getMsid());
                    for (HostVO host : hostsToRebalance) {
                        long hostId = host.getId();
                        s_logger.debug("Asking management server " + node.getMsid() + " to give away host id=" + hostId);
                        boolean result = true;
                        
                        if (_hostTransferDao.findById(hostId) != null) {
                            s_logger.warn("Somebody else is already rebalancing host id: " + hostId);
                            continue;
                        }

                        HostTransferMapVO transfer = null; 
                        try {
                            transfer = _hostTransferDao.startAgentTransfering(hostId, node.getMsid(), _nodeId);
                            Answer[] answer = sendRebalanceCommand(node.getMsid(), hostId, node.getMsid(), _nodeId, Event.RequestAgentRebalance);
                            if (answer == null) {
                                s_logger.warn("Failed to get host id=" + hostId + " from management server " + node.getMsid());
                                result = false;
                            }
                        } catch (Exception ex) {
                            s_logger.warn("Failed to get host id=" + hostId + " from management server " + node.getMsid(), ex);
                            result = false;
                        } finally {
                            if (transfer != null) {
                                HostTransferMapVO transferState = _hostTransferDao.findByIdAndFutureOwnerId(transfer.getId(), _nodeId);
                                if (!result && transferState != null && transferState.getState() == HostTransferState.TransferRequested) {
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Removing mapping from op_host_transfer as it failed to be set to transfer mode");
                                    }
                                    //just remove the mapping (if exists) as nothing was done on the peer management server yet
                                    _hostTransferDao.remove(transfer.getId());
                                }
                            }
                        }
                    }
                } else {
                    s_logger.debug("Found no hosts to rebalance from the management server " + node.getMsid());
                }
            }
        }
    }

    private Answer[] sendRebalanceCommand(long peer, long agentId, long currentOwnerId, long futureOwnerId, Event event) {
        TransferAgentCommand transfer = new TransferAgentCommand(agentId, currentOwnerId, futureOwnerId, event);
        Commands commands = new Commands(OnError.Stop);
        commands.addCommand(transfer);

        Command[] cmds = commands.toCommands();

        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Forwarding " + cmds[0].toString() + " to " + peer);
            }
            String peerName = Long.toString(peer);
            Answer[] answers = _clusterMgr.execute(peerName, agentId, cmds, true);
            return answers;
        } catch (Exception e) {
            s_logger.warn("Caught exception while talking to " + currentOwnerId, e);
            return null;
        }
    }

    private Runnable getTransferScanTask() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("Clustered agent transfer scan check, management server id:" + _nodeId);
                    }
                    synchronized (_agentToTransferIds) {
                        if (_agentToTransferIds.size() > 0) {
                            s_logger.debug("Found " + _agentToTransferIds.size() + " agents to transfer");
                            //for (Long hostId : _agentToTransferIds) {
                            for (Iterator<Long> iterator = _agentToTransferIds.iterator(); iterator.hasNext();) {
                                Long hostId = iterator.next();
                                AgentAttache attache = findAttache(hostId);
                                
                                // if the thread:
                                // 1) timed out waiting for the host to reconnect
                                // 2) recipient management server is not active any more
                                // 3) if the management server doesn't own the host any more
                                // remove the host from re-balance list and delete from op_host_transfer DB
                                // no need to do anything with the real attache as we haven't modified it yet
                                Date cutTime = DateUtil.currentGMTTime();
                                HostTransferMapVO transferMap = _hostTransferDao.findActiveHostTransferMapByHostId(hostId, new Date(cutTime.getTime() - rebalanceTimeOut));

                                if (transferMap == null) {
                                    s_logger.debug("Timed out waiting for the host id=" + hostId + " to be ready to transfer, skipping rebalance for the host");
                                    iterator.remove();
                                    _hostTransferDao.completeAgentTransfer(hostId);
                                    continue;
                                }
                                
                                if (transferMap.getInitialOwner() != _nodeId || attache == null || attache.forForward()) {
                                    s_logger.debug("Management server " + _nodeId + " doesn't own host id=" + hostId + " any more, skipping rebalance for the host");
                                    iterator.remove();
                                    _hostTransferDao.completeAgentTransfer(hostId);
                                    continue;
                                }
   
                                ManagementServerHostVO ms = _mshostDao.findByMsid(transferMap.getFutureOwner());
                                if (ms != null && ms.getState() != ManagementServerHost.State.Up) {
                                    s_logger.debug("Can't transfer host " + hostId + " as it's future owner is not in UP state: " + ms + ", skipping rebalance for the host");
                                    iterator.remove();
                                    _hostTransferDao.completeAgentTransfer(hostId);
                                    continue;
                                } 
                                
                                if (attache.getQueueSize() == 0 && attache.getNonRecurringListenersSize() == 0) {
                                    iterator.remove();
                                    try {
                                        _executor.execute(new RebalanceTask(hostId, transferMap.getInitialOwner(), transferMap.getFutureOwner()));
                                    } catch (RejectedExecutionException ex) {
                                        s_logger.warn("Failed to submit rebalance task for host id=" + hostId + "; postponing the execution");
                                        continue;
                                    }
                                    
                                } else {
                                    s_logger.debug("Agent " + hostId + " can't be transfered yet as its request queue size is " + attache.getQueueSize() + " and listener queue size is " + attache.getNonRecurringListenersSize()); 
                                }
                            }
                        } else {
                            if (s_logger.isTraceEnabled()) {
                                s_logger.trace("Found no agents to be transfered by the management server " + _nodeId);
                            }
                        }
                    }

                } catch (Throwable e) {
                    s_logger.error("Problem with the clustered agent transfer scan check!", e);
                }
            }
        };
    }
    
    
    private boolean setToWaitForRebalance(final long hostId, long currentOwnerId, long futureOwnerId) {
        s_logger.debug("Adding agent " + hostId + " to the list of agents to transfer");
        synchronized (_agentToTransferIds) {
            return  _agentToTransferIds.add(hostId);
        }
    }
    
    
    protected boolean rebalanceHost(final long hostId, long currentOwnerId, long futureOwnerId) throws AgentUnavailableException{

        boolean result = true;
        if (currentOwnerId == _nodeId) {
            if (!startRebalance(hostId)) {
                s_logger.debug("Failed to start agent rebalancing");
                finishRebalance(hostId, futureOwnerId, Event.RebalanceFailed);
                return false;
            }
            try {
                Answer[] answer = sendRebalanceCommand(futureOwnerId, hostId, currentOwnerId, futureOwnerId, Event.StartAgentRebalance);
                if (answer == null || !answer[0].getResult()) {
                    result = false;
                }

            } catch (Exception ex) {
                s_logger.warn("Host " + hostId + " failed to connect to the management server " + futureOwnerId + " as a part of rebalance process", ex);
                result = false;
            }
            
            if (result) {
                s_logger.debug("Successfully transfered host id=" + hostId + " to management server " + futureOwnerId);
                finishRebalance(hostId, futureOwnerId, Event.RebalanceCompleted);
            } else {
                s_logger.warn("Failed to transfer host id=" + hostId + " to management server " + futureOwnerId);
                finishRebalance(hostId, futureOwnerId, Event.RebalanceFailed);
            }
                
        } else if (futureOwnerId == _nodeId) {
            HostVO host = _hostDao.findById(hostId);
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Disconnecting host " + host.getId() + "(" + host.getName() + " as a part of rebalance process without notification");
                }

                AgentAttache attache = findAttache(hostId);
                if (attache != null) {
                    result = handleDisconnect(attache, Event.AgentDisconnected, false, false);
                }

                if (result) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Loading directly connected host " + host.getId() + "(" + host.getName() + ") to the management server " + _nodeId + " as a part of rebalance process");
                    }
                    result = loadDirectlyConnectedHost(host, true);
                } else {
                    s_logger.warn("Failed to disconnect " + host.getId() + "(" + host.getName() + 
                            " as a part of rebalance process without notification");
                }
                
            } catch (Exception ex) {
                s_logger.warn("Failed to load directly connected host " + host.getId() + "(" + host.getName() + ") to the management server " + _nodeId + " as a part of rebalance process due to:", ex);
                result = false;
            }
            
            if (result) {
                s_logger.debug("Successfully loaded directly connected host " + host.getId() + "(" + host.getName() + ") to the management server " + _nodeId + " as a part of rebalance process");
            } else {
                s_logger.warn("Failed to load directly connected host " + host.getId() + "(" + host.getName() + ") to the management server " + _nodeId + " as a part of rebalance process");
            }
        }

        return result;
    }
    

    protected void finishRebalance(final long hostId, long futureOwnerId, Event event){

        boolean success = (event == Event.RebalanceCompleted) ? true : false;
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Finishing rebalancing for the agent " + hostId + " with event " + event);
        }
        
        AgentAttache attache = findAttache(hostId);
        if (attache == null || !(attache instanceof ClusteredAgentAttache)) {
            s_logger.debug("Unable to find forward attache for the host id=" + hostId + ", assuming that the agent disconnected already");
            _hostTransferDao.completeAgentTransfer(hostId);
            return;
        } 
        
        ClusteredAgentAttache forwardAttache = (ClusteredAgentAttache)attache;
        
        if (success) {

            //1) Set transfer mode to false - so the agent can start processing requests normally
            forwardAttache.setTransferMode(false);
            
            //2) Get all transfer requests and route them to peer
            Request requestToTransfer = forwardAttache.getRequestToTransfer();
            while (requestToTransfer != null) {
                s_logger.debug("Forwarding request " + requestToTransfer.getSequence() + " held in transfer attache " + hostId + " from the management server " + _nodeId + " to " + futureOwnerId);
                boolean routeResult = routeToPeer(Long.toString(futureOwnerId), requestToTransfer.getBytes());
                if (!routeResult) {
                    logD(requestToTransfer.getBytes(), "Failed to route request to peer");
                }
                
                requestToTransfer = forwardAttache.getRequestToTransfer();
            }
            
            s_logger.debug("Management server " + _nodeId + " completed agent " + hostId + " rebalance to " + futureOwnerId);
           
        } else {
            failRebalance(hostId);
        }
        
        s_logger.debug("Management server " + _nodeId + " completed agent " + hostId + " rebalance");
        _hostTransferDao.completeAgentTransfer(hostId);
    }
    
    protected void failRebalance(final long hostId){
        try {
            s_logger.debug("Management server " + _nodeId + " failed to rebalance agent " + hostId);
            _hostTransferDao.completeAgentTransfer(hostId);
            handleDisconnectWithoutInvestigation(findAttache(hostId), Event.RebalanceFailed, true);
        } catch (Exception ex) {
            s_logger.warn("Failed to reconnect host id=" + hostId + " as a part of failed rebalance task cleanup");
        }
    }
    
    protected boolean startRebalance(final long hostId) {
        HostVO host = _hostDao.findById(hostId);
        
        if (host == null || host.getRemoved() != null) {
            s_logger.warn("Unable to find host record, fail start rebalancing process");
            return false;
        } 
        
        synchronized (_agents) {
            ClusteredDirectAgentAttache attache = (ClusteredDirectAgentAttache)_agents.get(hostId);
            if (attache != null && attache.getQueueSize() == 0 && attache.getNonRecurringListenersSize() == 0) {
            	handleDisconnectWithoutInvestigation(attache, Event.StartAgentRebalance, true);
                ClusteredAgentAttache forwardAttache = (ClusteredAgentAttache)createAttache(hostId);
                if (forwardAttache == null) {
                    s_logger.warn("Unable to create a forward attache for the host " + hostId + " as a part of rebalance process");
                    return false;
                }
                s_logger.debug("Putting agent id=" + hostId + " to transfer mode");
                forwardAttache.setTransferMode(true);
                _agents.put(hostId, forwardAttache);
            } else {
                if (attache == null) {
                    s_logger.warn("Attache for the agent " + hostId + " no longer exists on management server " + _nodeId + ", can't start host rebalancing");
                } else {
                    s_logger.warn("Attache for the agent " + hostId + " has request queue size= " + attache.getQueueSize() + " and listener queue size " + attache.getNonRecurringListenersSize() + ", can't start host rebalancing");
                }
                return false;
            }
        }
        _hostTransferDao.startAgentTransfer(hostId);
        return true;
    }
    
    protected void cleanupTransferMap(long msId) {
        List<HostTransferMapVO> hostsJoingingCluster = _hostTransferDao.listHostsJoiningCluster(msId);
        
        for (HostTransferMapVO hostJoingingCluster : hostsJoingingCluster) {
            _hostTransferDao.remove(hostJoingingCluster.getId());
        }
        
        List<HostTransferMapVO> hostsLeavingCluster = _hostTransferDao.listHostsLeavingCluster(msId);
        for (HostTransferMapVO hostLeavingCluster : hostsLeavingCluster) {
            _hostTransferDao.remove(hostLeavingCluster.getId());
        }
    }
    
    
    protected class RebalanceTask implements Runnable {
        Long hostId = null;
        Long currentOwnerId = null;
        Long futureOwnerId = null;
        
        
        public RebalanceTask(long hostId, long currentOwnerId, long futureOwnerId) {
            this.hostId = hostId;
            this.currentOwnerId = currentOwnerId;
            this.futureOwnerId = futureOwnerId;
        }

        @Override
        public void run() {
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Rebalancing host id=" + hostId);
                }
                rebalanceHost(hostId, currentOwnerId, futureOwnerId);
            } catch (Exception e) {
                s_logger.warn("Unable to rebalance host id=" + hostId, e);
            } finally {
                StackMaid.current().exitCleanup();
            }
        }
    }
    
}
