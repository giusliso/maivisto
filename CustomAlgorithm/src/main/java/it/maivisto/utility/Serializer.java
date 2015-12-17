package it.maivisto.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Serializer {

	private static final Logger logger = LoggerFactory.getLogger(Serializer.class);
	private Kryo kryo=null;
	
	/**
	 * Serializes an object.
	 * @param dirPath Directory in which serialize the object.
	 * @param obj Object to serialize.
	 * @param filename Name of the file.
	 */
	public Serializer(){
		 kryo = new Kryo();
	}
	
	public void serialize(String dirPath, Object obj, String filename){
		logger.info("Serializing "+filename+"...");
		try {
			File dir = new File(dirPath);
			dir.mkdir();
			Output output = new Output(new FileOutputStream(dir+File.separator+filename+".dat"));
			kryo.writeObject(output, obj);
			output.close();	
			logger.info("Serialized "+filename);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * Deserializes an object.
	 * @param dirPath Directory containing the file.
	 * @param filename Name of the file.
	 * @param cl Class of the object to deserialize.
	 */
	public <T> Object deserialize(String dirPath, String filename,Class<T> cl){
		logger.info("Deserializing "+filename+"...");
		try {
			Input input = new Input(new FileInputStream(dirPath+filename+".dat"));
			Object m=kryo.readObject(input,cl);
			input.close();
			logger.info("Deserialized "+filename);
			return m;
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		} 		
	}

	/**
	 * Registers a not serializable class to serialize.
	 * @param cl class not serializable.
	 */
	public <T> void register(Class<T> cl) {
		kryo.register(cl);
	}
	
}