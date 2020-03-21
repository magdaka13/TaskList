/*
 * Copyright 2017 Actian Corporation
 */
package com.actian.zen.tasklist;

import com.actian.zen.db.Btrieve;
import com.actian.zen.db.BtrieveClient;
import com.actian.zen.db.BtrieveFile;
import com.actian.zen.db.BtrieveFileAttributes;
import com.actian.zen.db.BtrieveIndexAttributes;
import com.actian.zen.db.BtrieveKeySegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *  The TaskTable class.  Tasks are stored in a table in the Zen database.
 *  The TaskTable class encapsulates the database record layout and associated
 *  operations.
 */
public class TaskTable {
    BtrieveClient client;
    String mTablePath;

    //  Task records have two fields
    //  1) ID - a unique (auto-increment) number
    //  2) TITLE - a null terminated string (max length of 32).
    //
    //  Begin Record layout
    //   0 ------------- ID_OFFSET=0
    //           /|\
    //     ID     |      ID_SIZE=4
    //           \|/
    //   4 ------------- TITLE_OFFSET=0+4
    //           /|\
    //     TITLE  |      TITLE_SIZE=32
    //           \|/
    //  36 ------------- RECORD_SIZE=4+32

    static final int ID_OFFSET = 0;
    static final int ID_SIZE = 4;
    static final int TITLE_OFFSET = ID_SIZE;
    static final int TITLE_SIZE = 32;
    static final int RECORD_SIZE = ID_SIZE+TITLE_SIZE;
    // End Record layout

    public int getTitleSize() { return TITLE_SIZE;    }

    protected BtrieveClient getClient() {
        return client;
    }

    protected String getPath() {
        return mTablePath;
    }

    // Given a raw byte array representing a record, construct
    // a TaskRecord object.
    public TaskRecord getRecord (byte[] rawbuf) {
        ByteBuffer recbuf = ByteBuffer.wrap(rawbuf);
        recbuf.order(ByteOrder.LITTLE_ENDIAN);
        int offset = ID_OFFSET;
        recbuf.position(offset);
        int id = recbuf.getInt();
        offset += ID_SIZE;
        String title = ZenDBHelper.getZString(recbuf, offset, TITLE_SIZE);
        return new TaskRecord(id, title);
    }

    // Pack a TaskRecord object into a byte array suitable for
    // inserting into the database.
    public byte[] putRecord(TaskRecord task) {
        byte[] rawbuf = new byte[RECORD_SIZE];
        ByteBuffer rec = ByteBuffer.wrap(rawbuf);
        rec.order(ByteOrder.LITTLE_ENDIAN);
        // For new records the id field mst be zero.
        // Database will assign auto-increment value.
        rec.putInt(task.get_id());
        String title = task.getTitle();
        // for simplicity we truncate title down to the max size.
        if (title.length() >= TITLE_SIZE) {
            title = title.substring(0, TITLE_SIZE-1);
        }
        ZenDBHelper.putZString(rec, TITLE_OFFSET, TITLE_SIZE, title);
        return rawbuf;
    }

    // Class constructor
    public TaskTable(String filepath) {
        client = new BtrieveClient(0xAAAB, 1001);
        mTablePath = filepath;
        createIfNeeded(filepath);
    }

    // Create the Zen database table if not already present.
    public void createIfNeeded(String filepath) {
        BtrieveClient client = new BtrieveClient(0xAAAD, 0x0102);
        BtrieveFileAttributes f_attrs = new BtrieveFileAttributes();
        Btrieve.StatusCode status = f_attrs.SetFixedRecordLength(RECORD_SIZE);
        if (status == Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
            status = client.FileCreate(f_attrs, filepath, Btrieve.CreateMode.CREATE_MODE_NO_OVERWRITE);
            if (status == Btrieve.StatusCode.STATUS_CODE_FILE_ALREADY_EXISTS) {
                return;
            }
        }

        // Make an index on the ID field.
        if (status == Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
            BtrieveIndexAttributes iattrs = new BtrieveIndexAttributes();
            BtrieveKeySegment ks = new BtrieveKeySegment();
            ks.SetField(0, ID_SIZE, Btrieve.DataType.DATA_TYPE_AUTOINCREMENT);
            iattrs.AddKeySegment(ks);

            BtrieveFile handle = new BtrieveFile();
            status = client.FileOpen(handle, filepath, null, Btrieve.OpenMode.OPEN_MODE_NORMAL);
            if (status == Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
                status = handle.IndexCreate(iattrs);
                client.FileClose(handle);
            }
        }

        if(status != Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
            ZenDBHelper.raise_DbException(status);
        }
    }

}

/**
 * The TaskCursor class a cursor or handle to the TaskTable.
 */
class TaskCursor {
    private BtrieveFile m_handle;
    private TaskTable m_table;

