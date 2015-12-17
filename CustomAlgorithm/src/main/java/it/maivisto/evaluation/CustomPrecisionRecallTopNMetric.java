package it.maivisto.evaluation;

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
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.maivisto.utility.Utilities;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A metric to compute the precision and recall of a recommender.
 */
public class CustomPrecisionRecallTopNMetric extends AbstractMetric<CustomPrecisionRecallTopNMetric.Context, CustomPrecisionRecallTopNMetric.Result, CustomPrecisionRecallTopNMetric.Result> {
    private static final Logger logger = LoggerFactory.getLogger(CustomPrecisionRecallTopNMetric.class);

    private final int listSize;
    private final ItemSelector candidates;
    private final ItemSelector exclude;

    /**
     * @param listSize The number of recommendations. 
     * @param candidates The candidate selector.
     * @param exclude The exclude selector.
     */
    public CustomPrecisionRecallTopNMetric(int listSize, ItemSelector candidates, ItemSelector exclude) {
        super(Result.class, Result.class);
        this.listSize = listSize;
        this.candidates = candidates;
        this.exclude = exclude;
    }


    @Override
    public Context createContext(Attributed algo, TTDataSet ds, Recommender rec) {
        return new Context();
    }

    @Override
    public Result doMeasureUser(TestUser user, Context context) {
    	
    	UserHistory<Rating> history = user.getTrainHistory().filter(Rating.class);	
		double userMeanRating = Utilities.meanValue(history);

		LongSet items = ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(userMeanRating)).select(user);
		
        if (items.isEmpty())
            logger.warn("No good items for user {}", user.getUserId());

        List<ScoredId> recs = user.getRecommendations(listSize, candidates, exclude);
        if (recs == null) {
            logger.warn("No recommendations for user {}", user.getUserId());
            return null;
        }

        logger.info("Searching for {} good items among {} recommendations for {}", items.size(), recs.size(), user.getUserId());
        int tp = 0;
        for(ScoredId s : recs)
            if(items.contains(s.getId()))
                tp += 1;

        if (items.size() > 0 && recs.size() > 0) {
            // if both the items set and recommendations are non-empty (no division by 0).
            double precision = (double) tp / recs.size();
            double recall = (double) tp / items.size();
            double fmeasure = (double) (2*precision*recall)/(precision+recall);
            context.addUser(precision, recall, fmeasure);
            return new Result(precision, recall,fmeasure);
        } 
        else
            return null;
    }

    @Override
    protected Result getTypedResults(Context context) {
        return context.finish();
    }

    public static class Result {
        @ResultColumn("Precision")
        public final double precision;
        @ResultColumn("Recall")
        public final double recall;
        @ResultColumn("F-Measure")
        public final double fMeasure;

        public Result(double prec, double rec, double fmea) {
            precision = prec;
            recall = rec;
            fMeasure = fmea;
        }
    }

    public class Context {
        double totalPrecision = 0;
        double totalRecall = 0;
        double totalFMeasure = 0;
        int nusers = 0;

        private void addUser(double prec, double rec, double fmeasure) {
            totalPrecision += prec;
            totalRecall += rec;
            totalFMeasure += fmeasure;
            nusers += 1;
        }

        public Result finish() {
            if (nusers > 0)
                return new Result(totalPrecision / nusers, totalRecall / nusers, totalFMeasure / nusers);
            else
                return null;          
        }
    }

    public static class Builder extends TopNMetricBuilder<CustomPrecisionRecallTopNMetric>{

        public Builder() {
            // override the default candidate items with a more reasonable set.
            setCandidates(ItemSelectors.allItems());
        }

        @Override
        public CustomPrecisionRecallTopNMetric build() {
            return new CustomPrecisionRecallTopNMetric(listSize, candidates, exclude);
        }
    }
}