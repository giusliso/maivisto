package it.maivisto.recommender;

/**
 * A set of three elements:
 * - the item ID
 * - the score for that item
 * - the number of occurrences
 */
public class OccScoreTriple implements Comparable<OccScoreTriple> {
	private long itemID;
	private double score;
	private int occurrences;

	public OccScoreTriple(long itemID, int occurrences, double score)  {
		this.itemID=itemID;
		this.score=score;
		this.occurrences=occurrences;
	}

	public long getItemID() {
		return itemID;
	}

	public double getScore() {
		return score;
	}

	public int getOccurrences() {
		return occurrences;
	}

	/**
	 * Sort elements first by number of occurrences, then by score
	 */
	@Override
	public int compareTo(OccScoreTriple o) {
		if(this.getOccurrences() < o.getOccurrences())
			return -1;
		else if(this.getOccurrences() > o.getOccurrences())
			return 1;
		else if(this.getScore() < o.getScore())
			return -1;
		else if(this.getScore() > o.getScore())
			return 1;	
		else
			return 0;
	}

}
