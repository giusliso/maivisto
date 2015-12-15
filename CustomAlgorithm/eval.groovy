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

import com.sun.org.apache.xalan.internal.xsltc.compiler.Import;

import org.grouplens.lenskit.ItemRecommender
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel
import TopNMapMetric
import it.maivisto.baselines.CoCoverageRec
import it.maivisto.baselines.PopularityRec
import it.maivisto.baselines.RandomPopularityRec
import it.maivisto.evaluation.CustomRMSEPredictMetric;
import it.maivisto.models.CoOccurrenceMatrixModel
import it.maivisto.models.CosineSimilarityMatrixModel
import it.maivisto.models.ItemContentMatrixModel
import it.maivisto.qualifiers.CoOccurrenceModel
import it.maivisto.qualifiers.CosineSimilarityModel
import it.maivisto.qualifiers.ItemContentSimilarityModel
import it.maivisto.recommender.SeedRecommender
import it.maivisto.evaluation.CustomPrecisionRecallTopNMetric


dataDir = "build/data"

// utility function allows me to load code bits from other files and run them as if they were in this file
// this lets me split the file up.
def load(filename) {
	// there is probably a cleaner way to do this
	def code =  new File(filename).text // load the code from the other file
	def newCode = ""
	def doneImports = false
	for (line in code.split("\n")) {
		if (!doneImports && !line.startsWith("import")) {
			doneImports = true
			newCode += "tmp = {\n"
		}
		newCode += line + "\n"
	}
	newCode += "};\ntmp"

	Closure closure = evaluate(newCode) // evaluate it to get this closure
	closure.setDelegate(this) // set the closure to be aware of the current evaluation context
	closure() // run it.
}

// load datasets
datasets = []
load("Datasets/ML-100K.groovy")
load("Datasets/ML-1m.groovy")

// dataset switches
datasetName = "ml100k"

sizes = [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19]
maxSize = 19



// process all datasets
crossfolds = [:]
for (data in datasets) {
	def name = data.name

	def coldMax = target(name+"-cold-max") {
		requires data
		crossfold(name+"-cold-max") {
			source data
			test "${dataDir}/${name}-crossfold/test-cold-max.%d.csv"
			train "${dataDir}/${name}-crossfold/train-cold-max.%d.csv"
			order RandomOrder
			retain maxSize
			partitions 5
		}
	}

	def coldSubsets = []
	for (def size in 0..maxSize) {

		// make a version of this variable that is local to the loop.
		// This is necisary to get semantics of scoping with the perform closure right.
		// without this every dataset is build with size 19.
		def localsize = size
		def coldSplit = target(name+"-cold-"+localsize) {
			requires coldMax
			perform {
				//TODO: I probably don't have to do it this way
				def downsample =  new DownsampleDataSet(name+"-cold-"+localsize);
				downsample.setDirectory("${dataDir}/${name}-crossfold/")
				downsample.setRetain(localsize)
				downsample.setSources(coldMax.get())
				downsample.execute()
				downsample.get()
			}
		}
		coldSubsets << coldSplit
	}
	crossfolds << [(name):coldSubsets]
}

topNConfig = {
	it.setListSize(20);
	it.setCandidates(ItemSelectors.allItems());
	it.setExclude(ItemSelectors.trainingItems());
	it.build();
}


target("cold-eval") {
	for (size in sizes) {
		requires crossfolds[datasetName][size]
	}

	trainTest("cold-eval") {
		output "build/output/${datasetName}-cold-eval-results.csv"
		userOutput "build/output/${datasetName}-cold-eval-user.csv"
		componentCacheDirectory "build/componentCache"
		cacheAllComponents true

//		addMetric topNConfig(new CustomRMSEPredictMetric.Builder())
//		addMetric topNConfig(new NDCGTopNMetric.Builder())

		addMetric topNConfig(new CustomPrecisionRecallTopNMetric.Builder())
		
		for (size in sizes) {
			dataset crossfolds[datasetName][size]
		}

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
				set NeighborhoodSize to 21
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
			set NeighborhoodSize to 7
			bind (CoOccurrenceModel, ItemItemModel) to CoOccurrenceMatrixModel
			bind (CosineSimilarityModel, ItemItemModel) to CosineSimilarityMatrixModel
			within (CosineSimilarityModel, ItemItemModel) {
				bind VectorSimilarity to CosineVectorSimilarity
			}
			bind (ItemContentSimilarityModel, ItemItemModel) to ItemContentMatrixModel
		}
	}
}