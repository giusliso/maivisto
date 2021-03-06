package it.maivisto.utility;

import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;

/**
 * Utility methods.
 */
public class Utilities {
	/**
	 * Converts the double "oldVal" from the range [oldMin,oldMax] to the range [newMin, newMax]
	 * @param oldMin 
	 * @param oldMax
	 * @param oldVal
	 * @param newMin
	 * @param newMax
	 * @return the normalized value of oldVal
	 */
	public static double normalize(double oldMin, double oldMax, double oldVal, double newMin, double newMax) {
		double scale = (newMax-newMin)/(oldMax-oldMin);
		return newMin + ( (oldVal-oldMin) * scale );
	}

	/**
	 * Returns the mean value of user ratings.
	 * @param userHistory The user ratings.
	 * @return The mean value.
	 */
	public static double meanValue(UserHistory<Rating> userHistory){
		double sum=0.0;
		for(Rating rate : userHistory) 
			sum += rate.getValue();
		if(userHistory.isEmpty())
			return sum;
		else
			return sum/userHistory.size();
	}
}
