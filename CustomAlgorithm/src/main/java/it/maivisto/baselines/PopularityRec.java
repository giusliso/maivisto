package it.maivisto.baselines;

import java.util.List;
import java.util.TreeSet;

import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Popularity Recommender. 
 * It recommends the list of the 'n' most popular items, i.e. those with the highest number of ratings.
 */
public class PopularityRec extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(PopularityRec.class);

	private ItemDAO idao;
	private ItemEventDAO iedao;
	private UserEventDAO uedao;
	private ItemScorer scorer;

	@Inject
	public PopularityRec(ItemDAO idao, UserEventDAO uedao, ItemEventDAO iedao, ItemScorer scorer) {
		this.uedao = uedao;
		this.idao = idao;
		this.iedao = iedao;
		this.scorer = scorer;
	}


	/**
	 * Recommend a list of item to a user
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @param candidates The candidate items
	 * @param exclude The exclude set
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

		TreeSet<Popularity> popTree = new TreeSet<>(); // items sorted by popularity
		for(Long itemId : recommendableItems)
			popTree.add(new Popularity(itemId, iedao.getEventsForItem(itemId).size()));
		logger.info("Sorted {} items by popularity", recommendableItems.size());

		// add n items to the recommendations list
		int lastPop=0;
		int count = 0;
		for(Popularity pop : popTree) {
			if(count!=n){
				reclist.put(pop.getItem(),scorer.score(user, pop.getItem()));
				lastPop = pop.getPopularity();
				count++;
			}
			else
				if(pop.getPopularity() == lastPop)
					reclist.put(pop.getItem(),scorer.score(user, pop.getItem()));
				else
					break;
		}

		return reclist.finish();
	}

	/**
	 * Inner class that stores information about the popularity of an item.
	 * It stores a couple of values < itemID , number of ratings >.
	 */
	private class Popularity implements Comparable<Popularity> {
		private final int popularity;
		private final long item;

		public Popularity(long item, int popularity) {
			this.item=item;
			this.popularity=popularity;
		}

		public int getPopularity() {
			return popularity;
		}

		public long getItem() {
			return item;
		}

		@Override
		public String toString() {
			return "[item: "+item+", popularity: "+popularity+"]";
		}

		/**
		 * Sorts values by popularity and then by item
		 */
		@Override
		public int compareTo(Popularity o) {
			if(popularity < o.getPopularity())
				return 1;
			else if(popularity > o.getPopularity())
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
