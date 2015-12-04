import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.BaselineScorer
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer
import org.grouplens.lenskit.baseline.MeanDamping
import org.grouplens.lenskit.baseline.UserMeanBaseline
import org.grouplens.lenskit.baseline.UserMeanItemScorer
import org.grouplens.lenskit.data.event.Rating
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.*
import org.grouplens.lenskit.eval.metrics.topn.*
import org.grouplens.lenskit.iterative.IterationCount
import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ItemSimilarityThreshold
import org.grouplens.lenskit.knn.item.ModelSize
import org.grouplens.lenskit.knn.item.NeighborhoodScorer
import org.grouplens.lenskit.knn.item.WeightedAverageNeighborhoodScorer
import org.grouplens.lenskit.knn.item.model.ItemItemModel
import org.grouplens.lenskit.knn.item.model.NormalizingItemItemModelBuilder
import org.grouplens.lenskit.knn.user.NeighborFinder
import org.grouplens.lenskit.knn.user.SnapshotNeighborFinder
import org.grouplens.lenskit.knn.user.UserUserItemScorer
import org.grouplens.lenskit.mf.funksvd.FeatureCount
import org.grouplens.lenskit.mf.funksvd.FunkSVDItemScorer
import org.grouplens.lenskit.transform.normalize.BaselineSubtractingUserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.threshold.NoThreshold
import org.grouplens.lenskit.transform.threshold.Threshold
import org.grouplens.lenskit.transform.truncate.VectorTruncator
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity
import org.hamcrest.Matchers
import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel
import org.grouplens.lenskit.eval.metrics.topn.PrecisionRecallTopNMetric
import TopNMapMetric
import it.maivisto.baselines.CoCoverageRec;
import it.maivisto.baselines.PopularityRec
import it.maivisto.baselines.RandomPopularityRec
import it.maivisto.models.CoOccurrenceMatrixModel;
import it.maivisto.models.CosineSimilarityMatrixModel
import it.maivisto.qualifiers.CoOccurrenceModel
import it.maivisto.qualifiers.CosineSimilarityModel;
import it.maivisto.recommender.SeedRecommender;;


dataDir = "build/data"

datasetName = "ml100k"

trainTest("eval") {

	output "build/output/${datasetName}-results.csv"
	userOutput "build/output/${datasetName}-user.csv"
	componentCacheDirectory "build/componentCache"
	cacheAllComponents true

	def data = csvfile("${dataDir}/ml-100k/u.data") {
		delimiter "\t"
		domain {
			minimum 1
			maximum 5
			precision 1.0
		}
	}

	def name = data.name

	def d = new CrossfoldColdUserTask(name+"-all-eval")
	d.setProject(this.getProject())
	d.setSource(data)
	d.setOrder(new RandomOrder())
	d.setPartitions(5)
	d.setColdStartCasesPercentual(50)
	d.setTrain("$dataDir/${name}-crossfold/train-all-eval.%d.csv")
	d.setTest("$dataDir/${name}-crossfold/test-all-eval.%d.csv")

	dataset d.perform()

	addMetric RMSEPredictMetric
	addMetric NDCGPredictMetric
	
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
			set NeighborhoodSize to 30
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
		bind ItemScorer to UserMeanItemScorer
		bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
	}
	
	algorithm("RandomPopularity") {
		bind ItemRecommender to RandomPopularityRec
		bind ItemScorer to UserMeanItemScorer
		bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
	}
	
	algorithm("CoCoverage") {
		bind ItemRecommender to CoCoverageRec
		bind ItemScorer to UserMeanItemScorer
		bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
		bind (CoOccurrenceModel, ItemItemModel) to CoOccurrenceMatrixModel
	}
	
	algorithm("SeedRec") {
		bind ItemRecommender to SeedRecommender
		bind ItemScorer to UserMeanItemScorer
		bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
		set MeanDamping to 5.0d
		set NeighborhoodSize to 20
		bind (CoOccurrenceModel, ItemItemModel) to CoOccurrenceMatrixModel
		bind (CosineSimilarityModel, ItemItemModel) to CosineSimilarityMatrixModel
		within (CosineSimilarityModel, ItemItemModel) {
			bind VectorSimilarity to CosineVectorSimilarity
		}
	}
}

