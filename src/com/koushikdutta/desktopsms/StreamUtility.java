package com.koushikdutta.desktopsms;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtility {
	private static final String LOGTAG = StreamUtility.class.getSimpleName();
	public static int copyStream(InputStream input, OutputStream output) throws IOException
	{
		byte[] stuff = new byte[65536];
		int read = 0;
		int total = 0;
		while ((read = input.read(stuff)) != -1)
		{
			output.write(stuff, 0, read);
			total += read;
		}
		return total;
	}
	
	public static String readToEnd(InputStream input) throws IOException
	{
		DataInputStream dis = new DataInputStream(input);
		byte[] stuff = new byte[1024];
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		int read = 0;
		int total = 0;
		while ((read = dis.read(stuff)) != -1)
		{
			buff.write(stuff, 0, read);
			total += read;
		}
		
		return new String(buff.toByteArray());
	}

    static public String readFile(String filename) throws IOException {
        return readFile(new File(filename));
    }
    
    static public String readFile(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        DataInputStream input = new DataInputStream(new FileInputStream(file));
        input.readFully(buffer);
        return new String(buffer);
    }
    
    public static void writeFile(File file, String string) throws IOException {
        writeFile(file.getAbsolutePath(), string);
    }
    
    public static void writeFile(String file, String string) throws IOException {
        File f = new File(file);
        f.getParentFile().mkdirs();
        DataOutputStream dout = new DataOutputStream(new FileOutputStream(f));
        dout.write(string.getBytes());
        dout.close();
    }
}
