package it.maivisto.baselines;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
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
 * Random Popularity Recommender. 
 * It extends the list of 'n-1' randomly selected items by inserting the most popular item, 
 * i.e. the item with the highest number of ratings.
 */
public class RandomPopularityRec extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(RandomPopularityRec.class);

	private ItemDAO idao;
	private ItemEventDAO iedao;
	private UserEventDAO uedao;
	private ItemScorer scorer;

	@Inject
	public RandomPopularityRec(ItemDAO idao, UserEventDAO uedao, ItemEventDAO iedao, ItemScorer scorer) {
		this.uedao = uedao;
		this.idao = idao;
		this.iedao = iedao;
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

		// add the most popular item to recommendation list
		logger.info("Getting the most popular item");
		long mostPopularItem=-1;
		int maxPopularity=0;
		for(Long itemId : recommendableItems){
			int pop = iedao.getEventsForItem(itemId).size();
			if(pop>maxPopularity){
				mostPopularItem=itemId;
				maxPopularity=pop;
			}
		}
		logger.info("Added most popular item {}", mostPopularItem);
		reclist.put(mostPopularItem, scorer.score(user, mostPopularItem));

		// add n-1 randomly selected items to recommendation list
		logger.info("Randomly selecting {} items", n-1);
		Random random = new Random();
		Object[] recItems = recommendableItems.toArray();	
		HashSet<Integer> chosenItem = new HashSet<Integer>();
		while(chosenItem.size() != n-1){
			int i = random.nextInt(recItems.length);
			if(!chosenItem.contains(i)){
				chosenItem.add(i);
				long item = (long) recItems[i];
				logger.info("Added randomly selected item {}", item);
				reclist.put(item, scorer.score(user, item));
			}
		}

		return reclist.finish();
	}
}