package it.maivisto.models;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
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
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Build a item content similarity model.
 */
@NotThreadSafe
public class ItemContentMatrixModelBuilder implements Provider<ItemItemModel> {
	private static final Logger logger = LoggerFactory.getLogger(ItemContentMatrixModelBuilder.class);

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
	@Override
	public ItemItemModel get() {

		Serializer serializer = new Serializer();

		ItemContentMatrixModel model = (ItemContentMatrixModel) serializer.deserialize(Config.dirSerialModel, "ItemContentMatrixModel");

		if(model==null) {
			LongSortedSet allItems = context.getItems();
			int nitems = allItems.size();
			int sims = (nitems*(nitems-1))/2;

			ArrayList<ItemContentThread> threads=new ArrayList<ItemContentThread>();
			for(int z = 0; z < nthreads; z++)
				try {
					threads.add(new ItemContentThread());
				} catch (Exception e) {
					logger.error(e.getStackTrace().toString());
				}

			int simsThread=sims/nthreads; // how many similarities a thread have to compute

			icMap = getItemsContentMap(); // read item content and store it in a map

			logger.info("building item-content similarity model for {} items", nitems);
			logger.info("item-content similarity model is symmetric");

			rows = makeAccumulators(allItems);

			try {
				Stopwatch timer = Stopwatch.createStarted();

				int countItems=0;
				int currThread=0;

				for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext() ; ) {
					Long i = itI.next();

					for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
						Long j = itJ.next();

						// assign the same number of similarities to each thread (the last one can have more similarities)
						if(countItems!=simsThread || currThread==nthreads-1){
							countItems++;
						}
						else{
							countItems=0;
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

				model = new ItemContentMatrixModel(finishRows(rows));    
				serializer.serialize(Config.dirSerialModel,model,"ItemContentMatrixModel");

			} catch (Exception e) {
				logger.error(e.getStackTrace().toString());
			}
		}

		return model;
	}



	private HashMap<Long,String> getItemsContentMap(){
		HashMap<Long,String> icMap = new HashMap<Long,String>();
		for(long item : context.getItems()){
			String content = "";
			try {
				content = readItemContent(item);
			}catch (IOException e) {
				e.printStackTrace();
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



	class Similarity{
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
					logger.info("computed content similarity sim({},{}) = sim({},{}) = {}", i, j, j, i, simIJ);
					
				} catch (Exception e) {
					logger.error(e.getStackTrace().toString());
				}
			} 
			threadCount--;
			logger.info("finish thread - {}",similarities.size());
		}

		public void addSimilarity(Similarity similarity) {
			this.similarities.add(similarity);
		}

	}
}
