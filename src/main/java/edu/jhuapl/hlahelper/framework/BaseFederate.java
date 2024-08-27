package edu.jhuapl.hlahelper.framework;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public abstract class BaseFederate extends NullFederateAmbassador {
    private static final Logger log = Logger.getLogger(BaseFederate.class.getSimpleName());
    protected static final String RUN_SYNC_POINT = "Run";
    protected static final String SHUTDOWN_SYNC_POINT = "Shutdown";
    //required time management variables for lookahead, step size, and initial federate offset
    protected double lookahead;
    protected double timeOffset;
    protected double stepSize;

    protected String federationName = "BD21";
    protected String federateName;

    protected boolean timeRegulationIsEnabled = false;
    protected boolean timeConstrainedIsEnabled = false;

    protected boolean isRunning = false;
    protected boolean isAdvancing = false;
    protected boolean isShutdown = false;
    protected boolean isDone = false;

    protected double federateTime = 0.0;

    protected RTIambassador rtIambassador;

    protected String fomFile;

    protected HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;
    //All maps from handle.toString() to String name
    protected Map<String, String> interactionClassHandles = new Hashtable<>();
    protected Map<String, String> parameterHandles = new Hashtable<>();
    protected Map<String, String> objectClassHandles = new Hashtable<>();
    protected Map<String, String> attributeHandles = new Hashtable<>();

    protected FederateHandleSet fedHandles;

    protected Map<String, SyncPointState> synchronizationPoints = new Hashtable<>();

    /**
     * @throws RTIexception
     */
    protected abstract void publishAndSubscribe() throws RTIexception;

    /**
     *
     */
    public abstract void runFederate() throws Exception;

    protected void setup(String federationName, String federateName, String federateType, Set<String> federateNames,
                         Set<String> syncPointLabels, FederateJoinMonitor federateJoinMonitor, String fomPath, String settingsDesignator)
                            throws RTIexception, MalformedURLException {
        this.fomFile = fomPath;
        this.federationName = federationName;
        this.federateName = federateName;

        setSimulationSyncPoints(syncPointLabels);

        rtIambassador = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        rtIambassador.connect(this, CallbackModel.HLA_IMMEDIATE, settingsDesignator);

        URL[] joinModules = null;
        if(federateNames == null) {
            joinModules = new URL[]{
                    new File(fomFile).toURI().toURL()
            };
        } else {
            joinModules = new URL[]{
                    new File(fomFile).toURI().toURL(),
                    new File("HLAstandardMIM.xml").toURI().toURL()
            };
        }

        if(federateNames != null) {
            try {
                rtIambassador.createFederationExecution(federationName, joinModules);
            }catch (FederationExecutionAlreadyExists e) {
                log.info("Federation already exists");
            }
        }

        rtIambassador.joinFederationExecution(federateName,
                federateType, federationName, joinModules);

        log.info(federateName + " has joined the federation");
        publishAndSubscribe();

        timeFactory = (HLAfloat64TimeFactory) rtIambassador.getTimeFactory();

        if(federateNames != null && federateJoinMonitor != null) {
            while(!federateJoinMonitor.allFederatesJoined() &&
                    !federateJoinMonitor.timeoutReached()) {
                rtIambassador.evokeMultipleCallbacks(0.1, 0.2);
            }
            if(federateJoinMonitor.timeoutReached()) {
                throw new RTIexception("Timed out waiting for federates to join federation");
            } else {
                fedHandles = rtIambassador.getFederateHandleSetFactory().create();
                for(String fedName : federateNames) {
                    fedHandles.add(rtIambassador.getFederateHandle(fedName));
                }

            }
        }
    }

    /**
     * @throws RTIexception
     */
    protected void resign() throws RTIexception {

        rtIambassador.resignFederationExecution(ResignAction.UNCONDITIONALLY_DIVEST_ATTRIBUTES);

        try {
            rtIambassador.destroyFederationExecution(federationName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        rtIambassador.disconnect();
    }

    protected void setSimulationSyncPoints(Set<String> syncPointLabels) {
        for(String syncPoint : syncPointLabels) {
            synchronizationPoints.put(syncPoint, SyncPointState.None);
        }
        if(!synchronizationPoints.containsKey(BaseFederate.RUN_SYNC_POINT)) {
            synchronizationPoints.put(BaseFederate.RUN_SYNC_POINT, SyncPointState.None);
        }
    }

    /**
     * @throws InTimeAdvancingState
     * @throws FederateNotExecutionMember
     * @throws RestoreInProgress
     * @throws TimeRegulationAlreadyEnabled
     * @throws NotConnected
     * @throws RTIinternalError
     * @throws SaveInProgress
     * @throws InvalidLookahead
     * @throws RequestForTimeRegulationPending
     * @throws CallNotAllowedFromWithinCallback
     * @throws TimeConstrainedAlreadyEnabled
     * @throws RequestForTimeConstrainedPending
     */
    protected void enableRegulatingAndConstrained() throws InTimeAdvancingState, FederateNotExecutionMember, RestoreInProgress,
            TimeRegulationAlreadyEnabled, NotConnected, RTIinternalError, SaveInProgress, InvalidLookahead,
            RequestForTimeRegulationPending, CallNotAllowedFromWithinCallback, TimeConstrainedAlreadyEnabled, RequestForTimeConstrainedPending {

        HLAfloat64Interval step = timeFactory.makeInterval(lookahead);

        rtIambassador.enableTimeRegulation(step);

        while (!timeRegulationIsEnabled) {
            rtIambassador.evokeMultipleCallbacks(0.1, 0.2);
        }

        rtIambassador.enableTimeConstrained();

        while (!timeConstrainedIsEnabled) {
            rtIambassador.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    protected void disableRegulatingAndConstrained() throws RTIexception {
        rtIambassador.disableTimeConstrained();

        timeConstrainedIsEnabled = false;

        rtIambassador.disableTimeRegulation();

        timeRegulationIsEnabled = false;
    }
    /**
     *
     * @throws RTIexception
     */
    protected void advanceTime() throws RTIexception
    {

        isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(this.federateTime + stepSize);
        rtIambassador.timeAdvanceRequest(time);

        while(isAdvancing && !isShutdown)
        {
            rtIambassador.evokeMultipleCallbacks( 0.0001, 0.2 );
        }
    }

    /**
     *
     * @throws RTIexception
     */
    protected void advanceToTime(double newTime) throws RTIexception
    {
        // request the advance
        isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(newTime);
        rtIambassador.timeAdvanceRequest(time);

        while(isAdvancing && !isShutdown)
        {
            rtIambassador.evokeMultipleCallbacks( 0.0001, 0.2 );
        }
    }

    /**
     *
     * @param currentTime
     * @throws RTIexception
     */
    protected void advanceTimeToOffset(double currentTime) throws RTIexception
    {
        // request the advance
        isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(currentTime + timeOffset);
        rtIambassador.timeAdvanceRequest(time);

        while(isAdvancing)
        {
            rtIambassador.evokeMultipleCallbacks( 0.0001, 0.2 );
        }
    }

    protected void getNextMessage(double theTime) throws RTIexception {
        isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(theTime);
        rtIambassador.nextMessageRequest(time);

        while(isAdvancing)
        {
            rtIambassador.evokeMultipleCallbacks( 0.0001, 0.2 );
        }
    }

    @Override
    public void timeAdvanceGrant(LogicalTime theTime) throws FederateInternalError {
        super.timeAdvanceGrant(theTime);
        this.federateTime = ((HLAfloat64Time)theTime).getValue();
        this.isAdvancing = false;
    }

    protected void publishAndSubscribeAttributes(String className, String[] attributeNames, boolean subscribe, boolean publish) throws RTIexception {
        ObjectClassHandle objectClassHandle = rtIambassador.getObjectClassHandle(className);
        this.objectClassHandles.put(objectClassHandle.toString(), className);
        AttributeHandleSet attributes = rtIambassador.getAttributeHandleSetFactory().create();

        for(int i = 0; i < attributeNames.length; i++) {
            AttributeHandle attributeHandle = rtIambassador.getAttributeHandle(objectClassHandle, attributeNames[i]);
            this.attributeHandles.put(attributeHandle.toString(), className+"."+attributeNames[i]);
            attributes.add(attributeHandle);
        }

        if(subscribe) {
            rtIambassador.subscribeObjectClassAttributes(objectClassHandle, attributes);
        }
        if(publish) {
            rtIambassador.publishObjectClassAttributes(objectClassHandle, attributes);
        }
    }

    protected void publishAndSubscribeInteraction(String interactionClass, boolean subscribe, boolean publish) throws RTIexception {
        InteractionClassHandle interactionClassHandle = rtIambassador.getInteractionClassHandle(interactionClass);
        this.interactionClassHandles.put(interactionClassHandle.toString(), interactionClass);

        if(subscribe) {
            rtIambassador.subscribeInteractionClass(interactionClassHandle);
        }
        if(publish) {
            rtIambassador.publishInteractionClass(interactionClassHandle);
        }
    }

    public LogicalTime getTimestamp() throws RTIexception {
        return Utils.getTimeForDouble(rtIambassador,federateTime+lookahead);
    }

    @Override
    public void announceSynchronizationPoint(String synchronizationPointLabel, byte[] userSuppliedTag) throws FederateInternalError {
        if(synchronizationPoints.containsKey(synchronizationPointLabel.trim())) {
            synchronizationPoints.put(synchronizationPointLabel.trim(), SyncPointState.Registered);
        }
        else if(synchronizationPointLabel.trim().equalsIgnoreCase(BaseFederate.SHUTDOWN_SYNC_POINT)) {
            isShutdown = true;
        }
    }

    @Override
    public void federationSynchronized(String synchronizationPointLabel, FederateHandleSet failedToSyncSet) throws FederateInternalError {

        if(synchronizationPoints.containsKey(synchronizationPointLabel.trim())) {
            synchronizationPoints.put(synchronizationPointLabel.trim(), SyncPointState.Achieved);
        }
        if(synchronizationPointLabel.trim().equalsIgnoreCase(BaseFederate.RUN_SYNC_POINT)) {
            isRunning = true;
        } else if(synchronizationPointLabel.trim().equalsIgnoreCase(BaseFederate.SHUTDOWN_SYNC_POINT)) {
            isDone = true;
        } else {
            System.err.println(federateName + ": saw unknown synchronization point");
        }

    }

    @Override
    public void timeRegulationEnabled(LogicalTime time) throws FederateInternalError {
        federateTime = ((HLAfloat64Time)time).getValue();
        timeRegulationIsEnabled = true;
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime time) throws FederateInternalError {
        federateTime = ((HLAfloat64Time)time).getValue();
        timeConstrainedIsEnabled = true;
    }

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject, ObjectClassHandle theObjectClass, String objectName, FederateHandle producingFederate) throws FederateInternalError {
        discoverObjectInstance(theObject, theObjectClass, objectName);
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject, AttributeHandleValueMap theAttributes, byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport, LogicalTime theTime, OrderType receivedOrdering, MessageRetractionHandle retractionHandle, SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        reflectAttributeValues(theObject, theAttributes, userSuppliedTag, sentOrdering, theTransport, theTime, receivedOrdering, reflectInfo);
    }


    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject, AttributeHandleValueMap theAttributes, byte[] userSuppliedTag,
                                       OrderType sentOrdering, TransportationTypeHandle theTransport, SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        reflectAttributeValues(theObject, theAttributes, userSuppliedTag, sentOrdering, theTransport, null, null, reflectInfo);
    }


    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters, byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport, SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters, userSuppliedTag, sentOrdering, theTransport, null, null, receiveInfo);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters, byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport, LogicalTime theTime, OrderType receivedOrdering, MessageRetractionHandle retractionHandle, SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters, userSuppliedTag, sentOrdering, theTransport, theTime, receivedOrdering, receiveInfo);
    }
}
