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
public class JustRecommendMetric extends AbstractMetric<Void, Void, Void> {
    private final String suffix;
    private final int listSize;
    private final ItemSelector candidates;
    private final ItemSelector exclude;
    
    public JustRecommendMetric(String sfx, int listSize, ItemSelector candidates, ItemSelector exclude) {
        super(Void.class, Void.class);
        suffix = sfx;
        this.listSize = listSize;
        this.candidates = candidates;
        this.exclude = exclude;
    }

    @Nullable
    @Override
    public Void createContext(Attributed algorithm, TTDataSet dataSet, Recommender recommender) {
        return null;
    }

    @Override
    protected String getSuffix() {
        return suffix;
    }

    @Override
    protected Void doMeasureUser(TestUser user, Void cntx) {
        List<ScoredId> recs = user.getRecommendations(listSize, candidates, exclude);
        return null;
    }

    @Override
    protected Void getTypedResults(Void context) {
        return null;
    }

    /**
     * Build a Top-N length metric to measure Top-N lists.
     * @author <a href="http://www.grouplens.org">GroupLens Research</a>
     */
    public static class Builder extends TopNMetricBuilder<JustRecommendMetric> {
        @Override
        public JustRecommendMetric build() {
            return new JustRecommendMetric(suffix, listSize, candidates, exclude);
        }
    }

}
