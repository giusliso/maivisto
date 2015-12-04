package it.maivisto.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Serializer {

	private static final Logger logger = LoggerFactory.getLogger(Serializer.class);
	
	/**
	 * Serialize an object
	 * @param dirPath directory in which serialize the object
	 * @param obj object to serialize
	 * @param filename name of the file
	 */
	public void serialize(String dirPath, Object obj, String filename){
		logger.info("Serializing "+filename+"...");
		try {
			File dir = new File(dirPath);
			dir.mkdir();
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dir+filename+".dat"));
			out.writeObject(obj);
			out.close();
			logger.info("Serialized "+filename);
		} catch (IOException e) {
			logger.error(e.getStackTrace().toString());
		}
	}
	
	/**
	 * Deserialize an object.
	 * @param dirPath directory containing the file
	 * @param filename name of the file
	 */
	public Object deserialize(String dirPath, String filename){
		logger.info("Deserializing "+filename+"...");
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(dirPath+filename+".dat"));
			Object m = in.readObject();
			in.close();
			logger.info("Deserialized "+filename);
			return m;
		} catch (FileNotFoundException e) {
			logger.error(e.getStackTrace().toString());
		} catch (IOException e) {
			logger.error(e.getStackTrace().toString());
		} catch (ClassNotFoundException e) {
			logger.error(e.getStackTrace().toString());
		}
		return null;
	}
}
