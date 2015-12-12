package it.maivisto.evaluation;

import static java.lang.Math.sqrt;
import java.util.LinkedList;
import java.util.List;
import org.grouplens.lenskit.Recommender;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.eval.Attributed;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.metrics.AbstractMetric;
import org.grouplens.lenskit.eval.metrics.ResultColumn;
import org.grouplens.lenskit.eval.metrics.topn.ItemSelector;
import org.grouplens.lenskit.eval.metrics.topn.ItemSelectors;
import org.grouplens.lenskit.eval.metrics.topn.TopNMetricBuilder;
import org.grouplens.lenskit.eval.traintest.TestUser;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.SparseVector;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.maivisto.utility.Utilities;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Evaluate a recommender's prediction accuracy with RMSE,
 * computed on items in the intersection set between the test items
 * and the recommended ones. 
 */
public class CustomRMSEPredictMetric extends AbstractMetric<CustomRMSEPredictMetric.Context, CustomRMSEPredictMetric.AggregateResult, CustomRMSEPredictMetric.UserResult> {
	private static final Logger logger = LoggerFactory.getLogger(CustomRMSEPredictMetric.class);

	private final int listSize;
	private final ItemSelector candidates;
	private final ItemSelector exclude;

	/**
     * @param listSize The number of recommendations 
     * @param candidates The candidate selector.
     * @param exclude The exclude selector.
     */
	public CustomRMSEPredictMetric(int listSize, ItemSelector candidates, ItemSelector exclude) {
		super(AggregateResult.class, UserResult.class);
		this.listSize = listSize;
		this.candidates = candidates;
		this.exclude = exclude;
	}

	@Override
	public Context createContext(Attributed algo, TTDataSet ds, Recommender rec) {
		return new Context();
	}

	@Override
	public UserResult doMeasureUser(TestUser user, Context context) {
		UserHistory<Rating> history = user.getTrainHistory().filter(Rating.class);	
		double userMeanRating = Utilities.meanValue(history);

		LongSet goodTestItems = ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(userMeanRating))
				.select(user);

		if (goodTestItems.isEmpty())
			logger.warn("No good items for user {}", user.getUserId());

		List<ScoredId> recs = user.getRecommendations(listSize, candidates, exclude);

		if (recs == null) {
			logger.warn("No recommendations for user {}", user.getUserId());
			return null;
		}

		logger.info("Searching for {} good items among {} recommendations for {}", goodTestItems.size(), recs.size(), user.getUserId());
		
		List<ScoredId> commonItems = new LinkedList<ScoredId>(); // test items ∩ recommended items
		for(ScoredId s : recs)
			if(goodTestItems.contains(s.getId())) 
				commonItems.add(s);
			
		SparseVector testRatings = user.getTestRatings();
		double sse = 0;
		int n = 0;
		for (ScoredId e : commonItems) {
			if (Double.isNaN(e.getScore()))
				continue;

			double err = e.getScore() - testRatings.get(e.getId());
			sse += err * err;
			n++;
		}
		if (n > 0) {
			double rmse = sqrt(sse / n);
			context.addUser(n, sse, rmse);
			logger.info("RMSE computed on {} items - Test items: {} - Recommended items: {}", commonItems.size(), goodTestItems.size(), recs.size());
			return new UserResult(rmse);
		} 
		else
			return null;
	}

	@Override
	protected AggregateResult getTypedResults(Context context) {
		return context.finish();
	}

	public static class UserResult {
		@ResultColumn("RMSE")
		public final double mae;

		public UserResult(double err) {
			mae = err;
		}
	}

	public static class AggregateResult {
		@ResultColumn("RMSE.ByUser")
		public final double userRMSE;
		@ResultColumn("RMSE.ByRating")
		public final double globalRMSE;

		public AggregateResult(double uerr, double gerr) {
			userRMSE = uerr;
			globalRMSE = gerr;
		}
	}

	public class Context {
		private double totalSSE = 0;
		private double totalRMSE = 0;
		private int nratings = 0;
		private int nusers = 0;

		private void addUser(int n, double sse, double rmse) {
			totalSSE += sse;
			totalRMSE += rmse;
			nratings += n;
			nusers += 1;
		}

		public AggregateResult finish() {
			if (nratings > 0) {
				double v = sqrt(totalSSE / nratings);
				logger.info("RMSE: {}", v);
				return new AggregateResult(totalRMSE / nusers, v);
			} else {
				return null;
			}
		}
	}
	


    public static class Builder extends TopNMetricBuilder<CustomRMSEPredictMetric>{

        public Builder() {
            setCandidates(ItemSelectors.allItems());
        }

        @Override
        public CustomRMSEPredictMetric build() {
            return new CustomRMSEPredictMetric(listSize, candidates, exclude);
        }
    }
}
