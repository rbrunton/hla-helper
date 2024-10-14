/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.RTIexception;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class InteractionValuesWrapper {
    private static final Logger log = Logger.getLogger(InteractionValuesWrapper.class.getSimpleName());
    protected Map<String, Boolean> updateStatusTracker;
    protected String[] parameterNames;

    public abstract String getInteractionName();

    protected InteractionValuesWrapper() {
        Method methList[] = this.getClass().getDeclaredMethods();
        List<Method> mutators = Arrays.asList(methList).stream().filter(m -> m.getName().startsWith("set")).collect(Collectors.toList());
        parameterNames = new String[mutators.size()];
        for(int i = 0; i < mutators.size(); i++) {
            parameterNames[i] = mutators.get(i).getName().substring(3);
        }
        updateStatusTracker = createUpdateStatusTracker(parameterNames);
    }

    public void parse(RTIambassador rtIambassador, EncoderFactory encoderFactory,
                                                     InteractionClassHandle interactionClass,
                                                     ParameterHandleValueMap theParameters) throws RTIexception, DecoderException {
        for(ParameterHandle parameterHandle : theParameters.keySet()) {
            String parameterName = rtIambassador.getParameterName(interactionClass, parameterHandle);
            byte[] value = theParameters.get(parameterHandle);

            try {
                Class[] paramType = new Class[1];
                Method getter = this.getClass().getMethod("get"+parameterName);
                paramType[0] = getter.getReturnType();
                Method setter = this.getClass().getMethod("set"+parameterName, paramType);

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
                    log.severe("Got null value for " + parameterName);
                }
            }catch (Exception e) {
                log.severe("Failed to parse attribute: " + parameterName);
                log.severe(e.toString());
                log.severe(e.getMessage());
                log.throwing("InteractionValuesWrapper", "parse", e);
            }

        }
    }

    public ParameterHandleValueMap encode(RTIambassador rtIambassador, EncoderFactory encoderFactory,
                                          InteractionClassHandle interactionClass) throws RTIexception, EncoderException {
        ParameterHandleValueMap valueMap = rtIambassador.getParameterHandleValueMapFactory().create(parameterNames.length);

        Map<String, ParameterHandle> parameterHandles = InteractionValuesWrapper.getParameterHandles(rtIambassador,
                interactionClass, parameterNames);

        for(String parameterName : parameterNames) {
            if(updateStatusTracker.get(parameterName)) {
                try {
                    Object value = this.getClass().getMethod("get" + parameterName).invoke(this);
                    byte[] encodedVal = Utils.encode(encoderFactory, value);
                    if(value != null && parameterHandles.get(parameterName) != null) {
                        valueMap.put(parameterHandles.get(parameterName), encodedVal);
                    }
                }catch (Exception e) {
                    log.severe("Failed to encode attribute: " + parameterName);
                    log.severe(e.toString());
                    log.severe(e.getMessage());
                    log.throwing("InterationValuesWrapper", "encode", e);
                }
            }
        }

        return valueMap;
    }

    public static Map<String, ParameterHandle> getParameterHandles(RTIambassador rtIambassador,
                                                                   InteractionClassHandle interactionClassHandle,
                                                                   String[] parameterNames)
            throws RTIexception {
        Map<String, ParameterHandle> handles = new HashMap<>();

        for(int i = 0; i < parameterNames.length; i++) {
            ParameterHandle handle = rtIambassador.getParameterHandle(interactionClassHandle, parameterNames[i]);
            handles.put(parameterNames[i], handle);
        }

        return handles;
    }

    public String[] getParameterNames() {
        return parameterNames.clone();
    }

    public boolean isParameterSet(String parameterName) {
        return updateStatusTracker.containsKey(parameterName) ? updateStatusTracker.get(parameterName) : false;
    }

    public String[] getSetParameterNames() {
        return Arrays.stream(parameterNames).filter(p -> isParameterSet(p)).toArray(size -> new String[size]);
    }

    protected Map<String, Boolean> createUpdateStatusTracker(String[] parameterNames) {
        Map<String, Boolean> statuses = new HashMap<>();
        for(String aName : parameterNames) {
            statuses.put(aName, false);
        }
        return statuses;
    }
    protected void setParameterUpdated(String parameterName, boolean state) {
        updateStatusTracker.put(parameterName, state);
    }

}
