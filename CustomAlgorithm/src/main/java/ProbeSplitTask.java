import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.source.CSVDataSourceBuilder;
import org.grouplens.lenskit.eval.AbstractTask;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.grouplens.lenskit.eval.data.RatingWriter;
import org.grouplens.lenskit.eval.data.RatingWriters;
import org.grouplens.lenskit.eval.data.crossfold.*;
import org.grouplens.lenskit.eval.data.traintest.GenericTTDataSet;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.util.io.StagedWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Extract probe ratings by splitting each test user's training ratings.  This produces a new TT
 * data set whose train set is the reduced train set and test set is the probe set.
 *
 * @author Michael Ekstrand
 * 
 * Copied from lenskit-error-analysis project, and retooled to suit my needs.
 */
public class ProbeSplitTask extends AbstractTask<TTDataSet> {
    private static final Logger logger = LoggerFactory.getLogger(ProbeSplitTask.class);
    private TTDataSet input;
    private File trainFile;
    private File testFile;
    private Map<String, Object> attributes;
    private Order<Rating> order;
    private PartitionAlgorithm<Rating> partition;

    public ProbeSplitTask() {
        this("probe-extract");
        attributes = new HashMap<>();
    }

    public ProbeSplitTask(String name) {
        super(name);
    }

    public void setInput(TTDataSet data) {
        input = data;
    }

    public void setTrainFile(File file) {
        trainFile = file;
    }

    public void setTrain(String file) {
        setTrainFile(new File(file));
    }

    public void setTestFile(File file) {
        testFile = file;
    }

    public void setTest(String file) {
        setTestFile(new File(file));
    }

    /**
     * Set the order for the train-test splitting. To split a user's ratings, the ratings are
     * first ordered by this order, and then partitioned.
     *
     * @param o The sort order.
     * @see org.grouplens.lenskit.eval.data.crossfold.RandomOrder
     * @see org.grouplens.lenskit.eval.data.crossfold.TimestampOrder
     */
    public void setOrder(Order<Rating> o) {
        order = o;
    }
    /**
     * Set holdout to a fixed number of items per user.
     *
     * @param n The number of items to hold out from each user's profile.
     */
    public void setHoldout(int n) {
        partition = new HoldoutNPartition<Rating>(n);
    }

    /**
     * Set holdout from using the retain part to a fixed number of items.
     *
     * @param n The number of items to train data set from each user's profile.
     */
    public void setRetain(int n) {
        partition = new RetainNPartition<Rating>(n);
    }

    /**
     * Set holdout to a fraction of each user's profile.
     *
     * @param f The fraction of a user's ratings to hold out.
     */
    public void setHoldoutFraction(double f){
        partition = new FractionPartition<Rating>(f);
    }



    public Map<String,Object> getAttributes() {
        ImmutableMap.Builder<String,Object> attrs = ImmutableMap.builder();
        return attrs.putAll(input.getAttributes())
                    .putAll(attributes)
                    .build();
    }

    public Map<String,Object> getExtraAttributes() {
        return attributes;
    }

    private TTDataSet makeResult() {
        CSVDataSourceBuilder trainBuild = new CSVDataSourceBuilder();
        trainBuild.setDomain(input.getTrainingData().getPreferenceDomain())
                  .setDelimiter(",")
                  .setFile(trainFile);
        CSVDataSourceBuilder testBuild = new CSVDataSourceBuilder();
        testBuild.setDomain(input.getTestData().getPreferenceDomain())
                 .setDelimiter(",")
                 .setFile(testFile);

        return GenericTTDataSet.copyBuilder(input)
                               .setTrain(trainBuild.build())
                               .setTest(testBuild.build())
                               .setQuery(null)
                               .build();
    }

    @Override
    protected TTDataSet perform() throws TaskExecutionException, InterruptedException {
        Holdout holdout = new Holdout(order, partition);
        
        TTDataSet result = makeResult();
        if (result.lastModified() > input.lastModified()) {
            logger.info("{} up to date", getName());
            return result;
        }
        
        logger.info("splitting {}", input.getName());
        
        LongSet users = input.getTestData().getUserDAO().getUserIds();
        logger.info("splitting for {} test users", users.size());

        try (StagedWrite trainWrite = StagedWrite.begin(trainFile);
             StagedWrite probeWrite = StagedWrite.begin(testFile)) {
            try (RatingWriter train = RatingWriters.csv(trainWrite.getStagingFile());
                 RatingWriter probe = RatingWriters.csv(probeWrite.getStagingFile());
                 Cursor<UserHistory<Rating>> profiles = input.getTrainingData()
                                                             .getUserEventDAO()
                                                             .streamEventsByUser(Rating.class)) {

                for (UserHistory<Rating> profile: profiles) {
                    List<Rating> trainRatings, testRatings;
                    if (users.contains(profile.getUserId())) {
                        ArrayList<Rating> ratings = new ArrayList<>(profile);
                        
                        int part = holdout.partition(ratings, getProject().getRandom());
                        
                        trainRatings = ratings.subList(0, part);
                        testRatings = ratings.subList(part, ratings.size());
                    } else {
                        trainRatings = profile;
                        testRatings = Collections.emptyList();
                    }

                    for (Rating r: trainRatings) {
                        train.writeRating(r);
                    }
                    for (Rating r: testRatings) {
                        probe.writeRating(r);
                    }
                }
            }
            probeWrite.commit();
            trainWrite.commit();
        } catch (IOException e) {
            throw new TaskExecutionException("I/O error", e);
        }

        return result;
    }
}
