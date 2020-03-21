/*
 * Copyright 2017 Actian Corporation
 */
package com.actian.zen.tasklist;

import android.content.Context;
import android.content.res.AssetManager;

import com.actian.zen.db.Btrieve;
import com.actian.zen.db.DbManager;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;

public class ZenDBHelper {

    public static void raise_DbException(Btrieve.StatusCode status, String message) {
        String exc_msg = String.format("%s: %s", message, Btrieve.StatusCodeToString(status));
        RuntimeException exc = new RuntimeException(exc_msg);
        throw exc;
    }

    public static void raise_DbException(Btrieve.StatusCode status) {
        String exc_msg = Btrieve.StatusCodeToString(status);
        RuntimeException exc = new RuntimeException(exc_msg);
        throw exc;
    }

    // Helper function to decode a UTF-8 encoded string that
    // has been retrieved from a record.
    private static String decodeString(ByteBuffer in_bb) {
        Charset charset = Charset.forName("UTF-8");
        CharsetDecoder decoder = charset.newDecoder();
        CharBuffer outcb;
        try {
            outcb = decoder.decode (in_bb);
        }
        catch (CharacterCodingException ex) {
            throw new RuntimeException ("encodeString: CharCodingException");
        }
        String out_s = outcb.toString();
        return out_s;
    }

    // Helper function to encode a String in UTF-8 so that it can be
    // stored in a database record.
    private static ByteBuffer encodeString(String in_str) {
        CharBuffer cb = CharBuffer.wrap(in_str);
        Charset charset = Charset.forName("UTF-8");
        CharsetEncoder encoder = charset.newEncoder();
        ByteBuffer outbb;
        try {
            outbb = encoder.encode (cb);
        }
        catch (CharacterCodingException ex) {
            throw new RuntimeException ("encodeString: CharCodingException");
        }
        return outbb;
    }

    //
    // Helper function to extract a ZString from the record buffer.
    protected static String getZString(ByteBuffer rec, int offset, int length) {
        ByteBuffer in_bb = rec.asReadOnlyBuffer();
        in_bb.position(offset);
        in_bb.limit(offset+length);
        // Locate null terminator.  Note: length is a maximum value -- the
        // actual length of the string may be smaller.
        while (in_bb.hasRemaining()) {
            byte ch = in_bb.get();
            if (ch == 0)
                break;
        }
        int newlimit = in_bb.position();
        newlimit -= 1;
        in_bb.position(offset);
        in_bb.limit(newlimit);

        String out_s = decodeString(in_bb);
        // update position to end of field.
        rec.position(offset+length);
        return out_s;
    }

    //
    // Helper function to pack a ZString into a record buffer at the specified
    // offset.
    protected static void putZString (ByteBuffer rec, int offset, int length, String str) {
        if (str == null)
            str = "";
        ByteBuffer enc = encodeString(str);
        //enc.put((byte) 0);
        int remaining = enc.remaining();
        if (remaining < length) {
            rec.position(offset);
            rec.put(enc);
            rec.put((byte) 0);
        } else {
            // **exc** invalid length for field.
            throw new RuntimeException("Invalid field length: " + remaining + " >= " + length);
        }
    }

    // Extract Zen configuration file zendb.config from assets.
    // Initialize database engine library passing it the configuration file.
    public static void Initialize (Context context) {
        AssetManager assetManager = context.getAssets();

        try {
            File filesDir = context.getFilesDir();
            File outfilepath = new File(filesDir, "zendb.config");
            if (! outfilepath.exists()) {
                System.out.println("Did not find zendb.config");
                // Extract zendb.config from assets and copy to files dir.
                extractFileFromAssets(assetManager, "zendb.config", outfilepath);
                System.out.println("Finished extracting zendb.config");
            }

            DbManager.Initialize(filesDir.getAbsolutePath());
        } catch (IOException ex) {
            throw new RuntimeException("Exception " + ex.getMessage());
        }
    }

    public static void extractFileFromAssets(AssetManager mgr, String assetName, File outfilepath) throws IOException {
        InputStream infile = mgr.open(assetName, AssetManager.ACCESS_BUFFER);
        FileOutputStream outfile = new FileOutputStream(outfilepath);
        byte[] buffer = new byte[4096];
        int nread = 0;
        while ((nread = infile.read(buffer)) != -1) {
            outfile.write(buffer, 0, nread);
        }
        infile.close();
        outfile.close();
    }

    static {
        System.loadLibrary("btrievecppjni");
    }

}
