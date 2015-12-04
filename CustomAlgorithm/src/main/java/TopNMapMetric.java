import it.unimi.dsi.fastutil.longs.LongSet;
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
import org.grouplens.lenskit.util.statistics.MeanAccumulator;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Metric that measures how diverse the items in the TopN list are.
 * 
 * To use this metric ensure that you have a reasonable itemSimilarityMetric configured in each algorithm
 * 
 * Example configuration (add this to your existing algorithm configuration)
 * <pre>
 * root (ItemSimilarityMetric)
 * within (ItemSimilarityMetric) {
 *     bind VectorSimilarity to CosineVectorSimilarity
 *     bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
 *     within (UserVectorNormalizer) {
 *         bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
 *         set MeanDamping to 5.0d
 *     }
 * }
 * </pre>
 * 
 * I also recommend enabling model sharing and cacheing between algorithms to make this much more efficient.
 * 
 * This computes the average disimilarity (-1 * similarity) of all pairs of items. 
 * 
 * The number is 1 for a perfectly diverse list, and -1 for a perfectly non-diverse lists.
 * 
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class TopNMapMetric extends AbstractMetric<MeanAccumulator, TopNMapMetric.Result, TopNMapMetric.Result> {
    private final String suffix;
    private final int listSize;
    private final ItemSelector candidates;
    private final ItemSelector exclude;
    private final ItemSelector queryItems;

    public TopNMapMetric(String sfx, int listSize, ItemSelector candidates, ItemSelector exclude, ItemSelector goodItems) {
        super(Result.class, Result.class);
        suffix = sfx;
        this.listSize = listSize;
        this.candidates = candidates;
        this.exclude = exclude;
        this.queryItems = goodItems;
    }

    @Nullable
    @Override
    public MeanAccumulator createContext(Attributed algorithm, TTDataSet dataSet, Recommender recommender) {
        return new MeanAccumulator();
    }

    @Override
    protected String getSuffix() {
        return suffix;
    }

    @Override
    protected Result doMeasureUser(TestUser user, MeanAccumulator cntx) {
        
        double precisionSum = 0;
        int n = 0;
        int tp = 0;

        LongSet items = queryItems.select(user);
        List<ScoredId> recs = user.getRecommendations(listSize, candidates, exclude);
        for(ScoredId s : CollectionUtils.fast(recs)) {
            if(items.contains(s.getId())) {
                tp += 1;
            }
            n +=1;
            double precision = (double) tp/n;
            precisionSum += precision;
        }

        if (n > 0) {
            double map = precisionSum/n;
            cntx.add(map);
            return new Result(map);
        } else {
            return null;
        }
    }

    @Override
    protected Result getTypedResults(MeanAccumulator context) {
        if (context.getCount() > 0) {
            return new Result(context.getMean());
        } else {
            return null;
        }
    }

    public static class Result {
        @ResultColumn("MAP")
        public double map;

        public Result(double map) {
            this.map = map;
        }
    }

    /**
     * Build a Top-N length metric to measure Top-N lists.
     * @author <a href="http://www.grouplens.org">GroupLens Research</a>
     */
    public static class Builder extends TopNMetricBuilder<TopNMapMetric> {
        private ItemSelector goodItems = null;

        public ItemSelector getGoodItems() {
            return goodItems;
        }

        public Builder setGoodItems(ItemSelector goodItems) {
            this.goodItems = goodItems;
            return this;
        }

        @Override
        public TopNMapMetric build() {
            return new TopNMapMetric(suffix, listSize, candidates, exclude, goodItems);
        }
    }

}
