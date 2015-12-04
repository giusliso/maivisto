import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.Recommender;
import org.grouplens.lenskit.eval.Attributed;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.metrics.AbstractMetric;
import org.grouplens.lenskit.eval.metrics.ResultColumn;
import org.grouplens.lenskit.eval.traintest.TestUser;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple evaluator that records user, rating and prediction counts and computes
 * recommender coverage over the queried items.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class ItemScorerCoveragePredictMetric extends AbstractMetric<ItemScorerCoveragePredictMetric.Context, ItemScorerCoveragePredictMetric.AggregateCoverage, ItemScorerCoveragePredictMetric.Coverage> {
    private static final Logger logger = LoggerFactory.getLogger(ItemScorerCoveragePredictMetric.class);

    public ItemScorerCoveragePredictMetric() {
        super(AggregateCoverage.class, Coverage.class);
    }

    @Override
    public Context createContext(Attributed algo, TTDataSet ds, Recommender rec) {
        return new Context(rec.getItemScorer());
    }

    @Override
    public Coverage doMeasureUser(TestUser user, Context context) {
        SparseVector ratings = user.getTestRatings();
        SparseVector predictions = context.is.score(user.getUserId(), user.getTestHistory().itemSet());
        
        if (predictions == null) {
            return null;
        }
        int n = 0;
        int good = 0;
        for (VectorEntry e : ratings.fast()) {
            n += 1;
            if (predictions.containsKey(e.getKey())) {
                good += 1;
            }
        }
        context.addUser(n, good);
        return new Coverage(n, good);
    }

    @Override
    protected AggregateCoverage getTypedResults(Context context) {
        return new AggregateCoverage(context.nusers, context.npreds, context.ngood);
    }

    public static class Coverage {
        @ResultColumn(value="ItemScorer.NAttempted", order=1)
        public final int nattempted;
        @ResultColumn(value="ItemScorer.NGood", order=2)
        public final int ngood;

        private Coverage(int na, int ng) {
            nattempted = na;
            ngood = ng;
        }

        @ResultColumn(value="ItemScorer.Coverage", order=3)
        public Double getCoverage() {
            if (nattempted > 0) {
                return ((double) ngood) / nattempted;
            } else {
                return null;
            }
        }
    }

    public static class AggregateCoverage extends Coverage {
        @ResultColumn(value="ItemScorer.NUsers", order=0)
        public final int nusers;

        private AggregateCoverage(int nu, int na, int ng) {
            super(na, ng);
            nusers = nu;
        }
    }

    public class Context {
        public final ItemScorer is;

        public Context(ItemScorer is) {
            this.is = is;
        }

        private int npreds = 0;
        private int ngood = 0;
        private int nusers = 0;

        private void addUser(int np, int ng) {
            npreds += np;
            ngood += ng;
            nusers += 1;
        }
    }
}