    TaskCursor(TaskTable table) {
        m_handle = new BtrieveFile();
        m_table = table;

        Btrieve.StatusCode status=table.getClient().FileOpen(m_handle, table.getPath(), null,
                                                             Btrieve.OpenMode.OPEN_MODE_NORMAL);
        if (status != Btrieve.StatusCode.STATUS_CODE_NO_ERROR)
            ZenDBHelper.raise_DbException(status, String.format("Opening %s failed", table.getPath()));

    }

    TaskRecord getRecord(byte[] rawbuf) {
        return  m_table.getRecord(rawbuf);
    }

    // Find the record which has the given id field.
    public TaskRecord lookupById(int _id) {
        // Set up a key buffer with the id of interest.
        byte[] keybuf = new byte[TaskTable.ID_SIZE];
        ByteBuffer.wrap(keybuf).order(ByteOrder.LITTLE_ENDIAN).putInt(_id);
        byte[] rawbuf = new byte[TaskTable.RECORD_SIZE];
        int count=m_handle.RecordRetrieve (Btrieve.Comparison.COMPARISON_EQUAL,
                                           Btrieve.Index.INDEX_1,
                                           keybuf, rawbuf, Btrieve.LockMode.LOCK_MODE_NONE);
        if (count < 0)
            ZenDBHelper.raise_DbException(m_handle.GetLastStatusCode());
        return getRecord(rawbuf);
    }

    // Retrieve the first record using the specified index.
    // index may be Btrieve.INDEX_NONE.
    public TaskRecord retrieveFirst(Btrieve.Index index) {
        byte[] rawbuf = new byte[TaskTable.RECORD_SIZE];
        int count = m_handle.RecordRetrieveFirst(index, rawbuf);
        if (count < 0) {
            Btrieve.StatusCode status = m_handle.GetLastStatusCode();
            if (status == Btrieve.StatusCode.STATUS_CODE_END_OF_FILE) {
                return null;
            } else {
                ZenDBHelper.raise_DbException(status);
            }
        }
        return getRecord(rawbuf);
    }

    public TaskRecord retrieveNext() {
        byte[] rawbuf = new byte[TaskTable.RECORD_SIZE];
        int count = m_handle.RecordRetrieveNext(rawbuf);
        if (count < 0) {
            Btrieve.StatusCode status = m_handle.GetLastStatusCode();
            if (status == Btrieve.StatusCode.STATUS_CODE_END_OF_FILE) {
                return null;
            } else {
                ZenDBHelper.raise_DbException(status);
            }
        }
        return getRecord(rawbuf);
    }

    // Insert a new TaskRecord object into the task table.
    public void insert(TaskRecord task) {
        // Note: The id field should have been initialized to 0.
        // The database will assign a value.
        byte[] rawbuf =  m_table.putRecord(task);
        Btrieve.StatusCode status = m_handle.RecordCreate(rawbuf);
        if (status != Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
            ZenDBHelper.raise_DbException(status);
        }
    }

    // Delete the current record (record that the cursor is positioned on).
    public void deleteById(int _id) {
        TaskRecord task = lookupById(_id);
        Btrieve.StatusCode status = m_handle.RecordDelete();
        if (status != Btrieve.StatusCode.STATUS_CODE_NO_ERROR) {
            ZenDBHelper.raise_DbException(status);
        }
    }

    // Close table when done.  Flushes any pending change operations.
    public void close() {
        m_table.getClient().FileClose(m_handle);
    }
}

/**
 * Helper class to facilitate scanning the TaskTable.
 */
class TaskRecordIterator implements Iterator<TaskRecord> {

    private boolean seekfirst;
    private TaskRecord m_prefetch;
    private Btrieve.Index m_index;
    private TaskCursor m_cursor;

    public TaskRecordIterator(boolean useCursorPosition) {
        if (useCursorPosition)
            seekfirst = false;
        else
            seekfirst = true;
        m_prefetch = null;
        m_index = Btrieve.Index.INDEX_NONE;
    }

    public TaskRecordIterator(TaskCursor cursor) {
        seekfirst = true;
        m_prefetch = null;
        m_index = Btrieve.Index.INDEX_1;
        m_cursor = cursor;
    }

    public boolean hasNext() {
        // In order to implement the hasNext() Iterator<> method
        // we have to actually go and retrieve the next record.
        if (m_prefetch == null) {
            byte[] rawbuf = new byte[TaskTable.RECORD_SIZE];
            Btrieve.StatusCode status = Btrieve.StatusCode.STATUS_CODE_NO_ERROR;
            if (seekfirst) {
                m_prefetch = m_cursor.retrieveFirst(m_index);
            } else {
                m_prefetch = m_cursor.retrieveNext();
            }
            seekfirst = false;
        }
        if (m_prefetch != null)
            return true;
        return false;
    }

    public TaskRecord next() {
        if (hasNext()) {
            TaskRecord task = m_prefetch;
            m_prefetch = null;
            return task;
        }
        throw new NoSuchElementException();
    }
}

