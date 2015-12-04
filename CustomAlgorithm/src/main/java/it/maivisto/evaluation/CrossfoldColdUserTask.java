package it.maivisto.evaluation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.grouplens.lenskit.cursors.Cursors;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.grouplens.lenskit.eval.data.crossfold.CrossfoldTask;
import org.grouplens.lenskit.eval.data.crossfold.Holdout;
import org.grouplens.lenskit.util.table.writer.CSVWriter;
import org.grouplens.lenskit.util.table.writer.TableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closer;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongLists;

/**
 * It extends the lenskit CrossfoldTask class and runs a custom crossfold on the data source file 
 * and outputs the partition files. After selecting training and testing users for each partition, 
 * it sets a certain percentage of test users in cold start condition (i.e. number of rating < 20)
 * and splits them into profile sizes from 0 to 19 ratings. 
 */
public class CrossfoldColdUserTask extends CrossfoldTask {
	private static final Logger logger = LoggerFactory.getLogger(CrossfoldColdUserTask.class);

	private int coldPercent=30; // default 

	public CrossfoldColdUserTask(String s) {
		super(s);
	}

	/**
	 * Write train-test split files
	 * @throws  java.io.IOException if there is an error writing the files.
	 */
	@Override
	protected void createTTFiles() throws IOException {
		int partitionCount = getPartitionCount();
		File[] trainFiles = getFiles(getTrainPattern());
		File[] testFiles = getFiles(getTestPattern());
		TableWriter[] trainWriters = new TableWriter[partitionCount];
		TableWriter[] testWriters = new TableWriter[partitionCount];
		Closer closer = Closer.create();
		try {
			for (int i = 0; i < partitionCount; i++) {
				File train = trainFiles[i];
				File test = testFiles[i];
				trainWriters[i] = closer.register(CSVWriter.open(train, null));
				testWriters[i] = closer.register(CSVWriter.open(test, null));
			}
			writeTTFiles(trainWriters, testWriters);
		} catch (Throwable th) {
			throw closer.rethrow(th);
		} finally {
			closer.close();
		}
	}

	/**
	 * Write the split files by Users from the DAO using specified holdout method.
	 * The "coldPercent"% (default 30%) of the test users has a number of training ratings < 20 (cold start situation).
	 * @param trainWriters The tableWriter that write train files
	 * @param testWriters The tableWriter that writ test files
	 * @throws org.grouplens.lenskit.eval.TaskExecutionException
	 */
	private void writeTTFiles(TableWriter[] trainWriters, TableWriter[] testWriters) throws TaskExecutionException  {

		logger.info("splitting data source {} to {} partitions by users", getName(), getPartitionCount());

		Long2IntMap splits = splitUsers(getSource().getUserDAO()); 

		LinkedList<Partition> partitions = new LinkedList<Partition>();
		for(int i=0; i<getPartitionCount(); i++)
			partitions.add(new Partition(i,getPartitionUsers(i,splits), 30));

		Holdout mode = this.getHoldout();
		try {

			logger.info("cold start test users in each partition: {}%", coldPercent);

			ArrayList<UserHistory<Rating>> histories = Cursors.makeList(getSource().getUserEventDAO().streamEventsByUser(Rating.class));
			Collections.shuffle(histories);

			for (UserHistory<Rating> history : histories) {
				List<Rating> ratings = new ArrayList<Rating>(history);
				int trainRating=0;

				long user = history.getUserId();
				int p = splits.get(history.getUserId());
				int n = ratings.size();

				Partition partition = partitions.get(p);
				if(partition.isColdStartUser(user)) {
					// cold start user
					trainRating = partition.getProfileSizeForUser(user);
					logger.info("Cold Start User {} has {} training ratings and {} test ratings", user,trainRating,n-trainRating);
				}
				else {
					// no cold start user
					trainRating = mode.partition(ratings, getProject().getRandom());
					if(trainRating<20)
						trainRating=20+(n-20)/2; 
					logger.info("No Cold Start User {} has {} training ratings and {} test ratings", user,trainRating,n-trainRating);
				}

				for (int f = 0; f < getPartitionCount(); f++) {
					if (f == p) {
						for (int j = 0; j < trainRating; j++)
							writeRating(trainWriters[f], ratings.get(j));

						for (int j = p; j < n; j++) 
							writeRating(testWriters[f], ratings.get(j));
					} 
					else 
						for (Rating rating : ratings) 
							writeRating(trainWriters[f], rating);
				}
			}
		} catch (IOException e) {
			throw new TaskExecutionException("Error writing to the train test files", e);
		}
	}



	/**
	 * Set how many test user must be in cold start situation.
	 * @param csPercentual cold start test user percentage 
	 */
	public void setColdStartCasesPercentual(int csPercentual) {
		this.coldPercent = csPercentual;
	}



	private LinkedList<Long> getPartitionUsers(int i, Long2IntMap splits) {
		LinkedList<Long> users = new LinkedList<Long>();
		for(long u : splits.keySet())
			if(splits.get(u) == i)
				users.add(u);
		return users;
	}


	class Partition {

		private Long2IntMap csUsers;
		private final int numProfiles = 20;
		private int n_partition;

		public Partition(int np, LinkedList<Long> testUsers, int percCsUsers) {
			n_partition = np;
			csUsers = new Long2IntOpenHashMap();

			int testUsersSize = testUsers.size();
			int csu = testUsersSize*percCsUsers/100; //csUsers in partition

			Collections.shuffle(testUsers);
			for(int i=0; i<csu; i++)
				csUsers.put(testUsers.get(i).longValue(), 0);

			logger.info("Partition {} has {} cold start users and {} no cold start users", n_partition, csUsers.keySet().size(), testUsers.size()-csUsers.keySet().size());

			splitCSUsersIntoProfileSizes();
		}

		public boolean isColdStartUser(long user){
			return csUsers.containsKey(user);
		}

		public int getProfileSizeForUser(long user){
			return csUsers.get(user);
		}

		private void splitCSUsersIntoProfileSizes(){
			LongArrayList users = new LongArrayList(csUsers.keySet());
			LongLists.shuffle(users, getProject().getRandom());
			LongListIterator iter = users.listIterator();
			while (iter.hasNext()) {
				final int idx = iter.nextIndex();
				final long user = iter.nextLong();
				final int profSize =idx % numProfiles;
				csUsers.put(user, profSize);
				logger.info("Partition {} - Cold start user {} with profile size = {}", n_partition, user,profSize);
			}
		}
	}
}
