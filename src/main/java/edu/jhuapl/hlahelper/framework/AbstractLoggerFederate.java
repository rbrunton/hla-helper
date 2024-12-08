/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Abstract class for a federate that logs interactions and object attribute updates to a MongoDB database.
 */
public abstract class AbstractLoggerFederate extends BaseFederate {
    private static final Logger log = Logger.getLogger(AbstractLoggerFederate.class.getSimpleName());
    protected String typesPackageName;
    protected Map<String, Class<InteractionValuesWrapper>> interactionToWrapperClass = new HashMap<>();
    protected Map<String, Class<ObjectClassWrapper>> fomClassNameToWrapperClass = new HashMap<>();
    protected MongoLogger mongoLogger;
    public AbstractLoggerFederate(String typesPackageName, String simulationId) {
        this.typesPackageName = typesPackageName;
        mongoLogger = new MongoLogger(simulationId);
    }

    /**
     * Finds all interaction wrapper classes in the types package.
     * @return
     * @throws IOException
     */
    private Set<Class> findAllInteractionWrappers() throws IOException {
        Reflections reflections = new Reflections(typesPackageName, new SubTypesScanner(false));
        return reflections.getSubTypesOf(InteractionValuesWrapper.class)
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * Finds all object class wrapper classes in the types package.
     * @return
     * @throws IOException
     */
    private Set<Class> findAllObjectClassWrappers() throws IOException {
        Reflections reflections = new Reflections(typesPackageName, new SubTypesScanner(false));
        return reflections.getSubTypesOf(ObjectClassWrapper.class)
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * Publishes and subscribes to all interactions and object classes in the types package.
     * @throws RTIexception
     */
    @Override
    protected void publishAndSubscribe() throws RTIexception {
        try {
            Set<Class> interactionClasses = findAllInteractionWrappers();
            interactionClasses.stream().forEach(clazz -> {
                try {
                    String interactionName = ((InteractionValuesWrapper)clazz.getDeclaredConstructors()[0].newInstance()).getInteractionName();
                    interactionToWrapperClass.put(interactionName, clazz);
                    publishAndSubscribeInteraction(interactionName, true, false);
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Error subscribing to " + clazz.getName());
                    log.log(Level.SEVERE, e.getMessage(), e);
                }
            });

            Set<Class> objectClasses = findAllObjectClassWrappers();
            objectClasses.stream().forEach(clazz -> {
                try {
                    ObjectClassWrapper ocw = (ObjectClassWrapper)clazz.getDeclaredConstructors()[0].newInstance();
                    String objectClassName = ocw.getFOMClassName();
                    fomClassNameToWrapperClass.put(objectClassName, clazz);
                    publishAndSubscribeAttributes(objectClassName, ocw.getAttributeNames(), true, false);
                }catch (Exception e) {
                    log.log(Level.SEVERE, "Error subscribing to " + clazz.getName());
                    log.log(Level.SEVERE, e.getMessage(), e);
                }
            });

        }catch (Exception e) {
            throw new RTIexception("Failed to subscribe");
        }
    }

    @Override
    /**
     * Logs interactions to the MongoDB database as they are received from the RTI.
     */
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
                                   byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport,
                                   LogicalTime theTime, OrderType receivedOrdering, SupplementalReceiveInfo receiveInfo) throws FederateInternalError {

        try {
            if(interactionClassHandles.containsKey(interactionClass.toString())) {
                String interactionName = interactionClassHandles.get(interactionClass.toString());
                InteractionValuesWrapper ivw = (InteractionValuesWrapper) interactionToWrapperClass.get(interactionName).getDeclaredConstructors()[0].newInstance();
                ivw.parse(rtIambassador, encoderFactory, interactionClass, theParameters);
                mongoLogger.logInteraction(ivw, ((HLAfloat64Time)theTime).getValue());
            }
        }catch(Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Logs object attribute updates to the MongoDB database as they are received from the RTI.
     * @param theObject
     * @param theAttributes
     * @param userSuppliedTag
     * @param sentOrdering
     * @param theTransport
     * @param theTime
     * @param receivedOrdering
     * @param reflectInfo
     * @throws FederateInternalError
     */
    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject, AttributeHandleValueMap theAttributes, byte[] userSuppliedTag,
                                       OrderType sentOrdering, TransportationTypeHandle theTransport, LogicalTime theTime,
                                       OrderType receivedOrdering, SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        try {
            String objectClass = objectClassHandles.get(rtIambassador.getKnownObjectClassHandle(theObject).toString());
            ObjectClassWrapper ocw = (ObjectClassWrapper)fomClassNameToWrapperClass.get(objectClass).getDeclaredConstructors()[0].newInstance();
            ocw.parse(rtIambassador, encoderFactory, theObject, theAttributes);
            mongoLogger.logObjectAttributeUpdate(ocw, ((HLAfloat64Time)theTime).getValue());
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
