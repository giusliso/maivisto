package it.maivisto.recommender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdBuilder;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.maivisto.qualifiers.CoOccurrenceModel;
import it.maivisto.qualifiers.CosineSimilarityModel;
import it.maivisto.qualifiers.ItemContentSimilarityModel;
import it.maivisto.recommender.RecommendationTriple;
import it.maivisto.utility.Utilities;
import it.unimi.dsi.fastutil.longs.Long2DoubleArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;


public class SeedRecommender extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(SeedRecommender.class);

	private EventDAO dao;
	private ItemDAO idao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private final int neighborhoodSize;
	private boolean activate_standard_seed = true;
	private ModelsManager models;
	private Set<Long> seed_itemset = null;

	@Inject
	public SeedRecommender(EventDAO dao, ItemDAO idao, UserEventDAO uedao, 
			ItemScorer scorer, 
			@CoOccurrenceModel ItemItemModel coOccModel, @CosineSimilarityModel ItemItemModel cosModel,
			@ItemContentSimilarityModel ItemItemModel contModel,@NeighborhoodSize int nnbrs) {
		this.uedao = uedao;
		this.dao = dao;
		this.idao = idao;
		this.scorer = scorer;
		this.neighborhoodSize = nnbrs;
		this.models = new ModelsManager();
		this.models.addModel(contModel, 1.0);
		this.models.addModel(cosModel, 1.0);
		this.models.addModel(coOccModel, 1.0);
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
		if(userHistory == null || userHistory.size() < 20)
			return coldStartSituationAlgorithm(user, n);
		else
			return noColdStartSituationAlgorithm(user, n);	
	}

	/**
	 * Implements the algorithm to use in case of cold start situation (i.e. user ratings < 20).
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @return The result list.
	 */
	private List<ScoredId> coldStartSituationAlgorithm(long user, int n) {

		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);

		logger.info("User {} rated {} items --> Cold Start situation", user, ((userHistory != null) ? userHistory.size() : 0));

		LinkedList<RecommendationTriple> reclist = new LinkedList<RecommendationTriple>();
		Set<Long> recItemsSet = new HashSet<Long>();

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap(); // <itemId,score>

		if(this.activate_standard_seed) {
			SeedItemSet set = new SeedItemSet(dao);
			Set<Long> seeds = set.getSeedItemSet();
			for (long seed : seeds) {
				double score = scorer.score(user, seed);
				seedMap.put(seed, score);	

				// if user hasn't rated the seed yet, add it to recommendations list
				if(!hasRatedItem(userHistory, seed)){
					// there is no origin similarity matrix for standard seed items. 
					// We set matID=-1 and weight=1
					reclist.add(new RecommendationTriple(seed, score, -1)); 
					recItemsSet.add(seed);
					logger.info("Standard seed {} added with score {}", seed, score);
				}
			}

			logger.info("Added standard seeds");
		}

		// add external seed items, if available 
		if(this.seed_itemset != null) {
			for (long seed : seed_itemset)
				if(!seedMap.containsKey(seed)) {
					double score = scorer.score(user, seed);
					seedMap.put(seed, score);		
					logger.info("Added seed {} from seed_itemset", seed);
				}
		}

		// add positive user ratings to seedmap
		if(userHistory != null) {			
			double meanUserRating = Utilities.meanValue(userHistory);
			logger.info("Ratings mean value for user {}: {}", user, meanUserRating);
			for(Rating rate : userHistory)
				if(rate.getValue() >= meanUserRating)
					seedMap.put(rate.getItemId(),rate.getValue());			
		}

		int k=0;
		int neighsSize = Integer.MAX_VALUE;
		while(k<neighsSize && recItemsSet.size() < n) {
			for (Long s : seedMap.keySet()){
				Set<Integer> matIdsSet = models.modelsIdSet();
				for(Integer matID : matIdsSet) {
					ItemItemModel model = models.getModel(matID);
					SparseVector neighbors = model.getNeighbors(s);
					if(neighsSize > neighbors.size())
						neighsSize = neighbors.size();
					if (!neighbors.isEmpty()){
						LongArrayList neighs = neighbors.keysByValue(true);
						Long i = neighs.get(k);
						if (!seedMap.containsKey(i) ) {
							double simISnorm = neighbors.get(i);
							double scoreSeed = seedMap.get(s);
							reclist.add(new RecommendationTriple(i, simISnorm*scoreSeed, matID));
							recItemsSet.add(i);
						}
					}
				}
			}
			k++;
		}

		return getRankedRecommendationsList(n, reclist);
	}

	/**
	 * Implements the algorithm to use not in case of cold start situation (i.e. user ratings ≥ 20).
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @return The result list.
	 */
	private List<ScoredId> noColdStartSituationAlgorithm(long user, int n){
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);

		logger.info("[ User {} rated {} items --> Not in Cold Start situation ]", user, userHistory.size());

		TopNScoredItemAccumulator reclist = new TopNScoredItemAccumulator(n);		

		// set of all items (catalog)
		LongSet recommendableItems = idao.getItemIds();

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap(); // consider external seed items and rated items by user to score recommendable items

		// remove external seed item from catalog, if available
		if(this.seed_itemset != null) {
			for (long seed : seed_itemset){
				double score = scorer.score(user, seed);			
				seedMap.put(seed, score);
				recommendableItems.remove(seed);
				logger.info("Removed seed_item {} (score: {}) from catalog", seed, score);
			}
		}

		// remove rated items by user from catalog
		for (Rating rating : userHistory){
			seedMap.put(rating.getItemId(), rating.getValue());		
			recommendableItems.remove(rating.getItemId());
			logger.info("Removed rated item {} (rate: {}) from catalog", rating.getItemId(), rating.getValue());
		}

		logger.info("Scoring {} items.", recommendableItems.size());
		for(long itemId : recommendableItems){
			double recscoreI=0;
			double weightI=0; 
			Set<Integer> matIdsSet = models.modelsIdSet();		
			for(Integer matID : matIdsSet) {
				ItemItemModel model = models.getModel(matID);

				// get the "neighborhoodSize" neighbors of the considered item
				SparseVector neighbors = model.getNeighbors(itemId);
				LongList neighs = (!neighbors.isEmpty()) ? neighbors.keysByValue(true) : new LongArrayList();
				int nnbrs = (neighs.size() >= neighborhoodSize) ? neighborhoodSize : neighs.size();
				neighs = neighs.subList(0, nnbrs);

				for (long neigh : neighs) {
					// intersection between neighbors and rated items
					if(seedMap.containsKey(neigh)){
						double simISnorm = neighbors.get(neigh);
						double scoreSeed = seedMap.get(neigh);
						recscoreI += simISnorm*scoreSeed*models.getModelWeight(matID);
						weightI += models.getModelWeight(matID);
					}
				}			
			}
			if(!Double.isNaN(recscoreI/weightI))
			reclist.put(itemId, recscoreI/weightI);
		}

		return reclist.finish();
	}

	/**
	 * Sorts the list of possible recommendations. 
	 * Since the list can contain multiple instance of the same item coming from different similarity matrices, 
	 * it is sorted first by number of occurrences and then by score. 
	 * The score for an item is computed considering the weight of the matrix that has recommended it.
	 * @param n The number of recommendations to produce.
	 * @param items The list of possible recommendations. It can contain multiple instance of the same item.
	 * @return The result list .
	 */
	private List<ScoredId> getRankedRecommendationsList(int n, List<RecommendationTriple> items){
		logger.info("Ranking the recommendations list");

		List<ScoredId> recommendations = new LinkedList<ScoredId>();

		// groups items by id
		HashMap<Long,List<RecommendationTriple>> unsortedMap = new HashMap<Long,List<RecommendationTriple>>();
		for(RecommendationTriple triple : items){
			long item = triple.getItemID();
			if(!unsortedMap.containsKey(item)){
				unsortedMap.put(item, new LinkedList<RecommendationTriple>());
				unsortedMap.get(item).add(triple);
			}
			else
				unsortedMap.get(item).add(triple);
		}

		TreeSet<OccScoreTriple> sortedList = new TreeSet<OccScoreTriple>();

		// computes the score of each item (weighted mean)
		for(Long item : unsortedMap.keySet()){
			double score = 0;
			double totWm = 0;
			for(RecommendationTriple i : unsortedMap.get(item)){
				double si = i.getScore();
				int mat = i.getMatID();
				double wm = (mat == -1) ? 1 : models.getModelWeight(mat);
				totWm += wm;
				score += si*wm;
			}

			// sorted first by number of occurrences, then by score
			sortedList.add(new OccScoreTriple(item, unsortedMap.get(item).size(), score/totWm));	
		}

		for(OccScoreTriple item : sortedList)
			if(recommendations.size() != n)
				recommendations.add(new ScoredIdBuilder(item.getItemID(), item.getScore()).build());

		logger.info("Ranking completed");
		return recommendations;
	}

	/**
	 * Checks if a user has already rated an item
	 * @param userHistory list of user ratings
	 * @param seed the item to check
	 * @return true if the user has already rated the item, otherwise false
	 */
	private boolean hasRatedItem(UserHistory<Rating> userHistory, long seed) {
		if(userHistory == null)
			return false;
		for(Rating r : userHistory)
			if(r.getItemId() == seed)
				return true;
		return false;
	}

	/**
	 * Product recommendation list of size N for a registered user ID.
	 * @param user the considered user
	 * @param n number of recommendations
	 * @param seed_itemset external seed items
	 * @param activate_standard_seed true if standard seed must be used, false otherwise
	 * @return the recommendations list
	 */
	public List<ScoredId> get_recommendation_list(long user, int n, Set<Long> seed_itemset, boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(user, n);
	}

	/**
	 * Product recommendation list of size N for a registered user ID without external itemset.  
	 * @param user the considered user
	 * @param n number of recommendations
	 * @param activate_standard_seed true if standard seed must be used, false otherwise
	 * @return the recommendations list
	 */
	public List<ScoredId> get_recommendation_list(long user, int n, boolean activate_standard_seed){
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(user, n);
	}

	/**
	 * Product recommendation list of size N for a registered user ID
	 * @param n number of recommendations
	 * @param seed_itemset external seed items
	 * @param activate_standard_seed true if standard seed must be used, false otherwise
	 * @return the recommendations list
	 */
	public List<ScoredId> get_recommendation_list(int n, Set<Long> seed_itemset, boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(-1,n);
	}

	/**
	 * Product recommendation list of size N for the anonymous user, similar to the service to the user registered with the passage of seed
	 * @param n number of recommendations
	 * @return the recommendations list
	 */
	public List<ScoredId> get_recommendation_list(int n){
		return this.recommend(-1, n);
	}

}
