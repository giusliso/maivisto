package it.maivisto.models;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import it.maivisto.utility.Config;
import it.maivisto.utility.Serializer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Build a co-occurrence model from rating data and normalize it (co-occurrence / max).
 */
@NotThreadSafe
public class CoOccurrenceMatrixModelBuilder implements Provider<ItemItemModel> {
	private static final Logger logger = LoggerFactory.getLogger(CoOccurrenceMatrixModelBuilder.class);

	private final ItemItemBuildContext context;


	@Inject
	public CoOccurrenceMatrixModelBuilder(@Transient ItemItemBuildContext context) {
		this.context = context;
	}

	/**
	 * Create the co-occurrence matrix.
	 */
	@Override
	public ItemItemModel get() {
		int max = 0;
		CoOccurrenceMatrixModel model=null;
		//Serializer serializer=new Serializer();

		//CoOccurrenceMatrixModel model=(CoOccurrenceMatrixModel) serializer.deserialize(Config.dirSerialModel, "CoOccurrenceMatrixModel");
		//if(model==null){

			LongSortedSet allItems = context.getItems();
			int nitems = allItems.size();

			logger.info("building item-item model for {} items", nitems);
			logger.info("co-occurrence function is symmetric");

			Long2ObjectMap<ScoredItemAccumulator> rows = makeAccumulators(allItems);

			Stopwatch timer = Stopwatch.createStarted();
			int ndone=1;
			for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext() ; ) {
				Long i = itI.next();
				SparseVector vecI = context.itemVector(i);

				if (logger.isDebugEnabled()) 
					logger.info("computing co-occurrences for item {} ({} of {})", i, ndone, nitems);

				for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
					Long j = itJ.next();
					SparseVector vecJ = context.itemVector(j);
					int coOccurences = vecJ.countCommonKeys(vecI);
					rows.get(i).put(j, coOccurences);
					rows.get(j).put(i, coOccurences);

					if(coOccurences > max)
						max=coOccurences;
				}

				if (logger.isDebugEnabled() && ndone % 100 == 0) 
					logger.info("computed {} of {} model rows ({}s/row)", 
							ndone, nitems, 
							String.format("%.3f", timer.elapsed(TimeUnit.MILLISECONDS) * 0.001 / ndone));

				ndone++;
			}

			logger.info("max co-occurrence value = {}", max);
			logger.info("normalizing item-item model");

			for(Long i : rows.keySet()){
				ScoredItemAccumulator acc = new UnlimitedScoredItemAccumulator();			
				for( ScoredId val : rows.get(i).finish())
					acc.put(val.getId(), val.getScore()/max);					
				rows.put(i, acc);
			}
			logger.info("normalized item-item model");		

			timer.stop();
			logger.info("built model for {} items in {}", ndone, timer);

			model=new CoOccurrenceMatrixModel(finishRows(rows));			
			//serializer.serialize(Config.dirSerialModel,model,"CoOccurrenceMatrixModel");
		//}

		return model;
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
}