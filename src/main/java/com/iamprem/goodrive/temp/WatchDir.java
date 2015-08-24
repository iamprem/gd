package com.iamprem.goodrive.temp;

import com.iamprem.goodrive.db.DBWrite;
import com.iamprem.goodrive.service.GoogleDriveServices;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.LinkOption.*;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by prem on 8/23/15.
 */
public class WatchDir {

    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;

    // Creates a WatchService and registers the given directory
    WatchDir(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    //Register the given directory with the WatchService
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    //Register the given directory, and all its sub-directories, with the WatchService.
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }



    /**
     * Process all events for keys queued to the watcher
     */
    private void processEvents() throws SQLException {

        while(true){
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                System.out.println("Stopping Directory watch -- Application is offline!");
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {

                WatchEvent.Kind kind = event.kind();
                if (kind == OVERFLOW) {
                    System.err.println("Overflow of events occured");
                    System.exit(1);
                }
                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);
                System.out.format("%s: %s\n", event.kind().name(), child);

                switch (event.kind().name()){

                    case "ENTRY_CREATE":
                        System.out.println("Created some file");
                        DBWrite.insertFile(child.toString(), name.toString(), "ENTRY_CREATE");
                        break;

                    case "ENTRY_DELETE":
                        System.out.println("Deleted some file");
                        DBWrite.updateFileLocalStatus(child.toString(), "ENTRY_DELETE");
                        break;

                    case "ENTRY_MODIFY":
                        System.out.println("Modified some file");
                        DBWrite.updateFileLocalStatus(child.toString(), "ENTRY_MODIFY");
                        break;

                }


                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            }
            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }


    public static void main(String[] args) throws IOException, SQLException {

        boolean recursive = true;
        // register directory and process its events
        Path dir = Paths.get(GoogleDriveServices.HOME_DIR);
        new WatchDir(dir, recursive).processEvents();
    }
}
