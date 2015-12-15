import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.BaselineScorer;
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer
import org.grouplens.lenskit.baseline.MeanDamping
import org.grouplens.lenskit.baseline.UserMeanBaseline;
import org.grouplens.lenskit.baseline.UserMeanItemScorer;
import org.grouplens.lenskit.eval.metrics.predict.NDCGPredictMetric;
import org.grouplens.lenskit.eval.metrics.predict.RMSEPredictMetric
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.iterative.IterationCount;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ModelSize
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.mf.funksvd.FeatureCount;
import org.grouplens.lenskit.mf.funksvd.FunkSVDItemScorer;
import org.grouplens.lenskit.transform.normalize.BaselineSubtractingUserVectorNormalizer;
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;
import org.grouplens.lenskit.eval.metrics.topn.*

import it.maivisto.baselines.CoCoverageRec
import it.maivisto.baselines.PopularityRec
import it.maivisto.baselines.RandomPopularityRec
import it.maivisto.evaluation.CrossfoldColdUserTask
import it.maivisto.evaluation.CustomPrecisionRecallTopNMetric
import it.maivisto.models.CoOccurrenceMatrixModel
import it.maivisto.models.CosineSimilarityMatrixModel
import it.maivisto.models.ItemContentMatrixModel
import it.maivisto.qualifiers.CoOccurrenceModel
import it.maivisto.qualifiers.CosineSimilarityModel
import it.maivisto.qualifiers.ItemContentSimilarityModel
import it.maivisto.recommender.SeedRecommender


dataDir = "build/data"

datasetName = "ml100k"

def csuPerc = 50;

topNConfig = {
	it.setListSize(20);
	it.setCandidates(ItemSelectors.allItems());
	it.setExclude(ItemSelectors.trainingItems());
	it.build();
}


trainTest("eval") {

	output "build/output/${datasetName}-results-${csuPerc}.csv"
	userOutput "build/output/${datasetName}-user-${csuPerc}.csv"
	componentCacheDirectory "build/componentCache"
	cacheAllComponents true

	def data = csvfile("data/ml100k/u.data") {
		delimiter "\t"
		domain {
			minimum 1
			maximum 5
			precision 1.0
		}
	}

	def name = data.name

	def d = new CrossfoldColdUserTask()
	d.setName(name+"-all-eval")
	d.setProject(this.getProject())
	d.setSource(data)
	d.setOrder(new RandomOrder())
	d.setPartitions(1)
	d.setColdStartCasesPercentual(csuPerc)
	d.setTrain("$dataDir/${datasetName}-crossfold/train-all-eval.%d.csv")
	d.setTest("$dataDir/${datasetName}-crossfold/test-all-eval.%d.csv")

	dataset d.perform()

	addMetric topNConfig(new CustomPrecisionRecallTopNMetric.Builder())

	scorerConfig = {
		bind ItemScorer to UserMeanItemScorer
		bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
		set MeanDamping to 5.0d
	}

algorithm("ItemItem") {
			bind ItemScorer to ItemItemScorer
			within (ItemScorer) {
				bind VectorSimilarity to CosineVectorSimilarity
				bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
				within (UserVectorNormalizer) {
					bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
					set MeanDamping to 5.0d
				}
				set ModelSize to 500
				set NeighborhoodSize to 45
			}
			bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
			bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer

			set MeanDamping to 5.0d
		}

		algorithm("FunkSVD") {
			bind ItemScorer to FunkSVDItemScorer
			bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
			bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
			set MeanDamping to 5.0d
			set FeatureCount to 30
			set IterationCount to 150
		}

		algorithm("Popularity") {
			bind ItemRecommender to PopularityRec
			include scorerConfig
		}

		algorithm("RandomPopularity") {
			bind ItemRecommender to RandomPopularityRec
			include scorerConfig
		}

		algorithm("CoCoverage") {
			bind ItemRecommender to CoCoverageRec
			include scorerConfig
		}

		algorithm("SeedRec") {
			bind ItemRecommender to SeedRecommender
			bind ItemScorer to UserMeanItemScorer
			bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
			set MeanDamping to 5.0d
			set NeighborhoodSize to 15
			bind (CoOccurrenceModel, ItemItemModel) to CoOccurrenceMatrixModel
			bind (CosineSimilarityModel, ItemItemModel) to CosineSimilarityMatrixModel
			within (CosineSimilarityModel, ItemItemModel) {
				bind VectorSimilarity to CosineVectorSimilarity
			}
			bind (ItemContentSimilarityModel, ItemItemModel) to ItemContentMatrixModel
		}
}


