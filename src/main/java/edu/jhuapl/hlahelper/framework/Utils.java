package edu.jhuapl.hlahelper.framework;

import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.util.*;
import java.util.logging.Logger;

public class Utils {

    private static final Logger log = Logger.getLogger(Utils.class.getSimpleName());

    public static byte[] encode(EncoderFactory encoderFactory, Object value) {
        String type = value.getClass().getSimpleName();

        switch (type) {
            case "String":
                return encodeString(encoderFactory, (String)value);
            case "byte":
                return encodeByte(encoderFactory, (byte)value);
            case "boolean":
                return encodeBoolean(encoderFactory, (Boolean)value);
            case "int":
                return encodeInteger(encoderFactory, (Integer)value);
            case "short":
                return encodeShort(encoderFactory, (Short)value);
            case "long":
                return encodeLong(encoderFactory, (Long)value);
            case "float":
                return encodeFloat(encoderFactory, (Float)value);
            case "double":
                return encodeDouble(encoderFactory, (Double)value);
            case "Byte":
                return encodeByte(encoderFactory, (byte)value);
            case "Boolean":
                return encodeBoolean(encoderFactory, (Boolean)value);
            case "Integer":
                return encodeInteger(encoderFactory, (Integer)value);
            case "Short":
                return encodeShort(encoderFactory, (Short)value);
            case "Long":
                return encodeLong(encoderFactory, (Long)value);
            case "Float":
                return encodeFloat(encoderFactory, (Float)value);
            case "Double":
                return encodeDouble(encoderFactory, (Double)value);
        }
        if(value instanceof Enum) {
            return encodeEnumeration(encoderFactory, (Enum)value);
        }
        return null;
    }

    public static Object decode(EncoderFactory encoderFactory, byte[] value, Class returnType) throws DecoderException {
        String type = returnType.getSimpleName();

        switch (type) {
            case "String":
                return decodeString(encoderFactory, value);
            case "byte":
                return decodeByte(encoderFactory, value);
            case "boolean":
                return decodeBoolean(encoderFactory, value);
            case "int":
                return decodeInteger(encoderFactory, value);
            case "short":
                return decodeShort(encoderFactory, value);
            case "long":
                return decodeLong(encoderFactory, value);
            case "float":
                return decodeFloat(encoderFactory, value);
            case "double":
                return decodeDouble(encoderFactory, value);
            case "Byte":
                return decodeByte(encoderFactory, value);
            case "Boolean":
                return decodeBoolean(encoderFactory, value);
            case "Integer":
                return decodeInteger(encoderFactory, value);
            case "Short":
                return decodeShort(encoderFactory, value);
            case "Long":
                return decodeLong(encoderFactory, value);
            case "Float":
                return decodeFloat(encoderFactory, value);
            case "Double":
                return decodeDouble(encoderFactory, value);
        }
        Class<? extends Enum> enumClass = returnType.asSubclass(Enum.class);
        if(enumClass != null) {
            Set<? extends Enum> allElementsInMyEnum = EnumSet.allOf(enumClass);
            for(Enum enumValue : allElementsInMyEnum) {
                if(enumValue.ordinal() == value[0]) {
                    return returnType.cast(enumValue);
                }
            }
        }

        throw new DecoderException("Unknown type: " + returnType.getName());
    }

    public static byte decodeByte(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        return value[0];
    }

    public static byte[] encodeByte(EncoderFactory encoderFactory, byte value) {
        byte[] val = new byte[1];
        val[0] = value;
        return val;
    }

    public static int decodeInteger(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAinteger32BE decoder = encoderFactory.createHLAinteger32BE();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeInteger(EncoderFactory encoderFactory, int value) {
        return encoderFactory.createHLAinteger32BE(value).toByteArray();
    }

    public static int decodeShort(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAinteger16BE decoder = encoderFactory.createHLAinteger16BE();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeShort(EncoderFactory encoderFactory, short value) {
        return encoderFactory.createHLAinteger16BE(value).toByteArray();
    }

    public static long decodeLong(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAinteger64BE decoder = encoderFactory.createHLAinteger64BE();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeLong(EncoderFactory encoderFactory, long value) {
        return encoderFactory.createHLAinteger64BE(value).toByteArray();
    }

    public static double decodeDouble(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAfloat64BE decoder = encoderFactory.createHLAfloat64BE();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeFloat(EncoderFactory encoderFactory, float value) {
        return encoderFactory.createHLAfloat32BE(value).toByteArray();
    }

    public static float decodeFloat(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAfloat32BE decoder = encoderFactory.createHLAfloat32BE();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeDouble(EncoderFactory encoderFactory, double value) {
        return encoderFactory.createHLAfloat64BE(value).toByteArray();
    }

    public static String decodeString(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAASCIIstring decoder = encoderFactory.createHLAASCIIstring();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeString(EncoderFactory encoderFactory, String value) {
        return encoderFactory.createHLAASCIIstring(value).toByteArray();
    }

    public static String decodeUnicodeString(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAunicodeString decoder = encoderFactory.createHLAunicodeString();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeUnicodeString(EncoderFactory encoderFactory, String value) {
        return encoderFactory.createHLAunicodeString(value).toByteArray();
    }

    public static boolean decodeBoolean(EncoderFactory encoderFactory, byte[] value) throws DecoderException {
        HLAboolean decoder = encoderFactory.createHLAboolean();
        decoder.decode(value);
        return decoder.getValue();
    }

    public static byte[] encodeBoolean(EncoderFactory encoderFactory, boolean value) {
        return encoderFactory.createHLAboolean(value).toByteArray();
    }

    public static byte[] encodeEnumeration(EncoderFactory encoderFactory, Enum simEnum) {
        return encoderFactory.createHLAoctet((byte)simEnum.ordinal()).toByteArray();
    }

    public static HLAfloat64Time getTimeForDouble(RTIambassador rtIambassador, double value) throws FederateNotExecutionMember, NotConnected {
        HLAfloat64TimeFactory hlAfloat64TimeFactory = (HLAfloat64TimeFactory)rtIambassador.getTimeFactory();
        return hlAfloat64TimeFactory.makeTime(value);
    }

    public static HLAfloat64Time addToTime(RTIambassador rtIambassador, double addend, LogicalTime logicalTime) throws RTIexception {
        return ((HLAfloat64TimeFactory)rtIambassador.getTimeFactory()).makeTime(((HLAfloat64Time)logicalTime).getValue()+addend);
    }

    public static HLAfloat64Time addToTime(RTIambassador rtIambassador, double addend) throws RTIexception {
        return ((HLAfloat64TimeFactory)rtIambassador.getTimeFactory()).makeTime(((HLAfloat64Time)rtIambassador.queryLogicalTime()).getValue()+addend);
    }

    public static double addTimes(LogicalTime timeOne, LogicalTime timeTwo) {
        return ((HLAfloat64Time)timeOne).getValue()+((HLAfloat64Time)timeTwo).getValue();
    }

    public static double subtractTimes(LogicalTime timeOne, LogicalTime timeTwo) {
        return ((HLAfloat64Time)timeOne).getValue()-((HLAfloat64Time)timeTwo).getValue();
    }

}


