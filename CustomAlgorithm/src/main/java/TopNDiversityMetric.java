
import com.google.common.base.Preconditions;
import org.grouplens.lenskit.Recommender;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.grouplens.lenskit.core.LenskitRecommender;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.eval.Attributed;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.metrics.AbstractMetric;
import org.grouplens.lenskit.eval.metrics.ResultColumn;
import org.grouplens.lenskit.eval.metrics.topn.ItemSelector;
import org.grouplens.lenskit.eval.metrics.topn.TopNMetricBuilder;
import org.grouplens.lenskit.eval.traintest.TestUser;
import org.grouplens.lenskit.knn.item.ItemSimilarity;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.statistics.MeanAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;

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
public class TopNDiversityMetric extends AbstractMetric<TopNDiversityMetric.SimAndMeanAccumulator, TopNDiversityMetric.Result, TopNDiversityMetric.Result> {
    private final int listSize;
    private final ItemSelector candidates;
    private final ItemSelector exclude;
    private String suffix;

    public TopNDiversityMetric(String suffix, int listSize, ItemSelector candidates, ItemSelector exclude) {
        super(Result.class, Result.class);
        this.listSize = listSize;
        this.candidates = candidates;
        this.exclude = exclude;
        this.suffix = suffix;
    }

    @Nullable
    @Override
    public SimAndMeanAccumulator createContext(Attributed algorithm, TTDataSet dataSet, Recommender recommender) {
        ItemSimilarityMetric similarityMetric = ItemSimilarityMetric.makeOne(dataSet.getTrainingDAO());
        return new SimAndMeanAccumulator(similarityMetric);
    }

    @Override
    protected String getSuffix() {
        return suffix;
    }

    @Override
    protected Result doMeasureUser(TestUser user, SimAndMeanAccumulator cntx) {
        List<ScoredId> recs;
        recs = user.getRecommendations(listSize, candidates, exclude);
        if (recs == null || recs.isEmpty()) {
            return null;
        }

        double simSum = 0;

        ItemSimilarityMetric metric = cntx.metric;
        ItemItemBuildContext context = metric.getContext();
        ItemSimilarity sim = metric.getSim();
        if (context == null || sim == null) {
            throw new RuntimeException("TopNDiversityMetric requires a build context and similarity function.");
        }

        for (ScoredId s1 : CollectionUtils.fast(recs)) {
            long i1 = s1.getId();
            SparseVector v1 = context.itemVector(i1);
            for (ScoredId s2 : CollectionUtils.fast(recs)) {
                long i2 = s2.getId();
                if(i1 == i2) {
                    continue;
                }
                SparseVector v2 = context.itemVector(i2);
                simSum += sim.similarity(i1,v1,i2,v2);
            }
        }

        int n = recs.size();
        simSum /= (n*n - n);

        cntx.add(simSum);
        return new Result(simSum);

    }

    @Override
    protected Result getTypedResults(SimAndMeanAccumulator context) {
        if (context.getCount() > 0) {
            return new Result(context.getMean());
        } else {
            return null;
        }
    }

    public static class Result {
        @ResultColumn("diversity")
        public double diverse;

        public Result(double diverse) {
            this.diverse = diverse;
        }
    }

    /**
     * Build a Top-N length metric to measure Top-N lists.
     * @author <a href="http://www.grouplens.org">GroupLens Research</a>
     */
    public static class Builder extends TopNMetricBuilder<TopNDiversityMetric> {
        @Override
        public TopNDiversityMetric build() {
            return new TopNDiversityMetric(suffix, listSize, candidates, exclude);
        }
    }
    
    public static class SimAndMeanAccumulator extends MeanAccumulator {
        ItemSimilarityMetric metric;

        public SimAndMeanAccumulator(ItemSimilarityMetric metric) {
            super();
            this.metric = metric;
        }
    }

}
