import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer;
import org.grouplens.lenskit.baseline.UserMeanBaseline;
import org.grouplens.lenskit.baseline.UserMeanItemScorer;
import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;
import com.thesis.models.CoOccurrenceMatrixModel
import com.thesis.qualifiers.CoOccurrenceModel;
import com.thesis.qualifiers.CosineSimilarityModel;
import com.thesis.recommender.SeedRecommender
import org.grouplens.lenskit.baseline.MeanDamping

bind ItemRecommender to SeedRecommender
bind ItemScorer to UserMeanItemScorer
bind (UserMeanBaseline,ItemScorer) to ItemMeanRatingItemScorer
set MeanDamping to 5.0d
bind (CoOccurrenceModel, ItemItemModel) to CoOccurrenceMatrixModel
bind (CosineSimilarityModel, ItemItemModel) to SimilarityMatrixModel
within (CosineSimilarityModel, ItemItemModel) {
	bind VectorSimilarity to CosineVectorSimilarity
}