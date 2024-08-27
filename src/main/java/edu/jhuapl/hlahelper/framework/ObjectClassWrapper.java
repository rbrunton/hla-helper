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

public abstract class ObjectClassWrapper {
    private static final Logger log = Logger.getLogger(ObjectClassWrapper.class.getSimpleName());
    protected Map<String, Boolean> updateStatusTracker;
    protected String[] attributeNames;

    public abstract String getFOMClassName();
    protected ObjectClassWrapper() {
        Method methList[] = this.getClass().getDeclaredMethods();
        List<Method> mutators = Arrays.asList(methList).stream().filter(m -> m.getName().startsWith("set")).collect(Collectors.toList());
        attributeNames = new String[mutators.size()];
        for(int i = 0; i < mutators.size(); i++) {
            attributeNames[i] = mutators.get(i).getName().substring(3);
        }
        updateStatusTracker = createUpdateStatusTracker(attributeNames);
    }

    protected void setAttributeUpdated(String attributeName, boolean state) {
        updateStatusTracker.put(attributeName, state);
    }

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

    public String[] getAttributeNames() {
        return attributeNames.clone();
    }

    public boolean isAttributeSet(String attributeName) {
        return updateStatusTracker.containsKey(attributeName) ? updateStatusTracker.get(attributeName) : false;
    }

    public String[] getSetAttributeNames() {
        return Arrays.stream(attributeNames).filter(a -> isAttributeSet(a)).toArray(size -> new String[size]);
    }

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

    protected Map<String, Boolean> createUpdateStatusTracker(String[] attributeNames) {
        Map<String, Boolean> statuses = new HashMap<>();
        for(String aName : attributeNames) {
            statuses.put(aName, false);
        }
        return statuses;
    }
}
