
package it.maivisto.baselines;

import java.util.List;
import java.util.TreeSet;

import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.maivisto.qualifiers.CoOccurrenceModel;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Co-Coverage Recommender. 
 * It recommends the list of the 'n' items that are highly co-rated by the users.
 */
public class CoCoverageRec extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(CoCoverageRec.class);

	private ItemDAO idao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private ItemItemModel coOccModel;

	@Inject
	public CoCoverageRec(ItemDAO idao, UserEventDAO uedao, @CoOccurrenceModel ItemItemModel coOccModel, ItemScorer scorer) {
		this.uedao = uedao;
		this.idao = idao;
		this.coOccModel = coOccModel;
		this.scorer = scorer;
	}

	/**
	 * It recommends a list of item to a user.
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @param candidates The candidate items.
	 * @param exclude The exclude set.
	 * @return The result list.
	 */
	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		TopNScoredItemAccumulator reclist = new TopNScoredItemAccumulator(n);		

		// remove rated items from the set of all items (catalog)
		LongSet recommendableItems = idao.getItemIds();
		if(userHistory != null) 			
			for(Rating rate : userHistory)
				recommendableItems.remove(rate.getItemId());		

		TreeSet<CoCoverage> coOccTree = new TreeSet<CoCoverage>(); // items sorted by co-coverage
		for(Long itemId : recommendableItems){
			SparseVector vec = coOccModel.getNeighbors(itemId);
			double cocoverage = vec.sum();
			coOccTree.add(new CoCoverage(itemId, cocoverage, 0));
		}
		logger.info("Sorted {} items by co-coverage", recommendableItems.size());

		TreeSet<CoCoverage> recTree = new TreeSet<CoCoverage>(); 

		double lastCocov=0;
		int count = 0;
		for(CoCoverage coOcc : coOccTree) {
			if(count!=n){
				recTree.add(new CoCoverage(coOcc.getItem(),coOcc.getCoCoverage(),scorer.score(user, coOcc.getItem())));
				lastCocov = coOcc.getCoCoverage();
				count++;
			}
			else
				if(coOcc.getCoCoverage() == lastCocov)
					recTree.add(new CoCoverage(coOcc.getItem(),coOcc.getCoCoverage(),scorer.score(user, coOcc.getItem())));
				else
					break;
		}

		// add n items to the recommendations list
		count = 0;
		for(CoCoverage coOcc : recTree)
			if(count!=n){
				reclist.put(coOcc.getItem(),scorer.score(user, coOcc.getItem()));
				count++;
			}

		return reclist.finish(); 
	}

	/**
	 * Inner class that stores information about the co-coverage of an item.
	 * It stores a triple of values < itemID , co-coverage , score >.
	 */
	private class CoCoverage implements Comparable<CoCoverage> {
		private final double cocoverage;
		private final double score;
		private final long item;

		public CoCoverage(long item, double cocoverage, double score) {
			this.item=item;
			this.cocoverage=cocoverage;
			this.score=score;
		}

		public double getCoCoverage() {
			return cocoverage;
		}

		public long getItem() {
			return item;
		}

		public double getScore() {
			return score;
		}

		@Override
		public String toString() {
			return "[item: "+item+", cocoverage: "+cocoverage+", score: "+score+"]";
		}

		/**
		 * Sorts values by co-coverage, by score and then by item.
		 */
		@Override
		public int compareTo(CoCoverage o) {
			if(cocoverage < o.getCoCoverage())
				return 1;
			else if(cocoverage > o.getCoCoverage())
				return -1;
			else if(score < o.getScore())
				return 1;
			else if (score > o.getScore())
				return -1;
			else if(item < o.getItem())
				return 1;
			else if (item > o.getItem())
				return -1;
			else
				return 0;
		}
	}
}