import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.BaselineScorer
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer
import org.grouplens.lenskit.baseline.MeanDamping
import org.grouplens.lenskit.baseline.UserMeanBaseline
import org.grouplens.lenskit.baseline.UserMeanItemScorer
import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ModelSize
import org.grouplens.lenskit.transform.normalize.BaselineSubtractingUserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity

bind ItemScorer to ItemItemScorer
within (ItemScorer) {
    bind VectorSimilarity to CosineVectorSimilarity
    bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
    within (UserVectorNormalizer) {
        bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
        set MeanDamping to 5.0d
    }
    set ModelSize to 500
    set NeighborhoodSize to 30
}
bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer

set MeanDamping to 5.0d
