/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.Template;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * This class generates Java classes representing all object classes and interactions from a FOM file.
 */
public class WrapperGeneratorTool {

    static String WRAPPER_TEMPLATE = "templates/type_wrapper.vm";
    static String ENUM_WRAPPER_TEMPLATE = "templates/hla_enum.vm";

    /**
     * Main method to generate Java classes from a FOM file.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            System.out.println("Usage: java -jar hla-helper-[version]-jar-with-dependencies.jar <FOMFile> <outputDirectory> <basePackageName>");
            System.exit(1);
        }

        try {
            (new WrapperGeneratorTool()).processFOM(args[0], args[1], args[2]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Generate Java classes representing all object classes and interactions from a FOM file.
     * @param fomFile The FOM file to process
     * @param outputDirectory The directory where the generated Java classes will be written
     * @param basePackageName The base package name for the generated Java classes
     * @throws Exception
     */
    public void processFOM(String fomFile, String outputDirectory, String basePackageName) throws Exception {
        // Read FOM file
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new File(fomFile));
        doc.getDocumentElement().normalize();
        // For each enumeratedData:
        //   Generate wrapper
        NodeList nodeList = doc.getElementsByTagName("enumeratedData");
        for(int i = 0; i < nodeList.getLength(); i++) {
            EnumData enumData = new EnumData();
            Element element = (Element) nodeList.item(i);
            // Put <name> into enumName
            enumData.enumName =element.getElementsByTagName("name").item(0).getTextContent();
            // Put provided package name into packageName
            enumData.packageName = basePackageName;
            // Create list enumValues
            List<String> enumValues = new ArrayList<>();
            // For each instance of <enumerator>:
            //   Add enumValue to enumValues
            NodeList enumNodeList = element.getElementsByTagName("enumerator");
            for(int j = 0; j < enumNodeList.getLength(); j++) {
                enumValues.add(((Element)enumNodeList.item(j)).getElementsByTagName("name").item(0).getTextContent());
            }
            enumData.enumValues = enumValues;
            generateEnumWrapper(outputDirectory, enumData);
        }
        // For each objectClass:
        //   Generate wrapper
        nodeList = doc.getElementsByTagName("objectClass");
        for(int i = 1; i < nodeList.getLength(); i++) { //skip first objectClass, as it's HLAobjectRoot
            WrapperData wrapperData = new WrapperData();
            Element element = (Element) nodeList.item(i);
            wrapperData.fomType = "ObjectClass";
            // Put <name> into className
            wrapperData.className = element.getElementsByTagName("name").item(0).getTextContent();
            // Put provided package name into packageName
            wrapperData.packageName = basePackageName;
            // Put HLAobjectRoot." + <name> into fomName
            wrapperData.fomName = getFullName(element);
            // Add inheritance info based on FOM hierarchy
            String[] hierarchy = wrapperData.fomName.split("\\.");
            wrapperData.parentType = hierarchy.length > 2 ? hierarchy[hierarchy.length-2] : "ObjectClassWrapper";
            // Create list fields
            wrapperData.fields = new ArrayList<>();
            // For each instance of <attribute>:
            //   Create field
            NodeList fieldNodeList = element.getElementsByTagName("attribute");
            for(int j = 0; j < fieldNodeList.getLength(); j++) {
                // Put <name> into field.name
                String fName = ((Element)fieldNodeList.item(j)).getElementsByTagName("name").item(0).getTextContent();
                fName = fName.substring(0,1).toLowerCase() + fName.substring(1);
                // Put <dataType> into field.type
                String fType = ((Element)fieldNodeList.item(j)).getElementsByTagName("dataType").item(0).getTextContent();
                wrapperData.fields.add(getDataType(fType.trim()) + " " + fName);
                //System.out.println("Added field: " + field.name + " of type " + field.type + " to " + wrapperData.className);
            }

            generateWrapper(outputDirectory, wrapperData);
        }
        // For each interactionClass:
        //   Generate wrapper
        nodeList = doc.getElementsByTagName("interactionClass");
        for(int i = 1; i < nodeList.getLength(); i++) { //skip first interactionClass, as it's HLAinteractionRoot
            WrapperData wrapperData = new WrapperData();
            Element element = (Element) nodeList.item(i);
            wrapperData.fomType = "InteractionClass";
            // Put <name> into className
            wrapperData.className = element.getElementsByTagName("name").item(0).getTextContent();
            // Put provided package name into packageName
            wrapperData.packageName = basePackageName;
            // Put HLAinteractionRoot." + <name> into fomName
            wrapperData.fomName = getFullName(element);
            // Add inheritance info based on FOM hierarchy
            String[] hierarchy = wrapperData.fomName.split("\\.");
            wrapperData.parentType = hierarchy.length > 2 ? hierarchy[hierarchy.length-2] : "InteractionValuesWrapper";
            // Create list fields
            List<String> fields = new ArrayList<>();
            // For each instance of <parameter>:
            //   Create field
            NodeList fieldNodeList = element.getElementsByTagName("parameter");
            for(int j = 0; j < fieldNodeList.getLength(); j++) {
                // Put <name> into field.name
                String fName = ((Element)fieldNodeList.item(j)).getElementsByTagName("name").item(0).getTextContent();
                fName = fName.substring(0,1).toLowerCase() + fName.substring(1);
                // Put <dataType> into field.type
                String fType = ((Element)fieldNodeList.item(j)).getElementsByTagName("dataType").item(0).getTextContent();
                fields.add(getDataType(fType.trim()) + " " + fName);
            }
            wrapperData.fields = fields;
            generateWrapper(outputDirectory, wrapperData);
        }
    }

    /**
     * Generate a Java class representing an object class or interaction.
     * @param outputDirectory
     * @param wrapperData
     * @throws IOException
     */
    private void generateWrapper(String outputDirectory, WrapperData wrapperData) throws IOException {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());

        velocityEngine.init();

        Template t = velocityEngine.getTemplate(WRAPPER_TEMPLATE, "UTF-8");

        VelocityContext context = new VelocityContext();
        context.put("packageName", wrapperData.packageName);
        context.put("className", wrapperData.className);
        context.put("parentType", wrapperData.parentType);
        context.put("fomName", wrapperData.fomName);
        context.put("fomType", wrapperData.fomType);
        context.put("fields", wrapperData.fields);

        Writer writer = new FileWriter(outputDirectory+"/"+wrapperData.className+".java");
        t.merge(context, writer);

        writer.flush();
        writer.close();

        System.out.println("Generated " + outputDirectory+"/"+wrapperData.className+".java");
    }

    /**
     * Generate a Java class representing an enumerated data type.
     * @param outputDirectory
     * @param enumData
     * @throws IOException
     */
    private void generateEnumWrapper(String outputDirectory, EnumData enumData) throws IOException {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        Template t = velocityEngine.getTemplate(ENUM_WRAPPER_TEMPLATE, "UTF-8");

        VelocityContext context = new VelocityContext();
        context.put("packageName", enumData.packageName);
        context.put("enumName", enumData.enumName);
        context.put("enumValues", enumData.enumValues);

        Writer writer = new FileWriter(outputDirectory+"/"+enumData.enumName+".java");
        t.merge(context, writer);

        writer.flush();
        writer.close();

        System.out.println("Generated " + outputDirectory+"/"+enumData.enumName+".java");
    }

    /**
     * Get the fully-qualified name of an object class or interaction.
     * @param element
     * @return
     */
    private String getFullName(Element element) {
        String fullName = element.getElementsByTagName("name").item(0).getTextContent();
        Element parent = (Element) element.getParentNode();
        if(parent != null &&
                (parent.getTagName().equals("objectClass") ||
                        parent.getTagName().equals("interactionClass") ) ) {
            fullName = getFullName(parent) + "." + fullName;
        }
        return fullName;
    }

    /**
     * Get the Java data type corresponding to an HLA data type.
     * @param dataType
     * @return
     */
    private String getDataType(String dataType) {
        switch(dataType) {
            case "HLAASCIIstring", "HLAunicodeString":
                return "String";
            case "HLAboolean":
                return "boolean";
            case "Float32BE", "Float32LE":
                return "float";
            case "Float64BE", "Float64LE", "TimeType":
                return "double";
            case "Integer16BE", "Integer16LE", "HLAoctetPairBE", "HLAoctetPairLE":
                return "short";
            case "Integer32BE", "Integer32LE":
                return "int";
            case "Integer64BE", "Integer64LE":
                return "long";
            case "HLAoctet", "IDType":
                return "byte";
            default:
                return dataType;
        }
    }
    /**
     * Wrapper class to hold data through recursive traversal of FOM tree.
     */
    private class WrapperData {
        public String packageName;
        public String className;
        public String fomName;
        public String fomType;
        public String parentType;
        public List<String> fields;
    }

    /**
     * Class to hold data for generating Java classes for enumerations.
     */
    private class EnumData {
        public String packageName;
        public String enumName;
        public List<String> enumValues;
    }
}
