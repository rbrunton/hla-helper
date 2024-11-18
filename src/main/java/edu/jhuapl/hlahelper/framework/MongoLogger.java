/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDateTime;
import org.bson.Document;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is a wrapper for logging data to a MongoDB database.
 */
public class MongoLogger {
    private static final Logger log = Logger.getLogger(MongoLogger.class.getSimpleName());
    private String simulationId;
    private MongoClient mongoClient;

    /**
     * Constructor for the MongoLogger class. It initializes the simulationId and creates a connection to the MongoDB database.
     * It also saves the simulation metadata to the database.
     * @param simulationId
     */
    public MongoLogger(String simulationId) {
        this.simulationId = simulationId;
        this.mongoClient = MongoClients.create(System.getProperty("mongodb.uri"));
        Document simDocument = new Document("id", simulationId).append("start", new BsonDateTime((new Date()).getTime()));
        saveDocument(simDocument, "simMetadata");
    }

    /**
     * Logs a sync point to the database.
     * @param syncPointName
     */
    public void logSyncPoint(String syncPointName) {
        Document syncDoc = new Document("simulationId", simulationId).append("syncPoint", syncPointName);
        saveDocument(syncDoc, "syncPoint");
    }

    /**
     * Logs an object attribute update to the database.
     * @param attributeUpdate
     * @param timestamp
     * @param <T>
     * @throws RuntimeException
     */
    public <T extends ObjectClassWrapper> void logObjectAttributeUpdate(T attributeUpdate, double timestamp) throws RuntimeException {
        Document document = new Document("simulationId", simulationId).append("timestamp", timestamp);
        //filter out the fields added by AspectJ
        Arrays.stream(attributeUpdate.getSetAttributeNames()).forEach(f -> {
            String getterName = "get"+f;
            try {
                document.append(f, attributeUpdate.getClass().getMethod(getterName, null).invoke(attributeUpdate));
            } catch (IllegalAccessException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
        saveDocument(document, attributeUpdate.getFOMClassName().replace('.', '_'));
    }

    /**
     * Logs an interaction to the database.
     * @param interaction
     * @param timestamp
     * @param <T>
     * @throws RuntimeException
     */
    public <T extends InteractionValuesWrapper> void logInteraction(T interaction, double timestamp) throws RuntimeException {
       Document document = new Document("simulationId", simulationId).append("timestamp", timestamp);
        //filter out the fields added by AspectJ
        Arrays.stream(interaction.getSetParameterNames()).forEach( f -> {
            String getterName = "get"+f;
            try {
                document.append(f, interaction.getClass().getMethod(getterName, null).invoke(interaction));
            } catch (IllegalAccessException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
        saveDocument(document, interaction.getInteractionName().replace('.', '_'));
    }

    /**
     * Saves a document to the database. This method is called by all of the other logging methods.
     * @param document
     * @param logType
     */
    public void saveDocument(Document document, String logType) {
        if(this.mongoClient == null) {
            this.mongoClient = MongoClients.create(System.getProperty("mongodb.uri"));
        }
        try {
            MongoDatabase bd21LogDB = mongoClient.getDatabase("bd21Log");

            MongoCollection<Document> docCollection = bd21LogDB.getCollection(logType);
            document.append("clockTime", new BsonDateTime((new Date()).getTime()));
            docCollection.insertOne(document);
        } catch(Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
