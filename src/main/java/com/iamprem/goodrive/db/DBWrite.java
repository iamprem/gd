package com.iamprem.goodrive.db;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.iamprem.goodrive.entity.FilesMeta;
import com.iamprem.goodrive.filesystem.Attributes;
import com.iamprem.goodrive.main.App;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

/**
 * Created by prem on 8/23/15.
 */
public class DBWrite {

    private DBWrite(){
        // Private Constructor
    }

    // Insert details about the new files to db on the first run
    public static void insertFile(File file, java.io.File diskFile) throws IOException, SQLException {

        String id = file.getId();
        String localName = diskFile.getName();
        String remoteName = file.getTitle();
        String localPath = diskFile.getPath();
        String parentIds = "";
        List<ParentReference> parentList = file.getParents();
        if (parentList.size() == 1){
            parentIds = parentList.get(0).getId();
        } else{
            for (ParentReference parentReference : parentList) {
                parentIds = parentIds + parentReference.getId() + ";";
            }
        }
        String remoteStatus = "Synced";
        String localStatus = "Synced";
        //TODO CHECK THIS
        //long localModified = Files.getLastModifiedTime(diskFile.toPath()).toMillis();
        long localModified = file.getModifiedDate().getValue();
        String mimeType = file.getMimeType();

        Connection con = App.conn;

        Statement stmt = con.createStatement();
        String sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                "localmodified, mimetype) " +
                "VALUES ('"+id+"', '"+localName+"', '"+remoteName+"', '"+localPath+"', '"+parentIds+"', '"+remoteStatus
                +"', '"+localStatus+"',"+localModified+", '"+mimeType+"' );";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    public static void updateFileModified(FilesMeta fm) throws SQLException {

        String id = fm.getId();
        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET localmodified = "+fm.getLocalModified()+" WHERE id = '"+fm.getId()+"';";
        stmt.executeUpdate(sql);
        stmt.close();

    }

    //Only for ENTRY_MODIFY or ENTRY_DELETE
    public static void updateFileLocalStatus(String localPath, String localStatus) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET localstatus = '"+localStatus+"', localmodified = '"+new Date().getTime()
                +"' WHERE localpath = '"+localPath+"';";

        stmt.executeUpdate(sql);
        stmt.close();


    }

    public static void insertFile(String localPath, String localName, String localStatus) throws SQLException {

        String remoteName = localName;
        Connection con = App.conn;
        Statement stmt = con.createStatement();

        String sql1 = "SELECT * FROM files WHERE localpath = '"+localPath+"';";
        ResultSet rs = stmt.executeQuery(sql1);

        if (rs.next()){
            updateFileLocalStatus(localPath, localStatus);
        } else{
            String sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                    "localmodified, mimetype) " +
                    "VALUES (null, '"+localName+"', '"+remoteName+"', '"+localPath+"', null, null, '"+localStatus
                    +"',"+new Date().getTime()+", null );";
            stmt.executeUpdate(sql);
        }
        stmt.close();
    }


}
