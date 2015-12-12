package it.maivisto.models;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import it.maivisto.utility.Config;
import it.maivisto.utility.STS;
import it.maivisto.utility.Serializer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Build a item content similarity model.
 */
@NotThreadSafe
public class ItemContentMatrixModelBuilder implements Provider<ItemItemModel> {
	private static final Logger logger = LoggerFactory.getLogger(ItemContentMatrixModelBuilder.class);

	private int totSimilarities = 0;
	private int computedSims=0;

	private final ItemItemBuildContext context;
	private Long2ObjectMap<ScoredItemAccumulator> rows;
	private int nthreads = 5;
	private int threadCount = nthreads;
	private HashMap<Long,String> icMap;

	@Inject
	public ItemContentMatrixModelBuilder(@Transient ItemItemBuildContext context) {
		this.context = context;
	}


	/**
	 * Create the item-content matrix.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public ItemItemModel get() {
		boolean toUpdate=true;
		LongSortedSet allItems = context.getItems();

		Serializer serializer = new Serializer();
		serializer.register(Long2ObjectOpenHashMap.class);
		serializer.register(UnlimitedScoredItemAccumulator.class);

		rows=(Long2ObjectMap<ScoredItemAccumulator>) serializer.deserialize(Config.dirSerialModel, "ItemContentMatrixModel",Long2ObjectOpenHashMap.class);

		try {
			if(rows==null) 
				buildModel(allItems);				
			else {
				// check if the model is updated
				LongSortedSet serializedItem = new LongAVLTreeSet(rows.keySet());
				LongSortedSet newItems = LongUtils.setDifference(allItems, serializedItem);
				LongSortedSet deletedItems = LongUtils.setDifference(LongUtils.setUnion(serializedItem, allItems), allItems);

				if(!newItems.isEmpty() || !deletedItems.isEmpty())
					updateModel(newItems, serializedItem, deletedItems, allItems);
				else
					toUpdate=false;
			}
		} catch (InterruptedException e) {
			logger.error(e.getMessage());
		}	

		if(toUpdate)
			serializer.serialize(Config.dirSerialModel, rows, "ItemContentMatrixModel");

		return new ItemContentMatrixModel(finishRows(rows));
	}



	private void buildModel(LongSortedSet allItems) throws InterruptedException {
		int nitems = allItems.size();
		totSimilarities = (nitems*(nitems-1))/2;

		if(totSimilarities < nthreads){
			nthreads=totSimilarities;
			threadCount = nthreads;
		}

		ArrayList<ItemContentThread> threads=new ArrayList<ItemContentThread>();
		for(int z = 0; z < nthreads; z++)
			try {
				threads.add(new ItemContentThread());
			} catch (Exception e) {
				logger.error(e.getMessage());
			}

		int simsThread=totSimilarities/nthreads; // how many similarities a thread have to compute

		icMap = getItemsContentMap(); // read item content and store it in a map

		logger.info("building item-content similarity model for {} items", nitems);
		logger.info("item-content similarity model is symmetric");

		rows = makeAccumulators(allItems);

		Stopwatch timer = Stopwatch.createStarted();

		int countItems=0;
		int currThread=0;

		for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext() ; ) {
			Long i = itI.next();

			for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
				Long j = itJ.next();

				// assign the same number of similarities to each thread (the last one can have more similarities)
				if(countItems!=simsThread || currThread==nthreads-1)
					countItems++;
				else{
					countItems=1;
					currThread++;
				}
				threads.get(currThread).addSimilarity(new Similarity(i,j));
			}

		}

		// start computing similarities
		for(ItemContentThread t:threads)
			t.start();

		// wait for the ending of all threads
		while(threadCount!=0)
			Thread.sleep(0);

		timer.stop();
		logger.info("built model for {} items in {}", nitems, timer);

	}

	private void updateModel(LongSortedSet newItems, LongSortedSet serializedItems, LongSortedSet deletedItems, LongSortedSet allItems) throws InterruptedException {
		Stopwatch timer = Stopwatch.createStarted();

		int allItemsSize = allItems.size();
		int newItemsSize = newItems.size();

		// remove column and row of deleted items
		if(deletedItems.size() > 0) {
			for(long i : serializedItems){
				UnlimitedScoredItemAccumulator newAcc = new UnlimitedScoredItemAccumulator();
				List<ScoredId> oldAcc = rows.get(i).finish();
				for(ScoredId si : oldAcc)
					if(!deletedItems.contains(si.getId()))
						newAcc.put(si.getId(), si.getScore());
				rows.replace(i, newAcc);
			}
			for(long di : deletedItems)
				rows.remove(di);

			logger.info("deleted {} items from serialized item-content model", deletedItems.size());
		}

		// add new items similarities	

		if(newItems.size() >0){

			int simNotToCompute = newItemsSize*(newItemsSize+1)/2;
			totSimilarities = allItemsSize * newItemsSize - simNotToCompute;

			if(totSimilarities < nthreads){
				nthreads=totSimilarities;
				threadCount = nthreads;
			}

			ArrayList<ItemContentThread> threads=new ArrayList<ItemContentThread>();
			for(int z = 0; z < nthreads; z++)
				try {
					threads.add(new ItemContentThread());
				} catch (Exception e) {
					logger.error(e.getMessage());
				}

			int simsThread=totSimilarities/nthreads; // how many similarities a thread have to compute		

			icMap = getItemsContentMap(); // read item content and store it in a map

			logger.info("updating item-content similarity model with {} new items", newItemsSize);
			logger.info("item-content similarity model is symmetric");

			int countItems=0;
			int currThread=0;

			HashSet<Similarity> addedSimilarity = new HashSet<Similarity>();
			for(Long i : newItems){
				rows.put(i, new UnlimitedScoredItemAccumulator()); 

				for(Long j : allItems){
					if(i!=j){
						Similarity sim = new Similarity(i,j);

						if(!addedSimilarity.contains(sim)) {
							// assign the same number of similarities to each thread (the last one can have more similarities)
							if(countItems!=simsThread || currThread==nthreads-1)
								countItems++;
							else{
								countItems=1;
								currThread++;
							}
							threads.get(currThread).addSimilarity(sim);
							addedSimilarity.add(sim);
						}
					}
				}
			}

			// start computing similarities
			for(ItemContentThread t:threads)
				t.start();

			// wait for the ending of all threads
			while(threadCount!=0)
				Thread.sleep(0);

			logger.info("added similarities for {} new items to serialized item-content model", newItemsSize);
		}
		timer.stop();
		logger.info("update model in {}", timer);
	}


	private HashMap<Long,String> getItemsContentMap(){
		HashMap<Long,String> icMap = new HashMap<Long,String>();
		for(long item : context.getItems()){
			String content = "";
			try {
				content = readItemContent(item);
			}catch (IOException e) {
				logger.error(e.getMessage());
			}
			icMap.put(item, content);
		}
		return icMap;
	}

	private String readItemContent(long item) throws IOException{
		logger.info("reading content item {}",item);
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(new File("data/abstract/"+item)));
		String s = "";
		while((s = br.readLine()) != null)
			sb.append(s);
		br.close();
		logger.info("read content item {}",item);
		return sb.toString();
	}



	private Long2ObjectMap<ScoredItemAccumulator> makeAccumulators(LongSet items) {
		Long2ObjectMap<ScoredItemAccumulator> rows = new Long2ObjectOpenHashMap<ScoredItemAccumulator>(items.size());
		for(Long item : items)
			rows.put(item, new UnlimitedScoredItemAccumulator());       
		return rows;
	}

	private Long2ObjectMap<ImmutableSparseVector> finishRows(Long2ObjectMap<ScoredItemAccumulator> rows) {
		Long2ObjectMap<ImmutableSparseVector> results = new Long2ObjectOpenHashMap<ImmutableSparseVector>(rows.size());
		for (Long2ObjectMap.Entry<ScoredItemAccumulator> e: rows.long2ObjectEntrySet()) 
			results.put(e.getLongKey(), e.getValue().finishVector().freeze());      
		return results;
	}






	class Similarity {
		private long i;
		private long j;

		public Similarity(long i, long j){
			this.i=i;
			this.j=j;
		}

		public long getI(){
			return i;
		}

		public long getJ(){
			return j;
		}

		@Override
		public boolean equals(Object obj) {
			Similarity o = (Similarity)obj;
			if((i == o.getI() && j == o.getJ()) || (i == o.getJ() && j == o.getI()))
				return true;
			else
				return false;
		}

		@Override
		public int hashCode() {
			// TODO Auto-generated method stub
			return 0;
		}

	}



	class ItemContentThread extends Thread {
		private STS valueSim;
		private LinkedList<Similarity> similarities;

		ItemContentThread() throws Exception {
			similarities=new LinkedList<Similarity>();
			valueSim=new STS(Config.dirConfigItemContent,Config.dirStackingItemContent);
		}

		public void run() {

			for(Similarity sim:similarities){
				try {
					long i=sim.getI();
					long j=sim.getJ();

					double simIJ = valueSim.computeSimilarities(icMap.get(i), icMap.get(j)).getFeatureSet().getValue("dsmCompSUM-ri");
					rows.get(i).put(j, simIJ);
					rows.get(j).put(i, simIJ);
					computedSims++;
					logger.info("computed content similarity sim({},{}) = sim({},{}) = {}", i, j, j, i, simIJ);
					logger.info("Progress = {}% - {}/{} completed", (double)(computedSims*100/totSimilarities),computedSims,totSimilarities);

				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			} 

			threadCount--;
			logger.info("thread finishes - computed {} similarities",similarities.size());
		}

		public void addSimilarity(Similarity similarity) {
			this.similarities.add(similarity);
		}

	}
}
