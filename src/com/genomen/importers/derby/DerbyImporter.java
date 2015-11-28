package com.genomen.importers.derby;

import com.genomen.core.Configuration;
import com.genomen.dao.ContentDAO;
import com.genomen.dao.DAOFactory;
import com.genomen.dao.DataSetDAO;
import com.genomen.dao.DerbyDAO;
import com.genomen.dao.DerbyDAOFactory;
import com.genomen.dao.TaskDAO;
import com.genomen.entities.DataEntityAttributeValue;
import com.genomen.entities.DataType;
import com.genomen.entities.DataTypeManager;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Base class for importers importing to Derby database
 * @author ciszek
 */
public abstract class DerbyImporter extends DerbyDAO {

    
    public static final int INVALID_ID = -1;

    /**
     * Inserts a list of samples into the sample table.
     * @param names a list of sample names
     * @return <code>true</code> if individuals were successfully inserted, <code>false</code> otherwise.
     */
    protected boolean insertIndividuals( List<String> names ) {
        
        boolean success = true;
        
        String insertStatement = "INSERT INTO " + Configuration.getConfiguration().getDatabaseTempSchemaName() + ".Individuals ( INDIVIDUAL_ID)  " + " VALUES ( ? )";
              
        Connection connection = null;
        PreparedStatement statement = null;
             
        try {
            connection = DerbyDAOFactory.createConnection();
        }
        catch (Exception ex) {
            Logger.getLogger( DerbyImporter.class ).debug(ex);
            success = false;
        }

        try {
            statement = connection.prepareStatement(insertStatement);
            
            for ( String name : names) {
                statement.setString(1, name);
                statement.addBatch();
            }
         
            statement.executeBatch();
            statement.close();


        } catch (SQLException ex) {
            Logger.getLogger( DerbyImporter.class ).debug(ex);
            success = false;
        }
        finally {
            closeConnection(connection);
        }
        return success;
    }

    /**
     * Bulk imports a preformed table presentation of a dataset into the database.
     * @param schemaName Name of the schema used for the dataset
     * @param individualID id of the sample to which the inserted dataset is associated
     * @param type Type of the data
     * @param file File that stores the table presentation of the dataset 
     */
    public void bulkImport( String schemaName, String individualID, String type, File file) {
        
        DataSetDAO dataSetDAO = DAOFactory.getDAOFactory().getDataSetDAO();
        ContentDAO contentDAO = DAOFactory.getDAOFactory().getContentDAO();
        DataType dataType = DataTypeManager.getDataType(type);
        String tableName = dataSetDAO.createTableName(individualID.toUpperCase(), dataType );

        
        //If table for this data type has not been already created
        if ( !contentDAO.tableExists(schemaName, tableName)) {
            //Create a new table for this data type.
            dataSetDAO.createDataTable(schemaName, individualID, dataType );               
        }
        
        Connection connection = null;
        try {
            connection = DerbyDAOFactory.createConnection();
        }
        catch (Exception ex) {
            Logger.getLogger(DerbyImporter.class ).debug(ex);           
            return;
        }

        try {           
            Statement statement = connection.createStatement();
            statement.executeUpdate("CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE ('" + schemaName + "', '" + tableName + "', '" + file.getAbsolutePath() + "'" + ",'\t',null,null,0)");
            statement.close();
            connection.close();
        }
        catch (SQLException ex) {
            Logger.getLogger(DerbyImporter.class ).debug(ex);          
        }

    }
    
    /**
     * Creates a tab separated tuple that presents a data entity as a single row.
     * @param id An unique ID for the data
     * @param attributes Attributes of a data entity presented as a set of <code>DataEntityAttributeValue</code> instances
     * @return String representation of a data entity
     */
    protected String createTuple( int id, HashMap<String, DataEntityAttributeValue> attributes, String type ) {

        StringBuilder tuple = new StringBuilder();
       
        DataType dataType = DataTypeManager.getDataType(type);
        
        List<String> dataTypeAttributes = dataType.getAttributeNames();
        for ( int i = 0; i < dataTypeAttributes.size(); i++) {
            DataEntityAttributeValue dataTypeAttribute = attributes.get(dataTypeAttributes.get(i));

            String value = dataTypeAttribute.getString();

            tuple.append(value);
            tuple.append("\t");            
        }
        tuple.append(id);

        return tuple.toString();

    }    
    
    protected String findFile( String[] fileArray, String ending ) {
        
        for ( int i = 0; i < fileArray.length; i++ ) {
            String regexp = ".+".concat(ending).concat("$");
            if ( fileArray[i].matches(regexp)) {
                return fileArray[i];
            }
            
        }
        return null;
    }
    
    protected int getCurrentId( String individualID, String type ) {
        
        DataType dataType = DataTypeManager.getDataType(type);
        
        if ( dataType == null ) {
            return INVALID_ID;
        }
        
        ContentDAO contentDAO = DAOFactory.getDAOFactory().getContentDAO();
        DataSetDAO datasetDAO = DAOFactory.getDAOFactory().getDataSetDAO();
        if ( !contentDAO.tableExists( Configuration.getConfiguration().getDatabaseTempSchemaName(),  datasetDAO.createTableName( individualID,dataType))) {
            return 0;
        }
        

        return datasetDAO.getCurrentId(Configuration.getConfiguration().getDatabaseTempSchemaName(), individualID.toUpperCase(), dataType);
    }

}
