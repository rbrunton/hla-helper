/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.RTIexception;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class is a wrapper for HLA object classes. It provides a way to track which attributes have been updated and
 * provides methods to encode and decode the object class attributes.
 */
public abstract class ObjectClassWrapper {
    private static final Logger log = Logger.getLogger(ObjectClassWrapper.class.getSimpleName());
    protected Map<String, Boolean> updateStatusTracker;
    protected String[] attributeNames;

    /**
     * This method should return the fully qualified name of the HLA object class that this wrapper represents.
     * @return
     */
    public abstract String getFOMClassName();

    /**
     * This method should return the fully qualified name of the HLA object class that this wrapper represents.
     */
    protected ObjectClassWrapper() {
        Method methList[] = this.getClass().getDeclaredMethods();
        List<Method> mutators = Arrays.asList(methList).stream().filter(m -> m.getName().startsWith("set")).collect(Collectors.toList());
        attributeNames = new String[mutators.size()];
        for(int i = 0; i < mutators.size(); i++) {
            attributeNames[i] = mutators.get(i).getName().substring(3);
        }
        updateStatusTracker = createUpdateStatusTracker(attributeNames);
    }

    /**
     * This method sets updated status for the attribute.
     * @param attributeName
     * @param state
     */
    protected void setAttributeUpdated(String attributeName, boolean state) {
        updateStatusTracker.put(attributeName, state);
    }

    /**
     * This method parses the attribute values from the RTI and sets the values in the wrapper object.
     * @param rtIambassador
     * @param encoderFactory
     * @param objectClassInstance
     * @param attributeHandleValueMap
     * @throws RTIexception
     * @throws DecoderException
     */
    public void parse(RTIambassador rtIambassador, EncoderFactory encoderFactory, ObjectInstanceHandle objectClassInstance, AttributeHandleValueMap attributeHandleValueMap) throws RTIexception, DecoderException {
        ObjectClassHandle objectClassHandle = rtIambassador.getKnownObjectClassHandle(objectClassInstance);
        for(AttributeHandle attributeHandle : attributeHandleValueMap.keySet()) {
            String attributeName = rtIambassador.getAttributeName(objectClassHandle, attributeHandle);
            byte[] value = attributeHandleValueMap.get(attributeHandle);

            try {
                Class[] paramType = new Class[1];
                Method getter = this.getClass().getMethod("get"+attributeName);
                paramType[0] = getter.getReturnType();
                Method setter = this.getClass().getMethod("set"+attributeName, paramType);

                Object decodedValue = Utils.decode(encoderFactory, value, paramType[0]);

                if(decodedValue != null) {
                    switch(paramType[0].getSimpleName()) {
                        case "byte":
                            setter.invoke(this, ((Byte)decodedValue).byteValue());
                            break;
                        case "boolean":
                            setter.invoke(this, ((Boolean)decodedValue).booleanValue());
                            break;
                        case "int":
                            setter.invoke(this, ((Integer)decodedValue).intValue());
                            break;
                        case "short":
                            setter.invoke(this, ((Short)decodedValue).shortValue());
                            break;
                        case "long":
                            setter.invoke(this, ((Long)decodedValue).longValue());
                            break;
                        case "float":
                            setter.invoke(this, ((Float)decodedValue).floatValue());
                            break;
                        case "double":
                            setter.invoke(this, ((Double)decodedValue).doubleValue());
                            break;
                        default:
                            setter.invoke(this, paramType[0].cast(decodedValue));
                    }
                } else {
                    log.severe("Got null value for " + attributeName);
                }
            }catch (Exception e) {
                log.severe("Failed to parse attribute: " + attributeName);
                log.severe(e.toString());
                log.severe(e.getMessage());
                log.throwing("ObjectClassWrapper", "parse", e);
            }

        }
    }

    /**
     * This method encodes the attribute values in the wrapper object and returns the AttributeHandleValueMap.
     * @param rtIambassador
     * @param encoderFactory
     * @param objectInstanceHandle
     * @return
     * @throws RTIexception
     */
    public AttributeHandleValueMap encode(RTIambassador rtIambassador, EncoderFactory encoderFactory, ObjectInstanceHandle objectInstanceHandle) throws RTIexception {
        AttributeHandleValueMap valueMap = rtIambassador.getAttributeHandleValueMapFactory().create(attributeNames.length);

        Map<String, AttributeHandle> handleMap = getAttributeHandles(rtIambassador, objectInstanceHandle, attributeNames);

        for(String attributeName : attributeNames) {
            if(updateStatusTracker.get(attributeName)) {
                try {
                    Object value = this.getClass().getMethod("get" + attributeName).invoke(this);
                    byte[] encodedVal = Utils.encode(encoderFactory, value);
                    if(value != null && handleMap.get(attributeName) != null) {
                        valueMap.put(handleMap.get(attributeName), encodedVal);
                    }
                }catch (Exception e) {
                    log.severe("Failed to encode attribute: " + attributeName);
                    log.severe(e.toString());
                    log.severe(e.getMessage());
                    log.throwing("ObjectClassWrapper", "encode", e);
                }
            }
        }

        return valueMap;
    }

    /**
     * This method returns the attribute names for the object class.
     * @return
     */
    public String[] getAttributeNames() {
        return attributeNames.clone();
    }

    /**
     * This method returns the updated status of the attribute.
     * @param attributeName
     * @return
     */
    public boolean isAttributeSet(String attributeName) {
        return updateStatusTracker.containsKey(attributeName) ? updateStatusTracker.get(attributeName) : false;
    }

    /**
     * This method returns the attribute names that have been updated.
     * @return
     */
    public String[] getSetAttributeNames() {
        return Arrays.stream(attributeNames).filter(a -> isAttributeSet(a)).toArray(size -> new String[size]);
    }

    /**
     * This method returns a map of attribute names to attribute handles for a given object instance.
     * @param rtIambassador
     * @param objectInstanceHandle
     * @param attributeNames
     * @return
     * @throws RTIexception
     */
    protected Map<String, AttributeHandle> getAttributeHandles(RTIambassador rtIambassador,
                                                                   ObjectInstanceHandle objectInstanceHandle,
                                                                   String[] attributeNames)
                                                                        throws RTIexception {
        Map<String, AttributeHandle> handles = new HashMap<>();

        ObjectClassHandle classHandle = rtIambassador.getKnownObjectClassHandle(objectInstanceHandle);
        for(int i = 0; i < attributeNames.length; i++) {
            AttributeHandle handle = rtIambassador.getAttributeHandle(classHandle, attributeNames[i]);
            handles.put(attributeNames[i], handle);
        }

        return handles;
    }

    /**
     * This method creates an update status tracker for the object class.
     * @param attributeNames
     * @return
     */
    protected Map<String, Boolean> createUpdateStatusTracker(String[] attributeNames) {
        Map<String, Boolean> statuses = new HashMap<>();
        for(String aName : attributeNames) {
            statuses.put(aName, false);
        }
        return statuses;
    }
}
