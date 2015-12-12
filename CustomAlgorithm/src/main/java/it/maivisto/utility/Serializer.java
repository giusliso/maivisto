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
			Kryo kryo = new Kryo();
			kryo.register(obj.getClass());
			Output output = new Output(new FileOutputStream(dir+File.separator+filename+".dat"));
			kryo.writeObject(output, obj);
			output.close();	
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
	public <T> Object deserialize(String dirPath, String filename,Class<T> cl){
		logger.info("Deserializing "+filename+"...");
		try {
			Kryo kryo = new Kryo();
			kryo.register(cl);
			Input input = new Input(new FileInputStream(dirPath+filename+".dat"));
			Object m=kryo.readObject(input, cl);
			input.close();
			logger.info("Deserialized "+filename);
			return m;
		} catch (Exception e) {
			logger.error(e.getStackTrace().toString());
			return null;
		} 		
	}	
}
