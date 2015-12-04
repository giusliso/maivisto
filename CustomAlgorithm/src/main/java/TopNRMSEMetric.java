import org.grouplens.lenskit.Recommender;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.grouplens.lenskit.eval.Attributed;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.metrics.AbstractMetric;
import org.grouplens.lenskit.eval.metrics.ResultColumn;
import org.grouplens.lenskit.eval.metrics.topn.ItemSelector;
import org.grouplens.lenskit.eval.metrics.topn.TopNMetricBuilder;
import org.grouplens.lenskit.eval.traintest.TestUser;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.SparseVector;

import java.util.List;

import static java.lang.Math.sqrt;

/**
 * computes RMSE for just those items returned in a topN recommendation task.
 */
public class TopNRMSEMetric extends AbstractMetric<TopNRMSEMetric.Context, TopNRMSEMetric.AggregateResult, TopNRMSEMetric.UserResult> {
    private final String suffix;
    private final int listSize;
    private final ItemSelector candidates;
    private final ItemSelector exclude;

    public TopNRMSEMetric(String sfx, int listSize, ItemSelector candidates, ItemSelector exclude) {
        super(AggregateResult.class, UserResult.class);
        suffix = sfx;
        this.listSize = listSize;
        this.candidates = candidates;
        this.exclude = exclude;
    }

    @Override
    protected String getSuffix() {
        return suffix;
    }

    @Override
    public Context createContext(Attributed algo, TTDataSet ds, Recommender rec) {
        return new Context();
    }

    @Override
    public UserResult doMeasureUser(TestUser user, Context context) {
        List<ScoredId> recs = user.getRecommendations(listSize, candidates, exclude);
        SparseVector ratings = user.getTestRatings();
        SparseVector predictions = user.getPredictions();
        if (predictions == null || recs == null) {
            return null;
        }
        
        double ratSum = 0;
        double sse = 0;
        int n = 0;
        for(ScoredId s : CollectionUtils.fast(recs)) {
            double rating = ratings.get(s.getId(), Double.NaN);
            double prediction = predictions.get(s.getId(), Double.NaN);
            if (Double.isNaN(rating) || Double.isNaN(prediction)) {
                continue;
            }
            ratSum += rating;
            double err = prediction - rating;
            sse += err * err;
            n++;
        }
        if (n > 0) {
            double rmse = sqrt(sse / n);
            double avgRat = ratSum /n;
            return context.addUser(n, rmse, avgRat);
        } else {
            return null;
        }
    }

    @Override
    protected AggregateResult getTypedResults(Context context) {
        return context.getResult();
    }

    public static class UserResult {
        @ResultColumn("TopN.RMSE.seenItems")
        public final double count;

        @ResultColumn("TopN.RMSE")
        public final double score;
        
        @ResultColumn("TopN.Ave.Rat")
        public final double averageRating;
        
        public UserResult(double len, double rmse, double avgRat) {
            count = len;
            score = rmse;
            averageRating = avgRat;
        }
    }
    
    public static class AggregateResult extends UserResult {
        public AggregateResult(double len, double rmse, double avgRat) {
            super(len, rmse, avgRat);
        }
    }
    
    public static class Context {
        double nUsers = 0;
        double nItems = 0;
        double rmseSum = 0;
        double avgRatSum = 0;
        private AggregateResult result;

        public UserResult addUser(int n, double rmse, double avgRat) {
            nUsers += 1;
            nItems += n;
            rmseSum += rmse;
            avgRatSum += avgRat;
            return new UserResult(n, rmse, avgRat);
        }


        public AggregateResult getResult() {
            if (nUsers > 0) {
                return new AggregateResult(nItems / nUsers, rmseSum / nUsers, avgRatSum / nUsers);
            } else {
                return null;
            }
        }
    }

    /**
     * Build a Top-N length metric to measure Top-N lists.
     * @author <a href="http://www.grouplens.org">GroupLens Research</a>
     */
    public static class Builder extends TopNMetricBuilder<TopNRMSEMetric> {
        @Override
        public TopNRMSEMetric build() {
            return new TopNRMSEMetric(suffix, listSize, candidates, exclude);
        }
    }
}
